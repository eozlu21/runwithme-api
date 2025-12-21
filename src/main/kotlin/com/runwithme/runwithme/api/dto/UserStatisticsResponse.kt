package com.runwithme.runwithme.api.dto

data class UserStatisticsResponse(
    val totalRuns: Int,
    val totalDistanceKm: Double,
    val averagePace: String, // e.g., "5:30 /km"
    val averageDistancePerRunKm: Double,
    val runsPerWeek: Double,
    val kmPerWeek: Double,
    val allTimeDistanceKm: Double,
    val allTimeTotalRuns: Int,
    val periodDays: Int?, // null if all-time stats
)
