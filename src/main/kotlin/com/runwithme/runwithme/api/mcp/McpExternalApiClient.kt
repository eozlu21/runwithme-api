package com.runwithme.runwithme.api.mcp

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.RequestEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.net.URI

@Component
class McpExternalApiClient(
    private val restTemplate: RestTemplate,
    private val properties: McpProperties,
) {
    private val logger = LoggerFactory.getLogger(McpExternalApiClient::class.java)

    // Calls the resolved route with the caller's Authorization header.
    fun fetchData(route: McpRoute, resolvedPath: String, authorizationHeader: String?): ExternalApiResult {
        val urlToCall = resolveUrl(resolvedPath)
        val headers = HttpHeaders()
        if (route.requiresAuth) {
            val token =
                authorizationHeader?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("`${route.name}` cagrisini calistirmak icin kullanici yetkisi gerekiyor.")
            headers.set(HttpHeaders.AUTHORIZATION, token)
        }
        val request = RequestEntity<Any>(null, headers, route.method, URI.create(urlToCall))
        logger.info("Calling external API for route='{}' url='{}'", route.name, urlToCall)
        val body =
            try {
                restTemplate.exchange(request, String::class.java).body ?: ""
            } catch (ex: RestClientException) {
                logger.error("External API call failed for route='{}': {}", route.name, ex.message)
                throw IllegalStateException("`${route.name}` cagrisi basarisiz oldu: ${ex.message}", ex)
            }
        logger.debug("External API response received for route='{}' ({} chars)", route.name, body.length)
        return ExternalApiResult(route.name, urlToCall, body)
    }

    // Builds the final URL by combining the base and optional relative path.
    private fun resolveUrl(rawPath: String): String =
        if (rawPath.startsWith("http", ignoreCase = true)) {
            rawPath
        } else {
            properties.externalApiBaseUrl.trimEnd('/') + "/" + rawPath.trimStart('/')
        }
}

data class ExternalApiResult(
    val routeName: String,
    val url: String,
    val body: String,
)
