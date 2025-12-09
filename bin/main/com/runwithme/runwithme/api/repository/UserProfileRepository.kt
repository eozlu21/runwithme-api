package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.UserProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserProfileRepository : JpaRepository<UserProfile, UUID>
