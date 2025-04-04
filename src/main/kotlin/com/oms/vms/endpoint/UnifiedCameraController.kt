package com.oms.vms.endpoint

import com.oms.vms.UnifiedCameraService
import com.oms.vms.mongo.docs.UnifiedCamera
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux

/**
 * 통합 카메라 관리 컨트롤러
 * VMS 유형별 카메라 데이터의 동기화 및 통합 카메라 데이터 조회를 위한 API 제공
 */
@RestController
@RequestMapping("/api/vms/cameras")
class UnifiedCameraController(
    private val unifiedCameraService: UnifiedCameraService
) {
    private val log = LoggerFactory.getLogger(UnifiedCameraController::class.java)

    /**
     * 특정 VMS 유형의 카메라 데이터 동기화
     */
    @PostMapping("/sync/{vmsType}")
    suspend fun synchronizeVmsData(@PathVariable vmsType: String): ResponseEntity<Map<String, String>> {
        return try {
            unifiedCameraService.synchronizeVmsData(vmsType)
            val response = mapOf("status" to "success", "message" to "$vmsType 유형의 카메라 데이터가 성공적으로 동기화되었습니다.")
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            log.error("$vmsType 유형의 카메라 데이터 동기화 중 오류 발생: ${e.message}", e)
            val response = mapOf("status" to "error", "message" to "동기화 중 오류 발생: ${e.message}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    /**
     * 통합된 모든 카메라 데이터 조회
     */
    @GetMapping
    fun getAllUnifiedCameras(): Flux<UnifiedCamera> {
        return unifiedCameraService.getAllUnifiedCameras()
    }

    /**
     * 특정 VMS 유형의 통합 카메라 데이터 조회
     */
    @GetMapping("/type/{vmsType}")
    fun getUnifiedCamerasByVmsType(@PathVariable vmsType: String): Flux<UnifiedCamera> {
        return unifiedCameraService.getUnifiedCamerasByVmsType(vmsType)
    }

    /**
     * VMS 유형의 필드 구조 분석
     */
    @GetMapping("/analyze/{vmsType}")
    suspend fun analyzeVmsFieldStructure(@PathVariable vmsType: String): ResponseEntity<Document?> {
        return try {
            val analysisResult = unifiedCameraService.analyzeVmsFieldStructure(vmsType)
            if (analysisResult != null) {
                ResponseEntity.ok(analysisResult)
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Document("message", "$vmsType 유형의 카메라 데이터를 찾을 수 없습니다."))
            }
        } catch (e: Exception) {
            log.error("$vmsType 유형의 필드 구조 분석 중 오류 발생: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Document("error", "필드 구조 분석 중 오류 발생: ${e.message}"))
        }
    }

    /**
     * 유니파이드 카메라 스키마 확장
     */
    @PostMapping("/schema/extend")
    suspend fun extendUnifiedCameraSchema(
        @RequestParam fieldName: String,
        @RequestParam fieldType: String
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val success = unifiedCameraService.extendUnifiedCameraSchema(fieldName, fieldType)
            val response = if (success) {
                mapOf(
                    "status" to "success",
                    "message" to "필드가 성공적으로 추가되었습니다.",
                    "fieldName" to fieldName,
                    "fieldType" to fieldType
                )
            } else {
                mapOf("status" to "error", "message" to "필드 추가에 실패했습니다.")
            }
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            log.error("스키마 확장 중 오류 발생: ${e.message}", e)
            val response = mapOf("status" to "error", "message" to "스키마 확장 중 오류 발생: ${e.message}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }
}