package com.runwithme.runwithme.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Login request")
data class LoginRequest(
    @Schema(description = "Username", example = "johndoe") val username: String,
    @Schema(description = "Password", example = "password123") val password: String,
)

@Schema(description = "Register request")
data class RegisterRequest(
    @Schema(description = "Username", example = "johndoe") val username: String,
    @Schema(description = "Email address", example = "john@example.com") val email: String,
    @Schema(description = "Password", example = "password123") val password: String,
)

@Schema(description = "Authentication response with JWT tokens")
data class AuthResponse(
    @Schema(description = "Access token (JWT)") val accessToken: String,
    @Schema(description = "Refresh token (JWT)") val refreshToken: String,
    @Schema(description = "Token type", example = "Bearer") val tokenType: String = "Bearer",
    @Schema(description = "User information") val user: UserDto,
)

@Schema(description = "Refresh token request")
data class RefreshTokenRequest(
    @Schema(description = "Refresh token") val refreshToken: String,
)

@Schema(description = "Registration response before email verification")
data class RegisterResponse(
    @Schema(description = "Success message") val message: String,
    @Schema(description = "Whether email verification is required")
    val emailVerificationRequired: Boolean = true,
    @Schema(description = "User email (masked)") val email: String,
)

@Schema(description = "Resend verification email request")
data class ResendVerificationRequest(
    @Schema(description = "Email address", example = "john@example.com") val email: String,
)

@Schema(description = "Email verification response")
data class VerificationResponse(
    @Schema(description = "Success/failure message") val message: String,
    @Schema(description = "Whether verification was successful") val success: Boolean,
)
