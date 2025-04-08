package com.oms.vms.sync

import com.oms.api.exception.ApiAccessException
import com.oms.vms.sync.endpoint.TransformationRequest
import com.oms.vms.mongo.docs.VmsMappingDocument
import com.oms.vms.mongo.repo.FieldMappingRepository
import format
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * VMS 필드 매핑 서비스
 * 필드 매핑 규칙 관리를 위한 비즈니스 로직 제공
 */
@Service
class FieldMappingService(
    private val fieldMappingRepository: FieldMappingRepository
) {
    private val log = LoggerFactory.getLogger(FieldMappingService::class.java)

    /**
     * 특정 VMS 유형의 매핑 규칙 조회
     */
    suspend fun getMappingRules(vmsType: String): VmsMappingDocument {
        log.info("mapping rules for VMS type $vmsType")
        return fieldMappingRepository.getMappingRules(vmsType)
    }

    /**
     * 모든 VMS 유형의 매핑 규칙 조회
     */
    suspend fun getAllMappingRules(): List<VmsMappingDocument> {
        log.info("mapping rules for all VMS types")
        return fieldMappingRepository.getAllMappingRules()
    }

    /**
     * 필드 변환 규칙 추가
     */
    suspend fun addTransformation(
        vmsType: String,
        transformationRequest: TransformationRequest
    ): VmsMappingDocument {
        val transformation = FieldTransformation(
            sourceField = transformationRequest.sourceField,
            targetField = transformationRequest.targetField,
            transformationType = transformationRequest.transformationType,
            parameters = transformationRequest.parameters
        )

        log.info("$vmsType VSM type mapping rules added: ${transformation.sourceField} -> ${transformation.targetField} [${transformation.transformationType}]")

        val mappingRules = fieldMappingRepository.getMappingRules(vmsType)
        val updatedTransformations = mappingRules.transformations.toMutableList()
        updatedTransformations.add(transformation)

        val updatedRule = mappingRules.copy(
            transformations = updatedTransformations,
            updatedAt = LocalDateTime.now().format()
        )

        return fieldMappingRepository.updateMappingRules(updatedRule)
    }

    /**
     * 필드 변환 규칙 삭제
     */
    suspend fun removeTransformation(
        vmsType: String,
        transformationIndex: Int
    ): VmsMappingDocument {
        log.info("$vmsType VMS type transformation rule delete: index $transformationIndex")

        val mappingRules = fieldMappingRepository.getMappingRules(vmsType)

        if (transformationIndex < 0 || transformationIndex >= mappingRules.transformations.size) {
            throw ApiAccessException(HttpStatus.BAD_REQUEST, "invalid transformation index: $transformationIndex")
        }

        val updatedTransformations = mappingRules.transformations.toMutableList()
        updatedTransformations.removeAt(transformationIndex)

        val updatedRule = mappingRules.copy(
            transformations = updatedTransformations,
            updatedAt = LocalDateTime.now().format()
        )

        return fieldMappingRepository.updateMappingRules(updatedRule)
    }

    /**
     * 특정 필드에 대한 변환 규칙 조회
     */
    suspend fun getTransformationsForField(
        vmsType: String,
        sourceField: String
    ): List<FieldTransformation> {
        val mappingRules = fieldMappingRepository.getMappingRules(vmsType)

        return mappingRules.transformations.filter {
            it.sourceField == sourceField
        }
    }

    /**
     * 특정 유형의 변환 규칙만 조회
     */
    suspend fun getTransformationsByType(
        vmsType: String,
        transformationType: TransformationType
    ): List<FieldTransformation> {
        val mappingRules = fieldMappingRepository.getMappingRules(vmsType)

        return mappingRules.transformations.filter {
            it.transformationType == transformationType
        }
    }

    /**
     * VMS 유형에 대한 매핑 규칙 전체 삭제
     */
    suspend fun deleteAllMappingRules(vmsType: String): Boolean {
        log.info("deleting all $vmsType VMS mapping rules")
        return fieldMappingRepository.deleteMappingRules(vmsType)
    }

    /**
     * 매핑 규칙 초기화
     * 특정 VMS 유형의 매핑을 초기 상태로 리셋
     */
    suspend fun resetMappingRules(vmsType: String): VmsMappingDocument {
        log.info("initializing $vmsType VMS mapping rules")

        // 기존 규칙 삭제
        fieldMappingRepository.deleteMappingRules(vmsType)

        // 새 기본 규칙 생성
        return fieldMappingRepository.createDefaultMapping(vmsType)
    }
}