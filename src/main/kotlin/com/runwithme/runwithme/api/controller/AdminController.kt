package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.service.SimilarityPrecomputeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administrative endpoints for system management")
class AdminController(
    private val similarityPrecomputeService: SimilarityPrecomputeService,
) {
    @PostMapping("/similarity/recompute")
    @Operation(
        summary = "Recompute all similarity scores",
        description = """
            Triggers a full recomputation of similarity scores for all user pairs.
            This is a long-running operation. The weekly scheduled task calls this automatically.

            WARNING: This clears the existing cache and recomputes everything.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Recomputation completed successfully"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "500", description = "Recomputation failed"),
        ],
    )
    fun recomputeAllSimilarities(): ResponseEntity<SimilarityRecomputeResponse> {
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
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Recomputation completed successfully"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "User not found"),
            ApiResponse(responseCode = "500", description = "Recomputation failed"),
        ],
    )
    fun recomputeUserSimilarities(
        @Parameter(description = "User ID to recompute similarities for")
        @PathVariable
        userId: UUID,
    ): ResponseEntity<SimilarityRecomputeResponse> {
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
