package com.oms.vms.dahua

import com.google.gson.JsonObject
import com.oms.logging.gson.gson
import com.oms.vms.DefaultVms
import com.oms.vms.VmsType
import com.oms.vms.sync.VmsSynchronizeUtil
import com.oms.vms.config.DigestAuthenticatorClient
import com.oms.vms.config.VmsConfig
import com.oms.vms.persistence.mongo.repository.ReactiveMongoRepo
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.regex.Pattern

@Component(value = "dahua")
class DahuaNvr(
    private val webClient: WebClient,
    private val vmsConfig: VmsConfig,
    private val reactiveMongoRepo: ReactiveMongoRepo
) : DefaultVms() {
    private val client: DigestAuthenticatorClient = DigestAuthenticatorClient(webClient, vmsConfig.id, vmsConfig.password)
    override val type = VmsType.DAHUA.serviceName

    override suspend fun download() {
        TODO("Not yet implemented")
    }

    override suspend fun synchronize() {
        val uri = "/cgi-bin/configManager.cgi?action=getConfig&name=RemoteDevice"
        val response = client.makeRequest(uri).awaitSingle()

        // 공통 유틸리티를 사용하여 데이터 동기화
        VmsSynchronizeUtil.synchronize(
            rawResponse = response,
            uri = uri,
            vmsType = type,
            mongoRepo = reactiveMongoRepo,
            processJsonData = processJsonData
        )
    }

    override suspend fun getRtspURL(id: String): String = ""

    private val processJsonData: (String) -> List<JsonObject> = { responseText ->
        val cameraList = parseCameraList(responseText)
        cameraList.map { camera ->
            gson.toJsonTree(camera).asJsonObject
        }
    }

    private fun parseCameraList(content: String): List<DahuaCameraSync> {
        val cameras = mutableMapOf<String, DahuaCameraSync>()
        val lines = content.split("\n")

        for (line in lines) {
            val parts = line.split("=", limit = 2)
            if (parts.size != 2) continue

            val key = parts[0].trim()
            val value = parts[1].trim()

            val keyParts = key.split(".")
            if (keyParts.size < 4) continue

            val infoIndex = keyParts[2] // 예: "System_CONFIG_NETCAMERA_INFO_10"

            if (!cameras.containsKey(infoIndex)) {
                cameras[infoIndex] = DahuaCameraSync()
            }

            val camera = cameras[infoIndex]!!

            // 채널 인덱스 파싱 (예: "System_CONFIG_NETCAMERA_INFO_10"에서 숫자만 추출)
            val pattern = Pattern.compile("_(\\d+)$")
            val matcher = pattern.matcher(infoIndex)
            if (matcher.find()) {
                try {
                    val channelNumber = matcher.group(1).toInt()
                    camera.channelIndex = channelNumber // 숫자로 변환해서 ChannelIndex에 저장
                    camera.channelName = "채널${channelNumber + 1}" // "채널1", "채널2"와 같은 형식으로 저장
                } catch (e: NumberFormatException) {
                    // 숫자 변환 실패시 무시
                }
            }

            // 전체 키 경로를 분석
            val propertyPathBuilder = StringBuilder()
            for (i in 3 until keyParts.size) {
                if (i > 3) propertyPathBuilder.append(".")
                propertyPathBuilder.append(keyParts[i])
            }
            val propertyPath = propertyPathBuilder.toString()

            when (propertyPath) {
                "Address" -> camera.address = value
                "DeviceType" -> {
                    camera.deviceType = value
                    camera.model = value
                }
                "Enable" -> camera.isEnabled = value.equals("true", ignoreCase = true)
                "Port" -> try {
                    camera.port = value.toInt()
                } catch (e: NumberFormatException) {
                    // 숫자 변환 실패시 무시
                }
                "HttpPort" -> try {
                    camera.httpPort = value.toInt()
                } catch (e: NumberFormatException) {
                    // 숫자 변환 실패시 무시
                }
                "SerialNo" -> camera.serialNumber = value
                "VideoInputChannels" -> try {
                    camera.videoInputChannels = value.toInt()
                } catch (e: NumberFormatException) {
                    // 숫자 변환 실패시 무시
                }
                "Version" -> camera.version = value
                "Vendor" -> camera.vendor = value
                "ProtocolType" -> camera.protocolType = value
                "AlarmInChannels" -> try {
                    camera.alarmInChannels = value.toInt()
                } catch (e: NumberFormatException) {
                    // 숫자 변환 실패시 무시
                }
                "AudioInputChannels" -> try {
                    camera.audioInputChannels = value.toInt()
                } catch (e: NumberFormatException) {
                    // 숫자 변환 실패시 무시
                }
                "VideoInputs[0].Name" -> camera.name = value
            }
        }

        // 카메라 이름이 설정되지 않은 경우 채널명으로 설정
        for (camera in cameras.values) {
            if (camera.name.isNullOrBlank()) {
                // 채널명으로 카메라 이름을 설정
                camera.name = camera.channelName
            }
        }

        // ChannelIndex 기준으로 오름차순 정렬하여 반환
        return cameras.values
            .filter { it.isEnabled }
            .sortedBy { it.channelIndex }
    }

    data class DahuaCameraSync(
        var name: String? = null,
        var channelName: String? = null,
        var channelIndex: Int = 0,
        var address: String? = null,
        var port: Int = 0,
        var httpPort: Int = 0,
        var model: String? = null,
        var serialNumber: String? = null,
        var isEnabled: Boolean = false,
        var deviceType: String? = null,
        var videoInputChannels: Int = 0,
        var version: String? = null,
        var vendor: String? = null,
        var protocolType: String? = null,
        var alarmInChannels: Int = 0,
        var audioInputChannels: Int = 0
    )
}