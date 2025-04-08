package com.oms.vms.sync

import com.oms.vms.sync.endpoint.TransformationRequest
import com.oms.vms.mongo.docs.VmsMappingDocument
import com.oms.vms.mongo.repo.FieldMappingRepository
import format
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.*

@SpringBootTest
@ExtendWith(MockKExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
@ActiveProfiles("test")
class FieldMappingServiceTest {

    private val log = LoggerFactory.getLogger(this::class.java)

    @MockK
    private lateinit var fieldMappingRepository: FieldMappingRepository

    private val vmsType = "test-vms"
    private lateinit var testMappingDocument: VmsMappingDocument
    private lateinit var testTransformation: FieldTransformation

    private lateinit var fieldMappingService: FieldMappingService


    @BeforeEach
    fun setup() {
        // 테스트용 매핑 문서 생성
        testMappingDocument = VmsMappingDocument(
            id = UUID.randomUUID().toString(),
            vms = vmsType,
            transformations = listOf(),
            description = "Test mapping document",
            createdAt = LocalDateTime.now().format(),
            updatedAt = LocalDateTime.now().format()
        )

        // 테스트용 변환 규칙 생성
        testTransformation = FieldTransformation(
            sourceField = "source_field",
            targetField = "target_field",
            transformationType = TransformationType.DEFAULT_CONVERSION
        )

        coEvery { fieldMappingRepository.getMappingRules(vmsType) } returns testMappingDocument

        fieldMappingService = FieldMappingService(fieldMappingRepository)
    }

    @Test
    @DisplayName("특정 VMS 유형의 매핑 규칙 조회 테스트")
    fun `getMappingRules should return mapping rules for vms type`() = runTest {
        // given

        // when
        val result = fieldMappingService.getMappingRules(vmsType)

        log.info("result: $result")

        // then
        assertNotNull(result, "Mapping rules should not be null")
        assertEquals(vmsType, result.vms, "VMS type should match")

        coVerify(exactly = 1) { fieldMappingRepository.getMappingRules(vmsType) }
    }

    @Test
    @DisplayName("모든 VMS 유형의 매핑 규칙 조회 테스트")
    fun `getAllMappingRules should return all mapping rules`() = runTest {
        // given
        val mappingDocuments = listOf(
            testMappingDocument,
            testMappingDocument.copy(vms = "another-vms")
        )

        coEvery { fieldMappingRepository.getAllMappingRules() } returns mappingDocuments

        // when
        val result = fieldMappingService.getAllMappingRules()

        // then
        assertNotNull(result, "Mapping rules should not be null")
        assertEquals(2, result.size, "Should return 2 mapping documents")

        coVerify(exactly = 1) { fieldMappingRepository.getAllMappingRules() }
    }

    @Test
    @DisplayName("필드 변환 규칙 추가 테스트")
    fun `addTransformation should add transformation to mapping rules`() = runTest {
        // given
        val updatedMappingDoc = testMappingDocument.copy(
            transformations = listOf(testTransformation),
            updatedAt = LocalDateTime.now().format()
        )

        coEvery { fieldMappingRepository.getMappingRules(vmsType) } returns testMappingDocument
        coEvery { fieldMappingRepository.updateMappingRules(any()) } returns updatedMappingDoc

        val transformationRequest = TransformationRequest(
            sourceField = testTransformation.sourceField,
            targetField = testTransformation.targetField,
            transformationType = testTransformation.transformationType,
            parameters = testTransformation.parameters
        )

        // when
        val result = fieldMappingService.addTransformation(vmsType, transformationRequest)

        // then
        assertNotNull(result, "Updated mapping document should not be null")
        assertEquals(1, result.transformations.size, "Should have one transformation")
        assertEquals(testTransformation.sourceField, result.transformations[0].sourceField, "Source field should match")
        assertEquals(testTransformation.targetField, result.transformations[0].targetField, "Target field should match")
        assertEquals(
            testTransformation.transformationType,
            result.transformations[0].transformationType,
            "Transformation type should match"
        )

        coVerify(exactly = 1) { fieldMappingRepository.getMappingRules(vmsType) }
        coVerify(exactly = 1) { fieldMappingRepository.updateMappingRules(any()) }
    }

    @Test
    @DisplayName("필드 변환 규칙 삭제 테스트")
    fun `removeTransformation should remove transformation from mapping rules`() = runTest {
        // given
        val mappingWithTransformation = testMappingDocument.copy(
            transformations = listOf(testTransformation),
            updatedAt = LocalDateTime.now().format()
        )

        val mappingWithoutTransformation = testMappingDocument.copy(
            transformations = listOf(),
            updatedAt = LocalDateTime.now().format()
        )

        coEvery { fieldMappingRepository.getMappingRules(vmsType) } returns mappingWithTransformation
        coEvery { fieldMappingRepository.updateMappingRules(any()) } returns mappingWithoutTransformation

        // when
        val result = fieldMappingService.removeTransformation(vmsType, 0)

        // then
        assertNotNull(result, "Updated mapping document should not be null")
        assertEquals(0, result.transformations.size, "Should have no transformations")

        coVerify(exactly = 1) { fieldMappingRepository.getMappingRules(vmsType) }
        coVerify(exactly = 1) { fieldMappingRepository.updateMappingRules(any()) }
    }

    @Test
    @DisplayName("특정 필드에 대한 변환 규칙 조회 테스트")
    fun `getTransformationsForField should return transformations for specific field`() = runTest {
        // given
        val anotherTransformation = FieldTransformation(
            sourceField = "another_source",
            targetField = "another_target",
            transformationType = TransformationType.NUMBER_CONVERSION
        )

        val mappingWithTransformations = testMappingDocument.copy(
            transformations = listOf(testTransformation, anotherTransformation)
        )

        coEvery { fieldMappingRepository.getMappingRules(vmsType) } returns mappingWithTransformations

        // when
        val result = fieldMappingService.getTransformationsForField(vmsType, "source_field")

        // then
        assertNotNull(result, "Transformations should not be null")
        assertEquals(1, result.size, "Should return 1 transformation")
        assertEquals(testTransformation.sourceField, result[0].sourceField, "Source field should match")
        assertEquals(testTransformation.targetField, result[0].targetField, "Target field should match")

        coVerify(exactly = 1) { fieldMappingRepository.getMappingRules(vmsType) }
    }

    @Test
    @DisplayName("특정 유형의 변환 규칙 조회 테스트")
    fun `getTransformationsByType should return transformations of specific type`() = runTest {
        // given
        val numberTransformation = FieldTransformation(
            sourceField = "number_source",
            targetField = "number_target",
            transformationType = TransformationType.NUMBER_CONVERSION
        )

        val booleanTransformation = FieldTransformation(
            sourceField = "boolean_source",
            targetField = "boolean_target",
            transformationType = TransformationType.BOOLEAN_CONVERSION
        )

        val mappingWithTransformations = testMappingDocument.copy(
            transformations = listOf(testTransformation, numberTransformation, booleanTransformation)
        )

        coEvery { fieldMappingRepository.getMappingRules(vmsType) } returns mappingWithTransformations

        // when
        val result = fieldMappingService.getTransformationsByType(vmsType, TransformationType.NUMBER_CONVERSION)

        // then
        assertNotNull(result, "Transformations should not be null")
        assertEquals(1, result.size, "Should return 1 transformation")
        assertEquals(numberTransformation.sourceField, result[0].sourceField, "Source field should match")
        assertEquals(numberTransformation.targetField, result[0].targetField, "Target field should match")
        assertEquals(
            TransformationType.NUMBER_CONVERSION,
            result[0].transformationType,
            "Transformation type should match"
        )

        coVerify(exactly = 1) { fieldMappingRepository.getMappingRules(vmsType) }
    }

    @Test
    @DisplayName("VMS 유형에 대한 매핑 규칙 삭제 테스트")
    fun `deleteMappingRules should delete mapping rules for vms type`() = runTest {
        // given
        coEvery { fieldMappingRepository.deleteMappingRules(vmsType) } returns true

        // when
        val result = fieldMappingService.deleteAllMappingRules(vmsType)

        // then
        assertTrue(result, "Deletion should be successful")
        coVerify(exactly = 1) { fieldMappingRepository.deleteMappingRules(vmsType) }
    }

    @Test
    @DisplayName("매핑 규칙 초기화 테스트")
    fun `resetMappingRules should reset mapping rules for vms type`() = runTest {
        // given
        coEvery { fieldMappingRepository.deleteMappingRules(vmsType) } returns true
        coEvery { fieldMappingRepository.getMappingRules(vmsType) } returns testMappingDocument.copy(transformations = listOf())

        // when
        val result = fieldMappingService.resetMappingRules(vmsType)

        // then
        assertNotNull(result, "Reset mapping document should not be null")
        assertEquals(0, result.transformations.size, "Should have no transformations")

        coVerify(exactly = 1) { fieldMappingRepository.deleteMappingRules(vmsType) }
        coVerify(exactly = 1) { fieldMappingRepository.getMappingRules(vmsType) }
    }
}