package com.oms.vms.manufacturers.hanwha

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.oms.logging.gson.gson
import com.oms.vms.VmsType
import com.oms.vms.digest.DigestAuthenticatorClient
import com.oms.vms.manufacturers.DefaultVms
import com.oms.vms.service.VmsSynchronizeService
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component(value = "hanwha")
class HanwhaVisionNvr(
    mongoTemplate: ReactiveMongoTemplate,
    vmsSynchronizeService: VmsSynchronizeService
) : DefaultVms(mongoTemplate, vmsSynchronizeService) {
    override val type = VmsType.HANWHA_VISION.serviceName

    override var webClient = WebClient.builder()
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    private val om = ObjectMapper()

    override suspend fun download() {
        TODO("Not yet implemented")
    }

    override suspend fun synchronize() {
        val vmsConfig = getVmsConfig()

        webClient = webClient.mutate()
            .baseUrl("http://${vmsConfig.ip}:${vmsConfig.port}")
            .build()

        val client = DigestAuthenticatorClient(webClient, vmsConfig.username, vmsConfig.password)
        val uri = "/stw-cgi/media.cgi?msubmenu=cameraregister&action=view"

        val rawResponse = client.makeRequest(uri).awaitSingle()

        vmsSynchronizeService.synchronize(
            vmsType = type,
            uri = uri,
            rawResponse = rawResponse,
            processJsonData = processJsonData
        )
    }

    override suspend fun getRtspURL(id: String): String {
        return ""
    }

    private val processJsonData: (String) -> List<JsonObject> = { responseText ->
        JsonParser.parseString(responseText).asJsonObject["RegisteredCameras"].asJsonArray.map { it.asJsonObject }
    }

    private data class CameraSync(
        @JsonProperty("RegisteredCameras") val cameras: List<Data>
    ) {
        data class Data(
            @JsonProperty("Channel") val channel: Int,
            @JsonProperty("Model") val model: String,
            @JsonProperty("Title") val title: String,
            @JsonProperty("UserID") val userID: String,
            @JsonProperty("Protocol") val protocol: String,
            @JsonProperty("TLS") val tls: String,
            @JsonProperty("AuthenticationType") val authenticationType: String,
            @JsonProperty("StreamingMode") val streamingMode: String,
            @JsonProperty("VideoState") val videoState: String,
            @JsonProperty("AudioState") val audioState: String,
            @JsonProperty("AddressType") val addressType: String,
            @JsonProperty("IPAddress") val ipAddress: String,
            @JsonProperty("HTTPPort") val httpPort: Int,
            @JsonProperty("CamChannel") val camChannel: Int,
            @JsonProperty("Status") val status: String,
            @JsonProperty("DataRate") val dataRate: Double,
            @JsonProperty("CPUUsage") val cpuUsage: Double,
            @JsonProperty("IsBypassSupported") val isBypassSupported: Boolean,
            @JsonProperty("PoeStatus") val poeStatus: Boolean
        )
    }
}