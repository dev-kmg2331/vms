package com.oms.vms.app.config.api

import com.oms.api.exception.ApiExceptionHandlerBase
import org.springframework.web.bind.annotation.ControllerAdvice

@ControllerAdvice
class ApiExceptionHandler: ApiExceptionHandlerBase()