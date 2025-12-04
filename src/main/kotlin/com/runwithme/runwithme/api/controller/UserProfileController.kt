package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.CreateUserProfileRequest
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.UpdateUserProfileRequest
import com.runwithme.runwithme.api.dto.UserProfileDto
import com.runwithme.runwithme.api.exception.UnauthorizedActionException
import com.runwithme.runwithme.api.service.UserProfileService
import com.runwithme.runwithme.api.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/user-profiles")
@Tag(name = "User Profiles", description = "User profile management APIs")
class UserProfileController(
    private val userProfileService: UserProfileService,
    private val userService: UserService,
) {
    @GetMapping
    @Operation(
        summary = "Get all user profiles with pagination",
        description = "Retrieves a paginated list of user profiles. Default page size is 10.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved user profiles",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getUserProfiles(
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
    ): ResponseEntity<PageResponse<UserProfileDto>> = ResponseEntity.ok(userProfileService.getUserProfiles(page, size))

    @GetMapping("/{id}")
    @Operation(
        summary = "Get user profile by user ID",
        description = "Retrieves a single user profile by user ID",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved user profile",
                content = [Content(schema = Schema(implementation = UserProfileDto::class))],
            ),
            ApiResponse(responseCode = "404", description = "User profile not found"),
        ],
    )
    fun getUserProfileById(
        @Parameter(description = "User ID", example = "1")
        @PathVariable id: UUID,
    ): ResponseEntity<UserProfileDto> {
        val userProfile = userProfileService.getUserProfileById(id)
        return if (userProfile != null) {
            ResponseEntity.ok(userProfile)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @PostMapping
    @Operation(
        summary = "Create a new user profile",
        description = "Creates a new user profile; only the authenticated user can create their own profile.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "User profile created successfully",
                content = [Content(schema = Schema(implementation = UserProfileDto::class))],
            ),
            ApiResponse(responseCode = "400", description = "Invalid request or user profile already exists"),
            ApiResponse(responseCode = "403", description = "Cannot create a profile for another user"),
        ],
    )
    fun createUserProfile(
        @RequestBody request: CreateUserProfileRequest,
        principal: Principal,
    ): ResponseEntity<UserProfileDto> {
        val authenticatedUserId =
            userService
                .getUserIdByUsername(principal.name)
                ?: throw UnauthorizedActionException("Authenticated user not found")
        val userProfile = userProfileService.createUserProfile(request, authenticatedUserId)
        return ResponseEntity.status(HttpStatus.CREATED).body(userProfile)
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update user profile",
        description = "Updates an existing user profile",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "User profile updated successfully",
                content = [Content(schema = Schema(implementation = UserProfileDto::class))],
            ),
            ApiResponse(responseCode = "404", description = "User profile not found"),
            ApiResponse(responseCode = "403", description = "Cannot update a profile for another user"),
        ],
    )
    fun updateUserProfile(
        @Parameter(description = "User ID", example = "1")
        @PathVariable id: UUID,
        @RequestBody request: UpdateUserProfileRequest,
        principal: Principal,
    ): ResponseEntity<UserProfileDto> {
        val authenticatedUserId =
            userService
                .getUserIdByUsername(principal.name)
                ?: throw UnauthorizedActionException("Authenticated user not found")
        val userProfile = userProfileService.updateUserProfile(id, request, authenticatedUserId)
        return if (userProfile != null) {
            ResponseEntity.ok(userProfile)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete user profile",
        description = "Deletes a user profile by user ID",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "User profile deleted successfully"),
            ApiResponse(responseCode = "404", description = "User profile not found"),
        ],
    )
    fun deleteUserProfile(
        @Parameter(description = "User ID", example = "1")
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        val deleted = userProfileService.deleteUserProfile(id)
        return if (deleted) {
            ResponseEntity.status(HttpStatus.NO_CONTENT).build()
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
}
