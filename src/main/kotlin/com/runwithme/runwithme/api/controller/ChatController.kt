package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.CreateMessageRequest
import com.runwithme.runwithme.api.dto.MarkMessagesReadRequest
import com.runwithme.runwithme.api.dto.MarkMessagesReadResponse
import com.runwithme.runwithme.api.dto.MessageDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.service.MessageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.user.SimpUserRegistry
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping
@Tag(
    name = "Chat",
    description =
        """
    Private messaging APIs for 1-to-1 chat between users.
    
    **WebSocket Integration:**
    - **Connect:** `ws://<host>:8080/ws` with SockJS
    - **Auth Header:** `Authorization: Bearer <token>` in STOMP CONNECT headers
    - **Subscribe to receive messages:** `/user/queue/messages`
    - **Subscribe to public messages:** `/topic/public`
    - **Send private message:** `/app/chat` with `CreateMessageRequest` JSON payload
    - **Send public message:** `/app/chat.public` with `CreateMessageRequest` JSON payload
    
    **Message Flow:**
    1. Connect to WebSocket with JWT token
    2. Subscribe to `/user/queue/messages` to receive incoming messages
    3. Send messages via `/app/chat` - they will be saved to DB and delivered to recipient
    4. Use REST endpoints below to load chat history
    """,
)
class ChatController(
    private val messageService: MessageService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val simpUserRegistry: SimpUserRegistry,
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)

    @MessageMapping("/chat")
    fun processMessage(
        @Payload request: CreateMessageRequest,
        principal: Principal,
    ) {
        logger.info("Processing message from principal: ${principal.name}")
        logger.info("Connected users: ${simpUserRegistry.users.map { it.name }}")

        val messageDto = messageService.sendMessage(principal.name, request)

        // Send to recipient
        if (messageDto.recipientUsername != null) {
            logger.info("Sending to recipient: ${messageDto.recipientUsername}")

            // Check if user is connected
            val recipientUser = simpUserRegistry.getUser(messageDto.recipientUsername)
            if (recipientUser != null) {
                logger.info(
                    "Recipient ${messageDto.recipientUsername} is connected with ${recipientUser.sessions.size} sessions",
                )
            } else {
                logger.warn("Recipient ${messageDto.recipientUsername} is NOT connected")
            }

            messagingTemplate.convertAndSendToUser(
                messageDto.recipientUsername,
                "/queue/messages",
                messageDto,
            )
        }

        // Send back to sender (so they see it immediately confirmed/formatted)
        logger.info("Sending confirmation to sender: ${principal.name}")
        messagingTemplate.convertAndSendToUser(
            principal.name,
            "/queue/messages",
            messageDto,
        )
    }

    @PostMapping("/api/v1/chat/send")
    @Operation(
        summary = "Send a private message via REST",
        description =
            "Alternative to WebSocket for sending messages. " +
                "The message will be saved to DB and delivered via " +
                "WebSocket if recipient is connected.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Message sent successfully",
                content = [Content(schema = Schema(implementation = MessageDto::class))],
            ),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "Recipient not found"),
        ],
    )
    fun sendMessageViaRest(
        @RequestBody request: CreateMessageRequest,
        authentication: Authentication,
    ): ResponseEntity<MessageDto> {
        logger.info("Sending message via REST from ${authentication.name} to ${request.recipientId}")
        val messageDto = messageService.sendMessage(authentication.name, request)

        // Try to deliver via WebSocket if recipient is connected
        if (messageDto.recipientUsername != null) {
            val recipientUser = simpUserRegistry.getUser(messageDto.recipientUsername)
            if (recipientUser != null) {
                messagingTemplate.convertAndSendToUser(
                    messageDto.recipientUsername,
                    "/queue/messages",
                    messageDto,
                )
                logger.info("Message also delivered via WebSocket to ${messageDto.recipientUsername}")
            }
        }

        return ResponseEntity.ok(messageDto)
    }

    @GetMapping("/api/v1/chat/history/{otherUserId}")
    @Operation(
        summary = "Get chat history with a specific user",
        description = "Retrieves paginated message history between the authenticated user and another user. Messages are ordered by creation time (newest first).",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Chat history retrieved successfully",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
            ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing token"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    fun getChatHistory(
        @Parameter(description = "UUID of the other user to get chat history with", required = true)
        @PathVariable otherUserId: UUID,
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Number of messages per page", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<MessageDto>> = ResponseEntity.ok(messageService.getChatHistory(authentication.name, otherUserId, page, size))

    @GetMapping("/api/v1/chat/connected-users")
    @Operation(
        summary = "Get connected WebSocket users",
        description = "Returns list of usernames currently connected via WebSocket. Useful for debugging real-time messaging.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "List of connected usernames"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
        ],
    )
    fun getConnectedUsers(): ResponseEntity<List<String>> {
        val users = simpUserRegistry.users.map { it.name }
        logger.info("Connected users requested: $users")
        return ResponseEntity.ok(users)
    }

    @GetMapping("/api/v1/chat/history")
    @Operation(
        summary = "Get all chat messages for authenticated user",
        description = "Retrieves all messages where the authenticated user is either sender or recipient. Can optionally filter to only show messages from friends.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Messages retrieved successfully",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
            ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing token"),
        ],
    )
    fun getAllRelatedMessages(
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Number of messages per page", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "If true, only return messages from friends", example = "false")
        @RequestParam(defaultValue = "false") friendsOnly: Boolean,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<MessageDto>> =
        ResponseEntity.ok(
            messageService.getAllRelatedMessages(
                authentication.name,
                page,
                size,
                friendsOnly,
            ),
        )

    @PostMapping("/api/v1/chat/read")
    @Operation(
        summary = "Mark messages as read",
        description = "Marks the specified message IDs as read. Only messages where the authenticated user is the recipient can be marked as read.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Messages marked as read",
                content = [Content(schema = Schema(implementation = MarkMessagesReadResponse::class))],
            ),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
        ],
    )
    fun markMessagesAsRead(
        @RequestBody request: MarkMessagesReadRequest,
        authentication: Authentication,
    ): ResponseEntity<MarkMessagesReadResponse> {
        val updatedCount =
            messageService.markMessagesAsRead(authentication.name, request.messageIds)
        return ResponseEntity.ok(MarkMessagesReadResponse(updatedCount))
    }
}
