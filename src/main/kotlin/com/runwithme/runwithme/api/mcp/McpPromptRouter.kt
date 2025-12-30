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
                    "Fetch a user's profile details by user id (e.g., firstName, lastName, pronouns, birthday, expertLevel, profileVisibility). Use for: 'Ben kimim?', 'profilim', 'zamirlerim', 'doğum günüm', 'uzmanlık seviyem'.",
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
                    "Find a user by username handle (e.g., 'minaaa' without '@') and return the matching user's basic account/profile info. Use for: '@minaaa var mı?', 'kullanıcı adı minaaa'. Not for searching by real name (e.g., 'Mina adında biri').",
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
                name = "Send Friend Request",
                description =
                    "Send/create a friend request from the authenticated user to another user. Requires the target user's UUID (receiverId). Optionally include a short note (message). Use for: 'arkadaşlık isteği gönder', 'arkadaş ekle'.",
                method = HttpMethod.POST,
                pathTemplate = "api/v1/friends/requests",
                parameters =
                    listOf(
                        McpRouteParameter(
                            name = "receiverId",
                            description =
                                "UUID of the user who should receive the friend request.",
                            location = McpRouteParameterLocation.BODY,
                        ),
                        McpRouteParameter(
                            name = "message",
                            description =
                                "Optional note/message to include with the friend request.",
                            required = false,
                            location = McpRouteParameterLocation.BODY,
                        ),
                    ),
                errorTemplates =
                    mapOf(
                        HttpStatus.NOT_FOUND.value() to
                            "User '{receiverId}' could not be found.",
                        HttpStatus.CONFLICT.value() to
                            "There is already a pending request with this user.",
                    ),
            ),
            McpRoute(
                name = "Received Friend Requests",
                description =
                    "List pending friend requests received by the authenticated user (incoming requests). Supports pagination via page/size. Use for: 'gelen istekler', 'bana gelen arkadaşlık istekleri'.",
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
                    "List pending friend requests created by the authenticated user (outgoing requests). Supports pagination via page/size. Use for: 'gönderdiğim istekler', 'bekleyen isteklerim'.",
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
                    "List suggested profiles to add as friends (recommendations, e.g., friends-of-friends). Supports pagination via page/size. Use for: 'arkadaş öner', 'arkadaş önerileri'.",
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
                    "Return friend summary counts for the authenticated user (e.g., total friends, pending incoming requests, pending outgoing requests). Use for: 'kaç arkadaşım var?', 'arkadaş istatistiklerim'.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/friends/stats",
            ),
            McpRoute(
                name = "Specific User Friends",
                description =
                    "List a specific user's friends, if the authenticated viewer has permission to see them. Supports pagination via page/size. Use for: 'X kullanıcısının arkadaşları'.",
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
                    "List survey responses previously submitted by the authenticated user (their own submissions/history). Use for: 'anket cevaplarım', 'gönderdiğim anketler'.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/survey-responses/my",
            ),
        )

    fun routes(): List<McpRoute> = availableRoutes

    fun routeByName(name: String): McpRoute? = availableRoutes.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
