package com.oms.vms.sync.endpoint

import com.oms.api.response.ResponseUtil
import com.oms.vms.VmsType
import com.oms.vms.mongo.docs.VmsMappingDocument
import com.oms.vms.sync.FieldMappingService
import com.oms.vms.sync.FieldTransformation
import com.oms.vms.sync.TransformationType
import com.oms.vms.sync.VmsSynchronizeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * VMS 필드 매핑 API 컨트롤러
 * 필드 매핑 규칙 관리를 위한 RESTful 엔드포인트 제공
 */
@RestController
@RequestMapping("/api/v2/vms/mappings")
@Tag(name = "필드 매핑 관리", description = "VMS 유형별 필드 매핑 규칙 관리 API")
class FieldMappingController(
    private val fieldMappingService: FieldMappingService,
    private val vmsSynchronizeService: VmsSynchronizeService
) {
    private val log = LoggerFactory.getLogger(FieldMappingController::class.java)

    /**
     * 특정 VMS 유형의 매핑 규칙 조회
     */
    @GetMapping("/{vmsType}")
    @Operation(
        summary = "특정 VMS 유형의 매핑 규칙 조회",
        description = "지정된 VMS 유형에 대한 필드 매핑 규칙을 반환합니다. 규칙이 없는 경우 기본 매핑을 생성합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "매핑 규칙 조회 성공",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = VmsMappingDocument::class))]
            ),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun getMappingRules(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String
    ): ResponseEntity<*> {
        return try {
            val mappingRules = fieldMappingService.getMappingRules(vmsType)
            log.info("Successfully retrieved mapping rules for {} VMS type", vmsType)
            ResponseUtil.success(mappingRules)
        } catch (e: Exception) {
            log.error("Error in finding {} VMS type mapping rules: {}", vmsType, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in finding $vmsType VMS type mapping rules")
        }
    }

    /**
     * 모든 VMS 유형의 매핑 규칙 조회
     */
    @GetMapping
    @Operation(
        summary = "모든 VMS 유형의 매핑 규칙 조회",
        description = "시스템에 등록된 모든 VMS 유형의 매핑 규칙 목록을 반환합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "매핑 규칙 목록 조회 성공",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = ArraySchema(schema = Schema(implementation = VmsMappingDocument::class))
                )]
            ),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun getAllMappingRules(): ResponseEntity<*> {
        return try {
            val allRules = fieldMappingService.getAllMappingRules()
            log.info("Successfully retrieved all mapping rules")
            ResponseUtil.success(allRules)
        } catch (e: Exception) {
            log.error("Error in finding all VMS type mapping rules: {}", e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in finding all VMS type mapping rules")
        }
    }

    /**
     * 필드 변환 규칙 추가
     */
    @PostMapping("/{vmsType}/transformation")
    @Operation(
        summary = "필드 변환 규칙 추가",
        description = "지정된 VMS 유형에 대한 새로운 필드 변환 규칙을 추가합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "변환 규칙 추가 성공",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = VmsMappingDocument::class))]
            ),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun addTransformation(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String,
        @Parameter(description = "추가할 변환 규칙 정보", required = true)
        @RequestBody transformationRequest: TransformationRequest
    ): ResponseEntity<*> {
        return try {
            val updatedRule = fieldMappingService.addTransformation(vmsType, transformationRequest)
            log.info("Successfully added transformation rule for {} VMS type: {} -> {}",
                vmsType, transformationRequest.sourceField, transformationRequest.targetField)
            ResponseUtil.success(updatedRule)
        } catch (e: Exception) {
            log.error("Error in adding {} VMS type mapping rule: {}", vmsType, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in adding $vmsType VMS type mapping rule")
        }
    }

    /**
     * 필드 변환 규칙 삭제
     */
    @DeleteMapping("/{vmsType}/transformation/{index}")
    @Operation(
        summary = "필드 변환 규칙 삭제",
        description = "지정된 VMS 유형의 특정 인덱스에 있는 필드 변환 규칙을 삭제합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "변환 규칙 삭제 성공",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = VmsMappingDocument::class))]
            ),
            ApiResponse(responseCode = "400", description = "잘못된 인덱스"),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun removeTransformation(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String,
        @Parameter(description = "삭제할 변환 규칙의 인덱스", required = true)
        @PathVariable index: Int
    ): ResponseEntity<*> {
        return try {
            val updatedRule = fieldMappingService.removeTransformation(vmsType, index)
            log.info("Successfully removed transformation rule at index {} for {} VMS type", index, vmsType)
            ResponseUtil.success(updatedRule)
        } catch (e: Exception) {
            log.error("Error in removing {} VMS type mapping rule at index {}: {}", vmsType, index, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in removing $vmsType VMS type mapping rule")
        }
    }

    /**
     * 특정 필드에 대한 변환 규칙 조회
     */
    @GetMapping("/{vmsType}/transformations/field")
    @Operation(
        summary = "특정 필드에 대한 변환 규칙 조회",
        description = "지정된 VMS 유형에서 특정 소스 필드에 대한 모든 변환 규칙을 반환합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "변환 규칙 조회 성공",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = ArraySchema(schema = Schema(implementation = FieldTransformation::class))
                )]
            ),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun getTransformationsForField(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String,
        @Parameter(description = "조회할 소스 필드 이름", required = true)
        @RequestParam(name = "field") sourceField: String
    ): ResponseEntity<*> {
        return try {
            val transformations = fieldMappingService.getTransformationsForField(vmsType, sourceField)
            log.info("Successfully retrieved transformations for field {} in {} VMS type", sourceField, vmsType)
            ResponseUtil.success(transformations)
        } catch (e: Exception) {
            log.error("Error in finding {} VMS type mapping rule for field {}: {}", vmsType, sourceField, e.message, e)
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
    @Operation(
        summary = "매핑 규칙 전체 삭제",
        description = "지정된 VMS 유형에 대한 모든 매핑 규칙을 삭제합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "매핑 규칙 삭제 성공"),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun deleteMappingRules(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String
    ): ResponseEntity<*> {
        return try {
            val deleted = fieldMappingService.deleteAllMappingRules(vmsType)
            log.info("Successfully deleted all mapping rules for {} VMS type", vmsType)
            ResponseUtil.success(object {
                val deleted = deleted
            })
        } catch (e: Exception) {
            log.error("Error in deleting {} VMS type mapping rules: {}", vmsType, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in deleting $vmsType VMS type mapping rule")
        }
    }

    /**
     * 매핑 규칙 초기화
     */
    @PostMapping("/{vmsType}/reset")
    @Operation(
        summary = "매핑 규칙 초기화",
        description = "지정된 VMS 유형의 매핑 규칙을 초기 상태로 리셋합니다. 기존 규칙을 모두 삭제하고 기본 매핑을 생성합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "매핑 규칙 초기화 성공",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = VmsMappingDocument::class))]
            ),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun resetMappingRules(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String
    ): ResponseEntity<*> {
        return try {
            val resetRule = fieldMappingService.resetMappingRules(vmsType)
            log.info("Successfully reset mapping rules for {} VMS type", vmsType)
            ResponseUtil.success(resetRule)
        } catch (e: Exception) {
            log.error("Error in resetting {} VMS type mapping rules: {}", vmsType, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in resetting $vmsType VMS type mapping rule")
        }
    }

    /**
     * 모든 VMS 유형 조회
     */
    @GetMapping("/vms-types")
    @Operation(
        summary = "지원되는 모든 VMS 유형 조회",
        description = "시스템에서 지원하는 모든 VMS 유형 목록을 반환합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "VMS 유형 목록 조회 성공",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = ArraySchema(schema = Schema(implementation = String::class))
                )]
            ),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun getAllVmsTypes(): ResponseEntity<*> {
        return try {
            val vmsTypes = VmsType.entries.map { it.serviceName }
            log.info("Successfully retrieved all supported VMS types")
            ResponseUtil.success(vmsTypes)
        } catch (e: Exception) {
            log.error("Error in finding all VMS types: {}", e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error in finding all VMS types")
        }
    }

    /**
     * VMS 유형의 필드 구조 분석
     */
    @GetMapping("/analyze/{vmsType}")
    @Operation(
        summary = "VMS 필드 구조 분석",
        description = "지정된 VMS 유형의 카메라 데이터 필드 구조를 분석합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "필드 구조 분석 성공"),
            ApiResponse(responseCode = "400", description = "필드 구조 분석 실패 - 잘못된 요청"),
            ApiResponse(responseCode = "500", description = "필드 구조 분석 중 서버 오류")
        ]
    )
    suspend fun analyzeVmsFieldStructure(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String
    ): ResponseEntity<*> {
        return try {
            return vmsSynchronizeService.analyzeVmsFieldStructure(vmsType).let { ResponseUtil.success(it) }
        } catch (e: Exception) {
            log.error("Error analyzing field structure for {} VMS type: {}", vmsType, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "unknown error in analyzing field structure")
        }
    }

}

/**
 * 변환 규칙 요청을 위한 데이터 클래스
 */
@Schema(description = "필드 변환 규칙 요청 정보")
data class TransformationRequest(
    @Schema(description = "원본 필드 이름", example = "name")
    val sourceField: String,

    @Schema(description = "대상 필드 이름", example = "camera_name")
    val targetField: String,

    @Schema(description = "변환 유형", example = "DEFAULT_CONVERSION",
        allowableValues = ["DEFAULT_CONVERSION", "BOOLEAN_CONVERSION", "NUMBER_CONVERSION", "STRING_FORMAT", "DATE_FORMAT"])
    val transformationType: TransformationType,

    @Schema(description = "추가 매개변수 (변환 유형에 따라 필요한 경우)", example = "{\"format\": \"%s\"}")
    val parameters: Map<String, String> = mapOf()
)