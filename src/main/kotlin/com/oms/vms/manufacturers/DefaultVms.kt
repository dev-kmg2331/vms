package com.oms.vms.manufacturers

import com.oms.api.exception.ApiAccessException
import com.oms.vms.Vms
import com.oms.vms.endpoint.VmsCommonApiController
import com.oms.vms.endpoint.VmsConfigUpdateRequest
import com.oms.vms.mongo.docs.VMS_CONFIG
import com.oms.vms.mongo.docs.VmsConfig
import com.oms.vms.service.VmsSynchronizeService
import format
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import reactor.util.retry.Retry.RetrySignal
import java.time.Duration
import java.time.LocalDateTime
import java.util.function.Consumer

abstract class DefaultVms protected constructor(
    protected val mongoTemplate: ReactiveMongoTemplate,
    protected val vmsSynchronizeService: VmsSynchronizeService,
    protected final val executor: ThreadPoolTaskExecutor = ThreadPoolTaskExecutor(),
    poolSize: Int = 16
) : Vms {
    abstract var webClient: WebClient

    protected val log: Logger = LoggerFactory.getLogger(DefaultVms::class.java)

    init {
        executor.apply {
            corePoolSize = poolSize
            maxPoolSize = poolSize * 2
            queueCapacity = 128
            setWaitForTasksToCompleteOnShutdown(false)
            initThreadPool()
        }
    }

    private fun initThreadPool() {
        executor.queueCapacity = 128
        executor.setWaitForTasksToCompleteOnShutdown(false)
        executor.initialize()
    }

    override fun initialize() {
        executor.shutdown()
        executor.initialize()
    }

//    protected fun handleDownloadError(download: VmsDownload): Function<Throwable, Mono<out VmsDownload>> {
//        return Function { ex ->
//            log.error("Download failed. ", ex)
//            when (ex) {
//                is InterruptedException -> Mono.just(download.ofError(DownloadStatus.CANCEL))
//                is ExecutionException -> Mono.just(download.ofError(DownloadStatus.VIDEO_PATH_NULL))
//                is RetryExhaustedException -> Mono.just(download.ofError(DownloadStatus.MAX_RETRY_ERROR))
//                else -> Mono.just(download.ofError(DownloadStatus.NO_RECORDED_AT_THAT_TIME))
//            }
//        }
//    }

    override suspend fun getVmsConfig(includeInactive: Boolean): VmsConfig {
        val await = mongoTemplate.find(
            Query.query(Criteria.where("vms").`is`(type)),
            VmsConfig::class.java,
            VMS_CONFIG
        ).awaitFirstOrNull() ?: throw ApiAccessException(HttpStatus.BAD_REQUEST, "vms config not found.")

        if (!includeInactive) await.apply { if (!this.isActive) throw ApiAccessException(HttpStatus.BAD_REQUEST, "vms config is not active.") }

        return await
    }

    override suspend fun saveVmsConfig(vmsConfigRequest: VmsConfigUpdateRequest): VmsConfig {
        val vmsConfig = mongoTemplate.find(
            Query.query(Criteria.where("vms").`is`(type)),
            VmsConfig::class.java,
            VMS_CONFIG
        ).awaitFirstOrNull()

        return if (vmsConfig != null) {
            vmsConfig.username = vmsConfigRequest.username
            vmsConfig.password = vmsConfigRequest.password ?: vmsConfig.password
            vmsConfig.ip = vmsConfigRequest.ip
            vmsConfig.port = vmsConfigRequest.port
            vmsConfig.updatedAt = LocalDateTime.now().format()
            vmsConfig.additionalInfo = vmsConfigRequest.additionalInfo.toMutableList()

            mongoTemplate.findAndReplace(
                Query.query(Criteria.where("vms").`is`(type)),
                vmsConfig,
            ).awaitSingle()
        } else {
            val password = vmsConfigRequest.password ?: throw ApiAccessException(HttpStatus.BAD_REQUEST, "password is cannot be empty.")

            // 업데이트할 설정 객체 생성
            val newConfig = VmsConfig(
                username = vmsConfigRequest.username,
                password = password,
                ip = vmsConfigRequest.ip,
                port = vmsConfigRequest.port,
                vms = type,
                additionalInfo = vmsConfigRequest.additionalInfo.toMutableList()
            )
            mongoTemplate.save(newConfig).awaitSingle()
        }
    }

    /**
     * VMS 활성화 상태 변경
     * @param active 활성화 여부
     * @return 업데이트된 VMS 설정 정보
     */
    override suspend fun setVmsConfigActive(active: Boolean): VmsConfig {
        val vmsConfig = mongoTemplate.find(
            Query.query(Criteria.where("vms").`is`(type)),
            VmsConfig::class.java,
            VMS_CONFIG
        ).awaitFirstOrNull()
            ?: throw ApiAccessException(HttpStatus.BAD_REQUEST, "vms config not found.")

        vmsConfig.isActive = active
        vmsConfig.updatedAt = LocalDateTime.now().format()

        mongoTemplate.findAndReplace(
            Query.query(Criteria.where("vms").`is`(type)),
            vmsConfig,
        ).awaitSingle()

        return vmsConfig
    }

    protected suspend fun callVmsApi(uri: String, headers: Consumer<HttpHeaders>? = null): String {
        val vmsConfig = getVmsConfig()
        var client = webClient.get()
            .uri("http://${vmsConfig.ip}:${vmsConfig.port}$uri")

        if (headers != null) {
            client = client.headers(headers)
        }

        return client
            .retrieve()
            .onStatus(
                { obj: HttpStatusCode -> obj.is4xxClientError },
                { clientResponse: ClientResponse ->
                    clientResponse.bodyToMono(String::class.java).map { message: String? ->
                        ApiAccessException(
                            HttpStatus.valueOf(
                                clientResponse.statusCode().value()
                            ),
                            "vms api server responded with client error. status: ${clientResponse.statusCode()}. message: $message"
                        )
                    }
                }
            )
            .onStatus(
                { obj: HttpStatusCode -> obj.is5xxServerError },
                { clientResponse: ClientResponse ->
                    clientResponse.bodyToMono(String::class.java).map { message: String? ->
                        ApiAccessException(
                            HttpStatus.valueOf(
                                clientResponse.statusCode().value()
                            ),
                            "vms api server responded with internal server error. status: ${clientResponse.statusCode()}. message: $message"
                        )
                    }
                }
            )
            .bodyToMono(String::class.java)
            .retryWhen(
                Retry.fixedDelay(1, Duration.ofSeconds(1))
                    .doBeforeRetry { _: RetrySignal? -> log.info("vms api error, retrying connection") }
                    .onRetryExhaustedThrow { _, retrySignal ->
                        ApiAccessException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            retrySignal.failure()
                        )
                    }
            )
            .doOnError { e: Throwable? -> log.error("vms api error, exception: $e", e) }
            .awaitSingle()
    }

    /**
     * Vms 구현체 AOP
     */
