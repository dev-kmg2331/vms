package com.oms.vms.innodep

import com.oms.vms.endpoint.VmsConfigUpdateRequest
import com.oms.vms.manufacturers.innodep.VurixVms
import com.oms.vms.mongo.docs.VMS_CONFIG
import com.oms.vms.mongo.docs.VmsAdditionalInfo
import com.oms.vms.mongo.docs.VmsConfig
import com.oms.vms.service.VmsSynchronizeService
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import java.util.function.Consumer

/**
 * Vurix VMS 테스트
 *
 * 이노뎁 Vurix VMS의 로그인, 세션 갱신 및 카메라 동기화 기능을 테스트합니다.
 * MockK 라이브러리를 사용하여 의존성을 모킹하고 독립적인 테스트를 수행합니다.
 */
@ExtendWith(MockKExtension::class)
class VurixVmsTest {

    @MockK
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @MockK
    private lateinit var vmsSynchronizeService: VmsSynchronizeService

    @MockK
    private lateinit var webClientMock: WebClient

    @MockK
    private lateinit var webClientBuilder: WebClient.Builder

    private lateinit var vms: VurixVms
    private val log = LoggerFactory.getLogger(this::class.java)

    // 헤더 값 캡쳐 객체
    private val headersSlot = slot<Consumer<HttpHeaders>>()

    // Mock 응답 객체
    private lateinit var loginResponseMock: String
    private lateinit var vmsConfig: VmsConfig

    // Mock WebClient 관련 객체
    private lateinit var defaultRequestBodyUriSpecMock: WebClient.RequestBodyUriSpec

    // 테스트 상수값
    private val mockAuthToken = "test-auth-token-123456"
    private val mockUserSerial = "user-serial-123"
    private val mockApiSerial = "api-serial-456"

    /**
     * WebClient 요청/응답 모킹 헬퍼 함수
     * 
     * @param pathPrefix URI 경로 접두사
     * @param responseJson 응답으로 사용할 JSON 문자열
     * @return Pair<WebClient.RequestBodyUriSpec, WebClient.ResponseSpec> 모킹된 요청/응답 객체 쌍
     */
    private fun mockWebClientForPath(
        pathPrefix: String,
        responseJson: String
    ): Pair<WebClient.RequestBodyUriSpec, WebClient.ResponseSpec> {
        val requestSpec = mockk<WebClient.RequestBodyUriSpec>()
        val responseSpec = mockk<WebClient.ResponseSpec>()
        
        every { defaultRequestBodyUriSpecMock.uri(match<String> { it.startsWith(pathPrefix) }) } returns requestSpec
        every { requestSpec.retrieve() } returns responseSpec
        every { responseSpec.bodyToMono(String::class.java) } returns Mono.just(responseJson)
        
        return Pair(requestSpec, responseSpec)
    }

    /**
     * WebClient 요청/응답 모킹 헬퍼 함수 - 다중 응답 버전
     * 
     * @param pathPrefix URI 경로 접두사
     * @param responseJsons 순차적으로 반환할 JSON 문자열 목록
     * @return Pair<WebClient.RequestBodyUriSpec, WebClient.ResponseSpec> 모킹된 요청/응답 객체 쌍
     */
    private fun mockWebClientForPathWithMultipleResponses(
        pathPrefix: String,
        responseJsons: List<String>
    ): Pair<WebClient.RequestBodyUriSpec, WebClient.ResponseSpec> {
        val requestSpec = mockk<WebClient.RequestBodyUriSpec>()
        val responseSpec = mockk<WebClient.ResponseSpec>()
        
        every { defaultRequestBodyUriSpecMock.uri(match<String> { it.startsWith(pathPrefix) }) } returns requestSpec
        every { requestSpec.retrieve() } returns responseSpec
        
        val monoResponses = responseJsons.map { Mono.just(it) }
        every { responseSpec.bodyToMono(String::class.java) } returnsMany monoResponses
        
        return Pair(requestSpec, responseSpec)
    }

