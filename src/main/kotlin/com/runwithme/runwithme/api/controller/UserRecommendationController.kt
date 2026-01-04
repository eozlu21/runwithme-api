package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.LocationFilterLevel
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.UserRecommendationDto
import com.runwithme.runwithme.api.dto.UserSimilarityDetailDto
import com.runwithme.runwithme.api.service.UserRecommendationService
import com.runwithme.runwithme.api.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/recommendations")
@Tag(name = "Recommendations", description = "User recommendation APIs based on similarity scores")
class UserRecommendationController(
    private val userRecommendationService: UserRecommendationService,
    private val userService: UserService,
) {
    @GetMapping("/users")
    @Operation(
        summary = "Get recommended users",
        description = """
            Get users recommended based on route similarity and preference matching.

            The combined score is calculated as:
            - 50% route similarity (average across all route pairs)
            - 50% survey preference matching

            Pre-filters applied:
            - Location (based on locationLevel parameter)
            - Schedule compatibility (preferred days and time of day)
            - Gender preference (if user has matchGenderPreference = true)
            - Excludes existing friends
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved recommendations",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized",
            ),
        ],
    )
    fun getRecommendedUsers(
        @Parameter(description = "Page number (0-indexed)")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Page size")
        @RequestParam(defaultValue = "10")
        size: Int,
        @Parameter(description = "Location filter level: CITY (default), COUNTRY, or NONE")
        @RequestParam(defaultValue = "CITY")
        locationLevel: LocationFilterLevel,
        principal: Principal,
    ): ResponseEntity<PageResponse<UserRecommendationDto>> {
        val userId = getUserIdFromPrincipal(principal)
        val result = userRecommendationService.getRecommendations(userId, page, size, locationLevel)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/users/{targetUserId}/similarity")
    @Operation(
        summary = "Get similarity with a specific user",
        description = """
            Get detailed similarity breakdown with another user.

            Returns:
            - Combined similarity score
            - Route similarity breakdown (average score, pair count, top pairs)
            - Preference similarity breakdown (each field with match status)
            - Friendship status
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved similarity details",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Target user not found",
            ),
        ],
    )
    fun getSimilarityWithUser(
        @Parameter(description = "Target user ID")
        @PathVariable
        targetUserId: UUID,
        principal: Principal,
    ): ResponseEntity<UserSimilarityDetailDto> {
        val userId = getUserIdFromPrincipal(principal)
        val result = userRecommendationService.getSimilarityWithUser(userId, targetUserId)
        return ResponseEntity.ok(result)
    }

    private fun getUserIdFromPrincipal(principal: Principal): UUID {
        val user =
            userService.getUserByUsername(principal.name)
                ?: throw NoSuchElementException("User not found")
        return user.userId
    }
}
