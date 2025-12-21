package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.UserStatisticsResponse
import com.runwithme.runwithme.api.service.UserStatisticsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Statistics", description = "User statistics APIs")
class UserStatisticsController(
    private val userStatisticsService: UserStatisticsService,
) {
    @GetMapping("/{userId}/statistics")
    @Operation(summary = "Get user statistics", description = "Get statistics for a user (average pace, runs per week, etc.)")
    fun getUserStatistics(
        @PathVariable userId: UUID,
    ): ResponseEntity<UserStatisticsResponse> {
        val stats = userStatisticsService.getUserStatistics(userId)
        return ResponseEntity.ok(stats)
    }
}
