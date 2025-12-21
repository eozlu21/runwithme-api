# Security Directory

JWT authentication and authorization components.

## Purpose

Handles:
- JWT token generation and validation
- Request authentication via filter
- User details loading for Spring Security

## Files

| File | Responsibility |
|------|----------------|
| `JwtTokenProvider.kt` | Generate, parse, and validate JWT tokens |
| `JwtAuthenticationFilter.kt` | Extract and validate JWT from request headers |
| `CustomUserDetailsService.kt` | Load user details from database for Spring Security |

## Authentication Flow

```
1. User logs in via /api/v1/auth/login
   ↓
2. AuthService validates credentials
   ↓
3. JwtTokenProvider generates access + refresh tokens
   ↓
4. Client stores tokens, sends access token in header:
   Authorization: Bearer <token>
   ↓
5. JwtAuthenticationFilter intercepts each request
   ↓
6. Filter validates token via JwtTokenProvider
   ↓
7. CustomUserDetailsService loads user
   ↓
8. SecurityContext populated with authenticated user
```

## JwtTokenProvider

```kotlin
// Key methods:
fun generateAccessToken(email: String): String
fun generateRefreshToken(email: String): String
fun validateToken(token: String): Boolean
fun getEmailFromToken(token: String): String
```

- Access tokens: short-lived (configurable, typically 15min-1hr)
- Refresh tokens: long-lived (configurable, typically 7 days)
- Secret: 256-bit key from `JWT_SECRET` environment variable

## JwtAuthenticationFilter

- Extends `OncePerRequestFilter`
- Extracts token from `Authorization: Bearer <token>` header
- Skips validation for public endpoints (auth, actuator)
- Sets `SecurityContextHolder` on successful validation

## CustomUserDetailsService

- Implements Spring Security's `UserDetailsService`
- Loads user by email from `UserRepository`
- Returns `UserDetails` with granted authorities

## Configuration

Security configuration is in `config/SecurityConfig.kt`:
- Defines public vs protected endpoints
- Configures CORS
- Registers the JWT filter in the filter chain
