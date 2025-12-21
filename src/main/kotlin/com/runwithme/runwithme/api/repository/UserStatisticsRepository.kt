package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.UserStatistics
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserStatisticsRepository : JpaRepository<UserStatistics, UUID>
