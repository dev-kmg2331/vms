package com.oms.vms.mongo.docs

import com.fasterxml.jackson.annotation.JsonInclude
import com.oms.vms.field_mapping.transformation.ChannelIdTransFormation
import com.oms.vms.field_mapping.transformation.FieldTransformation
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

/**
 * VMS 매핑 규칙 데이터베이스 문서
 * 매핑 설정을 MongoDB에 저장하여 동적으로 관리
 */
@Document(collection = VMS_FIELD_MAPPINGS)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class FieldMappingDocument(
    @Id
    @Field("_id")
    override val id: String = BaseDoc.defaultId(),

    @Field("ref_id")
    override val refId: String = BaseDoc.defaultRefId(),

    @Field("created_at")
    override val createdAt: String = BaseDoc.defaultCreatedAt(),

    @Field("updated_at")
    override var updatedAt: String = BaseDoc.defaultUpdatedAt(),

    val vms: String,                       // VMS 유형 (dahua, emstone, naiz 등)
    var channelIdTransformation: ChannelIdTransFormation? = null,
    val transformations: MutableList<FieldTransformation> = mutableListOf(), // 특수 변환 룰
    val description: String? = null,           // 매핑 설명
) : BaseDoc