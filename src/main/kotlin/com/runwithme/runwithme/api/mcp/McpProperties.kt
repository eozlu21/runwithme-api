package com.runwithme.runwithme.api.mcp

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mcp")
data class McpProperties(
    val externalApiBaseUrl: String,
    val geminiModel: String,
    val geminiApiKey: String,
    val agentRequestHeader: String,
    val responseFilter: ResponseFilterProperties,
) {
    data class ResponseFilterProperties(
        val enabled: Boolean,
        val redactedFields: Set<String>,
    )
}
