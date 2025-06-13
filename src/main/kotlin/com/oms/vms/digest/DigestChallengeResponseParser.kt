package com.oms.vms.digest

import com.oms.api.exception.ApiAccessException
import org.springframework.http.HttpStatus
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object DigestChallengeResponseParser {
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
        method: String = "GET",
        username: String,
        password: String,
        authParams: Map<String, String>,
        uri: String?
    ): String {
        try {
            val realm = authParams["realm"] ?: throw RuntimeException("Realm is required for Digest authentication")
            val nonce = authParams["nonce"] ?: throw RuntimeException("Nonce is required for Digest authentication")
            val qop = authParams["qop"] // qop는 선택적
            val opaque = authParams["opaque"] // opaque는 선택적
            val algorithm = authParams.getOrDefault("algorithm", "MD5") // 기본적으로 MD5를 사용

            // HA1 = hash(username:realm:password)
            val ha1 = hash("$username:$realm:$password", algorithm)

            // HA2 = hash(method:digestURI)
            val ha2 = hash("$method:$uri", algorithm)

            // 응답 생성 - qop 유무에 따라 다르게 처리
            val response: String
            val authHeader =
                StringBuilder("Digest username=\"$username\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\"")

            if (qop != null) {
                // qop가 있는 경우 (RFC 2617 표준)
                val nc = "00000001" // 요청 카운터
                val cnonce = nonce // 간단하게 nonce를 cnonce로 사용
                response = hash("$ha1:$nonce:$nc:$cnonce:$qop:$ha2", algorithm)

                authHeader.append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
            } else {
                // qop가 없는 경우 (RFC 2069 호환)
                response = hash("$ha1:$nonce:$ha2", algorithm)
            }

            authHeader.append(", response=\"$response\"")

            if (opaque != null) {
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