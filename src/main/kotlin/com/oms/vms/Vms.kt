package com.oms.vms

import com.oms.api.exception.ApiAccessException
import com.oms.vms.dahua.DahuaNvr
import com.oms.vms.emstone.EmstoneNvr
import com.oms.vms.naiz.NaizVms
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

interface Vms {
    val type: String

    suspend fun download()

    suspend fun synchronize()

    suspend fun getRtspURL(id: String): String

    fun initialize()
}

enum class VmsType(val serviceName: String, val serviceClass: Class<*>) {
    EMSTONE("emstone", EmstoneNvr::class.java),
    NAIZ("naiz", NaizVms::class.java),
    DAHUA("dahua", DahuaNvr::class.java);

    companion object {
        fun findByServiceName(serviceName: String): VmsType {
            return entries.find { it.serviceName == serviceName }
                ?: throw IllegalArgumentException("service name $serviceName is not defined.")
        }
    }
}

@Component
class VmsFactory(private val applicationContext: ApplicationContext) {

    private val beans = applicationContext.getBeansOfType(Vms::class.java).values

    fun getService(type: String): Vms {
        return try {
            val vmsType = VmsType.findByServiceName(type)
            // filter 사용하여 클래스 타입 검사
            beans.firstOrNull { vmsType.serviceClass.isInstance(it) }
                ?: throw NoSuchElementException("No VMS service of type $type found")
        } catch (e: IllegalArgumentException) {
            throw ApiAccessException(HttpStatus.BAD_REQUEST, "VMS type $type not found.")
        } catch (e: NoSuchElementException) {
            throw ApiAccessException(HttpStatus.BAD_REQUEST, e)
        } catch (e: Exception) {
            throw ApiAccessException(HttpStatus.INTERNAL_SERVER_ERROR, "internal error while finding $type VMS service.")
        }
    }

    fun getAllServices(): Set<Vms> = beans.toSet()
}