package com.oms.vms.mongo.docs

import com.oms.vms.camera.ExcelExclude
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

/**
 * 여러 VMS 시스템에서 공통적으로 사용되는 카메라 데이터 구조
 */
@Document(collection = VMS_CAMERA_UNIFIED)
data class UnifiedCamera(
    // Doc 인터페이스 구현
    @Id
    @Field("_id")
    @field:ExcelExclude
    override val id: String = BaseDoc.defaultId(),

    @Field("ref_id")
    @field:ExcelExclude
    override val refId: String = BaseDoc.defaultRefId(),

    @Field("created_at")
    @field:ExcelExclude
    override val createdAt: String = BaseDoc.defaultCreatedAt(),

    @Field("updated_at")
    @field:ExcelExclude
    override var updatedAt: String = BaseDoc.defaultUpdatedAt(),

    // 기본 정보
    @Field("name")
    val name: String = "",                 // 카메라 이름
    @Field("channel_ID")
    val channelId: String = "",             // 채널 인덱스
    @Field("channel_name")
    val channelName: String = "",          // 채널 이름

    // 네트워크 정보
    @Field("ip_address")
    val ipAddress: String = "",            // IP 주소
    @Field("port")
    val port: Int = 0,                     // 포트
    @Field("http_port")
    val httpPort: Int = 0,                 // HTTP 포트
    @Field("rtsp")
    var rtsp: RtspData,                   // RTSP

    // 상태 정보
    @Field("is_enabled")
    val isEnabled: Boolean = true,          // 활성화 여부
    @Field("status")
    val status: String = "",                // 상태 (온라인/오프라인 등)

    // 기능 정보
    @Field("supports_PTZ")
    val supportsPtz: Boolean = false,       // PTZ 지원 여부
    @Field("supports_audio")
    val supportsAudio: Boolean = false,     // 오디오 지원 여부

    // 좌표(위치) 정보
    @Field("latitude")
    val latitude: Double = 0.0,             // 위도
    @Field("longitude")
    val longitude: Double = 0.0,            // 경도

    // 메타데이터
    @Field("vms")
    @field:ExcelExclude
    val vms: String = "unknown",                        // VMS 유형
    @Field("original_id")
    @field:ExcelExclude
    val originalId: String = "",            // 원본 VMS의 카메라 ID

    // 원본 데이터에 대한 참조
    @Field("source_reference")
    @field:ExcelExclude
    val sourceReference: SourceReference?
) : BaseDoc

/**
 * 원본 VMS 데이터에 대한 참조 정보
 */
data class SourceReference(
    @Field("collection_name")
    val collectionName: String,  // 원본 컬렉션 이름
    @Field("document_id")
    val documentId: String       // 원본 문서 ID
)

data class RtspData(
    val url: String,
    val codec: String,
    val width: String,
    val height: String,
)