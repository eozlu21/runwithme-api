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
data class RouteLikeId(
    @Column(name = "route_id")
    var routeId: Long = 0,
    @Column(name = "user_id")
    var userId: UUID = UUID(0, 0),
) : Serializable

@Entity
@Table(name = "route_likes")
open class RouteLike(
    @EmbeddedId
    open var id: RouteLikeId = RouteLikeId(),
    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
