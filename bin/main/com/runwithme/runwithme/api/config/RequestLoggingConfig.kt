package com.runwithme.runwithme.api.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class RequestLoggingConfig : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(RequestLoggingInterceptor())
    }
}

class RequestLoggingInterceptor : HandlerInterceptor {
    private val logger = LoggerFactory.getLogger(RequestLoggingInterceptor::class.java)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val startTime = System.currentTimeMillis()
        request.setAttribute("startTime", startTime)

        val logMessage =
            buildString {
                appendLine("=== Incoming HTTP Request ===")
                append("Method: ${request.method} | ")
                appendLine("URI: ${request.requestURI}")

                if (request.queryString != null) {
                    appendLine("Query String: ${request.queryString}")
                }

                appendLine("Remote Address: ${request.remoteAddr}")

                // Log headers
                appendLine("Headers:")
                request.headerNames.toList().forEach { headerName ->
                    val headerValue = request.getHeader(headerName)
                    // Mask sensitive headers
                    val maskedValue =
                        if (headerName.lowercase() in listOf("authorization", "cookie", "x-api-key")) {
                            "***MASKED***"
                        } else {
                            headerValue
                        }
                    appendLine("  $headerName: $maskedValue")
                }

                // Log parameters
                if (request.parameterMap.isNotEmpty()) {
                    appendLine("Parameters:")
                    request.parameterMap.forEach { (key, values) ->
                        appendLine("  $key: ${values.joinToString(", ")}")
                    }
                }
            }

        logger.info(logMessage)
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val startTime = request.getAttribute("startTime") as? Long ?: return
        val duration = System.currentTimeMillis() - startTime

        val logMessage =
            buildString {
                appendLine("=== HTTP Response ===")
                append("Method: ${request.method} | ")
                append("URI: ${request.requestURI} | ")
                append("Status: ${response.status} | ")
                appendLine("Duration: ${duration}ms")

                if (ex != null) {
                    appendLine("Exception: ${ex.message}")
                }
            }

        logger.info(logMessage)
    }
}
