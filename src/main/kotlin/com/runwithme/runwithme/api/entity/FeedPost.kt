package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

enum class PostType {
    TEXT,
    ROUTE,
    RUN_SESSION,
}

enum class PostVisibility {
    PUBLIC,
    FRIENDS,
    PRIVATE,
}

@Entity
@Table(name = "feed_posts")
open class FeedPost(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    open var id: Long? = null,
    @Column(name = "author_id")
    open var authorId: UUID? = null,
    @Column(name = "post_type")
    @Enumerated(EnumType.STRING)
    open var postType: PostType? = null,
    @Column(name = "text_content", columnDefinition = "TEXT")
    open var textContent: String? = null,
    @Column(name = "media_url", columnDefinition = "TEXT")
    open var mediaUrl: String? = null,
    @Column(name = "visibility")
    @Enumerated(EnumType.STRING)
    open var visibility: PostVisibility? = PostVisibility.PUBLIC,
    @Column(name = "created_at")
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
