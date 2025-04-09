package com.oms.vms.manufacturers

import reactor.core.publisher.Mono

interface LoginRequired {
    fun login(): Mono<Void>
}