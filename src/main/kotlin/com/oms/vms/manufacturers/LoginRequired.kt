package com.oms.vms.manufacturers

import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

interface LoginRequired {
    fun login(sessionClient: WebClient): Mono<Void>
}