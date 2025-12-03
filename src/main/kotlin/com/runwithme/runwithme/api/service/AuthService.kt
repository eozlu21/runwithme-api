package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.AuthResponse
import com.runwithme.runwithme.api.dto.LoginRequest
import com.runwithme.runwithme.api.dto.RegisterRequest
import com.runwithme.runwithme.api.dto.RegisterResponse
import com.runwithme.runwithme.api.dto.UserDto
import com.runwithme.runwithme.api.dto.VerificationResponse
import com.runwithme.runwithme.api.entity.User
import com.runwithme.runwithme.api.exception.DuplicateResourceException
import com.runwithme.runwithme.api.exception.EmailNotVerifiedException
import com.runwithme.runwithme.api.repository.UserRepository
import com.runwithme.runwithme.api.security.JwtTokenProvider
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authenticationManager: AuthenticationManager,
    private val emailService: EmailService,
) {
    @Transactional
    fun register(request: RegisterRequest): RegisterResponse {
        if (userRepository.existsByUsername(request.username)) {
            throw DuplicateResourceException("username", "Username already exists")
        }
        if (userRepository.existsByEmail(request.email)) {
            throw DuplicateResourceException("email", "Email already exists")
        }

        val user =
            User(
                username = request.username,
                email = request.email,
                passwordHash = passwordEncoder.encode(request.password),
                createdAt = OffsetDateTime.now(),
                emailVerified = false,
            )

        val savedUser = userRepository.save(user)

        // Create and send verification token
        val verificationToken = emailService.createVerificationToken(savedUser)
        emailService.sendVerificationEmail(savedUser, verificationToken.token)

        // Mask email for response (e.g., j***n@example.com)
        val maskedEmail = maskEmail(savedUser.email)

        return RegisterResponse(
            message =
                "Registration successful. Please check your email to verify your account.",
            emailVerificationRequired = true,
            email = maskedEmail,
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(
                request.username,
                request.password,
            ),
        )

        val user =
            userRepository.findByUsername(request.username).orElseThrow {
                IllegalArgumentException("Invalid username or password")
            }

        // Check if email is verified
        if (!user.emailVerified) {
            throw EmailNotVerifiedException()
        }

        val accessToken = jwtTokenProvider.generateToken(user.username)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.username)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = UserDto.fromEntity(user),
        )
    }

    @Transactional
    fun verifyEmail(token: String): VerificationResponse {
        val verificationToken =
            emailService.findByToken(token)
                ?: return VerificationResponse(
                    message = "Invalid verification token.",
                    success = false,
                )

        val user = verificationToken.user!!

        // Check if already verified
        if (user.emailVerified) {
            return VerificationResponse(
                message = "Your email is already verified. You can log in.",
                success = true,
            )
        }

        if (verificationToken.isExpired()) {
            emailService.deleteToken(verificationToken)
            return VerificationResponse(
                message = "Verification token has expired. Please request a new one.",
                success = false,
            )
        }

        user.emailVerified = true
        userRepository.save(user)

        emailService.deleteToken(verificationToken)

        return VerificationResponse(
            message = "Email verified successfully. You can now log in.",
            success = true,
        )
    }

    @Transactional
    fun resendVerificationEmail(email: String): VerificationResponse {
        val user =
            userRepository.findByEmail(email).orElse(null)
                ?: return VerificationResponse(
                    message =
                        "If an account exists with this email, a verification link has been sent.",
                    success = true,
                )

        if (user.emailVerified) {
            return VerificationResponse(
                message = "Email is already verified. You can log in.",
                success = true,
            )
        }

        val verificationToken = emailService.createVerificationToken(user)
        emailService.sendVerificationEmail(user, verificationToken.token)

        return VerificationResponse(
            message =
                "If an account exists with this email, a verification link has been sent.",
            success = true,
        )
    }

    fun refreshToken(refreshToken: String): AuthResponse {
        if (jwtTokenProvider.isTokenExpired(refreshToken)) {
            throw IllegalArgumentException("Refresh token is expired")
        }

        val username = jwtTokenProvider.getUsernameFromToken(refreshToken)
        val user =
            userRepository.findByUsername(username).orElseThrow {
                IllegalArgumentException("User not found")
            }

        val newAccessToken = jwtTokenProvider.generateToken(user.username)
        val newRefreshToken = jwtTokenProvider.generateRefreshToken(user.username)

        return AuthResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            user = UserDto.fromEntity(user),
        )
    }

    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return email

        val localPart = parts[0]
        val domain = parts[1]

        val maskedLocal =
            if (localPart.length <= 2) {
                "${localPart.first()}***"
            } else {
                "${localPart.first()}***${localPart.last()}"
            }

        return "$maskedLocal@$domain"
    }
}
