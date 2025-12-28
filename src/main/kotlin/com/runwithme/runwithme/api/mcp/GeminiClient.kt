package com.runwithme.runwithme.api.mcp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.util.UUID

@Component
class GeminiClient(
    private val restTemplate: RestTemplate,
    private val properties: McpProperties,
) {
    private val objectMapper = jacksonObjectMapper()

    // Asks Gemini to choose one of the allowed routes (function calling style).
    fun selectRoute(
        prompt: String,
        routes: List<McpRoute>,
        starterUserId: UUID,
        chatHistory: List<McpConversationTurn> = emptyList(),
    ): GeminiRouteDecision {
        if (routes.isEmpty()) {
            return GeminiRouteDecision(routeName = null, reason = "No route could be selected because the allow-list is empty.", arguments = null)
        }
        if (properties.geminiApiKey.isBlank()) {
            return GeminiRouteDecision(routeName = null, reason = "No route could be selected because MCP_GEMINI_API_KEY is blank.", arguments = null)
        }

        val functionList =
            routes.joinToString(separator = "\n") { route ->
                val params =
                    if (route.parameters.isEmpty()) {
                        "no parameters"
                    } else {
                        route.parameters.joinToString { param ->
                            "${param.name}[${param.location.name.lowercase()}](required=${param.required}, ${param.description})"
                        }
                    }
                "- ${route.name}: ${route.description} [${route.method} ${route.pathTemplate}] Params: $params"
            }
        val selectionPrompt = buildString {
            appendLine("Task: Review the user request and choose exactly one allowed function.")
            appendLine("Starter user ID: $starterUserId")
            appendChatHistory(chatHistory)
            appendLine("Function list:")
            appendLine(functionList)
            appendLine("Rules:")
            appendLine("1) Only choose routeName values from the list.")
            appendLine("2) Output must be JSON: {\"routeName\": \"...\", \"reason\": \"...\", \"arguments\": {\"param\": \"value\"}}")
            appendLine("3) If a parameter is needed, fill it in `arguments` as key=value.")
            appendLine("4) Keep the explanation short and clear.")
            appendLine("5) If none of the functions apply, set routeName to \"$NO_MATCH_ROUTE\" and explain why.")
            appendLine("6) Produce JSON without any Markdown formatting (no bold or italics).")
            appendLine("User prompt: $prompt")
        }
        val selectionResult = callGemini(selectionPrompt)
        if (selectionResult.error != null) {
            return GeminiRouteDecision(routeName = null, reason = selectionResult.error, arguments = null)
        }
        val responseText =
            selectionResult.text
                ?: run {
                    return GeminiRouteDecision(routeName = null, reason = "Gemini response could not be read.", arguments = null)
                }
        val sanitizedResponse = sanitizeJsonCandidate(responseText)
        val payload =
            try {
                objectMapper.readValue(sanitizedResponse, RouteSelectionPayload::class.java)
            } catch (ex: Exception) {
                return GeminiRouteDecision(
                    routeName = null,
                    reason = "Route selection was not valid JSON: ${ex.message}. Model response: $responseText",
                    arguments = null,
                )
            }
        val reason = payload.reason?.takeIf { it.isNotBlank() } ?: "Model did not provide a reason."
        return GeminiRouteDecision(payload.routeName?.trim(), reason, payload.arguments ?: emptyMap())
    }

    // Sends the merged prompt to Gemini and returns the readable text.
    fun generateAnswer(
        prompt: String,
        routeDescription: String,
        apiBody: String,
        starterUserId: UUID,
        chatHistory: List<McpConversationTurn> = emptyList(),
    ): String {
        if (properties.geminiApiKey.isBlank()) {
            return "Response could not be generated because MCP_GEMINI_API_KEY is blank."
        }

        val combinedPrompt = buildString {
            appendChatHistory(chatHistory)
            appendLine("User request: $prompt")
            appendLine("Starter user ID: $starterUserId")
            appendLine("Selected action: $routeDescription")
            appendLine("API response: $apiBody")
            appendLine("Summarize the data briefly. Do not propose additional action recommendations.")
            appendLine("Whenever a date appears in YYYY-MM-DD format, convert it to the written form (e.g., 20 May 2025).")
            append("Respond in plain text without Markdown formatting (no bullets, bold, or italics). New lines are allowed.")
        }
        val answerResult = callGemini(combinedPrompt)
        return answerResult.text ?: answerResult.error ?: "Gemini response could not be read."
    }

    private fun callGemini(prompt: String): GeminiCallResult {
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiTextPart(text = prompt))),
            ),
        )
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/${properties.geminiModel}:generateContent?key=${properties.geminiApiKey}"
        val response =
            try {
                restTemplate.postForObject(url, request, GeminiResponse::class.java)
            } catch (ex: RestClientException) {
                return GeminiCallResult(
                    text = null,
                    error = "Gemini request failed: ${ex.message}",
                )
            }
        val text = response?.firstText()
        return GeminiCallResult(text = text, error = null)
    }

    private fun sanitizeJsonCandidate(candidate: String): String {
        var text = candidate.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```")
            if (text.startsWith("json", ignoreCase = true)) {
                text = text.removePrefix("json")
            }
            text = text.trim()
            if (text.endsWith("```")) {
                text = text.removeSuffix("```")
            }
        }
        return text.trim()
    }

    private fun StringBuilder.appendChatHistory(chatHistory: List<McpConversationTurn>) {
        if (chatHistory.isEmpty()) {
            return
        }
        appendLine("Earlier conversation turns (oldest first):")
        chatHistory.forEach { turn ->
            appendLine("${roleLabel(turn.role)}: ${turn.content}")
        }
        appendLine("End of earlier conversation.")
    }

    private fun roleLabel(role: McpConversationRole): String =
        if (role == McpConversationRole.USER) {
            "user"
        } else {
            "assistant"
        }

    companion object {
        const val NO_MATCH_ROUTE = "__NO_MATCH__"
    }
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
    val arguments: Map<String, String>? = emptyMap(),
)

data class GeminiRequest(
    val contents: List<GeminiContent>,
)

data class GeminiContent(
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

// Helper to grab the very first candidate text.
private fun GeminiResponse.firstText(): String? =
    candidates
        ?.firstOrNull()
        ?.content
        ?.parts
        ?.firstOrNull()
        ?.text
