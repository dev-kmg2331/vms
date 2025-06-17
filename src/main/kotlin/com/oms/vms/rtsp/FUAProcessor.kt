package com.oms.vms.rtsp

import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream

data class FUAHeader(
    val start: Boolean,      // S bit - 첫 번째 프래그먼트
    val end: Boolean,        // E bit - 마지막 프래그먼트
    val nalUnitType: Int     // 원본 NAL 유닛 타입
)

class FUAProcessor {
    private val log = LoggerFactory.getLogger(this::class.java)

    // 타임스탬프별 조립 상태
    private val assemblyBuffers = mutableMapOf<Long, AssemblyState>()

    data class AssemblyState(
        val buffer: ByteArrayOutputStream,
        val nalType: Int,
        val nalRefIdc: Int,
        val startSequence: Int,
        val startTime: Long,
        var lastSequence: Int,
        var fragmentCount: Int = 1
    )

    fun processFUA(
        rtpPayload: ByteArray,
        sequenceNumber: Int,
        timestamp: Long,
        marker: Boolean
    ): ByteArray? {

        if (rtpPayload.size < 2) {
            log.warn("FU-A 페이로드가 너무 짧음: ${rtpPayload.size}")
            return null
        }

        try {
            // FU Indicator & Header 파싱
            val fuIndicator = rtpPayload[0].toInt() and 0xFF
            val nalRefIdc = (fuIndicator shr 5) and 0x03
            val fuType = fuIndicator and 0x1F

            if (fuType != 28) {
                log.warn("FU-A가 아닌 패킷: type=$fuType")
                return null
            }

            val fuHeader = rtpPayload[1].toInt() and 0xFF
            val start = (fuHeader and 0x80) != 0
            val end = (fuHeader and 0x40) != 0
            val originalNalType = fuHeader and 0x1F

            val fragmentPayload = rtpPayload.sliceArray(2 until rtpPayload.size)

            log.debug("FU-A: seq=$sequenceNumber, ts=$timestamp, start=$start, end=$end, type=$originalNalType, size=${fragmentPayload.size}")

            return when {
                start -> {
                    handleStartFragment(timestamp, sequenceNumber, nalRefIdc, originalNalType, fragmentPayload)
                    return null
                }
                end -> handleEndFragment(timestamp, sequenceNumber, fragmentPayload, marker)
                else -> {
                    handleMiddleFragment(timestamp, sequenceNumber, fragmentPayload)
                    return null
                }
            }

        } catch (e: Exception) {
            log.error("FU-A 처리 중 오류: ${e.message}")
            // 오류 발생시 해당 타임스탬프의 조립 데이터 정리
            assemblyBuffers.remove(timestamp)?.buffer?.close()
            return null
        }
    }

    private fun handleStartFragment(
        timestamp: Long,
        sequenceNumber: Int,
        nalRefIdc: Int,
        nalType: Int,
        payload: ByteArray
    ) {

        log.debug("🚀 FU-A 시작: ts=$timestamp, seq=$sequenceNumber, nalType=$nalType")

        // 기존 미완성 조립 데이터 정리
        assemblyBuffers.remove(timestamp)?.buffer?.close()

        // 원본 NAL 헤더 재구성
        val originalNalHeader = ((nalRefIdc shl 5) or nalType).toByte()

        // 새로운 조립 상태 생성
        val buffer = ByteArrayOutputStream()
        buffer.write(originalNalHeader.toInt())
        buffer.write(payload)

        val assemblyState = AssemblyState(
            buffer = buffer,
            nalType = nalType,
            nalRefIdc = nalRefIdc,
            startSequence = sequenceNumber,
            startTime = System.currentTimeMillis(),
            lastSequence = sequenceNumber
        )

        assemblyBuffers[timestamp] = assemblyState

        log.debug("   NAL 헤더: 0x${String.format("%02X", originalNalHeader)}, 첫 조각: ${payload.size}bytes")
    }

