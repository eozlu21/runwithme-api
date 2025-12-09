package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.FriendDto
import com.runwithme.runwithme.api.dto.FriendRequestDto
import com.runwithme.runwithme.api.dto.FriendRequestWithUserDto
import com.runwithme.runwithme.api.dto.FriendStatsDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.RespondToFriendRequestDto
import com.runwithme.runwithme.api.dto.SendFriendRequestDto
import com.runwithme.runwithme.api.dto.UserDto
import com.runwithme.runwithme.api.service.FriendshipService
import com.runwithme.runwithme.api.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/friends")
@Tag(name = "Friendships", description = "Friend management APIs")
class FriendshipController(
    private val friendshipService: FriendshipService,
    private val userService: UserService,
) {
    // ============ Friend Request Endpoints ============

    @PostMapping("/requests")
    @Operation(
        summary = "Send a friend request",
        description = "Send a friend request to another user",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "Friend request sent successfully",
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "Invalid request or already friends",
                ),
                ApiResponse(responseCode = "404", description = "User not found"),
                ApiResponse(
                    responseCode = "409",
                    description = "Friend request already exists",
                ),
            ],
    )
    fun sendFriendRequest(
        @RequestBody request: SendFriendRequestDto,
        principal: Principal,
    ): ResponseEntity<FriendRequestDto> {
        val userId = getUserIdFromPrincipal(principal)
        val result =
            friendshipService.sendFriendRequest(
                senderId = userId,
                receiverId = request.receiverId,
                message = request.message,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @GetMapping("/requests/received")
    @Operation(
        summary = "Get received friend requests",
        description = "Get all pending friend requests received by the authenticated user",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved friend requests",
                ),
            ],
    )
    fun getReceivedRequests(
        @Parameter(description = "Page number (0-indexed)")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "10") size: Int,
        principal: Principal,
    ): ResponseEntity<PageResponse<FriendRequestWithUserDto>> {
        val userId = getUserIdFromPrincipal(principal)
        val result = friendshipService.getPendingReceivedRequests(userId, page, size)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/requests/sent")
    @Operation(
        summary = "Get sent friend requests",
        description = "Get all pending friend requests sent by the authenticated user",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved friend requests",
                ),
            ],
    )
    fun getSentRequests(
        @Parameter(description = "Page number (0-indexed)")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "10") size: Int,
        principal: Principal,
    ): ResponseEntity<PageResponse<FriendRequestWithUserDto>> {
        val userId = getUserIdFromPrincipal(principal)
        val result = friendshipService.getPendingSentRequests(userId, page, size)
        return ResponseEntity.ok(result)
    }

    @PutMapping("/requests/{requestId}/respond")
    @Operation(
        summary = "Respond to a friend request",
        description = "Accept or reject a pending friend request",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Response recorded successfully",
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "Request already processed",
                ),
                ApiResponse(
                    responseCode = "403",
                    description = "Not authorized to respond to this request",
                ),
                ApiResponse(
                    responseCode = "404",
                    description = "Friend request not found",
                ),
            ],
    )
    fun respondToRequest(
        @PathVariable requestId: UUID,
        @RequestBody response: RespondToFriendRequestDto,
        principal: Principal,
    ): ResponseEntity<FriendRequestDto> {
        val userId = getUserIdFromPrincipal(principal)
        val result =
            friendshipService.respondToFriendRequest(
                requestId,
                userId,
                response.status,
            )
        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/requests/{requestId}")
    @Operation(
        summary = "Cancel a friend request",
        description = "Cancel a pending friend request that you sent",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Friend request cancelled",
                ),
                ApiResponse(
                    responseCode = "403",
                    description = "Not authorized to cancel this request",
                ),
                ApiResponse(
                    responseCode = "404",
                    description = "Friend request not found",
                ),
            ],
    )
    fun cancelRequest(
        @PathVariable requestId: UUID,
        principal: Principal,
    ): ResponseEntity<FriendRequestDto> {
        val userId = getUserIdFromPrincipal(principal)
        val result = friendshipService.cancelFriendRequest(requestId, userId)
        return ResponseEntity.ok(result)
    }

    // ============ Friendship Endpoints ============

    @GetMapping
    @Operation(
        summary = "Get friends list",
        description = "Get all friends of the authenticated user",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved friends list",
                ),
            ],
    )
    fun getFriends(
        @Parameter(description = "Page number (0-indexed)")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "10") size: Int,
        principal: Principal,
    ): ResponseEntity<PageResponse<FriendDto>> {
        val userId = getUserIdFromPrincipal(principal)
        val result = friendshipService.getFriends(userId, page, size)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Get a user's friends",
        description = "Get friends of a specific user (subject to privacy settings)",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved friends list",
                ),
                ApiResponse(
                    responseCode = "403",
                    description = "Not authorized to view this user's friends",
                ),
                ApiResponse(responseCode = "404", description = "User not found"),
            ],
    )
    fun getUserFriends(
        @PathVariable userId: UUID,
        @Parameter(description = "Page number (0-indexed)")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "10") size: Int,
        principal: Principal?,
    ): ResponseEntity<PageResponse<FriendDto>> {
        val viewerId = principal?.let { getUserIdFromPrincipal(it) }

        if (!friendshipService.canViewProfile(viewerId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val result = friendshipService.getFriends(userId, page, size)
        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/{friendId}")
    @Operation(
        summary = "Remove a friend",
        description = "Remove a friend from your friends list",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "204",
                    description = "Friend removed successfully",
                ),
                ApiResponse(responseCode = "404", description = "Friendship not found"),
            ],
    )
    fun removeFriend(
        @PathVariable friendId: UUID,
        principal: Principal,
    ): ResponseEntity<Void> {
        val userId = getUserIdFromPrincipal(principal)
        friendshipService.removeFriend(userId, friendId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/status/{otherUserId}")
    @Operation(
        summary = "Get friendship status",
        description = "Get the friendship status between you and another user",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Status retrieved",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            String::class,
                                    ),
                            ),
                        ],
                ),
            ],
    )
    fun getFriendshipStatus(
        @PathVariable otherUserId: UUID,
        principal: Principal,
    ): ResponseEntity<Map<String, String>> {
        val userId = getUserIdFromPrincipal(principal)
        val status = friendshipService.getFriendshipStatus(userId, otherUserId)
        return ResponseEntity.ok(mapOf("status" to status))
    }

    @GetMapping("/stats")
    @Operation(
        summary = "Get friend statistics",
        description = "Get statistics about your friends and friend requests",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Statistics retrieved successfully",
                ),
            ],
    )
    fun getFriendStats(principal: Principal): ResponseEntity<FriendStatsDto> {
        val userId = getUserIdFromPrincipal(principal)
        val stats = friendshipService.getFriendStats(userId)
        return ResponseEntity.ok(stats)
    }

    @GetMapping("/mutual/{otherUserId}")
    @Operation(
        summary = "Get mutual friends",
        description = "Get friends in common with another user",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Mutual friends retrieved successfully",
                ),
            ],
    )
    fun getMutualFriends(
        @PathVariable otherUserId: UUID,
        principal: Principal,
    ): ResponseEntity<List<UserDto>> {
        val userId = getUserIdFromPrincipal(principal)
        val mutualFriends = friendshipService.getMutualFriends(userId, otherUserId)
        return ResponseEntity.ok(mutualFriends)
    }

    @GetMapping("/suggestions")
    @Operation(
        summary = "Get friend suggestions",
        description = "Get suggested friends (friends of friends)",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Suggestions retrieved successfully",
                ),
            ],
    )
    fun getFriendSuggestions(
        @Parameter(description = "Page number (0-indexed)")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "10") size: Int,
        principal: Principal,
    ): ResponseEntity<List<UserDto>> {
        val userId = getUserIdFromPrincipal(principal)
        val suggestions = friendshipService.getFriendsOfFriends(userId, page, size)
        return ResponseEntity.ok(suggestions)
    }

    // ============ Helper Methods ============

    private fun getUserIdFromPrincipal(principal: Principal): UUID {
        val user =
            userService.getUserByUsername(principal.name)
                ?: throw NoSuchElementException("User not found")
        return user.userId
    }
}
