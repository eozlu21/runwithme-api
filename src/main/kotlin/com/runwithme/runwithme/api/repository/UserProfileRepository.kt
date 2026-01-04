package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.UserProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserProfileRepository : JpaRepository<UserProfile, UUID> {
    fun findByCityId(cityId: Int): List<UserProfile>

    fun findByCountryId(countryId: Int): List<UserProfile>

    fun findByPronouns(pronouns: String): List<UserProfile>

    fun findByCityIdAndPronouns(
        cityId: Int,
        pronouns: String,
    ): List<UserProfile>

    fun findByCountryIdAndPronouns(
        countryId: Int,
        pronouns: String,
    ): List<UserProfile>
}
