package com.oms.vms.mongo.config

import com.mongodb.reactivestreams.client.MongoClient
import com.oms.ExcludeInTestProfile
import com.oms.camelToSnakeCase
import com.oms.vms.mongo.docs.DeprecatedUnifiedCamera
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.get
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.util.concurrent.TimeUnit

@ExcludeInTestProfile
@Configuration
@EnableTransactionManagement
class MongoConfig(private val environment: Environment) : AbstractReactiveMongoConfiguration() {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun getDatabaseName(): String = environment["spring.data.mongodb.database"] ?: "vms"

    init {
        DATABASE_NAME = environment["spring.data.mongodb.database"] ?: "vms"
    }

    @Bean
    fun reactiveMongoTransactionManager(reactiveMongoDbFactory: ReactiveMongoDatabaseFactory): ReactiveMongoTransactionManager {
        return ReactiveMongoTransactionManager(reactiveMongoDbFactory)
    }

    @Bean
    fun mongoTemplate(mongoClient: MongoClient): ReactiveMongoTemplate {
        val mongoTemplate = ReactiveMongoTemplate(mongoClient, databaseName)

        val deprecatedTTLIndex = "deprecated_ttl_index"

        /**
         * @see DeprecatedUnifiedCamera
         * */
        fun insertIndex(mongoTemplate: ReactiveMongoTemplate) {
            val indexOps = mongoTemplate.indexOps(DeprecatedUnifiedCamera::class.java)

            val indexDefinition =
                Index(DeprecatedUnifiedCamera::deprecatedTime.name.camelToSnakeCase(), Sort.Direction.ASC)
                    .named(deprecatedTTLIndex)
                    .expire(7, TimeUnit.DAYS)

            indexOps.ensureIndex(indexDefinition).block()
        }

        try {
            insertIndex(mongoTemplate)
        } catch (e: Exception) {
            log.error("Unable to insert mongo collection. retrying. message: ${e.message}")
            mongoTemplate.indexOps(DeprecatedUnifiedCamera::class.java)
                .dropIndex(deprecatedTTLIndex)
                .block()

            insertIndex(mongoTemplate)
        }

        log.info("TTL index created for DeprecatedUnifiedCamera collection")

        return mongoTemplate
    }

    companion object {
        lateinit var DATABASE_NAME: String
    }
}

private val documentResponseExcludedKeys = listOf("_id", "created_at", "updated_at", "_class", "source_reference")

fun Document.toResponse(): Document {
    this.forEach { (k, v) ->
        if (v is Document) {
            this[k] = v.toResponse()
        }
    }

    documentResponseExcludedKeys.forEach { this.remove(it) }

    return this
}

fun ReactiveMongoTemplate.toDoc(any: Any): Document {
    val document = Document()
    this.converter.write(any, document)
    return document
}