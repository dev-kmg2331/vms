package com.oms.vms

import com.oms.vms.mongo.docs.VmsMappingDocument
import com.oms.vms.mongo.repo.FieldMappingRepository
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
     * 필드 매핑 규칙 추가 또는 업데이트
     */
    suspend fun addOrUpdateFieldMapping(
        vmsType: String,
        sourceField: String,
        targetField: String
    ): VmsMappingDocument {
        log.info("$vmsType 유형의 매핑 규칙 업데이트: $sourceField -> $targetField")
        
        val mappingRules = fieldMappingRepository.getMappingRules(vmsType)
        val updatedMappings = mappingRules.mappings.toMutableMap()
        updatedMappings[sourceField] = targetField
        
        val updatedRule = mappingRules.copy(
            mappings = updatedMappings,
            updatedAt = LocalDateTime.now()
        )
        
        return fieldMappingRepository.updateMappingRules(updatedRule)
    }

    /**
     * 필드 매핑 규칙 삭제
     */
    suspend fun removeFieldMapping(
        vmsType: String,
        sourceField: String
    ): VmsMappingDocument {
        log.info("$vmsType 유형의 매핑 규칙 삭제: $sourceField")
        
        val mappingRules = fieldMappingRepository.getMappingRules(vmsType)
        val updatedMappings = mappingRules.mappings.toMutableMap()
        updatedMappings.remove(sourceField)
        
        val updatedRule = mappingRules.copy(
            mappings = updatedMappings,
            updatedAt = LocalDateTime.now()
        )
        
        return fieldMappingRepository.updateMappingRules(updatedRule)
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
            updatedAt = LocalDateTime.now()
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
            updatedAt = LocalDateTime.now()
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
     * 매핑 규칙 복제
     * 기존 VMS 유형의 매핑을 새 VMS 유형으로 복사
     */
    suspend fun cloneMappingRules(sourceVmsType: String, targetVmsType: String): VmsMappingDocument {
        log.info("${sourceVmsType}의 매핑 규칙을 ${targetVmsType}으로 복제")
        
        val sourceMappingRules = fieldMappingRepository.getMappingRules(sourceVmsType)
        
        // 이미 존재하는 매핑 규칙을 가져오거나 새로 생성
        val targetMappingRules = try {
            fieldMappingRepository.getMappingRules(targetVmsType)
        } catch (e: Exception) {
            // 대상 VMS 유형에 대한 매핑이 없으면 새로 생성
            VmsMappingDocument(
                vmsType = targetVmsType,
                description = "Cloned from $sourceVmsType"
            )
        }
        
        // 소스 규칙을 대상 규칙에 복사
        val clonedRule = targetMappingRules.copy(
            mappings = sourceMappingRules.mappings,
            transformations = sourceMappingRules.transformations.map { it.copy() },
            description = "Cloned from $sourceVmsType at ${LocalDateTime.now()}",
            updatedAt = LocalDateTime.now()
        )
        
        return fieldMappingRepository.updateMappingRules(clonedRule)
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

    /**
     * 매핑 규칙 검증
     * 규칙의 일관성과 유효성 검사
     */
    suspend fun validateMappingRules(vmsType: String): Map<String, List<String>> {
        log.info("$vmsType 유형의 매핑 규칙 검증")
        
        val mappingRules = fieldMappingRepository.getMappingRules(vmsType)
        val validationResults = mutableMapOf<String, List<String>>()
        val errors = mutableListOf<String>()
        
        // 1. 중복 매핑 검사
        val targetFields = mappingRules.mappings.values
        val duplicateTargets = targetFields.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicateTargets.isNotEmpty()) {
            errors.add("중복된 대상 필드가 발견되었습니다: ${duplicateTargets.joinToString()}")
        }
        
        // 2. 변환 규칙 검증
        for ((index, transformation) in mappingRules.transformations.withIndex()) {
            val transformationErrors = mutableListOf<String>()
            
            // 소스 필드가 존재하는지 확인
            if (transformation.sourceField.isBlank()) {
                transformationErrors.add("[$index] 소스 필드가 비어 있습니다")
            }
            
            // 대상 필드가 존재하는지 확인
            if (transformation.targetField.isBlank()) {
                transformationErrors.add("[$index] 대상 필드가 비어 있습니다")
            }
            
            // 변환 유형별 파라미터 검증
            when (transformation.transformationType) {
                TransformationType.STRING_FORMAT -> {
                    if (!transformation.parameters.containsKey("format")) {
                        transformationErrors.add("[$index] STRING_FORMAT 변환에는 'format' 파라미터가 필요합니다")
                    }
                }
                TransformationType.DATE_FORMAT -> {
                    if (!transformation.parameters.containsKey("sourceFormat") || 
                        !transformation.parameters.containsKey("targetFormat")) {
                        transformationErrors.add("[$index] DATE_FORMAT 변환에는 'sourceFormat' 및 'targetFormat' 파라미터가 필요합니다")
                    }
                }
                TransformationType.CUSTOM_SCRIPT -> {
                    if (!transformation.parameters.containsKey("script")) {
                        transformationErrors.add("[$index] CUSTOM_SCRIPT 변환에는 'script' 파라미터가 필요합니다")
                    }
                }
                else -> { /* 추가 검증이 필요 없는 변환 유형 */ }
            }
            
            if (transformationErrors.isNotEmpty()) {
                validationResults["transformation_$index"] = transformationErrors
            }
        }
        
        if (errors.isNotEmpty()) {
            validationResults["general"] = errors
        }
        
        return validationResults
    }
}