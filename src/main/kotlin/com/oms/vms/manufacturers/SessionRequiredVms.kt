package com.oms.vms.manufacturers

import com.oms.vms.Vms
import com.oms.vms.service.VmsSynchronizeService
import org.springframework.beans.factory.InitializingBean
import org.springframework.core.env.Environment
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.function.Consumer

abstract class SessionRequiredVms(
    protected val environment: Environment,
    mongoTemplate: ReactiveMongoTemplate,
    vmsSynchronizeService: VmsSynchronizeService,
    httpMethod: HttpMethod = HttpMethod.GET,
//    siteRepo: SiteRepo,
//    cameraRepo: CameraRepo
) : DefaultVms(mongoTemplate,vmsSynchronizeService), Vms, LoginRequired, SessionRequired, InitializingBean {

    protected val sessionClient: WebClient = WebClient.builder().baseUrl("http://localhost").build()
    private var httpMethod: HttpMethod = httpMethod
    private val sessionUri: String = environment.getProperty("vms.session.refresh-uri")!!
    protected val sessionPeriod: Long = environment.getProperty("vms.session.refresh-on", Long::class.java)!!
    protected val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    override fun <T> refreshSession(headersConsumer: Consumer<HttpHeaders>, body: T) {
        val requestSpec = sessionClient.method(this.httpMethod)
            .uri(this.sessionUri)
            .headers(headersConsumer)
            .accept(MediaType.APPLICATION_JSON)

        if (body != null) requestSpec.bodyValue(body)

        requestSpec
            .retrieve()
            .onStatus({ it.is4xxClientError }) { clientResponse ->
                clientResponse.bodyToMono(String::class.java).map { RuntimeException(it) }
            }
            .bodyToMono(String::class.java)
            .retryWhen(
                Retry.fixedDelay(1, Duration.ofSeconds(3))
                    .doBeforeRetry { log.info("retrying session refresh request") }
                    .onRetryExhaustedThrow { _, retrySignal -> retrySignal.failure() }
            )
            .doOnError { thr -> log.info("session refresh failed. ${thr.localizedMessage}") }
            .subscribe()
    }

    protected fun setHttpMethod(httpMethod: HttpMethod) {
        this.httpMethod = httpMethod
    }
}