//    @Component
//    @Aspect
//    protected class VmsAspect(
//        private val siteRepo: SiteRepo,
//        private val transaction: TransactionTemplate
//    ) {
//        companion object {
//            private val log = LoggerFactory.getLogger(VmsAspect::class.java)
//        }
//
//        /**
//         * 다운로드 파일 메타데이터 로깅
//         */
//        @Around(value = "execution(* com.oms.vms.Vms.download(..))", argNames = "joinPoint")
//        @Throws(Throwable::class)
//        fun afterDownloadReturning(joinPoint: ProceedingJoinPoint): Flux<VmsDownload> {
//            val proceeded = joinPoint.proceed()
//
//            val downloads = proceeded as Flux<VmsDownload>
//
//            return downloads.doOnNext { this.logDownloadMetaData(it) }
//        }
//
//        private fun logDownloadMetaData(download: VmsDownload) {
//            // 다운로드 성공 여부
//            val isSuccess = DownloadStatus.SUCCESS == download.status
//
//            // 성공이 아닐 경우 메소드 종료.
//            if (!isSuccess) return
//
//            try {
//                // 로그 파일 형식
//                val jsonFileName = "${download.start}_${download.end}_at_${
//                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH_mm_ss"))
//                }.json"
//
//                // 로그 파일 경로
//                var jsonPath = "/workspace/log" + "/download/" +
//                        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) +
//                        "/" + "cmr_" + download.vmsId
//
//                val path = File(jsonPath)
//
//                // 파일 없을 경우 생성
//                if (!path.exists()) path.mkdirs()
//
//                jsonPath = "$jsonPath/$jsonFileName"
//
//                log.info("download path : " + download.path)
//
//                // ffprobe를 통해 다운로드 영상의 메타 정보 로깅
//                val processBuilder = ProcessBuilder(
//                    "ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", download.path
//                )
//                processBuilder.redirectErrorStream(true)
//
//                // ffprobe 명령의 출력을 jsonOutputPath 파일에 저장
//                val file = File(jsonPath)
//                processBuilder.redirectOutput(file)
//
//                val process = processBuilder.start()
//                val exitCode = process.waitFor()
//                if (exitCode != 0) {
//                    log.error("ffprobe exited with code : $exitCode")
//                }
//
//                // 예외 처리. 예외 발생하더라도 다운로드 프로세스에는 영향 없음.
//            } catch (e: Exception) {
//                log.error("Failed to save FFprobe output as JSON: $e")
//            }
//        }
//    }
}