    private fun handleMiddleFragment(
        timestamp: Long,
        sequenceNumber: Int,
        payload: ByteArray
    ) {

        val assemblyState = assemblyBuffers[timestamp]

        if (assemblyState == null) {
            log.debug("중간 프래그먼트 무시 - 시작 없음: ts=$timestamp, seq=$sequenceNumber")
            return
        }

        // 시퀀스 번호 검증
        val expectedSequence = (assemblyState.lastSequence + 1) and 0xFFFF
        if (sequenceNumber != expectedSequence) {
            log.warn("⚠️ 시퀀스 불일치 - 예상: $expectedSequence, 실제: $sequenceNumber (ts=$timestamp)")
            // 불일치시에도 일단 추가 (순서는 이미 맞춰진 상태라고 가정)
        }

        assemblyState.buffer.write(payload)
        // 시퀀스 번호 수정
        assemblyState.lastSequence = sequenceNumber
        assemblyState.fragmentCount++

        log.debug("🔄 중간 프래그먼트: ts=$timestamp, seq=$sequenceNumber, size=${payload.size}, 총크기=${assemblyState.buffer.size()}")
        return
    }

    private fun handleEndFragment(
        timestamp: Long,
        sequenceNumber: Int,
        payload: ByteArray,
        marker: Boolean
    ): ByteArray? {

        val assemblyState = assemblyBuffers.remove(timestamp)

        if (assemblyState == null) {
            log.warn("마지막 프래그먼트 무시 - 시작 없음: ts=$timestamp, seq=$sequenceNumber")
            return null
        }

        try {
            // 마지막 페이로드 추가
            assemblyState.buffer.write(payload)

            val completeNalUnit = assemblyState.buffer.toByteArray()
            val assemblyDuration = System.currentTimeMillis() - assemblyState.startTime

            // 조립 완료 검증
            if (completeNalUnit.isNotEmpty()) {
                val nalType = completeNalUnit[0].toInt() and 0x1F

                if (nalType == assemblyState.nalType) {
                    log.debug("✅ FU-A 조립 완료: ts=$timestamp, nalType=$nalType, " +
                            "크기=${completeNalUnit.size}, 프래그먼트=${assemblyState.fragmentCount}개, " +
                            "소요시간=${assemblyDuration}ms, marker=$marker")

                    return completeNalUnit
                } else {
                    log.error("❌ NAL 타입 불일치: 예상=$nalType, 실제=${assemblyState.nalType}")
                }
            } else {
                log.error("❌ 빈 NAL Unit 조립됨")
            }

        } catch (e: Exception) {
            log.error("마지막 프래그먼트 처리 실패: ${e.message}")
        } finally {
            assemblyState.buffer.close()
        }

        return null
    }

    fun cleanupOldFragments(currentTimestamp: Long, timeoutMs: Long = 5000) {
        val cutoffTime = currentTimestamp - timeoutMs
        val timeoutEntries = mutableListOf<Long>()

        for ((timestamp, assemblyState) in assemblyBuffers) {
            if (assemblyState.startTime < cutoffTime) {
                timeoutEntries.add(timestamp)
            }
        }

        timeoutEntries.forEach { timestamp ->
            val assemblyState = assemblyBuffers.remove(timestamp)
            assemblyState?.buffer?.close()
            log.warn("🕐 타임아웃으로 미완성 FU-A 정리: ts=$timestamp, " +
                    "프래그먼트=${assemblyState?.fragmentCount}개")
        }

        if (timeoutEntries.isNotEmpty()) {
            log.info("총 ${timeoutEntries.size}개의 미완성 FU-A 정리됨")
        }
    }

    fun getAssemblyStatus(): String {
        return "조립 중인 FU-A: ${assemblyBuffers.size}개, " +
                "타임스탬프: ${assemblyBuffers.keys.joinToString(", ")}"
    }

    fun reset() {
        assemblyBuffers.values.forEach { it.buffer.close() }
        assemblyBuffers.clear()
        log.info("FU-A 처리기 초기화됨")
    }
}