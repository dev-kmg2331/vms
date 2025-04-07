package com.oms.vms.mongo.docs

import com.fasterxml.jackson.annotation.JsonInclude
import com.oms.vms.FieldTransformation
import format
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.util.*

/**
 * VMS 매핑 규칙 데이터베이스 문서
 * 매핑 설정을 MongoDB에 저장하여 동적으로 관리
 */
@Document(collection = "vms_field_mappings")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class VmsMappingDocument(
    @Id
    val id: String = UUID.randomUUID().toString(),
    val vmsType: String,                       // VMS 유형 (dahua, emstone, naiz 등)
    val transformations: List<FieldTransformation> = listOf(), // 특수 변환 룰
    val description: String? = null,           // 매핑 설명
    val createdAt: String = LocalDateTime.now().format(),
    val updatedAt: String = LocalDateTime.now().format()
)