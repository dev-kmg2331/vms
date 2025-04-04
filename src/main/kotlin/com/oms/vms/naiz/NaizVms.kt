package com.oms.vms.naiz

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.oms.logging.gson.gson
import com.oms.vms.DefaultVms
import com.oms.vms.VmsSynchronizeUtil
import com.oms.vms.config.VmsConfig
import com.oms.vms.persistence.mongo.repository.ReactiveMongoRepo
import kotlinx.coroutines.reactor.awaitSingle
import org.json.XML
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class NaizVms(
    @Qualifier("naiz")
    private val webClient: WebClient,
    private val vmsConfig: VmsConfig,
    private val mongoRepo: ReactiveMongoRepo
) : DefaultVms() {

    override suspend fun download() {
    }

    override suspend fun synchronize() {
        try {
            val uri = "/camera/list.cgi"
            val rawXML = callVmsApi(
                webClient = webClient,
                uri = "$uri?id=${vmsConfig.id}&password=${vmsConfig.password}&key=all&method=get"
            ).awaitSingle()
            val response = XML.toJSONObject(rawXML).toMap()
            VmsSynchronizeUtil.synchronize(
                rawResponse = gson.toJson(response),
                uri = uri,
                vmsType = "naiz",
                mongoRepo = mongoRepo,
                processJsonData = ::extractActualData
            )

        } catch (e: Exception) {
            log.error("$e", e)
        }
    }

    fun extractActualData(it: String): List<JsonObject> {
        val json = gson.fromJson(it, TypeToken.get(JsonObject::class.java))
        return json
            .getAsJsonObject("Camera")
            .getAsJsonObject("CameraList")
            .getAsJsonArray("CameraListItem")
            .map { o -> o.asJsonObject }
    }
}