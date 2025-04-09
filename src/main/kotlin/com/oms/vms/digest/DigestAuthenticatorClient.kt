package com.oms.vms.digest

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.*
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*


class DigestAuthenticatorClient(
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
                            { res ->
                                res.bodyToMono(String::class.java).doOnNext(log::info).flatMap { res.createException() }
                            }
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