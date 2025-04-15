package com.oms.vms

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.time.Instant

lateinit var applicationStartTime: Instant

@SpringBootApplication
class VmsSynchronizeApplication

fun main(args: Array<String>) {
    applicationStartTime = Instant.now()
    runApplication<VmsSynchronizeApplication>(*args)
}
