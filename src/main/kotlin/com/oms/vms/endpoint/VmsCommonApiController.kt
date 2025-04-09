package com.oms.vms.endpoint

import com.oms.api.response.CommonApiResponse
import com.oms.api.response.ResponseUtil
import com.oms.vms.VmsFactory
import com.oms.vms.VmsType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
@Tag(name = "VMS Common API", description = "일반적인 VMS 관련 API 제공")
class VmsCommonApiController(private val vmsFactory: VmsFactory) {
    private val log = LoggerFactory.getLogger(this::class.java)



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