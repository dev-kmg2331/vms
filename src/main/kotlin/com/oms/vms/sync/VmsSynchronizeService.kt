package com.oms.vms.sync

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.oms.api.exception.ApiAccessException
import com.oms.vms.VmsType
import com.oms.vms.mongo.docs.VMS_CAMERA
import com.oms.vms.mongo.docs.VMS_CAMERA_KEYS
import com.oms.vms.mongo.docs.VMS_RAW_JSON
import format
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.*

/**
 * VMS 동기화 서비스 - VMS 구현 간의 공통 기능을 제공합니다.
 */
@Service
class VmsSynchronizeService(
    private val mongoTemplate: ReactiveMongoTemplate
) {
    private val log = LoggerFactory.getLogger(VmsSynchronizeService::class.java)

    /**
     * 원시 VMS 응답 데이터에 대한 기본 문서를 생성합니다.
     */
    private fun createRawResponseDocument(response: String, uri: String, vmsType: String): Document {
        val doc = Document()
        doc["_id"] = UUID.randomUUID().toString()
        doc["created_at"] = LocalDateTime.now().format()
        doc["raw_data"] = Document.parse(response)
        doc["vms"] = vmsType
        doc["request_uri"] = uri
        return doc
    }

    /**
     * 카메라 데이터를 위한 기본 문서를 생성합니다.
     */
    private fun createCameraDocument(jsonObj: JsonObject, vmsType: String): Document {
        return Document.parse(jsonObj.toString()).apply {
            this["_id"] = UUID.randomUUID().toString()
            this["created_at"] = LocalDateTime.now().format()
            this["vms"] = vmsType
        }
    }

    /**
     * JsonElement에서 키를 재귀적으로 추출하는 함수
     * @param jsonElement Gson의 JsonElement
     * @return 추출된 키 구조를 가진 맵
     */
    fun extractKeys(jsonElement: JsonElement): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        if (jsonElement.isJsonObject) {
            val jsonObject = jsonElement.asJsonObject

            for (key in jsonObject.keySet()) {
                val value = jsonObject.get(key)

                if (value.isJsonObject) {
                    // 값이 JSON 객체인 경우 재귀적으로 처리
                    val nestedKeys = extractKeys(value)
                    result[key] = nestedKeys
                } else if (value.isJsonArray) {
                    // 값이 JSON 배열인 경우 배열의 첫 요소를 샘플로 처리
                    val jsonArray = value.asJsonArray

                    if (jsonArray.size() > 0) {
                        val firstElement = jsonArray.get(0)
                        if (firstElement.isJsonObject) {
                            val nestedKeys = extractKeys(firstElement)
                            result[key] = listOf(nestedKeys)
                        } else {
                            result[key] = key
                        }
                    } else {
                        result[key] = key
                    }
                } else {
                    // 값이 단순 값인 경우 그대로 저장
                    result[key] = key
                }
            }
        }

        return result
    }

    /**
     * 카메라 키 목록을 위한 문서를 생성합니다.
     */
    private fun createKeysDocument(keys: Any, vmsType: String): Document {
        return Document().apply {
            this["_id"] = UUID.randomUUID().toString()
            this["created_at"] = LocalDateTime.now().format()
            this["vms"] = vmsType
            this["keys"] = keys
        }
    }

    /**
     * 데이터 동기화를 위한 기본 메서드 - 컬렉션 초기화 및 데이터 저장
     */
    suspend fun synchronize(
        rawResponse: String,
        uri: String,
        vmsType: String,
        processJsonData: (String) -> List<JsonObject>,
    ) {

        // 컬렉션 초기화
        mongoTemplate.remove(
            Query.query(Criteria.where("vms").`is`(vmsType)),
            VMS_RAW_JSON
        ).awaitSingle()
        mongoTemplate.remove(
            Query.query(Criteria.where("vms").`is`(vmsType)),
            VMS_CAMERA
        ).awaitSingle()
        mongoTemplate.remove(
            Query.query(Criteria.where("vms").`is`(vmsType)),
            VMS_CAMERA_KEYS
        ).awaitSingle()

        // 원시 응답 저장
        val rawDoc = createRawResponseDocument(rawResponse, uri, vmsType)
        mongoTemplate.insert(rawDoc, VMS_RAW_JSON).awaitSingle()

        // JSON 데이터 처리
        val jsonObjects = processJsonData(rawResponse)

        // 각 카메라 객체 처리 및 저장
        jsonObjects.forEachIndexed { i, json ->
            // 첫 번째 카메라 object 에서 key 추출
            if (i == 0) {
                val keys = extractKeys(json)

                // 키 목록 저장
                val keysDoc = createKeysDocument(keys, vmsType)
                mongoTemplate.insert(keysDoc, VMS_CAMERA_KEYS).awaitSingle()
            }

            // 카메라 JSON 저장
            val cameraDoc = createCameraDocument(json, vmsType)
            mongoTemplate.insert(cameraDoc, VMS_CAMERA).awaitSingle()
        }
    }

    /**
     * 특정 VMS 유형의 키값 구조 조회
     * vms_camera_keys 컬렉션에서 키 구조 정보를 가져옴
     */
    suspend fun getVmsDataJsonKeys(vmsType: String): Document {
        log.info("fetching keys for VMS type: {}", vmsType)

        val vms: VmsType

        try {
            vms = VmsType.findByServiceName(vmsType)
        } catch (e: Exception) {
            throw ApiAccessException(HttpStatus.BAD_REQUEST, e)
        }

        return mongoTemplate.findOne(
            Query.query(Criteria.where("vms_type").`is`(vms.serviceName)),
            Document::class.java,
            "vms_camera_keys"
        ).awaitFirstOrNull() ?: throw ApiAccessException(HttpStatus.BAD_REQUEST, "no keys found for VMS type: $vmsType")
    }
}