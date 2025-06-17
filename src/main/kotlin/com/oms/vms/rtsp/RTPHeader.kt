package com.oms.vms.rtsp

data class RTPHeader(
    val version: Int,
    val hasPadding: Boolean,
    val hasExtension: Boolean,
    val csrcCount: Int,
    val marker: Boolean,
    val payloadType: Int,
    val sequenceNumber: Int,
    val timestamp: Long,
    val ssrc: Long,
    val headerLength: Int
) {
    override fun toString(): String {
        return """
            RTP Header:
            - Version: $version
            - Padding: $hasPadding
            - Extension: $hasExtension
            - CSRC Count: $csrcCount
            - Marker: $marker
            - Payload Type: $payloadType
            - Sequence Number: $sequenceNumber
            - Timestamp: $timestamp
            - SSRC: 0x${ssrc.toString(16).uppercase()}
        """.trimIndent()
    }
}