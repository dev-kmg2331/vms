package com.oms.vms

import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
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
/**
 * 필드 변환 유형
 * 각 변환 유형이 자신의 변환 로직을 직접 구현
 */
enum class TransformationType {
    BOOLEAN_CONVERSION {
        override fun apply(
            document: Document,
            unifiedCameraMap: MutableMap<String, Any?>,
            transformation: FieldTransformation
        ) {
            val sourceValue = document[transformation.sourceField]
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
            }
        }
    },

    NUMBER_CONVERSION {
        override fun apply(
            document: Document,
            unifiedCameraMap: MutableMap<String, Any?>,
            transformation: FieldTransformation
        ) {
            val sourceValue = document[transformation.sourceField]
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
            }
        }
    },
    STRING_FORMAT {
        override fun apply(
            document: Document,
            unifiedCameraMap: MutableMap<String, Any?>,
            transformation: FieldTransformation
        ) {
            val sourceValue = document[transformation.sourceField]
            if (sourceValue != null) {
                val format = transformation.parameters["format"] ?: "%s"
                unifiedCameraMap[transformation.targetField] = String.format(format, sourceValue)
            }
        }
    },
    DATE_FORMAT {
        override fun apply(
            document: Document,
            unifiedCameraMap: MutableMap<String, Any?>,
            transformation: FieldTransformation
        ) {
            val sourceValue = document[transformation.sourceField] ?: return
            val sourceFormat = transformation.parameters["sourceFormat"] ?: return
            val targetFormat = transformation.parameters["targetFormat"] ?: return

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
                log.error("날짜 형식 변환 중 오류: ${e.message}")
            }
        }
    },
    CUSTOM_SCRIPT {
        private val scriptEngine = ScriptEngineManager().getEngineByName("nashorn")

        override fun apply(
            document: Document,
            unifiedCameraMap: MutableMap<String, Any?>,
            transformation: FieldTransformation
        ) {
            val script = transformation.parameters["script"] ?: return

            // 스크립트 실행을 위한 바인딩 설정
            val bindings = SimpleBindings()
            bindings["document"] = document
            bindings["result"] = null

            try {
                // 스크립트 실행
                scriptEngine.eval(script, bindings)

                // 결과 저장
                val result = bindings["result"]
                if (result != null) {
                    unifiedCameraMap[transformation.targetField] = result
                }
            } catch (e: Exception) {
                log.error("스크립트 실행 중 오류: ${e.message}")
            }
        }
    };

    companion object {
        val log = LoggerFactory.getLogger(TransformationType::class.java)
    }

    /**
     * 변환 로직을 적용하는 추상 메소드
     * 각 열거형 요소가 이 메소드를 구현해야 함
     */
    abstract fun apply(
        document: Document,
        unifiedCameraMap: MutableMap<String, Any?>,
        transformation: FieldTransformation
    )
}
