package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.AuthResponse
import com.runwithme.runwithme.api.dto.LoginRequest
import com.runwithme.runwithme.api.dto.RefreshTokenRequest
import com.runwithme.runwithme.api.dto.RegisterRequest
import com.runwithme.runwithme.api.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication APIs for login, register, and token refresh")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/register")
    @Operation(
        summary = "Register a new user",
        description = "Creates a new user account and returns JWT tokens",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully registered",
                content = [Content(schema = Schema(implementation = AuthResponse::class))],
            ),
            ApiResponse(responseCode = "400", description = "Username or email already exists"),
        ],
    )
    fun register(
        @RequestBody request: RegisterRequest,
    ): ResponseEntity<AuthResponse> = ResponseEntity.ok(authService.register(request))

    @PostMapping("/login")
    @Operation(
        summary = "Login",
        description = "Authenticates a user and returns JWT tokens",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully authenticated",
                content = [Content(schema = Schema(implementation = AuthResponse::class))],
            ),
            ApiResponse(responseCode = "401", description = "Invalid credentials"),
        ],
    )
    fun login(
        @RequestBody request: LoginRequest,
    ): ResponseEntity<AuthResponse> = ResponseEntity.ok(authService.login(request))

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh access token",
        description = "Generates a new access token using a valid refresh token",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully refreshed token",
                content = [Content(schema = Schema(implementation = AuthResponse::class))],
            ),
            ApiResponse(responseCode = "401", description = "Invalid or expired refresh token"),
        ],
    )
    fun refreshToken(
        @RequestBody request: RefreshTokenRequest,
    ): ResponseEntity<AuthResponse> = ResponseEntity.ok(authService.refreshToken(request.refreshToken))
}
