package com.runwithme.runwithme.api.mcp

import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.regex.Pattern

@Service
class McpAgentService(
    private val externalApiClient: McpExternalApiClient,
    private val geminiClient: GeminiClient,
    private val promptRouter: McpPromptRouter,
) {
    private val logger = LoggerFactory.getLogger(McpAgentService::class.java)

    // Simple orchestrator that combines routing, API call and Gemini answer.
    fun runAgent(request: McpAgentRequest, authorizationHeader: String?, starterUserId: UUID): McpAgentResponse {
        logger.info("MCP agent invoked with prompt='{}' starterUserId='{}'", request.prompt, starterUserId)
        val availableRoutes = promptRouter.routes()
        val decision = geminiClient.selectRoute(request.prompt, availableRoutes, starterUserId)
        val selectedRouteName = decision.routeName?.trim()
        if (selectedRouteName.isNullOrBlank()) {
            logger.warn("Route selection missing. reason='{}'", decision.reason)
            return McpAgentResponse(
                success = false,
                routeName = null,
                requestedUrl = null,
                apiBody = null,
                llmMessage = null,
                routeDecisionReason = decision.reason,
                resolvedArguments = decision.arguments,
                starterUserId = starterUserId,
                error = decision.reason ?: "Gemini herhangi bir rota secemedi.",
            )
        }
        val route =
            promptRouter.routeByName(selectedRouteName)
                ?: return McpAgentResponse(
                    success = false,
                    routeName = selectedRouteName,
                    requestedUrl = null,
                    apiBody = null,
                    llmMessage = null,
                    routeDecisionReason = decision.reason,
                    resolvedArguments = decision.arguments,
                    starterUserId = starterUserId,
                    error = "Secilen rota whitelist'te bulunamadigi icin politika reddetti.",
                )

        val resolvedPath =
            try {
                resolvePath(route, decision.arguments)
            } catch (ex: IllegalStateException) {
                logger.warn("Path resolution failed for route='{}': {}", route.name, ex.message)
                return McpAgentResponse(
                    success = false,
                    routeName = route.name,
                    requestedUrl = null,
                    apiBody = null,
                    llmMessage = null,
                    routeDecisionReason = decision.reason,
                    resolvedArguments = decision.arguments,
                    starterUserId = starterUserId,
                    error = ex.message,
                )
            }

        return try {
            logger.info("Executing MCP route='{}' resolvedPath='{}'", route.name, resolvedPath)
            val apiResult = externalApiClient.fetchData(route, resolvedPath, authorizationHeader)
            val llmText = geminiClient.generateAnswer(request.prompt, route.description, apiResult.body, starterUserId)
            McpAgentResponse(
                success = true,
                routeName = apiResult.routeName,
                requestedUrl = apiResult.url,
                apiBody = apiResult.body,
                llmMessage = llmText,
                routeDecisionReason = decision.reason,
                resolvedArguments = decision.arguments,
                starterUserId = starterUserId,
                error = null,
            )
        } catch (ex: IllegalStateException) {
            logger.warn("MCP execution failed for route='{}': {}", route.name, ex.message)
            McpAgentResponse(
                success = false,
                routeName = route.name,
                requestedUrl = null,
                apiBody = null,
                llmMessage = null,
                routeDecisionReason = decision.reason,
                resolvedArguments = decision.arguments,
                starterUserId = starterUserId,
                error = ex.message ?: "Ajan istegi basarisiz oldu.",
            )
        }
    }

    private fun resolvePath(route: McpRoute, arguments: Map<String, String>?): String {
        var path = route.pathTemplate
        logger.debug("Resolving path for route='{}' template='{}' arguments={}", route.name, route.pathTemplate, arguments)
        route.parameters.forEach { parameter ->
            val rawValue =
                arguments
                    ?.get(parameter.name)
                    ?.takeIf { it.isNotBlank() }
                    ?: if (parameter.required) {
                        throw IllegalStateException("`${route.name}` icin `${parameter.name}` parametresi gerekli.")
                    } else {
                        ""
                    }
            if (rawValue.isNotEmpty()) {
                val encodedValue = URLEncoder.encode(rawValue, StandardCharsets.UTF_8)
                path = path.replace("{${parameter.name}}", encodedValue)
            } else {
                path = path.replace("{${parameter.name}}", "")
            }
        }
        val matcher = PLACEHOLDER_PATTERN.matcher(path)
        if (matcher.find()) {
            throw IllegalStateException("`${route.name}` icin `${matcher.group(1)}` parametresi saglanmadigi icin rota olusturulamadi.")
        }
        logger.debug("Resolved path='{}' for route='{}'", path, route.name)
        return path
    }

    companion object {
        private val PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}")
    }
}

data class McpAgentRequest(
    @field:NotBlank(message = "Prompt bos olamaz")
    val prompt: String,
)

data class McpAgentResponse(
    val success: Boolean,
    val routeName: String?,
    val requestedUrl: String?,
    val apiBody: String?,
    val llmMessage: String?,
    val routeDecisionReason: String?,
    val resolvedArguments: Map<String, String>?,
    val starterUserId: UUID,
    val error: String?,
)
