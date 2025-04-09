package com.oms.vms.emstone

import com.google.gson.JsonObject
import com.oms.logging.gson.gson
import com.oms.vms.manufacturers.emstone.EmstoneNvr
import com.oms.vms.mongo.docs.VmsConfig
import com.oms.vms.service.VmsSynchronizeService
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.BeforeTest
import kotlin.test.Test

@SpringBootTest
@ActiveProfiles("test")
class EmstoneNvrTest {

    private lateinit var vmsConfig: VmsConfig
    private lateinit var vms: EmstoneNvr
    private lateinit var webClient: WebClient

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @Autowired
    private lateinit var vmsSynchronizeService: VmsSynchronizeService

    private val log = LoggerFactory.getLogger(this::class.java)

    @BeforeTest
    fun setup() {
        vms = EmstoneNvr(mongoTemplate, vmsSynchronizeService)
    }

    @AfterEach
    fun cleanUp() {
//        runBlocking {
//            // 테스트 후 컬렉션 정리
//            mongoTemplate.dropCollection("vms_raw_json").block()
//            mongoTemplate.dropCollection("vms_camera").block()
//        }
    }

    @Test
    fun `synchronize should fetch camera data from Emstone API and store in MongoDB`(): Unit = runTest {
        // when: API 호출 및 동기화 실행
        vms.saveVmsConfig(
            VmsConfig(
                username = "admin",
                password = "oms20190211",
                ip = "192.168.182.200",
                port = "80",
                vms = "emstone"
            )
        )

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
        // when: 동기화 실행
        vms.synchronize()

        // then: 응답이 올바르게 파싱되었는지 확인
        val rawJsonDoc = mongoTemplate.findOne(
            Query.query(Criteria.where("vms.type").`is`("emstone")),
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
            Query.query(Criteria.where("vms.type").`is`("emstone")),
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
}