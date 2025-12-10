package com.runwithme.runwithme.api.entity

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.locationtech.jts.geom.LineString
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "run_sessions")
open class RunSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    open var id: Long? = null,
    @Column(name = "user_id")
    open var userId: UUID? = null,
    @Column(name = "route_id")
    open var routeId: Long? = null,
    @Column(name = "is_public", nullable = false)
    @get:JsonProperty("isPublic")
    open var isPublic: Boolean = false,
    @Column(name = "started_at")
    open var startedAt: OffsetDateTime? = null,
    @Column(name = "ended_at")
    open var endedAt: OffsetDateTime? = null,
    @Column(name = "moving_time_s")
    open var movingTimeS: Int? = null,
    @Column(name = "geom_track", columnDefinition = "geography(LineString,4326)")
    open var geomTrack: LineString? = null,
    @Column(name = "created_at")
    open var createdAt: OffsetDateTime? = OffsetDateTime.now(),
    @Column(name = "elevation_gain_m")
    open var elevationGainM: Double? = null,
    @Column(name = "avg_pace_sec_per_km")
    open var avgPaceSecPerKm: Double? = null,
    @Column(name = "total_distance_m")
    open var totalDistanceM: Double? = null,
)
