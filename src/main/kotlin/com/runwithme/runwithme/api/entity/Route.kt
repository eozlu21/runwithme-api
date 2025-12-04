package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "routes")
open class Route(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    open var id: Long? = null,
    @Column(name = "title", columnDefinition = "TEXT") open var title: String? = null,
    @Column(name = "description", columnDefinition = "TEXT")
    open var description: String? = null,
    @Column(name = "distance_m") open var distanceM: Double? = null,
    @Column(name = "estimated_duration_s") open var estimatedDurationS: Int? = null,
    @Column(name = "difficulty", columnDefinition = "TEXT") open var difficulty: String? = null,
    @Column(name = "is_public", nullable = false) open var isPublic: Boolean = true,
    @Column(name = "start_point", columnDefinition = "geography(Point,4326)")
    open var startPoint: Point? = null,
    @Column(name = "end_point", columnDefinition = "geography(Point,4326)")
    open var endPoint: Point? = null,
    @Column(name = "path_geom", columnDefinition = "geography(LineString,4326)")
    open var pathGeom: LineString? = null,
    @Column(name = "creator_id") open var creatorId: UUID? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
