package com.oms.vms.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VmsConfiguration {

    @Bean
    fun vmsConfig() = VmsConfig(
        id = "admin",
        password = "oms20190211",
        ip = "192.168.182.200",
        port = "80"
//        id = "test",
//        password = "1234",
//        ip = "naiz.re.kr",
//        port = "8002"
    )
}

class VmsConfig(
    var id: String,
    var password: String,
    var ip: String,
    var port: String,
)