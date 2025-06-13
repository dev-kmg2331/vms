package com.oms.vms.mongo.docs

import format
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.LocalDateTime

@Document(collection = VMS_FIELD_ANALYSIS)
data class FieldAnalyze(
    @Id
    @Field("_id")
    override val id: String = BaseDoc.defaultId(),

    @Field("ref_id")
    override val refId: String = BaseDoc.defaultRefId(),

    @Field("created_at")
    override val createdAt: String = BaseDoc.defaultCreatedAt(),

    @Field("updated_at")
    override var updatedAt: String = BaseDoc.defaultUpdatedAt(),

    @Field("vms")
    val vms: String,
    @Field("fields")
    var fields: Any,
    @Field("analyzed_at")
    var analyzedAt: String = LocalDateTime.now().format(),
) : BaseDoc