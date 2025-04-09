package com.oms.vms.camera.endpoint

import com.oms.api.response.ResponseUtil
import com.oms.vms.VmsType
import com.oms.vms.mongo.docs.UnifiedCamera
import com.oms.vms.camera.service.UnifiedCameraService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 통합 카메라 관리 컨트롤러
 * VMS 유형별 카메라 데이터의 동기화 및 통합 카메라 데이터 조회를 위한 API 제공
 */
@RestController
@RequestMapping("/api/vms/cameras")
@Tag(name = "통합 카메라 관리", description = "통합 카메라 데이터 관리 API")
class UnifiedCameraController(
    private val unifiedCameraService: UnifiedCameraService
) {
    private val log = LoggerFactory.getLogger(UnifiedCameraController::class.java)

    /**
     * 특정 VMS 유형의 카메라 데이터 동기화
     */
    @PostMapping("/sync/{vmsType}")
    @Operation(
        summary = "VMS 카메라 동기화",
        description = "지정된 VMS 유형의 카메라 데이터를 통합 데이터 구조로 동기화합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "동기화 성공"),
            ApiResponse(responseCode = "500", description = "동기화 중 오류 발생")
        ]
    )
    suspend fun synchronizeVmsData(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String
    ): ResponseEntity<*> {
        return try {
            unifiedCameraService.synchronizeVmsData(vmsType)
            ResponseUtil.success()
        } catch (e: Exception) {
            log.error("Internal error during {} VMS synchronization: {}", vmsType, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "internal error during synchronization.")
        }
    }

    /**
     * 통합된 모든 카메라 데이터 조회
     */
    @GetMapping
    @Operation(
        summary = "모든 통합 카메라 조회",
        description = "시스템에 등록된 모든 VMS 유형의 통합 카메라 데이터를 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "통합 카메라 목록 조회 성공",
        content = [Content(array = ArraySchema(schema = Schema(implementation = UnifiedCamera::class)))]
    )
    suspend fun getAllUnifiedCameras(): ResponseEntity<*> =
        ResponseUtil.success(unifiedCameraService.getAllUnifiedCameras())

    /**
     * 특정 VMS 유형의 통합 카메라 데이터 조회
     */
    @GetMapping("/type/{vmsType}")
    @Operation(
        summary = "VMS 유형별 통합 카메라 조회",
        description = "지정된 VMS 유형의 통합 카메라 데이터만 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "VMS 유형별 통합 카메라 목록 조회 성공",
        content = [Content(array = ArraySchema(schema = Schema(implementation = UnifiedCamera::class)))]
    )
    suspend fun getUnifiedCamerasByVmsType(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String
    ): ResponseEntity<*> =
        ResponseUtil.success(unifiedCameraService.getUnifiedCamerasByVmsType(vmsType))
}