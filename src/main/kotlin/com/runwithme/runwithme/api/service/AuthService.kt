package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.AuthResponse
import com.runwithme.runwithme.api.dto.LoginRequest
import com.runwithme.runwithme.api.dto.RegisterRequest
import com.runwithme.runwithme.api.dto.UserDto
import com.runwithme.runwithme.api.entity.User
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
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByUsername(request.username)) {
            throw IllegalArgumentException("Username already exists")
        }
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already exists")
        }

        val user =
            User(
                username = request.username,
                email = request.email,
                passwordHash = passwordEncoder.encode(request.password),
                createdAt = OffsetDateTime.now(),
            )

        val savedUser = userRepository.save(user)
        val accessToken = jwtTokenProvider.generateToken(savedUser.username)
        val refreshToken = jwtTokenProvider.generateRefreshToken(savedUser.username)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = UserDto.fromEntity(savedUser),
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
            userRepository
                .findByUsername(request.username)
                .orElseThrow { IllegalArgumentException("Invalid username or password") }

        val accessToken = jwtTokenProvider.generateToken(user.username)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.username)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = UserDto.fromEntity(user),
        )
    }

    fun refreshToken(refreshToken: String): AuthResponse {
        if (jwtTokenProvider.isTokenExpired(refreshToken)) {
            throw IllegalArgumentException("Refresh token is expired")
        }

        val username = jwtTokenProvider.getUsernameFromToken(refreshToken)
        val user =
            userRepository
                .findByUsername(username)
                .orElseThrow { IllegalArgumentException("User not found") }

        val newAccessToken = jwtTokenProvider.generateToken(user.username)
        val newRefreshToken = jwtTokenProvider.generateRefreshToken(user.username)

        return AuthResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            user = UserDto.fromEntity(user),
        )
    }
}
