package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.config.CacheConfig
import com.runwithme.runwithme.api.dto.CreateUserProfileRequest
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.UpdateUserProfileRequest
import com.runwithme.runwithme.api.dto.UserProfileDto
import com.runwithme.runwithme.api.entity.UserProfile
import com.runwithme.runwithme.api.exception.UnauthorizedActionException
import com.runwithme.runwithme.api.repository.UserProfileRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserProfileService(
    private val userProfileRepository: UserProfileRepository,
) {
    fun getUserProfiles(
        page: Int,
        size: Int,
    ): PageResponse<UserProfileDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("userId").ascending())
        val userProfilePage = userProfileRepository.findAll(pageRequest)
        return PageResponse.fromPage(userProfilePage, UserProfileDto::fromEntity)
    }

    @Cacheable(value = [CacheConfig.USER_PROFILE_CACHE], key = "#id")
    fun getUserProfileById(id: UUID): UserProfileDto? = userProfileRepository.findById(id).map(UserProfileDto::fromEntity).orElse(null)

    @Transactional
    fun createUserProfile(
        request: CreateUserProfileRequest,
        authenticatedUserId: UUID,
    ): UserProfileDto {
        if (request.userId != authenticatedUserId) {
            throw UnauthorizedActionException("You can only create your own profile")
        }
        if (userProfileRepository.existsById(request.userId)) {
            throw IllegalArgumentException("User profile already exists for user ID: ${request.userId}")
        }

        val userProfile =
            UserProfile(
                userId = request.userId,
                firstName = request.firstName,
                lastName = request.lastName,
                pronouns = request.pronouns,
                birthday = request.birthday,
                expertLevel = request.expertLevel,
                profilePic = request.profilePic,
                profileVisibility = request.profileVisibility,
                regionId = request.regionId,
                subregionId = request.subregionId,
                countryId = request.countryId,
                stateId = request.stateId,
                cityId = request.cityId,
            )
        val savedProfile = userProfileRepository.save(userProfile)
        return UserProfileDto.fromEntity(savedProfile)
    }

    @Transactional
    @CacheEvict(value = [CacheConfig.USER_PROFILE_CACHE], key = "#id")
    fun updateUserProfile(
        id: UUID,
        request: UpdateUserProfileRequest,
        authenticatedUserId: UUID,
    ): UserProfileDto? {
        val userProfile = userProfileRepository.findById(id).orElse(null) ?: return null
        if (userProfile.userId != authenticatedUserId) {
            throw UnauthorizedActionException("You can only update your own profile")
        }

        request.firstName?.let { userProfile.firstName = it }
        request.lastName?.let { userProfile.lastName = it }
        request.pronouns?.let { userProfile.pronouns = it }
        request.birthday?.let { userProfile.birthday = it }
        request.expertLevel?.let { userProfile.expertLevel = it }
        request.profilePic?.let { userProfile.profilePic = it }
        request.profileVisibility?.let { userProfile.profileVisibility = it }
        request.regionId?.let { userProfile.regionId = it }
        request.subregionId?.let { userProfile.subregionId = it }
        request.countryId?.let { userProfile.countryId = it }
        request.stateId?.let { userProfile.stateId = it }
        request.cityId?.let { userProfile.cityId = it }

        val updatedProfile = userProfileRepository.save(userProfile)
        return UserProfileDto.fromEntity(updatedProfile)
    }

    @Transactional
    @CacheEvict(value = [CacheConfig.USER_PROFILE_CACHE], key = "#id")
    fun deleteUserProfile(id: UUID): Boolean {
        if (!userProfileRepository.existsById(id)) {
            return false
        }
        userProfileRepository.deleteById(id)
        return true
    }
}
