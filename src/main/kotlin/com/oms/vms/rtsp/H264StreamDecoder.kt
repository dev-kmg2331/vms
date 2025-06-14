package com.oms.vms.rtsp

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream


/**
 * H.264 스트림 디코더 클래스
 */
class H264StreamDecoder {
    private val nalUnitStartCode = byteArrayOf(0x00, 0x00, 0x00, 0x01)
    private var frameGrabber: FFmpegFrameGrabber? = null
    private var frameConverter: Java2DFrameConverter? = null

    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        try {
            frameConverter = Java2DFrameConverter()

            log.info("H.264 decoder initialized.")
            true
        } catch (e: Exception) {
            log.error("H.264 decoder initialize failed.", e)
            false
        }
    }

    fun decodeFrame(
        frameData: ByteArray,
        sdpData: ByteArray,
        ppsData: ByteArray,
        frameNumber: Int
    ): DecodedFrameResult? {
        return try {
            // SPS, PPS와 함께 완전한 H.264 스트림 생성
            val completeStream = createCompleteH264Stream(frameData, sdpData, ppsData)

            log.info("H.264 decoding finished. ${completeStream.size} | frame: ${frameData.size} | sdp: ${sdpData.size} | pps: ${ppsData.size}")

            val inputStream = completeStream.inputStream()

            // FFmpegFrameGrabber로 디코딩
            frameGrabber = FFmpegFrameGrabber(inputStream).apply {
                format = "h264"
                imageWidth = 1280
                imageHeight = 720
                start()
            }

            val frame = frameGrabber!!.grab()

            if (frame != null && frame.image != null) {
                // BufferedImage로 변환
                val bufferedImage = frameConverter!!.convert(frame)

                val decodedFrameResult = DecodedFrameResult(
                    frameNumber = frameNumber,
                    bufferedImage = bufferedImage,
//                    openCVMat = mat,
                    width = frame.imageWidth,
                    height = frame.imageHeight,
                    timestamp = System.currentTimeMillis()
                )

                log.info("$decodedFrameResult")

                decodedFrameResult
            } else {
                log.warn("프레임 #$frameNumber 디코딩 실패: null frame")
                null
            }

        } catch (e: Exception) {
            log.error("프레임 #$frameNumber 디코딩 중 오류", e)
            null
        } finally {
            // 리소스 정리
            frameGrabber?.stop()
            frameGrabber?.release()
            frameGrabber = null
        }
    }

    private fun createCompleteH264Stream(frameData: ByteArray, spsData: ByteArray, ppsData: ByteArray): ByteArray {
        val stream = ByteArrayOutputStream()

        // SPS 추가
        spsData.let { sps ->
            stream.write(nalUnitStartCode)
            stream.write(sps)
        }

        // PPS 추가
        ppsData.let { pps ->
            stream.write(nalUnitStartCode)
            stream.write(pps)
        }

        // 프레임 데이터 추가 (이미 start code가 포함되어 있을 수 있음)
        if (!frameData.sliceArray(IntRange(0, 4)).contentEquals(nalUnitStartCode)) {
            stream.write(nalUnitStartCode)
        }
        stream.write(frameData)

        return stream.toByteArray()
    }

    fun release() {
        frameGrabber?.stop()
        frameGrabber?.release()
        frameConverter?.close()
    }
}

/**
 * H.264 디코딩된 프레임 결과.
 * */
data class DecodedFrameResult(
    val frameNumber: Int,
    val bufferedImage: BufferedImage?,
//    val openCVMat: Mat?,
    val width: Int,
    val height: Int,
    val timestamp: Long
) {
    override fun toString(): String {
        val imageType = bufferedImage?.type?.let { type ->
            when (type) {
                BufferedImage.TYPE_3BYTE_BGR -> "BGR"
                BufferedImage.TYPE_4BYTE_ABGR -> "ABGR"
                BufferedImage.TYPE_INT_RGB -> "RGB"
                BufferedImage.TYPE_INT_ARGB -> "ARGB"
                BufferedImage.TYPE_BYTE_GRAY -> "GRAY"
                else -> "TYPE_$type"
            }
        } ?: "N/A"

        val imageSizeKB = bufferedImage?.let { img ->
            (img.width * img.height * when (img.type) {
                BufferedImage.TYPE_3BYTE_BGR -> 3
                BufferedImage.TYPE_4BYTE_ABGR -> 4
                BufferedImage.TYPE_INT_RGB -> 4
                BufferedImage.TYPE_INT_ARGB -> 4
                BufferedImage.TYPE_BYTE_GRAY -> 1
                else -> 3
            }) / 1024
        } ?: 0

        val timestampFormatted = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

        return """
            Decoded Frame Result:
            - Frame Number: #$frameNumber
            - Resolution: ${width}x${height}
            - Image Type: $imageType
            - Image Size: ${imageSizeKB}KB
            - Timestamp: $timestampFormatted
            - Raw Timestamp: $timestamp
            - Has Image: ${bufferedImage != null}
            """.trimIndent()
    }
}