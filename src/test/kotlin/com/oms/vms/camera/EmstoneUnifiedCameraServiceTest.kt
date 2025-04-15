package com.oms.vms.camera

import com.oms.vms.Vms
import com.oms.vms.camera.service.UnifiedCameraService
import com.oms.vms.field_mapping.transformation.ChannelIdTransFormation
import com.oms.vms.field_mapping.transformation.FieldTransformation
import com.oms.vms.field_mapping.transformation.TransformationType
import com.oms.vms.manufacturers.emstone.EmstoneNvr
import com.oms.vms.mongo.docs.*
import com.oms.vms.mongo.repo.FieldMappingRepository
import com.oms.vms.service.VmsSynchronizeService
import format
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
import java.time.LocalDateTime
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test

@SpringBootTest
@ActiveProfiles("test")
class EmstoneUnifiedCameraServiceTest {

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @Autowired
    private lateinit var vmsSynchronizeService: VmsSynchronizeService

    @Autowired
    private lateinit var mappingRepository: FieldMappingRepository

    @Autowired
    private lateinit var service: UnifiedCameraService

    private lateinit var vms: Vms

    private val log = LoggerFactory.getLogger(this::class.java)

    @BeforeTest
    fun setup() {
        vms = EmstoneNvr(mongoTemplate, vmsSynchronizeService)

        listOf(
//            VMS_CAMERA_UNIFIED,
            VMS_CAMERA,
            VMS_RAW_JSON,
            VMS_FIELD_MAPPINGS
        ).forEach {
            mongoTemplate.remove(
                Query.query(Criteria.where("vms").`is`("emstone")),
                it
            ).block()
        }
    }

    @Test
    fun `synchronize emstone vms data as unified camera data`() = runTest {
        // given
        vms.synchronize()

        // 먼저 VMS 데이터를 확인 (테스트 환경 설정에서 이미 생성됨)
        val vmsCamera = mongoTemplate.findOne(
            Query.query(Criteria.where("vms").`is`("emstone")),
            Document::class.java,
            "vms_camera"
        ).block()

        assertNotNull(vmsCamera, "VMS camera data should exist")
        assertEquals(
            "emstone",
            vmsCamera?.get("vms", String::class.java),
            "VMS type should be 'emstone'"
        )

        setupEmstoneTransformations()

        service.synchronizeVmsData("emstone")

        // then
        // 통합 카메라 데이터가 성공적으로 생성되었는지 확인
        val unifiedCameras = mongoTemplate.find(
            Query.query(Criteria.where("vms").`is`("emstone")),
            UnifiedCamera::class.java,
            "vms_camera_unified"
        ).asFlow().toList()

        assertFalse(unifiedCameras.isEmpty(), "Unified camera data should be created")
        assertTrue(unifiedCameras.size >= 2, "At least 2 unified camera data should be created")

        unifiedCameras.forEach {
            val document = mongoTemplate.find(
                Query.query(
                    Criteria.where("id").`is`(it.channelID.toInt())
                ),
                Document::class.java,
                VMS_CAMERA
            ).awaitSingle()

            assertEquals(document.getString("name"), it.name, "Camera name should be correctly mapped")
            assertEquals(document.getString("address"), it.channelName, "Channel name should be correctly mapped")
            assertEquals(document.getBoolean("has_ptz"), it.supportsPTZ, "PTZ support should be correctly mapped")
            assertEquals(document.getBoolean("has_signal"), it.isEnabled, "Enabled status should be correctly mapped")
            assertNotNull(it.rtspUrl, "RTSP URL should exist")

            log.info(
                "Camera ${it.channelID} detailed validation: name=${it.name}, channel=${it.channelName}, " +
                        "channelIndex=${it.channelID}, PTZ=${it.supportsPTZ}, enabled=${it.isEnabled}"
            )
        }
    }

    /**
     * Emstone VMS에 맞는 변환 규칙 설정
     * 실제 데이터 구조에 맞춰 변환 규칙 정의
     */
    private suspend fun setupEmstoneTransformations() {
        val mappingRules = mappingRepository.getMappingRules("emstone")

        mappingRules.channelIdTransformation = ChannelIdTransFormation("id")

        mappingRepository.updateMappingRules(mappingRules)

        // PTZ 지원 여부 변환
        val ptzTransformation = FieldTransformation(
            sourceField = "has_ptz",
            targetField = "supports_PTZ",
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
            targetField = "channel_name",
            transformationType = TransformationType.DEFAULT_CONVERSION
        )

        // 채널 번호 변환
        val channelNumberTransformation = FieldTransformation(
            sourceField = "id",
            targetField = "channel_ID",
            transformationType = TransformationType.NUMBER_CONVERSION
        )

        // 활성화 상태 변환
        val enabledTransformation = FieldTransformation(
            sourceField = "connected",
            targetField = "is_enabled",
            transformationType = TransformationType.BOOLEAN_CONVERSION
        )

        // 매핑 규칙 등록
        registerTransformation("emstone", ptzTransformation)
        registerTransformation("emstone", nameTransformation)
        registerTransformation("emstone", channelNameTransformation)
        registerTransformation("emstone", channelNumberTransformation)
        registerTransformation("emstone", enabledTransformation)

        // 원본 ID 매핑 추가
        registerTransformation(
            "emstone", FieldTransformation(
                sourceField = "id",
                targetField = "original_id",
                transformationType = TransformationType.STRING_FORMAT,
                parameters = mapOf("format" to "%s")
            )
        )
    }

    private suspend fun registerTransformation(
        vmsType: String,
        transformation: FieldTransformation
    ): FieldMappingDocument {
        log.info(
            "Registering new transformation for {} VMS: {} -> {} ({})",
            vmsType,
            transformation.sourceField,
            transformation.targetField,
            transformation.transformationType
        )

        // 현재 매핑 규칙 가져오기
        val mappingRules = mappingRepository.getMappingRules(vmsType)

        // 새 변환 추가
        val updatedTransformations = mappingRules.transformations.toMutableList()
        updatedTransformations.add(transformation)

        // 업데이트된 매핑 규칙 저장
        val updatedRules = mappingRules.copy(
            transformations = updatedTransformations,
            updatedAt = LocalDateTime.now().format()
        )

        return mappingRepository.updateMappingRules(updatedRules)
    }
}
