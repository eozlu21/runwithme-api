# Config Directory

Spring configuration classes for framework setup.

## Purpose

Contains `@Configuration` classes that configure:
- Spring Security and JWT authentication
- WebSocket/STOMP for real-time features
- Web settings (CORS, etc.)
- API documentation (OpenAPI/Swagger)
- Logging and monitoring

## Files

| File | Responsibility |
|------|----------------|
| `SecurityConfig.kt` | Spring Security configuration, JWT filter registration, endpoint protection |
| `WebSocketConfig.kt` | STOMP WebSocket configuration for real-time features |
| `WebConfig.kt` | CORS configuration, web MVC settings |
| `OpenApiConfig.kt` | Swagger/OpenAPI documentation setup |
| `RequestLoggingConfig.kt` | HTTP request/response logging for debugging |

## SecurityConfig

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig {
    // Defines:
    // - Public endpoints: /api/v1/auth/**, /actuator/**, /swagger-ui/**
    // - Protected endpoints: everything else
    // - JWT filter placement in filter chain
    // - CORS configuration
    // - CSRF disabled (stateless API)
}
```

## WebSocketConfig

```kotlin
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    // Configures:
    // - STOMP endpoint: /ws
    // - Message broker prefixes: /topic, /queue
    // - Application destination prefix: /app
}
```

WebSocket endpoints:
- `/ws` - Main WebSocket connection endpoint
- `/topic/*` - Broadcast messages (e.g., feed updates)
- `/queue/*` - User-specific messages (e.g., chat)
- `/app/*` - Client-to-server messages

## WebConfig

- CORS allowed origins configuration
- Request/response content type settings

## OpenApiConfig

- API documentation title, version, description
- Security scheme (JWT Bearer token)
- Server URLs for different environments

Access documentation at:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## RequestLoggingConfig

- Logs incoming requests and outgoing responses
- Useful for debugging API issues
- Can be toggled via application properties
