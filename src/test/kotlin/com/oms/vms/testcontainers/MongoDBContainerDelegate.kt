package com.oms.vms.testcontainers

import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer

/**
 * MongoDB 컨테이너 delegate class.
 */
object MongoDBContainerDelegate {
    private const val IMAGE = "mongodb/mongodb-atlas-local:7.0.9"
    private const val PORT = 27017
    private const val REUSE = true

    private val log = LoggerFactory.getLogger(this::class.java)

    lateinit var mongoDBContainer: MongoDBAtlasLocalContainer

    fun initialize() {
        // 어노테이션 설정에 따라 컨테이너 생성
        val container = MongoDBAtlasLocalContainer(IMAGE)

        // 컨테이너 설정
        container.apply {
            withExposedPorts(PORT)
            withReuse(REUSE)
        }

        // 컨테이너 시작
        container.start()

        // MongoDB 컨테이너가 실행 중인지 확인
        log.info("MongoDB container is running: ${container.isRunning}, host: ${container.host}, port: ${container.firstMappedPort}")

        mongoDBContainer = container
    }

    fun stop() {
        mongoDBContainer.close()
    }
}
