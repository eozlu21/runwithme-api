package com.runwithme.runwithme.api.mcp

import com.runwithme.runwithme.api.dto.MessageDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.repository.MessageRepository
import com.runwithme.runwithme.api.repository.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.persistence.EntityManager
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/mcp")
@Tag(
    name = "MCP",
    description =
        """
        Endpoints for the MCP (Model Context Protocol) agent integration.

        Includes:
        - Manual agent invocation (`/run`)
        - Chat helper endpoints for the MCP agent conversation (`/chat/*`)
        """,
)
class McpController(
    private val agentService: McpAgentService,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val properties: McpProperties,
    private val entityManager: EntityManager,
) {
    // Exposes the agent over HTTP for quick manual tests.
    @PostMapping("/run")
    @Operation(
        summary = "Run MCP agent",
        description =
            """
            Runs the MCP agent for the authenticated user.

            - Uses the allow-list routes from `McpPromptRouter`
            - Forwards the caller's `Authorization` header to the selected internal API call
            - Persists the user prompt and agent reply into the chat history (unless the agent decides `NO_MATCH`)
            """,
    )
    fun run(
        @Valid @RequestBody request: McpAgentRequest,
        @Parameter(description = "Bearer token forwarded to internal API calls (optional depending on selected route)")
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        authentication: Authentication,
    ): ResponseEntity<McpAgentResponse> {
        val starter = requireAuthenticatedUser(authentication)
        val response = agentService.runAgent(request, authorization, starter.userId, starter.username)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
        }
    }

    @GetMapping("/chat/history")
    @Operation(
        summary = "Get MCP chat history",
        description =
            """
            Returns paginated chat history between the authenticated user and the MCP agent user.

            Useful for debugging or building an MCP chat UI.
            """,
    )
    fun chatHistory(
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1..100)", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val starter = requireAuthenticatedUser(authentication)
        val agent = agentIdentityOrNull() ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(McpChatErrorResponse("MCP agent identity is not configured."))

        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }

        val pageRequest =
            PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"),
            )
        val messagePage = messageRepository.findChatHistory(starter.userId, agent.userId, pageRequest)

        val response =
            PageResponse.fromPage(messagePage) { message ->
                val senderUsername = if (message.senderId == starter.userId) starter.username else agent.username
                val recipientUsername = if (message.recipientId == starter.userId) starter.username else agent.username
                MessageDto.fromEntity(
                    message = message,
                    senderUsername = senderUsername,
                    recipientUsername = recipientUsername,
                )
            }
        return ResponseEntity.ok(response)
    }

    @PostMapping("/chat/read-all")
    @Transactional
    @Operation(
        summary = "Mark all MCP chat messages as read",
        description =
            """
            Marks all unread messages addressed to the MCP agent user as read, across all conversations.

            Notes:
            - This endpoint is intended to be called while authenticated as the MCP agent user.
            - It marks only messages where the MCP agent is the recipient.
            """,
    )
    fun readAllMcpChatMessages(authentication: Authentication): ResponseEntity<Any> {
        val agent = agentIdentityOrNull() ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(McpChatErrorResponse("MCP agent identity is not configured."))
        val caller = requireAuthenticatedUser(authentication)
        if (caller.userId != agent.userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(McpChatErrorResponse("Only the MCP agent user can call this endpoint."))
        }

        val updatedCount =
            entityManager
                .createQuery(
                    "UPDATE Message m SET m.isRead = true WHERE m.recipientId = :recipientId AND m.isRead = false",
                ).setParameter("recipientId", agent.userId)
                .executeUpdate()

        return ResponseEntity.ok(
            McpReadAllMessagesResponse(
                updatedCount = updatedCount,
            ),
        )
    }

    @PostMapping("/reset-history")
    @Operation(
        summary = "Reset MCP agent history",
        description =
            """
            Clears the MCP agent's conversation context for the authenticated user.

            This affects how Gemini sees prior chat turns (the agent will behave like a fresh session).
            """,
    )
    fun resetHistory(authentication: Authentication): ResponseEntity<Void> {
        val starter = requireAuthenticatedUser(authentication)
        agentService.resetHistory(starter.userId)
        return ResponseEntity.noContent().build()
    }

    private fun requireAuthenticatedUser(authentication: Authentication): AuthenticatedUser {
        val username = authentication.name
        val userId =
            userRepository
                .findByUsername(username)
                .orElseThrow { IllegalStateException("Authenticated user not found: $username") }
                .userId
                ?: throw IllegalStateException("User id missing for $username")
        return AuthenticatedUser(userId = userId, username = username)
    }

    private fun agentIdentityOrNull(): AgentIdentity? {
        val username = properties.agentUsername.trim()
        val rawUserId = properties.agentUserId.trim()
        if (username.isBlank() || rawUserId.isBlank()) {
            return null
        }
        val userId =
            try {
                UUID.fromString(rawUserId)
            } catch (ex: IllegalArgumentException) {
                return null
            }
        return AgentIdentity(userId = userId, username = username)
    }
}
