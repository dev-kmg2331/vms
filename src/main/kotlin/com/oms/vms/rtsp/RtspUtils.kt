package com.oms.vms.rtsp

import com.oms.api.exception.ApiAccessException
import com.oms.vms.digest.DigestChallengeResponseParser
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import java.io.*
import java.net.Socket

/**
 * Socket을 사용하여 RTSP URL에서 SDP 데이터를 파싱하는 유틸리티 클래스
 */
object RtspTCPConnector {
    private val log = LoggerFactory.getLogger(this::class.java)
    private const val DEFAULT_RTSP_PORT = 554
    private const val CRLF = "\r\n"


    /**
     * RTSP URL을 통해 지정된 method 로 통신 후 데이터를 가져온다.
     *
     * @param url RTSP URL 문자열 (형식: rtsp://username:password@host:port/stream)
     * @param method RTSP request type
     * @return SDP 필드를 키-값 쌍으로 담은 Map 객체
     * @throws IOException Socket 연결 또는 파싱 중 오류가 발생한 경우
     */
    @Throws(IOException::class)
    fun connectRTSP(url: String, method: RTSPMethod): String {
        log.info("Trying to parse SDP from RTSP URL: $url")

        // 인증 정보 추출
        val credentials = extractCredentials(url)

        // RTSP URL 파싱
        val (host, port, path) = parseRtspUrl(url)
        val rtspUrl = "rtsp://$host:$port$path"

        // Socket 연결
        log.info("Creating socket connection to $host:$port")
        val socket = createSocket(host, port)
        val inputStream = createBufferedReader(socket.getInputStream())
        val outputStream = createPrintWriter(socket.getOutputStream())

        try {
            // 초기 DESCRIBE 요청 시도
            log.info("Attempting initial DESCRIBE request")
            val (statusCode, response, contentLength) = sendRequest(
                method,
                rtspUrl,
                null,
                inputStream,
                outputStream
            )

            // 401 Unauthorized 응답 처리 (Digest Authentication 필요)
            if (statusCode == 401) {
                log.info("Received 401 Unauthorized, attempting Digest Authentication")

                val (username, password) = credentials ?: throw ApiAccessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "no credentials found from rtsp url: $url"
                )

                // WWW-Authenticate 헤더에서 인증 정보 추출
                val wwwAuthHeader = extractWwwAuthenticateHeader(response)
                    ?: throw IOException("Failed to parse Digest authentication parameters")

                // DigestChallengeResponseParser 를 사용하여 인증 처리
                val digestParams = DigestChallengeResponseParser.parseDigestChallenge(wwwAuthHeader)

                if (digestParams.isEmpty()) {
                    log.error("WWW-Authenticate header not found in 401 response or no credentials provided")
                    throw IOException("WWW-Authenticate header not found or no credentials provided")
                }

                // Digest 인증을 사용하여 다시 요청
                log.info("Sending request with Digest Authentication")
                val digestAuthHeader = DigestChallengeResponseParser.createDigestAuthHeader(
                    method.name, username, password, digestParams, rtspUrl
                )

                val (digestStatusCode, _, newContentLength) =
                    sendRequest(method, rtspUrl, digestAuthHeader, inputStream, outputStream)

                if (digestStatusCode == 200 && newContentLength > 0) {
                    // SDP 콘텐츠 읽기
                    val content = readContent(inputStream, newContentLength)

                    return content
                } else {
                    log.error("Failed to authenticate with Digest Authentication, status code: $digestStatusCode")
                    throw IOException("Authentication failed with status code: $digestStatusCode")
                }
            }

            // 인증 없이 성공한 경우
            if (statusCode == 200 && contentLength > 0) {
                val content = readContent(inputStream, contentLength)
                return content
            }

            throw ApiAccessException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to connect RTSP, status code: $statusCode"
            )
        } catch (e: Exception) {
            log.error("Failed to connect RTSP.", e)
            throw ApiAccessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to connect RTSP.")
        } finally {
            // 자원 해제
            try {
                outputStream.close()
                inputStream.close()
                socket.close()
                log.info("Socket connection closed")
            } catch (e: IOException) {
                log.error("Error closing socket resources", e)
            }
        }
    }

    /**
     * RTSP URL에서 인증 정보(사용자 이름과 비밀번호)를 추출합니다.
     *
     * @param url RTSP URL 문자열
     * @return 사용자 이름과 비밀번호를 포함한 Pair 객체, 없으면 null
     */
    fun extractCredentials(url: String): Pair<String, String>? {
        val regex = Regex("rtsp://([^:]+):([^@]+)@")
        val matchResult = regex.find(url)

        return if (matchResult != null) {
            val (username, password) = matchResult.destructured
            log.info("Extracted credentials - Username: $username, Password: $password")
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
        log.info("$lines")
        for (line in lines) {
            if (line.startsWith("WWW-Authenticate:", ignoreCase = true)) {
                log.info(line)
                return line.substring("WWW-Authenticate:".length).trim()
            }
        }
        return null
    }

    /**
     * 서버로부터 SDP 콘텐츠를 읽습니다.
     *
     * @param inputStream 서버로부터의 입력 스트림
     * @param contentLength 콘텐츠 길이
     * @return SDP 콘텐츠 문자열
     */
    fun readContent(inputStream: BufferedReader, contentLength: Int): String {
        log.info("Reading SDP content of length $contentLength")
        val buffer = CharArray(contentLength)

        inputStream.read(buffer, 0, buffer.size)

        val string = String(buffer)

        return string
    }

    /**
     * DESCRIBE 요청을 보내고 응답을 처리합니다.
     *
     * @param rtspUrl RTSP URL
     * @param authHeader 인증 헤더 (있는 경우)
     * @param inputStream 서버로부터의 입력 스트림
     * @param outputStream 서버로의 출력 스트림
     * @return 상태 코드, 응답 문자열, Content-Length를 포함한 Triple 객체
     */
    fun sendRequest(
        method: RTSPMethod,
        rtspUrl: String,
        authHeader: String?,
        inputStream: BufferedReader,
        outputStream: PrintWriter
    ): Triple<Int, String, Int> {
        val request = StringBuilder()
        request.append("${method.name} $rtspUrl RTSP/1.0$CRLF")
        request.append("CSeq: 2$CRLF")
        request.append("Accept: application/sdp$CRLF")
        request.append("User-Agent: omsecurity$CRLF")

        // 인증 헤더 추가 (있는 경우)
        if (!authHeader.isNullOrEmpty()) {
            request.append("Authorization: $authHeader$CRLF")
        }

        request.append(CRLF)

        log.info("Sending ${method.name} request to RTSP server")
        log.debug("Request headers: ${request.toString().replace(CRLF, " | ")}")
        outputStream.print(request.toString())
        outputStream.flush()

        // 응답 읽기
        var line: String?
        var statusCode = -1
        var contentLength = 0
        val response = StringBuilder()

        // 헤더 처리
        while (inputStream.readLine().also { line = it } != null) {
            if (line.isNullOrEmpty()) break

            response.append(line).append(CRLF)

            // 첫 번째 줄에서 상태 코드 확인
            if (line!!.startsWith("RTSP/")) {
                val parts = line!!.split(" ")
                if (parts.size >= 2) {
                    statusCode = parts[1].toIntOrNull() ?: -1
                    log.info("Response status code: $statusCode")
                }
            }
            // Content-Length 헤더 검색
            else if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                val lengthStr = line!!.substring("Content-Length:".length).trim()
                contentLength = lengthStr.toIntOrNull() ?: 0
                log.info("Content length: $contentLength bytes")
            }
        }

        log.info("Response headers received: $response")
        return Triple(statusCode, response.toString(), contentLength)
    }

    /**
     * RTSP URL을 파싱하여 호스트, 포트, 경로, 인증 정보를 추출합니다.
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

    fun createSocket(host: String, port: Int): Socket = Socket(host, port)
    fun createBufferedReader(inputStream: InputStream): BufferedReader = BufferedReader(InputStreamReader(inputStream))
    fun createPrintWriter(outputStream: OutputStream): PrintWriter = PrintWriter(outputStream, true)
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
    fun parseSdpContent(sdpContent: String): MutableMap<String, Any> {
        log.info("Parsing SDP content$CRLF$sdpContent")
        val sdpFields = mutableMapOf<String, Any>()

        val attributes = mutableListOf<String>()

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
                        sdpFields["version"] = value
                        log.info("Found version: $value")
                    }

                    'o' -> { // Origin
                        sdpFields["origin"] = value
                        log.info("Found origin: $value")
                    }

                    's' -> { // Session Name
                        sdpFields["sessionName"] = value
                        log.info("Found session name: $value")
                    }

                    'i' -> { // Session Information
                        sdpFields["sessionInfo"] = value
                        log.info("Found session info: $value")
                    }

                    'c' -> { // Connection Information
                        sdpFields["connectionInfo"] = value
                        log.info("Found connection info: $value")
                    }

                    't' -> { // Timing
                        sdpFields["timing"] = value
                        log.info("Found timing: $value")
                    }

                    'm' -> { // Media Description
                        if (!sdpFields.containsKey("media")) {
                            sdpFields["media"] = value
                        } else {
                            sdpFields["media${sdpFields.size}"] = value
                        }
                        log.info("Found media description: $value")
                    }

                    'a' -> { // Attribute
                        // 미디어 속성을 별도로 처리 (미디어 별로 구분하는 경우)
                        attributes.add(value)
                    }

                    else -> continue
                }
            }
        }

        if (attributes.isNotEmpty()) {
            sdpFields["attributes"] = attributes
        }

        log.info("SDP parsing completed, found ${sdpFields.size} fields")
        return sdpFields
    }
}

/**
 * HTTP 메소드 열거형
 */
enum class RTSPMethod {
    DESCRIBE, SETUP, PLAY, TEARDOWN, OPTIONS, PAUSE
}

fun main() {
    try {
        val rtspUrl = "rtsp://admin:eldigm2211!@223.171.45.247:554/cam/realmonitor?channel=1&subtype=2"
//        val rtspUrl = "rtsp://admin:oms20190211@192.168.182.200/video62"
        val response = RtspTCPConnector.connectRTSP(rtspUrl, RTSPMethod.OPTIONS)
        val sdpData = RtspSdpParser.parseSdpContent(response)

        println("Parsed SDP fields:")
        for ((key, value) in sdpData) {
            println("$key: $value")
        }
    } catch (e: IOException) {
        println("Error parsing SDP from RTSP URL $e")
    }
}