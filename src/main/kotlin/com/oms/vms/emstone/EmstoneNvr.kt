package com.oms.vms.emstone

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.oms.logging.gson.gson
import com.oms.vms.DefaultVms
import com.oms.vms.VmsSynchronizeUtil
import com.oms.vms.persistence.mongo.repository.ReactiveMongoRepo
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class EmstoneNvr(
    @Qualifier("emstone")
    private val webClient: WebClient,
    private val reactiveMongoRepo: ReactiveMongoRepo
) : DefaultVms() {

    override suspend fun synchronize() {
        val uri = "/api/cameras"
        val response = callVmsApi(webClient = webClient, uri = uri).awaitSingle()

        VmsSynchronizeUtil.synchronize(
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

    override suspend fun download() {
        TODO("Not yet implemented")
    }
}