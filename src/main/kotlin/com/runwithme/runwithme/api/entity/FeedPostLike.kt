package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.OffsetDateTime
import java.util.UUID

@Embeddable
data class FeedPostLikeId(
    @Column(name = "post_id")
    var postId: Long = 0,
    @Column(name = "user_id")
    var userId: UUID = UUID(0, 0),
) : Serializable

@Entity
@Table(name = "feed_post_likes")
open class FeedPostLike(
    @EmbeddedId
    open var id: FeedPostLikeId = FeedPostLikeId(),
    @Column(name = "created_at")
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
