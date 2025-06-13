package com.oms.vms.mongo.docs

import com.github.f4b6a3.tsid.TsidCreator
import com.oms.camelToSnakeCase
import com.oms.vms.mongo.config.toResponse
import format
import org.bson.Document
import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

interface BaseDoc {
    val id: String
    val refId: String
    val createdAt: String
    var updatedAt: String

    companion object {
        fun defaultId(): String = TsidCreator.getTsid1024().toString()
        fun defaultRefId(): String = UUID.randomUUID().toString()
        fun defaultCreatedAt(): String = LocalDateTime.now().format()
        fun defaultUpdatedAt(): String = LocalDateTime.now().format()
    }
}

/**
 * Doc 인터페이스를 구현한 객체를 MongoDB Document로 변환하는 확장 함수
 *
 * @return 변환된 MongoDB Document 객체
 */
fun BaseDoc.toDocument(): Document {
    val document = Document()
    // 추가적인 필드들 자동 매핑
    this::class.memberProperties
        .filter { it.visibility == KVisibility.PUBLIC }
        .forEach {
            val v = it.getter.call(this)
            // camel -> snake
            val k = it.name.camelToSnakeCase()

            if (v != null) {
                document[k] = it.getter.call(this)
            }
        }
    return document
}

fun BaseDoc.toDocumentResponse(): Document = this.toDocument().toResponse()
