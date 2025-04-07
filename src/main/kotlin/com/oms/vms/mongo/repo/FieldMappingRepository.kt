package com.oms.vms.mongo.repo

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
    private val mappingCollection = "vms_field_mappings"
    
    /**
     * 특정 VMS 유형에 대한 현재 매핑 규칙을 가져옴
     * DB에 저장된 규칙이 없으면 기본 매핑 생성
     */
    suspend fun getMappingRules(vmsType: String): VmsMappingDocument {
        return mongoTemplate.findOne(
            Query.query(Criteria.where("vmsType").`is`(vmsType)),
            VmsMappingDocument::class.java,
            mappingCollection
        ).awaitFirstOrNull()
            ?: createDefaultMapping(vmsType)
//            ?: throw ApiAccessException(status = HttpStatus.BAD_REQUEST, message = "vms mapping not found")
    }
    
    /**
     * 새로운 VMS 유형에 대한 기본 매핑 생성
     */
    private suspend fun createDefaultMapping(vmsType: String): VmsMappingDocument {
        // 새 VMS 유형에 대한 기본 매핑 생성
        val defaultMapping = VmsMappingDocument(
            vmsType = vmsType,
            description = "Auto-generated default mapping for $vmsType"
        )
        
        return mongoTemplate.save(defaultMapping, mappingCollection).awaitFirst()
    }
    
    /**
     * 매핑 규칙을 업데이트하고 저장
     */
    suspend fun updateMappingRules(mapping: VmsMappingDocument): VmsMappingDocument {
        // 업데이트 시간 설정
        val updatedMapping = mapping.copy(updatedAt = LocalDateTime.now().format())
        
        return mongoTemplate.save(updatedMapping, mappingCollection).awaitFirst()
    }
    
    /**
     * 모든 VMS 매핑 규칙 가져오기
     */
    suspend fun getAllMappingRules(): List<VmsMappingDocument> {
        return mongoTemplate.findAll(VmsMappingDocument::class.java, mappingCollection)
            .collectList()
            .awaitFirst()
    }
    
    /**
     * VMS 매핑 규칙 삭제
     */
    suspend fun deleteMappingRules(vmsType: String): Boolean {
        val result = mongoTemplate.remove(
            Query.query(Criteria.where("vmsType").`is`(vmsType)),
            mappingCollection
        ).awaitFirst()
        
        return result.deletedCount > 0
    }
}