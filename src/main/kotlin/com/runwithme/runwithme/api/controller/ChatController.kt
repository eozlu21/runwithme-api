package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.CreateMessageRequest
import com.runwithme.runwithme.api.dto.MarkMessagesReadRequest
import com.runwithme.runwithme.api.dto.MarkMessagesReadResponse
import com.runwithme.runwithme.api.dto.MessageDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.service.MessageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

@RestController
@Tag(
    name = "Chat",
    description =
        """
    Messaging APIs.
    
    **WebSocket Integration:**
    - **Connect:** `ws://<host>:8080/ws`
    - **Subscribe:** `/user/queue/messages` (to receive messages)
    - **Send:** `/app/chat` (to send messages)
    - **Payload:** `CreateMessageRequest` JSON
    """,
)
class ChatController(
    private val messageService: MessageService,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    @MessageMapping("/chat")
    fun processMessage(
        @Payload request: CreateMessageRequest,
        principal: Principal,
    ) {
        val messageDto = messageService.sendMessage(principal.name, request)

        // Send to recipient
        if (messageDto.recipientUsername != null) {
            messagingTemplate.convertAndSendToUser(
                messageDto.recipientUsername,
                "/queue/messages",
                messageDto,
            )
        }

        // Send back to sender (so they see it immediately confirmed/formatted)
        messagingTemplate.convertAndSendToUser(
            principal.name, // Or sender ID if principal name is username
            "/queue/messages",
            messageDto,
        )
    }

    @GetMapping("/api/v1/chat/history/{otherUserId}")
    @Operation(summary = "Get chat history with a user")
    fun getChatHistory(
        @Parameter(description = "Other User ID") @PathVariable otherUserId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<MessageDto>> =
        ResponseEntity.ok(
            messageService.getChatHistory(authentication.name, otherUserId, page, size),
        )

    @PostMapping("/api/v1/chat/read")
    @Operation(summary = "Mark specific messages as read")
    fun markMessagesAsRead(
        @RequestBody request: MarkMessagesReadRequest,
        authentication: Authentication,
    ): ResponseEntity<MarkMessagesReadResponse> {
        val updatedCount =
            messageService.markMessagesAsRead(authentication.name, request.messageIds)
        return ResponseEntity.ok(MarkMessagesReadResponse(updatedCount))
    }
}
