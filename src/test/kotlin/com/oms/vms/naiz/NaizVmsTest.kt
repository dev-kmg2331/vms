package com.oms.vms.naiz

import com.github.f4b6a3.tsid.TsidCreator
import com.google.gson.JsonObject
import com.mongodb.client.result.DeleteResult
import com.oms.logging.gson.gson
import com.oms.vms.WithMongoDBTestContainer
import com.oms.vms.endpoint.VmsConfigUpdateRequest
import com.oms.vms.manufacturers.naiz.NaizVms
import com.oms.vms.mongo.docs.*
import com.oms.vms.service.VmsSynchronizeService
import com.oms.vms.testcontainers.MongoDBContainerDelegate
import com.oms.vms.testcontainers.MongoDBContainerDelegate.mongoDBContainer
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.bson.Document
import org.json.XML
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.exists
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@SpringBootTest
@ExtendWith(MockKExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
@ActiveProfiles("test")
class NaizVmsTest: WithMongoDBTestContainer {

    @Autowired
    private lateinit var vms: NaizVms

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @MockK
    private lateinit var mongoTemplateMock: ReactiveMongoTemplate

    @Autowired
    private lateinit var vmsSynchronizeService: VmsSynchronizeService

    @MockK
    private lateinit var vmsSynchronizeServiceMock: VmsSynchronizeService

    @MockK
    private lateinit var mockWebClient: WebClient

    private val log = LoggerFactory.getLogger(this::class.java)
    private val testResponseFile = "src/test/resources/naiz-test-response.xml"
    private lateinit var rawXMLResponse: String

    private lateinit var getSpec: WebClient.RequestHeadersUriSpec<*>
    private lateinit var responseSpec: WebClient.ResponseSpec

    @BeforeEach
    fun setup() {
        log.info("Setting up test environment for tests")

        // VMS WebClient mocking 설정
        getSpec = mockk<WebClient.RequestHeadersUriSpec<*>>()
        responseSpec = mockk<WebClient.ResponseSpec>()

        every { mockWebClient.get() } returns getSpec
        every { getSpec.uri(any<String>()) } returns getSpec
        every { getSpec.headers(any()) } returns getSpec
        every { getSpec.retrieve() } returns responseSpec
        every { responseSpec.onStatus(any(), any()) } returns responseSpec

        rawXMLResponse = File(testResponseFile).readText()

        every { responseSpec.bodyToMono(String::class.java) } returns Mono.just(rawXMLResponse)

        vmsSynchronizeServiceMock = VmsSynchronizeService(mongoTemplateMock)


        // vms config mock 조회 설정.
        every {
            mongoTemplateMock.find(
                Query.query(Criteria.where("vms").`is`("naiz")),
                VmsConfig::class.java,
                VMS_CONFIG
            )
        } returns Flux.just(
            VmsConfig(
                username = "admin",
                password = "admin",
                ip = "naiz.re.kr",
                port = 8002,
                vms = "naiz"
            )
        )

        // vms field analysis 유무 확인.
        every {
            mongoTemplateMock.exists(
                Query.query(Criteria.where("vms").`is`("naiz")),
                VMS_FIELD_ANALYSIS
            )
        } returns Mono.just(true)

        val mockDocument = Document()
        mockDocument["_id"] = TsidCreator.getTsid1024().toString()

        every { mongoTemplateMock.insert(any<Document>(), any<String>()) } returns Mono.just(mockDocument)

        every { mongoTemplateMock.save(any<Document>(), VMS_FIELD_ANALYSIS) } returns Mono.empty()

        val deleteResult = mockk<DeleteResult>()

        every { mongoTemplateMock.remove(any<Query>(), any<String>()) } returns Mono.just(deleteResult)

        // 테스트용 vms 생성
        vms = NaizVms(
            mongoTemplate = mongoTemplate,
            vmsSynchronizeService = vmsSynchronizeService
        )
    }

    @AfterEach
    fun cleanup() {
        // 테스트 후 컬렉션 정리 (주석 처리되어 있음 - 필요시 활성화)
//         mongoTemplate.dropCollection("vms_raw_json").block()
//         mongoTemplate.dropCollection("vms_camera").block()
//         mongoTemplate.dropCollection("vms_camera_keys").block()
    }

    @Test
    @DisplayName("Mock 저장소를 사용한 동기화 테스트")
    fun `synchronize test with mock repository`(): Unit = runTest {
        // when: 모킹된 저장소와 WebClient로 동기화 실행
        vms = NaizVms(mongoTemplate = mongoTemplateMock, vmsSynchronizeService = vmsSynchronizeServiceMock)
            .apply { webClient = mockWebClient }

        vms.synchronize()

        // 각 삽입된 문서 내용 검증 (옵션)
        val documentSlot = slot<Document>()
        log.info("verifying mock mongo template document")
        verify { mongoTemplateMock.insert(capture(documentSlot), eq("vms_raw_json")) }

        val capturedDoc = documentSlot.captured

        log.info("document : $capturedDoc")

        assertNotNull(capturedDoc)
        assertTrue(capturedDoc.containsKey("vms"))

        val vmsInfo = capturedDoc["vms"] as String
        assertEquals("naiz", vmsInfo)
    }

    @Test
    @DisplayName("Naiz VMS에서 카메라 데이터를 가져와 MongoDB에 저장하는지 테스트")
    fun `synchronize should fetch camera data from Naiz API and store in MongoDB`(): Unit = runTest {
        vms.saveVmsConfig(
            VmsConfigUpdateRequest(
                username = "admin",
                password = "admin",
                ip = "naiz.re.kr",
                port = 8002,
            )
        )

        // when: API 호출 및 동기화 실행
        vms.synchronize()

        // then: MongoDB에 저장된 데이터 확인

        // 1. vms_raw_json 컬렉션 데이터 확인
        val rawJsonQuery = Query.query(Criteria.where("vms").`is`("naiz"))
        val rawJsonDoc = mongoTemplate.findOne(rawJsonQuery, Document::class.java, VMS_RAW_JSON).awaitSingle()

        assertNotNull(rawJsonDoc, "Raw JSON document should be saved")
        assertEquals(
            "naiz", rawJsonDoc?.get("vms", String::class.java),
            "Document should have correct VMS type"
        )
        assertTrue(
            rawJsonDoc?.get("request_uri", String::class.java)?.contains("/camera/list.cgi") ?: false,
            "Document should have correct API URI"
        )

        // 2. vms_camera 컬렉션 데이터 확인
        val cameraQuery = Query.query(Criteria.where("vms").`is`("naiz"))
        val cameraCount = mongoTemplate.count(cameraQuery, VMS_CAMERA).block() ?: 0

        // 카메라 데이터가 있는지 확인
        assertTrue(cameraCount > 0, "Camera documents should be saved")

        // 3. 카메라 데이터의 필드 확인
        val cameras =
            mongoTemplate.find(cameraQuery, Document::class.java, "vms_camera").collectList().block() ?: emptyList()

        for (camera in cameras) {
            log.info(camera.toJson())
            assertNotNull(camera.getString("_id"), "Camera should have ID")
            assertNotNull(camera.getString("created_at"), "Camera should have creation timestamp")

            val vmsDoc = camera.get("vms", String::class.java)
            assertNotNull(vmsDoc, "Camera should have VMS information")
            assertEquals("naiz", vmsDoc, "Camera should have correct VMS type")

            // Naiz VMS의 카메라 데이터가 필요한 필드를 포함하는지 확인
            // 실제 응답 구조에 따라 조정 필요
            if (camera.containsKey("ID")) {
                assertNotNull(camera.getString("ID"), "Camera should have ID field")
            }

            if (camera.containsKey("Name")) {
                assertNotNull(camera["Name"], "Camera should have Name field")
            }
        }

        log.info("테스트 성공: ${cameras.size}개의 카메라 정보가 저장되었습니다.")

        cameras.forEachIndexed { index, camera ->
            log.info("카메라 ${index + 1} 정보: ${camera.toJson()}")
        }
    }

    @Test
    @DisplayName("XML 응답이 올바르게 JSON으로 변환되는지 테스트")
    fun `synchronize should handle XML to JSON conversion correctly`() = runTest {
        // when: 동기화 실행
        vms.synchronize()

        // then: 응답이 올바르게 파싱되었는지 확인
        val rawJsonDoc = mongoTemplate.findOne(
            Query.query(Criteria.where("vms").`is`("naiz")),
            Document::class.java,
            VMS_RAW_JSON
        ).block()

        assertNotNull(rawJsonDoc, "Raw JSON document should be saved")

        // JSON 문서가 올바른 형식인지 확인
        val jsonString = rawJsonDoc?.toJson() ?: ""
        try {
            val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)
            // Camera 필드가 있는지 확인
            assertTrue(jsonObject.has("raw_data"), "Document should have raw-data field")

            val rawData = jsonObject.getAsJsonObject("raw_data")
            assertTrue(rawData.has("Camera"), "Camera field should exist in the parsed JSON")

            // CameraList가 있는지 확인
            val camera = rawData.getAsJsonObject("Camera")
            assertTrue(camera.has("CameraList"), "CameraList field should exist")
        } catch (e: Exception) {
            fail("Failed to parse JSON: ${e.message}")
        }
    }

    @Test
    @DisplayName("저장된 문서에 올바른 타임스탬프 형식이 있는지 테스트")
    fun `synchronize should set correct timestamp format`() = runTest {
        // when: 동기화 실행
        vms.synchronize()

        // then: 저장된 문서의 타임스탬프 형식 확인
        val documents = mongoTemplate.find(
            Query.query(Criteria.where("vms").`is`("naiz")),
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

//    @Test
//    @DisplayName("카메라 키 목록이 올바르게 저장되는지 테스트")
//    fun `synchronize should save camera keys correctly`() = runTest {
//        // when: 동기화 실행
//        vms.synchronize()
//
//        // then: 카메라 키 목록 확인
//        val keysDoc = mongoTemplate.findOne(
//            Query.query(Criteria.where("vms").`is`("naiz")),
//            Document::class.java,
//            "vms_camera_keys"
//        ).awaitSingle()
//
//        assertNotNull(keysDoc, "Camera keys document should be saved")
//        assertTrue(keysDoc!!.containsKey("keys"), "Document should have keys field")
//
//        val keys = keysDoc["keys"] as Map<*, *>
//        assertFalse(keys.isEmpty(), "Keys list should not be empty")
//
//        // 일반적인 Naiz 카메라 필드 확인
//        val xml = XML.toJSONObject(rawXMLResponse).toMap()
//
//        val jsonElement = vms.extractActualData(gson.toJson(xml)).first()
//
//        val expectedKeys = vmsSynchronizeService.analyzeUnifiedFieldStructure()
//
//        log.info("required keys: $expectedKeys")
//
//        fun validate(k1: Any?, v1: Any?, keys: Map<*, *>) {
//            log.info("required keys: $k1, values: $v1, map: $keys")
//            assertTrue(k1 != null, "A Key should not be null")
//            assertTrue(v1 != null, "A Value should not be null")
//
//            when (v1) {
//                is String -> {
//                    assertTrue(keys.contains(v1), "Keys should contain $v1")
//                }
//
//                is Map<*, *> -> {
//                    v1.forEach { (k2, v2) -> validate(k2, v2, v1) }
//                }
//
//                is List<*> -> {
//                    assertTrue(v1[0] is Map<*, *>, "list value must contain a single map of keys.")
//                    (v1[0] as Map<*, *>).forEach { (k2, v2) -> validate(k2, v2, v1[0] as Map<*, *>) }
//                }
//            }
//        }
//
//        expectedKeys.forEach { (k, v) -> validate(k, v, keys) }
//    }
}