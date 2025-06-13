package com.oms.vms.field_mapping.transformation

import com.oms.api.exception.ApiAccessException
import com.oms.logging.gson.gson
import org.bson.Document
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

interface Transformation {
    val sourceField: String     // 원본 필드
}

/**
 * 필드 변환 규칙
 * 필드에 적용할 특수 변환 로직을 정의
 */
data class FieldTransformation(
    override var sourceField: String,
    val targetField: String,
    var sourceDocumentField: String = sourceField,
    val transformationType: TransformationType, // 변환 유형
    val parameters: Map<String, String> = mapOf() // 변환에 필요한 추가 매개변수
) : Transformation {

    fun sourceIsDocument(): Boolean = sourceField.contains("-")

    fun sourceIsList(): Boolean = sourceField.contains("[0]")

    fun getSourceDocument(sourceDoc: Document): Document {
        var doc = sourceDoc

        var fields = sourceField.split("-")
        this.sourceDocumentField = fields.last()
        fields = fields.subList(0, fields.size - 1)

        for (field in fields) {
            doc = doc.get(field, Document::class.java) ?: throw ApiAccessException(
                HttpStatus.BAD_REQUEST,
                "error while parsing source object. field : $field not found"
            )
        }

        return doc
    }

    fun getSourceListDocument(sourceDoc: Document): Document {
        val fields = sourceField.split("[0]")

        val field = fields.first()

        this.sourceDocumentField = fields.last()

        return sourceDoc.getList(field, Document::class.java)[0] as Document
    }
}

data class ChannelIdTransFormation(
    override val sourceField: String,
) : Transformation

/**
 * 필드 변환 유형
 * 다양한 변환 유형 지원
 */
enum class TransformationType {
    DEFAULT_CONVERSION {
        override fun apply(
            source: Document,
            target: Document,
            transformation: FieldTransformation
        ) {
            if (source[transformation.sourceDocumentField] != null) {
                target[transformation.targetField] = source[transformation.sourceDocumentField]
            }
        }
    },

    BOOLEAN_CONVERSION {
        override fun apply(
            source: Document,
            target: Document,
            transformation: FieldTransformation
        ) {
            val sourceValue = source[transformation.sourceDocumentField]
            log.info("source value: $sourceValue, targetField : $target")
            if (sourceValue != null) {
                val result = when (sourceValue) {
                    is Boolean -> sourceValue
                    is String -> sourceValue.lowercase(Locale.getDefault()).equals("true", ignoreCase = true) ||
                            sourceValue.lowercase(Locale.getDefault()).equals("yes", ignoreCase = true) ||
                            sourceValue.lowercase(Locale.getDefault()).equals("y", ignoreCase = true) ||
                            sourceValue.lowercase(Locale.getDefault()).equals("on", ignoreCase = true) ||
                            sourceValue.equals("1", ignoreCase = true)

                    is Number -> sourceValue.toInt() != 0
                    else -> false
                }
                target[transformation.targetField] = result
                log.info("source value: $sourceValue, targetField : $target, result: $result")
            } else throw ApiAccessException(HttpStatus.BAD_REQUEST, "source ${transformation.sourceDocumentField} is invalid")
        }
    },
    NUMBER_CONVERSION {
        override fun apply(
            source: Document,
            target: Document,
            transformation: FieldTransformation
        ) {
            try {
                log.debug("\nsource: ${source.toJson()}\ntarget: ${target.toJson()}\ntransformation: ${gson.toJson(transformation)}")
                val sourceValue = source[transformation.sourceDocumentField]
                if (sourceValue != null) {
                    val result = when (sourceValue) {
                        is Number -> sourceValue
                        is String -> try {
                            if (sourceValue.contains(".")) sourceValue.toDouble() else sourceValue.toInt()
                        } catch (e: Exception) {
                            log.error("failed to parse source $sourceValue to ${target.toJson()}", e)
                            null
                        }

                        else -> null
                    }
                    if (result != null) {
                        target[transformation.targetField] = result
                    }
                    log.debug("result ${target.toJson()}: $result")
                }
            } catch (e: Exception) {
                throw ApiAccessException(HttpStatus.BAD_REQUEST, e, "source ${transformation.sourceDocumentField} is invalid")
            }
        }
    },
    STRING_FORMAT {
        override fun apply(
            source: Document,
            target: Document,
            transformation: FieldTransformation
        ) {
            val sourceValue = source[transformation.sourceDocumentField]
            if (sourceValue != null) {
                val format = transformation.parameters["format"] ?: "%s"
                target[transformation.targetField] = String.format(format, sourceValue)
            } else throw ApiAccessException(HttpStatus.BAD_REQUEST, "source ${transformation.sourceDocumentField} is invalid")
        }
    },
    DATE_FORMAT {
        override fun apply(
            source: Document,
            target: Document,
            transformation: FieldTransformation
        ) {
            val sourceValue = source[transformation.sourceDocumentField] ?: throw ApiAccessException(
                HttpStatus.BAD_REQUEST,
                "source ${transformation.sourceDocumentField} is invalid"
            )
            val sourceFormat = transformation.parameters["sourceFormat"] ?: throw ApiAccessException(
                HttpStatus.BAD_REQUEST,
                "source date format ${transformation.sourceDocumentField} undefined"
            )
            val targetFormat = transformation.parameters["targetFormat"] ?: throw ApiAccessException(
                HttpStatus.BAD_REQUEST,
                "target date format ${transformation.sourceDocumentField} undefined"
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
                    target[transformation.targetField] = result
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
        target: Document,
        transformation: FieldTransformation
    )
}
