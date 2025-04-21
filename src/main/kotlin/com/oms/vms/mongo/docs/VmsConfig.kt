package com.oms.vms.mongo.docs

import format
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.LocalDateTime
import java.util.*

/**
 * VMS 설정 정보 도큐먼트
 * 기본 인증 정보와 추가 속성 및 메타데이터를 포함한 VMS 설정 정보
 */
@Document(collection = VMS_CONFIG)
data class VmsConfig(
    @Id
    @Field("_id")
    val id: String = UUID.randomUUID().toString(),

    // 기본 인증 및 연결 정보
    @Field("username")
    var username: String,        // VMS 접속 계정
    @Field("password")
    var password: String,        // VMS 접속 비밀번호
    @Field("ip")
    var ip: String,              // VMS IP 주소
    @Field("port")
    var port: Int,            // VMS 포트

    // 설정 및 상태 관리 필드
    @Field("vms")
    val vms: String,       // VMS 구성 이름 (식별용)
    @Field("")
    var isActive: Boolean = true, // 활성화 여부

    @Field("additional_info") // VMS 별 추가 정보 필드
    var additionalInfo: MutableList<VmsAdditionalInfo> = mutableListOf(),

    @field:Transient
    @Field("additional_info_keys")
    val additionalInfoKeys: Set<String> = additionalInfo.map { it.key }.toSet(),

    // 메타데이터
    @Field("created_at")
    var createdAt: String = LocalDateTime.now().format(),
    @Field("updated_at")
    var updatedAt: String = LocalDateTime.now().format(),

    ) {
    fun getAdditionalInfo(key: String) = this.additionalInfo.find { it.key == key }?.value
        ?: throw IllegalArgumentException("No additional info found with key $key")
}

/**
 * VMS 추가 정보 클래스
 * VMS 설정에 연결된 추가 정보 항목을 표현
 */
data class VmsAdditionalInfo(
    @Field("key")
    val key: String,                     // 정보 키
    @Field("value")
    val value: String,                      // 정보 값
    @Field("description")
    val description: String = "",     // 설명
    @Field("created_at")
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Field("updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)