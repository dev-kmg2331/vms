package com.oms.vms.app.config

import com.oms.logging.config.RequestLoggingConfig
import com.oms.logging.filter.RequestLoggingFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowedHeaders("Authorization", "Content-Type")
            .exposedHeaders("Custom-Header")
            .allowCredentials(false)
            .maxAge(3600)
    }

    @Bean
    fun requestLoggingFilter(): RequestLoggingFilter {
        return RequestLoggingFilter(includePayload = false)
    }
}