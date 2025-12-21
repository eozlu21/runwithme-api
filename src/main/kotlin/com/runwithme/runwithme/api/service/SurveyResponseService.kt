package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.CreateSurveyResponseDto
import com.runwithme.runwithme.api.dto.SurveyResponseDto
import com.runwithme.runwithme.api.entity.SurveyResponse
import com.runwithme.runwithme.api.repository.SurveyResponseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class SurveyResponseService(
    private val surveyResponseRepository: SurveyResponseRepository,
) {
    fun getSurveyResponsesByUserId(userId: UUID): List<SurveyResponseDto> = surveyResponseRepository.findByUserId(userId).map { toDto(it) }

    @Transactional
    fun createSurveyResponse(
        userId: UUID,
        request: CreateSurveyResponseDto,
    ): SurveyResponseDto {
        val surveyResponse =
            SurveyResponse(
                userId = userId,
                preferredDays = request.preferredDays,
                timeOfDay = request.timeOfDay,
                experienceLevel = request.experienceLevel,
                activityType = request.activityType,
                intensityPreference = request.intensityPreference,
                socialVibe = request.socialVibe,
                motivationType = request.motivationType,
                coachingStyle = request.coachingStyle,
                musicPreference = request.musicPreference,
                matchGenderPreference = request.matchGenderPreference,
            )
        val saved = surveyResponseRepository.save(surveyResponse)
        return toDto(saved)
    }

    @Transactional
    fun updateSurveyResponse(
        id: Long,
        userId: UUID,
        request: CreateSurveyResponseDto,
    ): SurveyResponseDto {
        val existing =
            surveyResponseRepository
                .findById(id)
                .orElseThrow { IllegalArgumentException("Survey response not found") }

        if (existing.userId != userId) {
            throw IllegalArgumentException("Unauthorized to update this survey response")
        }

        existing.preferredDays = request.preferredDays
        existing.timeOfDay = request.timeOfDay
        existing.experienceLevel = request.experienceLevel
        existing.activityType = request.activityType
        existing.intensityPreference = request.intensityPreference
        existing.socialVibe = request.socialVibe
        existing.motivationType = request.motivationType
        existing.coachingStyle = request.coachingStyle
        existing.musicPreference = request.musicPreference
        existing.matchGenderPreference = request.matchGenderPreference
        existing.updatedAt = OffsetDateTime.now()

        val saved = surveyResponseRepository.save(existing)
        return toDto(saved)
    }

    @Transactional
    fun deleteSurveyResponse(
        id: Long,
        userId: UUID,
    ) {
        val existing =
            surveyResponseRepository
                .findById(id)
                .orElseThrow { IllegalArgumentException("Survey response not found") }

        if (existing.userId != userId) {
            throw IllegalArgumentException("Unauthorized to delete this survey response")
        }

        surveyResponseRepository.delete(existing)
    }

    private fun toDto(entity: SurveyResponse): SurveyResponseDto =
        SurveyResponseDto(
            id = entity.id!!,
            userId = entity.userId!!,
            preferredDays = entity.preferredDays,
            timeOfDay = entity.timeOfDay,
            experienceLevel = entity.experienceLevel,
            activityType = entity.activityType,
            intensityPreference = entity.intensityPreference,
            socialVibe = entity.socialVibe,
            motivationType = entity.motivationType,
            coachingStyle = entity.coachingStyle,
            musicPreference = entity.musicPreference,
            matchGenderPreference = entity.matchGenderPreference,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
