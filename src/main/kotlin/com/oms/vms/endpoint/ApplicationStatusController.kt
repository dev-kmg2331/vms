package com.oms.vms.endpoint

import com.oms.api.response.ResponseUtil
import com.oms.vms.mongo.MongoDbMonitorService
import com.oms.vms.applicationStartTime
import format
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.management.ManagementFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 애플리케이션 상태 컨트롤러
 *
 * 애플리케이션 및 인프라 상태에 대한 정보를 제공하는 API입니다.
 */
@RestController
@RequestMapping("/api/v2/status")
@Tag(name = "애플리케이션 상태", description = "애플리케이션 및 인프라 상태 모니터링 API")
class ApplicationStatusController(
    private val mongoDbMonitorService: MongoDbMonitorService
) {
    private val log = LoggerFactory.getLogger(ApplicationStatusController::class.java)

    /**
     * 시스템 상태 조회 API
     *
     * 이 API는 애플리케이션의 상태와 MongoDB 서버 상태를 포함한 전체 시스템 상태 정보를 제공합니다.
     */
    @GetMapping
    @Operation(
        summary = "시스템 상태 조회",
        description = "애플리케이션 및 MongoDB 상태를 포함한 전체 시스템 상태 정보를 반환합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "상태 조회 성공"),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun getSystemStatus(): ResponseEntity<*> {
        log.info("Request to get system status information")

        try {
            // 애플리케이션 정보 수집
            val appStatus = collectApplicationStatus()

            // 결합된 상태 정보 생성
            val statusInfo = Document()
            statusInfo.append("application", appStatus)

            log.info("Successfully retrieved system status information")
            return ResponseUtil.success(statusInfo)
        } catch (e: Exception) {
            log.error("Error retrieving system status information: {}", e.message, e)
            return ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve system status")
        }
    }

    /**
     * MongoDB 서버 상태 조회 API
     *
     * 이 API는 MongoDB의 serverStatus 명령을 실행하여 서버의 상태 정보를 반환합니다.
     */
    @GetMapping("/mongodb")
    @Operation(
        summary = "MongoDB 서버 상태 조회",
        description = "MongoDB 서버의 상태 정보(메모리 사용량, 연결 수, 작업 통계 등)를 반환합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "서버 상태 조회 성공"),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    suspend fun getMongoDbStatus(): ResponseEntity<*> {
        return try {
            log.info("Request to get MongoDB server status")
            val status = mongoDbMonitorService.getServerStatus()
            log.info("Successfully retrieved MongoDB server status")
            ResponseUtil.success(status)
        } catch (e: Exception) {
            log.error("Error retrieving MongoDB server status: {}", e.message, e)
            ResponseUtil.fail(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve MongoDB server status")
        }
    }

    /**
     * 애플리케이션 상태 정보를 수집합니다.
     *
     * @return 애플리케이션 상태 정보를 담은 Document
     */
    private fun collectApplicationStatus(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        val mb = 1024 * 1024

        val memoryBean = ManagementFactory.getMemoryMXBean()
        val threadBean = ManagementFactory.getThreadMXBean()

        val uptime = Duration.between(applicationStartTime, Instant.now())

        return mapOf(
            "version" to "1.0.0",
            "startTime" to LocalDateTime.ofInstant(applicationStartTime, ZoneId.systemDefault()).format(),
            // 애플리케이션 버전
            "uptime" to "${uptime.toHours()}h ${uptime.toMinutesPart()}m ${uptime.toSecondsPart()}s",
            "memory" to mapOf(
                "total" to runtime.totalMemory() / mb,
                "free" to runtime.freeMemory() / mb,
                "max" to runtime.maxMemory() / mb,
                "used" to (runtime.totalMemory() - runtime.freeMemory()) / mb
            ),
            "heapUsage" to memoryBean.heapMemoryUsage.used / mb,
            "nonHeapUsage" to memoryBean.nonHeapMemoryUsage.used / mb,
            "threads" to Document(),
            "count" to threadBean.threadCount,
            "peakCount" to threadBean.peakThreadCount,
            "daemonCount" to threadBean.daemonThreadCount,
            "totalStarted" to threadBean.totalStartedThreadCount,
            "processors" to runtime.availableProcessors()
        )
    }
}