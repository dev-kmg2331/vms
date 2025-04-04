package com.oms.vms

import com.oms.vms.config.VmsConfig
import com.oms.vms.emstone.EmstoneNvr
import com.oms.vms.persistence.mongo.repository.ReactiveMongoRepo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClient
import java.util.*
import kotlin.test.Test

@SpringBootTest
@ActiveProfiles("test")
class UnifiedCameraServiceTest {

    @Autowired
    private lateinit var service: UnifiedCameraService

    @Autowired
    private lateinit var reactiveMongoRepo: ReactiveMongoRepo

    private lateinit var vms: Vms

    private fun setEmstoneNvr() {
        val vmsConfig = VmsConfig(
            id = "admin",
            password = "oms20190211",
            ip = "192.168.182.200",
            port = "80"
        )

        val authToken =
            Base64.getEncoder().encodeToString("${vmsConfig.id}:${vmsConfig.password}".toByteArray(Charsets.UTF_8))

        val webClient = WebClient.builder()
            .baseUrl("http://${vmsConfig.ip}:${vmsConfig.port}")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $authToken")
            .build()

        vms = EmstoneNvr(webClient, vmsConfig, reactiveMongoRepo)
    }

    @Test
    fun it_should_work() = runTest {
        setEmstoneNvr()

        vms.synchronize()

        service.synchronizeVmsData("emstone")
    }
}