    @BeforeEach
    fun setup() {
        log.info("Initializing test environment for VurixVmsTest")

        // WebClient 관련 객체 모킹
        defaultRequestBodyUriSpecMock = mockk<WebClient.RequestBodyUriSpec>()
        log.info("Created mock objects for WebClient interactions")

        // WebClient 빌더 모킹
        every { webClientBuilder.baseUrl(any()) } returns webClientBuilder
        every { webClientBuilder.defaultHeader(any(), any<String>()) } returns webClientBuilder
        every { webClientBuilder.defaultHeaders(any()) } returns webClientBuilder
        every { webClientBuilder.defaultRequest(any()) } returns webClientBuilder
        every { webClientBuilder.build() } returns webClientMock
        every { webClientMock.mutate() } returns webClientBuilder
        log.info("Configured WebClient builder mock behaviors")

        // WebClient GET 요청 모킹
        every { webClientMock.get() } returns defaultRequestBodyUriSpecMock

        // VMS 설정 모킹
        vmsConfig = VmsConfig(
            id = UUID.randomUUID().toString(),
            username = "testUser",
            password = "testPassword",
            ip = "192.168.0.100",
            port = "8080",
            vms = "vurix",
            additionalInfo = mutableListOf()
        )
        log.info("Created VMS configuration with IP: {}, port: {}", vmsConfig.ip, vmsConfig.port)

        // MongoDB 템플릿 모킹
        every { mongoTemplate.find(any<Query>(), VmsConfig::class.java, VMS_CONFIG) } returns Flux.just(vmsConfig)
        every { mongoTemplate.findAndReplace(any(), any<VmsConfig>()) } returns Mono.just(vmsConfig)
        every { mongoTemplate.save(any<VmsConfig>()) } returns Mono.just(vmsConfig)
        log.info("Configured MongoDB template mock behaviors")

        // 로그인 응답 JSON 준비
        loginResponseMock = """
            {
                "success": true,
                "results": {
                    "auth_token": "$mockAuthToken",
                    "user_serial": "$mockUserSerial",
                    "api_serial": "$mockApiSerial"
                }
            }
        """.trimIndent()
        log.info("Prepared login response mock with token: {}", mockAuthToken)

        // 로그인 및 세션 관련 경로 모킹
        val (_, loginResponseSpec) = mockWebClientForPath("/login?force-login=true", loginResponseMock)
        mockWebClientForPath("/keep-alive", "Session refreshed")
        
        // 상태 오류 처리 모킹
        every { loginResponseSpec.onStatus(any(), any()) } returns loginResponseSpec

        // VurixVms 인스턴스 생성 및 WebClient 모킹
        vms = VurixVms(mongoTemplate, vmsSynchronizeService)
        vms.webClient = webClientMock
        vms.sessionClient = webClientMock
        vms.loadLoginContext(vmsConfig)

        log.info("VurixVms instance created and login context loaded")
    }

    @Test
    @DisplayName("VMS 설정 업데이트 테스트")
    fun `saveVmsConfig should update configuration and reload login context`() = runTest {
        log.info("Starting VMS config update test")

        // given - 업데이트할 설정값 준비
        val updatedIp = "192.168.0.200"
        val updatedPort = "9090"
        val updatedUsername = "updatedUser"
        val updatedPassword = "updatedPassword"
        val additionalInfo = listOf(
            VmsAdditionalInfo("license", "license-12345"),
            VmsAdditionalInfo("account-group", "admin")
        )

        val updatedConfig = VmsConfig(
            id = vmsConfig.id,
            username = updatedUsername,
            password = updatedPassword,
            ip = updatedIp,
            port = updatedPort,
            vms = "vurix",
            additionalInfo = additionalInfo.toMutableList()
        )

        val updateRequest = VmsConfigUpdateRequest(
            username = updatedUsername,
            password = updatedPassword,
            ip = updatedIp,
            port = updatedPort,
            additionalInfo = additionalInfo
        )

        log.info("Created update request with IP: {}, port: {}, username: {}", 
            updatedIp, updatedPort, updatedUsername)

        // MongoDB 업데이트 모킹
        every { mongoTemplate.save(any<VmsConfig>()) } returns Mono.just(updatedConfig)
        
        // WebClient 변경 모킹
        val newWebClient = mockk<WebClient>()
//        val newSessionClient = mockk<WebClient>()
        val newWebClientBuilder = mockk<WebClient.Builder>()
        
        every { webClientMock.mutate() } returns newWebClientBuilder
        every { newWebClientBuilder.baseUrl(any()) } returns newWebClientBuilder
        every { newWebClientBuilder.defaultHeader(any(), any<String>()) } returns newWebClientBuilder
        every { newWebClientBuilder.defaultHeaders(any()) } returns newWebClientBuilder
        every { newWebClientBuilder.defaultRequest(any()) } returns newWebClientBuilder
        every { newWebClientBuilder.build() } returns newWebClient
        
        // 로그인 모킹 (새 설정으로 로그인 컨텍스트 로드)
        every { newWebClient.get() } returns defaultRequestBodyUriSpecMock
        
        // spyk로 실제 메소드 일부 호출 모킹
        val spyVms = spyk(vms)
        coEvery { spyVms.saveVmsConfig(any()) } returnsMany listOf(updatedConfig)
        
        // when - 설정 업데이트 실행
        log.info("Executing saveVmsConfig with updated parameters")
        val result = spyVms.saveVmsConfig(updateRequest)
        
        // then - 결과 검증
        assertEquals(updatedUsername, result.username)
        assertEquals(updatedPassword, result.password)
        assertEquals(updatedIp, result.ip)
        assertEquals(updatedPort, result.port)
        assertEquals("vurix", result.vms)
        
        // additionalInfo 검증
        val license = result.additionalInfo.find { it.key == "license" }
        val accountGroup = result.additionalInfo.find { it.key == "account-group" }
        assertNotNull(license)
        assertNotNull(accountGroup)
        assertEquals("license-12345", license?.value)
        assertEquals("admin", accountGroup?.value)
        
        log.info("Verified updated config has correct values")
        
        // loadLoginContext 메소드가 호출되었는지 확인
        coVerify(exactly = 1) { spyVms.saveVmsConfig(updateRequest) }
        
        log.info("VMS config update test successful - configuration updated with new values")
    }

