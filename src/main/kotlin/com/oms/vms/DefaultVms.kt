package com.oms.vms

import com.oms.vms.sync.VmsSynchronizeService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import reactor.util.retry.Retry.RetrySignal
import java.time.Duration

abstract class DefaultVms protected constructor(
    protected val vmsSynchronizeService: VmsSynchronizeService,
    protected final val executor: ThreadPoolTaskExecutor = ThreadPoolTaskExecutor(),
    poolSize: Int = 16
) : Vms {

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

    protected fun callVmsApi(webClient: WebClient, uri: String): Mono<String> {
        return webClient.get()
            .uri(uri)
            .retrieve()
            .onStatus(
                { obj: HttpStatusCode -> obj.is4xxClientError },
                { clientResponse: ClientResponse ->
                    clientResponse.bodyToMono(String::class.java).map { message: String? -> RuntimeException(message) }
                }
            )
            .bodyToMono(String::class.java)
            .retryWhen(
                Retry.fixedDelay(1, Duration.ofSeconds(1))
                    .doBeforeRetry { _: RetrySignal? -> log.info("vms api error, retrying connection") }
            )
            .doOnError { e: Throwable? -> throw RuntimeException(e) }
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