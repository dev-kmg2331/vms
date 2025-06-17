package com.oms.vms.digest

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.*
import reactor.core.publisher.Mono


class DigestAuthenticatorClient(
    private val webClient: WebClient,
    private val username: String,
    private val password: String,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val digestChallengeResponseParser = DigestChallengeResponseParser()

    fun makeRequest(uri: String, httpMethod: HttpMethod = HttpMethod.GET): Mono<String> {
        // 1. 첫 번째 요청: 서버로부터 챌린지 응답을 받음

        return webClient.method(httpMethod)
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
                log.info("Authentication header: {}", authHeader)
                if (authHeader.startsWith("Digest")) {
                    // 2. 챌린지 분석 및 인증 헤더 생성
                    val authParams: Map<String, String> =
                        digestChallengeResponseParser.parseDigestChallenge(authHeader)
                    val digestAuthHeader: String =
                        digestChallengeResponseParser.createDigestAuthHeader(
                            httpMethod.name(), username, password, authParams, uri
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
}