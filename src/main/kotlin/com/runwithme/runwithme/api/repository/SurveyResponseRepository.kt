package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.SurveyResponse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SurveyResponseRepository : JpaRepository<SurveyResponse, Long> {
    fun findByUserId(userId: UUID): List<SurveyResponse>
}
