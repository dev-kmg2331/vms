package com.oms.vms

import com.fasterxml.jackson.annotation.JsonProperty
import com.oms.api.exception.ApiAccessException
import com.oms.vms.manufacturers.dahua.DahuaNvr
import com.oms.vms.manufacturers.emstone.EmstoneNvr
import com.oms.vms.manufacturers.naiz.NaizVms
import com.oms.vms.mongo.docs.VmsConfig
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

interface Vms {
    val type: String

    suspend fun download()

    suspend fun synchronize()

    suspend fun getRtspURL(id: String): String

    fun initialize()
    suspend fun getVmsConfig(): VmsConfig
    suspend fun saveVmsConfig(vmsConfigDoc: VmsConfig): VmsConfig
    suspend fun setVmsConfigActive(active: Boolean): VmsConfig
}

enum class VmsType(val serviceName: String, val serviceClass: Class<*>) {
    // @JsonProperty required for swagger...!
    @JsonProperty("emstone")
    EMSTONE("emstone", EmstoneNvr::class.java),

    @JsonProperty("naiz")
    NAIZ("naiz", NaizVms::class.java),

    @JsonProperty("dahua")
    DAHUA("dahua", DahuaNvr::class.java);

    companion object {
        fun findByServiceName(serviceName: String): VmsType {
            return entries.find { it.serviceName == serviceName }
                ?: throw IllegalArgumentException("service name $serviceName is not defined.")
        }

        val serviceNames = VmsType.entries.map { it.serviceName }
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
            throw ApiAccessException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal error while finding $type VMS service."
            )
        }
    }

    fun getAllServices(): Set<Vms> = beans.toSet()
}