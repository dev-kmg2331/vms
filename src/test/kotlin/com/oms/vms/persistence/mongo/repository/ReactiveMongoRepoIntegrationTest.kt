package com.oms.vms.persistence.mongo.repository

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Profile
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import kotlin.test.Test

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReactiveMongoRepoIntegrationTest {

    private val log = LoggerFactory.getLogger(this::class.java)

    @Autowired
    private lateinit var reactiveMongoRepo: ReactiveMongoRepo

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    val testEntities = "testEntities"
    val customCollection = "customCollection"
    val complexEntities = "complexEntities"

    @AfterEach
    fun cleanUp() {
        // 테스트 후 컬렉션 정리
        runBlocking {
            mongoTemplate.dropCollection(testEntities).block()
            mongoTemplate.dropCollection(customCollection).block()
            mongoTemplate.dropCollection(complexEntities).block()
        }
    }

    @Test
    fun `should insert document into MongoDB and retrieve it`(): Unit = runBlocking {
        // given
        val id = UUID.randomUUID().toString()

        val testEntity = TestEntity(
            id = id, // 자동 생성되도록 null로 설정
            name = "Test Entity",
            value = 42
        )

        // when
        val savedEntity = reactiveMongoRepo.insert(testEntity, testEntities)

        // then
        assertNotNull(savedEntity.id)
        assertEquals(testEntity.name, savedEntity.name)
        assertEquals(testEntity.value, savedEntity.value)
    }

    @Test
    fun `should insert complex document and verify all fields`() = runBlocking {
        // given
        val address = Address("Seoul", "123 Street", "12345")
        val complexEntity = ComplexEntity(
            id = null,
            title = "Complex Test",
            details = "Test Details",
            count = 10,
            address = address,
            tags = listOf("test", "mongodb", "kotlin")
        )
        // when
        val savedEntity = reactiveMongoRepo.insert(complexEntity, complexEntities)

        // then
        assertNotNull(savedEntity.id)
        assertEquals(complexEntity.title, savedEntity.title)
        assertEquals(complexEntity.details, savedEntity.details)
        assertEquals(complexEntity.count, savedEntity.count)
        assertEquals(complexEntity.address, savedEntity.address)
        assertEquals(complexEntity.tags, savedEntity.tags)

        // 쿼리로 문서 검색
        val query = Query.query(Criteria.where("title").`is`("Complex Test"))
        val foundEntity = mongoTemplate.findOne(query, ComplexEntity::class.java, complexEntities).awaitSingle()
        assertEquals(savedEntity, foundEntity)
    }

    @Test
    fun `should insert into custom collection name`() = runBlocking {
        // given
        val customEntity = CustomEntity(null, "Custom Collection Test")

        // when
        val savedEntity = reactiveMongoRepo.insert(customEntity, customCollection)

        // then
        assertNotNull(savedEntity.id)

        // 컬렉션 이름을 직접 지정하여 조회
        val foundEntity = mongoTemplate.findById(savedEntity.id!!, CustomEntity::class.java).awaitSingle()
        assertEquals(savedEntity, foundEntity)
    }

    // 테스트 엔티티 클래스들
    @Document
    data class TestEntity(
        @Id
        val id: String?,
        val name: String,
        val value: Int
    )

    data class Address(
        val city: String,
        val street: String,
        val zipcode: String
    )

    @Document
    data class ComplexEntity(
        @Id
        val id: String?,
        val title: String,
        val details: String,
        val count: Int,
        val address: Address,
        val tags: List<String>
    )

    @Document
    data class CustomEntity(
        @Id
        val id: String?,
        val name: String
    )
}