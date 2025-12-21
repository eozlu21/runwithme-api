package com.runwithme.runwithme.api.dto

data class UserStatisticsResponse(
    val averagePace: String, // e.g., "5:30 /km"
    val runsPerWeek: Double,
    val kmPerWeek: Double,
    val allTimeDistanceKm: Double,
)
