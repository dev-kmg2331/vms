package com.oms.vms

import com.oms.vms.mongo.docs.VmsMappingDocument
import com.oms.vms.mongo.repo.FieldMappingRepository
import format
import org.slf4j.LoggerFactory
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
        log.debug("$vmsType 유형의 매핑 규칙 조회")
        return fieldMappingRepository.getMappingRules(vmsType)
    }

    /**
     * 모든 VMS 유형의 매핑 규칙 조회
     */
    suspend fun getAllMappingRules(): List<VmsMappingDocument> {
        log.debug("모든 VMS 유형의 매핑 규칙 조회")
        return fieldMappingRepository.getAllMappingRules()
    }

    /**
     * 필드 변환 규칙 추가
     */
    suspend fun addTransformation(
        vmsType: String,
        transformation: FieldTransformation
    ): VmsMappingDocument {
        log.info("$vmsType 유형의 변환 규칙 추가: ${transformation.sourceField} -> ${transformation.targetField} [${transformation.transformationType}]")
        
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
        log.info("$vmsType 유형의 변환 규칙 삭제: 인덱스 $transformationIndex")
        
        val mappingRules = fieldMappingRepository.getMappingRules(vmsType)
        
        if (transformationIndex < 0 || transformationIndex >= mappingRules.transformations.size) {
            throw IllegalArgumentException("유효하지 않은 변환 규칙 인덱스: $transformationIndex")
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
    suspend fun deleteMappingRules(vmsType: String): Boolean {
        log.info("$vmsType 유형의 매핑 규칙 전체 삭제")
        return fieldMappingRepository.deleteMappingRules(vmsType)
    }

    /**
     * 매핑 규칙 초기화
     * 특정 VMS 유형의 매핑을 초기 상태로 리셋
     */
    suspend fun resetMappingRules(vmsType: String): VmsMappingDocument {
        log.info("$vmsType 유형의 매핑 규칙 초기화")
        
        // 기존 규칙 삭제
        fieldMappingRepository.deleteMappingRules(vmsType)
        
        // 새 기본 규칙 생성
        return fieldMappingRepository.getMappingRules(vmsType)
    }
}