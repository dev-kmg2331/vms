package com.oms.vms.endpoint

import com.oms.vms.Vms
import com.oms.vms.dahua.DahuaNvr
import com.oms.vms.emstone.EmstoneNvr
import com.oms.vms.naiz.NaizVms
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * VMS 동기화 컨트롤러
 * 다양한 VMS 시스템의 카메라 데이터 동기화를 위한 API 제공
 */
@RestController
@RequestMapping("/api/vms/sync")
class VmsSynchronizeController(
    private val dahuaNvr: DahuaNvr,
    private val emstoneNvr: EmstoneNvr,
    private val naizVms: NaizVms
) {
    private val log = LoggerFactory.getLogger(VmsSynchronizeController::class.java)

    // VMS 구현체 맵
    private val vmsMap = mapOf(
        "dahua" to dahuaNvr,
        "emstone" to emstoneNvr,
        "naiz" to naizVms
    )

    /**
     * 특정 VMS 시스템의 카메라 데이터 동기화
     */
    @PostMapping("/{vmsType}")
    suspend fun synchronizeVms(@PathVariable vmsType: String): ResponseEntity<Map<String, String>> {
        val vms = vmsMap[vmsType]
        
        return if (vms != null) {
            try {
                vms.synchronize()
                ResponseEntity.ok(mapOf(
                    "status" to "success",
                    "message" to "$vmsType VMS 데이터 동기화가 완료되었습니다."
                ))
            } catch (e: Exception) {
                log.error("$vmsType VMS 동기화 중 오류 발생: ${e.message}", e)
                ResponseEntity.ok(mapOf(
                    "status" to "error",
                    "message" to "$vmsType VMS 동기화 중 오류 발생: ${e.message}"
                ))
            }
        } else {
            ResponseEntity.ok(mapOf(
                "status" to "error",
                "message" to "지원하지 않는 VMS 유형: $vmsType"
            ))
        }
    }

    /**
     * 모든 VMS 시스템의 카메라 데이터 동기화
     */
    @PostMapping
    suspend fun synchronizeAllVms(): ResponseEntity<Map<String, Any>> {
        val results = mutableMapOf<String, String>()
        
        try {
            coroutineScope {
                // 모든 VMS 동기화를 병렬로 실행
                val deferreds = vmsMap.map { (type, vms) ->
                    async {
                        try {
                            vms.synchronize()
                            type to "success"
                        } catch (e: Exception) {
                            log.error("$type VMS 동기화 중 오류 발생: ${e.message}", e)
                            type to "error: ${e.message}"
                        }
                    }
                }
                
                // 모든 비동기 작업 완료 대기
                val syncResults = deferreds.awaitAll()
                syncResults.forEach { (type, result) ->
                    results[type] = result
                }
            }
            
            return ResponseEntity.ok(mapOf(
                "status" to "completed",
                "results" to results
            ))
        } catch (e: Exception) {
            log.error("VMS 일괄 동기화 중 오류 발생: ${e.message}", e)
            return ResponseEntity.ok(mapOf(
                "status" to "error",
                "message" to "VMS 일괄 동기화 중 오류 발생: ${e.message}",
                "results" to results
            ))
        }
    }

    /**
     * 지원되는 모든 VMS 유형 목록 조회
     */
    @GetMapping("/types")
    fun getSupportedVmsTypes(): ResponseEntity<Map<String, List<String>>> {
        return ResponseEntity.ok(mapOf(
            "supportedTypes" to vmsMap.keys.toList()
        ))
    }

    /**
     * VMS RTSP URL 조회
     */
    @GetMapping("/rtsp/{vmsType}")
    suspend fun getRtspUrl(@PathVariable vmsType: String): ResponseEntity<Map<String, String>> {
        val vms = vmsMap[vmsType]
        
        return if (vms != null) {
            try {
                val rtspUrl = vms.getRtspURL()
                ResponseEntity.ok(mapOf(
                    "status" to "success",
                    "rtspUrl" to rtspUrl
                ))
            } catch (e: Exception) {
                log.error("$vmsType VMS RTSP URL 조회 중 오류 발생: ${e.message}", e)
                ResponseEntity.ok(mapOf(
                    "status" to "error",
                    "message" to "$vmsType VMS RTSP URL 조회 중 오류 발생: ${e.message}"
                ))
            }
        } else {
            ResponseEntity.ok(mapOf(
                "status" to "error",
                "message" to "지원하지 않는 VMS 유형: $vmsType"
            ))
        }
    }
}