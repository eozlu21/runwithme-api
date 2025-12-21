# Service Directory

Business logic layer - implements core application functionality.

## Purpose

Services contain all business logic and orchestrate:
- Data validation beyond simple input validation
- Business rules enforcement
- Coordination between multiple repositories
- Transaction management
- External service integration (email, S3)

## Files

| File | Responsibility |
|------|----------------|
| `AuthService.kt` | Login, registration, JWT token generation, email verification |
| `UserService.kt` | User account CRUD, password management |
| `UserProfileService.kt` | Profile operations, visibility settings |
| `RouteService.kt` | Route CRUD, geospatial queries |
| `RouteLikeService.kt` | Route like/unlike operations |
| `RouteMatchingService.kt` | Route similarity and matching algorithms |
| `FeedPostService.kt` | Feed post CRUD, feed generation logic |
| `FeedPostCommentService.kt` | Comment CRUD on posts |
| `FeedPostLikeService.kt` | Like/unlike operations on posts |
| `RunSessionService.kt` | Run session lifecycle management |
| `MessageService.kt` | Direct message operations (MongoDB) |
| `FriendshipService.kt` | Friend request flow, friendship management |
| `EmailService.kt` | Email sending via SMTP |
| `FileStorageService.kt` | Local file storage operations |
| `S3StorageService.kt` | AWS S3/Lightsail bucket operations |
| `VerificationPageService.kt` | Email verification page HTML generation |

## Conventions

- All services use `@Service` annotation
- Constructor injection for dependencies
- `@Transactional` on methods that modify data
- Throw custom exceptions from `exception/` package for error cases
- Return DTOs to controllers, not entities

## Common Patterns

```kotlin
@Service
class ResourceService(
    private val resourceRepository: ResourceRepository,
    private val userRepository: UserRepository
) {
    @Transactional(readOnly = true)
    fun getById(id: Long): ResourceDto {
        val resource = resourceRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Resource not found: $id") }
        return resource.toDto()
    }

    @Transactional
    fun create(request: CreateResourceRequest, username: String): ResourceDto {
        val user = userRepository.findByEmail(username)
            ?: throw UserNotFoundException("User not found")

        val resource = Resource(
            // ... map from request
            userId = user.id
        )
        return resourceRepository.save(resource).toDto()
    }
}
```

## Key Business Logic

- **AuthService**: JWT token generation with configurable expiry, refresh token rotation
- **FeedPostService**: Feed generation considers friendship status and visibility settings
- **FriendshipService**: Prevents duplicate requests, handles accept/reject flow
- **RunSessionService**: Tracks live run sessions with WebSocket updates
