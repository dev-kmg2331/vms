package com.oms.vms.rtsp

import com.oms.api.exception.ApiAccessException
import com.oms.logging.gson.gson
import com.oms.vms.digest.DigestChallengeResponseParser
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger


/**
 * Socket을 사용하여 RTSP URL에서 SDP 데이터를 파싱하는 유틸리티 클래스
 */
class RtspConnection(
    val rtspUrl: String
) {
    val socket = Socket()

    private val log = LoggerFactory.getLogger(this::class.java)
    private val DEFAULT_RTSP_PORT = 554
    private val SOCKET_TIMEOUT_MILLS = 1000
    private val CRLF = "\r\n"

    private val username: String?
    private val password: String?
    private var authHeader: String? = null

    private val cSeq: AtomicInteger = AtomicInteger(0)

    init {
        val credentials = extractCredentials(rtspUrl)
        this.username = credentials?.first
        this.password = credentials?.second

        // 인증 정보 추출
        // RTSP URL 파싱
        val (host, port, path) = parseRtspUrl(rtspUrl)
        log.info("Creating socket connection to $host:$port")
        socket.connect(InetSocketAddress(host, port), SOCKET_TIMEOUT_MILLS)

    }

    /**
     * RTSP URL을 통해 DESCRIBE method로 SDP 데이터를 가져온다.
     *
     * @throws IOException Socket 연결 또는 파싱 중 오류가 발생한 경우
     */
    @Throws(IOException::class)
    fun getSDPContent(): String {
        try {
            // DESCRIBE 요청 (인증 처리 포함)
            return sendRequestWithDigest(
                RTSPMethod.DESCRIBE,
                rtspUrl,
                username,
                password
            )
        } catch (e: Exception) {
            log.error("Failed to connect RTSP.", e)
            throw ApiAccessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to connect RTSP.")
        }
    }

    /**
     * 인증 처리를 포함한 RTSP 요청을 보냅니다.
     *
     * @param method RTSP 메서드
     * @param rtspUrl RTSP URL
     * @param credentials 인증 정보 (username, password)
     * @param inputStream 서버로부터의 입력 스트림
     * @param outputStream 서버로의 출력 스트림
     * @return SDP 콘텐츠 문자열
     * @throws IOException 요청 실패 시
     */
    fun sendRequestWithDigest(
        method: RTSPMethod,
        rtspUrl: String,
        username: String?,
        password: String?,
    ): String {
        val (statusCode, response) = sendRTCPRequest(method, rtspUrl)

        when (statusCode) {
            // 401 Unauthorized 응답 처리 (Digest Authentication 필요)
            401 -> {
                log.info("Received 401 Unauthorized, attempting Digest Authentication")

                if (username == null || password == null) {
                    throw ApiAccessException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "No credentials found from rtsp url: $rtspUrl"
                    )
                }

                // WWW-Authenticate 헤더에서 인증 정보 추출
                val wwwAuthHeader = extractWwwAuthenticateHeader(response)
                    ?: throw IOException("Failed to parse Digest authentication parameters. url: $rtspUrl")

                // DigestChallengeResponseParser를 사용하여 인증 처리
                val digestParams = DigestChallengeResponseParser.parseDigestChallenge(wwwAuthHeader)

                if (digestParams.isEmpty()) {
                    log.error("WWW-Authenticate header not found in 401 response or no credentials provided. url: $rtspUrl")
                    throw IOException("WWW-Authenticate header not found or no credentials provided")
                }

                // Digest 인증을 사용하여 다시 요청
                log.info("Sending request with Digest Authentication")
                val digestAuthHeader = DigestChallengeResponseParser.createDigestAuthHeader(
                    method.name, username, password, digestParams, rtspUrl
                )

                val (digestStatusCode, newContentLength) =
                    sendRTCPRequest(method, rtspUrl)

                if (digestStatusCode != 200) {
                    log.error("Failed to authenticate with Digest Authentication, status code: $digestStatusCode. url: $rtspUrl")
                    throw ApiAccessException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Diget authentication failed with status code: $digestStatusCode"
                    )
                }

                authHeader = digestAuthHeader
                return response
            }
            // 인증 없이 성공
            200 -> return response
            // 예외 발생
            else -> throw ApiAccessException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to connect RTSP, status code: $statusCode. url: $rtspUrl"
            )
        }
    }

    /**
     * RTSP URL에서 인증 정보(사용자 이름과 비밀번호)를 추출합니다.
     *
     * @param url RTSP URL 문자열
     * @return 사용자 이름과 비밀번호를 포함한 Pair 객체, 없으면 null
     */
    private fun extractCredentials(url: String): Pair<String, String>? {
        val regex = Regex("rtsp://([^:]+):([^@]+)@")
        val matchResult = regex.find(url)

        return if (matchResult != null) {
            val (username, password) = matchResult.destructured
            log.info("Extracted credentials - Username: $username")
            Pair(username, password)
        } else {
            log.info("No credentials found in URL")
            null
        }
    }

    /**
     * HTTP 응답에서 WWW-Authenticate 헤더를 추출합니다.
     *
     * @param response HTTP 응답 문자열
     * @return WWW-Authenticate 헤더 값, 없으면 null
     */
    fun extractWwwAuthenticateHeader(response: String): String? {
        val lines = response.split(CRLF).map { it.trimIndent() }
        log.debug("Response lines: {}", lines)
        for (line in lines) {
            if (line.startsWith("WWW-Authenticate:", ignoreCase = true)) {
                log.info("Found WWW-Authenticate header: $line")
                return line.substring("WWW-Authenticate:".length).trim()
            }
        }
        return null
    }

    /**
     * RTCP 요청을 보내고 응답을 처리합니다.
     *
     * @param method RTSP 메서드
     * @param rtspUrl RTSP URL
     * @param authHeader 인증 헤더 (있는 경우)
     * @param inputStream 서버로부터의 입력 스트림
     * @param outputStream 서버로의 출력 스트림
     * @return 상태 코드, 응답 문자열, Content-Length를 포함한 Triple 객체
     */
    fun sendRTCPRequest(
        method: RTSPMethod,
        rtspUrl: String,
        header: String? = null,
    ): Pair<Int, String> {
        val request = buildString {
            append("${method.name} $rtspUrl RTSP/1.0$CRLF")
            append("CSeq: ${cSeq.incrementAndGet()}$CRLF")
            append("Accept: application/sdp$CRLF")
            append("User-Agent: omsecurity$CRLF")
            // 인증 헤더 추가 (있는 경우)
            if (!authHeader.isNullOrEmpty()) {
                append("Authorization: $authHeader$CRLF")
            }
            if (!header.isNullOrEmpty()) {
                append(header)
            }
            append(CRLF)
        }

        log.info("request headers: $request")

        socket.outputStream.write(request.toByteArray())

        log.info("Sending ${method.name} request to RTSP server")

        socket.outputStream.flush()

        // 응답 읽기
        var statusCode = -1
        var contentLength = 0
        val response = StringBuilder()

        // 헤더 처리
        var line: String?
        while (readLine().also { line = it } != null) {
            if (line!!.isEmpty()) {
                break // 헤더 끝
            }
            response.append(line).append(CRLF)

            // 첫 번째 줄에서 상태 코드 확인
            if (line!!.startsWith("RTSP/")) {
                val parts = line!!.split(" ")
                if (parts.size >= 2) {
                    statusCode = parts[1].toIntOrNull() ?: -1
                }
            }
            // Content-Length 헤더 검색
            else if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                val lengthStr = line!!.substring("Content-Length:".length).trim()
                contentLength = lengthStr.toIntOrNull() ?: 0
            }
        }

        log.debug("Response headers received: {}", response)

        val bytes: ByteArray?

        // 헤더 읽기 완료 후, body가 있다면 읽기
        if (contentLength > 0) {
            val buffer = ByteArray(contentLength)
            var totalBytesRead = 0

            while (totalBytesRead < contentLength) {
                val bytesRead = socket.inputStream.read(buffer, totalBytesRead, contentLength - totalBytesRead)
                if (bytesRead == -1) {
                    log.error("Unexpected end of stream while reading body")
                    break
                }
                totalBytesRead += bytesRead
            }

            if (totalBytesRead == contentLength) {
                bytes = buffer
                log.info("Body data received: $totalBytesRead bytes")
            } else {
                log.error("Body read incomplete: $totalBytesRead / $contentLength bytes")
                bytes = buffer.copyOf(totalBytesRead) // 읽은 만큼만 반환
            }
        } else {
            bytes = null
        }

        if (bytes != null) {
            response.appendLine(String(bytes))
        }

        return Pair(statusCode, response.toString())
    }

    // 헤더를 라인별로 읽기 위한 함수
    private fun readLine(): String? {
        val line = StringBuilder()
        var char: Int

        while (socket.inputStream.read().also { char = it } != -1) {
            val c = char.toChar()
            if (c == '\r') {
                // CR 다음에 LF가 올 것으로 예상
                val nextChar = socket.inputStream.read()
                if (nextChar == '\n'.code) {
                    // CRLF 완성
                    return if (line.isEmpty()) "" else line.toString()
                } else {
                    // CR만 있는 경우 (비표준이지만 처리)
                    line.append(c)
                    if (nextChar != -1) {
                        line.append(nextChar.toChar())
                    }
                }
            } else if (c == '\n') {
                // LF만 있는 경우
                return if (line.isEmpty()) "" else line.toString()
            } else {
                line.append(c)
            }
        }

        return if (line.isEmpty()) null else line.toString()
    }

    /**
     * RTCP 요청을 보내고 응답을 처리합니다.
     *
     * @param method RTSP 메서드
     * @param rtspUrl RTSP URL
     * @param authHeader 인증 헤더 (있는 경우)
     * @param inputStream 서버로부터의 입력 스트림
     * @param outputStream 서버로의 출력 스트림
     * @return 상태 코드, 응답 문자열, Content-Length를 포함한 Triple 객체
     */
    fun sendRTPRequest(
        method: RTSPMethod,
        rtspUrl: String,
        header: String,
    ) {
        val header = buildString {
            append("${method.name} $rtspUrl RTSP/1.0$CRLF")
            append("CSeq: ${cSeq.incrementAndGet()}$CRLF")
            append("User-Agent: omsecurity$CRLF")
            append(header)
            append(CRLF)
        }

        socket.outputStream.write(header.toByteArray())
        log.info("Sending $method request")
        socket.outputStream.flush()
    }

    /**
     * RTSP URL을 파싱하여 호스트, 포트, 경로를 추출합니다.
     *
     * @param rtspUrl RTSP URL 문자열
     * @return 호스트, 포트, 경로를 포함한 3개의 값 튜플
     */
    fun parseRtspUrl(rtspUrl: String): Triple<String, Int, String> {
        // 프로토콜 제거
        var url: String
        val host: String
        val port: Int
        val path: String

        url = rtspUrl.replace(Regex("^rtsp://"), "")

        if (url.contains("@")) {
            url = url.split("@").last()
        }

        val connection = url.split("/").first()

        if (connection.contains(":")) {
            val split = connection.split(":")
            host = split.first()
            port = split.last().toIntOrNull()
                ?: throw NumberFormatException("failed parsing port info from rtsp url: $rtspUrl")
        } else {
            host = connection
            port = DEFAULT_RTSP_PORT
        }

        path = url.replace(connection, "")

        log.info("Parsed RTSP URL - Host: $host, Port: $port, Path: $path")

        return Triple(host, port, path)
    }

    fun close() {
        log.info("Closing connection.")
        socket.inputStream.close()
        socket.outputStream.close()
        socket.close()
    }
}

