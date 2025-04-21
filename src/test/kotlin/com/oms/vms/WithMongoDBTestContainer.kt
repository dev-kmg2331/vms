package com.oms.vms

import com.oms.vms.testcontainers.MongoDBContainerDelegate
import com.oms.vms.testcontainers.MongoDBContainerDelegate.mongoDBContainer
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

interface WithMongoDBTestContainer {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerMongoProperties(registry: DynamicPropertyRegistry) {
            MongoDBContainerDelegate.initialize()
            registry.add("spring.data.mongodb.uri") { mongoDBContainer.connectionString }
        }
    }
}
