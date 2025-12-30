package com.runwithme.runwithme.api.config

import com.runwithme.runwithme.api.service.SimilarityPrecomputeService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Configuration
@EnableScheduling
class SchedulingConfig

/**
 * Scheduled tasks for similarity precomputation.
 */
@Component
class SimilarityScheduledTasks(
    private val similarityPrecomputeService: SimilarityPrecomputeService,
) {
    private val logger = LoggerFactory.getLogger(SimilarityScheduledTasks::class.java)

    /**
     * Run full similarity recomputation every day at 3:00 AM.
     * Cron: second minute hour day-of-month month day-of-week
     * "0 0 3 * * *" = At 03:00:00 every day
     */
    @Scheduled(cron = "0 0 3 * * *")
    fun dailyFullRecomputation() {
        logger.info("Starting scheduled daily similarity recomputation...")
        try {
            val result = similarityPrecomputeService.computeAllSimilarities()
            logger.info("Scheduled recomputation completed: ${result.pairsComputed} pairs computed, ${result.pairsFailed} failed")
        } catch (e: Exception) {
            logger.error("Scheduled recomputation failed: ${e.message}", e)
        }
    }
}
