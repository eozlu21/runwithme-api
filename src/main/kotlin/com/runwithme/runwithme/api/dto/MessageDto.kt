package com.runwithme.runwithme.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.runwithme.runwithme.api.entity.Message
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Enum representing the type of chat event sent over WebSocket.
 *
 * Frontend should check this field to determine how to handle the incoming message:
 * - NEW_MESSAGE: A new chat message was received. Payload is a MessageDto.
 * - READ_RECEIPT: Messages were marked as read by the recipient. Payload is a ReadReceiptPayload.
 */
@Schema(description = "Type of chat event for WebSocket communication")
enum class MessageType {
    @Schema(description = "A new chat message was sent/received")
    NEW_MESSAGE,

    @Schema(description = "Messages were marked as read by the recipient")
    READ_RECEIPT,
}

/**
 * Wrapper DTO for all chat-related WebSocket events.
 *
 * This is the structure sent to /user/queue/messages for all real-time chat events. Frontend should
 * subscribe to /user/queue/messages and handle events based on the `type` field.
 *
 * Example payloads:
 * - For NEW_MESSAGE: payload is MessageDto
 * - For READ_RECEIPT: payload is ReadReceiptPayload
 */
@Schema(description = "Wrapper for all chat WebSocket events")
data class ChatEvent(
    @Schema(description = "Type of the chat event", example = "NEW_MESSAGE")
    val type: MessageType,
    @Schema(description = "Event payload - structure depends on type") val payload: Any,
    @Schema(description = "Timestamp when the event was created")
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
)

/**
 * Payload for READ_RECEIPT events.
 *
 * Sent to the original message sender when the recipient marks their messages as read. Contains the
 * list of message IDs that were marked as read.
 */
@Schema(description = "Payload for READ_RECEIPT events")
data class ReadReceiptPayload(
    @Schema(description = "ID of the user who read the messages") val readByUserId: UUID,
    @Schema(description = "Username of the user who read the messages")
    val readByUsername: String,
    @Schema(description = "List of message IDs that were marked as read", example = "[1, 2, 3]")
    val messageIds: List<Long>,
)

@Schema(description = "Message data transfer object")
data class MessageDto(
    @Schema(description = "Message ID", example = "1") val id: Long,
    @Schema(description = "Sender User ID", example = "1") val senderId: UUID,
    @Schema(description = "Recipient User ID", example = "2") val recipientId: UUID,
    @Schema(description = "Sender Username", example = "john_doe")
    val senderUsername: String? = null,
    @Schema(description = "Recipient Username", example = "jane_doe")
    val recipientUsername: String? = null,
    @Schema(description = "Message content", example = "Hello there!") val content: String,
    @Schema(description = "Creation timestamp") val createdAt: OffsetDateTime,
    @Schema(description = "Is message read", example = "false")
    @get:JsonProperty("isRead")
    val isRead: Boolean,
) {
    companion object {
        fun fromEntity(
            message: Message,
            senderUsername: String? = null,
            recipientUsername: String? = null,
        ): MessageDto =
            MessageDto(
                id = message.id!!,
                senderId = message.senderId!!,
                recipientId = message.recipientId!!,
                senderUsername = senderUsername,
                recipientUsername = recipientUsername,
                content = message.content!!,
                createdAt = message.createdAt,
                isRead = message.isRead,
            )
    }
}

@Schema(description = "Create message request")
data class CreateMessageRequest(
    @Schema(description = "Recipient User ID", example = "2") val recipientId: UUID,
    @Schema(description = "Message content", example = "Hello there!") val content: String,
)

@Schema(description = "Mark messages as read request")
data class MarkMessagesReadRequest(
    @Schema(description = "Message IDs to mark as read", example = "[1,2,3]")
    @field:JsonProperty("messageIds")
    val messageIds: List<Long>,
)

@Schema(description = "Mark messages as read response")
data class MarkMessagesReadResponse(
    @Schema(description = "Number of messages updated", example = "3") val updatedCount: Int,
)

@Schema(description = "Unread message count response")
data class UnreadCountResponse(
    @Schema(description = "Number of unread messages", example = "5") val unreadCount: Long,
)
