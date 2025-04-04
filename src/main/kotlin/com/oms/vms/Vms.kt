package com.oms.vms

import org.springframework.stereotype.Component

interface Vms {
    suspend fun download()

    suspend fun synchronize()

    suspend fun getRtspURL(): String

    fun initialize()

    companion object {
        const val RAW_JSON = "vms_raw_json"
        const val CAMERA = "vms_camera"
        const val CAMERA_KEYS = "vms_camera_keys"
    }
}