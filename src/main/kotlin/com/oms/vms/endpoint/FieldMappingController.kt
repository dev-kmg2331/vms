package com.oms.vms.endpoint

import com.oms.vms.FieldMappingService
import com.oms.vms.FieldTransformation
import com.oms.vms.TransformationType
import com.oms.vms.mongo.docs.VmsMappingDocument
import com.oms.vms.mongo.docs.VmsTypeInfo
import com.oms.vms.mongo.repo.VmsTypeRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

/**
 * VMS 필드 매핑 API 컨트롤러
 * 필드 매핑 규칙 관리를 위한 RESTful 엔드포인트 제공
 */
@RestController
@RequestMapping("/api/vms/mappings")
class FieldMappingController(
    private val fieldMappingService: FieldMappingService,
    private val vmsTypeRegistry: VmsTypeRegistry
) {
    private val log = LoggerFactory.getLogger(FieldMappingController::class.java)

    /**
     * 특정 VMS 유형의 매핑 규칙 조회
     */
    @GetMapping("/{vmsType}")
    suspend fun getMappingRules(@PathVariable vmsType: String): ResponseEntity<VmsMappingDocument> {
        return try {
            val mappingRules = fieldMappingService.getMappingRules(vmsType)
            ResponseEntity.ok(mappingRules)
        } catch (e: Exception) {
            log.error("$vmsType 유형의 매핑 규칙 조회 중 오류 발생: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * 모든 VMS 유형의 매핑 규칙 조회
     */
    @GetMapping
    suspend fun getAllMappingRules(): ResponseEntity<List<VmsMappingDocument>> {
        return try {
            val allRules = fieldMappingService.getAllMappingRules()
            ResponseEntity.ok(allRules)
        } catch (e: Exception) {
            log.error("모든 매핑 규칙 조회 중 오류 발생: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * 필드 변환 규칙 추가
     */
    @PostMapping("/{vmsType}/transformation")
    suspend fun addTransformation(
        @PathVariable vmsType: String,
        @RequestBody transformationRequest: TransformationRequest
    ): ResponseEntity<VmsMappingDocument> {
        return try {
            val transformation = FieldTransformation(
                sourceField = transformationRequest.sourceField,
                targetField = transformationRequest.targetField,
                transformationType = transformationRequest.transformationType,
                parameters = transformationRequest.parameters
            )
            
            val updatedRule = fieldMappingService.addTransformation(vmsType, transformation)
            ResponseEntity.ok(updatedRule)
        } catch (e: Exception) {
            log.error("$vmsType 유형의 변환 규칙 추가 중 오류 발생: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * 필드 변환 규칙 삭제
     */
    @DeleteMapping("/{vmsType}/transformation/{index}")
    suspend fun removeTransformation(
        @PathVariable vmsType: String,
        @PathVariable index: Int
    ): ResponseEntity<VmsMappingDocument> {
        return try {
            val updatedRule = fieldMappingService.removeTransformation(vmsType, index)
            ResponseEntity.ok(updatedRule)
        } catch (e: Exception) {
            log.error("$vmsType 유형의 변환 규칙 제거 중 오류 발생: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * 특정 필드에 대한 변환 규칙 조회
     */
    @GetMapping("/{vmsType}/transformations/field")
    suspend fun getTransformationsForField(
        @PathVariable vmsType: String,
        @RequestParam sourceField: String
    ): ResponseEntity<List<FieldTransformation>> {
        return try {
            val transformations = fieldMappingService.getTransformationsForField(vmsType, sourceField)
            ResponseEntity.ok(transformations)
        } catch (e: Exception) {
            log.error("$vmsType 유형의 필드 변환 규칙 조회 중 오류 발생: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * 특정 유형의 변환 규칙 조회
     */
    @GetMapping("/{vmsType}/transformations/type/{transformationType}")
    suspend fun getTransformationsByType(
        @PathVariable vmsType: String,
        @PathVariable transformationType: TransformationType
    ): ResponseEntity<List<FieldTransformation>> {
        return try {
            val transformations = fieldMappingService.getTransformationsByType(vmsType, transformationType)
            ResponseEntity.ok(transformations)
        } catch (e: Exception) {
            log.error("$vmsType 유형의 $transformationType 변환 규칙 조회 중 오류 발생: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * VMS 유형에 대한 매핑 규칙 전체 삭제
     */
    @DeleteMapping("/{vmsType}")
    suspend fun deleteMappingRules(@PathVariable vmsType: String): ResponseEntity<Boolean> {
        return try {
            val deleted = fieldMappingService.deleteMappingRules(vmsType)
            ResponseEntity.ok(deleted)
        } catch (e: Exception) {
            log.error("$vmsType 유형의 매핑 규칙 삭제 중 오류 발생: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * 매핑 규칙 초기화
     */
    @PostMapping("/{vmsType}/reset")
    suspend fun resetMappingRules(@PathVariable vmsType: String): ResponseEntity<VmsMappingDocument> {
        return try {
            val resetRule = fieldMappingService.resetMappingRules(vmsType)
            ResponseEntity.ok(resetRule)
        } catch (e: Exception) {
            log.error("$vmsType 유형의 매핑 규칙 초기화 중 오류 발생: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * VMS 유형 등록
     */
    @PostMapping("/vms-type")
    suspend fun registerVmsType(@RequestBody vmsTypeInfo: VmsTypeInfo): ResponseEntity<VmsTypeInfo> {
        return try {
            val registeredType = vmsTypeRegistry.registerVmsType(vmsTypeInfo)
            ResponseEntity.ok(registeredType)
        } catch (e: Exception) {
            log.error("VMS 유형 등록 중 오류 발생: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * 모든 VMS 유형 조회
     */
    @GetMapping("/vms-types")
    suspend fun getAllVmsTypes(): ResponseEntity<List<VmsTypeInfo>> {
        return try {
            val vmsTypes = vmsTypeRegistry.getAllVmsTypes()
            ResponseEntity.ok(vmsTypes)
        } catch (e: Exception) {
            log.error("VMS 유형 목록 조회 중 오류 발생: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
}

/**
 * 변환 규칙 요청을 위한 데이터 클래스
 */
data class TransformationRequest(
    val sourceField: String,
    val targetField: String,
    val transformationType: TransformationType,
    val parameters: Map<String, String> = mapOf()
)