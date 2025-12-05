package com.runwithme.runwithme.api.dto

import com.runwithme.runwithme.api.entity.FriendRequest
import com.runwithme.runwithme.api.entity.FriendRequestStatus
import com.runwithme.runwithme.api.entity.Friendship
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

// ============ Friend Request DTOs ============

@Schema(description = "Friend request data transfer object")
data class FriendRequestDto(
    @Schema(description = "Friend request unique identifier")
    val requestId: UUID,
    @Schema(description = "Sender user ID")
    val senderId: UUID,
    @Schema(description = "Receiver user ID")
    val receiverId: UUID,
    @Schema(description = "Status of the friend request")
    val status: FriendRequestStatus,
    @Schema(description = "When the request was created")
    val createdAt: OffsetDateTime,
    @Schema(description = "When the request was last updated")
    val updatedAt: OffsetDateTime,
    @Schema(description = "Optional message with the request")
    val message: String?,
) {
    companion object {
        fun fromEntity(entity: FriendRequest): FriendRequestDto =
            FriendRequestDto(
                requestId = entity.requestId!!,
                senderId = entity.senderId,
                receiverId = entity.receiverId,
                status = entity.status,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                message = entity.message,
            )
    }
}

@Schema(description = "Friend request with user details")
data class FriendRequestWithUserDto(
    @Schema(description = "Friend request unique identifier")
    val requestId: UUID,
    @Schema(description = "Sender user details")
    val sender: UserDto,
    @Schema(description = "Receiver user details")
    val receiver: UserDto,
    @Schema(description = "Status of the friend request")
    val status: FriendRequestStatus,
    @Schema(description = "When the request was created")
    val createdAt: OffsetDateTime,
    @Schema(description = "Optional message with the request")
    val message: String?,
)

@Schema(description = "Request to send a friend request")
data class SendFriendRequestDto(
    @Schema(description = "ID of the user to send friend request to", required = true)
    val receiverId: UUID,
    @Schema(description = "Optional message to include with the request")
    val message: String? = null,
)

@Schema(description = "Request to respond to a friend request")
data class RespondToFriendRequestDto(
    @Schema(description = "Whether to accept the friend request", required = true)
    val accept: Boolean,
)

// ============ Friendship DTOs ============

@Schema(description = "Friendship data transfer object")
data class FriendshipDto(
    @Schema(description = "Friendship unique identifier")
    val friendshipId: UUID,
    @Schema(description = "First user ID")
    val user1Id: UUID,
    @Schema(description = "Second user ID")
    val user2Id: UUID,
    @Schema(description = "When the friendship was created")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun fromEntity(entity: Friendship): FriendshipDto =
            FriendshipDto(
                friendshipId = entity.friendshipId!!,
                user1Id = entity.user1Id,
                user2Id = entity.user2Id,
                createdAt = entity.createdAt,
            )
    }
}

@Schema(description = "Friend details DTO")
data class FriendDto(
    @Schema(description = "User details of the friend")
    val user: UserDto,
    @Schema(description = "When the friendship was created")
    val friendsSince: OffsetDateTime,
)

// ============ Friend Count DTO ============

@Schema(description = "Friend statistics")
data class FriendStatsDto(
    @Schema(description = "Total number of friends")
    val friendCount: Int,
    @Schema(description = "Number of pending friend requests received")
    val pendingRequestsReceived: Int,
    @Schema(description = "Number of pending friend requests sent")
    val pendingRequestsSent: Int,
)
