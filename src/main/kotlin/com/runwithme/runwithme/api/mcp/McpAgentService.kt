package com.runwithme.runwithme.api.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.runwithme.runwithme.api.dto.CreateMessageRequest
import com.runwithme.runwithme.api.dto.UserStatisticsResponse
import com.runwithme.runwithme.api.entity.Message
import com.runwithme.runwithme.api.repository.FriendshipRepository
import com.runwithme.runwithme.api.repository.MessageRepository
import com.runwithme.runwithme.api.repository.RunSessionRepository
import com.runwithme.runwithme.api.service.MessageService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

@Service
class McpAgentService(
    private val externalApiClient: McpExternalApiClient,
    private val geminiClient: GeminiClient,
    private val promptRouter: McpPromptRouter,
    private val messageService: MessageService,
    private val messageRepository: MessageRepository,
    private val runSessionRepository: RunSessionRepository,
    private val friendshipRepository: FriendshipRepository,
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
        val routeSelectionHistory = priorHistory.takeLast(ROUTE_SELECTION_HISTORY_LIMIT)
        val decision =
            geminiClient.selectRoute(
                request.prompt,
                availableRoutes,
                starterUserId,
                starterUsername,
                routeSelectionHistory,
                requestId = requestId,
            )
        val decisionArguments = substituteArgumentPlaceholders(decision.arguments, starterUserId, starterUsername)
        val historyWithCurrentUser =
            if (priorHistory.lastOrNull()?.let { it.role == McpConversationRole.USER && it.content == request.prompt } == true) {
                priorHistory
            } else {
                priorHistory + McpConversationTurn(McpConversationRole.USER, request.prompt)
            }
        val answerHistory = historyWithCurrentUser.takeLast(5)
        val selectedRouteName = decision.routeName?.trim()
        if (selectedRouteName.isNullOrBlank() || selectedRouteName.equals(GeminiClient.NO_MATCH_ROUTE, ignoreCase = true)) {
            val noMatchContext =
                buildString {
                    appendLine("No API call was executed for this request.")
                    decision.reason?.takeIf { it.isNotBlank() }?.let { appendLine("Router note: $it") }
                }.trim()
            val llmText =
                geminiClient.generateAnswer(
                    prompt = request.prompt,
                    routeDescription = "No matching action",
                    apiBody = noMatchContext,
                    starterUserId = starterUserId,
                    starterUsername = starterUsername,
                    chatHistory = answerHistory,
                    routeArguments = decisionArguments,
                    requestId = requestId,
                )
            val userMessage =
                extractUserMessageFromEnvelope(llmText)
                    ?: defaultNoMatchMessage()
            return respondWithAgentMessage(
                McpAgentResponse(
                    success = true,
                    routeName = GeminiClient.NO_MATCH_ROUTE,
                    requestedUrl = null,
                    apiBody = null,
                    llmMessage = llmText,
                    userMessage = userMessage,
                    routeDecisionReason = decision.reason,
                    resolvedArguments = decisionArguments,
                    starterUserId = starterUserId,
                    error = null,
                ),
                starterUserId,
                agentIdentity,
                persistResponse = persistAgentReply,
            )
        }
        var route =
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
                        resolvedArguments = decisionArguments,
                        starterUserId = starterUserId,
                        error = "Policy rejected because the selected route was not on the allow-list.",
                    ),
                    starterUserId,
                    agentIdentity,
                    persistResponse = persistAgentReply,
                )

        var effectiveArguments = decisionArguments
        if (route.name.equals(RUNNING_STATS_BY_USERNAME_ROUTE_NAME, ignoreCase = true)) {
            val rawUsername = decisionArguments?.get("username")?.trim().orEmpty()
            val username = rawUsername.removePrefix("@").trim()
            if (username.isBlank()) {
                val errorMessage = "`${route.name}` requires parameter `username`."
                return respondWithAgentMessage(
                    McpAgentResponse(
                        success = false,
                        routeName = route.name,
                        requestedUrl = null,
                        apiBody = null,
                        llmMessage = null,
                        userMessage = errorMessage,
                        routeDecisionReason = decision.reason,
                        resolvedArguments = decisionArguments,
                        starterUserId = starterUserId,
                        error = errorMessage,
                    ),
                    starterUserId,
                    agentIdentity,
                    persistResponse = persistAgentReply,
                )
            }
            val userLookupRoute =
                promptRouter.routeByName(USER_BY_USERNAME_ROUTE_NAME)
                    ?: return respondWithAgentMessage(
                        McpAgentResponse(
                            success = false,
                            routeName = route.name,
                            requestedUrl = null,
                            apiBody = null,
                            llmMessage = null,
                            userMessage = "I couldn't look up that username right now.",
                            routeDecisionReason = decision.reason,
                            resolvedArguments = decisionArguments,
                            starterUserId = starterUserId,
                            error = "Required route `Get User By Username` is not available.",
                        ),
                        starterUserId,
                        agentIdentity,
                        persistResponse = persistAgentReply,
                    )
            val userLookupArguments = mapOf("username" to username)
            val userLookupResolvedRoute =
                try {
                    resolveRoute(userLookupRoute, userLookupArguments)
                } catch (ex: IllegalStateException) {
                    val errorMessage = ex.message ?: "I couldn't look up that username right now."
                    return respondWithAgentMessage(
                        McpAgentResponse(
                            success = false,
                            routeName = route.name,
                            requestedUrl = null,
                            apiBody = null,
                            llmMessage = null,
                            userMessage = errorMessage,
                            routeDecisionReason = decision.reason,
                            resolvedArguments = decisionArguments,
                            starterUserId = starterUserId,
                            error = errorMessage,
                        ),
                        starterUserId,
                        agentIdentity,
                        persistResponse = persistAgentReply,
                    )
                }
            val userLookupResult =
                try {
                    externalApiClient.fetchData(
                        route = userLookupRoute,
                        resolvedPath = userLookupResolvedRoute.path,
                        authorizationHeader = authorizationHeader,
                        requestBody = userLookupResolvedRoute.body,
                    )
                } catch (ex: ExternalApiCallException) {
                    val lookupMessage =
                        if (ex.statusCode == HttpStatus.NOT_FOUND.value()) {
                            resolveCustomErrorMessage(userLookupRoute, ex, userLookupArguments)
                                ?: "No user named '$username' was found."
                        } else {
                            "I couldn't look up that username right now."
                        }
                    return respondWithAgentMessage(
                        McpAgentResponse(
                            success = false,
                            routeName = route.name,
                            requestedUrl = ex.url,
                            apiBody = ex.responseBody,
                            llmMessage = null,
                            userMessage = lookupMessage,
                            routeDecisionReason = decision.reason,
                            resolvedArguments = decisionArguments,
                            starterUserId = starterUserId,
                            error = lookupMessage,
                        ),
                        starterUserId,
                        agentIdentity,
                        persistResponse = persistAgentReply,
                    )
                }
            val resolvedUserId = extractUserIdFromLookup(userLookupResult.body)
            if (resolvedUserId == null) {
                val errorMessage = "No user named '$username' was found."
                return respondWithAgentMessage(
                    McpAgentResponse(
                        success = false,
                        routeName = route.name,
                        requestedUrl = null,
                        apiBody = userLookupResult.body,
                        llmMessage = null,
                        userMessage = errorMessage,
                        routeDecisionReason = decision.reason,
                        resolvedArguments = decisionArguments,
                        starterUserId = starterUserId,
                        error = errorMessage,
                    ),
                    starterUserId,
                    agentIdentity,
                    persistResponse = persistAgentReply,
                )
            }
            if (resolvedUserId != starterUserId && !friendshipRepository.areFriends(starterUserId, resolvedUserId)) {
                val errorMessage = "I can only show running stats for you or your friends."
                return respondWithAgentMessage(
                    McpAgentResponse(
                        success = false,
                        routeName = route.name,
                        requestedUrl = null,
                        apiBody = null,
                        llmMessage = null,
                        userMessage = errorMessage,
                        routeDecisionReason = decision.reason,
                        resolvedArguments = decisionArguments,
                        starterUserId = starterUserId,
                        error = errorMessage,
                    ),
                    starterUserId,
                    agentIdentity,
                    persistResponse = persistAgentReply,
                )
            }
            val statsRoute =
                promptRouter.routeByName(RUNNING_STATS_ROUTE_NAME)
                    ?: return respondWithAgentMessage(
                        McpAgentResponse(
                            success = false,
                            routeName = route.name,
                            requestedUrl = null,
                            apiBody = null,
                            llmMessage = null,
                            userMessage = "I couldn't load running stats right now.",
                            routeDecisionReason = decision.reason,
                            resolvedArguments = decisionArguments,
                            starterUserId = starterUserId,
                            error = "Required route `Get User Running Statistics` is not available.",
                        ),
                        starterUserId,
                        agentIdentity,
                        persistResponse = persistAgentReply,
                    )
            val statsArguments = linkedMapOf("userId" to resolvedUserId.toString())
            decisionArguments?.get("days")?.takeIf { it.isNotBlank() }?.let { statsArguments["days"] = it }
            route = statsRoute
            effectiveArguments = statsArguments
        }

        val resolvedRoute =
            try {
                resolveRoute(route, effectiveArguments)
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
                        resolvedArguments = effectiveArguments,
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
                    starterUsername = starterUsername,
                    chatHistory = answerHistory,
                    routeArguments = effectiveArguments,
                    requestId = requestId,
                )
            val userMessage =
                extractUserMessageFromEnvelope(llmText)
                    ?: "I received your request but had trouble formatting the response. Please try again."
            respondWithAgentMessage(
                McpAgentResponse(
                    success = true,
                    routeName = apiResult.routeName,
                    requestedUrl = apiResult.url,
                    apiBody = apiResult.body,
                    llmMessage = llmText,
                    userMessage = userMessage,
                    routeDecisionReason = decision.reason,
                    resolvedArguments = effectiveArguments,
                    starterUserId = starterUserId,
                    error = null,
                ),
                starterUserId,
                agentIdentity,
                persistResponse = persistAgentReply,
            )
        } catch (ex: ExternalApiCallException) {
            val fallbackResponse =
                maybeRespondWithRunningStatsFallback(
                    route,
                    ex,
                    effectiveArguments,
                    request,
                    starterUserId,
                    starterUsername,
                    answerHistory,
                    decision.reason,
                    requestId,
                    agentIdentity,
                    persistAgentReply,
                )
            if (fallbackResponse != null) {
                return fallbackResponse
            }
            val customMessage = resolveCustomErrorMessage(route, ex, effectiveArguments)
            val fallbackNotFound =
                if (customMessage == null && ex.statusCode == HttpStatus.NOT_FOUND.value()) {
                    effectiveArguments?.get("username")?.takeIf { it.isNotBlank() }?.let {
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

            if (ex.statusCode == HttpStatus.NOT_FOUND.value()) {
                val summary = finalMessage ?: "Resource not found."
                val payload =
                    buildSchemaEnvelope(
                        type = "error",
                        summary = summary,
                        metadata =
                            mapOf(
                                "routeName" to route.name,
                                "url" to ex.url,
                                "statusCode" to ex.statusCode.toString(),
                            ),
                        errors =
                            listOf(
                                mapOf(
                                    "code" to "NOT_FOUND",
                                    "message" to summary,
                                    "details" to (ex.responseBody ?: ""),
                                ),
                            ),
                    )
                return respondWithAgentMessage(
                    McpAgentResponse(
                        success = false,
                        routeName = route.name,
                        requestedUrl = ex.url,
                        apiBody = ex.responseBody,
                        llmMessage = payload,
                        userMessage = summary,
                        routeDecisionReason = decision.reason,
                        resolvedArguments = effectiveArguments,
                        starterUserId = starterUserId,
                        error = summary,
                    ),
                    starterUserId,
                    agentIdentity,
                    persistResponse = persistAgentReply,
                )
            }
            respondWithAgentMessage(
                McpAgentResponse(
                    success = false,
                    routeName = route.name,
                    requestedUrl = ex.url,
                    apiBody = ex.responseBody,
                    llmMessage = finalMessage,
                    userMessage = finalMessage ?: errorMessage,
                    routeDecisionReason = decision.reason,
                    resolvedArguments = effectiveArguments,
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
                    resolvedArguments = effectiveArguments,
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

    private fun loadChatHistory(
        userId: UUID,
        agentIdentity: AgentIdentity,
    ): List<McpConversationTurn> =
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

    private fun recordChatMessage(
        senderUsername: String,
        recipientId: UUID,
        content: String,
    ) {
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

    private fun historyResetCutoff(
        userId: UUID,
        agentIdentity: AgentIdentity,
    ): OffsetDateTime? =
        try {
            messageRepository
                .findTopBySenderIdAndRecipientIdAndContentOrderByCreatedAtDesc(
                    agentIdentity.userId,
                    agentIdentity.userId,
                    historyResetMarker(userId),
                )?.createdAt
        } catch (ex: Exception) {
            null
        }

    private fun recordHistoryReset(
        userId: UUID,
        agentIdentity: AgentIdentity,
    ) {
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

    private fun extractUserIdFromLookup(apiBody: String?): UUID? {
        if (apiBody.isNullOrBlank()) {
            return null
        }
        return try {
            val userIdText = objectMapper.readTree(apiBody).path("userId").asText()
            if (userIdText.isBlank()) null else UUID.fromString(userIdText)
        } catch (ex: Exception) {
            null
        }
    }

    private fun maybeRespondWithRunningStatsFallback(
        route: McpRoute,
        exception: ExternalApiCallException,
        decisionArguments: Map<String, String>?,
        request: McpAgentRequest,
        starterUserId: UUID,
        starterUsername: String,
        answerHistory: List<McpConversationTurn>,
        decisionReason: String?,
        requestId: UUID,
        agentIdentity: AgentIdentity,
        persistAgentReply: Boolean,
    ): McpAgentResponse? {
        if (!shouldFallbackRunningStats(route, exception)) {
            return null
        }
        val userId = resolveStatsUserId(decisionArguments, starterUserId)
        val days = resolveStatsDays(decisionArguments)
        val fallbackBody =
            try {
                buildUserStatisticsFallback(userId, days)
            } catch (ex: Exception) {
                return null
            }
        val llmText =
            geminiClient.generateAnswer(
                prompt = request.prompt,
                routeDescription = route.description,
                apiBody = fallbackBody,
                starterUserId = starterUserId,
                starterUsername = starterUsername,
                chatHistory = answerHistory,
                routeArguments = decisionArguments,
                requestId = requestId,
            )
        val userMessage =
            extractUserMessageFromEnvelope(llmText)
                ?: "I received your request but had trouble formatting the response. Please try again."
        return respondWithAgentMessage(
            McpAgentResponse(
                success = true,
                routeName = route.name,
                requestedUrl = exception.url,
                apiBody = fallbackBody,
                llmMessage = llmText,
                userMessage = userMessage,
                routeDecisionReason = decisionReason,
                resolvedArguments = decisionArguments,
                starterUserId = starterUserId,
                error = null,
            ),
            starterUserId,
            agentIdentity,
            persistResponse = persistAgentReply,
        )
    }

    private fun shouldFallbackRunningStats(
        route: McpRoute,
        exception: ExternalApiCallException,
    ): Boolean {
        if (!route.name.equals(RUNNING_STATS_ROUTE_NAME, ignoreCase = true)) {
            return false
        }
        if (exception.statusCode != HttpStatus.BAD_REQUEST.value()) {
            return false
        }
        val responseBody = exception.responseBody ?: return false
        return responseBody.contains("For input string") && responseBody.contains("0,0")
    }

    private fun resolveStatsUserId(
        arguments: Map<String, String>?,
        fallbackUserId: UUID,
    ): UUID =
        arguments
            ?.get("userId")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: fallbackUserId

    private fun resolveStatsDays(arguments: Map<String, String>?): Int? =
        arguments
            ?.get("days")
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

    private fun buildUserStatisticsFallback(
        userId: UUID,
        days: Int?,
    ): String {
        val allRuns = runSessionRepository.findAllByUserId(userId)
        val allTimeDistanceKm = allRuns.sumOf { it.totalDistanceM ?: 0.0 } / 1000.0
        val allTimeTotalRuns = allRuns.size
        val firstRunDate = allRuns.mapNotNull { it.startedAt }.minOrNull()
        val periodRuns =
            if (days != null) {
                val since = OffsetDateTime.now().minusDays(days.toLong())
                runSessionRepository.findAllByUserIdAndStartedAtGreaterThanEqual(userId, since)
            } else {
                allRuns
            }
        val periodTotalRuns = periodRuns.size
        val periodTotalDistanceMeters = periodRuns.sumOf { it.totalDistanceM ?: 0.0 }
        val periodTotalDistanceKm = periodTotalDistanceMeters / 1000.0
        val periodTotalMovingTimeSeconds = periodRuns.sumOf { it.movingTimeS?.toLong() ?: 0L }
        val avgPaceSecPerKm =
            if (periodTotalDistanceKm > 0) {
                periodTotalMovingTimeSeconds.toDouble() / periodTotalDistanceKm
            } else {
                0.0
            }
        val avgPaceMin = (avgPaceSecPerKm / 60).toInt()
        val avgPaceSec = (avgPaceSecPerKm % 60).toInt()
        val averagePace = String.format(Locale.US, "%d:%02d /km", avgPaceMin, avgPaceSec)
        val averageDistancePerRunKm =
            if (periodTotalRuns > 0) {
                periodTotalDistanceKm / periodTotalRuns
            } else {
                0.0
            }
        val weeksActive =
            if (days != null) {
                if (days < 7) 1.0 else days / 7.0
            } else if (firstRunDate != null) {
                val daysSinceFirst = ChronoUnit.DAYS.between(firstRunDate, OffsetDateTime.now())
                if (daysSinceFirst < 7) 1.0 else daysSinceFirst / 7.0
            } else {
                1.0
            }
        val runsPerWeek = periodTotalRuns / weeksActive
        val kmPerWeek = periodTotalDistanceKm / weeksActive
        val response =
            UserStatisticsResponse(
                totalRuns = periodTotalRuns,
                totalDistanceKm = roundTo(periodTotalDistanceKm, 1),
                averagePace = averagePace,
                averageDistancePerRunKm = roundTo(averageDistancePerRunKm, 2),
                runsPerWeek = roundTo(runsPerWeek, 1),
                kmPerWeek = roundTo(kmPerWeek, 1),
                allTimeDistanceKm = roundTo(allTimeDistanceKm, 1),
                allTimeTotalRuns = allTimeTotalRuns,
                periodDays = days,
            )
        return objectMapper.writeValueAsString(response)
    }

    private fun roundTo(value: Double, scale: Int): Double =
        BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toDouble()

    companion object {
        private const val CHAT_HISTORY_LIMIT = 10
        private const val ROUTE_SELECTION_HISTORY_LIMIT = 5
        private const val HISTORY_RESET_MARKER_PREFIX = "__MCP_HISTORY_RESET__:"
        private const val RUNNING_STATS_ROUTE_NAME = "Get User Running Statistics"
        private const val RUNNING_STATS_BY_USERNAME_ROUTE_NAME = "Get User Running Statistics By Username"
        private const val USER_BY_USERNAME_ROUTE_NAME = "Get User By Username"
        private val PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}")
    }

    private fun defaultNoMatchMessage(): String = "I can't turn that into an action right now. I'm the RunWithMe assistant; I can help with certain in-app actions and information."

    private fun buildSchemaEnvelope(
        type: String,
        summary: String,
        metadata: Map<String, String> = emptyMap(),
        errors: List<Map<String, String>> = emptyList(),
    ): String =
        try {
            val metadataList =
                metadata.entries
                    .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                    .map { mapOf("key" to it.key, "value" to it.value) }
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to type,
                    "data" to
                        mapOf(
                            "summary" to summary,
                            "highlights" to emptyList<String>(),
                            "metadata" to metadataList,
                        ),
                    "errors" to errors,
                ),
            )
        } catch (ex: Exception) {
            """{"type":"error","data":{"summary":"$summary","highlights":[],"metadata":[]},"errors":[]}"""
        }

    private fun substituteArgumentPlaceholders(
        arguments: Map<String, String>?,
        starterUserId: UUID,
        starterUsername: String,
    ): Map<String, String>? {
        if (arguments.isNullOrEmpty()) {
            return arguments
        }
        return arguments.mapValues { (_, raw) ->
            val value = raw.trim()
            when {
                value.equals("starterUserId", ignoreCase = true) ||
                    value.equals("{starterUserId}", ignoreCase = true) ||
                    value.equals("\$starterUserId", ignoreCase = true) ->
                    starterUserId.toString()
                value.equals("starterUsername", ignoreCase = true) ||
                    value.equals("{starterUsername}", ignoreCase = true) ||
                    value.equals("\$starterUsername", ignoreCase = true) ->
                    starterUsername
                else -> value
            }
        }
    }
}
