package com.oms.vms.emstone

import com.google.gson.JsonObject
import com.oms.api.exception.ApiAccessException
import com.oms.logging.gson.gson
import com.oms.vms.WithMongoDBTestContainer
import com.oms.vms.endpoint.VmsConfigUpdateRequest
import com.oms.vms.manufacturers.emstone.EmstoneNvr
import com.oms.vms.mongo.docs.VmsConfig
import com.oms.vms.service.VmsSynchronizeService
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.bson.Document
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test


@SpringBootTest
@ActiveProfiles("test")
class EmstoneNvrTest : WithMongoDBTestContainer {

    private lateinit var vms: EmstoneNvr

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @Autowired
    private lateinit var vmsSynchronizeService: VmsSynchronizeService

    private lateinit var vmsConfig: VmsConfig

    private val log = LoggerFactory.getLogger(this::class.java)

    @BeforeTest
    fun setup() {
        vms = EmstoneNvr(mongoTemplate, vmsSynchronizeService)

        runBlocking {
            vms.saveVmsConfig(
                VmsConfigUpdateRequest(
                    username = "admin",
                    password = "oms20190211",
                    ip = "192.168.182.200",
                    port = 80,
                    additionalInfo = listOf()
                )
            )
        }
    }

    @Test
    fun `synchronize should fetch camera data from Emstone API and store in MongoDB`(): Unit = runTest {
        // given

        // when: API 호출 및 동기화 실행
        vms.synchronize()

        // then: MongoDB에 저장된 데이터 확인

        // 1. vms_raw_json 컬렉션 데이터 확인
        val rawJsonQuery = Query.query(Criteria.where("vms").`is`("emstone"))
        val rawJsonDoc = mongoTemplate.findOne(rawJsonQuery, Document::class.java, "vms_raw_json").awaitSingle()

        assertNotNull(rawJsonDoc, "Raw JSON document should be saved")
        assertEquals(
            "emstone", rawJsonDoc?.get("vms", String::class.java),
            "Document should have correct VMS type"
        )
        assertEquals(
            "/api/cameras", rawJsonDoc?.get("request_uri", String::class.java),
            "Document should have correct API URI"
        )

        // 2. vms_camera 컬렉션 데이터 확인
        val cameraQuery = Query.query(Criteria.where("vms").`is`("emstone"))
        val cameraCount = mongoTemplate.count(cameraQuery, "vms_camera").block() ?: 0

        // 카메라 데이터가 있는지 확인
        assertTrue(cameraCount > 0, "Camera documents should be saved")

        // 3. 카메라 데이터의 필드 확인
        val cameras =
            mongoTemplate.find(cameraQuery, Document::class.java, "vms_camera").collectList().block() ?: emptyList()

        for (camera in cameras) {
            assertNotNull(camera.getString("_id"), "Camera should have ID")
            assertNotNull(camera.getString("created_at"), "Camera should have creation timestamp")

            val vmsType = camera.get("vms", String::class.java)
            assertNotNull(vmsType, "Camera should have VMS information")
            assertEquals("emstone", vmsType, "Camera should have correct VMS type")

            // 카메라 데이터가 필요한 필드를 포함하는지 확인 (실제 응답에 따라 수정 필요)
            if (camera.containsKey("id")) {
                assertNotNull(camera.getInteger("id"), "Camera should have original ID")
            }

            if (camera.containsKey("name")) {
                assertNotNull(camera.getString("name"), "Camera should have name")
            }
        }

        log.info("테스트 성공: ${cameras.size}개의 카메라 정보가 저장되었습니다.")

        cameras.forEachIndexed { _, camera ->
            log.info("카메라 ${camera["id"]} 정보: ${camera.toJson()}")
        }
    }

