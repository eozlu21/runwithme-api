package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.LocationFilterLevel
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.PreferenceMatchDetail
import com.runwithme.runwithme.api.dto.RoutePairSimilarity
import com.runwithme.runwithme.api.dto.RouteSimilarityBreakdown
import com.runwithme.runwithme.api.dto.UserRecommendationDto
import com.runwithme.runwithme.api.dto.UserSimilarityDetailDto
import com.runwithme.runwithme.api.entity.Route
import com.runwithme.runwithme.api.entity.SurveyResponse
import com.runwithme.runwithme.api.entity.UserProfile
import com.runwithme.runwithme.api.repository.FriendshipRepository
import com.runwithme.runwithme.api.repository.RouteRepository
import com.runwithme.runwithme.api.repository.SurveyResponseRepository
import com.runwithme.runwithme.api.repository.UserProfileRepository
import com.runwithme.runwithme.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.math.min

@Service
class UserRecommendationService(
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val routeRepository: RouteRepository,
    private val surveyResponseRepository: SurveyResponseRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userPreferenceService: UserPreferenceService,
    private val routeSimilarityService: RouteSimilarityService,
) {
    private val logger = LoggerFactory.getLogger(UserRecommendationService::class.java)

    companion object {
        const val ROUTE_WEIGHT = 0.5
        const val PREFERENCE_WEIGHT = 0.5
        const val DEFAULT_ROUTE_SIMILARITY_BASELINE = 50.0
    }

    /**
     * Get recommended users for the requesting user
     */
    @Transactional(readOnly = true)
    fun getRecommendations(
        requestingUserId: UUID,
        page: Int,
        size: Int,
        locationLevel: LocationFilterLevel,
    ): PageResponse<UserRecommendationDto> {
        // Step 1: Get requesting user's data
        val requestingProfile = userProfileRepository.findById(requestingUserId).orElse(null)
        val requestingSurvey = getLatestSurvey(requestingUserId)
        val requestingRoutes = routeRepository.findByCreatorId(requestingUserId)

        // Step 2: Get friend IDs to exclude
        val friendIds = friendshipRepository.findFriendIds(requestingUserId).toSet()

        // Step 3: Find candidate users based on filters
        val candidates =
            findCandidates(requestingProfile, requestingSurvey, locationLevel)
                .filter { it.userId != requestingUserId && it.userId !in friendIds }

        // Step 4: Filter by schedule compatibility
        val scheduleFilteredCandidates = filterByScheduleCompatibility(candidates, requestingSurvey)

        // Step 5: Calculate similarity scores for each candidate
        val scoredCandidates =
            scheduleFilteredCandidates
                .mapNotNull { candidateProfile ->
                    calculateUserRecommendation(
                        requestingUserId,
                        requestingRoutes,
                        requestingSurvey,
                        candidateProfile,
                    )
                }.sortedByDescending { it.combinedScore }

        // Step 6: Apply pagination
        return paginateResults(scoredCandidates, page, size)
    }

    /**
     * Get detailed similarity breakdown with a specific user
     */
    @Transactional(readOnly = true)
    fun getSimilarityWithUser(
        requestingUserId: UUID,
        targetUserId: UUID,
    ): UserSimilarityDetailDto {
        val targetUser =
            userRepository
                .findById(targetUserId)
                .orElseThrow { NoSuchElementException("Target user not found") }
        val targetProfile = userProfileRepository.findById(targetUserId).orElse(null)

        val requestingRoutes = routeRepository.findByCreatorId(requestingUserId)
        val requestingSurvey = getLatestSurvey(requestingUserId)

        val targetRoutes = routeRepository.findByCreatorId(targetUserId)
        val targetSurvey = getLatestSurvey(targetUserId)

        // Calculate route similarity with details
        val routeSimilarityResult = calculateRouteSimilarityWithDetails(requestingRoutes, targetRoutes)

        // Calculate preference match with details
        val preferenceResult = calculatePreferenceMatchWithDetails(requestingSurvey, targetSurvey)

        // Combined score
        val combinedScore =
            (routeSimilarityResult.averageScore * ROUTE_WEIGHT) +
                (preferenceResult.first * PREFERENCE_WEIGHT)

        // Check friendship status
        val isFriend = friendshipRepository.findFriendIds(requestingUserId).contains(targetUserId)

        return UserSimilarityDetailDto(
            targetUserId = targetUserId,
            targetUsername = targetUser.username,
            combinedScore = combinedScore,
            routeSimilarity = routeSimilarityResult,
            preferenceSimilarity = preferenceResult.second,
            isFriend = isFriend,
        )
    }

    /**
     * Find candidates based on location and gender preferences
     */
    private fun findCandidates(
        requestingProfile: UserProfile?,
        requestingSurvey: SurveyResponse?,
        locationLevel: LocationFilterLevel,
    ): List<UserProfile> {
        val wantsGenderMatch = requestingSurvey?.matchGenderPreference == true
        val requestingPronouns = requestingProfile?.pronouns

        // If no profile or no location, return all users
        if (requestingProfile == null) {
            return if (wantsGenderMatch && requestingPronouns != null) {
                userProfileRepository.findByPronouns(requestingPronouns)
            } else {
                userProfileRepository.findAll()
            }
        }

        // Find by location level with optional gender filter
        return when (locationLevel) {
            LocationFilterLevel.CITY -> {
                val cityId = requestingProfile.cityId
                if (cityId != null) {
                    if (wantsGenderMatch && requestingPronouns != null) {
                        userProfileRepository.findByCityIdAndPronouns(cityId, requestingPronouns)
                    } else {
                        userProfileRepository.findByCityId(cityId)
                    }
                } else {
                    findCandidates(requestingProfile, requestingSurvey, LocationFilterLevel.COUNTRY)
                }
            }
            LocationFilterLevel.COUNTRY -> {
                val countryId = requestingProfile.countryId
                if (countryId != null) {
                    if (wantsGenderMatch && requestingPronouns != null) {
                        userProfileRepository.findByCountryIdAndPronouns(countryId, requestingPronouns)
                    } else {
                        userProfileRepository.findByCountryId(countryId)
                    }
                } else {
                    findCandidates(requestingProfile, requestingSurvey, LocationFilterLevel.NONE)
                }
            }
            LocationFilterLevel.NONE -> {
                if (wantsGenderMatch && requestingPronouns != null) {
                    userProfileRepository.findByPronouns(requestingPronouns)
                } else {
                    userProfileRepository.findAll()
                }
            }
        }
    }

    /**
     * Filter candidates by schedule compatibility
     */
    private fun filterByScheduleCompatibility(
        candidates: List<UserProfile>,
        requestingSurvey: SurveyResponse?,
    ): List<UserProfile> {
        if (requestingSurvey == null) return candidates

        val requestingDay = requestingSurvey.preferredDays?.lowercase() ?: "any"
        val requestingTime = requestingSurvey.timeOfDay?.lowercase() ?: "any"

        // If requesting user is "any" for both, no filtering needed
        if (requestingDay == "any" && requestingTime == "any") {
            return candidates
        }

        return candidates.filter { profile ->
            val candidateSurvey = getLatestSurvey(profile.userId!!)
            if (candidateSurvey == null) return@filter true // Include if no survey

            val dayCompatible =
                isDayCompatible(
                    requestingDay,
                    candidateSurvey.preferredDays?.lowercase() ?: "any",
                )
            val timeCompatible =
                isTimeCompatible(
                    requestingTime,
                    candidateSurvey.timeOfDay?.lowercase() ?: "any",
                )

            dayCompatible && timeCompatible
        }
    }

    /**
     * Calculate user recommendation for a candidate
     */
    private fun calculateUserRecommendation(
        requestingUserId: UUID,
        requestingRoutes: List<Route>,
        requestingSurvey: SurveyResponse?,
        candidateProfile: UserProfile,
    ): UserRecommendationDto? {
        val candidateUserId = candidateProfile.userId ?: return null
        val candidateUser = userRepository.findById(candidateUserId).orElse(null) ?: return null

        val candidateRoutes = routeRepository.findByCreatorId(candidateUserId)
        val candidateSurvey = getLatestSurvey(candidateUserId)

        // Calculate route similarity
        val routeSimilarity = calculateAverageRouteSimilarity(requestingRoutes, candidateRoutes)

        // Calculate preference match
        val preferenceMatch = calculatePreferenceMatch(requestingSurvey, candidateSurvey)

        // Combined score: 50% route + 50% preference
        val combinedScore = (routeSimilarity * ROUTE_WEIGHT) + (preferenceMatch * PREFERENCE_WEIGHT)

        return UserRecommendationDto(
            userId = candidateUserId,
            username = candidateUser.username,
            profilePic = candidateProfile.profilePic,
            combinedScore = combinedScore,
            routeSimilarityScore = routeSimilarity,
            preferenceSimilarityScore = preferenceMatch,
            routePairCount = requestingRoutes.size * candidateRoutes.size,
            hasRoutes = candidateRoutes.isNotEmpty(),
            hasSurvey = candidateSurvey != null,
        )
    }

    /**
     * Calculate AVERAGE similarity across ALL route pairs between two users
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
            // Use "overall" similarity (combines shape + metadata)
            result.similarities["overall"] ?: DEFAULT_ROUTE_SIMILARITY_BASELINE
        } catch (e: Exception) {
            logger.warn("Route similarity inference failed: ${e.message}")
            DEFAULT_ROUTE_SIMILARITY_BASELINE
        }
    }

    /**
     * Convert Route entity to the map format expected by Python inference service
     */
    private fun routeToMap(route: Route): Map<String, Any?>? {
        val pathGeom = route.pathGeom ?: return null
        val distanceM = route.distanceM ?: return null
        val durationS = route.estimatedDurationS ?: return null

        // Convert JTS LineString to GeoJSON format
        val coordinates = pathGeom.coordinates.map { listOf(it.x, it.y) }
        if (coordinates.size < 2) return null

        // Calculate pace (min/km)
        val distanceKm = distanceM / 1000.0
        val durationMin = durationS / 60.0
        val pace = if (distanceKm > 0) durationMin / distanceKm else 5.0

        return mapOf(
            "Geometry" to
                mapOf(
                    "type" to "LineString",
                    "coordinates" to coordinates,
                ),
            "Distance" to distanceM, // Python divides by 1000
            "Pace" to pace,
            "Terrain" to "urban", // Default, Route entity doesn't have terrain
        )
    }

    /**
     * Calculate route similarity with detailed breakdown
     */
    private fun calculateRouteSimilarityWithDetails(
        user1Routes: List<Route>,
        user2Routes: List<Route>,
    ): RouteSimilarityBreakdown {
        if (user1Routes.isEmpty() || user2Routes.isEmpty()) {
            return RouteSimilarityBreakdown(
                averageScore = DEFAULT_ROUTE_SIMILARITY_BASELINE,
                pairCount = 0,
                topPairs = emptyList(),
            )
        }

        val pairs = mutableListOf<RoutePairSimilarity>()

        for (route1 in user1Routes) {
            for (route2 in user2Routes) {
                val similarity = calculateRouteSimilarity(route1, route2)
                pairs.add(
                    RoutePairSimilarity(
                        route1Id = route1.id!!,
                        route2Id = route2.id!!,
                        similarity = similarity,
                    ),
                )
            }
        }

        val sortedPairs = pairs.sortedByDescending { it.similarity }
        val topPairs = sortedPairs.take(5)

        return RouteSimilarityBreakdown(
            averageScore = pairs.map { it.similarity }.average(),
            pairCount = pairs.size,
            topPairs = topPairs,
        )
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
     * Calculate preference match with detailed breakdown
     */
    private fun calculatePreferenceMatchWithDetails(
        survey1: SurveyResponse?,
        survey2: SurveyResponse?,
    ): Pair<Double, Map<String, PreferenceMatchDetail>> {
        val prefs1 = surveyToPreferences(survey1)
        val prefs2 = surveyToPreferences(survey2)
        val result = userPreferenceService.calculatePreferenceMatch(prefs1, prefs2)

        val details =
            result.matches.mapValues { (_, match) ->
                PreferenceMatchDetail(
                    user1Value = match.user1Value,
                    user2Value = match.user2Value,
                    isMatch = match.isMatch,
                )
            }

        return Pair(result.overallMatchPercent, details)
    }

    /**
     * Get latest survey for a user
     */
    private fun getLatestSurvey(userId: UUID): SurveyResponse? = surveyResponseRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)

    private fun isDayCompatible(
        day1: String,
        day2: String,
    ): Boolean {
        if (day1 == "any" || day2 == "any") return true
        if (day1 == day2) return true

        val weekdays = setOf("monday", "tuesday", "wednesday", "thursday", "friday")
        val weekends = setOf("saturday", "sunday")

        return when {
            day1 == "weekday" -> day2 in weekdays || day2 == "weekday"
            day2 == "weekday" -> day1 in weekdays
            day1 == "weekend" -> day2 in weekends || day2 == "weekend"
            day2 == "weekend" -> day1 in weekends
            else -> false
        }
    }

    private fun isTimeCompatible(
        time1: String,
        time2: String,
    ): Boolean {
        if (time1 == "any" || time2 == "any") return true
        return time1 == time2
    }

    /**
     * Paginate results manually
     */
    private fun paginateResults(
        results: List<UserRecommendationDto>,
        page: Int,
        size: Int,
    ): PageResponse<UserRecommendationDto> {
        val totalElements = results.size.toLong()
        val totalPages = if (size > 0) ((totalElements + size - 1) / size).toInt() else 0
        val startIndex = page * size
        val endIndex = min(startIndex + size, results.size)

        val content =
            if (startIndex < results.size) {
                results.subList(startIndex, endIndex)
            } else {
                emptyList()
            }

        return PageResponse(
            content = content,
            pageNumber = page,
            pageSize = size,
            totalElements = totalElements,
            totalPages = totalPages,
            first = page == 0,
            last = page >= totalPages - 1,
        )
    }
}
