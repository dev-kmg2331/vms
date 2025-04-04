package com.oms.vms.persistence.mongo

import org.springframework.data.annotation.Id
import java.time.LocalDateTime
import java.util.*

data class JsonDocument(
    @Id
    val id: UUID = UUID.randomUUID(),
    val content: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
)