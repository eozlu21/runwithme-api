package com.runwithme.runwithme.api.dto

import com.runwithme.runwithme.api.entity.FeedPostLike
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Feed post like data transfer object")
data class FeedPostLikeDto(
    @Schema(description = "Post ID that was liked", example = "1")
    val postId: Long,
    @Schema(description = "ID of the user who liked the post")
    val userId: UUID,
    @Schema(description = "When the like was created")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun fromEntity(like: FeedPostLike): FeedPostLikeDto =
            FeedPostLikeDto(
                postId = like.id.postId,
                userId = like.id.userId,
                createdAt = like.createdAt,
            )
    }
}
