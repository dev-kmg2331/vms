package com.oms.vms.rtsp

import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * VLC 스타일의 개선된 RTSP 스트리밍 수신 및 H.264 프레임 처리 서비스
 * 1. SPS/PPS 우선 처리 전략
 * 2. 버퍼링 전략 적용
 */
class RTSPStreamingService {
    private val log = LoggerFactory.getLogger(this::class.java)

    // 파라미터 셋 상태 관리 (VLC 스타일)
    private var hasValidSPS = false
    private var hasValidPPS = false
    private var spsData: ByteArray? = null
    private var ppsData: ByteArray? = null

    // 버퍼링 관련
    private val packetBuffer = ConcurrentLinkedQueue<BufferedPacket>()
    private val bufferLock = ReentrantLock()
    private val bufferThreshold = 15  // 15개 패킷 버퍼링 (VLC는 0-48% 버퍼링)
    private val maxBufferSize = 50    // 최대 50개까지 버퍼링

    // 스트리밍 상태 변수들
    private var totalPacketsReceived = 0L
    private var packetsProcessed = 0L
    private var packetsDropped = 0L
    private var frameCount = 0
    private var startTime = 0L

    // TCP 헤더 구분자
    private val CRLF = "\r\n"

    // 기존 컴포넌트들
    private val fuaProcessor = FUAProcessor()
    private val h264FileWriter = H264FileWriter("C:\\Users\\82103\\FFMPEG\\X86\\test.h264")

    // 소켓 연결 객체
    private lateinit var rtspConnection: RtspConnection

    // 버퍼링된 패킷 데이터 클래스
    data class BufferedPacket(
        val rtpData: ByteArray,
        val receivedTime: Long,
        val sequenceNumber: Int,
        val timestamp: Long,
        val marker: Boolean
    )

    /**
     * RTSP 스트리밍을 시작합니다.
     */
    fun startStreaming(rtspUrl: String) {
        try {
            log.info("🎬 VLC 스타일 RTSP 스트리밍 시작: $rtspUrl")

            // start connection
            rtspConnection = RtspConnection(rtspUrl)

            // DESCRIBE 요청
            val describe = rtspConnection.sendRequest(RTSPMethod.DESCRIBE)
            val sdpData = RtspSdpParser.parseSdpContent(describe)!!

            // Track ID
            val trackId = extractTrackId(sdpData)
            val setupUrl = "$rtspUrl/trackID=$trackId"

            // SETUP 요청
            val setupHeader = "Transport: RTP/AVP/TCP;unicast;interleaved=0-1$CRLF"
            val (setupStatus, setupContent) = rtspConnection.sendRTCPRequest(
                method = RTSPMethod.SETUP,
                rtspUrl = setupUrl,
                header = setupHeader
            )

            if (setupStatus != 200) {
                throw RuntimeException("SETUP failed with status: $setupStatus")
            }

            // Session ID
            val sessionID = extractSessionId(setupContent)
            log.info("RTSP session established: $sessionID")

            // PLAY 요청
            val playHeader = buildString {
                append("Session: $sessionID$CRLF")
                append("Range: npt=0.000-$CRLF") // 스트리밍 범위 : 시작
            }

            rtspConnection.sendRTPRequest(RTSPMethod.PLAY, playHeader)

            log.info("RTSP Stream session ID: $sessionID")

            // 1단계: SPS/PPS 대기
            waitForParameterSets()

            // 2단계: 버퍼링 시작
            log.info("📶 버퍼링 시작...")
            startStreamingLoop()

        } catch (e: Exception) {
            log.error("Failed to start RTSP streaming", e)
            throw e
        }
    }

    /**
     * VLC 스타일: SPS/PPS를 먼저 찾을 때까지 대기
     */
    private fun waitForParameterSets() {
        log.info("📋 파라미터 셋 검색 중...")
        val timeout = System.currentTimeMillis() + 10000 // 10초 타임아웃
        val buffer = ByteArray(1024 * 64)

        while (!hasValidSPS || !hasValidPPS) {
            if (System.currentTimeMillis() > timeout) {
                log.warn("⚠️ 파라미터 셋 타임아웃 - 일반 처리로 진행")
                break
            }

            try {
                val bytesRead = rtspConnection.socket.inputStream.read(buffer)
                if (bytesRead == -1) break

                processRTPPacketsForParameterSets(buffer, bytesRead)

                if (hasValidSPS && hasValidPPS) {
                    log.info("✅ found NAL_SPS & NAL_PPS - 파라미터 셋 준비 완료")
                    break
                }
            } catch (e: Exception) {
                log.error("파라미터 셋 검색 중 오류: ${e.message}")
                break
            }
        }
    }

