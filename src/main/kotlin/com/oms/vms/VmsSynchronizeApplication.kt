package com.oms.vms

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VmsSynchronizeApplication

fun main(args: Array<String>) {
    runApplication<VmsSynchronizeApplication>(*args)
}
