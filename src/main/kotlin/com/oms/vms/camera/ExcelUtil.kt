package com.oms.vms.camera

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.oms.api.exception.ApiAccessException
import com.oms.camelToSnakeCase
import com.oms.logging.gson.serializeToMap
import com.oms.string.snakeToCamel
import com.oms.vms.camera.ExcelUtil.convertClassToExcel
import com.oms.vms.mongo.docs.BaseDoc
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.reflect.KClass

object ExcelUtil {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * 데이터 리스트를 Excel(XLSX) 파일로 변환합니다.
     *
     * @param data 변환할 데이터 리스트 (각 맵은 하나의 행을 나타냄)
     * @param fileName 생성할 시트 이름
     * @return Excel 파일 데이터를 포함하는 ByteArray
     */
    fun <T> convertToExcel(
        data: List<T>,
        fileName: String,
    ): XSSFWorkbook {
        if (data.isEmpty()) throw ApiAccessException(HttpStatus.BAD_REQUEST, "given data is empty.")
        if (data[0] == null) throw ApiAccessException(HttpStatus.BAD_REQUEST, "reference data of index 0 is null.")

        val (workbook, headers) = convertClassToExcel(data[0]!!::class.java, fileName)

        val sheet = workbook.getSheet(fileName)

        // 데이터 행 생성
        data.forEachIndexed { rowNum, rowData ->
            val row = sheet.createRow(rowNum + 1) // +1 하여 엑셀 헤더 row 제외함
            val dataMap = rowData.serializeToMap()

            // 각 열의 데이터 설정
            headers.forEachIndexed { colNum, header ->
                val cell = row.createCell(colNum)

                dataMap[header]

                // 해당 헤더의 값이 있는 경우에만 셀 설정
                dataMap[header]?.let {
                    when (it) {
                        is Number -> {
                            cell.cellType = CellType.NUMERIC
                            cell.setCellValue(it.toDouble())
                        }

                        is Boolean -> {
                            cell.cellType = CellType.BOOLEAN
                            cell.setCellValue(it)
                        }

                        is Date -> {
                            cell.cellType = CellType.NUMERIC
                            cell.setCellValue(it)
                        }

                        else -> {
                            cell.cellType = CellType.STRING
                            cell.setCellValue(it.toString())
                        }
                    }
                } ?: cell.also { cell.cellType = CellType.STRING; cell.setCellValue("") }
            }
        }

        return workbook
    }

    fun <T> convertClassToExcel(clazz: Class<T>, fileName: String): Pair<XSSFWorkbook, List<String>> {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet(fileName)

        val headers =
            clazz.declaredFields.filterNot { it.isAnnotationPresent(ExcelExclude::class.java) }
                .map { it.name.camelToSnakeCase() }

        // 헤더 행 생성
        val headerRow = sheet.createRow(0)

        headers.forEachIndexed { i, v ->
            val cell = headerRow.createCell(i)
            cell.setCellValue(v)
        }

        // 컬럼 너비 자동 조정
        for (i in headers.indices) {
            sheet.autoSizeColumn(i)
        }

        return Pair(workbook, headers)
    }

    /**
     * Excel 파일을 파싱하여 카메라 데이터 맵 리스트를 반환합니다.
     *
     * @param file Excel 파일
     * @return 각 카메라 데이터가 맵으로 표현된 리스트
     */
    fun <T : Any> parseExcelFile(file: MultipartFile, clazz: KClass<out T>): List<T> {
        val result = mutableListOf<T>()

        WorkbookFactory.create(file.inputStream).use { workbook ->
            // 첫 번째 시트 사용
            val sheet = workbook.getSheetAt(0)

            if (sheet.physicalNumberOfRows < 1) {
                throw ApiAccessException(HttpStatus.BAD_REQUEST, "Excel file has no data or is empty")
            }

            // 헤더 읽기
            val headerRow = sheet.getRow(0)
            val headers = mutableListOf<String>()

            // 주어진 클래스의 필드명인지 확인.
            for (cell in headerRow) {
                val header = cell.toString().trim()

                val headerMatchesField =
                    clazz.java.declaredFields.any { field -> field.name.camelToSnakeCase() == header }

                if (!headerMatchesField) throw ApiAccessException(
                    HttpStatus.BAD_REQUEST,
                    "Excel file has wrong format. Check file."
                )

                headers.add(header)
            }

            log.info("Excel headers: {}", headers)

            val om = ObjectMapper().registerModule(KotlinModule.Builder().build())

            // 데이터 행 읽기 (첫 번째 행은 헤더이므로 제외)

            var currentRow = 0
            var currentCol = 0

            try {
                for (rowIndex in 1 until sheet.physicalNumberOfRows) {
                    currentRow = rowIndex

                    val row = sheet.getRow(rowIndex) ?: continue

                    val cameraData = mutableMapOf<String, Any?>()
                    var hasData = false

                    for (colIndex in headers.indices) {
                        currentCol = colIndex
                        val cell = row.getCell(colIndex)
                        val header = headers[colIndex].snakeToCamel()

                        if (cell != null) {
                            val value = when (cell.cellType) {
                                CellType.NUMERIC -> cell.numericCellValue
                                CellType.BOOLEAN -> cell.booleanCellValue
                                else -> cell.toString().trim()
                            }

                            hasData = true
                            cameraData[header] = value
                        } else {
                            cameraData[header] = null
                        }
                    }

                    if (hasData) {
                        val data = om.convertValue(cameraData, clazz.java)
                        result.add(data)
                    }
                }
            } catch (e: Exception) {
                log.error("error at row: $currentRow, col: $currentCol", e)
                throw ApiAccessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize data.")
            }
        }
        log.info("Parsed {} entries from Excel file", result.size)

        return result
    }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
annotation class ExcelExclude

/**
 * 단일 객체 데이터를 Excel(XLSX) 파일로 변환합니다.
 *
 * @param fileName 생성할 시트 이름
 * @return Excel 파일 데이터를 포함하는 ByteArray
 */
inline fun <reified T> Class<T>.convertToExcel(fileName: String): ByteArray where T : BaseDoc {
    val (xssfWorkbook, _) = convertClassToExcel(this, fileName)

    // 워크북을 ByteArrayOutputStream 변환
    return ByteArrayOutputStream().use {
        xssfWorkbook.write(it)
        xssfWorkbook.close()
        it
    }.toByteArray()
}

/**
 * 객체 리스트를 Excel(XLSX) 파일로 변환합니다.
 *
 * @param fileName 생성할 시트 이름
 * @return Excel 파일 데이터를 포함하는 ByteArray
 */
inline fun <reified T> T.convertToExcel(fileName: String): ByteArrayOutputStream where T : List<T>, T : BaseDoc {
    if (this.isEmpty()) throw RuntimeException("source data is empty.")

    val xssfWorkbook = ExcelUtil.convertToExcel(listOf(this), fileName)

    // 워크북을 ByteArrayOutputStream 변환
    return ByteArrayOutputStream().use {
        xssfWorkbook.write(it)
        xssfWorkbook.close()
        it
    }
}