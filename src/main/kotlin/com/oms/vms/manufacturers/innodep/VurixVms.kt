package com.oms.vms.manufacturers.innodep

import com.google.gson.JsonParser
import com.oms.api.exception.ApiAccessException
import com.oms.vms.VmsType
import com.oms.vms.endpoint.VmsConfigUpdateRequest
import com.oms.vms.manufacturers.SessionRequiredVms
import com.oms.vms.mongo.docs.VmsConfig
import com.oms.vms.service.VmsSynchronizeService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import org.bson.Document
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component(value = "vurix")
class VurixVms(
    mongoTemplate: ReactiveMongoTemplate,
    vmsSynchronizeService: VmsSynchronizeService
) : SessionRequiredVms(vmsSynchronizeService = vmsSynchronizeService, mongoTemplate = mongoTemplate) {

    override var webClient: WebClient = WebClient.builder()
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    override var sessionClient: WebClient = WebClient.builder()
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    override val type: String = VmsType.VURIX.serviceName

    lateinit var apiAccessDetail: ApiAccessDetail

    override fun afterPropertiesSet() {
        try {
            val vmsConfig = getVmsConfig()
            loadLoginContext(vmsConfig)
        } catch (e: Throwable) {
            log.error("VURIX VMS SERVICE UNAVAILABLE!!! LOGIN FAILED!!! MESSAGE: ${e.message}")
        }
    }

    override suspend fun download() {
        TODO("Not yet implemented")
    }

    override suspend fun synchronize() {
        val uri = "/device/page/${apiAccessDetail.userSerial}"
        val rawResponse = webClient.get()
            .uri(uri)
            .retrieve()
            .bodyToMono(String::class.java)
            .awaitSingle()

        log.info("response: $rawResponse")

        val response = Document.parse(rawResponse)

        if (!response.getBoolean("success"))
            throw ApiAccessException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "synchronize failed. Vurix api responded with failure."
            )

        val page = response.getList("results", Document::class.java)[0]

        val pageResult = webClient.get()
            .uri("/device/list/${apiAccessDetail.userSerial}/${page["ctx_serial"]}")
            .retrieve()
            .bodyToMono(String::class.java)
            .map { r -> Document.parse(r) }
            .awaitSingle()

        val rawCameras = mutableListOf<String>()

        browsePages(pageResult, rawCameras)

        vmsSynchronizeService.synchronize(
            rawResponse = rawCameras.toString(),
            uri = uri,
            vmsType = type,
            processJsonData = {
                JsonParser.parseString(it).asJsonArray
                    .map { obj ->
                        log.info("$obj")
                        val json = obj.asJsonObject

                        val isStreaming = json["dev_status"].asString == "Streaming"
                        json.addProperty("streaming", isStreaming)

                        val hasPtz = json["model_property"].asJsonObject["ptz_enabled"].asBoolean
                        json.addProperty("ptz_enabled", hasPtz)

                        json
                    }
            }
        )
    }

    private suspend fun browsePages(response: Document, rawCameras: MutableList<String>): Unit = coroutineScope {
        if (response.getBoolean("success") == null) {
            log.error("vurix response format error. response: $response")
            throw ApiAccessException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "synchronize failed. Vurix api responded with failure."
            )
        }

        if (!response.getBoolean("success"))
            throw ApiAccessException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "synchronize failed. Vurix api responded with failure."
            )

        val results = (response["results"] as Document).getList("tree", Document::class.java)

        results.map {
            async {
                if (it.containsKey("node_serial")) {
                    log.info("SEARCHING FOR NODE ${it["node_serial"]}")
                    val resp = webClient.get()
                        .uri("/device/list/${apiAccessDetail.userSerial}/${it["ctx_serial"]}/${it["node_serial"]}")
                        .retrieve()
                        .bodyToMono(String::class.java)
                        .map { r -> Document.parse(r) }
                        .awaitSingle()

                    browsePages(resp, rawCameras)
                } else {
                    val device = webClient.get()
                        .uri("/device/detail-info/${it["dev_serial"]}")
                        .retrieve()
                        .bodyToMono(String::class.java)
                        .map { r -> Document.parse(r).getList("results", Document::class.java)[0] }
                        .awaitSingle()

                    rawCameras.add(device.toJson())
                }
            }
        }.awaitAll()
    }


    override suspend fun getRtspURL(id: String): String {
        return ""
    }

    override suspend fun saveVmsConfig(vmsConfigRequest: VmsConfigUpdateRequest): VmsConfig {
        val password = vmsConfigRequest.password ?: getVmsConfig().password

        val newConfig = VmsConfig(
            username = vmsConfigRequest.username,
            password = password,
            ip = vmsConfigRequest.ip,
            port = vmsConfigRequest.port,
            vms = type,
            additionalInfo = vmsConfigRequest.additionalInfo.toMutableList()
        )

        loadLoginContext(newConfig)

        val vmsConfig = super.saveVmsConfig(vmsConfigRequest)

        return vmsConfig
    }

    fun loadLoginContext(vmsConfig: VmsConfig) {
        try {
            sessionClient = sessionClient.mutate()
                .baseUrl("http://${vmsConfig.ip}:${vmsConfig.port}/api")
                .defaultHeaders {
                    it[HttpHeaders.ACCEPT] = MediaType.APPLICATION_JSON_VALUE
                    it["x-account-id"] = vmsConfig.username
                    it["x-account-pass"] = vmsConfig.password
                    it["x-license"] = vmsConfig.getAdditionalInfo("license")
                    it["x-account-group"] = vmsConfig.getAdditionalInfo("account-group")
                }
                .defaultRequest { reqHeader -> reqHeader.accept(MediaType.APPLICATION_JSON) }
                .build()

            login(sessionClient).block()
        } catch (e: Exception) {
            log.error(e.message)
            throw ApiAccessException(HttpStatus.INTERNAL_SERVER_ERROR, "check your vms config. ${e.message}")
        }

        scheduleJob {
            super.refreshSession(
                "/keep-alive",
                mapOf("x-auth-token" to apiAccessDetail.authToken),
                null
            )
        }

        this.webClient = webClient.mutate()
            .baseUrl("http://${vmsConfig.ip}:${vmsConfig.port}/api")
            .defaultHeader("x-auth-token", apiAccessDetail.authToken)
            .defaultHeader("x-api-serial", apiAccessDetail.apiSerial)
            .defaultRequest { reqHeader -> reqHeader.accept(MediaType.APPLICATION_JSON) }
            .build()
    }

    override fun login(sessionClient: WebClient): Mono<Void> {
        return sessionClient.get()
            .uri("/login?force-login=true")
            .retrieve()
            .onStatus(
                { obj: HttpStatusCode -> obj.is4xxClientError },
                { clientResponse ->
                    clientResponse.bodyToMono(String::class.java).map { RuntimeException("vurix login failed.") }
                }
            )
            .bodyToMono(String::class.java)
            .mapNotNull { JsonParser.parseString(it) }
            .doOnSuccess { json ->
                val resp = json.asJsonObject["results"].asJsonObject

                apiAccessDetail = ApiAccessDetail(
                    resp["auth_token"].asString,
                    resp["user_serial"].asString,
                    resp["api_serial"].asString
                )

                log.info("vurix login success. $apiAccessDetail")
            }
            .then()
    }

    data class ApiAccessDetail(
        val authToken: String,
        val userSerial: String,
        val apiSerial: String,
    )
}