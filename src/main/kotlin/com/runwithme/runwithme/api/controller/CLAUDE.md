# Controller Directory

REST API endpoints layer - handles HTTP requests and responses.

## Purpose

Controllers are the entry point for all HTTP requests. They:
- Define REST endpoints under `/api/v1/...`
- Validate incoming requests
- Delegate business logic to services
- Return appropriate HTTP responses with DTOs

## Files

| File | Endpoints | Description |
|------|-----------|-------------|
| `AuthController.kt` | `/api/v1/auth/*` | Registration, login, email verification, token refresh |
| `UserController.kt` | `/api/v1/users/*` | User account CRUD operations |
| `UserProfileController.kt` | `/api/v1/profiles/*` | Profile viewing and management |
| `RouteController.kt` | `/api/v1/routes/*` | Route creation, retrieval, management |
| `RouteLikeController.kt` | `/api/v1/routes/*/likes` | Route liking functionality |
| `FeedPostController.kt` | `/api/v1/feed/*` | Feed post creation and retrieval |
| `FeedPostCommentController.kt` | `/api/v1/feed/*/comments` | Comments on feed posts |
| `FeedPostLikeController.kt` | `/api/v1/feed/*/likes` | Liking feed posts |
| `FriendshipController.kt` | `/api/v1/friendships/*` | Friend requests and friendships |
| `RunSessionController.kt` | `/api/v1/run-sessions/*` | Run session tracking |
| `RunSessionWebSocketController.kt` | WebSocket `/ws` | Real-time run session updates |
| `ChatController.kt` | `/api/v1/chat/*` | Direct messaging |
| `ImageController.kt` | `/api/v1/images/*` | Image upload/retrieval |

## Conventions

- All controllers use `@RestController` and `@RequestMapping`
- Authentication required for most endpoints (except auth endpoints)
- Current user obtained via `@AuthenticationPrincipal`
- Request validation using Jakarta validation annotations
- Responses wrapped in appropriate DTOs from `dto/` package

## Common Patterns

```kotlin
@RestController
@RequestMapping("/api/v1/resource")
class ResourceController(private val service: ResourceService) {

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<ResourceDto> {
        return ResponseEntity.ok(service.getById(id))
    }

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateResourceRequest,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<ResourceDto> {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.create(request, userDetails.username))
    }
}
```
