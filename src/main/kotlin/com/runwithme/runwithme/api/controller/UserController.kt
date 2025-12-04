package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.UpdateUserRequest
import com.runwithme.runwithme.api.dto.UserDto
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User management APIs")
class UserController(
    private val userService: UserService,
) {
    @GetMapping
    @Operation(
        summary = "Get all users with pagination",
        description = "Retrieves a paginated list of users. Default page size is 10.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved users",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getUsers(
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
    ): ResponseEntity<PageResponse<UserDto>> = ResponseEntity.ok(userService.getUsers(page, size))

    @GetMapping("/{id}")
    @Operation(
        summary = "Get user by ID",
        description = "Retrieves a single user by their ID",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved user",
                content = [Content(schema = Schema(implementation = UserDto::class))],
            ),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    fun getUserById(
        @Parameter(description = "User ID", example = "1")
        @PathVariable id: UUID,
    ): ResponseEntity<UserDto> {
        val user = userService.getUserById(id)
        return if (user != null) ResponseEntity.ok(user) else ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }

    @GetMapping("/username/{username}")
    @Operation(
        summary = "Get user by username",
        description = "Retrieves a single user by their username",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved user",
                content = [Content(schema = Schema(implementation = UserDto::class))],
            ),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    fun getUserByUsername(
        @Parameter(description = "Username", example = "johndoe")
        @PathVariable username: String,
    ): ResponseEntity<UserDto> {
        val user = userService.getUserByUsername(username)
        return if (user != null) ResponseEntity.ok(user) else ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }

    @GetMapping("/email/{email}")
    @Operation(
        summary = "Get user by email",
        description = "Retrieves a single user by their email address",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved user",
                content = [Content(schema = Schema(implementation = UserDto::class))],
            ),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    fun getUserByEmail(
        @Parameter(description = "Email", example = "john.doe@example.com")
        @PathVariable email: String,
    ): ResponseEntity<UserDto> {
        val user = userService.getUserByEmail(email)
        return if (user != null) ResponseEntity.ok(user) else ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }

    // Note: User creation is handled by /api/v1/auth/register endpoint
    // This keeps authentication flow separate and provides JWT tokens on registration

    @PutMapping("/{id}")
    @Operation(
        summary = "Update user",
        description =
            "Updates an existing user. Note: For password changes, consider implementing a separate " +
                "change-password endpoint that requires old password verification.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "User updated successfully",
                content = [Content(schema = Schema(implementation = UserDto::class))],
            ),
            ApiResponse(responseCode = "404", description = "User not found"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
        ],
    )
    fun updateUser(
        @Parameter(description = "User ID", example = "1")
        @PathVariable id: UUID,
        @RequestBody request: UpdateUserRequest,
    ): ResponseEntity<Any> =
        try {
            val user = userService.updateUser(id, request)
            if (user != null) {
                ResponseEntity.ok(user)
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            }
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete user",
        description = "Deletes a user by ID",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "User deleted successfully"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    fun deleteUser(
        @Parameter(description = "User ID", example = "1")
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        val deleted = userService.deleteUser(id)
        return if (deleted) {
            ResponseEntity
                .status(
                    HttpStatus.NO_CONTENT,
                ).build()
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
}
