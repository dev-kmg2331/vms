package com.oms.vms

import com.fasterxml.jackson.databind.ObjectMapper
import com.oms.logging.gson.gson
import com.oms.vms.emstone.EmstoneNvr
import com.oms.vms.mongo.docs.SourceReference
import com.oms.vms.mongo.docs.UnifiedCamera
import com.oms.vms.mongo.docs.VmsMappingDocument
import com.oms.vms.mongo.docs.VmsTypeInfo
import com.oms.vms.mongo.repo.FieldMappingRepository
import com.oms.vms.mongo.repo.VmsTypeRegistry
import format
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.time.LocalDateTime
import java.util.*

/**
 * 카메라 데이터 매핑 서비스
 * 각 VMS 시스템의 카메라 데이터를 통합 구조로 변환하고 관리
 */
@Service
class UnifiedCameraService(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val mappingRepository: FieldMappingRepository,
    private val vmsTypeRegistry: VmsTypeRegistry,
    private val objectMapper: ObjectMapper,
    private val vms: EmstoneNvr
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val unifiedCollection = "vms_camera_unified"

    /**
     * 특정 VMS 유형의 모든 카메라 데이터를 통합 구조로 변환하고 저장
     * 동적 매핑 규칙을 사용하며 확장 가능한 방식으로 구현
     */
    suspend fun synchronizeVmsData(vmsType: String) {
        log.info("$vmsType 유형의 VMS 데이터 동기화 시작")

        try {
            // 기존 해당 VMS 유형의 통합 데이터 삭제
            mongoTemplate.remove(
                Query.query(Criteria.where("vmsType").`is`(vmsType)),
                unifiedCollection
            ).awaitFirst()

            // 매핑 규칙 가져오기
            val mappingRules = mappingRepository.getMappingRules(vmsType)

            // VMS 카메라 데이터 가져오기
            val cameraQuery = Query.query(Criteria.where("vms.type").`is`(vmsType))
            val cameras =
                mongoTemplate.find(cameraQuery, Document::class.java, "vms_camera").collectList().awaitSingle()

            log.info("$vmsType 유형의 카메라 ${cameras.size}개를 처리합니다.")

            log.info("mapping rules: ${gson.toJson(mappingRules)}")

            // 각 카메라 문서를 통합 구조로 변환하고 저장
            val saved = cameras.map { vmsCamera ->
                val unifiedCamera = mapToUnifiedCamera(vmsCamera, mappingRules)
                mongoTemplate.save(unifiedCamera, unifiedCollection).awaitFirst()
            }

            log.info("$vmsType data sync complete. $saved")
        } catch (e: Exception) {
            log.error("$vmsType 데이터 동기화 중 오류 발생: ${e.message}", e)
            throw e
        }
    }

    /**
     * 문서를 통합 카메라 객체로 매핑
     * 동적 매핑 규칙과 변환을 적용
     */
    private suspend fun mapToUnifiedCamera(
        vmsCamera: Document,
        mappingRules: VmsMappingDocument
    ): UnifiedCamera {
        // 초기 통합 카메라 객체 생성
        val unifiedCamera = UnifiedCamera(
            id = UUID.randomUUID().toString(),
            vmsType = mappingRules.vmsType,
            sourceReference = SourceReference(
                collectionName = "vms_camera",
                documentId = vmsCamera.getString("_id")
            ),
            rtspUrl = vms.getRtspURL(vmsCamera.getString("_id"))
        )

        // 통합 카메라 객체를 가변 맵으로 변환
        val unifiedCameraMap =
            objectMapper.convertValue(unifiedCamera, Map::class.java).mapKeys { it.key as String }.toMutableMap()

        // 변환 적용
        for (t in mappingRules.transformations) {
            t.transformationType.apply(vmsCamera, unifiedCameraMap, t)
        }

        // 맵을 다시 UnifiedCamera 객체로 변환
        return objectMapper.convertValue(unifiedCameraMap, UnifiedCamera::class.java)
    }

    /**
     * 통합된 모든 카메라 데이터 가져오기
     */
    fun getAllUnifiedCameras(): Flux<UnifiedCamera> {
        return mongoTemplate.findAll(UnifiedCamera::class.java, unifiedCollection)
    }

    /**
     * 특정 VMS 유형의 통합 카메라 데이터 가져오기
     */
    fun getUnifiedCamerasByVmsType(vmsType: String): Flux<UnifiedCamera> {
        return mongoTemplate.find(
            Query.query(Criteria.where("vmsType").`is`(vmsType)),
            UnifiedCamera::class.java,
            unifiedCollection
        )
    }

    /**
     * 통합 카메라로 변환할 때 필요한 데이터 변환 규칙 등록
     */
    suspend fun registerTransformation(
        vmsType: String,
        transformation: FieldTransformation
    ): VmsMappingDocument {
        // 현재 매핑 규칙 가져오기
        val mappingRules = mappingRepository.getMappingRules(vmsType)

        // 변환 규칙 추가
        val updatedTransformations = mappingRules.transformations.toMutableList()
        updatedTransformations.add(transformation)

        // 업데이트된 매핑 규칙 저장
        val updatedRules = mappingRules.copy(
            transformations = updatedTransformations,
            updatedAt = LocalDateTime.now().format()
        )

        return mappingRepository.updateMappingRules(updatedRules)
    }

    /**
     * 새로운 VMS 유형 등록 및 기본 매핑 생성
     */
    suspend fun registerNewVmsType(vmsTypeInfo: VmsTypeInfo): VmsTypeInfo {
        // VMS 유형 등록
        val registeredType = vmsTypeRegistry.registerVmsType(vmsTypeInfo)

        // 기본 매핑 생성
        mappingRepository.getMappingRules(vmsTypeInfo.code)

        return registeredType
    }

    /**
     * VMS 유형의 필드 구조 분석 및 저장
     * 새로운 VMS 유형이 추가될 때 필드 구조를 자동으로 분석
     */
    suspend fun analyzeVmsFieldStructure(vmsType: String): Document? {
        // 해당 VMS 유형의 샘플 카메라 문서 가져오기
        val sampleCamera = mongoTemplate.findOne(
            Query.query(Criteria.where("vms.type").`is`(vmsType)),
            Document::class.java,
            "vms_camera"
        ).awaitFirstOrNull()

        if (sampleCamera == null) {
            log.warn("$vmsType 유형의 샘플 카메라 데이터를 찾을 수 없습니다.")
            return null
        }

        // 필드 구조 분석
        val fieldsDocument = Document()
        fieldsDocument["_id"] = UUID.randomUUID().toString()
        fieldsDocument["vms_type"] = vmsType
        fieldsDocument["analyzed_at"] = LocalDateTime.now()
        fieldsDocument["fields"] = analyzeDocumentStructure(sampleCamera)

        // 분석 결과 저장
        return mongoTemplate.save(fieldsDocument, "vms_field_analysis").awaitFirst()
    }

    /**
     * 문서 구조 재귀적 분석
     */
    private fun analyzeDocumentStructure(document: Any?): Any {
        when (document) {
            is Document -> {
                val result = Document()
                document.forEach { (key, value) ->
                    result[key] = analyzeDocumentStructure(value)
                }
                return result
            }

            is List<*> -> {
                if (document.isNotEmpty()) {
                    return listOf(analyzeDocumentStructure(document[0]))
                }
                return emptyList<Any>()
            }

            else -> return document?.javaClass?.simpleName ?: "null"
        }
    }

    /**
     * 유니파이드 카메라 스키마 동적 업데이트
     * 새로운 필드가 필요할 때 스키마를 확장
     */
    suspend fun extendUnifiedCameraSchema(fieldName: String, fieldType: String): Boolean {
        // 이 메서드는 스키마를 확장하는 대신 MongoDB의 유연한 스키마 특성을 활용합니다.
        // 실제 필드 추가는 동적으로 이루어집니다.

        // 필드 확장 사실을 기록
        val schemaExtension = Document()
        schemaExtension["fieldName"] = fieldName
        schemaExtension["fieldType"] = fieldType
        schemaExtension["addedAt"] = LocalDateTime.now()

        mongoTemplate.save(schemaExtension, "unified_camera_schema_extensions").awaitFirst()

        return true
    }
}