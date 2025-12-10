# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew bootRun              # Run application locally (port 8080)
./gradlew build                # Build JAR
./gradlew test                 # Run tests
./gradlew spotlessCheck        # Lint check (ktlint)
./gradlew spotlessApply        # Auto-fix formatting
./gradlew clean build test     # Full rebuild with tests
```

**Requirements**: JDK 21

**Code Style**: Spotless with ktlint (max line length: 180, indent: 4 spaces). Formatting is checked automatically on build.

## Architecture

This is a **Spring Boot 3.3.4 / Kotlin** REST API for a social running application.

### Layer Structure

```
Controller → Service → Repository → Entity
     ↓
    DTO (request/response objects)
```

All code is under `com.runwithme.runwithme.api`:
- `controller/` - REST endpoints (`/api/v1/...`)
- `service/` - Business logic
- `repository/` - Spring Data JPA (PostgreSQL) and MongoDB
- `entity/` - JPA entities
- `dto/` - Request/response DTOs
- `security/` - JWT authentication (JwtTokenProvider, JwtAuthenticationFilter)
- `config/` - Spring configuration (SecurityConfig, WebSocketConfig)
- `exception/` - Custom exceptions with GlobalExceptionHandler

### Key Technical Decisions

- **Authentication**: JWT tokens (stateless). Token in `Authorization: Bearer <token>` header.
- **Database**: PostgreSQL with PostGIS/Hibernate Spatial for geospatial route data. MongoDB for direct messages.
- **Real-time**: STOMP over WebSocket at `/ws` for feed updates and chat.
- **IDs**: Users use UUID, other entities use Long.
- **Timestamps**: `OffsetDateTime` throughout.
- **Entity Relations**: Weak relationships using IDs rather than JPA @OneToMany.

### Main Domains

- **Auth**: Registration, login, email verification, JWT refresh
- **Users/Profiles**: User accounts with visibility settings (PUBLIC, FRIENDS_ONLY, PRIVATE)
- **Routes**: Running routes with geospatial coordinates (RoutePoint entities)
- **Feed**: Posts (TEXT, ROUTE, RUN_SESSION types), comments, likes
- **Friendships**: Friend requests and confirmed friendships
- **Chat**: Direct messaging via MongoDB

## Configuration

Environment variables (see `.env.example`):
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` - PostgreSQL
- `JWT_SECRET` - 256-bit base64-encoded secret
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` - Email service
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_LIGHTSAIL_BUCKET_NAME` - S3 storage

## API Documentation

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI spec: `http://localhost:8080/v3/api-docs`
- Health check: `http://localhost:8080/actuator/health`

## CI/CD

GitHub Actions workflow (`.github/workflows/deploy-to-aws-simple.yml`):
1. Runs spotlessCheck and tests
2. Builds Docker image
3. Deploys to AWS EC2 on main branch

Docker uses multi-stage build (Gradle 8.9 + JDK 21 → eclipse-temurin:21-jre).