package com.oms.vms.mongo.repo

import com.oms.vms.mongo.docs.VMS_FIELD_MAPPINGS
import com.oms.vms.mongo.docs.VmsMappingDocument
import format
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 필드 매핑 저장소
 * 동적으로 업데이트된 매핑 설정을 관리
 */
@Component
class FieldMappingRepository(
    private val mongoTemplate: ReactiveMongoTemplate
) {
    // 기본 매핑 규칙 컬렉션 이름

    /**
     * 특정 VMS 유형에 대한 현재 매핑 규칙을 가져옴
     * DB에 저장된 규칙이 없으면 기본 매핑 생성
     */
    suspend fun getMappingRules(vmsType: String): VmsMappingDocument {
        return mongoTemplate.findOne(
            Query.query(Criteria.where("vms").`is`(vmsType)),
            VmsMappingDocument::class.java,
            VMS_FIELD_MAPPINGS
        ).awaitFirstOrNull()
            ?: createDefaultMapping(vmsType)
//            ?: throw ApiAccessException(status = HttpStatus.BAD_REQUEST, message = "vms mapping not found")
    }

    /**
     * 새로운 VMS 유형에 대한 기본 매핑 생성
     */
    suspend fun createDefaultMapping(vmsType: String): VmsMappingDocument {
        // 새 VMS 유형에 대한 기본 매핑 생성
        val defaultMapping = VmsMappingDocument(
            vms = vmsType,
            description = "Auto-generated default mapping for $vmsType"
        )

        return mongoTemplate.save(defaultMapping, VMS_FIELD_MAPPINGS).awaitFirst()
    }

    /**
     * 매핑 규칙을 업데이트하고 저장
     */
    suspend fun updateMappingRules(mapping: VmsMappingDocument): VmsMappingDocument {
        // 업데이트 시간 설정
        val updatedMapping = mapping.copy(updatedAt = LocalDateTime.now().format())
        val query = Query.query(Criteria.where("_id").`is`(updatedMapping.id))
        mongoTemplate.findAndReplace(query, updatedMapping, VMS_FIELD_MAPPINGS).awaitFirst()
        return updatedMapping
    }

    /**
     * 모든 VMS 매핑 규칙 가져오기
     */
    suspend fun getAllMappingRules(): List<VmsMappingDocument> {
        return mongoTemplate.findAll(VmsMappingDocument::class.java, VMS_FIELD_MAPPINGS)
            .collectList()
            .awaitFirst()
    }

    /**
     * VMS 매핑 규칙 삭제
     */
    suspend fun deleteMappingRules(vmsType: String): Boolean {
        val result = mongoTemplate.remove(
            Query.query(Criteria.where("vmsType").`is`(vmsType)),
            VMS_FIELD_MAPPINGS
        ).awaitFirst()

        return result.deletedCount > 0
    }
}