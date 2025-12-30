package com.runwithme.runwithme.api.controller

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
