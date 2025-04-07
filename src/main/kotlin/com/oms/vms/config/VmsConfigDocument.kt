package com.oms.vms.config

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.util.*

/**
 * VMS 구성 정보를 MongoDB에 저장하기 위한 문서 클래스
 */
@Document(collection = "vms_configs")
data class VmsConfigDocument(
    @Id
    val id: String = UUID.randomUUID().toString(),
    val name: String,                    // VMS 구성 이름 (식별용)
    val type: String,                    // VMS 유형 (emstone, naiz, dahua 등)
    val username: String,                // VMS 접속 계정
    val password: String,                // VMS 접속 비밀번호
    val ip: String,                      // VMS IP 주소
    val port: String,                    // VMS 포트
    val isActive: Boolean = true,        // 활성화 여부
    val properties: Map<String, String> = mapOf(), // 추가 속성
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)