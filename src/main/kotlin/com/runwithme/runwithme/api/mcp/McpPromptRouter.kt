package com.runwithme.runwithme.api.mcp

import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component

// MCP'nin call edebilecegi butun route'lari burada tutacaz haci

@Component
class McpPromptRouter {
    private val availableRoutes =
        listOf(

            McpRoute(
                name = "Kullanıcı İstatistikleri", //TODO
                description = "Kullanıcının istatistik bilgilerini getirir.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/users",
            ),
            McpRoute(
                name = "Kullanici Ismiyle Kullanici Bilgisi",
                description = "Kullanici ismine gore kullanici bilgisini getirir.",
                method = HttpMethod.GET,
                pathTemplate = "api/v1/users/username/{username}",
                parameters = listOf(
                    McpRouteParameter(
                        name = "username",
                        description = "Aranan kullanici adi (ornegin minaaa).",
                        required = true,
                    ),
                ),
            ),


        )

    fun routes(): List<McpRoute> = availableRoutes

    fun routeByName(name: String): McpRoute? =
        availableRoutes.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

data class McpRoute(
    val name: String,
    val description: String,
    val method: HttpMethod,
    val pathTemplate: String,
    val parameters: List<McpRouteParameter> = emptyList(),
    val requiresAuth: Boolean = true,
)

data class McpRouteParameter(
    val name: String,
    val description: String,
    val required: Boolean = true,
)
