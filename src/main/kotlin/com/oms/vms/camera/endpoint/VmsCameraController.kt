package com.oms.vms.camera.endpoint

import com.oms.api.response.ResponseUtil
import com.oms.vms.VmsType
import com.oms.vms.camera.service.VmsCameraService
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
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * VMS 카메라 컨트롤러
 * 원본 VMS 카메라 및 Raw JSON 데이터를 조회하기 위한 API 제공
 */
@RestController
@RequestMapping("/api/v2/vms/cameras")
@Tag(name = "VMS 카메라 관리", description = "원본 VMS 카메라 데이터 관리 API")
class VmsCameraController(
    private val vmsCameraService: VmsCameraService
) {
    private val log = LoggerFactory.getLogger(VmsCameraController::class.java)

    /**
     * 모든 VMS 카메라 데이터 조회
     */
    @GetMapping
    @Operation(
        summary = "모든 VMS 카메라 조회",
        description = "시스템에 등록된 모든 원본 VMS 카메라 데이터를 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "VMS 카메라 목록 조회 성공",
        content = [Content(array = ArraySchema(schema = Schema(implementation = Document::class)))]
    )
    suspend fun getAllCameras(): ResponseEntity<*> {
        return try {
            log.info("Request to get all VMS cameras")
            ResponseUtil.success(vmsCameraService.getAllCameras())
        } catch (e: Exception) {
            log.error("Error retrieving all VMS cameras: {}", e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve all VMS cameras")
        }
    }

    /**
     * 특정 VMS 유형의 카메라 데이터 조회
     */
    @GetMapping("/type/{vmsType}")
    @Operation(
        summary = "VMS 유형별 카메라 조회",
        description = "지정된 VMS 유형의 원본 카메라 데이터를 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "VMS 유형별 카메라 목록 조회 성공",
        content = [Content(array = ArraySchema(schema = Schema(implementation = Document::class)))]
    )
    suspend fun getCamerasByVmsType(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String
    ): ResponseEntity<*> {
        return try {
            log.info("Request to get cameras for VMS type: {}", vmsType)
            ResponseUtil.success(vmsCameraService.getCamerasByVmsType(vmsType))
        } catch (e: Exception) {
            log.error("Error retrieving cameras for VMS type {}: {}", vmsType, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve cameras for VMS type: $vmsType")
        }
    }

    /**
     * 특정 카메라 ID로 카메라 데이터 조회
     */
    @GetMapping("/{cameraId}")
    @Operation(
        summary = "카메라 ID로 조회",
        description = "지정된 ID의 원본 카메라 데이터를 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "카메라 조회 성공",
                content = [Content(schema = Schema(implementation = Document::class))]
            ),
            ApiResponse(responseCode = "404", description = "카메라를 찾을 수 없음")
        ]
    )
    suspend fun getCameraById(
        @Parameter(description = "카메라 ID", required = true)
        @PathVariable cameraId: String
    ): ResponseEntity<*> {
        return try {
            log.info("Request to get camera with ID: {}", cameraId)
            val camera = vmsCameraService.getCameraById(cameraId)
                ?: return ResponseUtil.fail(HttpStatus.NOT_FOUND, "Camera not found with ID: $cameraId")
            
            ResponseUtil.success(camera)
        } catch (e: Exception) {
            log.error("Error retrieving camera with ID {}: {}", cameraId, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve camera with ID: $cameraId")
        }
    }

    /**
     * 모든 원본 JSON 데이터 조회
     */
    @GetMapping("/raw")
    @Operation(
        summary = "모든 원본 JSON 조회",
        description = "시스템에 등록된 모든 VMS의 원본 JSON 데이터를 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "원본 JSON 목록 조회 성공",
        content = [Content(array = ArraySchema(schema = Schema(implementation = Document::class)))]
    )
    suspend fun getAllRawJson(): ResponseEntity<*> {
        return try {
            log.info("Request to get all raw JSON data")
            ResponseUtil.success(vmsCameraService.getAllRawJson())
        } catch (e: Exception) {
            log.error("Error retrieving all raw JSON data: {}", e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve all raw JSON data")
        }
    }

    /**
     * 특정 VMS 유형의 원본 JSON 데이터 조회
     */
    @GetMapping("/raw/type/{vmsType}")
    @Operation(
        summary = "VMS 유형별 원본 JSON 조회",
        description = "지정된 VMS 유형의 원본 JSON 데이터를 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "VMS 유형별 원본 JSON 목록 조회 성공",
        content = [Content(array = ArraySchema(schema = Schema(implementation = Document::class)))]
    )
    suspend fun getRawJsonByVmsType(
        @Parameter(
            description = "VMS 유형",
            required = true,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @PathVariable vmsType: String
    ): ResponseEntity<*> {
        return try {
            log.info("Request to get raw JSON data for VMS type: {}", vmsType)
            ResponseUtil.success(vmsCameraService.getRawJsonByVmsType(vmsType))
        } catch (e: Exception) {
            log.error("Error retrieving raw JSON data for VMS type {}: {}", vmsType, e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve raw JSON data for VMS type: $vmsType")
        }
    }
}