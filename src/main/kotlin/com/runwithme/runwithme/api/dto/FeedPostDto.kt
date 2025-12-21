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
) {
    companion object {
        fun fromEntity(
            post: FeedPost,
            likeCount: Long? = null,
            commentCount: Long? = null,
            isLikedByCurrentUser: Boolean? = null,
            linkedRouteId: Long? = null,
            linkedRunSessionId: Long? = null,
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
