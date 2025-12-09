package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.CreateFeedPostRequest
import com.runwithme.runwithme.api.dto.FeedPostDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.UpdateFeedPostRequest
import com.runwithme.runwithme.api.service.FeedPostService
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
@RequestMapping("/api/v1/feed-posts")
@Tag(name = "Feed Posts", description = "Social feed post management APIs")
class FeedPostController(
    private val feedPostService: FeedPostService,
    private val userService: UserService,
) {
    @GetMapping("/public")
    @Operation(
        summary = "Get public feed",
        description = "Retrieves a paginated list of all public posts. No authentication required.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved public feed",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getPublicFeed(
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
        principal: Principal?,
    ): ResponseEntity<PageResponse<FeedPostDto>> {
        val currentUserId = principal?.name?.let { userService.getUserIdByUsername(it) }
        return ResponseEntity.ok(feedPostService.getPublicFeed(page, size, currentUserId))
    }

    @GetMapping("/feed")
    @Operation(
        summary = "Get personalized feed",
        description = "Retrieves a paginated personalized feed including public posts, own posts, and friends' posts.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved personalized feed",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
        ],
    )
    fun getPersonalizedFeed(
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
        principal: Principal,
    ): ResponseEntity<PageResponse<FeedPostDto>> {
        val userId =
            userService.getUserIdByUsername(principal.name)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(feedPostService.getPersonalizedFeed(userId, page, size))
    }

    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Get posts by user",
        description = "Retrieves a paginated list of posts by a specific user, filtered by visibility.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved user posts",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getPostsByUser(
        @Parameter(description = "User ID") @PathVariable userId: UUID,
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
        principal: Principal,
    ): ResponseEntity<PageResponse<FeedPostDto>> {
        val requesterId =
            userService.getUserIdByUsername(principal.name)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(feedPostService.getPostsByUser(userId, requesterId, page, size))
    }

    @GetMapping("/{postId}")
    @Operation(
        summary = "Get post by ID",
        description = "Retrieves a single post by its ID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved post",
                content = [Content(schema = Schema(implementation = FeedPostDto::class))],
            ),
            ApiResponse(responseCode = "404", description = "Post not found"),
        ],
    )
    fun getPostById(
        @Parameter(description = "Post ID", example = "1") @PathVariable postId: Long,
        principal: Principal?,
    ): ResponseEntity<FeedPostDto> {
        val currentUserId = principal?.name?.let { userService.getUserIdByUsername(it) }
        val post = feedPostService.getPostById(postId, currentUserId)
        return if (post != null) {
            ResponseEntity.ok(post)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @PostMapping
    @Operation(
        summary = "Create a new post",
        description = "Creates a new feed post. The author is automatically set to the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Post created successfully",
                content = [Content(schema = Schema(implementation = FeedPostDto::class))],
            ),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
        ],
    )
    fun createPost(
        @RequestBody request: CreateFeedPostRequest,
        principal: Principal,
    ): ResponseEntity<FeedPostDto> {
        val authorId =
            userService.getUserIdByUsername(principal.name)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val createdPost = feedPostService.createPost(request, authorId)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPost)
    }

    @PutMapping("/{postId}")
    @Operation(
        summary = "Update a post",
        description = "Updates an existing post. Only the author can update their own post.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Post updated successfully",
                content = [Content(schema = Schema(implementation = FeedPostDto::class))],
            ),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "403", description = "Forbidden - not the post author"),
            ApiResponse(responseCode = "404", description = "Post not found"),
        ],
    )
    fun updatePost(
        @Parameter(description = "Post ID", example = "1") @PathVariable postId: Long,
        @RequestBody request: UpdateFeedPostRequest,
        principal: Principal,
    ): ResponseEntity<FeedPostDto> {
        val userId =
            userService.getUserIdByUsername(principal.name)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val updatedPost = feedPostService.updatePost(postId, request, userId)
        return ResponseEntity.ok(updatedPost)
    }

    @DeleteMapping("/{postId}")
    @Operation(
        summary = "Delete a post",
        description = "Deletes a post. Only the author can delete their own post.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Post deleted successfully"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "403", description = "Forbidden - not the post author"),
            ApiResponse(responseCode = "404", description = "Post not found"),
        ],
    )
    fun deletePost(
        @Parameter(description = "Post ID", example = "1") @PathVariable postId: Long,
        principal: Principal,
    ): ResponseEntity<Void> {
        val userId =
            userService.getUserIdByUsername(principal.name)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val deleted = feedPostService.deletePost(postId, userId)
        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
}
