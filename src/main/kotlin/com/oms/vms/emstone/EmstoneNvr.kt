package com.oms.vms.emstone

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.oms.logging.gson.gson
import com.oms.vms.DefaultVms
import com.oms.vms.config.VmsConfig
import com.oms.vms.persistence.mongo.repository.ReactiveMongoRepo
import com.oms.vms.sync.VmsSynchronizeService
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component(value = "emstone")
class EmstoneNvr(
    @Qualifier("emstone")
    private val webClient: WebClient,
    private val vmsConfig: VmsConfig,
    private val reactiveMongoRepo: ReactiveMongoRepo,
    vmsSynchronizeService: VmsSynchronizeService
) : DefaultVms(vmsSynchronizeService) {
    override val type: String = "emstone"

    override suspend fun synchronize() {
        val uri = "/api/cameras"
        val response = callVmsApi(webClient = webClient, uri = uri).awaitSingle()

        vmsSynchronizeService.synchronize(
            rawResponse = response,
            uri = uri,
            vmsType = "emstone",
            mongoRepo = reactiveMongoRepo,
            processJsonData = {
                val json = gson.fromJson(it, TypeToken.get(JsonObject::class.java))
                json["cameras"].asJsonArray.map { o -> o.asJsonObject }
            }
        )
    }

    override suspend fun getRtspURL(id: String): String {
        val rtspUrl = "rtsp://${vmsConfig.id}:${vmsConfig.password}@${vmsConfig.ip}/video{}"
        return rtspUrl
    }

    override suspend fun download() {
        TODO("Not yet implemented")
    }
}