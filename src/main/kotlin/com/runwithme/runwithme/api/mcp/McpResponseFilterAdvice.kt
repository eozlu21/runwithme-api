package com.runwithme.runwithme.api.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

/**
 * Removes sensitive fields from API responses when the request originates from the MCP agent.
 */
@RestControllerAdvice(basePackages = ["com.runwithme.runwithme.api"])
class McpResponseFilterAdvice(
    private val objectMapper: ObjectMapper,
    private val properties: McpProperties,
) : ResponseBodyAdvice<Any> {
    private val logger = LoggerFactory.getLogger(McpResponseFilterAdvice::class.java)

    private val redactedFieldNames: Set<String>
        get() =
            properties.responseFilter.redactedFields
                .map { it.lowercase() }
                .toSet()

    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>,
    ): Boolean = properties.responseFilter.enabled && redactedFieldNames.isNotEmpty()

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        if (body == null || !shouldSanitize(request, selectedContentType)) {
            return body
        }
        val jsonNode =
            try {
                toJsonNode(body)
            } catch (ex: Exception) {
                logger.warn("Unable to sanitize MCP response payload: {}", ex.message)
                return body
            }
        val fieldsToRedact = redactedFieldNames
        sanitize(jsonNode, fieldsToRedact)
        return if (body is String) {
            objectMapper.writeValueAsString(jsonNode)
        } else {
            jsonNode
        }
    }

    private fun shouldSanitize(
        request: ServerHttpRequest,
        mediaType: MediaType,
    ): Boolean {
        if (!isJsonMediaType(mediaType)) {
            return false
        }
        val markerValue = request.headers[properties.agentRequestHeader]?.firstOrNull()
        return !markerValue.isNullOrBlank()
    }

    private fun isJsonMediaType(mediaType: MediaType): Boolean =
        MediaType.APPLICATION_JSON.includes(mediaType) ||
            mediaType.subtype.equals("json", ignoreCase = true) ||
            mediaType.subtype.lowercase().endsWith("+json")

    private fun toJsonNode(body: Any): JsonNode =
        when (body) {
            is JsonNode -> body.deepCopy()
            is String -> objectMapper.readTree(body)
            else -> objectMapper.valueToTree(body)
        }

    private fun sanitize(
        node: JsonNode?,
        fieldsToRedact: Set<String>,
    ) {
        if (node == null || fieldsToRedact.isEmpty()) {
            return
        }
        when (node) {
            is ObjectNode -> sanitizeObject(node, fieldsToRedact)
            is ArrayNode -> node.forEach { sanitize(it, fieldsToRedact) }
        }
    }

    private fun sanitizeObject(
        objectNode: ObjectNode,
        fieldsToRedact: Set<String>,
    ) {
        val fieldsToRemove =
            buildList {
                val iterator = objectNode.fieldNames()
                while (iterator.hasNext()) {
                    val fieldName = iterator.next()
                    if (fieldsToRedact.contains(fieldName.lowercase())) {
                        add(fieldName)
                    } else {
                        sanitize(objectNode.get(fieldName), fieldsToRedact)
                    }
                }
            }
        if (fieldsToRemove.isNotEmpty()) {
            objectNode.remove(fieldsToRemove)
        }
    }
}
