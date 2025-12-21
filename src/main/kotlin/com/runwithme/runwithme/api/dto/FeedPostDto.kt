package com.runwithme.runwithme.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.runwithme.runwithme.api.entity.FeedPost
import com.runwithme.runwithme.api.entity.PostType
import com.runwithme.runwithme.api.entity.PostVisibility
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Feed post data transfer object")
data class FeedPostDto(
    @Schema(description = "Post unique identifier", example = "1")
    val id: Long,
    @Schema(description = "ID of the user who created the post")
    val authorId: UUID?,
    @Schema(description = "Type of post", example = "TEXT")
    val postType: PostType?,
    @Schema(description = "Text content of the post", example = "Just finished my morning run!")
    val textContent: String?,
    @Schema(description = "URL to media attachment", example = "https://example.com/image.jpg")
    val mediaUrl: String?,
    @Schema(description = "Post visibility setting", example = "PUBLIC")
    val visibility: PostVisibility?,
    @Schema(description = "Creation timestamp")
    val createdAt: OffsetDateTime,
    @Schema(description = "Number of likes on this post", example = "42")
    @get:JsonProperty("likesCount")
    val likeCount: Long? = null,
    @Schema(description = "Number of comments on this post", example = "5")
    @get:JsonProperty("commentsCount")
    val commentCount: Long? = null,
    @Schema(description = "Whether the current user has liked this post")
    @get:JsonProperty("isLikedByCurrentUser")
    val isLikedByCurrentUser: Boolean? = null,
    @Schema(description = "Linked route ID from mapping table", example = "1")
    @get:JsonProperty("routeId")
    val linkedRouteId: Long? = null,
    @Schema(description = "Linked run session ID from mapping table", example = "1")
    @get:JsonProperty("runSessionId")
    val linkedRunSessionId: Long? = null,
    // Denormalized Route Fields
    @Schema(description = "Route distance in meters", example = "5000.0")
    val routeDistanceM: Double? = null,
    @Schema(description = "Route title", example = "Morning Run Route")
    val routeTitle: String? = null,
    @Schema(description = "Start point latitude", example = "41.0082")
    val startPointLat: Double? = null,
    @Schema(description = "Start point longitude", example = "28.9784")
    val startPointLon: Double? = null,
    @Schema(description = "End point latitude", example = "41.0150")
    val endPointLat: Double? = null,
    @Schema(description = "End point longitude", example = "28.9850")
    val endPointLon: Double? = null,
    @Schema(description = "List of route points")
    val routePoints: List<RoutePointDto>? = null,
    // Denormalized Run Session Fields
    @Schema(description = "Run distance in meters", example = "5000.0")
    val runDistanceM: Double? = null,
    @Schema(description = "Run duration in seconds", example = "1800")
    val runDurationS: Int? = null,
    @Schema(description = "Run pace in seconds per km", example = "360.0")
    val runPaceSecPerKm: Double? = null,
    @Schema(description = "List of run points")
    val runPoints: List<RunSessionPointDto>? = null,
) {
    companion object {
        fun fromEntity(
            post: FeedPost,
            likeCount: Long? = null,
            commentCount: Long? = null,
            isLikedByCurrentUser: Boolean? = null,
            linkedRouteId: Long? = null,
            linkedRunSessionId: Long? = null,
            routeDistanceM: Double? = null,
            routeTitle: String? = null,
            startPointLat: Double? = null,
            startPointLon: Double? = null,
            endPointLat: Double? = null,
            endPointLon: Double? = null,
            routePoints: List<RoutePointDto>? = null,
            runDistanceM: Double? = null,
            runDurationS: Int? = null,
            runPaceSecPerKm: Double? = null,
            runPoints: List<RunSessionPointDto>? = null,
        ): FeedPostDto =
            FeedPostDto(
                id = post.id!!,
                authorId = post.authorId,
                postType = post.postType,
                textContent = post.textContent,
                mediaUrl = post.mediaUrl,
                visibility = post.visibility,
                createdAt = post.createdAt,
                likeCount = likeCount,
                commentCount = commentCount,
                isLikedByCurrentUser = isLikedByCurrentUser,
                linkedRouteId = linkedRouteId,
                linkedRunSessionId = linkedRunSessionId,
                routeDistanceM = routeDistanceM,
                routeTitle = routeTitle,
                startPointLat = startPointLat,
                startPointLon = startPointLon,
                endPointLat = endPointLat,
                endPointLon = endPointLon,
                routePoints = routePoints,
                runDistanceM = runDistanceM,
                runDurationS = runDurationS,
                runPaceSecPerKm = runPaceSecPerKm,
                runPoints = runPoints,
            )
    }
}

@Schema(description = "Create feed post request")
data class CreateFeedPostRequest(
    @Schema(description = "Type of post", example = "TEXT", required = true)
    val postType: PostType,
    @Schema(description = "Text content of the post", example = "Just finished my morning run!")
    val textContent: String? = null,
    @Schema(description = "URL to media attachment", example = "https://example.com/image.jpg")
    val mediaUrl: String? = null,
    @Schema(description = "Post visibility setting", example = "PUBLIC")
    val visibility: PostVisibility = PostVisibility.PUBLIC,
    @Schema(description = "Route ID to link with the post (for ROUTE type posts)", example = "1")
    val routeId: Long? = null,
    @Schema(description = "Run session ID to link with the post (for RUN_SESSION type posts)", example = "1")
    val runSessionId: Long? = null,
)

@Schema(description = "Update feed post request")
data class UpdateFeedPostRequest(
    @Schema(description = "Text content of the post", example = "Updated my run status!")
    val textContent: String? = null,
    @Schema(description = "URL to media attachment", example = "https://example.com/newimage.jpg")
    val mediaUrl: String? = null,
    @Schema(description = "Post visibility setting", example = "FRIENDS")
    val visibility: PostVisibility? = null,
)
