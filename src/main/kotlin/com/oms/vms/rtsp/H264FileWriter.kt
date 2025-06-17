package com.oms.vms.rtsp

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream

class H264FileWriter(filePath: String) {
    private val outputFile = File(filePath)
    private val fileOutputStream = FileOutputStream(outputFile)
    private val log = LoggerFactory.getLogger(this::class.java)
    
    // NAL 구분자
    private val nalSeparator = byteArrayOf(0x00, 0x00, 0x00, 0x01)
    
    // 파라미터 셋 저장
    private var spsData: ByteArray? = null
    private var ppsData: ByteArray? = null
    private var hasWrittenParameterSets = false
    
    // 프레임 카운터
    private var frameCount = 0
    
    fun writeNALUnit(nalType: Int, nalData: ByteArray) {
        if (nalData.isEmpty()) return
        
        when (nalType) {
            7 -> {
                // SPS 저장
                spsData = nalData
                log.debug("📋 SPS 저장됨 (${nalData.size} bytes)")
                writeParameterSet(nalData)
            }
            
            8 -> {
                // PPS 저장  
                ppsData = nalData
                log.debug("📋 PPS 저장됨 (${nalData.size} bytes)")
                writeParameterSet(nalData)
            }
            
            5 -> {
                // IDR 프레임 - 파라미터 셋 먼저 확인
                ensureParameterSetsWritten()
                writeFrameWithAUD(nalData, isKeyframe = true)
            }
            
            1 -> {
                // 일반 프레임 (FU-A로 조립된 것 포함)
                writeFrameWithAUD(nalData, isKeyframe = false)
            }
            
            28 -> {
                // FU-A 조립된 NAL Unit 처리
                val actualNalType = nalData[0].toInt() and 0x1F
                when (actualNalType) {
                    5 -> {
                        ensureParameterSetsWritten()
                        writeFrameWithAUD(nalData, isKeyframe = true)
                    }
                    1 -> {
                        writeFrameWithAUD(nalData, isKeyframe = false)
                    }
                    else -> {
                        writeRawNAL(nalData)
                        log.debug("FU-A 조립된 기타 NAL Unit: Type=$actualNalType, Size=${nalData.size}")
                    }
                }
            }
            
            9 -> {
                // AUD는 그대로 쓰기 (이미 있다면)
                writeRawNAL(nalData)
            }
            
            else -> {
                // 기타 NAL Unit
                writeRawNAL(nalData)
                log.debug("기타 NAL Unit: Type=$nalType, Size=${nalData.size}")
            }
        }
    }
    
    private fun writeFrameWithAUD(nalData: ByteArray, isKeyframe: Boolean) {
        frameCount++
        
        // 1. Access Unit Delimiter 먼저 쓰기
        writeAccessUnitDelimiter(isKeyframe)
        
        // 2. 키프레임이면 파라미터 셋 재전송 (선택적)
        if (isKeyframe && frameCount > 1) {
            spsData?.let { writeRawNAL(it) }
            ppsData?.let { writeRawNAL(it) }
        }
        
        // 3. 실제 프레임 데이터 쓰기
        writeRawNAL(nalData)
        
        val frameType = if (isKeyframe) "🔑 IDR" else "🎬 P/B"
        log.debug("$frameType 프레임 #$frameCount 작성 (${nalData.size} bytes)")
    }
    
    private fun writeAccessUnitDelimiter(isKeyframe: Boolean) {
        // AUD NAL Unit 생성
        // NAL Header: Type=9 (AUD)
        val audNalHeader = 0x09.toByte()
        
        // AUD Payload: 프레임 타입 표시
        val primaryPicType = if (isKeyframe) {
            0x10.toByte()  // I frames only (키프레임)
        } else {
            0x30.toByte()  // I, P frames (일반 프레임)
        }
        
        val audNal = byteArrayOf(audNalHeader, primaryPicType)
        writeRawNAL(audNal)
        
        log.debug("AUD 작성: ${if (isKeyframe) "I-frame" else "P-frame"}")
    }
    
    private fun writeParameterSet(nalData: ByteArray) {
        writeRawNAL(nalData)
        hasWrittenParameterSets = true
    }
    
    private fun ensureParameterSetsWritten() {
        if (!hasWrittenParameterSets) {
            spsData?.let { 
                writeRawNAL(it)
                log.info("지연된 SPS 작성")
            }
            ppsData?.let { 
                writeRawNAL(it)
                log.info("지연된 PPS 작성")
            }
            hasWrittenParameterSets = true
        }
    }
    
    private fun writeRawNAL(nalData: ByteArray) {
        try {
            // NAL 구분자 쓰기
            fileOutputStream.write(nalSeparator)
            // NAL 데이터 쓰기
            fileOutputStream.write(nalData)
            fileOutputStream.flush()
            
        } catch (e: Exception) {
            log.error("NAL Unit 쓰기 실패: ${e.message}")
        }
    }
    
    private fun writeEndOfStream() {
        // End of Stream NAL Unit (Type 11)
        val eosNal = byteArrayOf(0x0B.toByte())
        writeRawNAL(eosNal)
        log.info("🏁 End of Stream 작성")
    }
    
    fun close() {
        try {
            // 스트림 종료 마커 추가
            writeEndOfStream()
            
            fileOutputStream.close()
            log.info("✅ H.264 파일 작성 완료")
            log.info("   파일: ${outputFile.absolutePath}")
            log.info("   크기: ${outputFile.length()} bytes")

        } catch (e: Exception) {
            log.error("파일 닫기 실패: ${e.message}")
        }
    }
    
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "frameCount" to frameCount,
            "fileSize" to outputFile.length(),
            "hasParameterSets" to hasWrittenParameterSets,
            "hasSPS" to (spsData != null),
            "hasPPS" to (ppsData != null)
        )
    }
}