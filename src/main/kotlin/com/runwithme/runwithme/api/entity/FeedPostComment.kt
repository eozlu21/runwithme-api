package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "feed_post_comments")
open class FeedPostComment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    open var id: Long? = null,
    @Column(name = "post_id")
    open var postId: Long? = null,
    @Column(name = "user_id")
    open var userId: UUID? = null,
    @Column(name = "comment_text", columnDefinition = "TEXT")
    open var commentText: String? = null,
    @Column(name = "created_at")
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
