package com.oms.vms.rtsp

import org.bytedeco.opencv.opencv_core.Mat
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

/**
 * RTSP 스트리밍 수신 및 H.264 프레임 처리 서비스
 */
class RTSPStreamingService(
    // h264 decoder
    val decoder: H264StreamDecoder
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // 스트리밍 상태 변수들
    private var totalPacketsReceived = 0L
    private var frameCount = 0
    private var startTime = 0L

    // TCP 헤더 구분자
    private val CRLF = "\r\n"

    // 프레임 조립 변수들
    private var currentTimestamp = 0L
    private var isAssemblingFrame = false
    private val frameBuffer = ByteArrayOutputStream()
    private var spsData: ByteArray? = null
    private var ppsData: ByteArray? = null

    // 현재 I-frame
    private var currentIFrame: ByteArray? = null

    // 소켓 연결 객체
    private lateinit var rtspConnection: RtspConnection

    /**
     * RTSP 스트리밍을 시작합니다.
     *
     * @param rtspUrl RTSP 스트림 URL
     */
    fun startStreaming(rtspUrl: String) {
        try {
            log.info("Starting RTSP streaming from: $rtspUrl")

            // start connection
            rtspConnection = setupRTSPConnection(rtspUrl)
            val sessionID = performRTSPHandshake(rtspConnection, rtspUrl)

            log.info("RTSP Stream session ID: $sessionID")

            startStreamingLoop(rtspConnection)

        } catch (e: Exception) {
            log.error("Failed to start RTSP streaming", e)
            throw e
        }
    }

    /**
     * RTSP 연결을 설정하고 SDP를 가져옵니다.
     */
    private fun setupRTSPConnection(rtspUrl: String): RtspConnection {
        val rtspConnection = RtspConnection(rtspUrl)
        val response = rtspConnection.getSDPContent()
        val sdpData = RtspSdpParser.parseSdpContent(response)
            ?: throw IllegalStateException("SDP content parsing failed")

        log.info("SDP parsed successfully")
        return rtspConnection
    }

    /**
     * RTSP 핸드셰이크를 수행합니다 (SETUP, PLAY).
     */
    private fun performRTSPHandshake(rtspConnection: RtspConnection, rtspUrl: String): String {
        // SDP에서 trackID 추출
        val response = rtspConnection.getSDPContent()
        val sdpData = RtspSdpParser.parseSdpContent(response)!!

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

        val sessionID = extractSessionId(setupContent)
        log.info("RTSP session established: $sessionID")

        // PLAY 요청
        val playHeader = buildString {
            append("Session: $sessionID$CRLF")
            append("Range: npt=0.000-$CRLF") // 스트리밍 범위 : 시작
        }

        rtspConnection.sendRTPRequest(RTSPMethod.PLAY, rtspUrl, playHeader)

        return sessionID
    }

    /**
     * 실제 스트리밍 루프를 실행합니다.
     */
    private fun startStreamingLoop(rtspConnection: RtspConnection) {
        val buffer = ByteArray(4096)
        startTime = System.currentTimeMillis()

        log.info("Starting streaming loop...")

        while (true) {
            try {
                val bytesRead = rtspConnection.socket.inputStream.read(buffer)
                if (bytesRead == -1) {
                    log.info("Stream ended by server")
                    break
                }

                processRTPPackets(buffer, bytesRead)

            } catch (e: Exception) {
                log.error("Error in streaming loop: ${e.message}")
                Thread.sleep(100)
            }
        }
    }

    /**
     * 수신된 데이터에서 RTP 패킷들을 처리합니다.
     */
    private fun processRTPPackets(buffer: ByteArray, bytesRead: Int) {
        var offset = 0

        while (offset < bytesRead) {
            if (buffer[offset] == 0x24.toByte()) {
                offset = processRTPOverTCP(buffer, offset, bytesRead)
            } else {
                offset++
            }
        }
    }

    /**
     * RTP over TCP 패킷을 처리합니다.
     */
    private fun processRTPOverTCP(buffer: ByteArray, offset: Int, bytesRead: Int): Int {
        if (offset + 4 > bytesRead) return offset + 1

        val channel = buffer[offset + 1].toInt() and 0xFF
        val length = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                (buffer[offset + 3].toInt() and 0xFF)

        if (channel == 0 && offset + 4 + length <= bytesRead) {
            totalPacketsReceived++

            val rtpPacketData = buffer.sliceArray(offset + 4 until offset + 4 + length)
            processRTPPacket(rtpPacketData)

            printStatistics() // 통계 출력

            return offset + 4 + length
        }

        return offset + 1
    }

    /**
     * 개별 RTP 패킷을 처리합니다.
     */
    private fun processRTPPacket(rtpPacketData: ByteArray) {
        if (rtpPacketData.size < 12) return

        val timestamp = extractTimestamp(rtpPacketData)
        val rtpPayload = extractRTPPayload(rtpPacketData, 0, rtpPacketData.size)
        val h264Data = extractH264FromRTP(rtpPayload) ?: return

        if (h264Data.size < 5) return

        val nalType = h264Data[4].toInt() and 0x1F

        // 타임스탬프 변화로 새 프레임 시작 감지
        if (isNewFrame(timestamp)) {
            completeCurrentFrame()
        }

        processNALUnit(nalType, h264Data, timestamp)
    }

    /**
     * NAL 유닛 타입별로 처리합니다.
     */
    private fun processNALUnit(nalType: Int, h264Data: ByteArray, timestamp: Long) {
        when (nalType) {
            7 -> { // SPS
                spsData = h264Data
                log.info("SPS received: ${h264Data.size} bytes")
            }

            8 -> { // PPS
                ppsData = h264Data
                log.info("PPS received: ${h264Data.size} bytes")
            }

            5 -> { // I-frame
                startNewFrame(h264Data, timestamp)
                log.info("I-frame started")
            }

            1 -> { // P-frame
                if (isAssemblingFrame) {
                    frameBuffer.write(h264Data)
                } else {
                    startNewFrame(h264Data, timestamp)
                    log.info("P-frame started")
                }
            }

            28 -> { // FU-A
                if (h264Data.isNotEmpty()) {
                    if (isAssemblingFrame) {
                        frameBuffer.write(h264Data)
                    } else {
                        startNewFrame(h264Data, timestamp)
                    }
                }
            }

            else -> {
                // 기타 NAL 유닛들 (SEI, Access Unit Delimiter 등)
                if (isAssemblingFrame) {
                    frameBuffer.write(h264Data)
                }
            }
        }

        currentTimestamp = timestamp
    }

    /**
     * 새로운 프레임인지 확인합니다.
     */
    private fun isNewFrame(timestamp: Long): Boolean {
        return currentTimestamp != 0L && timestamp != currentTimestamp
    }

    /**
     * 현재 조립 중인 프레임을 완료 처리합니다.
     */
    private fun completeCurrentFrame() {
        if (isAssemblingFrame && frameBuffer.size() > 0) {
            processCompletedFrame(frameBuffer.toByteArray(), ++frameCount)
            frameBuffer.reset()
        }
        isAssemblingFrame = false
    }

    /**
     * 새로운 프레임을 시작합니다.
     */
    private fun startNewFrame(h264Data: ByteArray, timestamp: Long) {
        frameBuffer.reset()
        frameBuffer.write(h264Data)
        isAssemblingFrame = true
        currentTimestamp = timestamp
    }

    /**
     * 완성된 프레임을 처리합니다.
     * 여기서 실제 프레임 디코딩, 파일 저장 등을 수행할 수 있습니다.
     */
    private fun processCompletedFrame(frameData: ByteArray, frameNumber: Int) {
        if (frameData.size < 5) return

        // 프레임 타입 확인
        val nalType = frameData[4].toInt() and 0x1F
        val frameType = when (nalType) {
            5 -> "I-frame"
            1 -> "P-frame"
            else -> "Other"
        }

        if (nalType == 5) {
            currentIFrame = frameData
        }

        log.info("🎬 Frame #$frameNumber completed: $frameType, ${frameData.size} bytes")

        // TODO: 여기서 실제 프레임 처리

        // I-frame or P-frame
        if (nalType == 1 || nalType == 5) {
            // decode frame

            if (spsData == null) {
                log.warn("cannot decode $frameType. SPS data is null.")
                return
            }

            if (ppsData == null) {
                log.warn("cannot decode $frameType. PPS data is null.")
                return
            }

            decoder.decodeFrame(frameData, spsData!!, ppsData!!, frameNumber)
        }

    }

    /**
     * RTP 패킷에서 페이로드 부분만 추출합니다.
     *
     *                         [ RTP 헤더 구조 ]
     * byte                byte                byte                byte
     *  0                   1                   2                   3
     *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                           timestamp                           |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |           synchronization source (SSRC) identifier          |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * RTP 헤더(12바이트)를 제거하고 실제 H.264 데이터만 반환합니다.
     */
    private fun extractRTPPayload(buffer: ByteArray, offset: Int, length: Int): ByteArray {
        // RTP 헤더는 최소 12바이트 (고정 헤더만, 확장 헤더는 별도)
        if (length < 12) return ByteArray(0)

        // RTP 헤더 추출 (사용하지 않지만 분석 시 참고용)
//        val rtpHeader = analyzeRTPHeader(buffer.sliceArray(offset until offset + 12))
//        log.info("$rtpHeader")

        // RTP 페이로드 시작 위치 = RTP 헤더(12바이트) 다음
        val payloadOffset = offset + 12
        // 실제 H.264 데이터 길이 = 전체 길이 - RTP 헤더 길이
        val payloadLength = length - 12

        return if (payloadLength > 0) {
            // RTP 페이로드 부분만 추출하여 반환
            buffer.sliceArray(payloadOffset until payloadOffset + payloadLength)
        } else {
            // 페이로드가 없는 경우 빈 배열 반환
            ByteArray(0)
        }
    }

    /**
     * RTP 페이로드에서 H.264 NAL 유닛을 추출합니다.
     * RFC 6184 H.264 RTP Payload Format에 따라 처리합니다.
     */
    private fun extractH264FromRTP(rtpPayload: ByteArray): ByteArray? {
        // 빈 페이로드는 처리할 수 없음
        if (rtpPayload.isEmpty()) return null

        // NAL 유닛 타입 추출 (첫 바이트의 하위 5비트)
        val nalUnitType = rtpPayload[0].toInt() and 0x1F

        return when (nalUnitType) {
            // Single NAL Unit (타입 1-23): 하나의 완전한 NAL 유닛
            in 1..23 -> {
                // NAL start code(4바이트) + 기존 페이로드를 합친 새 배열 생성
                val result = ByteArray(rtpPayload.size + 4)

                // H.264 NAL start code 추가 (0x00000001)
                // 이 코드로 NAL 유닛의 시작을 표시
                result[0] = 0x00
                result[1] = 0x00
                result[2] = 0x00
                result[3] = 0x01

                // 원본 RTP 페이로드를 NAL start code 뒤에 복사
                System.arraycopy(rtpPayload, 0, result, 4, rtpPayload.size)
                result
            }

            // FU-A (Fragmentation Unit): 큰 NAL 유닛을 여러 RTP 패킷으로 분할
            28 -> {
                // FU-A는 최소 2바이트 헤더 필요 (FU Indicator + FU Header)
                if (rtpPayload.size < 2) return null

                // FU Indicator: 첫 번째 바이트 (NAL 헤더 정보 포함)
                val fuIndicator = rtpPayload[0]
                // FU Header: 두 번째 바이트 (분할 정보 포함)
                val fuHeader = rtpPayload[1]

                // FU Header에서 플래그 비트들 추출
                val startBit = (fuHeader.toInt() and 0x80) != 0  // 시작 패킷 여부
                val endBit = (fuHeader.toInt() and 0x40) != 0    // 마지막 패킷 여부
                val nalType = fuHeader.toInt() and 0x1F          // 원본 NAL 유닛 타입

                if (startBit) {
                    // 첫 번째 FU-A 패킷: NAL start code와 재구성된 NAL 헤더 추가

                    // 원본 NAL 헤더 재구성: FU Indicator의 상위 3비트 + NAL 타입
                    val nalHeader = (fuIndicator.toInt() and 0xE0) or nalType

                    // NAL start code(4바이트) + NAL 헤더(1바이트) + 페이로드
                    val result = ByteArray(rtpPayload.size - 1 + 4)

                    // NAL start code 추가
                    result[0] = 0x00
                    result[1] = 0x00
                    result[2] = 0x00
                    result[3] = 0x01
                    // 재구성된 NAL 헤더 추가
                    result[4] = nalHeader.toByte()

                    // FU-A 헤더 2바이트를 제외한 나머지 페이로드 복사
                    System.arraycopy(rtpPayload, 2, result, 5, rtpPayload.size - 2)
                    result
                } else {
                    // 중간 또는 마지막 FU-A 패킷: FU 헤더만 제거하고 페이로드만 반환
                    val result = ByteArray(rtpPayload.size - 2)
                    // FU-A 헤더 2바이트를 제외한 나머지 데이터만 복사
                    System.arraycopy(rtpPayload, 2, result, 0, rtpPayload.size - 2)
                    result
                }
            }

            // 다른 NAL 유닛 타입들 (STAP-A, STAP-B, MTAP 등)은 현재 지원하지 않음
            else -> {
                log.warn("Unsupported NAL Unit type: $nalUnitType. ")
                null
            }
        }
    }

    /**
     * 통계 정보를 출력합니다.
     */
    private fun printStatistics() {
        if (totalPacketsReceived % 1000 == 0L) {
            val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
            val packetsPerSecond = totalPacketsReceived / elapsedSeconds
            log.info(
                "Packets: $totalPacketsReceived, Frames: $frameCount, " +
                        "Rate: %.1f pkt/s".format(packetsPerSecond)
            )
        }
    }

    // 유틸리티 메서드들

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

    private fun extractTimestamp(rtpPacketData: ByteArray): Long {
        return ((rtpPacketData[4].toLong() and 0xFF) shl 24) or
                ((rtpPacketData[5].toLong() and 0xFF) shl 16) or
                ((rtpPacketData[6].toLong() and 0xFF) shl 8) or
                (rtpPacketData[7].toLong() and 0xFF)
    }

    /**
     * 스트리밍을 중지합니다.
     */
    fun stopStreaming() {
        log.info("RTSP streaming stopped")
        rtspConnection.close()
    }
}