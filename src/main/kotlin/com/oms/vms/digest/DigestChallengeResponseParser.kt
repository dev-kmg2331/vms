package com.oms.vms.digest

import com.oms.api.exception.ApiAccessException
import org.springframework.http.HttpStatus
import java.util.concurrent.atomic.AtomicInteger

class DigestChallengeResponseParser {
    private val counter = AtomicInteger(0)

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
        method: String = "DESCRIBE", // RTSP용으로 DESCRIBE 변경
        username: String,
        password: String,
        authParams: Map<String, String>,
        uri: String?
    ): String {
        try {
            val realm = authParams["realm"] ?: throw RuntimeException("Realm is required for Digest authentication")
            val nonce = authParams["nonce"] ?: throw RuntimeException("Nonce is required for Digest authentication")
            val qop = authParams["qop"]?.replace("\"", "")?.trim() // 따옴표 제거
            val opaque = authParams["opaque"]?.replace("\"", "")?.trim() // 따옴표 제거
            val algorithm = authParams.getOrDefault("algorithm", "MD5").replace("\"", "").trim()

            // HA1 = hash(username:realm:password)
            val ha1 = hash("$username:$realm:$password", algorithm)

            // HA2 = hash(method:digestURI)
            val ha2 = hash("$method:$uri", algorithm)

            // 응답 생성 - qop 유무에 따라 다르게 처리
            val response: String
            val authHeader =
                StringBuilder("Digest username=\"$username\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\"")

            if (!qop.isNullOrEmpty()) {
                // qop가 있는 경우 (RFC 2617 표준)
                val nc = String.format("%08d", counter.addAndGet(1)) // 요청 카운터
                val cnonce = generateCnonce() // 랜덤 cnonce 생성
                response = hash("$ha1:$nonce:$nc:$cnonce:$qop:$ha2", algorithm)

                authHeader.append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
            } else {
                // qop가 없는 경우 (RFC 2069 호환)
                response = hash("$ha1:$nonce:$ha2", algorithm)
            }

            authHeader.append(", response=\"$response\"")

            if (opaque != null && opaque.isNotEmpty()) {
                authHeader.append(", opaque=\"$opaque\"")
            }

            if (algorithm != "MD5") {
                authHeader.append(", algorithm=$algorithm")
            }

            return authHeader.toString()
        } catch (e: Exception) {
            throw ApiAccessException(HttpStatus.INTERNAL_SERVER_ERROR, e, "Error creating Digest auth header. ${e.message}")
        }
    }

    // 해시 함수 - SHA-256 지원 추가
    private fun hash(input: String, algorithm: String): String {
        val digest = when (algorithm.uppercase()) {
            "MD5" -> java.security.MessageDigest.getInstance("MD5")
            "SHA-256", "SHA256" -> java.security.MessageDigest.getInstance("SHA-256")
            else -> java.security.MessageDigest.getInstance("MD5")
        }
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // cnonce 랜덤 생성 함수
    private fun generateCnonce(): String {
        val random = java.security.SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}