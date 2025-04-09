package com.oms.vms.manufacturers

import org.springframework.http.HttpHeaders
import java.util.function.Consumer

interface SessionRequired {
    fun <T> refreshSession(headersConsumer: Consumer<HttpHeaders>, body: T)
}