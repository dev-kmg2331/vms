package com.oms.vms.field_mapping.transformation

import com.fasterxml.jackson.annotation.JsonIgnore
import com.oms.api.exception.ApiAccessException
import org.bson.Document
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

interface Transformation {
    val sourceField: String     // 원본 필드
}

/**
 * 필드 변환 규칙
 * 필드에 적용할 특수 변환 로직을 정의
 */
data class FieldTransformation(
    override val sourceField: String,
    val targetField: String,
    val transformationType: TransformationType, // 변환 유형
    val parameters: Map<String, String> = mapOf() // 변환에 필요한 추가 매개변수
) : Transformation

data class ChannelIdTransFormation(
    override val sourceField: String,
    @JsonIgnore
    val apply: (Document) -> String = { doc: Document -> doc[sourceField]!!.toString() }
) : Transformation

/**
 * 필드 변환 유형
 * 다양한 변환 유형 지원
 */
enum class TransformationType {
    DEFAULT_CONVERSION {
        override fun apply(
            source: Document,
            unifiedCamera: Document,
            transformation: FieldTransformation
        ) {
            if (source[transformation.sourceField] != null) {
                unifiedCamera[transformation.targetField] = source[transformation.sourceField]
            }
        }
    },

    BOOLEAN_CONVERSION {
        override fun apply(
            source: Document,
            unifiedCamera: Document,
            transformation: FieldTransformation
        ) {
            val sourceValue = source[transformation.sourceField]
            if (sourceValue != null) {
                val result = when (sourceValue) {
                    is Boolean -> sourceValue
                    is String -> sourceValue.equals("true", ignoreCase = true) ||
                            sourceValue.equals("yes", ignoreCase = true) ||
                            sourceValue.equals("1", ignoreCase = true)

                    is Number -> sourceValue.toInt() != 0
                    else -> false
                }
                unifiedCamera[transformation.targetField] = result
            } else throw ApiAccessException(HttpStatus.BAD_REQUEST, "source ${transformation.sourceField} is invalid")
        }
    },
    NUMBER_CONVERSION {
        override fun apply(
            source: Document,
            unifiedCamera: Document,
            transformation: FieldTransformation
        ) {
            val sourceValue = source[transformation.sourceField]
            if (sourceValue != null) {
                val result = when (sourceValue) {
                    is Number -> sourceValue
                    is String -> try {
                        if (sourceValue.contains(".")) sourceValue.toDouble() else sourceValue.toInt()
                    } catch (e: Exception) {
                        null
                    }

                    else -> null
                }
                if (result != null) {
                    unifiedCamera[transformation.targetField] = result
                }
            } else throw ApiAccessException(HttpStatus.BAD_REQUEST, "source ${transformation.sourceField} is invalid")
        }
    },
    STRING_FORMAT {
        override fun apply(
            source: Document,
            unifiedCamera: Document,
            transformation: FieldTransformation
        ) {
            val sourceValue = source[transformation.sourceField]
            if (sourceValue != null) {
                val format = transformation.parameters["format"] ?: "%s"
                unifiedCamera[transformation.targetField] = String.format(format, sourceValue)
            } else throw ApiAccessException(HttpStatus.BAD_REQUEST, "source ${transformation.sourceField} is invalid")
        }
    },
    DATE_FORMAT {
        override fun apply(
            source: Document,
            unifiedCamera: Document,
            transformation: FieldTransformation
        ) {
            val sourceValue = source[transformation.sourceField] ?: throw ApiAccessException(
                HttpStatus.BAD_REQUEST,
                "source ${transformation.sourceField} is invalid"
            )
            val sourceFormat = transformation.parameters["sourceFormat"] ?: throw ApiAccessException(
                HttpStatus.BAD_REQUEST,
                "source date format ${transformation.sourceField} undefined"
            )
            val targetFormat = transformation.parameters["targetFormat"] ?: throw ApiAccessException(
                HttpStatus.BAD_REQUEST,
                "target date format ${transformation.sourceField} undefined"
            )

            try {
                val result = when (sourceValue) {
                    is String -> {
                        val sourceFormatter = DateTimeFormatter.ofPattern(sourceFormat)
                        val targetFormatter = DateTimeFormatter.ofPattern(targetFormat)

                        // 날짜 또는 날짜시간인지 판단
                        if (sourceFormat.contains("H") || sourceFormat.contains("h")) {
                            val dateTime = LocalDateTime.parse(sourceValue, sourceFormatter)
                            dateTime.format(targetFormatter)
                        } else {
                            val date = LocalDate.parse(sourceValue, sourceFormatter)
                            date.format(targetFormatter)
                        }
                    }

                    else -> null
                }

                if (result != null) {
                    unifiedCamera[transformation.targetField] = result
                }
            } catch (e: Exception) {
                log.error("exception in datetime transformation: ${e.message}")
                throw ApiAccessException(HttpStatus.INTERNAL_SERVER_ERROR, e, "exception in datetime transformation")
            }
        }
    };

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * 변환 로직을 적용하는 추상 메소드
     * 각 열거형 요소가 이 메소드를 구현해야 함
     */
    abstract fun apply(
        source: Document,
        unifiedCamera: Document,
        transformation: FieldTransformation
    )
}