    /**
     * 파라미터 셋 검색을 위한 특별 처리
     */
    private fun processRTPPacketsForParameterSets(buffer: ByteArray, bytesRead: Int) {
        var offset = 0

        while (offset < bytesRead && (!hasValidSPS || !hasValidPPS)) {
            if (buffer[offset] == 0x24.toByte()) {
                offset = processParameterSetPacket(buffer, offset, bytesRead)
            } else {
                offset++
            }
        }
    }

    /**
     * 파라미터 셋 전용 패킷 처리
     */
    private fun processParameterSetPacket(buffer: ByteArray, offset: Int, bytesRead: Int): Int {
        if (offset + 4 > bytesRead) return offset + 1

        val channel = buffer[offset + 1].toInt() and 0xFF
        val length = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                (buffer[offset + 3].toInt() and 0xFF)

        if (channel == 0 && offset + 4 + length <= bytesRead) {
            val rtpPacketData = buffer.sliceArray(offset + 4 until offset + 4 + length)

            val (nalType, h264Data) = extractH264(rtpPacketData) ?: return offset + 4 + length

            when (nalType) {
                7 -> {
                    if (validateSPS(h264Data)) {
                        spsData = h264Data
                        hasValidSPS = true
                        h264FileWriter.writeNALUnit(nalType, h264Data)
                        log.info("✅ found NAL_SPS - 유효한 SPS 발견")
                    }
                }

                8 -> {
                    if (hasValidSPS && validatePPS(h264Data)) {
                        ppsData = h264Data
                        hasValidPPS = true
                        h264FileWriter.writeNALUnit(nalType, h264Data)
                        log.info("✅ found NAL_PPS - 유효한 PPS 발견")
                    }
                }
            }

            return offset + 4 + length
        }

        return offset + 1
    }

    /**
     * 실제 스트리밍 루프를 실행합니다 (버퍼링 적용)
     */
    private fun startStreamingLoop() {
        val buffer = ByteArray(1024 * 64) // 64 KB
        startTime = System.currentTimeMillis()

        log.info("📺 버퍼링 스트리밍 루프 시작...")

        while (true) {
            try {
                val bytesRead = rtspConnection.socket.inputStream.read(buffer)
                if (bytesRead == -1) {
                    log.info("Stream ended by server")
                    break
                }

                processRTPPacketsWithBuffering(buffer, bytesRead)

            } catch (e: Exception) {
                log.error("Error in streaming loop: ${e.message}")
                Thread.sleep(100)
            }
        }
    }

    /**
     * 버퍼링이 적용된 RTP 패킷 처리
     */
    private fun processRTPPacketsWithBuffering(buffer: ByteArray, bytesRead: Int) {
        var offset = 0

        while (offset < bytesRead) {
            if (buffer[offset] == 0x24.toByte()) {
                offset = bufferRTPPacket(buffer, offset, bytesRead)
            } else {
                offset++
            }
        }

        // 버퍼링 임계값 확인
        if (packetBuffer.size >= bufferThreshold) {
            processBufferedPackets()
        }
    }

    /**
     * RTP 패킷을 버퍼에 저장
     */
    private fun bufferRTPPacket(buffer: ByteArray, offset: Int, bytesRead: Int): Int {
        if (offset + 4 > bytesRead) return offset + 1

        val channel = buffer[offset + 1].toInt() and 0xFF
        val length = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                (buffer[offset + 3].toInt() and 0xFF)

        if (channel == 0 && offset + 4 + length <= bytesRead) {
            totalPacketsReceived++

            val rtpPacketData = buffer.sliceArray(offset + 4 until offset + 4 + length)

            // RTP 헤더 파싱하여 순서 정보 추출
            val header = parseRTPHeader(rtpPacketData, 0)
            if (header != null) {
                val bufferedPacket = BufferedPacket(
                    rtpData = rtpPacketData,
                    receivedTime = System.currentTimeMillis(),
                    sequenceNumber = header.sequenceNumber,
                    timestamp = header.timestamp,
                    marker = header.marker
                )

                bufferLock.withLock {
                    if (packetBuffer.size < maxBufferSize) {
                        packetBuffer.offer(bufferedPacket)
                    } else {
                        // 버퍼가 가득 차면 강제 처리
                        processBufferedPackets()
                        packetBuffer.offer(bufferedPacket)
                    }
                }
            }

            return offset + 4 + length
        }

        return offset + 1
    }

