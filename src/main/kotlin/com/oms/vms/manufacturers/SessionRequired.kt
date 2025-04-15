package com.oms.vms.manufacturers

import org.springframework.http.HttpHeaders
import java.util.function.Consumer

interface SessionRequired {
    fun <T> refreshSession(uri: String, headers: Map<String, String>, body: T)
}