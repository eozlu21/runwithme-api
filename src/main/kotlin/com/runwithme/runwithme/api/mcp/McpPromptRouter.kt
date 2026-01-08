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
                name = "Get User Profile",
                description =
                    "Fetch a user's profile details by user id (e.g., firstName, lastName, pronouns, birthday, expertLevel, profileVisibility). If the viewer cannot see the full profile, the API may return a limited profile view instead. Use for: 'Who am I?', 'my profile', 'my pronouns', 'my birthday', 'my expertise level'.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/user-profiles/{id}",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "id",
                            description =
                                "UUID of the user whose profile will be fetched (usually the authenticated user).",
                            required = true,
                        ),
                    ),
                errorTemplates =
                    mapOf(
                        HttpStatus.NOT_FOUND.value() to
                            "User profile not found",
                    ),
            ),
            McpRoute(
                name = "Get User By Username",
                description =
                    "Find a user by username handle (e.g., 'minaaa' without '@') and return the matching user's basic account info (UserDto: userId, username, email, createdAt, emailVerified). Use for: 'Is there a user @minaaa?', 'the username is minaaa'. Not for searching by real name (e.g., 'someone named Mina').",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/users/username/{username}",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "username",
                            description =
                                "Username handle to search, without '@' (e.g., minaaa).",
                            required = true,
                        ),
                    ),
                errorTemplates =
                    mapOf(
                        HttpStatus.NOT_FOUND.value() to
                            "No user named '{username}' was found.",
                    ),
            ),
            McpRoute(
                name = "Get User By Id",
                description =
                    "Fetch a user's account info by user id and return the UserDto fields including username. Use for: 'what is my username', 'my account info'.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/users/{id}",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "id",
                            description =
                                "UUID of the user whose account info will be fetched (usually the authenticated user).",
                            required = true,
                        ),
                    ),
                errorTemplates =
                    mapOf(
                        HttpStatus.NOT_FOUND.value() to
                            "User '{id}' was not found.",
                    ),
            ),
            McpRoute(
                name = "Get User Running Statistics",
                description =
                    "Return a user's running statistics (total runs, total distance, average pace, runs per week, km per week, all-time totals). Use for: 'my stats', 'running stats', 'my running statistics', 'total distance', 'average pace', 'runs per week', 'how many runs'.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/users/{userId}/statistics",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "userId",
                            description =
                                "UUID of the user whose running stats will be fetched (usually the authenticated user).",
                            required = true,
                        ),
                        McpRouteParameter(
                            name = "days",
                            description =
                                "Optional lookback window in days (e.g., 7 or 30) for period stats. Omit for all-time stats.",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                    ),
                errorTemplates =
                    mapOf(
                        HttpStatus.NOT_FOUND.value() to
                            "User '{userId}' was not found.",
                    ),
            ),
            McpRoute(
                name = "Send Friend Request",
                description =
                    "Send/create a friend request from a specified sender username to a specified receiver username. Use for MCP testing only.",
                method = HttpMethod.POST,
                pathTemplate = "api/v1/mcp/whitelist/friends/requests",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "senderUsername",
                            description =
                                "Username of the user sending the friend request (no '@').",
                            location = McpRouteParameterLocation.BODY,
                        ),
                        McpRouteParameter(
                            name = "receiverUsername",
                            description =
                                "Username of the user receiving the friend request (no '@').",
                            location = McpRouteParameterLocation.BODY,
                        ),
                    ),
            ),
            McpRoute(
                name = "Accept Friend Request",
                description =
                    "Accept a pending friend request using sender and receiver usernames. Use for MCP testing only.",
                method = HttpMethod.POST,
                pathTemplate = "api/v1/mcp/whitelist/friends/requests/accept",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "senderUsername",
                            description =
                                "Username of the user who sent the friend request (no '@').",
                            location = McpRouteParameterLocation.BODY,
                        ),
                        McpRouteParameter(
                            name = "receiverUsername",
                            description =
                                "Username of the user who received the friend request (no '@').",
                            location = McpRouteParameterLocation.BODY,
                        ),
                    ),
                errorTemplates =
                    mapOf(
                        HttpStatus.NOT_FOUND.value() to
                            "No pending friend request from {senderUsername} to you was found.",
                        HttpStatus.FORBIDDEN.value() to
                            "You are not allowed to respond to {senderUsername}'s friend request.",
                    ),
            ),
            McpRoute(
                name = "Reject Friend Request",
                description =
                    "Reject a pending friend request using sender and receiver usernames. Use for MCP testing only.",
                method = HttpMethod.POST,
                pathTemplate = "api/v1/mcp/whitelist/friends/requests/reject",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "senderUsername",
                            description =
                                "Username of the user who sent the friend request (no '@').",
                            location = McpRouteParameterLocation.BODY,
                        ),
                        McpRouteParameter(
                            name = "receiverUsername",
                            description =
                                "Username of the user who received the friend request (no '@').",
                            location = McpRouteParameterLocation.BODY,
                        ),
                    ),
                errorTemplates =
                    mapOf(
                        HttpStatus.NOT_FOUND.value() to
                            "No pending friend request from {senderUsername} to you was found.",
                        HttpStatus.FORBIDDEN.value() to
                            "You are not allowed to respond to {senderUsername}'s friend request.",
                    ),
            ),
            McpRoute(
                name = "Received Friend Requests",
                description =
                    "List pending friend requests received by the authenticated user (incoming requests). Supports pagination via page/size. Use for: 'incoming friend requests', 'friend requests I received'.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/friends/requests/received",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "page",
                            description =
                                "Zero-based page index for pagination (optional).",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                        McpRouteParameter(
                            name = "size",
                            description =
                                "Page size / number of records per page (optional).",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                    ),
            ),
            McpRoute(
                name = "Sent Friend Requests",
                description =
                    "List pending friend requests created by the authenticated user (outgoing requests). Supports pagination via page/size. Use for: 'sent friend requests', 'my pending friend requests'.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/friends/requests/sent",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "page",
                            description =
                                "Zero-based page index for pagination (optional).",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                        McpRouteParameter(
                            name = "size",
                            description =
                                "Page size / number of records per page (optional).",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                    ),
            ),
            McpRoute(
                name = "Friend Suggestions",
                description =
                    "List suggested profiles to add as friends (recommendations, e.g., friends-of-friends). Supports pagination via page/size. Use for: 'suggest friends', 'friend recommendations'.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/friends/suggestions",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "page",
                            description =
                                "Zero-based page index for pagination (optional).",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                        McpRouteParameter(
                            name = "size",
                            description =
                                "Page size / number of records per page (optional).",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                    ),
            ),
            McpRoute(
                name = "Friend Stats",
                description =
                    "Return friend summary counts for the authenticated user (e.g., total friends, pending incoming requests, pending outgoing requests). Use for: 'how many friends do I have?', 'my friend stats'.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/friends/stats",
            ),
            McpRoute(
                name = "Specific User Friends",
                description =
                    "List a specific user's friends, if the authenticated viewer has permission to see them. Supports pagination via page/size. Use for: 'a user's friends list', 'show this user's friends'.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/friends/user/{userId}",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "userId",
                            description =
                                "UUID of the user whose friends will be fetched.",
                        ),
                        McpRouteParameter(
                            name = "page",
                            description =
                                "Zero-based page index for pagination (optional).",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                        McpRouteParameter(
                            name = "size",
                            description =
                                "Page size / number of records per page (optional).",
                            required = false,
                            location = McpRouteParameterLocation.QUERY,
                        ),
                    ),
                errorTemplates =
                    mapOf(
                        HttpStatus.FORBIDDEN.value() to
                            "You are not allowed to view this user's friends.",
                        HttpStatus.NOT_FOUND.value() to
                            "User '{userId}' was not found.",
                    ),
            ),
            McpRoute(
                name = "My Survey Responses",
                description =
                    "List survey responses previously submitted by the authenticated user (their own submissions/history). Use for: 'my survey responses', 'surveys I submitted'.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/survey-responses/my",
            ),
        )

    fun routes(): List<McpRoute> = availableRoutes

    fun routeByName(name: String): McpRoute? = availableRoutes.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
