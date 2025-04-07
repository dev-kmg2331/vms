package com.oms.vms.sync

import com.oms.api.exception.ApiAccessException
import com.oms.vms.config.VmsConfig
import format
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.bson.Document
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * VMS 구성 관리 서비스
 * MongoDB에 VMS 구성 정보를 저장하고 관리
 * VMS 타입별로 상이한 파라미터를 유연하게 처리하기 위해 Document 객체를 직접 활용
 */
@Service
class VmsConfigService(
    private val mongoTemplate: ReactiveMongoTemplate
) {
    private val configCollection = "vms_configs"

    /**
     * 모든 VMS 구성 조회
     */
    suspend fun getAllConfigs(): List<Document> {
        return mongoTemplate.findAll(Document::class.java, configCollection)
            .collectList()
            .awaitFirst()
    }

    /**
     * 특정 VMS 구성 조회
     */
    suspend fun getConfigById(id: String): Document {
        return mongoTemplate.findById(id, Document::class.java, configCollection)
            .awaitFirstOrNull() ?: throw ApiAccessException(HttpStatus.BAD_REQUEST, "could not find config by id $id")
    }

    /**
     * VMS 유형별 구성 조회
     */
    suspend fun getConfigsByType(type: String): List<Document> {
        return mongoTemplate.find(
            Query.query(Criteria.where("type").`is`(type)),
            Document::class.java,
            configCollection
        ).collectList().awaitFirst()
    }

    /**
     * 특정 이름의 VMS 구성 조회
     */
    suspend fun getConfigByName(name: String): Document {
        return mongoTemplate.findOne(
            Query.query(Criteria.where("name").`is`(name)),
            Document::class.java,
            configCollection
        ).awaitFirstOrNull() ?: throw ApiAccessException(HttpStatus.BAD_REQUEST, "could not find config by name $name")
    }

    /**
     * VMS 구성 업데이트
     */
    suspend fun updateConfig(id: String, config: Document): Document {
        val existingConfig = getConfigById(id)

        // ID와 생성 시간은 유지
        config["_id"] = existingConfig.getString("_id")
        config["createdAt"] = existingConfig.getString("createdAt")

        // 업데이트 시간 설정
        config["updatedAt"] = LocalDateTime.now().format()

        return mongoTemplate.save(config, configCollection).awaitFirst()
    }

    /**
     * VMS 구성 삭제
     */
    suspend fun deleteConfig(id: String): Boolean {
        val result = mongoTemplate.remove(
            Query.query(Criteria.where("_id").`is`(id)),
            configCollection
        ).awaitFirst()

        return result.deletedCount > 0
    }
}