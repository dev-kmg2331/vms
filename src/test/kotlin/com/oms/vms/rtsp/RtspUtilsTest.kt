package com.oms.vms.rtsp

import com.oms.api.exception.ApiAccessException
import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.io.*
import java.net.Socket

/**
 * RtspTCPConnector와 RtspSdpParser에 대한 단위 테스트 클래스
 */
@ExtendWith(MockKExtension::class)
class RtspUtilsTest {

    private lateinit var rtspTCPConnector: RtspTCPConnector
    private lateinit var socket: Socket
    private lateinit var outputStream: OutputStream
    private lateinit var inputStream: InputStream
    private lateinit var printWriter: PrintWriter
    private lateinit var bufferedReader: BufferedReader

    private val rtspHost = "192.168.1.1"
    private val rtspBasePort = 554
    private val rtspTestPort = 8554

    private val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        const val CRLF = "\r\n"
    }

    @BeforeEach
    fun setUp() {
        // Socket 생성자를 완전히 모킹하여 실제 네트워크 연결을 방지
        rtspTCPConnector = spyk(RtspTCPConnector)
        socket = mockk<Socket>(relaxed = true)
        outputStream = mockk<OutputStream>(relaxed = true)
        inputStream = mockk<InputStream>(relaxed = true)
        printWriter = mockk<PrintWriter>(relaxed = true)
        bufferedReader = mockk<BufferedReader>(relaxed = true)

        // Socket 생성 메소드 모킹
        every { rtspTCPConnector.createSocket(any<String>(), any<Int>()) } returns socket

        // Socket의 메서드 모킹
        every { socket.getOutputStream() } returns outputStream
        every { socket.getInputStream() } returns inputStream

//        // PrintWriter와 BufferedReader 생성자 메소드 모킹
        every { rtspTCPConnector.createBufferedReader(any()) } returns bufferedReader
        every { rtspTCPConnector.createPrintWriter(any()) } returns printWriter
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("SDP 파싱 테스트 - 정상적인 SDP 데이터 처리")
    fun shouldParseSdpContentSuccessfully() {
        // Given
        val sdpContent = """
            v=0
            o=- 0 0 IN IP4 127.0.0.1
            s=Test Session
            i=Test Info
            c=IN IP4 192.168.1.1
            t=0 0
            m=video 0 RTP/AVP 96
            a=control:streamid=0
            a=rtpmap:96 H264/90000
        """.trimIndent()

        // When
        val result = RtspSdpParser.parseSdpContent(sdpContent)

        // Then
        assertEquals("0", result["version"])
        assertEquals("- 0 0 IN IP4 127.0.0.1", result["origin"])
        assertEquals("Test Session", result["sessionName"])
        assertEquals("Test Info", result["sessionInfo"])
        assertEquals("IN IP4 192.168.1.1", result["connectionInfo"])
        assertEquals("0 0", result["timing"])
        assertEquals("video 0 RTP/AVP 96", result["media"])

        val attributes = result["attributes"] as List<String>
        assertTrue(attributes.contains("control:streamid=0"))
        assertTrue(attributes.contains("rtpmap:96 H264/90000"))
    }

    @Test
    @DisplayName("SDP 파싱 테스트 - 다중 미디어 라인 처리")
    fun shouldParseMultipleMediaLines() {
        // Given
        val sdpContent = """
            v=0
            m=video 0 RTP/AVP 96
            m=audio 1 RTP/AVP 8
        """.trimIndent()

        // When
        val result = RtspSdpParser.parseSdpContent(sdpContent)

        // Then
        assertEquals("video 0 RTP/AVP 96", result["media"])
        assertEquals("audio 1 RTP/AVP 8", result["media${result.size - 1}"])
    }

    @Test
    @DisplayName("RTSP URL 파싱 테스트 - 인증 정보 있는 경우")
    fun shouldExtractCredentialsFromUrl() {
        // Given
        val url = "rtsp://username:password@$rtspHost:$rtspBasePort/stream"

        // When
        val credentials = rtspTCPConnector.extractCredentials(url)

        // Then
        assertEquals("username", credentials?.first)
        assertEquals("password", credentials?.second)
    }

    @Test
    @DisplayName("RTSP URL 파싱 테스트 - 인증 정보 없는 경우")
    fun shouldReturnNullWhenNoCredentialsInUrl() {
        // Given
        val url = "rtsp://$rtspHost:$rtspBasePort/stream"

        // When
        val credentials = rtspTCPConnector.extractCredentials(url)

        // Then
        assertNull(credentials)
    }

    @Test
    @DisplayName("RTSP URL 파싱 테스트 - 호스트와 포트 추출")
    fun shouldExtractHostAndPortFromUrl() {
        // Given
        val url = "rtsp://username:password@$rtspHost:$rtspTestPort/stream"

        // When
        val (host, port, path) = rtspTCPConnector.parseRtspUrl(url)

        // Then
        assertEquals(rtspHost, host)
        assertEquals(rtspTestPort, port)
        assertEquals("/stream", path)
    }

    @Test
    @DisplayName("RTSP URL 파싱 테스트 - 기본 포트 사용")
    fun shouldUseDefaultPortWhenNotSpecified() {
        // Given
        val url = "rtsp://$rtspHost/stream"

        // When
        val (host, port, path) = rtspTCPConnector.parseRtspUrl(url)

        // Then
        assertEquals(rtspHost, host)
        assertEquals(rtspBasePort, port)
        assertEquals("/stream", path)
    }

    @Test
    @DisplayName("connectRTSP 테스트 - 인증 성공")
    fun shouldAuthenticateSuccessfully() {
        // Given
        val url = "rtsp://username:password@$rtspHost:$rtspBasePort/stream"
        val sdpContent = "v=0\r\ns=Test Session\r\n"

        mockRtspFlow(
            initialStatusCode = 401,
            finalStatusCode = 200,
            sdpContent = sdpContent
        )

        // When
        val result = rtspTCPConnector.getSDPContent(url)

        // Then
        assertEquals(sdpContent, result)

        // Verify that socket and streams were closed
        verify(exactly = 1) { printWriter.close() }
        verify(exactly = 1) { bufferedReader.close() }
        verify(exactly = 1) { socket.close() }
    }

    @Test
    @DisplayName("connectRTSP 테스트 - 인증 없이 성공")
    fun shouldConnectSuccessfullyWithoutAuthentication() {
        // Given
        val url = "rtsp://$rtspHost:$rtspBasePort/stream"
        val sdpContent = "v=0\r\ns=Test Session\r\n"

        mockRtspFlow(
            initialStatusCode = 200,
            sdpContent = sdpContent
        )

        // When
        val result = rtspTCPConnector.getSDPContent(url)

        // Then
        assertEquals(sdpContent, result)
    }

    @Test
    @DisplayName("connectRTSP 테스트 - 인증 실패")
    fun shouldThrowExceptionWhenAuthenticationFails() {
        // Given
        val url = "rtsp://username:password@$rtspHost:$rtspBasePort/stream"

        mockRtspFlow(
            initialStatusCode = 401,
            finalStatusCode = 401
        )

        // When & Then
        assertThrows<ApiAccessException> {
            rtspTCPConnector.getSDPContent(url)
        }
    }

    @Test
    @DisplayName("connectRTSP 테스트 - 인증 필요하지만 크리덴셜 없음")
    fun shouldThrowExceptionWhenCredentialsNeededButNotProvided() {
        // Given
        val url = "rtsp://$rtspHost:$rtspBasePort/stream"

        mockRtspFlow(
            initialStatusCode = 401
        )

        // When & Then
        assertThrows<ApiAccessException> {
            rtspTCPConnector.getSDPContent(url)
        }
    }

    @Test
    @DisplayName("connectRTSP 테스트 - 소켓 연결 실패")
    fun shouldThrowExceptionWhenSocketConnectionFails() {
        // Given
        val url = "rtsp://$rtspHost:$rtspBasePort/stream"

        // 모든 Socket 생성자 호출에 대해 IOException을 발생시킴
        every { rtspTCPConnector.createSocket(any(), any()) } throws IOException("Connection refused")

        // When & Then
        assertThrows<IOException> {
            rtspTCPConnector.getSDPContent(url)
        }
    }

    @Test
    @DisplayName("WWW-Authenticate 헤더 추출 테스트")
    fun shouldExtractWwwAuthenticateHeader() {
        // Given
        val response = "RTSP/1.0 401 Unauthorized$CRLF" +
                "WWW-Authenticate: Digest realm=\"AXIS_ACCC8E1D9A8E\", nonce=\"0004aea1Y123456789\", stale=FALSE$CRLF" +
                "Content-Length: 0"

        // When
        val authHeader = rtspTCPConnector.extractWwwAuthenticateHeader(response)

        // Then
        assertEquals("Digest realm=\"AXIS_ACCC8E1D9A8E\", nonce=\"0004aea1Y123456789\", stale=FALSE", authHeader)
    }

    private fun mockRtspFlow(
        initialStatusCode: Int,
        finalStatusCode: Int? = null,
        sdpContent: String? = null
    ) {
        // Setup mock responses
        var callCount = 0

        every { bufferedReader.readLine() } answers {
            callCount++

            // First request
            if (callCount <= 4) { // Status + Headers
                when (callCount) {
                    1 -> "RTSP/1.0 $initialStatusCode ${if (initialStatusCode == 401) "Unauthorized" else "OK"}"
                    2 -> if (initialStatusCode == 401) "WWW-Authenticate: Digest realm=\"test\", nonce=\"123\"" else "Content-Length: ${sdpContent?.length ?: 0}"
                    3 -> if (initialStatusCode == 401) "Content-Length: 0" else ""
                    4 -> ""
                    else -> null
                }
            }
            // Second request (if authentication flow)
            else if (finalStatusCode != null && callCount <= 8) {
                when (callCount) {
                    5 -> "RTSP/1.0 $finalStatusCode ${if (finalStatusCode == 401) "Unauthorized" else "OK"}"
                    6 -> "Content-Length: ${sdpContent?.length ?: 0}"
                    7 -> ""
                    8 -> null
                    else -> null
                }
            } else {
                null
            }
        }

        if (sdpContent != null) {
            var readCalled = false
            every { bufferedReader.read(any<CharArray>(), any(), any()) } answers {
                if (!readCalled) {
                    readCalled = true
                    val buffer = args[0] as CharArray
                    sdpContent.toCharArray().copyInto(buffer)
                    sdpContent.length
                } else {
                    -1
                }
            }
        }
    }
}
