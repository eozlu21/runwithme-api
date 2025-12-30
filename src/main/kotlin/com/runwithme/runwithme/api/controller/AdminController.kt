package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.service.FriendshipService
import com.runwithme.runwithme.api.service.SimilarityPrecomputeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administrative endpoints for system management (localhost only)")
class AdminController(
    private val similarityPrecomputeService: SimilarityPrecomputeService,
    private val friendshipService: FriendshipService,
) {
    companion object {
        private val LOCALHOST_ADDRESSES = setOf("127.0.0.1", "0:0:0:0:0:0:0:1", "::1", "localhost")
    }

    /**
     * Check if the request is from a local/internal source.
     * Allows:
     * - Standard localhost addresses (127.0.0.1, ::1)
     * - Docker internal networks (172.x.x.x, 10.x.x.x, 192.168.x.x)
     * - Requests where remote and local address match (same machine)
     */
    private fun isLocalRequest(request: HttpServletRequest): Boolean {
        val remoteAddr = request.remoteAddr
        val localAddr = request.localAddr

        // Standard localhost check
        if (remoteAddr in LOCALHOST_ADDRESSES) {
            return true
        }

        // Same machine check (request originated from the same host)
        if (remoteAddr == localAddr) {
            return true
        }

        // Docker/internal network ranges (private IP addresses)
        if (isPrivateIpAddress(remoteAddr)) {
            return true
        }

        return false
    }

    private fun isPrivateIpAddress(ip: String): Boolean =
        ip.startsWith("10.") ||
            ip.startsWith("172.") ||
            ip.startsWith("192.168.") ||
            ip.startsWith("fc00:") ||
            ip.startsWith("fd")

    @PostMapping("/similarity/recompute")
    @Operation(
        summary = "Recompute all similarity scores",
        description = """
            Triggers a full recomputation of similarity scores for all user pairs.
            This is a long-running operation. The daily scheduled task calls this automatically.

            WARNING: This clears the existing cache and recomputes everything.
            NOTE: This endpoint can only be called from localhost.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Recomputation completed successfully"),
            ApiResponse(responseCode = "403", description = "Forbidden - not a local request"),
            ApiResponse(responseCode = "500", description = "Recomputation failed"),
        ],
    )
    fun recomputeAllSimilarities(request: HttpServletRequest): ResponseEntity<SimilarityRecomputeResponse> {
        if (!isLocalRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                SimilarityRecomputeResponse(
                    success = false,
                    message = "This endpoint can only be called from localhost",
                    usersProcessed = 0,
                    pairsComputed = 0,
                    pairsFailed = 0,
                ),
            )
        }

        val result = similarityPrecomputeService.computeAllSimilarities()
        return ResponseEntity.ok(
            SimilarityRecomputeResponse(
                success = true,
                message = "Full similarity recomputation completed",
                usersProcessed = result.usersProcessed,
                pairsComputed = result.pairsComputed,
                pairsFailed = result.pairsFailed,
            ),
        )
    }

    @PostMapping("/similarity/recompute/{userId}")
    @Operation(
        summary = "Recompute similarity scores for a specific user",
        description = """
            Triggers recomputation of similarity scores for a specific user with all other users.
            Useful when a user updates their profile/routes significantly.
            NOTE: This endpoint can only be called from localhost.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Recomputation completed successfully"),
            ApiResponse(responseCode = "403", description = "Forbidden - not a local request"),
            ApiResponse(responseCode = "404", description = "User not found"),
            ApiResponse(responseCode = "500", description = "Recomputation failed"),
        ],
    )
    fun recomputeUserSimilarities(
        @Parameter(description = "User ID to recompute similarities for")
        @PathVariable
        userId: UUID,
        request: HttpServletRequest,
    ): ResponseEntity<SimilarityRecomputeResponse> {
        if (!isLocalRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                SimilarityRecomputeResponse(
                    success = false,
                    message = "This endpoint can only be called from localhost",
                    usersProcessed = 0,
                    pairsComputed = 0,
                    pairsFailed = 0,
                ),
            )
        }

        val result = similarityPrecomputeService.computeSimilaritiesForUser(userId)
        return ResponseEntity.ok(
            SimilarityRecomputeResponse(
                success = true,
                message = "User similarity recomputation completed for $userId",
                usersProcessed = result.usersProcessed,
                pairsComputed = result.pairsComputed,
                pairsFailed = result.pairsFailed,
            ),
        )
    }

    // ============ MCP Friendship Operations ============

    @PostMapping("/mcp/friend-all")
    @Operation(
        summary = "Make all users friends with MCP",
        description = """
            Creates friendships between all existing users and the MCP user.
            Skips users who are already friends with MCP.
            NOTE: This endpoint can only be called from localhost.
            PREREQUISITE: A user with username 'MCP' must exist.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Operation completed successfully"),
            ApiResponse(responseCode = "403", description = "Forbidden - not a local request"),
            ApiResponse(responseCode = "404", description = "MCP user not found"),
        ],
    )
    fun makeAllUsersFriendsWithMcp(request: HttpServletRequest): ResponseEntity<McpFriendshipResponse> {
        if (!isLocalRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                McpFriendshipResponse(
                    success = false,
                    message = "This endpoint can only be called from localhost",
                    friendshipsCreated = 0,
                ),
            )
        }

        return try {
            val count = friendshipService.makeAllUsersFriendsWithMcp()
            ResponseEntity.ok(
                McpFriendshipResponse(
                    success = true,
                    message = "Successfully made $count users friends with MCP",
                    friendshipsCreated = count,
                ),
            )
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                McpFriendshipResponse(
                    success = false,
                    message = e.message ?: "MCP user not found",
                    friendshipsCreated = 0,
                ),
            )
        }
    }

    @PostMapping("/mcp/friend/{userId}")
    @Operation(
        summary = "Make a specific user friends with MCP",
        description = """
            Creates a friendship between the specified user and the MCP user.
            Useful for automatically friending new users with MCP upon registration.
            NOTE: This endpoint can only be called from localhost.
            PREREQUISITE: A user with username 'MCP' must exist.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Operation completed successfully"),
            ApiResponse(responseCode = "403", description = "Forbidden - not a local request"),
            ApiResponse(responseCode = "404", description = "MCP user not found or user not found"),
        ],
    )
    fun makeUserFriendsWithMcp(
        @Parameter(description = "User ID to make friends with MCP")
        @PathVariable
        userId: UUID,
        request: HttpServletRequest,
    ): ResponseEntity<McpFriendshipResponse> {
        if (!isLocalRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                McpFriendshipResponse(
                    success = false,
                    message = "This endpoint can only be called from localhost",
                    friendshipsCreated = 0,
                ),
            )
        }

        val created = friendshipService.makeFriendsWithMcp(userId)
        return if (created) {
            ResponseEntity.ok(
                McpFriendshipResponse(
                    success = true,
                    message = "Successfully made user $userId friends with MCP",
                    friendshipsCreated = 1,
                ),
            )
        } else {
            ResponseEntity.ok(
                McpFriendshipResponse(
                    success = true,
                    message = "User $userId is already friends with MCP or MCP user not found",
                    friendshipsCreated = 0,
                ),
            )
        }
    }
}

/**
 * Response DTO for similarity recomputation operations
 */
data class SimilarityRecomputeResponse(
    val success: Boolean,
    val message: String,
    val usersProcessed: Int,
    val pairsComputed: Int,
    val pairsFailed: Int,
)

/**
 * Response DTO for MCP friendship operations
 */
data class McpFriendshipResponse(
    val success: Boolean,
    val message: String,
    val friendshipsCreated: Int,
)
