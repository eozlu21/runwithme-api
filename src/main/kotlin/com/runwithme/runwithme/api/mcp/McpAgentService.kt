package com.runwithme.runwithme.api.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.runwithme.runwithme.api.dto.CreateMessageRequest
import com.runwithme.runwithme.api.entity.Message
import com.runwithme.runwithme.api.repository.MessageRepository
import com.runwithme.runwithme.api.service.MessageService
import jakarta.validation.constraints.NotBlank
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.util.UUID
import java.util.regex.Pattern

@Service
class McpAgentService(
    private val externalApiClient: McpExternalApiClient,
    private val geminiClient: GeminiClient,
    private val promptRouter: McpPromptRouter,
    private val messageService: MessageService,
    private val messageRepository: MessageRepository,
    private val properties: McpProperties,
) {
    private val objectMapper = jacksonObjectMapper()

    // Simple orchestrator that combines routing, API call and Gemini answer.
    fun runAgent(
        request: McpAgentRequest,
        authorizationHeader: String?,
        starterUserId: UUID,
        starterUsername: String,
        persistUserPrompt: Boolean = true,
        persistAgentReply: Boolean = true,
    ): McpAgentResponse {
        val requestId = UUID.randomUUID()
        val agentIdentity = resolveAgentIdentity()
        val priorHistory =
            if (request.resetHistory) {
                recordHistoryReset(starterUserId, agentIdentity)
                geminiClient.resetChatSessions(starterUserId)
                emptyList()
            } else {
                loadChatHistory(starterUserId, agentIdentity)
            }
        if (persistUserPrompt) {
            recordChatMessage(
                senderUsername = starterUsername,
                recipientId = agentIdentity.userId,
                content = request.prompt,
            )
        }
        val availableRoutes = promptRouter.routes()
        val decision =
            geminiClient.selectRoute(
                request.prompt,
                availableRoutes,
                starterUserId,
                priorHistory,
                requestId = requestId,
            )
        val historyWithCurrentUser =
            if (priorHistory.lastOrNull()?.let { it.role == McpConversationRole.USER && it.content == request.prompt } == true) {
                priorHistory
            } else {
                priorHistory + McpConversationTurn(McpConversationRole.USER, request.prompt)
            }
        val selectedRouteName = decision.routeName?.trim()
        if (selectedRouteName.isNullOrBlank()) {
            val errorMessage = decision.reason ?: "Gemini could not select any route."
            return respondWithAgentMessage(
                McpAgentResponse(
                    success = false,
                    routeName = null,
                    requestedUrl = null,
                    apiBody = null,
                    llmMessage = null,
                    userMessage = errorMessage,
                    routeDecisionReason = decision.reason,
                    resolvedArguments = decision.arguments,
                    starterUserId = starterUserId,
                    error = errorMessage,
                ),
                starterUserId,
                agentIdentity,
                persistResponse = persistAgentReply,
            )
        }
        if (selectedRouteName.equals(GeminiClient.NO_MATCH_ROUTE, ignoreCase = true)) {
            val reason = decision.reason ?: "I couldn't match that request to any action I can take."
            return respondWithAgentMessage(
                McpAgentResponse(
                    success = false,
                    routeName = null,
                    requestedUrl = null,
                    apiBody = null,
                    llmMessage = null,
                    userMessage = reason,
                    routeDecisionReason = decision.reason,
                    resolvedArguments = decision.arguments,
                    starterUserId = starterUserId,
                    error = reason,
                ),
                starterUserId,
                agentIdentity,
                persistResponse = false,
            )
        }
        val route =
            promptRouter.routeByName(selectedRouteName)
                ?: return respondWithAgentMessage(
                    McpAgentResponse(
                        success = false,
                        routeName = selectedRouteName,
                        requestedUrl = null,
                        apiBody = null,
                        llmMessage = null,
                        userMessage = "Policy rejected because the selected route was not on the allow-list.",
                        routeDecisionReason = decision.reason,
                        resolvedArguments = decision.arguments,
                        starterUserId = starterUserId,
                        error = "Policy rejected because the selected route was not on the allow-list.",
                    ),
                    starterUserId,
                    agentIdentity,
                    persistResponse = persistAgentReply,
                )

        val resolvedRoute =
            try {
                resolveRoute(route, decision.arguments)
            } catch (ex: IllegalStateException) {
                val errorMessage = ex.message ?: "`${route.name}` could not be resolved."
                return respondWithAgentMessage(
                    McpAgentResponse(
                        success = false,
                        routeName = route.name,
                        requestedUrl = null,
                        apiBody = null,
                        llmMessage = null,
                        userMessage = errorMessage,
                        routeDecisionReason = decision.reason,
                        resolvedArguments = decision.arguments,
                        starterUserId = starterUserId,
                        error = errorMessage,
                    ),
                    starterUserId,
                    agentIdentity,
                    persistResponse = persistAgentReply,
                )
            }

        return try {
            val apiResult =
                externalApiClient.fetchData(
                    route = route,
                    resolvedPath = resolvedRoute.path,
                    authorizationHeader = authorizationHeader,
                    requestBody = resolvedRoute.body,
                )
            val llmText =
                geminiClient.generateAnswer(
                    prompt = request.prompt,
                    routeDescription = route.description,
                    apiBody = apiResult.body,
                    starterUserId = starterUserId,
                    chatHistory = historyWithCurrentUser,
                    routeArguments = decision.arguments,
                    requestId = requestId,
                )
            val userMessage = extractUserMessageFromEnvelope(llmText)
            respondWithAgentMessage(
                McpAgentResponse(
                    success = true,
                    routeName = apiResult.routeName,
                    requestedUrl = apiResult.url,
                    apiBody = apiResult.body,
                    llmMessage = llmText,
                    userMessage = userMessage,
                    routeDecisionReason = decision.reason,
                    resolvedArguments = decision.arguments,
                    starterUserId = starterUserId,
                    error = null,
                ),
                starterUserId,
                agentIdentity,
                persistResponse = persistAgentReply,
            )
        } catch (ex: ExternalApiCallException) {
            val customMessage = resolveCustomErrorMessage(route, ex, decision.arguments)
            val fallbackNotFound =
                if (customMessage == null && ex.statusCode == HttpStatus.NOT_FOUND.value()) {
                    decision.arguments?.get("username")?.takeIf { it.isNotBlank() }?.let {
                        "No user named '$it' was found."
                    } ?: "No user was found."
                } else {
                    null
                }
            val finalMessage =
                customMessage ?: fallbackNotFound
            val errorMessage =
                finalMessage
                    ?: "`${route.name}` call failed: HTTP ${ex.statusCode ?: "?"}"
            respondWithAgentMessage(
                McpAgentResponse(
                    success = false,
                    routeName = route.name,
                    requestedUrl = ex.url,
                    apiBody = ex.responseBody,
                    llmMessage = finalMessage,
                    userMessage = finalMessage ?: errorMessage,
                    routeDecisionReason = decision.reason,
                    resolvedArguments = decision.arguments,
                    starterUserId = starterUserId,
                    error = errorMessage,
                ),
                starterUserId,
                agentIdentity,
                persistResponse = persistAgentReply,
            )
        } catch (ex: IllegalStateException) {
            respondWithAgentMessage(
                McpAgentResponse(
                    success = false,
                    routeName = route.name,
                    requestedUrl = null,
                    apiBody = null,
                    llmMessage = null,
                    userMessage = ex.message ?: "Agent request failed.",
                    routeDecisionReason = decision.reason,
                    resolvedArguments = decision.arguments,
                    starterUserId = starterUserId,
                    error = ex.message ?: "Agent request failed.",
                ),
                starterUserId,
                agentIdentity,
                persistResponse = persistAgentReply,
            )
        }
    }

    fun resetHistory(starterUserId: UUID) {
        val agentIdentity = resolveAgentIdentity()
        recordHistoryReset(starterUserId, agentIdentity)
        geminiClient.resetChatSessions(starterUserId)
    }

    private fun resolveRoute(
        route: McpRoute,
        arguments: Map<String, String>?,
    ): ResolvedRoute {
        var path = route.pathTemplate
        val queryParameters = mutableListOf<Pair<String, String>>()
        val bodyParameters = linkedMapOf<String, String>()
        route.parameters.forEach { parameter ->
            val rawValue =
                arguments
                    ?.get(parameter.name)
                    ?.takeIf { it.isNotBlank() }
                    ?: if (parameter.required) {
                        throw IllegalStateException("`${route.name}` requires parameter `${parameter.name}`.")
                    } else {
                        null
                    }
            when (parameter.location) {
                McpRouteParameterLocation.PATH -> {
                    val valueForPath = rawValue.orEmpty()
                    val encodedValue =
                        if (valueForPath.isNotEmpty()) URLEncoder.encode(valueForPath, StandardCharsets.UTF_8) else ""
                    path = path.replace("{${parameter.name}}", encodedValue)
                }
                McpRouteParameterLocation.QUERY -> {
                    if (rawValue != null) {
                        queryParameters += parameter.name to rawValue
                    }
                }
                McpRouteParameterLocation.BODY -> {
                    if (rawValue != null) {
                        bodyParameters[parameter.name] = rawValue
                    }
                }
            }
        }
        val matcher = PLACEHOLDER_PATTERN.matcher(path)
        if (matcher.find()) {
            throw IllegalStateException("`${route.name}` could not be resolved because `${matcher.group(1)}` was missing.")
        }
        if (queryParameters.isNotEmpty()) {
            val queryString =
                queryParameters.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}" }
            path =
                if (path.contains("?")) {
                    "$path&$queryString"
                } else {
                    "$path?$queryString"
                }
        }
        val body =
            if (bodyParameters.isEmpty()) {
                null
            } else {
                try {
                    objectMapper.writeValueAsString(bodyParameters)
                } catch (ex: Exception) {
                    throw IllegalStateException("Request body for `${route.name}` could not be created: ${ex.message}", ex)
                }
            }
        return ResolvedRoute(path = path, body = body)
    }

    private fun resolveCustomErrorMessage(
        route: McpRoute,
        exception: ExternalApiCallException,
        arguments: Map<String, String>?,
    ): String? {
        val statusCode = exception.statusCode ?: return null
        val template = route.errorTemplates[statusCode] ?: return null
        return renderTemplate(template, arguments)
    }

    private fun renderTemplate(
        template: String,
        arguments: Map<String, String>?,
    ): String {
        if (arguments.isNullOrEmpty()) {
            return template
        }
        var rendered = template
        arguments.forEach { (key, value) ->
            rendered = rendered.replace("{$key}", value)
        }
        return rendered
    }

    private fun resolveAgentIdentity(): AgentIdentity {
        val username = properties.agentUsername.trim()
        val userId = properties.agentUserId.trim()
        if (username.isBlank()) {
            throw IllegalStateException("MCP agent username is not configured.")
        }
        if (userId.isBlank()) {
            throw IllegalStateException("MCP agent user id is not configured.")
        }
        val parsedId =
            try {
                UUID.fromString(userId)
            } catch (ex: IllegalArgumentException) {
                throw IllegalStateException("MCP agent user id is invalid: ${ex.message}", ex)
            }
        return AgentIdentity(username = username, userId = parsedId)
    }

    private fun loadChatHistory(userId: UUID, agentIdentity: AgentIdentity): List<McpConversationTurn> =
        try {
            val cutoffTimestamp = historyResetCutoff(userId, agentIdentity)
            val pageRequest =
                PageRequest.of(
                    0,
                    CHAT_HISTORY_LIMIT,
                    Sort.by(Sort.Direction.DESC, "createdAt"),
                )
            messageRepository
                .findChatHistory(userId, agentIdentity.userId, pageRequest)
                .content
                .asReversed()
                .filter { cutoffTimestamp == null || it.createdAt.isAfter(cutoffTimestamp) }
                .map { message ->
                    val role =
                        if (message.senderId == userId) {
                            McpConversationRole.USER
                        } else {
                            McpConversationRole.AGENT
                        }
                    McpConversationTurn(role = role, content = message.content.orEmpty())
                }
        } catch (ex: Exception) {
            emptyList()
        }

    private fun recordChatMessage(senderUsername: String, recipientId: UUID, content: String) {
        if (content.isBlank()) {
            return
        }
        try {
            messageService.sendMessage(
                senderUsername,
                CreateMessageRequest(
                    recipientId = recipientId,
                    content = content,
                ),
            )
        } catch (ex: Exception) {
        }
    }

    private fun historyResetCutoff(userId: UUID, agentIdentity: AgentIdentity): OffsetDateTime? =
        try {
            messageRepository
                .findTopBySenderIdAndRecipientIdAndContentOrderByCreatedAtDesc(
                    agentIdentity.userId,
                    agentIdentity.userId,
                    historyResetMarker(userId),
                )
                ?.createdAt
        } catch (ex: Exception) {
            null
        }

    private fun recordHistoryReset(userId: UUID, agentIdentity: AgentIdentity) {
        val sentinel =
            Message(
                senderId = agentIdentity.userId,
                recipientId = agentIdentity.userId,
                content = historyResetMarker(userId),
                createdAt = OffsetDateTime.now(),
                isRead = true,
            )
        try {
            messageRepository.save(sentinel)
        } catch (ex: Exception) {
        }
    }

    private fun historyResetMarker(userId: UUID): String = "$HISTORY_RESET_MARKER_PREFIX$userId"

    private fun respondWithAgentMessage(
        response: McpAgentResponse,
        starterUserId: UUID,
        agentIdentity: AgentIdentity,
        persistResponse: Boolean = true,
    ): McpAgentResponse {
        if (persistResponse) {
            val reply = response.userMessage ?: response.error ?: response.llmMessage
            if (!reply.isNullOrBlank()) {
                recordChatMessage(agentIdentity.username, starterUserId, reply)
            }
        }
        return response
    }

    internal fun extractUserMessageFromEnvelope(llmMessage: String?): String? {
        if (llmMessage.isNullOrBlank()) {
            return null
        }
        return try {
            val summaryNode = objectMapper.readTree(llmMessage).path("data").path("summary")
            summaryNode.takeUnless { it.isMissingNode || it.isNull }?.asText()?.takeIf { it.isNotBlank() }
        } catch (ex: Exception) {
            null
        }
    }

    companion object {
        private const val CHAT_HISTORY_LIMIT = 10
        private const val HISTORY_RESET_MARKER_PREFIX = "__MCP_HISTORY_RESET__:"
        private val PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}")
    }
}

private data class ResolvedRoute(
    val path: String,
    val body: String?,
)

private data class AgentIdentity(
    val username: String,
    val userId: UUID,
)

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
