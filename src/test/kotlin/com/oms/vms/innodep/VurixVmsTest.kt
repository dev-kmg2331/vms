package com.oms.vms.innodep

import com.oms.vms.WithMongoDBTestContainer
import com.oms.vms.manufacturers.innodep.VurixVms
import com.oms.vms.mongo.docs.VmsAdditionalInfo
import com.oms.vms.mongo.docs.VmsConfig
import com.oms.vms.service.VmsSynchronizeService
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.test.context.ActiveProfiles
import java.util.*
import kotlin.test.Test

/**
 * Vurix VMS 실제 WebClient 테스트
 *
 * 이노뎁 Vurix VMS의 기능을 실제 WebClient를 사용하여 테스트합니다.
 * MockWebServer를 사용하여 HTTP 응답을 시뮬레이션합니다.
 */
@SpringBootTest
@ExtendWith(MockKExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
@ActiveProfiles("test")
class VurixVmsTest : WithMongoDBTestContainer {
    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate
    @Autowired
    private lateinit var vmsSynchronizeService: VmsSynchronizeService

    private lateinit var vms: VurixVms

    @BeforeEach
    fun beforeEach(): Unit = runBlocking {
        mongoTemplate.save(
            VmsConfig(
                id = UUID.randomUUID().toString(),
                username = "sdk",
                password = "Innodep1@",
                ip = "211.171.190.220",
                port = "16118",
                vms = "vurix",
                additionalInfo = mutableListOf(
                    VmsAdditionalInfo("license", "licNormalClient"),
                    VmsAdditionalInfo("account-group", "group1")
                )
            )
        ).awaitSingle()


        vms = VurixVms(mongoTemplate, vmsSynchronizeService)
        vms.afterPropertiesSet()
    }

    @Test
    fun `config`() = runTest {
        vms.synchronize()
    }
}
