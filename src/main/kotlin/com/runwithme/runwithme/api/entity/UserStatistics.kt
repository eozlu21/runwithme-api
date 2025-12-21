package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "user_statistics")
open class UserStatistics(
    @Id
    @Column(name = "user_id")
    open var userId: UUID? = null,
    @Column(name = "total_distance_meters")
    open var totalDistanceMeters: Double = 0.0,
    @Column(name = "total_runs")
    open var totalRuns: Int = 0,
    @Column(name = "total_moving_time_seconds")
    open var totalMovingTimeSeconds: Long = 0,
    @Column(name = "first_run_date")
    open var firstRunDate: OffsetDateTime? = null,
    @Column(name = "last_run_date")
    open var lastRunDate: OffsetDateTime? = null,
)