object RtspSdpParser {
    private val log = LoggerFactory.getLogger(this::class.java)
    private const val CRLF = "\r\n"

    /**
     * SDP 콘텐츠를 파싱하여 필드 맵으로 변환합니다.
     *
     * @param sdpContent SDP 문자열 데이터
     * @return SDP 필드를 키-값 쌍으로 담은 Map 객체
     */
    fun parseSdpContent(sdpContent: String): SDP? {
        log.info("Parsing SDP content$CRLF$sdpContent")

        if (sdpContent.isEmpty() || sdpContent.isBlank()) {
            log.warn("content is empty")
            return null
        }

        val sdp = SDP()
        var attrCount = 0

        // 줄별로 처리
        val lines = sdpContent.split(Regex("\r\n|\n"))
        for (line in lines) {
            if (line.isEmpty()) continue

            // SDP 형식: <type>=<value>
            if (line.length >= 2) {
                val type = line[0]
                val value = line.substring(2).trim()

                when (type) {
                    'v' -> { // Protocol Version
                        sdp.version = value
                        attrCount++
                    }

                    'o' -> { // Origin
                        sdp.origin = value
                        attrCount++
                    }

                    's' -> { // Session Name
                        sdp.sessionName = value
                        attrCount++
                    }

                    'i' -> { // Session Information
                        sdp.sessionInfo = value
                        attrCount++
                    }

                    'c' -> { // Connection Information
                        sdp.connectionInfo = value
                        attrCount++
                    }

                    't' -> { // Timing
                        sdp.timing = value
                        attrCount++
                    }

                    'm' -> { // Media Description
                        sdp.media = value
                        attrCount++
                    }

                    'a' -> { // Attribute
                        // 미디어 속성을 별도로 처리 (미디어 별로 구분하는 경우)
                        // 속성 형식: 'key:value'
                        val split = value.split(":")

                        if (split.size < 2) {
                            continue
                        }

                        sdp.attributes[split[0].trim()] = split[1].trim()

                        attrCount++
                    }

                    else -> continue
                }
            }
        }

        log.info("SDP parsing completed, found $attrCount fields.")
        return sdp
    }
}

/**
 * RTSP 메소드 열거형
 */
enum class RTSPMethod {
    DESCRIBE, SETUP, PLAY, TEARDOWN, OPTIONS, PAUSE
}

class SDP {
    var version: String = ""
    var origin: String = ""
    var sessionName: String = ""
    var sessionInfo: String = ""
    var connectionInfo: String = ""
    var timing: String = ""
    var media: String = ""
    var attributes: MutableMap<String, String> = mutableMapOf()
}

/**
 * 메인 함수 - 서비스 사용 예시
 */
fun main() {
    val decoder = H264StreamDecoder()
    val streamingService = RTSPStreamingService(decoder)

    try {
        val rtspUrl = "rtsp://210.99.70.120:1935/live/cctv015.stream"
        streamingService.startStreaming(rtspUrl)
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        streamingService.stopStreaming()
    }
}