package com.runwithme.runwithme.api.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
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
    private val objectMapper = jacksonObjectMapper()

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
                error = decision.reason ?: "Gemini could not select any route.",
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
                    error = "Policy rejected because the selected route was not on the allow-list.",
                )

        val resolvedRoute =
            try {
                resolveRoute(route, decision.arguments)
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
            logger.info("Executing MCP route='{}' resolvedPath='{}'", route.name, resolvedRoute.path)
            val apiResult =
                externalApiClient.fetchData(
                    route = route,
                    resolvedPath = resolvedRoute.path,
                    authorizationHeader = authorizationHeader,
                    requestBody = resolvedRoute.body,
                )
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
        } catch (ex: ExternalApiCallException) {
            logger.warn(
                "External API returned status={} for route='{}' url='{}'",
                ex.statusCode,
                route.name,
                ex.url,
            )
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
            McpAgentResponse(
                success = false,
                routeName = route.name,
                requestedUrl = ex.url,
                apiBody = ex.responseBody,
                llmMessage = finalMessage,
                routeDecisionReason = decision.reason,
                resolvedArguments = decision.arguments,
                starterUserId = starterUserId,
                error = errorMessage,
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
                error = ex.message ?: "Agent request failed.",
            )
        }
    }

    private fun resolveRoute(route: McpRoute, arguments: Map<String, String>?): ResolvedRoute {
        var path = route.pathTemplate
        val queryParameters = mutableListOf<Pair<String, String>>()
        val bodyParameters = linkedMapOf<String, String>()
        logger.debug("Resolving path for route='{}' template='{}' arguments={}", route.name, route.pathTemplate, arguments)
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
        logger.debug("Resolved path='{}' bodyPresent={} for route='{}'", path, body != null, route.name)
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

    private fun renderTemplate(template: String, arguments: Map<String, String>?): String {
        if (arguments.isNullOrEmpty()) {
            return template
        }
        var rendered = template
        arguments.forEach { (key, value) ->
            rendered = rendered.replace("{$key}", value)
        }
        return rendered
    }

    companion object {
        private val PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}")
    }
}

private data class ResolvedRoute(
    val path: String,
    val body: String?,
)

data class McpAgentRequest(
    @field:NotBlank(message = "Prompt must not be blank")
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
