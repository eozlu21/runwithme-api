package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.entity.Route
import com.runwithme.runwithme.api.entity.SurveyResponse
import com.runwithme.runwithme.api.entity.UserSimilarityCache
import com.runwithme.runwithme.api.repository.RouteRepository
import com.runwithme.runwithme.api.repository.SurveyResponseRepository
import com.runwithme.runwithme.api.repository.UserRepository
import com.runwithme.runwithme.api.repository.UserSimilarityCacheRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Service for precomputing and caching user similarity scores.
 *
 * This service handles:
 * - Full recomputation of all user pairs
 * - Single user recomputation
 * - Batch processing to manage memory
 */
@Service
class SimilarityPrecomputeService(
    private val userRepository: UserRepository,
    private val routeRepository: RouteRepository,
    private val surveyResponseRepository: SurveyResponseRepository,
    private val userSimilarityCacheRepository: UserSimilarityCacheRepository,
    private val routeSimilarityService: RouteSimilarityService,
    private val userPreferenceService: UserPreferenceService,
) {
    private val logger = LoggerFactory.getLogger(SimilarityPrecomputeService::class.java)

    companion object {
        const val ROUTE_WEIGHT = 0.5
        const val PREFERENCE_WEIGHT = 0.5
        const val DEFAULT_ROUTE_SIMILARITY_BASELINE = 50.0
        const val BATCH_SIZE = 100
        const val PROGRESS_LOG_INTERVAL = 10 // Log progress every N pairs
    }

    private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)

    /**
     * Recompute all similarity scores for all user pairs.
     * This is a heavy operation - runs in batches to manage memory.
     *
     * @return ComputationResult with statistics
     */
    @Transactional
    fun computeAllSimilarities(): ComputationResult {
        logger.debug("=== SIMILARITY COMPUTATION START ===")
        logger.info("Starting full similarity precomputation...")
        val startTime = System.currentTimeMillis()

        // Step 1: Clear existing cache
        logger.debug("Step 1: Clearing existing similarity cache...")
        userSimilarityCacheRepository.deleteAllEntries()
        logger.info("Cleared existing similarity cache")

        // Step 2: Get all user IDs
        logger.debug("Step 2: Loading all user IDs...")
        val allUserIds: List<UUID> = userRepository.findAll().mapNotNull { it.userId }
        val totalUsers = allUserIds.size
        val totalPairs = (totalUsers.toLong() * (totalUsers - 1)) / 2
        logger.info("Found $totalUsers users to process")
        logger.debug("Total pairs to compute: $totalPairs")

        if (totalUsers < 2) {
            logger.info("Not enough users for similarity computation")
            return ComputationResult(0, 0, 0)
        }

        // Step 3: Preload all routes and surveys into maps for efficiency
        logger.debug("Step 3: Preloading routes for all users...")
        val routeLoadStart = System.currentTimeMillis()
        val routesByUser = preloadRoutesByUser(allUserIds)
        val totalRoutes = routesByUser.values.sumOf { it.size }
        logger.debug("Loaded routes for $totalUsers users ($totalRoutes total routes) in ${System.currentTimeMillis() - routeLoadStart}ms")

        logger.debug("Step 4: Preloading surveys for all users...")
        val surveyLoadStart = System.currentTimeMillis()
        val surveysByUser = preloadSurveysByUser(allUserIds)
        val usersWithSurveys = surveysByUser.values.count { it != null }
        logger.debug("Loaded surveys for $usersWithSurveys/$totalUsers users in ${System.currentTimeMillis() - surveyLoadStart}ms")

        // Step 5: Compute similarities for all unique pairs
        logger.debug("Step 5: Computing similarities for all user pairs...")
        var pairsComputed = 0
        var pairsFailed = 0
        val cacheEntries = mutableListOf<UserSimilarityCache>()

        for (i in allUserIds.indices) {
            for (j in i + 1 until allUserIds.size) {
                val userId1 = allUserIds[i]
                val userId2 = allUserIds[j]

                try {
                    logger.trace("Computing pair: $userId1 <-> $userId2")
                    val pairStart = System.currentTimeMillis()

                    val similarity =
                        computePairSimilarity(
                            userId1,
                            userId2,
                            routesByUser[userId1] ?: emptyList(),
                            routesByUser[userId2] ?: emptyList(),
                            surveysByUser[userId1],
                            surveysByUser[userId2],
                        )

                    cacheEntries.add(similarity)
                    pairsComputed++

                    logger.trace(
                        "Pair $userId1 <-> $userId2: route=${similarity.routeSimilarity.format(2)}%, " +
                            "pref=${similarity.preferenceSimilarity.format(2)}%, combined=${similarity.combinedScore.format(2)}% " +
                            "(${System.currentTimeMillis() - pairStart}ms)",
                    )

                    // Log progress with ETA
                    if (pairsComputed == 1 || pairsComputed % PROGRESS_LOG_INTERVAL == 0) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val pairsPerSecond = if (elapsed > 0) pairsComputed * 1000.0 / elapsed else 0.0
                        val remainingPairs = totalPairs - pairsComputed
                        val etaSeconds = if (pairsPerSecond > 0) remainingPairs / pairsPerSecond else 0.0
                        val progressPercent = (pairsComputed * 100.0 / totalPairs).format(1)
                        logger.debug(
                            "Progress: $pairsComputed/$totalPairs pairs ($progressPercent%) | " +
                                "Speed: ${pairsPerSecond.format(2)} pairs/sec | ETA: ${etaSeconds.format(0)}s",
                        )
                    }

                    // Batch save to avoid memory issues
                    if (cacheEntries.size >= BATCH_SIZE) {
                        logger.debug("Saving batch of ${cacheEntries.size} entries to database...")
                        userSimilarityCacheRepository.saveAll(cacheEntries)
                        cacheEntries.clear()
                        logger.info("Saved batch, total pairs computed: $pairsComputed")
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to compute similarity for $userId1 - $userId2: ${e.message}")
                    pairsFailed++
                }
            }
        }

        // Save remaining entries
        if (cacheEntries.isNotEmpty()) {
            logger.debug("Saving final batch of ${cacheEntries.size} entries to database...")
            userSimilarityCacheRepository.saveAll(cacheEntries)
        }

        val duration = System.currentTimeMillis() - startTime
        val avgTimePerPair = if (pairsComputed > 0) duration.toDouble() / pairsComputed else 0.0
        logger.info("Full similarity precomputation completed: $pairsComputed pairs in ${duration}ms, $pairsFailed failed")
        logger.debug(
            "=== SIMILARITY COMPUTATION COMPLETE === " +
                "Avg time per pair: ${avgTimePerPair.format(2)}ms | " +
                "Success rate: ${((pairsComputed * 100.0) / (pairsComputed + pairsFailed)).format(1)}%",
        )

        return ComputationResult(totalUsers, pairsComputed, pairsFailed)
    }

    /**
     * Recompute similarity scores for a specific user with all other users.
     *
     * @param userId The user to recompute similarities for
     * @return ComputationResult with statistics
     */
    @Transactional
    fun computeSimilaritiesForUser(userId: UUID): ComputationResult {
        logger.debug("=== USER SIMILARITY COMPUTATION START for $userId ===")
        logger.info("Starting similarity computation for user $userId...")
        val startTime = System.currentTimeMillis()

        // Verify user exists
        if (!userRepository.existsById(userId)) {
            throw IllegalArgumentException("User not found: $userId")
        }

        // Step 1: Delete existing cache entries for this user
        logger.debug("Step 1: Clearing existing cache entries for user $userId...")
        userSimilarityCacheRepository.deleteAllByUserId(userId)
        logger.info("Cleared existing cache entries for user $userId")

        // Step 2: Get all other user IDs
        logger.debug("Step 2: Loading other user IDs...")
        val otherUserIds: List<UUID> =
            userRepository
                .findAll()
                .mapNotNull { it.userId }
                .filter { it != userId }

        val totalPairs = otherUserIds.size.toLong()
        logger.debug("Found ${otherUserIds.size} other users to compare against")

        if (otherUserIds.isEmpty()) {
            logger.info("No other users found for similarity computation")
            return ComputationResult(1, 0, 0)
        }

        // Step 3: Get target user's data
        logger.debug("Step 3: Loading target user's routes and survey...")
        val targetRoutes = routeRepository.findByCreatorId(userId)
        val targetSurvey = getLatestSurvey(userId)
        logger.debug("Target user has ${targetRoutes.size} routes, survey: ${if (targetSurvey != null) "yes" else "no"}")

        // Step 4: Preload other users' data
        logger.debug("Step 4: Preloading other users' routes...")
        val routeLoadStart = System.currentTimeMillis()
        val routesByUser = preloadRoutesByUser(otherUserIds)
        val totalRoutes = routesByUser.values.sumOf { it.size }
        logger.debug("Loaded routes for ${otherUserIds.size} users ($totalRoutes total routes) in ${System.currentTimeMillis() - routeLoadStart}ms")

        logger.debug("Step 5: Preloading other users' surveys...")
        val surveyLoadStart = System.currentTimeMillis()
        val surveysByUser = preloadSurveysByUser(otherUserIds)
        val usersWithSurveys = surveysByUser.values.count { it != null }
        logger.debug("Loaded surveys for $usersWithSurveys/${otherUserIds.size} users in ${System.currentTimeMillis() - surveyLoadStart}ms")

        // Step 6: Compute similarities
        logger.debug("Step 6: Computing similarities for all pairs...")
        var pairsComputed = 0
        var pairsFailed = 0
        val cacheEntries = mutableListOf<UserSimilarityCache>()

        for (otherUserId in otherUserIds) {
            try {
                logger.trace("Computing pair: $userId <-> $otherUserId")
                val pairStart = System.currentTimeMillis()

                val similarity =
                    computePairSimilarity(
                        userId,
                        otherUserId,
                        targetRoutes,
                        routesByUser[otherUserId] ?: emptyList(),
                        targetSurvey,
                        surveysByUser[otherUserId],
                    )

                cacheEntries.add(similarity)
                pairsComputed++

                logger.trace(
                    "Pair $userId <-> $otherUserId: route=${similarity.routeSimilarity.format(2)}%, " +
                        "pref=${similarity.preferenceSimilarity.format(2)}%, combined=${similarity.combinedScore.format(2)}% " +
                        "(${System.currentTimeMillis() - pairStart}ms)",
                )

                // Log progress with ETA
                if (pairsComputed == 1 || pairsComputed % PROGRESS_LOG_INTERVAL == 0) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val pairsPerSecond = if (elapsed > 0) pairsComputed * 1000.0 / elapsed else 0.0
                    val remainingPairs = totalPairs - pairsComputed
                    val etaSeconds = if (pairsPerSecond > 0) remainingPairs / pairsPerSecond else 0.0
                    val progressPercent = (pairsComputed * 100.0 / totalPairs).format(1)
                    logger.debug(
                        "Progress: $pairsComputed/$totalPairs pairs ($progressPercent%) | " +
                            "Speed: ${pairsPerSecond.format(2)} pairs/sec | ETA: ${etaSeconds.format(0)}s",
                    )
                }

                if (cacheEntries.size >= BATCH_SIZE) {
                    logger.debug("Saving batch of ${cacheEntries.size} entries to database...")
                    userSimilarityCacheRepository.saveAll(cacheEntries)
                    cacheEntries.clear()
                }
            } catch (e: Exception) {
                logger.warn("Failed to compute similarity for $userId - $otherUserId: ${e.message}")
                pairsFailed++
            }
        }

        if (cacheEntries.isNotEmpty()) {
            logger.debug("Saving final batch of ${cacheEntries.size} entries to database...")
            userSimilarityCacheRepository.saveAll(cacheEntries)
        }

        val duration = System.currentTimeMillis() - startTime
        val avgTimePerPair = if (pairsComputed > 0) duration.toDouble() / pairsComputed else 0.0
        logger.info("User similarity computation completed: $pairsComputed pairs in ${duration}ms, $pairsFailed failed")
        logger.debug(
            "=== USER SIMILARITY COMPUTATION COMPLETE for $userId === " +
                "Avg time per pair: ${avgTimePerPair.format(2)}ms | " +
                "Success rate: ${((pairsComputed * 100.0) / (pairsComputed + pairsFailed)).format(1)}%",
        )

        return ComputationResult(1, pairsComputed, pairsFailed)
    }

    /**
     * Compute similarity between two users and create cache entry.
     */
    private fun computePairSimilarity(
        userId1: UUID,
        userId2: UUID,
        routes1: List<Route>,
        routes2: List<Route>,
        survey1: SurveyResponse?,
        survey2: SurveyResponse?,
    ): UserSimilarityCache {
        // Calculate route similarity
        val routeSimilarity = calculateAverageRouteSimilarity(routes1, routes2)

        // Calculate preference similarity
        val preferenceSimilarity = calculatePreferenceMatch(survey1, survey2)

        // Combined score
        val combinedScore = (routeSimilarity * ROUTE_WEIGHT) + (preferenceSimilarity * PREFERENCE_WEIGHT)

        return UserSimilarityCache.create(
            userIdA = userId1,
            userIdB = userId2,
            routeSimilarity = routeSimilarity,
            preferenceSimilarity = preferenceSimilarity,
            combinedScore = combinedScore,
        )
    }

    /**
     * Calculate average route similarity (extracted from UserRecommendationService)
     */
    private fun calculateAverageRouteSimilarity(
        user1Routes: List<Route>,
        user2Routes: List<Route>,
    ): Double {
        if (user1Routes.isEmpty() || user2Routes.isEmpty()) {
            return DEFAULT_ROUTE_SIMILARITY_BASELINE
        }

        val similarities = mutableListOf<Double>()

        for (route1 in user1Routes) {
            for (route2 in user2Routes) {
                val similarity = calculateRouteSimilarity(route1, route2)
                similarities.add(similarity)
            }
        }

        return if (similarities.isNotEmpty()) {
            similarities.average()
        } else {
            DEFAULT_ROUTE_SIMILARITY_BASELINE
        }
    }

    /**
     * Calculate similarity between two routes using ML inference service
     */
    private fun calculateRouteSimilarity(
        route1: Route,
        route2: Route,
    ): Double {
        val map1 = routeToMap(route1) ?: return DEFAULT_ROUTE_SIMILARITY_BASELINE
        val map2 = routeToMap(route2) ?: return DEFAULT_ROUTE_SIMILARITY_BASELINE

        return try {
            val result = routeSimilarityService.compareTwoRoutes(map1, map2)
            result.similarities["overall"] ?: DEFAULT_ROUTE_SIMILARITY_BASELINE
        } catch (e: Exception) {
            logger.warn("Route similarity inference failed: ${e.message}")
            DEFAULT_ROUTE_SIMILARITY_BASELINE
        }
    }

    /**
     * Convert Route entity to map format for Python inference
     */
    private fun routeToMap(route: Route): Map<String, Any?>? {
        val pathGeom = route.pathGeom ?: return null
        val distanceM = route.distanceM ?: return null
        val durationS = route.estimatedDurationS ?: return null

        val coordinates = pathGeom.coordinates.map { listOf(it.x, it.y) }
        if (coordinates.size < 2) return null

        val distanceKm = distanceM / 1000.0
        val durationMin = durationS / 60.0
        val pace = if (distanceKm > 0) durationMin / distanceKm else 5.0

        return mapOf(
            "Geometry" to
                mapOf(
                    "type" to "LineString",
                    "coordinates" to coordinates,
                ),
            "Distance" to distanceM,
            "Pace" to pace,
            "Terrain" to "urban",
        )
    }

    /**
     * Calculate preference match between two users
     */
    private fun calculatePreferenceMatch(
        survey1: SurveyResponse?,
        survey2: SurveyResponse?,
    ): Double {
        val prefs1 = surveyToPreferences(survey1)
        val prefs2 = surveyToPreferences(survey2)
        return userPreferenceService.calculatePreferenceMatch(prefs1, prefs2).overallMatchPercent
    }

    /**
     * Convert SurveyResponse to UserPreferences
     */
    private fun surveyToPreferences(survey: SurveyResponse?): UserPreferences {
        if (survey == null) return UserPreferences()

        return UserPreferences(
            experienceLevel = survey.experienceLevel ?: "amateur",
            activityType = survey.activityType ?: "leisure",
            intensityPreference = survey.intensityPreference ?: "steady",
            socialVibe = survey.socialVibe ?: "social",
            motivation = survey.motivationType ?: "training",
            coachingStyle = survey.coachingStyle ?: "companion",
            musicPreference = survey.musicPreference ?: "headphone",
            matchGender = survey.matchGenderPreference ?: false,
            preferredDay = survey.preferredDays ?: "any",
            timeOfDay = survey.timeOfDay ?: "any",
        )
    }

    private fun getLatestSurvey(userId: UUID): SurveyResponse? = surveyResponseRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)

    /**
     * Preload all routes grouped by user ID for efficiency
     */
    private fun preloadRoutesByUser(userIds: List<UUID>): Map<UUID, List<Route>> = userIds.associateWith { routeRepository.findByCreatorId(it) }

    /**
     * Preload latest surveys for all users
     */
    private fun preloadSurveysByUser(userIds: List<UUID>): Map<UUID, SurveyResponse?> = userIds.associateWith { getLatestSurvey(it) }
}

/**
 * Result of a similarity computation operation
 */
data class ComputationResult(
    val usersProcessed: Int,
    val pairsComputed: Int,
    val pairsFailed: Int,
)
