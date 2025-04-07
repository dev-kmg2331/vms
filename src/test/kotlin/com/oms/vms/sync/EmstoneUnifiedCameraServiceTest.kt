package com.oms.vms.sync

import com.oms.vms.Vms
import com.oms.vms.config.VmsConfig
import com.oms.vms.emstone.EmstoneNvr
import com.oms.vms.mongo.docs.UnifiedCamera
import com.oms.vms.persistence.mongo.repository.ReactiveMongoRepo
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.test.runTest
import org.bson.Document
import org.junit.jupiter.api.Assertions.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClient
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test

@SpringBootTest
@ActiveProfiles("test")
class EmstoneUnifiedCameraServiceTest {

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @Autowired
    private lateinit var service: UnifiedCameraService

    @Autowired
    private lateinit var reactiveMongoRepo: ReactiveMongoRepo

    private lateinit var vms: Vms

    private val log = LoggerFactory.getLogger(this::class.java)

    @BeforeTest
    fun setup() {
        val vmsConfig = VmsConfig(
            id = "admin",
            password = "oms20190211",
            ip = "192.168.182.200",
            port = "80"
        )

        val authToken =
            Base64.getEncoder().encodeToString("${vmsConfig.id}:${vmsConfig.password}".toByteArray(Charsets.UTF_8))

        val webClient = WebClient.builder()
            .baseUrl("http://${vmsConfig.ip}:${vmsConfig.port}")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $authToken")
            .build()

        vms = EmstoneNvr(webClient, vmsConfig, reactiveMongoRepo)

        mongoTemplate.remove(
            Query.query(Criteria.where("vmsType").`is`("emstone")),
            "vms_field_mappings"
        ).block()
    }

    @Test
    fun `synchronize emstone vms data as unified camera data`() = runTest {
        // given
        vms.synchronize()

        // 먼저 VMS 데이터를 확인 (테스트 환경 설정에서 이미 생성됨)
        val vmsCamera = mongoTemplate.findOne(
            Query.query(Criteria.where("vms.type").`is`("emstone")),
            Document::class.java,
            "vms_camera"
        ).block()

        assertNotNull(vmsCamera, "VMS camera data should exist")
        assertEquals(
            "emstone",
            vmsCamera?.get("vms", Document::class.java)?.get("type"),
            "VMS type should be 'emstone'"
        )

        setupEmstoneTransformations()

        service.synchronizeVmsData("emstone")

        // then
        // 통합 카메라 데이터가 성공적으로 생성되었는지 확인
        val unifiedCameras = mongoTemplate.find(
            Query.query(Criteria.where("vmsType").`is`("emstone")),
            UnifiedCamera::class.java,
            "vms_camera_unified"
        ).asFlow().toList()

        assertFalse(unifiedCameras.isEmpty(), "Unified camera data should be created")
        assertTrue(unifiedCameras.size >= 2, "At least 2 unified camera data should be created")

        unifiedCameras.forEach {
            val document = mongoTemplate.find(
                Query.query(
                    Criteria.where("vms.type").`is`("emstone")
                        .andOperator(
                            Criteria.where("id").`is`(it.channelIndex)
                        )
                ),
                Document::class.java,
                "vms_camera"
            ).awaitSingle()

            assertEquals(document.getString("name"), it.name, "Camera name should be correctly mapped")
            assertEquals(document.getString("address"), it.channelName, "Channel name should be correctly mapped")
            assertEquals(document.getBoolean("has_ptz"), it.supportsPTZ, "PTZ support should be correctly mapped")
            assertEquals(document.getBoolean("has_signal"), it.isEnabled, "Enabled status should be correctly mapped")
            assertNotNull(it.rtspUrl, "RTSP URL should exist")

            log.info(
                "Camera ${it.channelIndex} detailed validation: name=${it.name}, channel=${it.channelName}, " +
                        "channelIndex=${it.channelIndex}, PTZ=${it.supportsPTZ}, enabled=${it.isEnabled}"
            )
        }
    }

    /**
     * Emstone VMS에 맞는 변환 규칙 설정
     * 실제 데이터 구조에 맞춰 변환 규칙 정의
     */
    private suspend fun setupEmstoneTransformations() {
        // PTZ 지원 여부 변환
        val ptzTransformation = FieldTransformation(
            sourceField = "has_ptz",
            targetField = "supportsPTZ",
            transformationType = TransformationType.BOOLEAN_CONVERSION
        )

        // 이름 변환
        val nameTransformation = FieldTransformation(
            sourceField = "name",
            targetField = "name",
            transformationType = TransformationType.DEFAULT_CONVERSION
        )

        // 채널 이름 변환 (address 필드를 채널 이름으로 사용)
        val channelNameTransformation = FieldTransformation(
            sourceField = "address",
            targetField = "channelName",
            transformationType = TransformationType.DEFAULT_CONVERSION
        )

        // 채널 번호 변환
        val channelNumberTransformation = FieldTransformation(
            sourceField = "id",
            targetField = "channelIndex",
            transformationType = TransformationType.NUMBER_CONVERSION
        )

        // 활성화 상태 변환
        val enabledTransformation = FieldTransformation(
            sourceField = "connected",
            targetField = "isEnabled",
            transformationType = TransformationType.BOOLEAN_CONVERSION
        )

        // 매핑 규칙 등록
        service.registerTransformation("emstone", ptzTransformation)
        service.registerTransformation("emstone", nameTransformation)
        service.registerTransformation("emstone", channelNameTransformation)
        service.registerTransformation("emstone", channelNumberTransformation)
        service.registerTransformation("emstone", enabledTransformation)

        // 원본 ID 매핑 추가
        service.registerTransformation(
            "emstone", FieldTransformation(
                sourceField = "id",
                targetField = "originalId",
                transformationType = TransformationType.STRING_FORMAT,
                parameters = mapOf("format" to "%s")
            )
        )

        // 생성 시간 매핑 추가
        service.registerTransformation(
            "emstone", FieldTransformation(
                sourceField = "created_at",
                targetField = "createdAt",
                transformationType = TransformationType.DATE_FORMAT,
                parameters = mapOf(
                    "sourceFormat" to "yyyy-MM-dd HH:mm:ss",
                    "targetFormat" to "yyyy-MM-dd HH:mm:ss"
                )
            )
        )
    }
}

