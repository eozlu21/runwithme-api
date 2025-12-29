package com.runwithme.runwithme.api.mcp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import com.runwithme.runwithme.api.mcp.prompts.McpSchemaPrompts
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
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
        val selectionPrompt = buildString {
            appendLine("Starter user ID: $starterUserId")
            appendLine("User prompt: $prompt")
        }
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
                systemSupplement = buildRouteSystemSupplement(functionListJson),
            )
        if (selectionResult.error != null) {
            return GeminiRouteDecision(routeName = null, reason = selectionResult.error, arguments = null)
        }
        val responseText =
            selectionResult.text
                ?: run {
                    mcpLogger.logGeminiError(
                        GeminiLogStage.ROUTE_SELECTION,
                        "Gemini response had no text candidate.",
                        selectionMetadata,
                    )
                    return GeminiRouteDecision(routeName = null, reason = "Gemini response could not be read.", arguments = null)
                }
        val sanitizedResponse = sanitizeJsonCandidate(responseText)
        val payload =
            try {
                objectMapper.readValue(sanitizedResponse, RouteSelectionPayload::class.java)
            } catch (ex: Exception) {
                val errorMetadata = selectionMetadata + mapOf("rawResponse" to sanitizedResponse)
                mcpLogger.logGeminiError(
                    GeminiLogStage.ROUTE_SELECTION,
                    "Route selection JSON parse failed: ${ex.message}",
                    errorMetadata,
                    ex,
                )
                return GeminiRouteDecision(
                    routeName = null,
                    reason = "Route selection was not valid JSON: ${ex.message}. Model response: $responseText",
                    arguments = null,
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

        val combinedPrompt = buildString {
            appendLine("User request: $prompt")
            appendLine("Starter user ID: $starterUserId")
            appendLine("Selected action: $routeDescription")
            appendLine("API response: $apiBody")
        }
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
                    appendLine(session.systemInstruction)
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
            val result = executeStreamRequest(request, stage, metadata)
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
            key = key,
            config = config,
            systemInstruction = config.systemInstruction,
            history = seededHistory,
        )
    }

    private fun routeSelectionChatConfig(): GeminiChatConfig =
        GeminiChatConfig(
            systemInstruction =
                buildString {
                    appendLine("You are the RunWithMe MCP policy router.")
                    appendLine("Review the user request and choose exactly one allowed function from the JSON allow-list.")
                    appendLine("If no route applies, respond with routeName \"$NO_MATCH_ROUTE\" and explain why.")
                    appendLine("Do not emit Markdown or text outside the JSON response.")
                },
            responseMimeType = "application/json",
            temperature = 0.2,
            maxOutputTokens = 512,
            responseSchema = ROUTE_SELECTION_SCHEMA,
        )

    private fun answerChatConfig(): GeminiChatConfig =
        GeminiChatConfig(
            systemInstruction =
                buildString {
                    appendLine("You summarize API responses for the RunWithMe MCP agent.")
                    appendLine("Return concise JSON summaries that match the configured schema.")
                    appendLine("Do not suggest additional actions or follow-ups.")
                    appendLine("Whenever a date appears in YYYY-MM-DD format, convert it to a written form such as 20 May 2025.")
                },
            responseMimeType = "application/json",
            temperature = 0.2,
            maxOutputTokens = 512,
            responseSchema = McpSchemaPrompts.answerSchemaMap(),
        )

    private fun executeStreamRequest(
        request: GeminiRequest,
        stage: GeminiLogStage,
        metadata: Map<String, Any?>,
    ): GeminiCallResult {
        val payload = objectMapper.writeValueAsString(request)
        mcpLogger.logGeminiRequest(stage, payload, metadata)
        val httpRequest =
            HttpRequest
                .newBuilder()
                .uri(streamEndpointUri())
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
        val httpResponse =
            try {
                httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
            } catch (ex: Exception) {
                mcpLogger.logGeminiError(stage, "Gemini stream failed: ${ex.message}", metadata, ex)
                return GeminiCallResult(text = null, error = "Gemini request failed: ${ex.message}")
            }
        if (httpResponse.statusCode() >= 400) {
            val errorBody = httpResponse.body().use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
            val errorMessage = "Gemini request failed: HTTP ${httpResponse.statusCode()} - $errorBody"
            mcpLogger.logGeminiError(stage, errorMessage, metadata)
            return GeminiCallResult(text = null, error = errorMessage)
        }
        httpResponse.body().use { inputStream ->
            val aggregated = readStreamingResponse(stage, metadata, inputStream)
            val trimmed = aggregated?.trim()
            return if (!trimmed.isNullOrEmpty()) {
                GeminiCallResult(text = trimmed, error = null)
            } else {
                mcpLogger.logGeminiError(stage, "Gemini response could not be read from stream.", metadata)
                GeminiCallResult(text = null, error = "Gemini response could not be read.")
            }
        }
    }

    private fun readStreamingResponse(
        stage: GeminiLogStage,
        metadata: Map<String, Any?>,
        inputStream: InputStream,
    ): String? {
        val aggregated = StringBuilder()
        val rawBuffer = StringBuilder()
        var chunkIndex = 0
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val eventBuilder = StringBuilder()

        fun flushEvent(force: Boolean = false) {
            if (eventBuilder.isEmpty()) {
                return
            }
            val payload = eventBuilder.toString()
            val nextIndex = chunkIndex + 1
            val chunkMetadata = metadata + ("chunkIndex" to nextIndex)
            val chunkText = parseChunk(payload, stage, chunkMetadata)
            if (!chunkText.isNullOrEmpty()) {
                chunkIndex = nextIndex
                aggregated.append(chunkText)
                mcpLogger.logGeminiResponse(stage, chunkText, chunkMetadata)
                eventBuilder.setLength(0)
            } else if (force) {
                eventBuilder.setLength(0)
            }
        }

        reader.use { buffered ->
            while (true) {
                val rawLine = buffered.readLine() ?: break
                rawBuffer.append(rawLine).append('\n')
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) {
                    flushEvent()
                    continue
                }
                var cleanedLine =
                    trimmed
                        .removePrefix("data:")
                        .trim()
                if (cleanedLine == "[DONE]") {
                    flushEvent(force = true)
                    break
                }
                if (cleanedLine.isNotEmpty()) {
                    eventBuilder.append(cleanedLine)
                }
            }
        }
        flushEvent(force = true)

        if (chunkIndex == 0) {
            val fallback = rawBuffer.toString().trim()
            if (fallback.isNotEmpty()) {
                val chunkText = parseChunk(fallback, stage, metadata + ("chunkIndex" to 1))
                if (!chunkText.isNullOrEmpty()) {
                    mcpLogger.logGeminiResponse(stage, chunkText, metadata + ("chunkIndex" to 1))
                    aggregated.append(chunkText)
                }
            }
        }

        return aggregated.toString()
    }

    private fun parseChunk(
        payload: String,
        stage: GeminiLogStage,
        metadata: Map<String, Any?>,
    ): String? {
        return try {
            val chunk = objectMapper.readValue(payload, GeminiResponse::class.java)
            chunk.firstText()
        } catch (ex: Exception) {
            try {
                val list = objectMapper.readValue(payload, Array<GeminiResponse>::class.java)
                val fallback = list.firstOrNull()?.firstText()
                if (fallback != null) {
                    return fallback
                }
            } catch (_: Exception) {
            }
            val preview = if (payload.length > 500) payload.substring(0, 500) + "..." else payload
            mcpLogger.logGeminiError(
                stage,
                "Gemini chunk parse failed: ${ex.message}",
                metadata + mapOf("chunkPreview" to preview),
                ex,
            )
            null
        }
    }

    private fun streamEndpointUri(): URI =
        URI.create("https://generativelanguage.googleapis.com/v1beta/models/${properties.geminiModel}:streamGenerateContent?key=${properties.geminiApiKey}")

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
    val key: GeminiChatKey,
    val config: GeminiChatConfig,
    val systemInstruction: String,
    val history: MutableList<GeminiContent>,
)

data class GeminiChatKey(
    val starterUserId: UUID,
    val stage: GeminiLogStage,
)

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

private fun buildRouteSystemSupplement(routesJson: String): String =
    buildString {
        appendLine("Allowed routes (JSON array):")
        append(routesJson)
    }.trim()

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

// Helper to grab the very first candidate text.
private fun GeminiResponse.firstText(): String? =
    candidates
        ?.firstOrNull()
        ?.content
        ?.parts
        ?.firstOrNull()
        ?.text
