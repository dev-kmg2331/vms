package com.oms.vms.mongo.repo

import com.oms.vms.mongo.docs.VmsTypeInfo
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * VMS 유형 관리 서비스
 * 시스템에 등록된 VMS 유형을 관리
 */
@Component
class VmsTypeRegistry(
    private val mongoTemplate: ReactiveMongoTemplate
) {
    private val registryCollection = "vms_type_registry"
    
    /**
     * 새 VMS 유형 등록
     */
    suspend fun registerVmsType(vmsType: VmsTypeInfo): VmsTypeInfo {
        // 이미 존재하는지 확인
        val existing = getVmsTypeInfo(vmsType.code)
        if (existing != null) {
            return updateVmsTypeInfo(vmsType)
        }
        
        return mongoTemplate.save(
            vmsType.copy(
                registeredAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            ), 
            registryCollection
        ).awaitFirst()
    }
    
    /**
     * VMS 유형 정보 업데이트
     */
    suspend fun updateVmsTypeInfo(vmsType: VmsTypeInfo): VmsTypeInfo {
        return mongoTemplate.save(
            vmsType.copy(updatedAt = LocalDateTime.now()),
            registryCollection
        ).awaitFirst()
    }
    
    /**
     * VMS 유형 정보 조회
     */
    suspend fun getVmsTypeInfo(code: String): VmsTypeInfo? {
        return mongoTemplate.findOne(
            Query.query(Criteria.where("code").`is`(code)),
            VmsTypeInfo::class.java,
            registryCollection
        ).awaitFirstOrNull()
    }
    
    /**
     * 모든 VMS 유형 조회
     */
    suspend fun getAllVmsTypes(): List<VmsTypeInfo> {
        return mongoTemplate.findAll(VmsTypeInfo::class.java, registryCollection)
            .collectList()
            .awaitFirst()
    }
    
    /**
     * VMS 유형 삭제
     */
    suspend fun deleteVmsType(code: String): Boolean {
        val result = mongoTemplate.remove(
            Query.query(Criteria.where("code").`is`(code)),
            registryCollection
        ).awaitFirst()
        
        return result.deletedCount > 0
    }
}