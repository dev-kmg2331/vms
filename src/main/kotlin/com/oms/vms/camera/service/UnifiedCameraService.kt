package com.oms.vms.camera.service

import com.oms.api.exception.ApiAccessException
import com.oms.logging.gson.gson
import com.oms.vms.Vms
import com.oms.vms.VmsFactory
import com.oms.vms.field_mapping.transformation.FieldTransformation
import com.oms.vms.mongo.docs.*
import com.oms.vms.mongo.repo.FieldMappingRepository
import format
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
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
    private val vmsFactory: VmsFactory,
) {
    private val log = LoggerFactory.getLogger(UnifiedCameraService::class.java)

    suspend fun getCamera(id: UUID): UnifiedCamera {
        return mongoTemplate.findOne(Query.query(Criteria.where("_id").`is`(id)), UnifiedCamera::class.java)
            .awaitSingleOrNull() ?: throw ApiAccessException(HttpStatus.NOT_FOUND, "Camera not found. ID: $id")
    }

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
    suspend fun synchronizeVmsData(vmsType: String) = coroutineScope {
        log.info("Starting synchronization process for {} VMS", vmsType)

        // 지정된 VMS 유형에 대한 매핑 규칙 가져오기
        val mappingRules = mappingRepository.getMappingRules(vmsType)

        // 채널 ID 변환 규칙 검증
        mappingRules.channelIdTransformation ?: throw ApiAccessException(
            HttpStatus.BAD_REQUEST,
            "Channel ID transformation rule must be defined first for $vmsType VMS"
        )

        try {
            // 이 VMS 유형에 대한 모든 카메라 문서 검색
            val cameraQuery = Query.query(Criteria.where("vms").`is`(vmsType))

            val cameras = mongoTemplate.find(cameraQuery, Document::class.java, VMS_CAMERA)
            val unifiedCameraIDs = mongoTemplate.find(cameraQuery, UnifiedCamera::class.java).asFlow()

            log.info("Processing {} VMS cameras with mapping rules: {}", vmsType, gson.toJson(mappingRules))

            val vms = vmsFactory.getService(mappingRules.vms)

            // 각 카메라 문서 변환 및 저장
            val unified = cameras.asFlow().map { mapToUnifiedCamera(it, mappingRules, vms) }

            log.info("Filtering un updated unified cameras")

            val newUnifiedCameraIDs = unified.map { it.id }.toSet()

            val filtered = unifiedCameraIDs.filterNot { newUnifiedCameraIDs.contains(it.id) }.toList()

            log.info("Filtered ${filtered.count()} not updated unified cameras. ${if (filtered.isEmpty()) "" else "Removing from database..."}")

            filtered.forEach {
                log.info("Removing camera ${it.id}")

                mongoTemplate.save(DeprecatedUnifiedCamera.instance(it), VMS_CAMERA_UNIFIED_DEPRECATED).awaitSingle()

                mongoTemplate.remove(Query.query(Criteria.where("_id").`is`(it.id)), VMS_CAMERA_UNIFIED)
                    .awaitSingleOrNull()
            }

            log.info("Successfully synchronized {} VMS - processed {} cameras", vmsType, unified.count())
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
        mappingRules: FieldMappingDocument,
        vms: Vms
    ): UnifiedCamera {
        // 채널 ID 추출
        val sourceField = mappingRules.channelIdTransformation!!.sourceField
        val channelID = vmsCamera[sourceField]?.toString() ?: throw ApiAccessException(
            HttpStatus.BAD_REQUEST,
            "Channel ID transformation rule source field error. Unknown field: $sourceField"
        )

        // 동일한 채널 ID를 가진 기존 카메라 확인
        val existingCamera = findExistingCamera(channelID, vms.type)

        // 기존 카메라 업데이트 처리
        if (existingCamera != null) {
            return updateExistingCamera(existingCamera, vmsCamera, mappingRules, vms)
        }

        // 새 통합 카메라 문서 생성
        return createNewUnifiedCamera(vmsCamera, channelID, mappingRules, vms)
    }

    /**
     * 주어진 채널 ID로 기존 카메라 문서를 찾습니다
     *
     * @param channelID 검색할 채널 ID
     * @return 기존 카메라 문서, 또는 찾지 못한 경우 null
     */
    private suspend fun findExistingCamera(channelID: String, vmsType: String): UnifiedCamera? {
        return mongoTemplate.find(
            Query.query(
                Criteria.where("vms")
                    .`is`(vmsType)
                    .and("channel_ID")
                    .`is`(channelID)
            ),
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
        mappingRules: FieldMappingDocument,
        vms: Vms
    ): UnifiedCamera {
        // 기존 카메라를 문서로 변환
        val document = Document()
        val converter = mongoTemplate.converter

        converter.write(existingCamera, document)

        // 매핑 규칙에서 모든 변환 적용
        applyTransformations(vmsCamera, document, mappingRules.transformations)

        // 데이터베이스에서 문서 업데이트
        val updatedUnifiedCamera = converter.read(UnifiedCamera::class.java, document)
            .apply {
                rtspUrl = vms.getRtspURL(vmsCamera["_id"] as String)
                updatedAt = LocalDateTime.now().format()
            }
        return mongoTemplate.findAndReplace(
            Query.query(Criteria.where("_id").`is`(existingCamera.id)),
            updatedUnifiedCamera,
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
        mappingRules: FieldMappingDocument,
        vms: Vms
    ): UnifiedCamera {
        log.info("Creating new unified camera for channel ID: {}", channelID)

        // 초기 통합 카메라 객체 생성
        val unifiedCamera = UnifiedCamera(
            id = UUID.randomUUID().toString(),
            vms = vms.type,
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
                val doc: Document = getTransformationSource(transformation, sourceDoc)
                transformation.transformationType.apply(doc, targetDoc, transformation)
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

    private fun getTransformationSource(transformation: FieldTransformation, sourceDoc: Document): Document {
        return if (transformation.sourceIsDocument()) {
            transformation.getSourceDocument(sourceDoc)
        } else if (transformation.sourceIsList()) {
            transformation.getSourceListDocument(sourceDoc)
        } else {
            sourceDoc
        }
    }

    /**
     * 모든 통합 카메라 데이터를 조회합니다
     *
     * @return 모든 통합 카메라 객체 목록
     */
    suspend fun getAllUnifiedCameras(page: Int = 0, size: Int = 50): PageImpl<UnifiedCamera> {
        log.debug("Retrieving all unified cameras")

        val pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "channel_ID"))

        val query = Query()
            .with(pageRequest)

        val pageResult = mongoTemplate.find(query, UnifiedCamera::class.java, VMS_CAMERA_UNIFIED).asFlow().toList()

        val total = mongoTemplate.count(Query(), UnifiedCamera::class.java).awaitSingle()

        return PageImpl(pageResult, pageRequest, total)
    }

    /**
     * 특정 VMS 유형에 대한 통합 카메라 데이터를 조회합니다
     *
     * @param vmsType 필터링할 VMS 유형
     * @return 지정된 VMS 유형에 대한 통합 카메라 객체 목록
     */
    suspend fun getUnifiedCamerasByVmsType(page: Int, size: Int, vmsType: String): PageImpl<UnifiedCamera> {
        log.debug("Retrieving unified cameras for VMS type: {}", vmsType)

        val pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "channel_ID"))

        val vmsQuery = Query.query(Criteria.where("vms").`is`(vmsType))
        val pageQuery = vmsQuery.with(pageRequest)

        val pageResult = mongoTemplate.find(pageQuery, UnifiedCamera::class.java, VMS_CAMERA_UNIFIED).asFlow().toList()

        val total = mongoTemplate.count(vmsQuery, UnifiedCamera::class.java).awaitSingle()

        return PageImpl(pageResult, pageRequest, total)
    }
}