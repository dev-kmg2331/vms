package com.oms.vms.mongo.docs

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
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
    val name: String? = null,                  // 카메라 이름
    val channelIndex: Int? = null,             // 채널 인덱스
    val channelName: String? = null,           // 채널 이름
    
    // 네트워크 정보
    val ipAddress: String? = null,             // IP 주소
    val port: Int? = null,                     // 포트
    val httpPort: Int? = null,                 // HTTP 포트
    val rtspUrl: String? = null,               // RTSP URL
    
    // 하드웨어 정보
    val model: String? = null,                 // 모델명
    val serialNumber: String? = null,          // 시리얼 번호
    val manufacturer: String? = null,          // 제조사
    val firmware: String? = null,              // 펌웨어 버전
    
    // 상태 정보
    val isEnabled: Boolean = true,             // 활성화 여부
    val status: String? = null,                // 상태 (온라인/오프라인 등)
    
    // 기능 정보
    val supportsPTZ: Boolean = false,          // PTZ 지원 여부
    val supportsAudio: Boolean = false,        // 오디오 지원 여부
    val videoInputChannels: Int? = null,       // 비디오 입력 채널 수
    val audioInputChannels: Int? = null,       // 오디오 입력 채널 수
    
    // 메타데이터
    val vmsType: String,                       // VMS 유형 (dahua, emstone, naiz 등)
    val originalId: String? = null,            // 원본 VMS의 카메라 ID
    val createdAt: LocalDateTime = LocalDateTime.now(),  // 생성 시간
    val updatedAt: LocalDateTime = LocalDateTime.now(),  // 업데이트 시간
    
    // 원본 데이터에 대한 참조
    val sourceReference: SourceReference? = null
)

/**
 * 원본 VMS 데이터에 대한 참조 정보
 */
data class SourceReference(
    val collectionName: String,  // 원본 컬렉션 이름
    val documentId: String       // 원본 문서 ID
)