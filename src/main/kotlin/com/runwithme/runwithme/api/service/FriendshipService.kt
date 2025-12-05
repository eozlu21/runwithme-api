package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.FriendDto
import com.runwithme.runwithme.api.dto.FriendRequestDto
import com.runwithme.runwithme.api.dto.FriendRequestWithUserDto
import com.runwithme.runwithme.api.dto.FriendStatsDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.UserDto
import com.runwithme.runwithme.api.entity.FriendRequest
import com.runwithme.runwithme.api.entity.FriendRequestStatus
import com.runwithme.runwithme.api.entity.Friendship
import com.runwithme.runwithme.api.entity.ProfileVisibility
import com.runwithme.runwithme.api.exception.DuplicateResourceException
import com.runwithme.runwithme.api.exception.UnauthorizedActionException
import com.runwithme.runwithme.api.repository.FriendRequestRepository
import com.runwithme.runwithme.api.repository.FriendshipRepository
import com.runwithme.runwithme.api.repository.UserProfileRepository
import com.runwithme.runwithme.api.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class FriendshipService(
    private val friendshipRepository: FriendshipRepository,
    private val friendRequestRepository: FriendRequestRepository,
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
) {
    // ============ Friend Request Operations ============

    /**
     * Send a friend request to another user
     */
    @Transactional
    fun sendFriendRequest(
        senderId: UUID,
        receiverId: UUID,
        message: String?,
    ): FriendRequestDto {
        // Check if sender is trying to add themselves
        if (senderId == receiverId) {
            throw IllegalArgumentException("You cannot send a friend request to yourself")
        }

        // Check if receiver exists
        if (!userRepository.existsById(receiverId)) {
            throw NoSuchElementException("User with ID $receiverId not found")
        }

        // Check if they are already friends
        if (friendshipRepository.areFriends(senderId, receiverId)) {
            throw DuplicateResourceException("friendship", "You are already friends with this user")
        }

        // Check if there's already a pending request between them
        if (friendRequestRepository.existsPendingRequestBetweenUsers(senderId, receiverId)) {
            throw DuplicateResourceException(
                "friendRequest",
                "A pending friend request already exists between you and this user",
            )
        }

        val friendRequest =
            FriendRequest(
                senderId = senderId,
                receiverId = receiverId,
                status = FriendRequestStatus.PENDING,
                message = message,
            )

        val savedRequest = friendRequestRepository.save(friendRequest)
        return FriendRequestDto.fromEntity(savedRequest)
    }

    /**
     * Respond to a friend request (accept or reject)
     */
    @Transactional
    fun respondToFriendRequest(
        requestId: UUID,
        responderId: UUID,
        accept: Boolean,
    ): FriendRequestDto {
        val request =
            friendRequestRepository
                .findById(requestId)
                .orElseThrow { NoSuchElementException("Friend request not found") }

        // Only the receiver can respond to the request
        if (request.receiverId != responderId) {
            throw UnauthorizedActionException("You can only respond to friend requests sent to you")
        }

        // Can only respond to pending requests
        if (request.status != FriendRequestStatus.PENDING) {
            throw IllegalStateException("This friend request has already been ${request.status.name.lowercase()}")
        }

        request.status = if (accept) FriendRequestStatus.ACCEPTED else FriendRequestStatus.REJECTED
        request.updatedAt = OffsetDateTime.now()

        val updatedRequest = friendRequestRepository.save(request)

        // If accepted, create the friendship
        if (accept) {
            val friendship = Friendship.create(request.senderId, request.receiverId)
            friendshipRepository.save(friendship)
        }

        return FriendRequestDto.fromEntity(updatedRequest)
    }

    /**
     * Cancel a sent friend request
     */
    @Transactional
    fun cancelFriendRequest(
        requestId: UUID,
        senderId: UUID,
    ): FriendRequestDto {
        val request =
            friendRequestRepository
                .findById(requestId)
                .orElseThrow { NoSuchElementException("Friend request not found") }

        // Only the sender can cancel the request
        if (request.senderId != senderId) {
            throw UnauthorizedActionException("You can only cancel friend requests you sent")
        }

        // Can only cancel pending requests
        if (request.status != FriendRequestStatus.PENDING) {
            throw IllegalStateException("This friend request has already been ${request.status.name.lowercase()}")
        }

        request.status = FriendRequestStatus.CANCELLED
        request.updatedAt = OffsetDateTime.now()

        val updatedRequest = friendRequestRepository.save(request)
        return FriendRequestDto.fromEntity(updatedRequest)
    }

    /**
     * Get pending friend requests received by a user
     */
    fun getPendingReceivedRequests(
        userId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<FriendRequestWithUserDto> {
        val pageRequest =
            PageRequest.of(
                maxOf(0, page),
                size.coerceIn(1, 100),
                Sort.by("createdAt").descending(),
            )

        val requestPage =
            friendRequestRepository.findByReceiverIdAndStatus(
                userId,
                FriendRequestStatus.PENDING,
                pageRequest,
            )

        return PageResponse.fromPage(requestPage) { request ->
            val sender = userRepository.findById(request.senderId).orElse(null)
            val receiver = userRepository.findById(request.receiverId).orElse(null)
            FriendRequestWithUserDto(
                requestId = request.requestId!!,
                sender = UserDto.fromEntity(sender),
                receiver = UserDto.fromEntity(receiver),
                status = request.status,
                createdAt = request.createdAt,
                message = request.message,
            )
        }
    }

    /**
     * Get pending friend requests sent by a user
     */
    fun getPendingSentRequests(
        userId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<FriendRequestWithUserDto> {
        val pageRequest =
            PageRequest.of(
                maxOf(0, page),
                size.coerceIn(1, 100),
                Sort.by("createdAt").descending(),
            )

        val requestPage =
            friendRequestRepository.findBySenderIdAndStatus(
                userId,
                FriendRequestStatus.PENDING,
                pageRequest,
            )

        return PageResponse.fromPage(requestPage) { request ->
            val sender = userRepository.findById(request.senderId).orElse(null)
            val receiver = userRepository.findById(request.receiverId).orElse(null)
            FriendRequestWithUserDto(
                requestId = request.requestId!!,
                sender = UserDto.fromEntity(sender),
                receiver = UserDto.fromEntity(receiver),
                status = request.status,
                createdAt = request.createdAt,
                message = request.message,
            )
        }
    }

    // ============ Friendship Operations ============

    /**
     * Get all friends for a user
     */
    fun getFriends(
        userId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<FriendDto> {
        val pageRequest =
            PageRequest.of(
                maxOf(0, page),
                size.coerceIn(1, 100),
                Sort.by("createdAt").descending(),
            )

        val friendshipPage = friendshipRepository.findAllByUserId(userId, pageRequest)

        return PageResponse.fromPage(friendshipPage) { friendship ->
            val friendId = if (friendship.user1Id == userId) friendship.user2Id else friendship.user1Id
            val friend = userRepository.findById(friendId).orElse(null)
            FriendDto(
                user = UserDto.fromEntity(friend),
                friendsSince = friendship.createdAt,
            )
        }
    }

    /**
     * Remove a friend
     */
    @Transactional
    fun removeFriend(
        userId: UUID,
        friendId: UUID,
    ): Boolean {
        val friendship =
            friendshipRepository
                .findBetweenUsers(userId, friendId)
                .orElseThrow { NoSuchElementException("Friendship not found") }

        friendshipRepository.delete(friendship)
        return true
    }

    /**
     * Check if two users are friends
     */
    fun areFriends(
        userId1: UUID,
        userId2: UUID,
    ): Boolean = friendshipRepository.areFriends(userId1, userId2)

    /**
     * Get friend statistics for a user
     */
    fun getFriendStats(userId: UUID): FriendStatsDto {
        val friendCount = friendshipRepository.countFriends(userId).toInt()
        val pendingReceived =
            friendRequestRepository
                .countByReceiverIdAndStatus(
                    userId,
                    FriendRequestStatus.PENDING,
                ).toInt()
        val pendingSent =
            friendRequestRepository
                .countBySenderIdAndStatus(
                    userId,
                    FriendRequestStatus.PENDING,
                ).toInt()

        return FriendStatsDto(
            friendCount = friendCount,
            pendingRequestsReceived = pendingReceived,
            pendingRequestsSent = pendingSent,
        )
    }

    // ============ Privacy/Visibility Operations ============

    /**
     * Check if a viewer can see a user's profile based on privacy settings
     */
    fun canViewProfile(
        viewerId: UUID?,
        targetUserId: UUID,
    ): Boolean {
        // Users can always view their own profile
        if (viewerId == targetUserId) {
            return true
        }

        val targetProfile =
            userProfileRepository.findById(targetUserId).orElse(null)
                ?: return false

        val visibility = targetProfile.profileVisibility

        return when (visibility) {
            ProfileVisibility.PUBLIC.name -> true
            ProfileVisibility.PRIVATE.name -> false
            ProfileVisibility.FRIENDS_ONLY.name -> {
                viewerId != null && friendshipRepository.areFriends(viewerId, targetUserId)
            }
            ProfileVisibility.FRIENDS_OF_FRIENDS.name -> {
                if (viewerId == null) return false
                if (friendshipRepository.areFriends(viewerId, targetUserId)) return true

                // Check if viewer is a friend of a friend
                val viewerFriends = friendshipRepository.findFriendIds(viewerId).toSet()
                val targetFriends = friendshipRepository.findFriendIds(targetUserId).toSet()

                // If there's any common friend, viewer is a friend of friend
                viewerFriends.intersect(targetFriends).isNotEmpty()
            }
            else -> {
                // Legacy boolean handling - treat "true" as PUBLIC, "false" as PRIVATE
                visibility.equals("true", ignoreCase = true)
            }
        }
    }

    /**
     * Get a list of friends in common with another user
     */
    fun getMutualFriends(
        userId: UUID,
        otherUserId: UUID,
    ): List<UserDto> {
        val userFriends = friendshipRepository.findFriendIds(userId).toSet()
        val otherFriends = friendshipRepository.findFriendIds(otherUserId).toSet()

        val mutualFriendIds = userFriends.intersect(otherFriends)

        return mutualFriendIds.mapNotNull { friendId ->
            userRepository.findById(friendId).map { UserDto.fromEntity(it) }.orElse(null)
        }
    }

    /**
     * Get friends of friends (for discovery/suggestions)
     */
    fun getFriendsOfFriends(
        userId: UUID,
        page: Int,
        size: Int,
    ): List<UserDto> {
        val directFriendIds = friendshipRepository.findFriendIds(userId).toSet()

        val friendsOfFriendsIds =
            directFriendIds
                .flatMap { friendId ->
                    friendshipRepository.findFriendIds(friendId)
                }.toSet() - directFriendIds - userId // Exclude direct friends and self

        val startIndex = page * size
        val endIndex = minOf(startIndex + size, friendsOfFriendsIds.size)

        if (startIndex >= friendsOfFriendsIds.size) {
            return emptyList()
        }

        return friendsOfFriendsIds
            .toList()
            .subList(startIndex, endIndex)
            .mapNotNull { friendId ->
                userRepository.findById(friendId).map { UserDto.fromEntity(it) }.orElse(null)
            }
    }

    /**
     * Get the friendship status between two users
     */
    fun getFriendshipStatus(
        userId: UUID,
        otherUserId: UUID,
    ): String {
        if (friendshipRepository.areFriends(userId, otherUserId)) {
            return "FRIENDS"
        }

        val pendingRequest =
            friendRequestRepository.findBetweenUsersWithStatus(
                userId,
                otherUserId,
                FriendRequestStatus.PENDING,
            )

        return when {
            pendingRequest.isEmpty -> "NONE"
            pendingRequest.get().senderId == userId -> "REQUEST_SENT"
            else -> "REQUEST_RECEIVED"
        }
    }
}
