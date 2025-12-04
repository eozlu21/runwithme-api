package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.AuthResponse
import com.runwithme.runwithme.api.dto.LoginRequest
import com.runwithme.runwithme.api.dto.RefreshTokenRequest
import com.runwithme.runwithme.api.dto.RegisterRequest
import com.runwithme.runwithme.api.dto.RegisterResponse
import com.runwithme.runwithme.api.dto.ResendVerificationRequest
import com.runwithme.runwithme.api.dto.VerificationResponse
import com.runwithme.runwithme.api.service.AuthService
import com.runwithme.runwithme.api.service.VerificationPageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Tag(
    name = "Authentication",
    description = "Authentication APIs for login, register, and token refresh",
)
class AuthController(
    private val authService: AuthService,
    private val verificationPageService: VerificationPageService,
) {
    @PostMapping("/register")
    @Operation(
        summary = "Register a new user",
        description =
            "Creates a new user account and sends a verification email. " +
                "User must verify email before logging in.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description =
                        "Successfully registered. Verification email sent.",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            RegisterResponse::class,
                                    ),
                            ),
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "Username or email already exists",
                ),
            ],
    )
    fun register(
        @RequestBody request: RegisterRequest,
    ): ResponseEntity<RegisterResponse> = ResponseEntity.ok(authService.register(request))

    @PostMapping("/login")
    @Operation(
        summary = "Login",
        description =
            "Authenticates a user and returns JWT tokens. Email must be verified before login.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully authenticated",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            AuthResponse::class,
                                    ),
                            ),
                        ],
                ),
                ApiResponse(responseCode = "401", description = "Invalid credentials"),
                ApiResponse(responseCode = "403", description = "Email not verified"),
            ],
    )
    fun login(
        @RequestBody request: LoginRequest,
    ): ResponseEntity<AuthResponse> = ResponseEntity.ok(authService.login(request))

    @GetMapping("/verify-email", produces = [MediaType.TEXT_HTML_VALUE])
    @Operation(
        summary = "Verify email",
        description = "Verifies user's email address using the token sent via email",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Email verification result",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            VerificationResponse::class,
                                    ),
                            ),
                        ],
                ),
            ],
    )
    fun verifyEmail(
        @Parameter(description = "Verification token from email") @RequestParam token: String,
    ): ResponseEntity<String> {
        val result = authService.verifyEmail(token)
        val html = verificationPageService.buildVerificationHtml(result.success, result.message)
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html)
    }

    @PostMapping("/resend-verification")
    @Operation(
        summary = "Resend verification email",
        description = "Resends the email verification link to the user's email address",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Verification email resent (if account exists)",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            VerificationResponse::class,
                                    ),
                            ),
                        ],
                ),
            ],
    )
    fun resendVerification(
        @RequestBody request: ResendVerificationRequest,
    ): ResponseEntity<VerificationResponse> = ResponseEntity.ok(authService.resendVerificationEmail(request.email))

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh access token",
        description = "Generates a new access token using a valid refresh token",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully refreshed token",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            AuthResponse::class,
                                    ),
                            ),
                        ],
                ),
                ApiResponse(
                    responseCode = "401",
                    description = "Invalid or expired refresh token",
                ),
            ],
    )
    fun refreshToken(
        @RequestBody request: RefreshTokenRequest,
    ): ResponseEntity<AuthResponse> = ResponseEntity.ok(authService.refreshToken(request.refreshToken))
}
