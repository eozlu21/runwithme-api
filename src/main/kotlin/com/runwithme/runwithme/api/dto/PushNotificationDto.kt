package com.runwithme.runwithme.api.dto

import com.runwithme.runwithme.api.entity.DevicePlatform
import com.runwithme.runwithme.api.entity.DeviceToken
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.UUID

// ==================== Device Token DTOs ====================

@Schema(description = "Request to register a device token for push notifications")
data class RegisterDeviceTokenRequest(
    @Schema(
        description = "Firebase Cloud Messaging device token",
        example = "dKj2h8s9f...",
        required = true,
    )
    @field:NotBlank(message = "Token is required")
    val token: String,
    @Schema(
        description = "Device platform (IOS, ANDROID, WEB)",
        example = "ANDROID",
        required = true,
    )
    @field:NotNull(message = "Platform is required")
    val platform: DevicePlatform,
    @Schema(
        description = "Optional device name for identification",
        example = "John's iPhone 15",
        required = false,
    )
    val deviceName: String? = null,
)

@Schema(description = "Request to unregister a device token")
data class UnregisterDeviceTokenRequest(
    @Schema(
        description = "Firebase Cloud Messaging device token to remove",
        example = "dKj2h8s9f...",
        required = true,
    )
    @field:NotBlank(message = "Token is required")
    val token: String,
)

@Schema(description = "Response for device token registration")
data class DeviceTokenResponse(
    @Schema(description = "Device token ID")
    val id: Long,
    @Schema(description = "Device platform")
    val platform: DevicePlatform,
    @Schema(description = "Device name")
    val deviceName: String?,
    @Schema(description = "Whether the token is active")
    val isActive: Boolean,
    @Schema(description = "When the token was registered")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun fromEntity(entity: DeviceToken): DeviceTokenResponse =
            DeviceTokenResponse(
                id = entity.id!!,
                platform = entity.platform,
                deviceName = entity.deviceName,
                isActive = entity.isActive,
                createdAt = entity.createdAt,
            )
    }
}

@Schema(description = "Response containing list of registered devices")
data class DeviceListResponse(
    @Schema(description = "List of registered devices")
    val devices: List<DeviceTokenResponse>,
    @Schema(description = "Total count of active devices")
    val activeCount: Int,
)

// ==================== Push Notification DTOs ====================

/**
 * Enum representing different types of push notifications.
 */
@Schema(description = "Type of push notification")
enum class PushNotificationType {
    @Schema(description = "New chat message received")
    NEW_MESSAGE,

    @Schema(description = "Friend request received")
    FRIEND_REQUEST,

    @Schema(description = "Friend request accepted")
    FRIEND_REQUEST_ACCEPTED,

    @Schema(description = "New comment on your post")
    NEW_COMMENT,

    @Schema(description = "Someone liked your post")
    NEW_LIKE,

    @Schema(description = "Run session invitation")
    RUN_INVITATION,

    @Schema(description = "General notification")
    GENERAL,
}

/**
 * Internal DTO for creating push notifications.
 */
data class PushNotificationRequest(
    val recipientUserId: UUID,
    val title: String,
    val body: String,
    val type: PushNotificationType,
    val data: Map<String, String> = emptyMap(),
    val imageUrl: String? = null,
)

/**
 * DTO representing a message notification payload.
 */
data class MessageNotificationPayload(
    val messageId: Long,
    val senderId: UUID,
    val senderUsername: String,
    val content: String,
    val conversationId: String,
)

@Schema(description = "Response for push notification operation")
data class PushNotificationResponse(
    @Schema(description = "Whether the notification was sent successfully")
    val success: Boolean,
    @Schema(description = "Number of devices the notification was sent to")
    val deviceCount: Int,
    @Schema(description = "Error message if failed")
    val errorMessage: String? = null,
)

// ==================== Notification Settings DTOs ====================

@Schema(description = "User's notification preferences")
data class NotificationPreferences(
    @Schema(description = "Enable push notifications for new messages")
    val messagesEnabled: Boolean = true,
    @Schema(description = "Enable push notifications for friend requests")
    val friendRequestsEnabled: Boolean = true,
    @Schema(description = "Enable push notifications for comments")
    val commentsEnabled: Boolean = true,
    @Schema(description = "Enable push notifications for likes")
    val likesEnabled: Boolean = true,
    @Schema(description = "Enable push notifications for run invitations")
    val runInvitationsEnabled: Boolean = true,
    @Schema(description = "Quiet hours start time (HH:mm format)")
    val quietHoursStart: String? = null,
    @Schema(description = "Quiet hours end time (HH:mm format)")
    val quietHoursEnd: String? = null,
)
