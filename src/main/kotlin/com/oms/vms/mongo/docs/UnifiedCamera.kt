package com.oms.vms.mongo.docs

import format
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.LocalDateTime
import java.util.*

/**
 * 여러 VMS 시스템에서 공통적으로 사용되는 카메라 데이터 구조
 */
@Document(collection = "vms_camera_unified")
data class UnifiedCamera(
    @Id
    val id: String = UUID.randomUUID().toString(),

    // 기본 정보
    @Field("name")
    val name: String = "",                 // 카메라 이름
    @Field("channelIndex")
    val channelIndex: Int = 0,             // 채널 인덱스
    @Field("channelName")
    val channelName: String = "",          // 채널 이름

    // 네트워크 정보
    @Field("ipAddress")
    val ipAddress: String = "",            // IP 주소
    @Field("port")
    val port: Int = 0,                     // 포트
    @Field("httpPort")
    val httpPort: Int = 0,                 // HTTP 포트
    @Field("rtspUrl")
    val rtspUrl: String?,                   // RTSP URL

    // 상태 정보
    @Field("enabled")
    val isEnabled: Boolean = true,          // 활성화 여부
    @Field("status")
    val status: String = "",                // 상태 (온라인/오프라인 등)

    // 기능 정보
    @Field("supportsPTZ")
    val supportsPTZ: Boolean = false,       // PTZ 지원 여부
    @Field("supportsAudio")
    val supportsAudio: Boolean = false,     // 오디오 지원 여부

    // 메타데이터
    @Field("vmsType")
    val vmsType: String,                    // VMS 유형
    @Field("originalId")
    val originalId: String = "",            // 원본 VMS의 카메라 ID
    @Field("createdAt")
    val createdAt: String = LocalDateTime.now().format(),  // 생성 시간
    @Field("updatedAt")
    val updatedAt: String = LocalDateTime.now().format(),  // 업데이트 시간

    // 원본 데이터에 대한 참조
    @Field("sourceReference")
    val sourceReference: SourceReference
)

/**
 * 원본 VMS 데이터에 대한 참조 정보
 */
data class SourceReference(
    val collectionName: String,  // 원본 컬렉션 이름
    val documentId: String       // 원본 문서 ID
)