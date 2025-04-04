package com.oms.vms

import com.oms.api.exception.ApiAccessException
import org.bson.Document
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.script.ScriptEngineManager
import javax.script.SimpleBindings

/**
 * VMS 필드 매핑 구성 클래스
 * 동적으로 매핑 규칙을 관리하는 확장 가능한 아키텍처
 */
@Configuration
class FieldMappingConfig {
    // 기본 구성 정보는 모두 데이터베이스에서 관리하도록 변경
}


/**
 * 필드 변환 규칙
 * 필드에 적용할 특수 변환 로직을 정의
 */
data class FieldTransformation(
    val sourceField: String,     // 원본 필드
    val targetField: String,     // 대상 필드
    val transformationType: TransformationType, // 변환 유형
    val parameters: Map<String, String> = mapOf() // 변환에 필요한 추가 매개변수
)

/**
 * 필드 변환 유형
 * 다양한 변환 유형 지원
 */
enum class TransformationType {
    DEFAULT_CONVERSION {
        override fun apply(
            source: Document,
            unifiedCameraMap: MutableMap<String, Any?>,
            transformation: FieldTransformation
        ) {
            if (source[transformation.sourceField] != null) {
                unifiedCameraMap[transformation.targetField] = source[transformation.sourceField]
            }
        }
    },

    BOOLEAN_CONVERSION {
        override fun apply(
            source: Document,
            unifiedCameraMap: MutableMap<String, Any?>,
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
                unifiedCameraMap[transformation.targetField] = result
            } else throw ApiAccessException(HttpStatus.BAD_REQUEST, "source ${transformation.sourceField} is invalid")
        }
    },
    NUMBER_CONVERSION {
        override fun apply(
            source: Document,
            unifiedCameraMap: MutableMap<String, Any?>,
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
                    unifiedCameraMap[transformation.targetField] = result
                }
            } else throw ApiAccessException(HttpStatus.BAD_REQUEST, "source ${transformation.sourceField} is invalid")
        }
    },
    STRING_FORMAT {
        override fun apply(
            source: Document,
            unifiedCameraMap: MutableMap<String, Any?>,
            transformation: FieldTransformation
        ) {
            val sourceValue = source[transformation.sourceField]
            if (sourceValue != null) {
                val format = transformation.parameters["format"] ?: "%s"
                unifiedCameraMap[transformation.targetField] = String.format(format, sourceValue)
            } else throw ApiAccessException(HttpStatus.BAD_REQUEST, "source ${transformation.sourceField} is invalid")
        }
    },
    DATE_FORMAT {
        override fun apply(
            source: Document,
            unifiedCameraMap: MutableMap<String, Any?>,
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
                    unifiedCameraMap[transformation.targetField] = result
                }
            } catch (e: Exception) {
                log.error("exception in datetime transformation: ${e.message}")
                throw ApiAccessException(HttpStatus.INTERNAL_SERVER_ERROR, e, "exception in datetime transformation")
            }
        }
    };

    companion object {
        val log: Logger = LoggerFactory.getLogger(TransformationType::class.java)
    }

    /**
     * 변환 로직을 적용하는 추상 메소드
     * 각 열거형 요소가 이 메소드를 구현해야 함
     */
    abstract fun apply(
        source: Document,
        unifiedCameraMap: MutableMap<String, Any?>,
        transformation: FieldTransformation
    )
}
