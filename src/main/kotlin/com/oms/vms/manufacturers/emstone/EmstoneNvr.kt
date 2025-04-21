package com.oms.vms.manufacturers.emstone

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.oms.api.exception.ApiAccessException
import com.oms.logging.gson.gson
import com.oms.vms.manufacturers.DefaultVms
import com.oms.vms.mongo.docs.VMS_CAMERA
import com.oms.vms.mongo.docs.VmsConfig
import com.oms.vms.service.VmsSynchronizeService
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.bson.Document
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

@Component(value = "emstone")
class EmstoneNvr(
    mongoTemplate: ReactiveMongoTemplate,
    vmsSynchronizeService: VmsSynchronizeService
) : DefaultVms(mongoTemplate, vmsSynchronizeService) {
    override val type: String = "emstone"

    override var webClient: WebClient = WebClient.builder()
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()


    private fun authToken(vmsConfig: VmsConfig): String = let {
        Base64.getEncoder().encodeToString("${vmsConfig.username}:${vmsConfig.password}".toByteArray(Charsets.UTF_8))
    }

    override suspend fun synchronize() {
        val vmsConfig = getVmsConfig()
        val uri = "/api/cameras"
        val response =
            callVmsApi(
                uri = uri,
                headers = { headers -> headers.add(HttpHeaders.AUTHORIZATION, "Basic ${authToken(vmsConfig)}") }
            )

        vmsSynchronizeService.synchronize(
            rawResponse = response,
            uri = uri,
            vmsType = "emstone",
            processJsonData = {
                val json = gson.fromJson(it, TypeToken.get(JsonObject::class.java))
                json["cameras"].asJsonArray.map { o -> o.asJsonObject }
            }
        )
    }

    override suspend fun getRtspURL(id: String): String {
        val vmsConfig = getVmsConfig()

        val vmsCamera = mongoTemplate.find(
            Query.query(Criteria.where("vms").`is`(type).and("_id").`is`(id)),
            Document::class.java,
            VMS_CAMERA
        ).awaitFirstOrNull() ?: throw ApiAccessException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "$type vms camera not found. _id: $id"
        )

        val emstoneId = vmsCamera["id"] ?: throw ApiAccessException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "id property not found. vms camera: $vmsCamera"
        )

        val rtspUrl = "rtsp://${vmsConfig.username}:${vmsConfig.password}@${vmsConfig.ip}/video$emstoneId"

        return rtspUrl
    }

    override suspend fun download() {
        TODO("Not yet implemented")
    }
}