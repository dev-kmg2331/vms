package com.oms.vms.rtsp

data class RTPHeader(
    var version: Int = 0,           // RTP 버전 (보통 2)
    var padding: Boolean = false,    // 패딩 여부
    var extension: Boolean = false,  // 확장 헤더 여부
    var csrcCount: Int = 0,         // CSRC 개수
    var marker: Boolean = false,     // 마커 비트 (프레임 끝 표시)
    var payloadType: Int = 0,       // 페이로드 타입 (H.264는 보통 96-127)
    var sequenceNumber: Long = 0,    // 시퀀스 번호 (패킷 순서)
    var timestamp: Long = 0,        // 타임스탬프 (90kHz 클록)
    var ssrc: Int = 0              // 동기화 소스 식별자
) {
    override fun toString(): String {
        return """
            RTP Header:
            - Version: $version
            - Padding: $padding
            - Extension: $extension
            - CSRC Count: $csrcCount
            - Marker: $marker
            - Payload Type: $payloadType
            - Sequence Number: $sequenceNumber
            - Timestamp: $timestamp
            - SSRC: 0x${ssrc.toString(16).uppercase()}
        """.trimIndent()
    }
}