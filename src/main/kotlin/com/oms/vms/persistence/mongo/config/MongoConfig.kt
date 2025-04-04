package com.oms.vms.persistence.mongo.config

import com.mongodb.reactivestreams.client.MongoClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.get
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration
@EnableTransactionManagement
class MongoConfig(private val environment: Environment) : AbstractReactiveMongoConfiguration() {
    override fun getDatabaseName(): String = environment["spring.data.mongodb.database"] ?: "vms"

    @Bean
    fun reactiveMongoTransactionManager(reactiveMongoDbFactory: ReactiveMongoDatabaseFactory): ReactiveMongoTransactionManager {
        return ReactiveMongoTransactionManager(reactiveMongoDbFactory)
    }

    @Bean
    fun mongoTemplate(mongoClient: MongoClient): ReactiveMongoTemplate {
        return ReactiveMongoTemplate(mongoClient, databaseName)
    }
}