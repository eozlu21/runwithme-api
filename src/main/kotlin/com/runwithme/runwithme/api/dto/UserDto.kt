package com.runwithme.runwithme.api.dto

import com.runwithme.runwithme.api.entity.User
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "User data transfer object")
data class UserDto(
    @Schema(description = "User unique identifier", example = "1")
    val userId: Long,
    @Schema(description = "Username", example = "johndoe")
    val username: String,
    @Schema(description = "Email address", example = "john.doe@example.com")
    val email: String,
    @Schema(description = "Account creation timestamp", example = "2024-01-15T10:30:00Z")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun fromEntity(user: User): UserDto =
            UserDto(
                userId = user.userId!!,
                username = user.username,
                email = user.email,
                createdAt = user.createdAt,
            )
    }
}

@Schema(description = "User creation request")
data class CreateUserRequest(
    @Schema(description = "Username", example = "johndoe", required = true)
    val username: String,
    @Schema(description = "Email address", example = "john.doe@example.com", required = true)
    val email: String,
    @Schema(description = "Password", example = "SecureP@ss123", required = true)
    val password: String,
)

@Schema(description = "User update request")
data class UpdateUserRequest(
    @Schema(description = "Username", example = "johndoe")
    val username: String?,
    @Schema(description = "Email address", example = "john.doe@example.com")
    val email: String?,
    @Schema(description = "Password", example = "NewSecureP@ss123")
    val password: String?,
)
