package com.oms.vms.service

import com.github.f4b6a3.tsid.TsidCreator
import com.google.gson.JsonObject
import com.oms.api.exception.ApiAccessException
import com.oms.vms.mongo.config.toDoc
import com.oms.vms.mongo.docs.*
import format
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime

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
        val rawData: Any = try {
            Document.parse(response)
        } catch (e: Exception) {
            response
        }

        doc["_id"] = TsidCreator.getTsid1024().toString()
        doc["created_at"] = LocalDateTime.now().format()
        doc["raw_data"] = rawData
        doc["vms"] = vmsType
        doc["request_uri"] = uri
        return doc
    }

    /**
     * 카메라 데이터를 위한 기본 문서를 생성합니다.
     */
    private fun createCameraDocument(jsonObj: JsonObject, vmsType: String): Document {
        return Document.parse(jsonObj.toString()).apply {
            this["_id"] = TsidCreator.getTsid1024().toString()
            this["created_at"] = LocalDateTime.now().format()
            this["vms"] = vmsType
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

        // VMS 필드 분석 유무 확인
        val fieldAnalysisExists = mongoTemplate.exists(
            Query.query(Criteria.where("vms").`is`(vmsType)),
            VMS_FIELD_ANALYSIS
        ).awaitSingle()

        // 원시 응답 저장
        val rawDoc = createRawResponseDocument(rawResponse, uri, vmsType)
        mongoTemplate.insert(rawDoc, VMS_RAW_JSON).awaitSingle()

        // JSON 데이터 처리
        val jsonObjects = processJsonData(rawResponse)

        // 각 카메라 객체 처리 및 저장
        jsonObjects.forEachIndexed { i, json ->
            // 카메라 JSON 저장
            val cameraDoc = createCameraDocument(json, vmsType)

            if (i == 0 && fieldAnalysisExists) {
                mongoTemplate.save(
                    analyzeDocumentStructure(cameraDoc),
                    VMS_FIELD_ANALYSIS
                ).awaitSingleOrNull()
            }

            mongoTemplate.insert(cameraDoc, VMS_CAMERA).awaitSingle()
        }
    }

    /**
     * UnifiedCamera 의 필드 구조를 분석하고 저장합니다
     * @return UnifiedCamera 의 필드 구조 분석 Document
     */
    suspend fun analyzeUnifiedFieldStructure(): Document {
        val emptyUnifiedCamera = UnifiedCamera(
            vms = "",
            rtsp = null,
            sourceReference = SourceReference(
                collectionName = "",
                documentId = ""
            ),
        )

        val document = mongoTemplate.toDoc(emptyUnifiedCamera)

        val structure = analyzeDocumentStructure(document)

        val responseDoc = Document()

        responseDoc["analyzed_at"] = LocalDateTime.now().format()
        responseDoc["fields"] = structure

        return responseDoc
    }


    /**
     * VMS 유형의 필드 구조를 분석하고 저장합니다
     *
     * 이 메서드는 다음과 같은 작업을 수행합니다:
     * 1. VMS 유형에 대한 샘플 카메라 문서 검색
     * 2. 문서 구조 분석
     * 3. 분석 결과를 데이터베이스에 저장
     *
     * @param vmsType 분석할 VMS 유형
     * @return 분석 문서, 또는 샘플을 찾지 못한 경우 null
     */
    suspend fun analyzeVmsFieldStructure(vmsType: String): Document {
        log.info("Analyzing field structure for {} VMS", vmsType)

        // VMS 유형에 대한 샘플 카메라 문서 가져오기
        val sampleCamera = mongoTemplate.findOne(
            Query.query(Criteria.where("vms").`is`(vmsType)),
            Document::class.java,
            VMS_CAMERA
        ).awaitFirstOrNull()

        if (sampleCamera == null) {
            log.warn("No sample camera found for $vmsType VMS")
            throw ApiAccessException(HttpStatus.BAD_REQUEST, "No sample camera found for $vmsType VMS")
        }

        val fieldAnalyze = mongoTemplate.find(
            Query.query(Criteria.where("vms").`is`(vmsType)),
            FieldAnalyze::class.java,
            VMS_FIELD_ANALYSIS
        ).awaitFirstOrNull()

        val analyzeDocumentStructure = analyzeDocumentStructure(sampleCamera)

        val res = if (fieldAnalyze == null) {
            // 분석 문서 생성
            val newFieldAnalyze = FieldAnalyze(
                fields = analyzeDocumentStructure,
                vms = vmsType
            )
            log.info("Saving new field structure analysis for {} VMS", vmsType)

            mongoTemplate.save(newFieldAnalyze).awaitSingle()
        } else {
            fieldAnalyze.analyzedAt = LocalDateTime.now().format()
            fieldAnalyze.fields = analyzeDocumentStructure

            log.info("Updating field structure analysis for {} VMS", vmsType)

            mongoTemplate.findAndReplace(
                Query.query(Criteria.where("vms").`is`(vmsType)),
                fieldAnalyze
            ).awaitSingle()
        }

        return mongoTemplate.toDoc(res)
    }

    /**
     * 문서의 구조를 재귀적으로 분석합니다
     *
     * 이 메서드는 다음과 같은 작업을 수행합니다:
     * 1. 다양한 유형의 값(Document, List, 프리미티브) 처리
     * 2. 필드 유형을 나타내는 구조 구축
     *
     * @param document 분석할 문서 또는 값
     * @return 문서 구조의 표현
     */
    private fun analyzeDocumentStructure(document: Any?): Any {
        return when (document) {
            is Document -> {
                // Document 객체의 경우 각 필드를 재귀적으로 분석
                val result = Document()
                document.forEach { (key, value) ->
                    result[key] = analyzeDocumentStructure(value)
                }
                result
            }

            is List<*> -> {
                // 리스트의 경우 첫 번째 요소를 샘플로 분석
                if (document.isNotEmpty()) {
                    return listOf(analyzeDocumentStructure(document[0]))
                }
                emptyList<Any>()
            }

            else -> {
                // 기본 값의 경우 타입 이름 반환
                document?.javaClass?.simpleName ?: "undefined"
            }
        }
    }
}