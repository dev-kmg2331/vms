package com.oms.vms.sync.endpoint

import com.oms.api.response.CommonApiResponse
import com.oms.api.response.ResponseUtil
import com.oms.vms.VmsFactory
import com.oms.vms.dahua.DahuaNvr
import com.oms.vms.emstone.EmstoneNvr
import com.oms.vms.naiz.NaizVms
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
@RequestMapping("/api/v2/vms/sync")
class VmsSynchronizeController(private val vmsFactory: VmsFactory) {
    private val log = LoggerFactory.getLogger(VmsSynchronizeController::class.java)

    /**
     * 특정 VMS 시스템의 카메라 데이터 동기화
     */
    @PostMapping("/{vmsType}")
    suspend fun synchronizeVms(@PathVariable vmsType: String): ResponseEntity<*> {
        val vms = vmsFactory.getService(vmsType)

        return try {
            vms.synchronize()
            ResponseUtil.success()
        } catch (e: Exception) {
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    /**
     * 모든 VMS 시스템의 카메라 데이터 동기화
     */
    @PostMapping
    suspend fun synchronizeAllVms(): ResponseEntity<*> {
        try {
            // 모든 VMS 동기화 실행
            val synchronize = coroutineScope {
                val deferreds = vmsFactory.getAllServices().map {
                    async {
                        try {
                            // 각 동기화 실행. (정상적인 경우 type: Unit 으로 Pair 생성.)
                            it.type to it.synchronize()
                        } catch (e: Exception) {
                            // 예외 로그 처리
                            log.error("Error synchronizing VMS ${it.type}: ${e.message}", e)
                            // 예외가 발생한 VMS 타입과 함께 에러 결과 반환
                            it.type to e
                        }
                    }
                }
                // 모든 비동기 작업 완료 대기
                val awaitAll = deferreds.awaitAll()

                awaitAll.associate { it.first to (it.second !is Exception) }
            }

            val hasFailed = synchronize.values.contains(false)
            val allFailed = synchronize.values.all { false }

            // 전체 실패
            return if (allFailed) ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "all vms synchronization failed.")
            // 부분 실패
            else if (hasFailed) ResponseUtil.success(
                HttpStatus.MULTI_STATUS,
                "vms synchronization partialy succeeded",
                synchronize
            )
            // 모두 성공
            else ResponseUtil.success(synchronize)
        } catch (e: Exception) {
            log.error("VMS synchronization internal error.: ${e.message}", e)
            return ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "VMS synchronization internal error.", e)
        }
    }

    /**
     * 지원되는 모든 VMS 유형 목록 조회
     */
    @GetMapping("/types")
    fun getSupportedVmsTypes(): ResponseEntity<CommonApiResponse> {
        val services = vmsFactory.getAllServices()
        return ResponseUtil.success(services)
    }

    /**
     * VMS RTSP URL 조회
     */
    @GetMapping("/rtsp/{vmsType}/{id}")
    suspend fun getRtspUrl(
        @PathVariable vmsType: String,
        @PathVariable id: String
    ): ResponseEntity<*> {
        val vms = vmsFactory.getService(vmsType)

        return try {
            val rtspUrl = vms.getRtspURL(id)
            ResponseUtil.success(rtspUrl)
        } catch (e: Exception) {
            log.error("error while finding $vmsType VMS RTSP URL: ${e.message}", e)
            return ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "error while finding VMS RTSP URL: ${e.message}", e)
        }
    }
}