    /**
     * 버퍼링된 패킷들을 순서대로 처리
     */
    private fun processBufferedPackets() {
        val packetsToProcess = mutableListOf<BufferedPacket>()

        bufferLock.withLock {
            // 버퍼에서 모든 패킷 추출
            while (packetBuffer.isNotEmpty()) {
                packetsToProcess.add(packetBuffer.poll())
            }
        }

        if (packetsToProcess.isEmpty()) return

        // 시퀀스 번호로 정렬 (VLC 스타일 순서 보장)
        packetsToProcess.sortBy { it.sequenceNumber }

        val bufferProgress = minOf(100, (packetsToProcess.size * 100) / bufferThreshold)
        log.debug("📶 버퍼링 $bufferProgress% - ${packetsToProcess.size}개 패킷 처리")

        // 순서대로 처리
        packetsToProcess.forEach { packet ->
            processOrderedRTPPacket(packet.rtpData)
            packetsProcessed++
        }

        // 주기적 통계 출력
        if (packetsProcessed % 1000 == 0L) {
            printBufferingStatistics()
        }
    }

    /**
     * 순서가 보장된 RTP 패킷 처리
     */
    private fun processOrderedRTPPacket(rtpPacket: ByteArray) {
        if (rtpPacket.size < 12) return

        val (nalType, h264Data) = extractH264(rtpPacket) ?: return

        if (h264Data.size < 5) return

        // 파라미터 셋이 있을 때만 프레임 처리 (VLC 스타일)
        when (nalType) {
            7, 8 -> {
                // 파라미터 셋은 항상 처리
                h264FileWriter.writeNALUnit(nalType, h264Data)
            }

            5, 1 -> {
                // 프레임 데이터는 파라미터 셋이 있을 때만 처리
                if (hasValidSPS && hasValidPPS) {
                    h264FileWriter.writeNALUnit(nalType, h264Data)
                    frameCount++
                } else {
                    log.debug("⚠️ 파라미터 셋 없이 프레임 데이터 수신 - 무시")
                    packetsDropped++
                }
            }

            28 -> {
                // FU-A는 파라미터 셋이 있을 때만 처리
                if (hasValidSPS && hasValidPPS) {
                    h264FileWriter.writeNALUnit(nalType, h264Data)
                } else {
                    log.debug("⚠️ 파라미터 셋 없이 FU-A 수신 - 무시")
                    packetsDropped++
                }
            }

            else -> {
                h264FileWriter.writeNALUnit(nalType, h264Data)
            }
        }
    }

    /**
     * SPS 유효성 검증
     */
    private fun validateSPS(spsData: ByteArray): Boolean {
        return spsData.size > 4 &&
                (spsData[0].toInt() and 0x1F) == 7 &&
                (spsData[1].toInt() and 0x80) == 0 // profile_idc 유효성
    }

    /**
     * PPS 유효성 검증
     */
    private fun validatePPS(ppsData: ByteArray): Boolean {
        return ppsData.size > 2 &&
                (ppsData[0].toInt() and 0x1F) == 8
    }

