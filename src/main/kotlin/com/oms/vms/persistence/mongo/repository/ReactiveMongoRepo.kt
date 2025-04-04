package com.oms.vms.persistence.mongo.repository

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class ReactiveMongoRepo(private val mongoTemplate: ReactiveMongoTemplate): MongoRepo {

    suspend fun <T : Any> insert(any: T, collectionName: String): T = mongoTemplate.insert(any, collectionName).awaitSingle()
    suspend fun dropDocument(collectionName: String) { mongoTemplate.dropCollection(collectionName).awaitSingleOrNull() }
}