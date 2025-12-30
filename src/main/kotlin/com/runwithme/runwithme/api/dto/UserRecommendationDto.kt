package com.runwithme.runwithme.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "Location filter level for user recommendations")
enum class LocationFilterLevel {
    CITY,
    COUNTRY,
    NONE,
}

@Schema(description = "User recommendation with similarity scores")
data class UserRecommendationDto(
    @Schema(description = "Recommended user's ID")
    val userId: UUID,
    @Schema(description = "Username")
    val username: String,
    @Schema(description = "Profile picture URL")
    val profilePic: String?,
    @Schema(description = "Combined similarity score (0-100)", example = "78.5")
    val combinedScore: Double,
    @Schema(description = "Route similarity score (0-100)", example = "85.0")
    val routeSimilarityScore: Double,
    @Schema(description = "Preference similarity score (0-100)", example = "72.0")
    val preferenceSimilarityScore: Double,
    @Schema(description = "Number of route pairs compared")
    val routePairCount: Int,
    @Schema(description = "Whether this user has routes")
    val hasRoutes: Boolean,
    @Schema(description = "Whether this user has completed the survey")
    val hasSurvey: Boolean,
    @Schema(description = "Whether this user is already a friend")
    val isFriend: Boolean,
)

@Schema(description = "Detailed similarity breakdown with another user")
data class UserSimilarityDetailDto(
    @Schema(description = "Target user's ID")
    val targetUserId: UUID,
    @Schema(description = "Target user's username")
    val targetUsername: String,
    @Schema(description = "Combined similarity score (0-100)")
    val combinedScore: Double,
    @Schema(description = "Route similarity breakdown")
    val routeSimilarity: RouteSimilarityBreakdown,
    @Schema(description = "Preference similarity breakdown by field")
    val preferenceSimilarity: Map<String, PreferenceMatchDetail>,
    @Schema(description = "Whether the target user is already a friend")
    val isFriend: Boolean,
)

@Schema(description = "Route similarity breakdown details")
data class RouteSimilarityBreakdown(
    @Schema(description = "Average similarity across all route pairs (0-100)")
    val averageScore: Double,
    @Schema(description = "Number of route pairs compared")
    val pairCount: Int,
    @Schema(description = "Top most similar route pairs")
    val topPairs: List<RoutePairSimilarity>,
)

@Schema(description = "Similarity between two routes")
data class RoutePairSimilarity(
    @Schema(description = "First route ID")
    val route1Id: Long,
    @Schema(description = "Second route ID")
    val route2Id: Long,
    @Schema(description = "Similarity score (0-100)")
    val similarity: Double,
)

@Schema(description = "Preference match detail for a single field")
data class PreferenceMatchDetail(
    @Schema(description = "Requesting user's value")
    val user1Value: String,
    @Schema(description = "Target user's value")
    val user2Value: String,
    @Schema(description = "Whether the values match")
    val isMatch: Boolean,
)
