package com.oms.vms.sync

import com.fasterxml.jackson.databind.ObjectMapper
import com.oms.api.exception.ApiAccessException
import com.oms.logging.gson.convert
import com.oms.logging.gson.gson
import com.oms.vms.emstone.EmstoneNvr
import com.oms.vms.mongo.docs.*
import com.oms.vms.mongo.repo.FieldMappingRepository
import format
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

/**
 * 통합 카메라 서비스 - 카메라 데이터 변환 및 통합을 위한 메인 서비스
 *
 * 이 서비스는 다음과 같은 책임을 갖습니다:
 * 1. 다양한 VMS 시스템의 카메라 데이터 동기화
 * 2. 소스별 데이터를 통합 구조로 변환
 * 3. 필드 변환을 위한 동적 매핑 규칙 적용
 * 4. 통합 카메라 스키마 관리
 * 5. 다양한 VMS 유형의 필드 구조 분석
 */
@Service
class UnifiedCameraService(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val mappingRepository: FieldMappingRepository,
    private val objectMapper: ObjectMapper,
    private val vms: EmstoneNvr
) {
    private val log = LoggerFactory.getLogger(UnifiedCameraService::class.java)

    /**
     * 특정 VMS 유형의 카메라 데이터를 통합 구조로 동기화합니다
     *
     * 이 메서드는 다음과 같은 작업을 수행합니다:
     * 1. VMS 유형에 대한 매핑 규칙 검색
     * 2. 필수 변환 규칙 검증
     * 3. 이 VMS 유형에 대한 이전 통합 데이터 정리
     * 4. 매핑 규칙을 사용하여 각 카메라 문서 처리
     * 5. 변환된 카메라 데이터를 통합 컬렉션에 저장
     *
     * @param vmsType 동기화할 VMS 유형 (예: "emstone", "naiz", "dahua")
     * @throws ApiAccessException 매핑 규칙이 유효하지 않거나 동기화 중 오류가 발생한 경우
     */
    suspend fun synchronizeVmsData(vmsType: String) {
        log.info("Starting synchronization process for {} VMS", vmsType)

        // 지정된 VMS 유형에 대한 매핑 규칙 가져오기
        val mappingRules = mappingRepository.getMappingRules(vmsType)

        // 채널 ID 변환 규칙 검증
        mappingRules.channelIdTransformation ?: throw ApiAccessException(
            HttpStatus.FORBIDDEN,
            "Channel ID transformation rule must be defined first for $vmsType VMS"
        )

        try {
            // 이 VMS 유형에 대한 모든 카메라 문서 검색
            val cameraQuery = Query.query(Criteria.where("vms").`is`(vmsType))
            val cameras = mongoTemplate.find(cameraQuery, Document::class.java, VMS_CAMERA)

            log.info("Processing {} VMS cameras with mapping rules: {}", vmsType, gson.toJson(mappingRules))

            // 각 카메라 문서 변환 및 저장
            val saved = cameras.asFlow().map { mapToUnifiedCamera(it, mappingRules) }.toList()

            log.info("Successfully synchronized {} VMS - processed {} cameras", vmsType, saved.size)
        } catch (e: Exception) {
            log.error("Failed to synchronize {} VMS data: {}", vmsType, e.message, e)
            throw ApiAccessException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                e,
                "Unexpected error while synchronizing $vmsType VMS data"
            )
        }
    }

    /**
     * 소스 카메라 문서를 통합 카메라 구조로 매핑합니다
     *
     * 이 메서드는 다음과 같은 작업을 수행합니다:
     * 1. 정의된 변환을 사용하여 채널 ID 추출
     * 2. 동일한 채널 ID를 가진 기존 카메라 문서 확인
     * 3. 통합 카메라 문서 생성 또는 업데이트
     * 4. 매핑 규칙에서 모든 필드 변환 적용
     *
     * @param vmsCamera 소스 카메라 문서
     * @param mappingRules 적용할 매핑 규칙
     * @return 저장된 통합 카메라 문서
     */
    private suspend fun mapToUnifiedCamera(
        vmsCamera: Document,
        mappingRules: VmsMappingDocument
    ): UnifiedCamera {
        // 정의된 변환을 사용하여 채널 ID 추출
        val channelID = mappingRules.channelIdTransformation!!.apply.invoke(vmsCamera)

        // 동일한 채널 ID를 가진 기존 카메라 확인
        val existingCamera = findExistingCamera(channelID)

        // 기존 카메라 업데이트 처리
        if (existingCamera != null) {
            return updateExistingCamera(existingCamera, vmsCamera, mappingRules)
        }

        // 새 통합 카메라 문서 생성
        return createNewUnifiedCamera(vmsCamera, channelID, mappingRules)
    }

    /**
     * 주어진 채널 ID로 기존 카메라 문서를 찾습니다
     *
     * @param channelID 검색할 채널 ID
     * @return 기존 카메라 문서, 또는 찾지 못한 경우 null
     */
    private suspend fun findExistingCamera(channelID: String): UnifiedCamera? {
        return mongoTemplate.find(
            Query.query(Criteria.where("channel_ID").`is`(channelID)),
            UnifiedCamera::class.java,
            VMS_CAMERA_UNIFIED
        ).awaitFirstOrNull()
    }

    /**
     * 기존 통합 카메라 문서를 새 데이터로 업데이트합니다
     *
     * @param existingCamera 기존 카메라 문서
     * @param vmsCamera 새 데이터가 있는 소스 카메라 문서
     * @param mappingRules 적용할 매핑 규칙
     * @return 업데이트된 카메라 문서
     */
    private suspend fun updateExistingCamera(
        existingCamera: UnifiedCamera,
        vmsCamera: Document,
        mappingRules: VmsMappingDocument
    ): UnifiedCamera {
        log.info("Updating existing camera: {}", existingCamera.id)

        // 기존 카메라를 문서로 변환
        val document = Document()
        val converter = mongoTemplate.converter
        converter.write(existingCamera, document)

        // 매핑 규칙에서 모든 변환 적용
        applyTransformations(vmsCamera, document, mappingRules.transformations)

        // 데이터베이스에서 문서 업데이트
        return mongoTemplate.findAndReplace(
            Query.query(Criteria.where("_id").`is`(existingCamera.id)),
            converter.read(UnifiedCamera::class.java, document).apply { updatedAt = LocalDateTime.now().format() },
            VMS_CAMERA_UNIFIED
        ).awaitFirst()
    }

    /**
     * 새 통합 카메라 문서를 생성합니다
     *
     * @param vmsCamera 소스 카메라 문서
     * @param channelID 추출된 채널 ID
     * @param mappingRules 적용할 매핑 규칙
     * @return 새 카메라 문서
     */
    private suspend fun createNewUnifiedCamera(
        vmsCamera: Document,
        channelID: String,
        mappingRules: VmsMappingDocument
    ): UnifiedCamera {
        log.info("Creating new unified camera for channel ID: {}", channelID)

        // 초기 통합 카메라 객체 생성
        val unifiedCamera = UnifiedCamera(
            id = UUID.randomUUID().toString(),
            vms = mappingRules.vms,
            channelID = channelID, // 채널 ID 직접 설정
            sourceReference = SourceReference(
                collectionName = VMS_CAMERA,
                documentId = vmsCamera.getString("_id")
            ),
            rtspUrl = vms.getRtspURL(vmsCamera.getString("_id")),
            createdAt = LocalDateTime.now().format(),
            updatedAt = LocalDateTime.now().format(),
        )

        // 문서로 변환
        val document = Document()
        val converter = mongoTemplate.converter

        converter.write(unifiedCamera, document)

        // 매핑 규칙에서 모든 변환 적용
        applyTransformations(vmsCamera, document, mappingRules.transformations)

        // 문서를 데이터베이스에 저장
        return mongoTemplate.save(converter.read(UnifiedCamera::class.java, document), VMS_CAMERA_UNIFIED).awaitFirst()
    }

    /**
     * 문서에 변환 목록을 적용합니다
     *
     * @param sourceDoc 소스 문서
     * @param targetDoc 대상 문서
     * @param transformations 적용할 변환 목록
     */
    private fun applyTransformations(
        sourceDoc: Document,
        targetDoc: Document,
        transformations: List<FieldTransformation>
    ) {
        for (transformation in transformations) {
            try {
                transformation.transformationType.apply(sourceDoc, targetDoc, transformation)
            } catch (e: Exception) {
                log.warn(
                    "Failed to apply transformation from '{}' to '{}': {}",
                    transformation.sourceField,
                    transformation.targetField,
                    e.message
                )
            }
        }
    }

    /**
     * 모든 통합 카메라 데이터를 조회합니다
     *
     * @return 모든 통합 카메라 객체 목록
     */
    suspend fun getAllUnifiedCameras(): List<UnifiedCamera> {
        log.debug("Retrieving all unified cameras")
        return mongoTemplate.findAll(UnifiedCamera::class.java, VMS_CAMERA_UNIFIED).asFlow().toList()
    }

    /**
     * 특정 VMS 유형에 대한 통합 카메라 데이터를 조회합니다
     *
     * @param vmsType 필터링할 VMS 유형
     * @return 지정된 VMS 유형에 대한 통합 카메라 객체 목록
     */
    suspend fun getUnifiedCamerasByVmsType(vmsType: String): List<UnifiedCamera> {
        log.debug("Retrieving unified cameras for VMS type: {}", vmsType)
        return mongoTemplate.find(
            Query.query(Criteria.where("vms").`is`(vmsType)),
            UnifiedCamera::class.java,
            VMS_CAMERA_UNIFIED
        ).asFlow().toList()
    }

    /**
     * VMS 유형의 필드 구조를 분석하고 저장합니다
     *
     * 이 메서드는 다음과 같은 작업을 수행합니다:
     * 1. VMS 유형에 대한 샘플 카메라 문서 검색
     * 2. 문서 구조 분석
     * 3. 분석 결과를 데이터베이스에 저장
     *
     * @param vmsType 분석할 VMS 유형
     * @return 분석 문서, 또는 샘플을 찾지 못한 경우 null
     */
    suspend fun analyzeVmsFieldStructure(vmsType: String): Document? {
        log.info("Analyzing field structure for {} VMS", vmsType)

        // VMS 유형에 대한 샘플 카메라 문서 가져오기
        val sampleCamera = mongoTemplate.findOne(
            Query.query(Criteria.where("vms").`is`(vmsType)),
            Document::class.java,
            VMS_CAMERA
        ).awaitFirstOrNull()

        if (sampleCamera == null) {
            log.warn("No sample camera found for {} VMS", vmsType)
            return null
        }

        // 분석 문서 생성
        val fieldsDocument = Document()
        fieldsDocument["_id"] = UUID.randomUUID().toString()
        fieldsDocument["vms_type"] = vmsType
        fieldsDocument["analyzed_at"] = LocalDateTime.now()
        fieldsDocument["fields"] = analyzeDocumentStructure(sampleCamera)

        // 분석 문서 저장
        log.info("Saving field structure analysis for {} VMS", vmsType)
        return mongoTemplate.save(fieldsDocument, "vms_field_analysis").awaitFirst()
    }

    /**
     * 문서의 구조를 재귀적으로 분석합니다
     *
     * 이 메서드는 다음과 같은 작업을 수행합니다:
     * 1. 다양한 유형의 값(Document, List, 프리미티브) 처리
     * 2. 필드 유형을 나타내는 구조 구축
     *
     * @param document 분석할 문서 또는 값
     * @return 문서 구조의 표현
     */
    private fun analyzeDocumentStructure(document: Any?): Any {
        return when (document) {
            is Document -> {
                // Document 객체의 경우 각 필드를 재귀적으로 분석
                val result = Document()
                document.forEach { (key, value) ->
                    result[key] = analyzeDocumentStructure(value)
                }
                result
            }
            is List<*> -> {
                // 리스트의 경우 첫 번째 요소를 샘플로 분석
                if (document.isNotEmpty()) {
                    return listOf(analyzeDocumentStructure(document[0]))
                }
                emptyList<Any>()
            }
            else -> {
                // 기본 값의 경우 타입 이름 반환
                document?.javaClass?.simpleName ?: "null"
            }
        }
    }

    /**
     * 통합 카메라 스키마를 새 필드로 확장합니다
     *
     * 이 메서드는 실제 스키마를 수정하는 대신 별도의 컬렉션에 스키마 확장 정보를 기록합니다
     * (MongoDB의 유연한 스키마 덕분에 가능).
     *
     * @param fieldName 새 필드의 이름
     * @param fieldType 새 필드의 타입
     * @return 확장이 성공적으로 기록되었으면 true
     */
    suspend fun extendUnifiedCameraSchema(fieldName: String, fieldType: String): Boolean {
        log.info("Extending unified camera schema with new field: {} ({})", fieldName, fieldType)

        // 스키마 확장 기록
        val schemaExtension = Document()
        schemaExtension["fieldName"] = fieldName
        schemaExtension["fieldType"] = fieldType
        schemaExtension["addedAt"] = LocalDateTime.now()

        mongoTemplate.save(schemaExtension, "unified_camera_schema_extensions").awaitFirst()

        log.info("Schema extension for field '{}' recorded successfully", fieldName)
        return true
    }
}