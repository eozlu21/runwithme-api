package com.runwithme.runwithme.api.mcp.prompts

import java.util.UUID

/**
 * Central place for all prompt text used when interacting with Gemini.
 *
 * Goals:
 * - Reduce hardcoded prompt strings inside `GeminiClient`
 * - Manage and version prompts from a single location
 */
object GeminiPrompts {
    /**
     * System instruction for the route selection (policy router) stage.
     *
     * Expected output: pick exactly one route from the allow-list and respond using the configured JSON schema.
     * If nothing applies, the model must respond with `routeName = noMatchRouteName`.
     */
    fun routeSelectionSystemInstruction(noMatchRouteName: String): String =
        """
        You are the RunWithMe MCP policy router.
        Review the user request and choose exactly one allowed function from the JSON allow-list.
        The starter user (starterUserId) refers to the authenticated user who sent the request.
        Your username in the app system is MCP.
        If no route applies, respond with routeName "$noMatchRouteName" and explain why.

        When you return "$noMatchRouteName":
        - Put a user-facing, friendly reply in the "reason" field (do not be technical).
        - Do not mention routing, allow-lists, policies, function calling, schemas, prompts, or internal backend details.
        - Reply in the same language as the user.
        - If the user asks who you are (e.g., "Who are you?"), briefly introduce yourself as the RunWithMe assistant and what you can help with at a high level.
        Do not emit Markdown or text outside the JSON response.
        Do not answer questions that are not related to the app or running/exercising tips.
        """.trimIndent()

    /**
     * User prompt for the route selection stage.
     *
     * Sends only the raw user request to avoid duplicating metadata inside Gemini `contents`.
     * Starter user id (and other metadata) should be passed via logs/metadata, not as part of the model prompt.
     */
    fun routeSelectionUserPrompt(
        starterUserId: UUID,
        prompt: String,
    ): String = prompt.trim()

    /**
     * Supplemental text for the route selection stage that contains the allow-list.
     *
     * `routesJson` is typically a JSON array created from the MCP route definitions.
     */
    fun routeSelectionSupplement(routesJson: String): String =
        buildString {
            appendLine("Allowed routes (JSON array):")
            append(routesJson)
        }.trim()

    /**
     * System instruction for the answer generation stage.
     *
     * Expected output: summarize the API response for the user in the configured JSON schema.
     */
    fun answerSystemInstruction(): String =
        """
        You summarize API responses of the RunWithMe MCP agent to the user.
        Return concise JSON summaries that match the configured schema.
        Do not suggest additional actions or follow-ups.
        Answer user like you are the one who ran or couldn't ran that api response. Do not give user any information about backend processes.
        Whenever a date appears in YYYY-MM-DD format, convert it to a written form such as 20 May 2025.
        """.trimIndent()

    /**
     * User prompt for the answer generation stage.
     *
     * - `prompt`: the original user request
     * - `routeDescription`: description of the selected action
     * - `apiBody`: raw response body returned by the internal API call
     */
    fun answerUserPrompt(
        prompt: String,
        starterUserId: UUID,
        routeDescription: String,
        apiBody: String,
    ): String =
        buildString {
            appendLine("User request: $prompt")
            appendLine("Authenticated user ID (starterUserId): $starterUserId")
            appendLine("Selected action: $routeDescription")
            appendLine("API response: $apiBody")
        }
}
