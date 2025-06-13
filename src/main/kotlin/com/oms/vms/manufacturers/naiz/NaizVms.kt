package com.oms.vms.manufacturers.naiz

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.oms.api.exception.ApiAccessException
import com.oms.logging.gson.gson
import com.oms.vms.manufacturers.DefaultVms
import com.oms.vms.service.VmsSynchronizeService
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.json.XML
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.nio.charset.StandardCharsets

@Component(value = "naiz")
class NaizVms(
    mongoTemplate: ReactiveMongoTemplate,
    vmsSynchronizeService: VmsSynchronizeService
) : DefaultVms(mongoTemplate, vmsSynchronizeService) {
    override var webClient: WebClient = let {
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

        WebClient.builder()
            .exchangeStrategies(exchangeStrategies)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    override val type: String = "naiz"

    override suspend fun download() {
    }

    override suspend fun synchronize() {
        val vmsConfig = getVmsConfig()
        try {
            val uri = "/camera/list.cgi"
            val rawXML = callVmsApi(uri = "$uri?id=${vmsConfig.username}&password=${vmsConfig.password}&key=all&method=get")
            val response = XML.toJSONObject(rawXML).toMap()

            vmsSynchronizeService.synchronize(
                rawResponse = gson.toJson(response),
                uri = uri,
                vmsType = "naiz",
                processJsonData = ::extractActualData
            )

        } catch (e: Exception) {
            log.error("$e", e)
            throw ApiAccessException(HttpStatus.INTERNAL_SERVER_ERROR, "unexpected internal server error.")
        }
    }

    override suspend fun getRtspURL(id: String): String {
        getVmsConfig()
        return ""
    }

    fun extractActualData(it: String): List<JsonObject> {
        val json = gson.fromJson(it, TypeToken.get(JsonObject::class.java))
        val camera = json.getAsJsonObject("Camera") ?: throw ApiAccessException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "key Camera not found in response data. response: $it"
        )

        val cameraList = camera.getAsJsonObject("CameraList") ?: throw ApiAccessException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "key CameraList not found in response data. response: $it"
        )

        val cameraListItem = cameraList.getAsJsonArray("CameraListItem") ?: throw ApiAccessException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "key CameraListItem not found in response data. response: $it"
        )

        return cameraListItem.map { o ->
            val str = String(o.toString().toByteArray(), StandardCharsets.UTF_8)
            JsonParser.parseString(str).asJsonObject
        }
    }
}