package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "survey_responses")
open class SurveyResponse(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    open var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    open var userId: UUID? = null,
    @Column(name = "preferred_days")
    open var preferredDays: String? = null,
    @Column(name = "time_of_day")
    open var timeOfDay: String? = null,
    @Column(name = "experience_level")
    open var experienceLevel: String? = null,
    @Column(name = "activity_type")
    open var activityType: String? = null,
    @Column(name = "intensity_preference")
    open var intensityPreference: String? = null,
    @Column(name = "social_vibe")
    open var socialVibe: String? = null,
    @Column(name = "motivation_type")
    open var motivationType: String? = null,
    @Column(name = "coaching_style")
    open var coachingStyle: String? = null,
    @Column(name = "music_preference")
    open var musicPreference: String? = null,
    @Column(name = "match_gender_preference")
    open var matchGenderPreference: Boolean? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
