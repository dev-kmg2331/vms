package com.oms.vms.sync.endpoint

import com.oms.api.response.ResponseUtil
import com.oms.vms.sync.FieldMappingService
import com.oms.vms.sync.TransformationType
import com.oms.vms.mongo.docs.VmsTypeInfo
import com.oms.vms.mongo.repo.VmsTypeRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

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
    suspend fun getMappingRules(@PathVariable vmsType: String): ResponseEntity<*> {
        return try {
            val mappingRules = fieldMappingService.getMappingRules(vmsType)
            ResponseUtil.success(mappingRules)
        } catch (e: Exception) {
            log.error("error in finding $vmsType VMS type mapping rules: ${e.message}", e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in finding $vmsType VMS type mapping rules")
        }
    }

    /**
     * 모든 VMS 유형의 매핑 규칙 조회
     */
    @GetMapping
    suspend fun getAllMappingRules(): ResponseEntity<*> {
        return try {
            val allRules = fieldMappingService.getAllMappingRules()
            ResponseUtil.success(allRules)
        } catch (e: Exception) {
            log.error("error in finding all VMS type mapping rules: ${e.message}", e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in finding all VMS type mapping rules")
        }
    }

    /**
     * 필드 변환 규칙 추가
     */
    @PostMapping("/{vmsType}/transformation")
    suspend fun addTransformation(
        @PathVariable vmsType: String,
        @RequestBody transformationRequest: TransformationRequest
    ): ResponseEntity<*> {
        return try {
            val updatedRule = fieldMappingService.addTransformation(vmsType, transformationRequest)
            ResponseUtil.success(updatedRule)
        } catch (e: Exception) {
            log.error("error in adding $vmsType VMS type mapping rule: ${e.message}", e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in adding $vmsType VMS type mapping rule")
        }
    }

    /**
     * 필드 변환 규칙 삭제
     */
    @DeleteMapping("/{vmsType}/transformation/{index}")
    suspend fun removeTransformation(
        @PathVariable vmsType: String,
        @PathVariable index: Int
    ): ResponseEntity<*> {
        return try {
            val updatedRule = fieldMappingService.removeTransformation(vmsType, index)
            ResponseUtil.success(updatedRule)
        } catch (e: Exception) {
            log.error("error in removing $vmsType VMS type mapping rule: ${e.message}", e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in removing $vmsType VMS type mapping rule")
        }
    }

    /**
     * 특정 필드에 대한 변환 규칙 조회
     */
    @GetMapping("/{vmsType}/transformations/field")
    suspend fun getTransformationsForField(
        @PathVariable vmsType: String,
        @RequestParam(name = "field") sourceField: String
    ): ResponseEntity<*> {
        return try {
            val transformations = fieldMappingService.getTransformationsForField(vmsType, sourceField)
            ResponseUtil.success(transformations)
        } catch (e: Exception) {
            log.error("error in finding $vmsType VMS type mapping rule $sourceField: ${e.message}", e)
            ResponseUtil.fail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "error in finding $vmsType VMS type mapping rule $sourceField"
            )
        }
    }


    /**
     * VMS 유형에 대한 매핑 규칙 전체 삭제
     */
    @DeleteMapping("/{vmsType}")
    suspend fun deleteMappingRules(@PathVariable vmsType: String): ResponseEntity<*> {
        return try {
            val deleted = fieldMappingService.deleteMappingRules(vmsType)
            ResponseUtil.success(object {
                val deleted = deleted
            })
        } catch (e: Exception) {
            log.error("error in deleting $vmsType VMS type mapping rule: ${e.message}", e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in deleting $vmsType VMS type mapping rule")
        }
    }

    /**
     * 매핑 규칙 초기화
     */
    @PostMapping("/{vmsType}/reset")
    suspend fun resetMappingRules(@PathVariable vmsType: String): ResponseEntity<*> {
        return try {
            val resetRule = fieldMappingService.resetMappingRules(vmsType)
            ResponseUtil.success(resetRule)
        } catch (e: Exception) {
            log.error("error in resetting $vmsType VMS type mapping rule: ${e.message}", e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in resetting $vmsType VMS type mapping rule")
        }
    }

    /**
     * VMS 유형 등록
     */
    @PostMapping("/vms-type")
    suspend fun registerVmsType(@RequestBody vmsTypeInfo: VmsTypeInfo): ResponseEntity<*> {
        return try {
            val registeredType = vmsTypeRegistry.registerVmsType(vmsTypeInfo)
            ResponseUtil.success(registeredType)
        } catch (e: Exception) {
            log.error("error in registering VMS type", e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in registering VMS type")
        }
    }

    /**
     * 모든 VMS 유형 조회
     */
    @GetMapping("/vms-types")
    suspend fun getAllVmsTypes(): ResponseEntity<*> {
        return try {
            val vmsTypes = vmsTypeRegistry.getAllVmsTypes()
            ResponseUtil.success(vmsTypes)
        } catch (e: Exception) {
            log.error("error in finding all VMS types: ${e.message}", e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in finding all VMS types")
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