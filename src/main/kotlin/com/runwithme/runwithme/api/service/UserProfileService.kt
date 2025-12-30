package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.CreateUserProfileRequest
import com.runwithme.runwithme.api.dto.LimitedUserProfileDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.UpdateUserProfileRequest
import com.runwithme.runwithme.api.dto.UserProfileDto
import com.runwithme.runwithme.api.entity.UserProfile
import com.runwithme.runwithme.api.exception.UnauthorizedActionException
import com.runwithme.runwithme.api.repository.UserProfileRepository
import com.runwithme.runwithme.api.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserProfileService(
    private val userProfileRepository: UserProfileRepository,
    private val friendshipService: FriendshipService,
    private val userRepository: UserRepository,
) {
    fun getUserProfiles(
        page: Int,
        size: Int,
        viewerId: UUID?,
    ): PageResponse<Any> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("userId").ascending())
        val userProfilePage = userProfileRepository.findAll(pageRequest)

        // Map profiles to either full or limited based on visibility
        val profiles =
            userProfilePage.content.mapNotNull { profile ->
                profile.userId?.let { targetId ->
                    val canViewFull = friendshipService.canViewProfile(viewerId, targetId)

                    if (canViewFull) {
                        UserProfileDto.fromEntity(profile)
                    } else {
                        // Get username from User entity for limited view
                        userRepository.findById(targetId).orElse(null)?.let { user ->
                            LimitedUserProfileDto(
                                userId = targetId,
                                username = user.username,
                                profileVisibility = profile.profileVisibility,
                                isRestricted = true,
                            )
                        }
                    }
                }
            }

        return PageResponse(
            content = profiles,
            pageNumber = safePage,
            pageSize = safeSize,
            totalElements = profiles.size.toLong(),
            totalPages = (profiles.size + safeSize - 1) / safeSize,
            first = safePage == 0,
            last = safePage >= (profiles.size + safeSize - 1) / safeSize - 1,
        )
    }

    fun getUserProfileById(id: UUID): UserProfileDto? = userProfileRepository.findById(id).map(UserProfileDto::fromEntity).orElse(null)

    fun getUserProfileByIdWithVisibility(
        id: UUID,
        viewerId: UUID?,
    ): Any? {
        val userProfile = userProfileRepository.findById(id).orElse(null) ?: return null

        // Check if viewer can see full profile details
        val canViewFull = friendshipService.canViewProfile(viewerId, id)

        return if (canViewFull) {
            // Return full profile
            UserProfileDto.fromEntity(userProfile)
        } else {
            // Return limited profile with just username and visibility
            val user = userRepository.findById(id).orElse(null) ?: return null
            LimitedUserProfileDto(
                userId = id,
                username = user.username,
                profileVisibility = userProfile.profileVisibility,
                isRestricted = true,
            )
        }
    }

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
    fun deleteUserProfile(
        id: UUID,
        authenticatedUserId: UUID,
    ) {
        val userProfile =
            userProfileRepository.findById(id).orElse(null)
                ?: throw IllegalArgumentException("User profile not found")

        if (userProfile.userId != authenticatedUserId) {
            throw UnauthorizedActionException("You can only delete your own profile")
        }

        userProfileRepository.deleteById(id)
    }
}
