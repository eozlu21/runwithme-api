package com.runwithme.runwithme.api.mcp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpMethod
import java.util.UUID

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

data class McpRoute(
    val name: String,
    val description: String,
    val method: HttpMethod,
    val pathTemplate: String,
    val parameters: List<McpRouteParameter> = emptyList(),
    val requiresAuth: Boolean = true,
    val errorTemplates: Map<Int, String> = emptyMap(),
)

data class McpRouteParameter(
    val name: String,
    val description: String,
    val required: Boolean = true,
    val location: McpRouteParameterLocation = McpRouteParameterLocation.PATH,
)

enum class McpRouteParameterLocation {
    PATH,
    QUERY,
    BODY,
}

data class McpConversationTurn(
    val role: McpConversationRole,
    val content: String,
)

enum class McpConversationRole {
    USER,
    AGENT,
}

data class McpAgentRequest(
    @field:NotBlank(message = "Prompt must not be blank")
    val prompt: String,
    val resetHistory: Boolean = false,
)

data class McpAgentResponse(
    val success: Boolean,
    val routeName: String?,
    val requestedUrl: String?,
    val apiBody: String?,
    val llmMessage: String?,
    // Human-readable text extracted from the LLM JSON envelope for UI consumption.
    val userMessage: String?,
    val routeDecisionReason: String?,
    val resolvedArguments: Map<String, String>?,
    val starterUserId: UUID,
    val error: String?,
)

internal data class ResolvedRoute(
    val path: String,
    val body: String?,
)

internal data class AgentIdentity(
    val username: String,
    val userId: UUID,
)

data class ExternalApiResult(
    val routeName: String,
    val url: String,
    val body: String,
)

class ExternalApiCallException(
    val routeName: String,
    val url: String,
    val statusCode: Int?,
    val responseBody: String?,
    cause: Throwable,
) : RuntimeException(cause)

enum class GeminiLogStage {
    ROUTE_SELECTION,
    ANSWER_GENERATION,
}

data class GeminiCallResult(
    val text: String?,
    val error: String?,
)

data class GeminiRouteDecision(
    val routeName: String?,
    val reason: String?,
    val arguments: Map<String, String>?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RouteSelectionPayload(
    val routeName: String?,
    val reason: String?,
    val arguments: List<GeminiArgumentPair>? = emptyList(),
)

data class GeminiArgumentPair(
    val key: String,
    val value: String,
)

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = null,
    val responseSchema: Map<String, Any>? = null,
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiTextPart>,
)

data class GeminiTextPart(
    val text: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiCandidate(
    val content: GeminiResponseContent? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiResponseContent(
    val parts: List<GeminiResponsePart>? = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiResponsePart(
    val text: String? = null,
)

data class GeminiRouteDescriptionPayload(
    val name: String,
    val description: String,
    val method: String,
    val pathTemplate: String,
    val parameters: List<GeminiRouteParameterPayload>,
)

data class GeminiRouteParameterPayload(
    val name: String,
    val description: String,
    val required: Boolean,
    val location: String,
)

data class GeminiChatConfig(
    val systemInstruction: String,
    val responseMimeType: String,
    val temperature: Double,
    val maxOutputTokens: Int,
    val responseSchema: Map<String, Any>? = null,
)

data class GeminiChatSession(
    val config: GeminiChatConfig,
    val history: MutableList<GeminiContent>,
)

data class GeminiChatKey(
    val starterUserId: UUID,
    val stage: GeminiLogStage,
)

internal data class AuthenticatedUser(
    val userId: UUID,
    val username: String,
)

internal data class McpReadAllMessagesResponse(
    val updatedCount: Int,
)

internal data class McpChatErrorResponse(
    val message: String,
)

