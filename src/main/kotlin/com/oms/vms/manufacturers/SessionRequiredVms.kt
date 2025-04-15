package com.oms.vms.manufacturers

import com.oms.vms.Vms
import com.oms.vms.service.VmsSynchronizeService
import org.springframework.beans.factory.InitializingBean
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

abstract class SessionRequiredVms(
    mongoTemplate: ReactiveMongoTemplate,
    vmsSynchronizeService: VmsSynchronizeService,
    private val httpMethod: HttpMethod = HttpMethod.GET,
//    siteRepo: SiteRepo,
//    cameraRepo: CameraRepo
) : DefaultVms(mongoTemplate, vmsSynchronizeService), Vms, LoginRequired, SessionRequired, InitializingBean {

    abstract var sessionClient: WebClient

    private val sessionPeriod = 60L
    private var scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    override fun <T> refreshSession(uri: String, headers: Map<String, String>, body: T) {
        val requestSpec = sessionClient.method(this.httpMethod)
            .uri(uri)
            .headers { it.setAll(headers) }
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
            .doOnError { thr -> log.error("session refresh failed. ${thr.localizedMessage}") }
            .subscribe()
    }

    protected fun scheduleJob(job: () -> Unit) {
        log.info("$type scheduleJob started")
        if (!scheduler.isShutdown) {
//            scheduler.shutdownNow()
            scheduler = Executors.newSingleThreadScheduledExecutor()
        }

        scheduler.scheduleAtFixedRate(job, 0, sessionPeriod, TimeUnit.SECONDS)
    }
}