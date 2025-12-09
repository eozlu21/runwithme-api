package com.runwithme.runwithme.api.dto

import com.runwithme.runwithme.api.entity.Message
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Message data transfer object")
data class MessageDto(
    @Schema(description = "Message ID", example = "1")
    val id: Long,
    @Schema(description = "Sender User ID", example = "1")
    val senderId: UUID,
    @Schema(description = "Recipient User ID", example = "2")
    val recipientId: UUID,
    @Schema(description = "Sender Username", example = "john_doe")
    val senderUsername: String? = null,
    @Schema(description = "Recipient Username", example = "jane_doe")
    val recipientUsername: String? = null,
    @Schema(description = "Message content", example = "Hello there!")
    val content: String,
    @Schema(description = "Creation timestamp")
    val createdAt: OffsetDateTime,
    @Schema(description = "Is message read", example = "false")
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
    @Schema(description = "Recipient User ID", example = "2")
    val recipientId: UUID,
    @Schema(description = "Message content", example = "Hello there!")
    val content: String,
)
