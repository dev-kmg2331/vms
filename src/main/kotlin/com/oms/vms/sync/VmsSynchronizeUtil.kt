package com.oms.vms.sync

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.oms.vms.persistence.mongo.repository.ReactiveMongoRepo
import format
import org.bson.Document
import java.time.LocalDateTime
import java.util.*

/**
 * VMS 유틸리티 클래스 - VMS 구현 간의 공통 기능을 제공합니다.
 */
object VmsSynchronizeUtil {

    /**
     * 원시 VMS 응답 데이터에 대한 기본 문서를 생성합니다.
     */
    private fun createRawResponseDocument(response: String, uri: String, vmsType: String): Document {
        val doc = Document()
        doc["_id"] = UUID.randomUUID().toString()
        doc["created_at"] = LocalDateTime.now().format()
        doc["raw-data"] = Document.parse(response)
        doc["vms"] = Document().apply {
            this["type"] = vmsType
            this["uri"] = uri
        }
        return doc
    }

    /**
     * 카메라 데이터를 위한 기본 문서를 생성합니다.
     */
    private fun createCameraDocument(jsonObj: JsonObject, vmsType: String): Document {
        return Document.parse(jsonObj.toString()).apply {
            this["_id"] = UUID.randomUUID().toString()
            this["created_at"] = LocalDateTime.now().format()
            this["vms"] = Document().apply {
                this["type"] = vmsType
            }
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
            this["vms_type"] = vmsType
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
        mongoRepo: ReactiveMongoRepo,
        processJsonData: (String) -> List<JsonObject>,
    ) {
        // 컬렉션 초기화
        val rawCollection = "vms_raw_json".apply { mongoRepo.dropDocument(this) }
        val cameraCollection = "vms_camera".apply { mongoRepo.dropDocument(this) }
        val keysCollection = "vms_camera_keys".apply { mongoRepo.dropDocument(this) }

        // 원시 응답 저장
        val rawDoc = createRawResponseDocument(rawResponse, uri, vmsType)
        mongoRepo.insert(rawDoc, rawCollection)

        // JSON 데이터 처리
        val jsonObjects = processJsonData(rawResponse)

        // 각 카메라 객체 처리 및 저장
        jsonObjects.forEachIndexed { i, json ->
            // 첫 번째 카메라 object 에서 key 추출
            if (i == 0) {
                val keys = extractKeys(json)

                // 키 목록 저장
                val keysDoc = createKeysDocument(keys, vmsType)
                mongoRepo.insert(keysDoc, keysCollection)
            } else {
                // 카메라 JSON 저장
                val cameraDoc = createCameraDocument(json, vmsType)
                mongoRepo.insert(cameraDoc, cameraCollection)
            }
        }
    }
}
