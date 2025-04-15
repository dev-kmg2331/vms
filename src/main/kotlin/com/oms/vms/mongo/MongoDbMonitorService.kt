package com.oms.vms.mongo

import kotlinx.coroutines.reactive.awaitFirst
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Service

/**
 * MongoDB 모니터링 서비스
 *
 * MongoDB 서버의 상태를 모니터링하고 진단 정보를 제공하는 서비스입니다.
 */
@Service
class MongoDbMonitorService(
    private val mongoTemplate: ReactiveMongoTemplate
) {
    private val log = LoggerFactory.getLogger(MongoDbMonitorService::class.java)

    /**
     * MongoDB 서버 상태를 조회합니다.
     *
     * 이 메서드는 MongoDB의 serverStatus 명령을 실행하여 서버의 다양한 지표와 상태 정보를 반환합니다.
     * 결과에는 메모리 사용량, 연결 정보, 작업 통계 등이 포함됩니다.
     *
     * @return MongoDB 서버 상태 정보가 포함된 Document
     */
    suspend fun getServerStatus(): Document {
        log.info("Retrieving MongoDB server status")
        try {
            val command = Document("serverStatus", 1)

            val res = Document()

            val exec = mongoTemplate.executeCommand(command).awaitFirst()
            log.info("Successfully retrieved MongoDB server status")

            res["document"] = exec.get("metrics", Document::class.java)["document"]
            res["connections"] = exec.get("connections", Document::class.java)
            res["memory"] = exec.get("mem", Document::class.java)

            return res
        } catch (e: Exception) {
            log.error("Failed to retrieve MongoDB server status: {}", e.message, e)
            throw e
        }
    }
}