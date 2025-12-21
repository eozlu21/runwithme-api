package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.UserStatisticsResponse
import com.runwithme.runwithme.api.entity.UserStatistics
import com.runwithme.runwithme.api.repository.RunSessionRepository
import com.runwithme.runwithme.api.repository.UserStatisticsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class UserStatisticsService(
    private val userStatisticsRepository: UserStatisticsRepository,
    private val runSessionRepository: RunSessionRepository,
) {
    @Transactional
    fun updateStatistics(userId: UUID) {
        val runs = runSessionRepository.findAllByUserId(userId)

        if (runs.isEmpty()) {
            val stats = userStatisticsRepository.findById(userId).orElse(UserStatistics(userId = userId))
            stats.totalDistanceMeters = 0.0
            stats.totalRuns = 0
            stats.totalMovingTimeSeconds = 0
            stats.firstRunDate = null
            stats.lastRunDate = null
            userStatisticsRepository.save(stats)
            return
        }

        val totalDistance = runs.sumOf { it.totalDistanceM ?: 0.0 }
        val totalRuns = runs.size
        val totalMovingTime = runs.sumOf { it.movingTimeS?.toLong() ?: 0L }
        val firstRunDate = runs.mapNotNull { it.startedAt }.minOrNull()
        val lastRunDate = runs.mapNotNull { it.startedAt }.maxOrNull()

        val stats = userStatisticsRepository.findById(userId).orElse(UserStatistics(userId = userId))
        stats.totalDistanceMeters = totalDistance
        stats.totalRuns = totalRuns
        stats.totalMovingTimeSeconds = totalMovingTime
        stats.firstRunDate = firstRunDate
        stats.lastRunDate = lastRunDate

        userStatisticsRepository.save(stats)
    }

    fun getUserStatistics(userId: UUID): UserStatisticsResponse {
        val stats =
            userStatisticsRepository.findById(userId).orElseGet {
                // If not found, try to calculate it once
                updateStatistics(userId)
                userStatisticsRepository.findById(userId).orElse(UserStatistics(userId = userId))
            }

        val allTimeDistanceKm = stats.totalDistanceMeters / 1000.0

        // Average Pace: min/km
        // totalMovingTime (s) / totalDistance (km) = s/km
        val avgPaceSecPerKm =
            if (stats.totalDistanceMeters > 0) {
                stats.totalMovingTimeSeconds.toDouble() / (stats.totalDistanceMeters / 1000.0)
            } else {
                0.0
            }
        val avgPaceMin = (avgPaceSecPerKm / 60).toInt()
        val avgPaceSec = (avgPaceSecPerKm % 60).toInt()
        val averagePace = String.format("%d:%02d /km", avgPaceMin, avgPaceSec)

        // Weeks active
        val weeksActive =
            if (stats.firstRunDate != null) {
                val days = ChronoUnit.DAYS.between(stats.firstRunDate, OffsetDateTime.now())
                if (days < 7) 1.0 else days / 7.0
            } else {
                1.0
            }

        val runsPerWeek = stats.totalRuns / weeksActive
        val kmPerWeek = allTimeDistanceKm / weeksActive

        return UserStatisticsResponse(
            averagePace = averagePace,
            runsPerWeek = String.format("%.1f", runsPerWeek).toDouble(),
            kmPerWeek = String.format("%.1f", kmPerWeek).toDouble(),
            allTimeDistanceKm = String.format("%.1f", allTimeDistanceKm).toDouble(),
        )
    }
}
