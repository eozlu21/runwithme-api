package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "feed_post_routes")
open class FeedPostRoute(
    @Id
    @Column(name = "post_id")
    open var postId: Long = 0,
    @Column(name = "route_id")
    open var routeId: Long = 0,
    @Column(name = "created_at")
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