    @Test
    fun `synchronize should handle API response format correctly`() = runTest {
        // given


        // when: 동기화 실행
        vms.synchronize()

        // then: 응답이 올바르게 파싱되었는지 확인
        val rawJsonDoc = mongoTemplate.findOne(
            Query.query(Criteria.where("vms").`is`("emstone")),
            Document::class.java,
            "vms_raw_json"
        ).block()

        assertNotNull(rawJsonDoc, "Raw JSON document should be saved")

        // JSON 문서가 올바른 형식인지 확인
        val jsonString = rawJsonDoc?.toJson() ?: ""
        try {
            val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)
            // cameras 필드가 있는지 확인 (실제 응답에 따라 수정 필요)
            if (jsonObject.has("cameras")) {
                assertTrue(jsonObject.get("cameras").isJsonArray, "Cameras field should be an array")
            }
        } catch (e: Exception) {
            fail("Failed to parse JSON: ${e.message}")
        }
    }

    @Test
    fun `synchronize should set correct timestamp format`() = runTest {
        // when: 동기화 실행
        vms.synchronize()

        // then: 저장된 문서의 타임스탬프 형식 확인
        val documents = mongoTemplate.find(
            Query.query(Criteria.where("vms").`is`("emstone")),
            Document::class.java,
            "vms_camera"
        ).collectList().block() ?: emptyList()

        assertFalse(documents.isEmpty(), "Documents should be saved")

        val dateTimePattern = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        for (doc in documents) {
            val createdAt = doc.getString("created_at")
            assertNotNull(createdAt, "Document should have created_at field")

            // 날짜 형식이 올바른지 확인
            try {
                LocalDateTime.parse(createdAt, dateTimePattern)
            } catch (e: Exception) {
                fail("Invalid date format: $createdAt, expected format: yyyy-MM-dd HH:mm:ss")
            }
        }
    }

    @Test
    fun `getRtspUrl should return correctly formatted RTSP URL`() = runTest {
        // given
        val vmsConfigRequest = VmsConfigUpdateRequest(
            username = "admin",
            password = "oms20190211",
            ip = "192.168.182.200",
            port = 80,
            additionalInfo = listOf()
        )

        vms.saveVmsConfig(vmsConfigRequest)

        // 테스트용 카메라 데이터 생성
        val emstoneId = 1 // 엠스톤 NVR의 카메라 ID

        // VMS 카메라 정보 생성 및 저장
        val cameraDoc = Document()
        cameraDoc["_id"] = UUID.randomUUID().toString()
        cameraDoc["vms"] = "emstone"
        cameraDoc["id"] = emstoneId
        cameraDoc["name"] = "Test Camera"
        cameraDoc["created_at"] = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        mongoTemplate.save(cameraDoc, "vms_camera").awaitSingle()

        // when: getRtspUrl 메소드 호출
        val rtspUrl = vms.getRtspURL(cameraDoc["_id"] as String)

        // then: 반환된 RTSP URL이 올바른 형식인지 확인
        // 예상되는 URL 형식: rtsp://username:password@ip/videoId
        val expectedUrl =
            "rtsp://${vmsConfigRequest.username}:${vmsConfigRequest.password}@${vmsConfigRequest.ip}/video$emstoneId"
        assertEquals(expectedUrl, rtspUrl, "RTSP URL should be correctly formatted")
    }

    @Test
    fun `getRtspUrl should throw ApiAccessException when camera not found`() = runTest {
        // given: VMS 설정만 저장하고 카메라 정보는 없음

        // when & then: 존재하지 않는 카메라 ID로 호출하면 예외 발생
        val nonExistentCameraId = "non_existent_camera"

        val exception = assertThrows<ApiAccessException> { vms.getRtspURL(nonExistentCameraId) }

        // 예외 메시지 확인
        assertTrue(
            exception.message.contains("not found"),
            "Exception message should indicate camera not found"
        )
    }

    @Test
    fun `getRtspUrl should throw ApiAccessException when camera has no id property`() = runTest {
        // given

        // ID 속성이 누락된 카메라 문서 생성
        val cameraId = "camera_without_id"
        val cameraDoc = Document()
        cameraDoc["_id"] = cameraId
        cameraDoc["vms"] = "emstone"
        cameraDoc["name"] = "Camera Without ID"
        cameraDoc["created_at"] = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        mongoTemplate.save(cameraDoc, "vms_camera").awaitSingle()

        // when & then: ID 속성이 없는 카메라에 대해 호출하면 예외 발생
        val exception = assertThrows<ApiAccessException> { vms.getRtspURL(cameraId) }

        // 예외 메시지 확인
        assertTrue(
            exception.message.contains("id property not found"),
            "Exception message should indicate missing id property"
        )
    }
}