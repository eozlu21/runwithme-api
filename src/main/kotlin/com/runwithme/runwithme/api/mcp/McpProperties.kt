package com.runwithme.runwithme.api.mcp

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mcp")
data class McpProperties(
    val externalApiBaseUrl: String = "https://jsonplaceholder.typicode.com",
    val geminiModel: String = "gemini-1.5-flash",
    val geminiApiKey: String = "",
    val agentUsername: String = "",
    val agentUserId: String = "",
    val agentRequestHeader: String = "X-MCP-Agent",
    val responseFilter: ResponseFilterProperties = ResponseFilterProperties(),
    val mcpLogFile: String = "logs/mcp.log",
) {
    data class ResponseFilterProperties(
        val enabled: Boolean = false,
        val redactedFields: Set<String> = emptySet(),
    )
}
