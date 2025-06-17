package com.oms.vms.rtsp

data class NALUnitHeader(
    val forbiddenZeroBit: Int,    // F bit (1비트) - 항상 0이어야 함
    val nalRefIdc: Int,           // NRI (2비트) - 참조 우선순위 (0-3)
    val nalUnitType: Int,         // Type (5비트) - NAL 유닛 타입 (0-31)
    val headerLength: Int = 1     // 헤더 길이 (기본 1바이트, 확장시 더 길어짐)
) {
    
    // NAL 타입별 설명 반환
    fun getTypeDescription(): String {
        return when (nalUnitType) {
            0 -> "Unspecified"
            1 -> "Coded slice of a non-IDR picture"
            2 -> "Coded slice data partition A"
            3 -> "Coded slice data partition B" 
            4 -> "Coded slice data partition C"
            5 -> "Coded slice of an IDR picture (Keyframe)"
            6 -> "Supplemental enhancement information (SEI)"
            7 -> "Sequence parameter set (SPS)"
            8 -> "Picture parameter set (PPS)"
            9 -> "Access unit delimiter"
            10 -> "End of sequence"
            11 -> "End of stream"
            12 -> "Filler data"
            13 -> "Sequence parameter set extension"
            14 -> "Prefix NAL unit"
            15 -> "Subset sequence parameter set"
            16 -> "Depth parameter set"
            17, 18 -> "Reserved"
            19 -> "Coded slice of an auxiliary coded picture without partitioning"
            20 -> "Coded slice extension"
            21 -> "Coded slice extension for depth view components"
            22, 23 -> "Reserved"
            24, 25, 26, 27 -> "STAP-A, STAP-B, MTAP16, MTAP24 (RTP)"
            28, 29 -> "FU-A, FU-B (RTP Fragmentation)"
            30, 31 -> "Undefined"
            else -> "Unknown ($nalUnitType)"
        }
    }
    
    // 참조 우선순위 설명
    fun getRefIdcDescription(): String {
        return when (nalRefIdc) {
            0 -> "Non-reference (disposable)"
            1 -> "Low priority reference"
            2 -> "High priority reference" 
            3 -> "Highest priority reference"
            else -> "Invalid ($nalRefIdc)"
        }
    }
    
    // 키프레임 여부 확인
    fun isKeyframe(): Boolean = nalUnitType == 5
    
    // 참조 프레임 여부 확인  
    fun isReferenceFrame(): Boolean = nalRefIdc > 0
    
    // 파라미터 셋 여부 확인
    fun isParameterSet(): Boolean = nalUnitType in 7..8
    
    // RTP 패킷 타입 여부 확인
    fun isRTPPacket(): Boolean = nalUnitType in 24..29
    
    // 헤더 유효성 검증
    fun isValid(): Boolean {
        return forbiddenZeroBit == 0 && 
               nalRefIdc in 0..3 && 
               nalUnitType in 0..31
    }
    
    override fun toString(): String {
        return "NAL Header - Type: $nalUnitType (${getTypeDescription()}), " +
               "RefIdc: $nalRefIdc (${getRefIdcDescription()}), " +
               "HeaderLen: $headerLength"
    }
}