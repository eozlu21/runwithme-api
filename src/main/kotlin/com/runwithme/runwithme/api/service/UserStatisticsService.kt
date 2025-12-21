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

    fun getUserStatistics(
        userId: UUID,
        days: Int? = null,
    ): UserStatisticsResponse {
        // Always ensure all-time stats are up to date
        val allTimeStats =
            userStatisticsRepository.findById(userId).orElseGet {
                updateStatistics(userId)
                userStatisticsRepository.findById(userId).orElse(UserStatistics(userId = userId))
            }

        val allTimeDistanceKm = allTimeStats.totalDistanceMeters / 1000.0

        // Get runs for the period (filtered by days if specified)
        val runs =
            if (days != null) {
                val since = OffsetDateTime.now().minusDays(days.toLong())
                runSessionRepository.findAllByUserIdAndStartedAtGreaterThanEqual(userId, since)
            } else {
                runSessionRepository.findAllByUserId(userId)
            }

        val periodTotalRuns = runs.size
        val periodTotalDistanceMeters = runs.sumOf { it.totalDistanceM ?: 0.0 }
        val periodTotalDistanceKm = periodTotalDistanceMeters / 1000.0
        val periodTotalMovingTimeSeconds = runs.sumOf { it.movingTimeS?.toLong() ?: 0L }

        // Average Pace for the period: min/km
        val avgPaceSecPerKm =
            if (periodTotalDistanceMeters > 0) {
                periodTotalMovingTimeSeconds.toDouble() / periodTotalDistanceKm
            } else {
                0.0
            }
        val avgPaceMin = (avgPaceSecPerKm / 60).toInt()
        val avgPaceSec = (avgPaceSecPerKm % 60).toInt()
        val averagePace = String.format("%d:%02d /km", avgPaceMin, avgPaceSec)

        // Average distance per run for the period
        val averageDistancePerRunKm =
            if (periodTotalRuns > 0) {
                periodTotalDistanceKm / periodTotalRuns
            } else {
                0.0
            }

        // Weeks active (for runs per week / km per week calculation)
        val weeksActive =
            if (days != null) {
                if (days < 7) 1.0 else days / 7.0
            } else if (allTimeStats.firstRunDate != null) {
                val daysSinceFirst = ChronoUnit.DAYS.between(allTimeStats.firstRunDate, OffsetDateTime.now())
                if (daysSinceFirst < 7) 1.0 else daysSinceFirst / 7.0
            } else {
                1.0
            }

        val runsPerWeek = periodTotalRuns / weeksActive
        val kmPerWeek = periodTotalDistanceKm / weeksActive

        return UserStatisticsResponse(
            totalRuns = periodTotalRuns,
            totalDistanceKm = String.format("%.1f", periodTotalDistanceKm).toDouble(),
            averagePace = averagePace,
            averageDistancePerRunKm = String.format("%.2f", averageDistancePerRunKm).toDouble(),
            runsPerWeek = String.format("%.1f", runsPerWeek).toDouble(),
            kmPerWeek = String.format("%.1f", kmPerWeek).toDouble(),
            allTimeDistanceKm = String.format("%.1f", allTimeDistanceKm).toDouble(),
            allTimeTotalRuns = allTimeStats.totalRuns,
            periodDays = days,
        )
    }
}
