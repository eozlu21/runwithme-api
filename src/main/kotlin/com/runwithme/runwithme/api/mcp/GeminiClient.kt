package com.runwithme.runwithme.api.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.runwithme.runwithme.api.mcp.prompts.GeminiPrompts
import com.runwithme.runwithme.api.mcp.prompts.McpSchemaPrompts
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class GeminiClient(
    private val properties: McpProperties,
    private val mcpLogger: McpLogger,
) {
    private val objectMapper = jacksonObjectMapper()
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    private val chatSessions = ConcurrentHashMap<GeminiChatKey, GeminiChatSession>()

    // Asks Gemini to choose one of the allowed routes (function calling style).
    fun selectRoute(
        prompt: String,
        routes: List<McpRoute>,
        starterUserId: UUID,
        chatHistory: List<McpConversationTurn> = emptyList(),
        requestId: UUID? = null,
    ): GeminiRouteDecision {
        if (routes.isEmpty()) {
            return GeminiRouteDecision(routeName = null, reason = "No route could be selected because the allow-list is empty.", arguments = null)
        }
        if (properties.geminiApiKey.isBlank()) {
            return GeminiRouteDecision(routeName = null, reason = "No route could be selected because MCP_GEMINI_API_KEY is blank.", arguments = null)
        }

        val functionListJson = objectMapper.writeValueAsString(routes.toGeminiRoutePayload())
        val selectionPrompt = GeminiPrompts.routeSelectionUserPrompt(starterUserId, prompt)
        val selectionMetadata =
            mutableMapOf<String, Any?>(
                "starterUserId" to starterUserId,
                "routeNames" to routes.joinToString(",") { it.name },
                "historyCount" to chatHistory.size,
                ).apply {
                    requestId?.let { put("requestId", it.toString()) }
                }
        val selectionResult =
            sendChatMessage(
                key = GeminiChatKey(starterUserId, GeminiLogStage.ROUTE_SELECTION),
                stage = GeminiLogStage.ROUTE_SELECTION,
                userMessage = selectionPrompt,
                metadata = selectionMetadata,
                chatHistory = chatHistory,
                systemSupplement = GeminiPrompts.routeSelectionSupplement(functionListJson),
            )
        if (selectionResult.error != null) {
            return GeminiRouteDecision(routeName = NO_MATCH_ROUTE, reason = selectionResult.error, arguments = emptyMap())
        }
        val responseText =
            selectionResult.text
                ?: run {
                    mcpLogger.logGeminiError(
                        GeminiLogStage.ROUTE_SELECTION,
                        "Gemini response had no text candidate.",
                        selectionMetadata,
                    )
                    return GeminiRouteDecision(routeName = NO_MATCH_ROUTE, reason = "Gemini response could not be read.", arguments = emptyMap())
                }
        val sanitizedResponse = sanitizeJsonCandidate(responseText)
        val payload =
            try {
                objectMapper.readValue(sanitizedResponse, RouteSelectionPayload::class.java)
            } catch (ex: Exception) {
                val extractedReason = extractJsonStringValue(sanitizedResponse, "reason")?.takeIf { it.isNotBlank() }
                val errorMetadata =
                    selectionMetadata +
                        mapOf(
                            "rawResponseLength" to sanitizedResponse.length,
                            "rawResponsePreview" to sanitizedResponse.take(300),
                        )
                mcpLogger.logGeminiError(
                    GeminiLogStage.ROUTE_SELECTION,
                    "Route selection JSON parse failed: ${ex.message}",
                    errorMetadata,
                    ex,
                )
                return GeminiRouteDecision(
                    routeName = NO_MATCH_ROUTE,
                    reason = extractedReason ?: "I couldn't decide an action for that request.",
                    arguments = emptyMap(),
                )
            }
        val reason = payload.reason?.takeIf { it.isNotBlank() } ?: "Model did not provide a reason."
        val resolvedArguments =
            payload.arguments
                ?.associate { it.key.trim() to it.value.trim() }
                ?.filterKeys { it.isNotEmpty() }
                ?: emptyMap()
        return GeminiRouteDecision(payload.routeName?.trim(), reason, resolvedArguments)
    }

    // Sends the merged prompt to Gemini and returns the readable text.
    fun generateAnswer(
        prompt: String,
        routeDescription: String,
        apiBody: String,
        starterUserId: UUID,
        chatHistory: List<McpConversationTurn> = emptyList(),
        routeArguments: Map<String, String>? = null,
        requestId: UUID? = null,
    ): String {
        if (properties.geminiApiKey.isBlank()) {
            return "Response could not be generated because MCP_GEMINI_API_KEY is blank."
        }

        val combinedPrompt =
            GeminiPrompts.answerUserPrompt(
                prompt = prompt,
                starterUserId = starterUserId,
                routeDescription = routeDescription,
                apiBody = apiBody,
            )
        val answerMetadata =
            mutableMapOf<String, Any?>(
                "starterUserId" to starterUserId,
                "routeDescription" to routeDescription,
                "apiBodyLength" to apiBody.length,
                "historyCount" to chatHistory.size,
            ).apply {
                if (!routeArguments.isNullOrEmpty()) {
                    put("routeArguments", routeArguments)
                }
                requestId?.let { put("requestId", it.toString()) }
            }
        val answerResult =
            sendChatMessage(
                key = GeminiChatKey(starterUserId, GeminiLogStage.ANSWER_GENERATION),
                stage = GeminiLogStage.ANSWER_GENERATION,
                userMessage = combinedPrompt,
                metadata = answerMetadata,
                chatHistory = chatHistory,
            )
        if (answerResult.text.isNullOrBlank()) {
            mcpLogger.logGeminiError(
                GeminiLogStage.ANSWER_GENERATION,
                answerResult.error ?: "Gemini response could not be read.",
                answerMetadata,
            )
        }
        return answerResult.text ?: answerResult.error ?: "Gemini response could not be read."
    }

    private fun sendChatMessage(
        key: GeminiChatKey,
        stage: GeminiLogStage,
        userMessage: String,
        metadata: Map<String, Any?>,
        chatHistory: List<McpConversationTurn>,
        systemSupplement: String? = null,
    ): GeminiCallResult {
        val session = chatSessions.computeIfAbsent(key) { createChatSession(it, chatHistory) }
        return synchronized(session) {
            val userContent = GeminiContent(role = "user", parts = listOf(GeminiTextPart(text = userMessage)))
            session.history.add(userContent)
            val systemText =
                buildString {
                    appendLine(session.config.systemInstruction)
                    if (!systemSupplement.isNullOrBlank()) {
                        appendLine(systemSupplement)
                    }
                }.trim()
            val request =
                GeminiRequest(
                    contents = session.history,
                    systemInstruction = GeminiContent(role = "system", parts = listOf(GeminiTextPart(systemText))),
                    generationConfig = session.config.toGenerationConfig(),
                )
            val result = executeNonStreamRequest(request, stage, metadata)
            if (result.text != null) {
                session.history.add(GeminiContent(role = "model", parts = listOf(GeminiTextPart(result.text))))
            } else {
                session.history.removeLastOrNull()
            }
            result
        }
    }

    private fun createChatSession(key: GeminiChatKey, chatHistory: List<McpConversationTurn>): GeminiChatSession {
        val config =
            when (key.stage) {
                GeminiLogStage.ROUTE_SELECTION -> routeSelectionChatConfig()
                GeminiLogStage.ANSWER_GENERATION -> answerChatConfig()
            }
        val seededHistory = chatHistoryToGeminiHistory(chatHistory)
        return GeminiChatSession(
            config = config,
            history = seededHistory,
        )
    }

    private fun routeSelectionChatConfig(): GeminiChatConfig =
        GeminiChatConfig(
            systemInstruction = GeminiPrompts.routeSelectionSystemInstruction(NO_MATCH_ROUTE),
            responseMimeType = "application/json",
            temperature = 0.2,
            maxOutputTokens = 1000,
            responseSchema = ROUTE_SELECTION_SCHEMA,
        )

    private fun answerChatConfig(): GeminiChatConfig =
        GeminiChatConfig(
            systemInstruction = GeminiPrompts.answerSystemInstruction(),
            responseMimeType = "application/json",
            temperature = 0.2,
            maxOutputTokens = 512,
            responseSchema = McpSchemaPrompts.answerSchemaMap(),
        )

    private fun executeNonStreamRequest(
        request: GeminiRequest,
        stage: GeminiLogStage,
        metadata: Map<String, Any?>,
    ): GeminiCallResult {
        val payload = objectMapper.writeValueAsString(request)
        mcpLogger.logGeminiRequest(stage, payload, metadata)
        val httpRequest =
            HttpRequest
                .newBuilder()
                .uri(nonStreamEndpointUri())
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
        val httpResponse =
            try {
                httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            } catch (ex: Exception) {
                mcpLogger.logGeminiError(stage, "Gemini request failed: ${ex.message}", metadata, ex)
                return GeminiCallResult(text = null, error = "Gemini request failed: ${ex.message}")
            }
        if (httpResponse.statusCode() >= 400) {
            val errorMessage = "Gemini request failed: HTTP ${httpResponse.statusCode()} - ${httpResponse.body()}"
            mcpLogger.logGeminiError(stage, errorMessage, metadata)
            return GeminiCallResult(text = null, error = errorMessage)
        }
        val responseBody = httpResponse.body()
        val response =
            try {
                objectMapper.readValue(responseBody, GeminiResponse::class.java)
            } catch (ex: Exception) {
                mcpLogger.logGeminiError(
                    stage,
                    "Gemini response was not valid JSON: ${ex.message}",
                    metadata +
                        mapOf(
                            "rawResponseLength" to responseBody.length,
                            "rawResponsePreview" to responseBody.take(300),
                        ),
                    ex,
                )
                return GeminiCallResult(text = null, error = "Gemini response could not be read.")
            }
        val text = response.allText()
        return if (!text.isNullOrBlank()) {
            val extra =
                if (stage == GeminiLogStage.ANSWER_GENERATION) {
                    val summary =
                        try {
                            objectMapper.readTree(text).path("data").path("summary").asText(null)
                        } catch (_: Exception) {
                            null
                        }
                    summary?.takeIf { it.isNotBlank() }?.let { mapOf("summaryPreview" to it.take(160)) } ?: emptyMap()
                } else {
                    emptyMap()
                }
            mcpLogger.logGeminiResponse(stage, text, metadata + extra)
            GeminiCallResult(text = text, error = null)
        } else {
            val errorMessage = "Gemini response could not be read."
            mcpLogger.logGeminiError(
                stage,
                errorMessage,
                metadata +
                    mapOf(
                        "rawResponseLength" to responseBody.length,
                        "rawResponsePreview" to responseBody.take(300),
                    ),
            )
            GeminiCallResult(text = null, error = errorMessage)
        }
    }

    private fun nonStreamEndpointUri(): URI =
        URI.create("https://generativelanguage.googleapis.com/v1beta/models/${properties.geminiModel}:generateContent?key=${properties.geminiApiKey}")

    fun resetChatSessions(starterUserId: UUID) {
        val removed =
            chatSessions.keys.removeIf { key ->
                key.starterUserId == starterUserId
            }
        if (removed) {
            mcpLogger.logEvent(
                event = "GeminiChatSessionsCleared",
                metadata = mapOf("starterUserId" to starterUserId),
            )
        }
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

    private fun extractJsonStringValue(jsonish: String, fieldName: String): String? {
        val needle = "\"$fieldName\""
        val start = jsonish.indexOf(needle)
        if (start < 0) return null
        val colon = jsonish.indexOf(':', start + needle.length)
        if (colon < 0) return null
        val firstQuote = jsonish.indexOf('"', colon + 1)
        if (firstQuote < 0) return null
        val builder = StringBuilder()
        var escaped = false
        var index = firstQuote + 1
        while (index < jsonish.length) {
            val ch = jsonish[index]
            if (escaped) {
                builder.append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                return builder.toString()
            } else {
                builder.append(ch)
            }
            index++
        }
        return builder.toString()
    }

    private fun chatHistoryToGeminiHistory(chatHistory: List<McpConversationTurn>): MutableList<GeminiContent> =
        chatHistory
            .mapNotNull { turn ->
                val value = turn.content.trim()
                if (value.isEmpty()) {
                    null
                } else {
                    GeminiContent(role = geminiRole(turn.role), parts = listOf(GeminiTextPart(value)))
                }
            }.toMutableList()

    private fun geminiRole(role: McpConversationRole): String =
        if (role == McpConversationRole.USER) {
            "user"
        } else {
            "model"
        }

    companion object {
        const val NO_MATCH_ROUTE = "__NO_MATCH__"
    }
}

private val ROUTE_SELECTION_SCHEMA: Map<String, Any> =
    mapOf(
        "type" to "object",
        "properties" to
            mapOf(
                "routeName" to mapOf("type" to "string"),
                "reason" to mapOf("type" to "string"),
                "arguments" to
                    mapOf(
                        "type" to "array",
                        "items" to
                            mapOf(
                                "type" to "object",
                                "properties" to
                                    mapOf(
                                        "key" to mapOf("type" to "string"),
                                        "value" to mapOf("type" to "string"),
                                    ),
                                "required" to listOf("key", "value"),
                            ),
                    ),
            ),
        "required" to listOf("routeName", "reason", "arguments"),
    )

private fun GeminiChatConfig.toGenerationConfig(): GeminiGenerationConfig =
    GeminiGenerationConfig(
        temperature = temperature,
        maxOutputTokens = maxOutputTokens,
        responseMimeType = responseMimeType,
        responseSchema = responseSchema,
    )

private fun List<McpRoute>.toGeminiRoutePayload(): List<GeminiRouteDescriptionPayload> =
    map { route ->
        GeminiRouteDescriptionPayload(
            name = route.name,
            description = route.description,
            method = route.method.name(),
            pathTemplate = route.pathTemplate,
            parameters =
                route.parameters.map { param ->
                    GeminiRouteParameterPayload(
                        name = param.name,
                        description = param.description,
                        required = param.required,
                        location = param.location.name.lowercase(),
                    )
                },
        )
    }

private fun MutableList<GeminiContent>.removeLastOrNull(): GeminiContent? =
    if (isEmpty()) {
        null
    } else {
        removeAt(size - 1)
    }

private fun GeminiResponse.allText(): String? =
    candidates
        ?.firstOrNull()
        ?.content
        ?.parts
        ?.joinToString("") { it.text.orEmpty() }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
