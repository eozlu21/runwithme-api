package com.runwithme.runwithme.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Survey response data transfer object")
data class SurveyResponseDto(
    @Schema(description = "Survey response ID")
    val id: Long,
    @Schema(description = "User ID")
    val userId: UUID,
    @Schema(description = "Preferred days to run")
    val preferredDays: String?,
    @Schema(description = "Preferred time of day")
    val timeOfDay: String?,
    @Schema(description = "Experience level")
    val experienceLevel: String?,
    @Schema(description = "Activity type")
    val activityType: String?,
    @Schema(description = "Intensity preference")
    val intensityPreference: String?,
    @Schema(description = "Social vibe")
    val socialVibe: String?,
    @Schema(description = "Motivation type")
    val motivationType: String?,
    @Schema(description = "Coaching style")
    val coachingStyle: String?,
    @Schema(description = "Music preference")
    val musicPreference: String?,
    @Schema(description = "Match gender preference")
    val matchGenderPreference: Boolean?,
    @Schema(description = "Creation timestamp")
    val createdAt: OffsetDateTime,
    @Schema(description = "Update timestamp")
    val updatedAt: OffsetDateTime,
)

@Schema(description = "Create survey response request")
data class CreateSurveyResponseDto(
    @Schema(description = "Preferred days to run")
    val preferredDays: String?,
    @Schema(description = "Preferred time of day")
    val timeOfDay: String?,
    @Schema(description = "Experience level")
    val experienceLevel: String?,
    @Schema(description = "Activity type")
    val activityType: String?,
    @Schema(description = "Intensity preference")
    val intensityPreference: String?,
    @Schema(description = "Social vibe")
    val socialVibe: String?,
    @Schema(description = "Motivation type")
    val motivationType: String?,
    @Schema(description = "Coaching style")
    val coachingStyle: String?,
    @Schema(description = "Music preference")
    val musicPreference: String?,
    @Schema(description = "Match gender preference")
    val matchGenderPreference: Boolean?,
)
