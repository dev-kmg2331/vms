package com.oms.vms.mongo.docs

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.util.*

/**
 * VMS 유형 정보
 * 시스템에 등록된 VMS 유형 정보를 저장
 */
@Document(collection = "vms_type_registry")
data class VmsTypeInfo(
    @Id
    val id: String = UUID.randomUUID().toString(),
    val code: String,                 // VMS 유형 코드 (고유 식별자)
    val name: String,                 // VMS 유형 이름
    val version: String? = null,      // VMS 버전
    val manufacturer: String? = null, // VMS 제조업체
    val description: String? = null,  // VMS 설명
    val properties: Map<String, String> = mapOf(), // 추가 속성
    val registeredAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)