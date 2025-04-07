package com.oms.vms.config

import com.oms.ExcludeInTestProfile
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.core.env.get
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.*
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.net.ssl.SSLException

@Configuration
class VmsWebClientConfig(
    private val vmsConfig: VmsConfig,
    private val environment: Environment
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Bean
    @Primary
    fun defaultWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl("http://${vmsConfig.ip}")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    @Bean
    @Qualifier("realhub")
    @ExcludeInTestProfile
    fun realhubWebClient(): WebClient {
        val serverAddr = environment["api.url.relay"] ?: "localhost:8000"

        val webClient = WebClient.builder()
            .baseUrl(serverAddr)
            .build()

        val authKey = webClient
            .post()
            .uri("/login")
            .retrieve()
            .bodyToMono(String::class.java)
            .onErrorResume(WebClientRequestException::class.java) { e: WebClientRequestException ->
                log.error("relay server connection failed! {}", e.localizedMessage)
                Mono.just<String>("{\"key\":\"\"}")
            }
            .doOnSuccess { log.info("realhub login success: $it") }
            .map { JSONObject(it).getString("key") }
            .block()

        return webClient.mutate().defaultHeader("nvr-auth-key", authKey).build()
    }

    @Bean
    @Qualifier("emstone")
    fun emstoneWebClient(): WebClient {
        val authToken =
            Base64.getEncoder().encodeToString("${vmsConfig.id}:${vmsConfig.password}".toByteArray(Charsets.UTF_8))

        return WebClient.builder()
            .baseUrl("http://${vmsConfig.ip}:${vmsConfig.port}") //                .baseUrl("http://" + "116.122.16.31:28080/")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $authToken")
            .build()
    }


    @Bean
    @Qualifier("naiz")
    @Throws(SSLException::class)
    fun naizWebClient(): WebClient {
        val sslContext = SslContextBuilder
            .forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build()

        val httpClient = HttpClient.create().secure { it.sslContext(sslContext) }

        val exchangeStrategies = ExchangeStrategies.builder()
            .codecs { clientCodecConfigurer: ClientCodecConfigurer ->
                clientCodecConfigurer.defaultCodecs().maxInMemorySize(-1)
            } // web client buffer 사이즈 제한 없음
            .build()

        return WebClient.builder()
            .exchangeStrategies(exchangeStrategies)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .baseUrl("http://${vmsConfig.ip}:${vmsConfig.port}")
            .build()
    }
}

open class DigestAuthenticatorClient(
    private val webClient: WebClient,
    username: String,
    password: String,
) {
    private val digestChallengeResponseParser: DigestChallengeResponseParser =
        DigestChallengeResponseParser(username, password)
    private val log = LoggerFactory.getLogger(this::class.java)

    fun makeRequest(uri: String, httpMethod: HttpMethod = HttpMethod.GET): Mono<String> {
        // 1. 첫 번째 요청: 서버로부터 챌린지 응답을 받음

        return webClient.get()
            .uri(uri)
            .retrieve()
            .onStatus(
                { obj: HttpStatusCode -> obj.is4xxClientError },
                { Mono.error(Digest401ErrorException(it.headers())) })
            .bodyToMono(String::class.java)
            .onErrorResume(
                Digest401ErrorException::class.java
            ) { e: Digest401ErrorException ->
                val authHeader = e.getHeader(HttpHeaders.WWW_AUTHENTICATE)
                if (authHeader.startsWith("Digest")) {
                    // 2. 챌린지 분석 및 인증 헤더 생성
                    val authParams: Map<String, String> =
                        digestChallengeResponseParser.parseDigestChallenge(authHeader)
                    val digestAuthHeader: String =
                        digestChallengeResponseParser.createDigestAuthHeader(
                            authParams, uri, httpMethod
                        )

                    // 3. 인증 헤더와 함께 다시 요청
                    return@onErrorResume webClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, digestAuthHeader)
                        .retrieve()
                        .onStatus(
                            { obj: HttpStatusCode -> obj.is4xxClientError },
                            { res -> res.bodyToMono(String::class.java).doOnNext(log::info) .flatMap { res.createException() } }
                        )
                        .bodyToMono(String::class.java)
                }
                Mono.error(RuntimeException("No Digest Auth challenge received"))
            }
    }

    class Digest401ErrorException(private val headers: ClientResponse.Headers) : Throwable() {
        fun getHeader(header: String): String {
            return headers.asHttpHeaders().getFirst(header)!!
        }
    }

    internal class DigestChallengeResponseParser(
        private val username: String,
        private val password: String
    ) {
        fun parseDigestChallenge(authHeader: String): Map<String, String> {
            val authParams: MutableMap<String, String> = HashMap()
            val tokens =
                authHeader.replaceFirst("Digest ".toRegex(), "").split(", ".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()

            for (token in tokens) {
                val keyValue = token.split("=".toRegex(), limit = 2).toTypedArray()
                val key = keyValue[0]
                val value = keyValue[1].replace("\"", "")
                authParams[key] = value
            }

            return authParams
        }

        fun createDigestAuthHeader(
            authParams: Map<String, String>,
            uri: String?,
            method: HttpMethod
        ): String {
            try {
                val realm = authParams["realm"]
                val nonce = authParams["nonce"]
                val qop = authParams["qop"]
                val opaque = authParams["opaque"]
                val algorithm = authParams.getOrDefault("algorithm", "MD5") // 기본적으로 MD5를 사용
                val nc = "00000001" // request counter, 첫 요청이므로 1로 설정

                // HA1 = hash(username:realm:password)
                val ha1 = hash(String.format("%s:%s:%s", username, realm, password), algorithm)

                // HA2 = hash(method:digestURI)
                val ha2 = hash(String.format("%s:%s", method.name(), uri), algorithm)

                // response = hash(HA1:nonce:nc:cnonce:qop:HA2)
                val response =
                    hash(String.format("%s:%s:%s:%s:%s:%s", ha1, nonce, nc,  /*cnonce*/nonce, qop, ha2), algorithm)

                return String.format(
                    "Digest algorithm=%s, username=\"%s\", realm=\"%s\", nonce=\"%s\", uri=\"%s\", qop=%s, nc=%s, cnonce=\"%s\", response=\"%s\", opaque=\"%s\"",
                    algorithm, username, realm, nonce, uri, qop, nc,  /*cnonce*/nonce, response, opaque
                )
            } catch (e: NoSuchAlgorithmException) {
                throw java.lang.RuntimeException("Error creating Digest auth header", e)
            } catch (e: NullPointerException) {
                throw java.lang.RuntimeException("Error creating Digest auth header", e)
            }
        }

        @Throws(NoSuchAlgorithmException::class)
        private fun hash(data: String, algorithm: String?): String {
            val md = MessageDigest.getInstance(algorithm)
            val digest = md.digest(data.toByteArray(StandardCharsets.UTF_8))
            return bytesToHex(digest)
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val sb = StringBuilder()
            for (b in bytes) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }
    }
}