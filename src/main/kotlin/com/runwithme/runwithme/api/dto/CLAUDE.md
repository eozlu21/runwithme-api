# DTO Directory

Data Transfer Objects - request/response payloads for the API.

## Purpose

DTOs provide:
- Clean separation between API contracts and internal entities
- Request validation via Jakarta validation annotations
- Response shaping (hide internal fields, add computed fields)
- API versioning flexibility

## Files

| File | Contents |
|------|----------|
| `AuthDto.kt` | `LoginRequest`, `RegisterRequest`, `TokenResponse`, `RefreshTokenRequest` |
| `UserDto.kt` | `UserResponse`, `UpdateUserRequest` |
| `UserProfileDto.kt` | `UserProfileResponse`, `UpdateProfileRequest` |
| `RouteDto.kt` | `RouteResponse`, `CreateRouteRequest`, `RoutePointDto` |
| `RouteLikeDto.kt` | `RouteLikeResponse`, `LikeRouteRequest` |
| `FeedPostDto.kt` | `FeedPostResponse`, `CreateFeedPostRequest` |
| `FeedPostCommentDto.kt` | `CommentResponse`, `CreateCommentRequest` |
| `FeedPostLikeDto.kt` | `FeedPostLikeResponse` |
| `FriendshipDto.kt` | `FriendshipResponse`, `FriendRequestResponse`, `SendFriendRequestRequest` |
| `RunSessionDto.kt` | `RunSessionResponse`, `CreateRunSessionRequest`, `RunSessionPointDto` |
| `MessageDto.kt` | `MessageResponse`, `SendMessageRequest` |
| `PageResponse.kt` | Generic pagination wrapper for list responses |

## Conventions

- Request DTOs: suffix with `Request` (e.g., `CreateRouteRequest`)
- Response DTOs: suffix with `Response` (e.g., `RouteResponse`)
- Use `data class` for all DTOs
- Use Jakarta validation annotations: `@NotBlank`, `@Email`, `@Size`, `@Valid`
- Nullable fields for optional request parameters
- Extension functions on entities for `.toDto()` conversion

## Common Patterns

```kotlin
// Request DTO with validation
data class CreateResourceRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 100, message = "Name must be at most 100 characters")
    val name: String,

    @field:Size(max = 500, message = "Description must be at most 500 characters")
    val description: String? = null
)

// Response DTO
data class ResourceResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val createdAt: OffsetDateTime,
    val authorName: String  // computed/joined field
)

// Entity to DTO conversion
fun Resource.toDto(authorName: String) = ResourceResponse(
    id = this.id,
    name = this.name,
    description = this.description,
    createdAt = this.createdAt,
    authorName = authorName
)
```

## PageResponse Usage

```kotlin
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
```
