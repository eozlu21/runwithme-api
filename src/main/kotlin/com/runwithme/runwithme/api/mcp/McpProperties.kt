package com.runwithme.runwithme.api.mcp

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mcp")
data class McpProperties(
    val externalApiBaseUrl: String = "https://jsonplaceholder.typicode.com",
    val geminiModel: String = "gemini-1.5-flash",
    val geminiApiKey: String = "",
    val agentRequestHeader: String = "X-MCP-Agent",
    val responseFilter: ResponseFilterProperties = ResponseFilterProperties(),
) {
    data class ResponseFilterProperties(
        val enabled: Boolean = true,
        val redactedFields: Set<String> = setOf("createdAt", "userId", "created at","email", "emailVerified", "receiverId", "requestId"),
    )
}