    /**
     * RTP 헤더 파싱 (기존 로직 유지)
     */
    private fun parseRTPHeader(buffer: ByteArray, offset: Int): RTPHeader? {
        if (buffer.size < 12) return null

        val firstByte = buffer[offset].toInt() and 0xFF
        val version = (firstByte shr 6) and 0x03
        val hasPadding = (firstByte and 0x20) != 0
        val hasExtension = (firstByte and 0x10) != 0
        val csrcCount = firstByte and 0x0F

        if (version != 2) {
            log.warn("Unsupported RTP version: $version")
            return null
        }

        val secondByte = buffer[offset + 1].toInt() and 0xFF
        val marker = (secondByte and 0x80) != 0
        val payloadType = secondByte and 0x7F

        val sequenceNumber = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                (buffer[offset + 3].toInt() and 0xFF)

        val timestamp = ((buffer[offset + 4].toInt() and 0xFF).toLong() shl 24) or
                ((buffer[offset + 5].toInt() and 0xFF).toLong() shl 16) or
                ((buffer[offset + 6].toInt() and 0xFF).toLong() shl 8) or
                (buffer[offset + 7].toInt() and 0xFF).toLong()

        val ssrc = ((buffer[offset + 8].toInt() and 0xFF).toLong() shl 24) or
                ((buffer[offset + 9].toInt() and 0xFF).toLong() shl 16) or
                ((buffer[offset + 10].toInt() and 0xFF).toLong() shl 8) or
                (buffer[offset + 11].toInt() and 0xFF).toLong()

        var headerLength = 12 + (csrcCount * 4)

        if (hasExtension) {
            if (offset + headerLength + 4 > buffer.size) return null
            val extensionLength = ((buffer[offset + headerLength + 2].toInt() and 0xFF) shl 8) or
                    (buffer[offset + headerLength + 3].toInt() and 0xFF)
            headerLength += 4 + (extensionLength * 4)
        }

        return RTPHeader(
            version, hasPadding, hasExtension, csrcCount, marker, payloadType,
            sequenceNumber, timestamp, ssrc, headerLength
        )
    }

    /**
     * H.264 추출 로직 (기존 유지, FU-A 처리 포함)
     */
    private fun extractH264(rtpPacket: ByteArray): Pair<Int, ByteArray>? {
        if (rtpPacket.isEmpty()) return null

        val header = parseRTPHeader(rtpPacket, 0) ?: return null

        var payloadLength = rtpPacket.size - header.headerLength

        if (header.hasPadding && payloadLength > 0) {
            val paddingLength = rtpPacket[rtpPacket.size - 1].toInt() and 0xFF
            payloadLength -= paddingLength
        }

        if (payloadLength == 0) {
            throw RuntimeException("RTP Header payload is empty.")
        }

        val rtpPayload = rtpPacket.sliceArray(header.headerLength until header.headerLength + payloadLength)
        val nalUnitType = rtpPayload[0].toInt() and 0x1F

        if (nalUnitType == 28) {
            // FU-A 처리
            val completeNal =
                fuaProcessor.processFUA(rtpPayload, header.sequenceNumber, header.timestamp, header.marker)

            return completeNal?.let { Pair(nalUnitType, it) }
        } else {
            return Pair(nalUnitType, rtpPayload)
        }
    }

    /**
     * 버퍼링 통계 정보 출력
     */
    private fun printBufferingStatistics() {
        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
        val packetsPerSecond = packetsProcessed / elapsedSeconds
        val dropRate = if (totalPacketsReceived > 0) (packetsDropped * 100.0 / totalPacketsReceived) else 0.0

        log.info(
            "📊 VLC 스타일 통계 - 수신: $totalPacketsReceived, 처리: $packetsProcessed, " +
                    "드롭: $packetsDropped (${String.format("%.2f", dropRate)}%), " +
                    "프레임: $frameCount, 속도: ${String.format("%.1f", packetsPerSecond)} pkt/s, " +
                    "버퍼: ${packetBuffer.size}개"
        )
    }

    // 기존 유틸리티 메서드들 유지
    private fun extractTrackId(sdpData: SDP): String {
        return sdpData.attributes["control"]?.let { control ->
            if (control.startsWith("trackID=")) {
                control.substring("trackID=".length)
            } else {
                "1"
            }
        } ?: "1"
    }

    private fun extractSessionId(setupContent: String): String {
        val sessionData = setupContent.lines().first { it.startsWith("Session") }
        return sessionData.split(":")[1].split(";")[0].trim()
    }

    /**
     * 스트리밍을 중지합니다.
     */
    fun stopStreaming() {
        log.info("RTSP streaming stopped")

        // 남은 버퍼 처리
        processBufferedPackets()

        h264FileWriter.close()
        rtspConnection.close()

        log.info("최종 통계: ${getStatistics()}")
    }

    /**
     * 처리 통계 반환
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "totalPacketsReceived" to totalPacketsReceived,
            "packetsProcessed" to packetsProcessed,
            "packetsDropped" to packetsDropped,
            "frameCount" to frameCount,
            "hasValidSPS" to hasValidSPS,
            "hasValidPPS" to hasValidPPS,
            "bufferSize" to packetBuffer.size,
            "h264Stats" to h264FileWriter.getStatistics()
        )
    }
}