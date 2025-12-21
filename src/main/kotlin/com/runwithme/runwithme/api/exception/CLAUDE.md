# Exception Directory

Custom exception handling for the API.

## Purpose

Provides:
- Custom exception classes for specific error scenarios
- Centralized exception handling via `@ControllerAdvice`
- Consistent error response format across the API

## Files

| File | Responsibility |
|------|----------------|
| `GlobalExceptionHandler.kt` | Central exception handler, converts exceptions to HTTP responses |
| `DuplicateResourceException.kt` | Thrown when attempting to create a resource that already exists |
| `EmailNotVerifiedException.kt` | Thrown when user tries to access protected resource without email verification |
| `UnauthorizedActionException.kt` | Thrown when user attempts action they don't have permission for |

## GlobalExceptionHandler

Handles exceptions and returns appropriate HTTP status codes:

| Exception | HTTP Status |
|-----------|-------------|
| `DuplicateResourceException` | 409 Conflict |
| `EmailNotVerifiedException` | 403 Forbidden |
| `UnauthorizedActionException` | 403 Forbidden |
| `EntityNotFoundException` | 404 Not Found |
| `MethodArgumentNotValidException` | 400 Bad Request |
| `Exception` (fallback) | 500 Internal Server Error |

## Error Response Format

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "User not found with id: 123",
  "path": "/api/v1/users/123"
}
```

## Custom Exception Pattern

```kotlin
class ResourceNotFoundException(message: String) : RuntimeException(message)

// Usage in service:
fun getById(id: Long): Resource {
    return repository.findById(id)
        .orElseThrow { ResourceNotFoundException("Resource not found: $id") }
}
```

## Adding New Exceptions

1. Create exception class extending `RuntimeException`:
```kotlin
class CustomException(message: String) : RuntimeException(message)
```

2. Add handler in `GlobalExceptionHandler`:
```kotlin
@ExceptionHandler(CustomException::class)
fun handleCustomException(ex: CustomException): ResponseEntity<ErrorResponse> {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse(message = ex.message))
}
```
