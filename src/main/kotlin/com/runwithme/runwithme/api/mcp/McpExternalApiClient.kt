package com.runwithme.runwithme.api.mcp

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.net.URI

@Component
class McpExternalApiClient(
    private val restTemplate: RestTemplate,
    private val properties: McpProperties,
    private val mcpLogger: McpLogger,
) {
    // Calls the resolved route with the caller's Authorization header.
    fun fetchData(
        route: McpRoute,
        resolvedPath: String,
        authorizationHeader: String?,
        requestBody: String? = null,
    ): ExternalApiResult {
        val urlToCall = resolveUrl(resolvedPath)
        val headers = HttpHeaders()
        headers.set(properties.agentRequestHeader, "true")
        if (route.requiresAuth) {
            val token =
                authorizationHeader?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("`${route.name}` requires a user Authorization header.")
            headers.set(HttpHeaders.AUTHORIZATION, token)
        }
        if (!requestBody.isNullOrEmpty()) {
            headers.contentType = MediaType.APPLICATION_JSON
        }

        val headersForLog =
            headers
                .filterKeys { it.lowercase() != HttpHeaders.AUTHORIZATION.lowercase() }
                .mapValues { (_, values) -> values.joinToString(",") }
        mcpLogger.logExternalApiRequest(
            routeName = route.name,
            method = route.method.name(),
            url = urlToCall,
            headers = headersForLog,
            body = requestBody,
        )

        val request = RequestEntity<String>(requestBody, headers, route.method, URI.create(urlToCall))
        val body =
            try {
                val responseEntity = restTemplate.exchange(request, String::class.java)
                mcpLogger.logExternalApiResponse(
                    routeName = route.name,
                    method = route.method.name(),
                    url = urlToCall,
                    statusCode = responseEntity.statusCode.value(),
                    body = responseEntity.body,
                )
                responseEntity.body ?: ""
            } catch (ex: HttpStatusCodeException) {
                mcpLogger.logExternalApiError(
                    routeName = route.name,
                    method = route.method.name(),
                    url = urlToCall,
                    statusCode = ex.statusCode.value(),
                    errorMessage = "HTTP ${ex.statusCode.value()}",
                    responseBody = ex.responseBodyAsString,
                    throwable = ex,
                )
                throw ExternalApiCallException(
                    routeName = route.name,
                    url = urlToCall,
                    statusCode = ex.statusCode.value(),
                    responseBody = ex.responseBodyAsString,
                    cause = ex,
                )
            } catch (ex: RestClientException) {
                mcpLogger.logExternalApiError(
                    routeName = route.name,
                    method = route.method.name(),
                    url = urlToCall,
                    statusCode = null,
                    errorMessage = ex.message ?: "RestClientException",
                    throwable = ex,
                )
                throw IllegalStateException("`${route.name}` call failed: ${ex.message}", ex)
            }
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
