package com.runwithme.runwithme.api.dto

import com.runwithme.runwithme.api.entity.FeedPostComment
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Feed post comment data transfer object")
data class FeedPostCommentDto(
    @Schema(description = "Comment unique identifier", example = "1")
    val id: Long,
    @Schema(description = "Post ID the comment belongs to", example = "1")
    val postId: Long?,
    @Schema(description = "ID of the user who created the comment")
    val userId: UUID?,
    @Schema(description = "Comment text content", example = "Great run!")
    val commentText: String?,
    @Schema(description = "When the comment was created")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun fromEntity(comment: FeedPostComment): FeedPostCommentDto =
            FeedPostCommentDto(
                id = comment.id!!,
                postId = comment.postId,
                userId = comment.userId,
                commentText = comment.commentText,
                createdAt = comment.createdAt,
            )
    }
}

@Schema(description = "Create comment request")
data class CreateCommentRequest(
    @Schema(description = "Comment text content", example = "Great run!", required = true)
    val commentText: String,
)

@Schema(description = "Update comment request")
data class UpdateCommentRequest(
    @Schema(description = "Updated comment text content", example = "Amazing run!")
    val commentText: String,
)
