package com.oms.vms.mongo.docs

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.util.*

@Document(collection = VMS_CAMERA_UNIFIED_DEPRECATED)
data class DeprecatedUnifiedCamera(
    @Id
    @Field("_id")
    override val id: String = BaseDoc.defaultId(),

    @Field("ref_id")
    override val refId: String = BaseDoc.defaultRefId(),

    @Field("created_at")
    override val createdAt: String = BaseDoc.defaultCreatedAt(),

    @Field("updated_at")
    override var updatedAt: String = BaseDoc.defaultUpdatedAt(),
    
    // 기본 정보
    @Field("name")
    val name: String = "",                  // 카메라 이름
    @Field("channel_ID")
    val channelID: String = "",             // 채널 인덱스
    @Field("channel_name")
    val channelName: String = "",           // 채널 이름

    // 네트워크 정보
    @Field("ip_address")
    val ipAddress: String = "",             // IP 주소
    @Field("port")
    val port: Int = 0,                      // 포트
    @Field("http_port")
    val httpPort: Int = 0,                  // HTTP 포트
    @Field("rtsp_url")
    val rtspUrl: String?,                   // RTSP URL

    // 상태 정보
    @Field("is_enabled")
    val isEnabled: Boolean = true,          // 활성화 여부
    @Field("status")
    val status: String = "",                // 상태 (온라인/오프라인 등)

    // 기능 정보
    @Field("supports_PTZ")
    val supportsPTZ: Boolean = false,       // PTZ 지원 여부
    @Field("supports_audio")
    val supportsAudio: Boolean = false,     // 오디오 지원 여부

    // 메타데이터
    @Field("vms")
    val vms: String,                        // VMS 유형
    @Field("original_id")
    val originalId: String = "",            // 원본 VMS의 카메라 ID

    @Field("deprecated_time", targetType = FieldType.DATE_TIME)
    val deprecatedTime: Date = Date(),

    // 원본 데이터에 대한 참조
    @Field("source_reference")
    val sourceReference: SourceReference?,
) : BaseDoc {
    companion object {
        fun instance(unifiedCamera: UnifiedCamera): DeprecatedUnifiedCamera = DeprecatedUnifiedCamera(
            id = unifiedCamera.id,
            refId = unifiedCamera.refId,
            name = unifiedCamera.name,
            channelID = unifiedCamera.channelId,
            channelName = unifiedCamera.channelName,
            ipAddress = unifiedCamera.ipAddress,
            port = unifiedCamera.port,
            httpPort = unifiedCamera.httpPort,
            rtspUrl = unifiedCamera.rtsp?.url,
            isEnabled = unifiedCamera.isEnabled,
            status = unifiedCamera.status,
            supportsPTZ = unifiedCamera.supportsPtz,
            supportsAudio = unifiedCamera.supportsAudio,
            vms = unifiedCamera.vms,
            originalId = unifiedCamera.originalId,
            createdAt = unifiedCamera.createdAt,
            updatedAt = unifiedCamera.updatedAt,
            sourceReference = unifiedCamera.sourceReference,
        )
    }
}