package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "feed_post_run_sessions")
open class FeedPostRunSession(
    @Id
    @Column(name = "post_id")
    open var postId: Long = 0,
    @Column(name = "run_session_id")
    open var runSessionId: Long = 0,
    @Column(name = "created_at")
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
