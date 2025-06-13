package com.oms.vms.camera.endpoint

import com.github.f4b6a3.tsid.Tsid
import com.oms.api.response.ResponseUtil
import com.oms.vms.VmsType
import com.oms.vms.camera.convertToExcel
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
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.util.UUID

/**
 * 통합 카메라 관리 컨트롤러
 * VMS 유형별 카메라 데이터의 동기화 및 통합 카메라 데이터 조회를 위한 API 제공
 */
@RestController
@RequestMapping("/api/v2/vms/unified/cameras")
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
     * 엑셀/CSV 파일을 통해 통합 카메라 일괄 등록
     */
    @PostMapping("/batch-import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "엑셀 파일을 통해 통합 카메라 일괄 등록",
        description = "엑셀(.xlsx, .xls) 또는 CSV 파일을 통해 통합 카메라 정보를 일괄 등록합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "등록 성공"),
            ApiResponse(responseCode = "400", description = "잘못된 요청 또는 파일 형식"),
            ApiResponse(responseCode = "500", description = "등록 중 오류 발생")
        ]
    )
    suspend fun importCamerasFromFile(
        @Parameter(
            description = "카메라 정보가 포함된 엑셀(.xlsx, .xls) 또는 CSV 파일",
            required = true,
            content = [Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)]
        )
        @RequestParam("file") file: MultipartFile,

        @Parameter(
            description = "VMS 유형 (옵션)",
            required = false,
            schema = Schema(type = "string", implementation = VmsType::class)
        )
        @RequestParam("vmsType", required = false) vmsType: String?
    ): ResponseEntity<*> {
        log.info("Batch import cameras requested with file: {}", file.originalFilename)

        try {
            // 파일 유효성 검사
            if (file.isEmpty) {
                log.error("File is empty")
                return ResponseUtil.fail(HttpStatus.BAD_REQUEST, "file is empty.")
            }

            unifiedCameraService.synchronizeByCameraFile(file)

            return ResponseUtil.success()
        } catch (e: IOException) {
            log.error("Failed to process file", e)
            return ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "파일 처리 중 오류가 발생했습니다.")
        }
    }

    @GetMapping(
        "/batch-import/format",
        produces = [MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "통합 카메라 일괄 등록 양식 파일 다운로드",
        description = "통합 카메라 정보 양식 엑셀 파일을 제공합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "등록 성공"),
            ApiResponse(responseCode = "400", description = "잘못된 요청 또는 파일 형식"),
            ApiResponse(responseCode = "500", description = "등록 중 오류 발생")
        ]
    )
    suspend fun getUnifiedCameraExcelFormatFile(
        @RequestParam("file_name", required = false, defaultValue = "oms-cameras.xlsx")
        fileName: String,
    ): ResponseEntity<*> {
        return try {
            val stream = UnifiedCamera::class.java.convertToExcel(fileName)

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$fileName")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(stream)
        } catch (e: Exception) {
            log.error("Failed to export camera format to Excel.", e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to export camera format to Excel.")
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
    suspend fun getAllUnifiedCameras(
        @RequestParam(value = "page", defaultValue = "0") page: Int,
        @RequestParam(value = "size", defaultValue = "50") size: Int,
        @RequestParam(value = "sort", defaultValue = "ID") sort: String,
    ): ResponseEntity<*> = ResponseUtil.success(unifiedCameraService.getAllUnifiedCameras(page, size))

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
        @PathVariable vmsType: String,
        @RequestParam(value = "page", defaultValue = "0") page: Int,
        @RequestParam(value = "size", defaultValue = "50") size: Int,
    ): ResponseEntity<*> =
        ResponseUtil.success(unifiedCameraService.getUnifiedCamerasByVmsType(page, size, vmsType))

    @GetMapping("/{id}")
    suspend fun getUnifiedCameraById(@PathVariable("id") id: String): ResponseEntity<*> {
        return try {
            val camera = unifiedCameraService.getCamera(id)
            ResponseUtil.success(camera)
        } catch (e: Exception) {
            log.error(e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "unknown exception occurred.", e)
        }
    }
}