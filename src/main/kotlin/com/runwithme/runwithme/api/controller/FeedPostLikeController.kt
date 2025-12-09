package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.FeedPostLikeDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.service.FeedPostLikeService
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
import java.util.UUID

@RestController
@RequestMapping("/api/v1/feed-post-likes")
@Tag(name = "Feed Post Likes", description = "Feed post likes management APIs")
class FeedPostLikeController(
    private val feedPostLikeService: FeedPostLikeService,
    private val userService: UserService,
) {
    @GetMapping("/post/{postId}")
    @Operation(
        summary = "Get likes for a post with pagination",
        description = "Retrieves a paginated list of likes for a specific post.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved post likes",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getLikesByPost(
        @Parameter(description = "Post ID", example = "1") @PathVariable postId: Long,
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
    ): ResponseEntity<PageResponse<FeedPostLikeDto>> = ResponseEntity.ok(feedPostLikeService.getLikesByPost(postId, page, size))

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
        @Parameter(description = "User ID") @PathVariable userId: UUID,
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
    ): ResponseEntity<PageResponse<FeedPostLikeDto>> = ResponseEntity.ok(feedPostLikeService.getLikesByUser(userId, page, size))

    @GetMapping("/post/{postId}/count")
    @Operation(
        summary = "Get like count for a post",
        description = "Returns the total number of likes for a specific post.",
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
        @Parameter(description = "Post ID", example = "1") @PathVariable postId: Long,
    ): ResponseEntity<Long> = ResponseEntity.ok(feedPostLikeService.getLikeCount(postId))

    @GetMapping("/post/{postId}/check")
    @Operation(
        summary = "Check if current user liked a post",
        description = "Returns whether the authenticated user has liked a specific post.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully checked like status",
            ),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
        ],
    )
    fun checkIfLiked(
        @Parameter(description = "Post ID", example = "1") @PathVariable postId: Long,
        principal: Principal,
    ): ResponseEntity<Boolean> {
        val userId =
            userService.getUserIdByUsername(principal.name)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(feedPostLikeService.isPostLikedByUser(postId, userId))
    }

    @PostMapping("/post/{postId}")
    @Operation(
        summary = "Like a post",
        description = "Likes a post for the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Post liked successfully",
                content = [Content(schema = Schema(implementation = FeedPostLikeDto::class))],
            ),
            ApiResponse(responseCode = "400", description = "Already liked or post not found"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
        ],
    )
    fun likePost(
        @Parameter(description = "Post ID", example = "1") @PathVariable postId: Long,
        principal: Principal,
    ): ResponseEntity<FeedPostLikeDto> {
        val userId =
            userService.getUserIdByUsername(principal.name)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val like = feedPostLikeService.likePost(postId, userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(like)
    }

    @DeleteMapping("/post/{postId}")
    @Operation(
        summary = "Unlike a post",
        description = "Removes a like from a post for the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Post unliked successfully"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "Like not found"),
        ],
    )
    fun unlikePost(
        @Parameter(description = "Post ID", example = "1") @PathVariable postId: Long,
        principal: Principal,
    ): ResponseEntity<Void> {
        val userId =
            userService.getUserIdByUsername(principal.name)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val deleted = feedPostLikeService.unlikePost(postId, userId)
        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
}
