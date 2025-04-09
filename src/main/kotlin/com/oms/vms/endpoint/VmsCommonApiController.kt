package com.oms.vms.endpoint

import com.oms.api.exception.ApiAccessException
import com.oms.api.response.CommonApiResponse
import com.oms.api.response.ResponseUtil
import com.oms.vms.VmsFactory
import com.oms.vms.VmsType
import com.oms.vms.mongo.docs.VmsAdditionalInfo
import com.oms.vms.mongo.docs.VmsConfig
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * VMS 동기화 컨트롤러
 * 다양한 VMS 시스템의 카메라 데이터 동기화를 위한 API 제공
 */
@RestController
@RequestMapping("/api/v2/vms")
@Tag(name = "VMS 일반 API", description = "일반적인 VMS 관련 API 제공")
class VmsCommonApiController(private val vmsFactory: VmsFactory) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * 특정 VMS 유형의 설정 정보 조회
     */
    @GetMapping("/config/{vmsType}")
    @Operation(
        summary = "VMS 설정 정보 조회",
        description = "지정된 VMS 유형의 설정 정보를 반환합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "VMS 설정 정보 조회 성공",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = VmsConfig::class))]
            ),
            ApiResponse(responseCode = "400", description = "잘못된 요청 또는 설정 정보 없음"),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun getVmsConfig(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String
    ): ResponseEntity<*> {
        return try {
            val vms = vmsFactory.getService(vmsType)
            val config = vms.getVmsConfig()
            log.info("Successfully retrieved configuration for {} VMS", vmsType)
            ResponseUtil.success(config)
        } catch (e: Exception) {
            log.error("Failed to retrieve configuration for {} VMS: {}", vmsType, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving VMS configuration")
        }
    }

    /**
     * 모든 VMS 유형의 설정 정보 조회
     */
    @GetMapping("/configs")
    @Operation(
        summary = "모든 VMS 설정 정보 조회",
        description = "시스템에 등록된 모든 VMS 유형의 설정 정보를 반환합니다. 보안상 비밀번호는 마스킹됩니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "모든 VMS 설정 정보 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = VmsConfig::class))
                )]
            ),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun getAllVmsConfigs(): ResponseEntity<*> {
        return try {
            val configList = coroutineScope {
                vmsFactory.getAllServices().map { vms ->
                    async {
                        try {
                            vms.getVmsConfig(includeInactive = true)
                        } catch (e: ApiAccessException) {
                            // 개별 VMS 설정 조회 실패 시 null 반환하고 계속 진행
                            log.warn("Failed to retrieve configuration for {} VMS: {}", vms.type, e.message)
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            log.info("Successfully retrieved configurations for all VMS types")
            ResponseUtil.success(configList)
        } catch (e: Exception) {
            log.error("Failed to retrieve configurations for all VMS types: {}", e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving VMS configurations")
        }
    }

    /**
     * 특정 VMS 유형의 설정 정보 업데이트
     */
    @PostMapping("/config/{vmsType}")
    @Operation(
        summary = "VMS 설정 정보 저장",
        description = "지정된 VMS 유형의 설정 정보를 업데이트합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "VMS 설정 정보 저장 성공",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = VmsConfig::class))]
            ),
            ApiResponse(responseCode = "400", description = "잘못된 요청 또는 설정 정보 없음"),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun saveVmsConfig(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String,
        @RequestBody vmsConfigRequest: VmsConfigUpdateRequest
    ): ResponseEntity<*> {
        return try {
            val serviceName = VmsType.findByServiceName(vmsType).serviceName
            val vms = vmsFactory.getService(serviceName)

            val updatedConfig = vms.saveVmsConfig(vmsConfigRequest)
            log.info("Successfully updated configuration for {} VMS", vmsType)

            ResponseUtil.success(updatedConfig)
        } catch (e: Exception) {
            log.error("Failed to update configuration for {} VMS: {}", vmsType, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "Error updating VMS configuration")
        }
    }

    /**
     * 특정 VMS 유형의 활성화 상태 변경
     */
    @PutMapping("/config/{vmsType}/active")
    @Operation(
        summary = "VMS 활성화 상태 변경",
        description = "지정된 VMS 유형의 활성화 상태를 변경합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "VMS 활성화 상태 변경 성공",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = VmsConfig::class))]
            ),
            ApiResponse(responseCode = "400", description = "잘못된 요청 또는 설정 정보 없음"),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun setVmsConfigActive(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String,
        @Parameter(
            description = "활성화 여부 (true: 활성화, false: 비활성화)",
            required = true
        )
        @RequestParam active: Boolean
    ): ResponseEntity<*> {
        return try {
            val vms = vmsFactory.getService(vmsType)
            val updatedConfig = vms.setVmsConfigActive(active)

            log.info("Successfully {} {} VMS", if (active) "activated" else "deactivated", vmsType)

            // 비밀번호 마스킹하여 반환
            ResponseUtil.success(updatedConfig)
        } catch (e: Exception) {
            log.error("Failed to change activation status for {} VMS: {}", vmsType, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "Error changing VMS activation status")
        }
    }

    /**
     * 지원되는 모든 VMS 유형 목록 조회
     */
    @GetMapping("/types")
    @Operation(
        summary = "지원 VMS 유형 조회",
        description = "시스템에서 지원하는 모든 VMS 유형 목록을 반환합니다."
    )
    @ApiResponse(responseCode = "200", description = "VMS 유형 목록 조회 성공")
    fun getSupportedVmsTypes(): ResponseEntity<CommonApiResponse> {
        val services = vmsFactory.getAllServices().map { it.type }
        return ResponseUtil.success(services)
    }
}

/**
 * VMS 설정 업데이트 요청 DTO
 */
data class VmsConfigUpdateRequest(
    val username: String,
    val password: String?, // null인 경우 기존 비밀번호 유지
    val ip: String,
    val port: String,
    val additionalInfo: List<VmsAdditionalInfo>
)