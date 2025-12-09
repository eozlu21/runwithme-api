package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.CreateCommentRequest
import com.runwithme.runwithme.api.dto.FeedPostCommentDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.UpdateCommentRequest
import com.runwithme.runwithme.api.service.FeedPostCommentService
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
@RequestMapping("/api/v1/feed-post-comments")
@Tag(name = "Feed Post Comments", description = "Feed post comments management APIs")
class FeedPostCommentController(
    private val feedPostCommentService: FeedPostCommentService,
    private val userService: UserService,
) {
    @GetMapping("/post/{postId}")
    @Operation(
        summary = "Get comments for a post with pagination",
        description = "Retrieves a paginated list of comments for a specific post.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved post comments",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getCommentsByPost(
        @Parameter(description = "Post ID", example = "1") @PathVariable postId: Long,
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
    ): ResponseEntity<PageResponse<FeedPostCommentDto>> = ResponseEntity.ok(feedPostCommentService.getCommentsByPost(postId, page, size))

    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Get comments by user with pagination",
        description = "Retrieves a paginated list of comments made by a specific user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved user comments",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getCommentsByUser(
        @Parameter(description = "User ID") @PathVariable userId: UUID,
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
    ): ResponseEntity<PageResponse<FeedPostCommentDto>> = ResponseEntity.ok(feedPostCommentService.getCommentsByUser(userId, page, size))

    @GetMapping("/{commentId}")
    @Operation(
        summary = "Get comment by ID",
        description = "Retrieves a single comment by its ID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved comment",
                content = [Content(schema = Schema(implementation = FeedPostCommentDto::class))],
            ),
            ApiResponse(responseCode = "404", description = "Comment not found"),
        ],
    )
    fun getCommentById(
        @Parameter(description = "Comment ID", example = "1") @PathVariable commentId: Long,
    ): ResponseEntity<FeedPostCommentDto> {
        val comment = feedPostCommentService.getCommentById(commentId)
        return if (comment != null) {
            ResponseEntity.ok(comment)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @GetMapping("/post/{postId}/count")
    @Operation(
        summary = "Get comment count for a post",
        description = "Returns the total number of comments for a specific post.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved comment count",
            ),
        ],
    )
    fun getCommentCount(
        @Parameter(description = "Post ID", example = "1") @PathVariable postId: Long,
    ): ResponseEntity<Long> = ResponseEntity.ok(feedPostCommentService.getCommentCount(postId))

    @PostMapping("/post/{postId}")
    @Operation(
        summary = "Create a comment on a post",
        description = "Creates a new comment on a post. The author is automatically set to the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Comment created successfully",
                content = [Content(schema = Schema(implementation = FeedPostCommentDto::class))],
            ),
            ApiResponse(responseCode = "400", description = "Invalid request or post not found"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
        ],
    )
    fun createComment(
        @Parameter(description = "Post ID", example = "1") @PathVariable postId: Long,
        @RequestBody request: CreateCommentRequest,
        principal: Principal,
    ): ResponseEntity<FeedPostCommentDto> {
        val userId =
            userService.getUserIdByUsername(principal.name)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val comment = feedPostCommentService.createComment(postId, request, userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    @PutMapping("/{commentId}")
    @Operation(
        summary = "Update a comment",
        description = "Updates an existing comment. Only the author can update their own comment.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Comment updated successfully",
                content = [Content(schema = Schema(implementation = FeedPostCommentDto::class))],
            ),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "403", description = "Forbidden - not the comment author"),
            ApiResponse(responseCode = "404", description = "Comment not found"),
        ],
    )
    fun updateComment(
        @Parameter(description = "Comment ID", example = "1") @PathVariable commentId: Long,
        @RequestBody request: UpdateCommentRequest,
        principal: Principal,
    ): ResponseEntity<FeedPostCommentDto> {
        val userId =
            userService.getUserIdByUsername(principal.name)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val updatedComment = feedPostCommentService.updateComment(commentId, request, userId)
        return ResponseEntity.ok(updatedComment)
    }

    @DeleteMapping("/{commentId}")
    @Operation(
        summary = "Delete a comment",
        description = "Deletes a comment. Only the author can delete their own comment.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Comment deleted successfully"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "403", description = "Forbidden - not the comment author"),
            ApiResponse(responseCode = "404", description = "Comment not found"),
        ],
    )
    fun deleteComment(
        @Parameter(description = "Comment ID", example = "1") @PathVariable commentId: Long,
        principal: Principal,
    ): ResponseEntity<Void> {
        val userId =
            userService.getUserIdByUsername(principal.name)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val deleted = feedPostCommentService.deleteComment(commentId, userId)
        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
}
