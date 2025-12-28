package com.runwithme.runwithme.api.service

import org.springframework.stereotype.Service

/**
 * User preferences for matching (handled by simple exact matching, not ML)
 *
 * @property experienceLevel beginner, amateur, intermediate, professional
 * @property activityType walking, hiking, leisure, competitive
 * @property intensityPreference high, steady
 * @property socialVibe silent, social
 * @property motivation mental, weightloss, training, socializing
 * @property coachingStyle pusher, companion
 * @property musicPreference headphone, nature
 * @property matchGender true = prefer same gender
 * @property preferredDay monday-sunday, weekday, weekend, any
 * @property timeOfDay early_bird, morning, lunch, afternoon, evening, night, any
 */
data class UserPreferences(
    val experienceLevel: String = "amateur",
    val activityType: String = "leisure",
    val intensityPreference: String = "steady",
    val socialVibe: String = "social",
    val motivation: String = "training",
    val coachingStyle: String = "companion",
    val musicPreference: String = "headphone",
    val matchGender: Boolean = false,
    val preferredDay: String = "any",
    val timeOfDay: String = "any",
)

/**
 * Result of preference matching between two users
 */
data class PreferenceMatchResult(
    val overallMatchPercent: Double,
    val matches: Map<String, PreferenceMatch>,
)

data class PreferenceMatch(
    val user1Value: String,
    val user2Value: String,
    val isMatch: Boolean,
    val matchPercent: Double,
)

/**
 * Combined similarity result (route ML + preference matching)
 */
data class CombinedSimilarityResult(
    val routeId: String,
    val routeSimilarities: Map<String, Double>, // From ML model
    val preferenceMatches: Map<String, Double>, // From simple matching
    val combinedScore: Double, // Weighted combination
    val routeMetadata: Map<String, Any?>,
    val userPreferences: UserPreferences?,
)

/**
 * User Preference Matching Service
 *
 * Handles simple exact matching for user preferences.
 * These are discrete values where exact match = 100%, no match = 0%
 */
@Service
class UserPreferenceService {
    companion object {
        // Valid values for each preference
        val EXPERIENCE_LEVELS = listOf("beginner", "amateur", "intermediate", "professional")
        val ACTIVITY_TYPES = listOf("walking", "hiking", "leisure", "competitive")
        val INTENSITY_PREFERENCES = listOf("high", "steady")
        val SOCIAL_VIBES = listOf("silent", "social")
        val MOTIVATIONS = listOf("mental", "weightloss", "training", "socializing")
        val COACHING_STYLES = listOf("pusher", "companion")
        val MUSIC_PREFERENCES = listOf("headphone", "nature")
        val PREFERRED_DAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "weekday", "weekend", "any")
        val TIME_OF_DAY_OPTIONS = listOf("early_bird", "morning", "lunch", "afternoon", "evening", "night", "any")

        // Default weights for combining route similarity and preference matching
        val DEFAULT_ROUTE_WEIGHT = 0.6
        val DEFAULT_PREFERENCE_WEIGHT = 0.4