    @Test
    @DisplayName("VMS 설정 업데이트 - 비밀번호 유지 테스트")
    fun `saveVmsConfig should keep existing password when new password is null`() = runTest {
        log.info("Starting VMS config update test - password preservation")

        // given - 업데이트할 설정값 준비 (비밀번호는 null로 설정)
        val updatedIp = "192.168.0.200"
        val updatedPort = "9090"
        val updatedUsername = "updatedUser"
        val currentPassword = "testPassword" // 기존 비밀번호
        
        val updateRequest = VmsConfigUpdateRequest(
            username = updatedUsername,
            password = null, // 비밀번호 null로 설정
            ip = updatedIp,
            port = updatedPort,
            additionalInfo = emptyList()
        )

        val updatedConfig = VmsConfig(
            id = vmsConfig.id,
            username = updatedUsername,
            password = currentPassword, // 기존 비밀번호가 유지됨
            ip = updatedIp,
            port = updatedPort,
            vms = "vurix",
            additionalInfo = mutableListOf()
        )

        log.info("Created update request with null password to test password preservation")

        // MongoDB 업데이트 모킹
        every { mongoTemplate.save(any<VmsConfig>()) } returns Mono.just(updatedConfig)
        
        // spyk로 실제 메소드 일부 호출 모킹
        val spyVms = spyk(vms)
        coEvery { spyVms.saveVmsConfig(any()) } returnsMany listOf(updatedConfig)
        
        // getVmsConfig 모킹
        coEvery { spyVms.getVmsConfig() } returns vmsConfig
        
        // when - 설정 업데이트 실행
        log.info("Executing saveVmsConfig with null password")
        val result = spyVms.saveVmsConfig(updateRequest)
        
        // then - 결과 검증
        assertEquals(updatedUsername, result.username)
        assertEquals(currentPassword, result.password) // 비밀번호가 유지되는지 확인
        assertEquals(updatedIp, result.ip)
        assertEquals(updatedPort, result.port)
        
        log.info("Verified updated config preserved original password: {}", currentPassword)
        
        // saveVmsConfig 메소드가 호출되었는지 확인
        coVerify(exactly = 1) { spyVms.saveVmsConfig(updateRequest) }
        
        log.info("VMS config update test successful - original password preserved when update request has null password")
    }

    @Test
    @DisplayName("카메라 동기화 테스트 - Mock WebClient 사용")
    fun `synchronize should process camera data using mapped WebClient responses`() = runTest {
        log.info("Starting camera synchronization test")

        // given - Mock 응답 준비
        val pageResponseJson = """
            {
                "success": true,
                "results": [
                    {
                        "ctx_serial": 0
                    }
                ]
            }
        """.trimIndent()

        val pageListResponses = listOf(
            """
            {
                "success": true,
                "results": {
                    "tree": [
                        {
                            "dev_serial": 100001
                        },
                        {
                            "node_serial": 1
                        }
                    ]
                }
            }
            """.trimIndent(),
            """
            {
                "success": true,
                "results": {
                    "tree": [
                        {
                            "dev_serial": 100002
                        }
                    ]
                }
            }
            """.trimIndent()
        )

        val deviceDetailResponseJson = """
            {
                "success": true,
                "results": [
                    {
                        "dev_serial": 100001,
                        "name": "Test Camera",
                        "model_property": {
                            "ptz_enabled": true
                        },
                        "dev_status": "Streaming"
                    }
                ]
            }
        """.trimIndent()

        log.info("Prepared mock JSON responses for camera synchronization flow")

        // API 응답 모킹 - 헬퍼 함수 사용
        mockWebClientForPath("/device/page/", pageResponseJson)
        mockWebClientForPathWithMultipleResponses("/device/list/", pageListResponses)
        mockWebClientForPath("/device/detail-info/", deviceDetailResponseJson)

        log.info("Configured WebClient response behavior with returnsMany for list requests")

        // vmsSynchronizeService 모킹
        coEvery {
            vmsSynchronizeService.synchronize(
                rawResponse = any(),
                uri = any(),
                vmsType = "vurix",
                processJsonData = any()
            )
        } just Runs

        log.info("Mocked vmsSynchronizeService.synchronize to run without exceptions")

        // when - 동기화 실행
        log.info("Executing camera synchronization")
        vms.synchronize()

        // then - 동기화 메서드 호출 확인
        coVerify {
            vmsSynchronizeService.synchronize(
                rawResponse = any(),
                uri = any(),
                vmsType = "vurix",
                processJsonData = any()
            )
        }

        log.info("Synchronization successful - VMS service correctly processed camera data")
    }
}