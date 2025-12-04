package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.RouteLikeDto
import com.runwithme.runwithme.api.exception.UnauthorizedActionException
import com.runwithme.runwithme.api.service.RouteLikeService
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/route-likes")
@Tag(name = "Route Likes", description = "Route likes management APIs")
class RouteLikeController(
    private val routeLikeService: RouteLikeService,
    private val userService: UserService,
) {
    @GetMapping("/route/{routeId}")
    @Operation(
        summary = "Get likes for a route with pagination",
        description = "Retrieves a paginated list of likes for a specific route.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved route likes",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getLikesByRoute(
        @Parameter(description = "Route ID", example = "1")
        @PathVariable routeId: Long,
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
    ): ResponseEntity<PageResponse<RouteLikeDto>> =
        ResponseEntity.ok(routeLikeService.getLikesByRoute(routeId, page, size))

    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Get likes by user with pagination",
        description = "Retrieves a paginated list of likes made by a specific user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved user likes",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getLikesByUser(
        @Parameter(description = "User ID", example = "1")
        @PathVariable userId: Long,
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
    ): ResponseEntity<PageResponse<RouteLikeDto>> =
        ResponseEntity.ok(routeLikeService.getLikesByUser(userId, page, size))

    @GetMapping("/route/{routeId}/count")
    @Operation(
        summary = "Get like count for a route",
        description = "Returns the total number of likes for a specific route.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved like count",
            ),
        ],
    )
    fun getLikeCount(
        @Parameter(description = "Route ID", example = "1")
        @PathVariable routeId: Long,
    ): ResponseEntity<Map<String, Long>> {
        val count = routeLikeService.getLikeCount(routeId)
        return ResponseEntity.ok(mapOf("routeId" to routeId, "likeCount" to count))
    }

    @GetMapping("/route/{routeId}/check")
    @Operation(
        summary = "Check if user liked a route",
        description = "Checks if the authenticated user has liked a specific route.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully checked like status",
            ),
        ],
    )
    fun checkIfLiked(
        @Parameter(description = "Route ID", example = "1")
        @PathVariable routeId: Long,
        principal: Principal,
    ): ResponseEntity<Map<String, Any>> {
        val authenticatedUserId =
            userService
                .getUserIdByUsername(principal.name)
                ?: throw UnauthorizedActionException("Authenticated user not found")
        val isLiked = routeLikeService.isRouteLikedByUser(routeId, authenticatedUserId)
        return ResponseEntity.ok(mapOf("routeId" to routeId, "isLiked" to isLiked))
    }

    @PostMapping("/route/{routeId}")
    @Operation(
        summary = "Like a route",
        description = "Adds a like to a route for the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Route liked successfully",
                content = [Content(schema = Schema(implementation = RouteLikeDto::class))],
            ),
            ApiResponse(responseCode = "400", description = "Invalid request or already liked"),
            ApiResponse(responseCode = "404", description = "Route not found"),
        ],
    )
    fun likeRoute(
        @Parameter(description = "Route ID", example = "1")
        @PathVariable routeId: Long,
        principal: Principal,
    ): ResponseEntity<RouteLikeDto> {
        val authenticatedUserId =
            userService
                .getUserIdByUsername(principal.name)
                ?: throw UnauthorizedActionException("Authenticated user not found")
        val like = routeLikeService.likeRoute(routeId, authenticatedUserId)
        return ResponseEntity.status(HttpStatus.CREATED).body(like)
    }

    @DeleteMapping("/route/{routeId}")
    @Operation(
        summary = "Unlike a route",
        description = "Removes a like from a route for the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Route unliked successfully"),
            ApiResponse(responseCode = "404", description = "Like not found"),
        ],
    )
    fun unlikeRoute(
        @Parameter(description = "Route ID", example = "1")
        @PathVariable routeId: Long,
        principal: Principal,
    ): ResponseEntity<Void> {
        val authenticatedUserId =
            userService
                .getUserIdByUsername(principal.name)
                ?: throw UnauthorizedActionException("Authenticated user not found")
        val deleted = routeLikeService.unlikeRoute(routeId, authenticatedUserId)
        return if (deleted) {
            ResponseEntity.status(HttpStatus.NO_CONTENT).build()
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
}
