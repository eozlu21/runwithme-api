package com.runwithme.runwithme.api.mcp

import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

// All MCP-callable routes are defined here.

@Component
class McpPromptRouter {
    private val availableRoutes =
        listOf(
            McpRoute(
                name = "User Statistics",
                description = "Returns the authenticated user's statistics.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/users",
            ),
            McpRoute(
                name = "Get User By Username",
                description = "Fetches a user's profile information by username.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/users/username/{username}",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "username",
                            description = "Username to search (e.g., minaaa).",
                            required = true,
                        ),
                    ),
                errorTemplates =
                    mapOf(
                        HttpStatus.NOT_FOUND.value() to "No user named '{username}' was found.",
                    ),
            ),
            McpRoute(
                name = "Send Friend Request",
                description = "Sends a friend request to the specified user.",
                method = HttpMethod.POST,
                pathTemplate = "api/v1/friends/requests",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "receiverId",
                            description = "UUID of the user who should receive the request.",
                            location = McpRouteParameterLocation.BODY,
                        ),
                        McpRouteParameter(
                            name = "message",
                            description = "Optional note to include with the request.",
                            required = false,
                            location = McpRouteParameterLocation.BODY,
                        ),
                    ),
                errorTemplates =
                    mapOf(
                        HttpStatus.NOT_FOUND.value() to "User '{receiverId}' could not be found.",
                        HttpStatus.CONFLICT.value() to "There is already a pending request with this user.",
                    ),
            ),
            McpRoute(
                name = "Received Friend Requests",
                description = "Lists pending friend requests received by the user.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/friends/requests/received",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "page",
                            description = "Zero-based page number for pagination.",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                        McpRouteParameter(
                            name = "size",
                            description = "Number of records per page.",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                    ),
            ),
            McpRoute(
                name = "Sent Friend Requests",
                description = "Lists pending friend requests created by the user.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/friends/requests/sent",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "page",
                            description = "Zero-based page number for pagination.",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                        McpRouteParameter(
                            name = "size",
                            description = "Number of records per page.",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                    ),
            ),
            McpRoute(
                name = "Friend Suggestions",
                description = "Lists suggested profiles using friends of friends.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/friends/suggestions",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "page",
                            description = "Zero-based page number for pagination.",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                        McpRouteParameter(
                            name = "size",
                            description = "Number of records per page.",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                    ),
            ),
            McpRoute(
                name = "Friend Stats",
                description = "Returns a summary of total friends and pending requests.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/friends/stats",
            ),
            McpRoute(
                name = "Specific User Friends",
                description = "Lists a specific user's friends if the viewer has permission.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/friends/user/{userId}",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "userId",
                            description = "UUID of the user whose friends will be fetched.",
                        ),
                        McpRouteParameter(
                            name = "page",
                            description = "Zero-based page number for pagination.",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                        McpRouteParameter(
                            name = "size",
                            description = "Number of records per page.",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                    ),
                errorTemplates =
                    mapOf(
                        HttpStatus.FORBIDDEN.value() to "You are not allowed to view this user's friends.",
                        HttpStatus.NOT_FOUND.value() to "User '{userId}' was not found.",
                    ),
            ),
            McpRoute(
                name = "My Survey Responses",
                description = "Lists survey responses previously submitted by the authenticated user.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/survey-responses/my",
            ),
        )

    fun routes(): List<McpRoute> = availableRoutes

    fun routeByName(name: String): McpRoute? = availableRoutes.firstOrNull { it.name.equals(name, ignoreCase = true) }
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
