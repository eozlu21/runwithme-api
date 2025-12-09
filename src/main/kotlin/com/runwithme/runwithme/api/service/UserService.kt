package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.CreateUserRequest
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.UpdateUserRequest
import com.runwithme.runwithme.api.dto.UserDto
import com.runwithme.runwithme.api.entity.User
import com.runwithme.runwithme.api.exception.DuplicateResourceException
import com.runwithme.runwithme.api.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun getUsers(
        page: Int,
        size: Int,
    ): PageResponse<UserDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("userId").ascending())
        val userPage = userRepository.findAll(pageRequest)
        return PageResponse.fromPage(userPage, UserDto::fromEntity)
    }

    fun getUserById(id: UUID): UserDto? = userRepository.findById(id).map(UserDto::fromEntity).orElse(null)

    fun getUserByUsername(username: String): UserDto? = userRepository.findByUsername(username).map(UserDto::fromEntity).orElse(null)

    fun getUserByEmail(email: String): UserDto? = userRepository.findByEmail(email).map(UserDto::fromEntity).orElse(null)

    fun getUserIdByUsername(username: String): UUID? =
        userRepository
            .findByUsername(username)
            .map { it.userId }
            .orElse(null)

    @Transactional
    fun createUser(request: CreateUserRequest): UserDto {
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
                passwordHash = hashPassword(request.password),
                createdAt = OffsetDateTime.now(),
            )
        val savedUser = userRepository.save(user)
        return UserDto.fromEntity(savedUser)
    }

    @Transactional
    fun updateUser(
        id: UUID,
        request: UpdateUserRequest,
    ): UserDto? {
        val user = userRepository.findById(id).orElse(null) ?: return null

        request.username?.let {
            if (it != user.username && userRepository.existsByUsername(it)) {
                throw DuplicateResourceException("username", "Username already exists")
            }
            user.username = it
        }

        request.email?.let {
            if (it != user.email && userRepository.existsByEmail(it)) {
                throw DuplicateResourceException("email", "Email already exists")
            }
            user.email = it
        }

        request.password?.let {
            user.passwordHash = hashPassword(it)
        }

        val updatedUser = userRepository.save(user)
        return UserDto.fromEntity(updatedUser)
    }

    @Transactional
    fun deleteUser(id: UUID): Boolean {
        if (!userRepository.existsById(id)) {
            return false
        }
        userRepository.deleteById(id)
        return true
    }

    private fun hashPassword(password: String): String = passwordEncoder.encode(password)

    fun verifyPassword(
        rawPassword: String,
        hashedPassword: String,
    ): Boolean = passwordEncoder.matches(rawPassword, hashedPassword)
}
