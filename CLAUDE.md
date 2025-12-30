# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew bootRun              # Run application locally (port 8080)
./gradlew build                # Build JAR
./gradlew test                 # Run all tests
./gradlew test --tests ClassName  # Run specific test class
./gradlew spotlessCheck        # Lint check (ktlint)
./gradlew spotlessApply        # Auto-fix formatting
./gradlew clean build test     # Full rebuild with tests
```

**Requirements**: JDK 21

**Code Style**: Spotless with ktlint (max line length: 180, indent: 4 spaces). Formatting is checked automatically on build and must pass for CI.

## Local Development

For offline-ready development with local infrastructure:

```bash
cp .env.example .env           # Create local environment config
docker-compose up -d           # Start local PostgreSQL, MinIO (S3), and Mailhog
./gradlew bootRun              # Run application
docker-compose down            # Stop containers when done
```

Local services:

- **PostgreSQL** (PostGIS-enabled): `localhost:5432` (db: `runwithme`, user: `appuser/localpass`)
- **MinIO** (S3-compatible): Console at `http://localhost:9001` (minioadmin/minioadmin), Endpoint: `http://localhost:9000`
- **Mailhog** (Email sink): SMTP `localhost:1025`, UI at `http://localhost:8025`

Helper scripts:

- `./quickstart-jwt.sh` - Quick JWT authentication test flow
- `./manage-deployment.sh [info|status|logs|health]` - Manage AWS deployment (requires `.ec2-config`)

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
- **Push Notifications**: Firebase Cloud Messaging for mobile/web push notifications (see `docs/PUSH_NOTIFICATIONS.md`)
- **MCP Agent** (`mcp/`): General-purpose agent at `/api/v1/mcp/run` using Gemini for route selection and response generation. Maintains whitelist of allowed endpoints, validates selections, and forwards authenticated requests.

## Configuration

Environment variables (see `.env.example`):

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` - PostgreSQL
- `JWT_SECRET` - 256-bit base64-encoded secret (generate with `openssl rand -base64 64`)
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` - Email service
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_LIGHTSAIL_BUCKET_NAME` - S3 storage
- `MCP_EXTERNAL_API_BASE_URL`, `MCP_GEMINI_API_KEY`, `MCP_GEMINI_MODEL` - MCP agent configuration
- `FIREBASE_ENABLED`, `FIREBASE_PROJECT_ID`, `FIREBASE_CREDENTIALS_PATH` - Push notifications (Firebase)

## API Documentation

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI spec: `http://localhost:8080/v3/api-docs`
- Health check: `http://localhost:8080/actuator/health`
- WebSocket: `ws://localhost:8080/ws` (STOMP protocol)

**Authentication**: All `/api/v1/**` endpoints require JWT except `/api/v1/auth/**`. Use `Authorization: Bearer <token>` header.

## MCP Agent

The `mcp/` package contains a general-purpose agent that uses Gemini for intelligent route selection:

1. **McpPromptRouter** - Maintains whitelist of allowed endpoints with descriptions
2. **GeminiClient** - Uses function-calling to select routes and generate responses
3. **McpExternalApiClient** - Executes validated API calls with user's auth token
4. **Policy enforcement** - Only whitelisted routes can be called; parameterized endpoints validated

Access via `/api/v1/mcp/run` (JWT required). Configure in `.env`:

```bash
MCP_EXTERNAL_API_BASE_URL=https://jsonplaceholder.typicode.com
MCP_GEMINI_MODEL=gemini-1.5-flash
MCP_GEMINI_API_KEY=your_api_key
```

Example request:

```bash
curl -X POST http://localhost:8080/api/v1/mcp/run \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"prompt": "Show the todo record and give tips about comments"}'
```

## CI/CD

GitHub Actions workflow (`.github/workflows/deploy-to-aws-simple.yml`):

1. Runs spotlessCheck and tests
2. Builds Docker image on EC2
3. Deploys to AWS EC2 on main branch

Docker uses multi-stage build (Gradle 8.9 + JDK 21 → eclipse-temurin:21-jre).