        // Weights for individual preferences (must sum to 1.0)
        val PREFERENCE_WEIGHTS =
            mapOf(
                "experience_level" to 0.12,
                "activity_type" to 0.12,
                "intensity_preference" to 0.08,
                "social_vibe" to 0.15, // Higher weight - important for social matching
                "motivation" to 0.12,
                "coaching_style" to 0.08,
                "music_preference" to 0.05,
                "match_gender" to 0.08,
                "preferred_day" to 0.12, // When they like to run
                "time_of_day" to 0.08, // Morning/evening person
            )
    }

    /**
     * Calculate preference match between two users
     *
     * @param user1 First user's preferences
     * @param user2 Second user's preferences
     * @return PreferenceMatchResult with overall score and individual matches
     */
    fun calculatePreferenceMatch(
        user1: UserPreferences,
        user2: UserPreferences,
    ): PreferenceMatchResult {
        val matches = mutableMapOf<String, PreferenceMatch>()

        // Experience level
        matches["experience_level"] =
            createMatch(
                user1.experienceLevel.lowercase(),
                user2.experienceLevel.lowercase(),
            )

        // Activity type
        matches["activity_type"] =
            createMatch(
                user1.activityType.lowercase(),
                user2.activityType.lowercase(),
            )

        // Intensity preference
        matches["intensity_preference"] =
            createMatch(
                user1.intensityPreference.lowercase(),
                user2.intensityPreference.lowercase(),
            )

        // Social vibe
        matches["social_vibe"] =
            createMatch(
                user1.socialVibe.lowercase(),
                user2.socialVibe.lowercase(),
            )

        // Motivation
        matches["motivation"] =
            createMatch(
                user1.motivation.lowercase(),
                user2.motivation.lowercase(),
            )

        // Coaching style
        matches["coaching_style"] =
            createMatch(
                user1.coachingStyle.lowercase(),
                user2.coachingStyle.lowercase(),
            )

        // Music preference
        matches["music_preference"] =
            createMatch(
                user1.musicPreference.lowercase(),
                user2.musicPreference.lowercase(),
            )

        // Gender matching
        matches["match_gender"] =
            createMatch(
                user1.matchGender.toString(),
                user2.matchGender.toString(),
            )

        // Preferred day - with smart matching for weekday/weekend/any
        matches["preferred_day"] =
            createDayMatch(
                user1.preferredDay.lowercase(),
                user2.preferredDay.lowercase(),
            )

        // Time of day - with "any" wildcard
        matches["time_of_day"] =
            createMatch(
                user1.timeOfDay.lowercase(),
                user2.timeOfDay.lowercase(),
                wildcardValue = "any",
            )

        // Calculate weighted overall match
        val overallMatch =
            matches.entries.sumOf { (key, match) ->
                match.matchPercent * (PREFERENCE_WEIGHTS[key] ?: 0.1)
            }

        return PreferenceMatchResult(
            overallMatchPercent = overallMatch,
            matches = matches,
        )
    }

    /**
     * Get preference match percentages as a simple map
     */
    fun getPreferenceMatchScores(
        user1: UserPreferences,
        user2: UserPreferences,
    ): Map<String, Double> {
        val result = calculatePreferenceMatch(user1, user2)
        return result.matches.mapValues { it.value.matchPercent }
    }

    /**
     * Combine route similarity (from ML) with preference matching
     *
     * @param routeSimilarity Overall route similarity from ML model (0-100)
     * @param preferenceMatches Preference match scores
     * @param routeWeight Weight for route similarity (default 0.6)
     * @return Combined score (0-100)
     */
    fun calculateCombinedScore(
        routeSimilarity: Double,
        preferenceMatches: Map<String, Double>,
        routeWeight: Double = DEFAULT_ROUTE_WEIGHT,
    ): Double {
        val preferenceWeight = 1.0 - routeWeight

        // Weighted average of preference matches
        val avgPreferenceMatch =
            preferenceMatches.entries.sumOf { (key, score) ->
                score * (PREFERENCE_WEIGHTS[key] ?: 0.1)
            }

        return (routeSimilarity * routeWeight) + (avgPreferenceMatch * preferenceWeight)
    }

    /**
     * Parse preferences from a map (e.g., from JSON request)
     */
    fun parsePreferences(data: Map<String, Any?>): UserPreferences =
        UserPreferences(
            experienceLevel =
                (data["experience_level"] as? String)?.lowercase()
                    ?: (data["experienceLevel"] as? String)?.lowercase()
                    ?: "amateur",
            activityType =
                (data["activity_type"] as? String)?.lowercase()
                    ?: (data["activityType"] as? String)?.lowercase()
                    ?: "leisure",
            intensityPreference =
                (data["intensity_preference"] as? String)?.lowercase()
                    ?: (data["intensityPreference"] as? String)?.lowercase()
                    ?: "steady",
            socialVibe =
                (data["social_vibe"] as? String)?.lowercase()
                    ?: (data["socialVibe"] as? String)?.lowercase()
                    ?: "social",
            motivation = (data["motivation"] as? String)?.lowercase() ?: "training",
            coachingStyle =
                (data["coaching_style"] as? String)?.lowercase()
                    ?: (data["coachingStyle"] as? String)?.lowercase()
                    ?: "companion",
            musicPreference =
                (data["music_preference"] as? String)?.lowercase()
                    ?: (data["musicPreference"] as? String)?.lowercase()
                    ?: "headphone",
            matchGender =
                (data["match_gender"] as? Boolean)
                    ?: (data["matchGender"] as? Boolean)
                    ?: false,
            preferredDay =
                (data["preferred_day"] as? String)?.lowercase()
                    ?: (data["preferredDay"] as? String)?.lowercase()
                    ?: (data["PreferredDay"] as? String)?.lowercase()
                    ?: "any",
            timeOfDay =
                (data["time_of_day"] as? String)?.lowercase()
                    ?: (data["timeOfDay"] as? String)?.lowercase()
                    ?: (data["TimeOfDay"] as? String)?.lowercase()
                    ?: "any",
        )

    /**
     * Validate preferences and return sanitized version
     */
    fun validatePreferences(prefs: UserPreferences): UserPreferences =
        UserPreferences(
            experienceLevel =
                if (prefs.experienceLevel.lowercase() in EXPERIENCE_LEVELS) {
                    prefs.experienceLevel.lowercase()
                } else {
                    "amateur"
                },
            activityType =
                if (prefs.activityType.lowercase() in ACTIVITY_TYPES) {
                    prefs.activityType.lowercase()
                } else {
                    "leisure"
                },
            intensityPreference =
                if (prefs.intensityPreference.lowercase() in INTENSITY_PREFERENCES) {
                    prefs.intensityPreference.lowercase()
                } else {
                    "steady"
                },
            socialVibe =
                if (prefs.socialVibe.lowercase() in SOCIAL_VIBES) {
                    prefs.socialVibe.lowercase()
                } else {
                    "social"
                },
            motivation =
                if (prefs.motivation.lowercase() in MOTIVATIONS) {
                    prefs.motivation.lowercase()
                } else {
                    "training"
                },
            coachingStyle =
                if (prefs.coachingStyle.lowercase() in COACHING_STYLES) {
                    prefs.coachingStyle.lowercase()
                } else {
                    "companion"
                },
            musicPreference =
                if (prefs.musicPreference.lowercase() in MUSIC_PREFERENCES) {
                    prefs.musicPreference.lowercase()
                } else {
                    "headphone"
                },
            matchGender = prefs.matchGender,
            preferredDay =
                if (prefs.preferredDay.lowercase() in PREFERRED_DAYS) {
                    prefs.preferredDay.lowercase()
                } else {
                    "any"
                },
            timeOfDay =
                if (prefs.timeOfDay.lowercase() in TIME_OF_DAY_OPTIONS) {
                    prefs.timeOfDay.lowercase()
                } else {
                    "any"
                },
        )

    private fun createMatch(
        value1: String,
        value2: String,
        wildcardValue: String? = null,
    ): PreferenceMatch {
        // If either value is the wildcard, it's a match
        val isMatch =
            value1 == value2 ||
                (wildcardValue != null && (value1 == wildcardValue || value2 == wildcardValue))
        return PreferenceMatch(
            user1Value = value1,
            user2Value = value2,
            isMatch = isMatch,
            matchPercent = if (isMatch) 100.0 else 0.0,
        )
    }

    private fun createDayMatch(
        day1: String,
        day2: String,
    ): PreferenceMatch {
        val weekdays = setOf("monday", "tuesday", "wednesday", "thursday", "friday")
        val weekends = setOf("saturday", "sunday")

        val isMatch =
            when {
                day1 == day2 -> true // Exact match
                day1 == "any" || day2 == "any" -> true // "any" matches everything
                day1 == "weekday" && day2 in weekdays -> true
                day2 == "weekday" && day1 in weekdays -> true
                day1 == "weekend" && day2 in weekends -> true
                day2 == "weekend" && day1 in weekends -> true
                day1 == "weekday" && day2 == "weekend" -> false // Weekday vs weekend
                day1 == "weekend" && day2 == "weekday" -> false
                else -> false
            }

        return PreferenceMatch(
            user1Value = day1,
            user2Value = day2,
            isMatch = isMatch,
            matchPercent = if (isMatch) 100.0 else 0.0,
        )
    }
}
