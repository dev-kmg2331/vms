package com.oms.vms

import reactor.core.publisher.Mono

interface LoginRequired {
    fun login(): Mono<Void>
}