package com.runwithme.runwithme.api.dto

import com.runwithme.runwithme.api.entity.UserProfile
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID

@Schema(description = "User profile data transfer object")
data class UserProfileDto(
    @Schema(description = "User unique identifier", example = "1")
    val userId: UUID,
    @Schema(description = "First name", example = "John")
    val firstName: String?,
    @Schema(description = "Last name", example = "Doe")
    val lastName: String?,
    @Schema(description = "Pronouns", example = "he/him")
    val pronouns: String?,
    @Schema(description = "Birthday", example = "1990-05-15")
    val birthday: LocalDate?,
    @Schema(description = "Expert level", example = "intermediate")
    val expertLevel: String?,
    @Schema(description = "Profile picture URL", example = "https://example.com/pic.jpg")
    val profilePic: String?,
    @Schema(
        description = "Profile visibility setting: PUBLIC, FRIENDS_ONLY, FRIENDS_OF_FRIENDS, or PRIVATE",
        example = "PUBLIC",
    )
    val profileVisibility: String,
    @Schema(description = "Region ID", example = "1")
    val regionId: Int?,
    @Schema(description = "Subregion ID", example = "10")
    val subregionId: Int?,
    @Schema(description = "Country ID", example = "100")
    val countryId: Int?,
    @Schema(description = "State ID", example = "34")
    val stateId: Int?,
    @Schema(description = "City ID", example = "2344")
    val cityId: Int?,
) {
    companion object {
        fun fromEntity(userProfile: UserProfile): UserProfileDto =
            UserProfileDto(
                userId = userProfile.userId!!,
                firstName = userProfile.firstName,
                lastName = userProfile.lastName,
                pronouns = userProfile.pronouns,
                birthday = userProfile.birthday,
                expertLevel = userProfile.expertLevel,
                profilePic = userProfile.profilePic,
                profileVisibility = userProfile.profileVisibility,
                regionId = userProfile.regionId,
                subregionId = userProfile.subregionId,
                countryId = userProfile.countryId,
                stateId = userProfile.stateId,
                cityId = userProfile.cityId,
            )
    }
}

@Schema(description = "User profile creation request")
data class CreateUserProfileRequest(
    @Schema(description = "User ID", example = "1", required = true)
    val userId: UUID,
    @Schema(description = "First name", example = "John")
    val firstName: String?,
    @Schema(description = "Last name", example = "Doe")
    val lastName: String?,
    @Schema(description = "Pronouns", example = "he/him")
    val pronouns: String?,
    @Schema(description = "Birthday", example = "1990-05-15")
    val birthday: LocalDate?,
    @Schema(description = "Expert level", example = "intermediate")
    val expertLevel: String?,
    @Schema(description = "Profile picture URL", example = "https://example.com/pic.jpg")
    val profilePic: String?,
    @Schema(
        description = "Profile visibility setting: PUBLIC, FRIENDS_ONLY, FRIENDS_OF_FRIENDS, or PRIVATE",
        example = "PUBLIC",
    )
    val profileVisibility: String = "PUBLIC",
    @Schema(description = "Region ID", example = "1")
    val regionId: Int?,
    @Schema(description = "Subregion ID", example = "10")
    val subregionId: Int?,
    @Schema(description = "Country ID", example = "100")
    val countryId: Int?,
    @Schema(description = "State ID", example = "34")
    val stateId: Int?,
    @Schema(description = "City ID", example = "2344")
    val cityId: Int?,
)

@Schema(description = "User profile update request")
data class UpdateUserProfileRequest(
    @Schema(description = "First name", example = "John")
    val firstName: String?,
    @Schema(description = "Last name", example = "Doe")
    val lastName: String?,
    @Schema(description = "Pronouns", example = "he/him")
    val pronouns: String?,
    @Schema(description = "Birthday", example = "1990-05-15")
    val birthday: LocalDate?,
    @Schema(description = "Expert level", example = "intermediate")
    val expertLevel: String?,
    @Schema(description = "Profile picture URL", example = "https://example.com/pic.jpg")
    val profilePic: String?,
    @Schema(
        description = "Profile visibility setting: PUBLIC, FRIENDS_ONLY, FRIENDS_OF_FRIENDS, or PRIVATE",
        example = "PUBLIC",
    )
    val profileVisibility: String?,
    @Schema(description = "Region ID", example = "1")
    val regionId: Int?,
    @Schema(description = "Subregion ID", example = "10")
    val subregionId: Int?,
    @Schema(description = "Country ID", example = "100")
    val countryId: Int?,
    @Schema(description = "State ID", example = "34")
    val stateId: Int?,
    @Schema(description = "City ID", example = "2344")
    val cityId: Int?,
)
