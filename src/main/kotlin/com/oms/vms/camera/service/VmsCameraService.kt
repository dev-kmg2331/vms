package com.oms.vms.camera.service

import com.oms.vms.mongo.config.toResponse
import com.oms.vms.mongo.docs.VMS_CAMERA
import com.oms.vms.mongo.docs.VMS_RAW_JSON
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

/**
 * VMS 카메라 서비스
 * 
 * 각 VMS 시스템의 카메라 정보를 직접 조회하기 위한 서비스
 */
@Service
class VmsCameraService(
    private val mongoTemplate: ReactiveMongoTemplate
) {
    private val log = LoggerFactory.getLogger(VmsCameraService::class.java)

    /**
     * 특정 VMS 유형의 카메라 데이터를 조회합니다
     * 
     * @param vmsType 조회할 VMS 유형 (예: "emstone", "naiz", "dahua")
     * @return 해당 VMS 유형의 카메라 문서 목록
     */
    suspend fun getCamerasByVmsType(vmsType: String): List<Document> {
        log.info("Retrieving cameras for VMS type: {}", vmsType)
        
        val query = Query.query(Criteria.where("vms").`is`(vmsType))
        return mongoTemplate.find(query, Document::class.java, VMS_CAMERA)
            .asFlow()
            .toList()
    }

    /**
     * 모든 VMS 카메라 데이터를 조회합니다
     * 
     * @return 모든 VMS 카메라 문서 목록
     */
    suspend fun getAllCameras(): List<Document> {
        log.info("Retrieving all VMS cameras")
        
        return mongoTemplate.findAll(Document::class.java, VMS_CAMERA)
            .map { it.toResponse() }
            .asFlow()
            .toList()
    }

    /**
     * 특정 카메라 ID로 카메라 데이터를 조회합니다
     * 
     * @param cameraId 조회할 카메라 ID
     * @return 해당 ID의 카메라 문서, 없으면 null
     */
    suspend fun getCameraById(cameraId: String): Document? {
        log.info("Retrieving camera by ID: {}", cameraId)
        
        val query = Query.query(Criteria.where("_id").`is`(cameraId))
        return mongoTemplate.findOne(query, Document::class.java, VMS_CAMERA)
            .map { it.toResponse() }
            .asFlow()
            .toList()
            .firstOrNull()
    }

    /**
     * 특정 VMS 유형의 원본 JSON 데이터를 조회합니다
     * 
     * @param vmsType 조회할 VMS 유형 (예: "emstone", "naiz", "dahua")
     * @return 해당 VMS 유형의 원본 JSON 문서 목록
     */
    suspend fun getRawJsonByVmsType(vmsType: String): List<Document> {
        log.info("Retrieving raw JSON data for VMS type: {}", vmsType)
        
        val query = Query.query(Criteria.where("vms").`is`(vmsType))
        return mongoTemplate.find(query, Document::class.java, VMS_RAW_JSON)
            .map { it.toResponse() }
            .asFlow()
            .toList()
    }

    /**
     * 모든 VMS의 원본 JSON 데이터를 조회합니다
     * 
     * @return 모든 VMS 유형의 원본 JSON 문서 목록
     */
    suspend fun getAllRawJson(): List<Document> {
        log.info("Retrieving all raw JSON data")
        
        return mongoTemplate.findAll(Document::class.java, VMS_RAW_JSON)
            .map { it.toResponse() }
            .asFlow()
            .toList()
    }
}