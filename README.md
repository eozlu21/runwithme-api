# RunWithMe API

Kotlin + Spring Boot backend for RunWithMe.

## Tech
- Kotlin, Spring Boot 3.x (Web, Security, Validation, Actuator)
- Spring Data JPA + PostgreSQL + PostGIS
- Spring Data MongoDB for DMs
- STOMP over WebSocket for realtime feed/chat
- springdoc-openapi for OpenAPI 3
- JDK 21, Gradle 8

## Quick Start

### Local Development (offline-ready)
```bash
# 1. Copy the environment template
cp .env.example .env

# 2. Start the local dependency stack (Postgres, MinIO, Mailhog)
docker-compose up -d

# 3. Run the application against the local services
./gradlew bootRun

# 4. (Optional) Run build & tests
./gradlew clean build test

# 5. Stop containers when you're done
docker-compose down
```

The `docker-compose.yml` spins up everything the API needs without touching live infrastructure:

| Service | Purpose | Access |
| ------- | ------- | ------ |
| `postgres` | PostGIS-enabled PostgreSQL instance seeded with `runwithme` DB, `appuser/localpass` credentials | `jdbc:postgresql://localhost:5432/runwithme` |
| `minio` + `minio-setup` | S3-compatible storage backing `S3StorageService`, bucket `runwithme-local` auto-created with public read | Console: http://localhost:9001 (user/pass: `minioadmin/minioadmin`), Endpoint: http://localhost:9000 |
| `mailhog` | SMTP sink for email features; messages are viewable via UI | SMTP: `localhost:1025`, UI: http://localhost:8025 |

After copying `.env.example`, the defaults already point to these containers (JWT secret, AWS credentials, etc.). Override the values if you need to target real services.

### API Endpoints
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- API Docs: http://localhost:8080/v3/api-docs
- Health Check: http://localhost:8080/actuator/health

### 🔐 JWT Authentication
All `/api/v1/**` endpoints require JWT authentication (except `/api/v1/auth/**`).

**Quick test:**
```bash
./quickstart-jwt.sh
```

**Documentation:**
- 📖 Full guide: [JWT_AUTHENTICATION_GUIDE.md](JWT_AUTHENTICATION_GUIDE.md)
- ⚡ Quick reference: [JWT_QUICK_REFERENCE.md](JWT_QUICK_REFERENCE.md)
- 📝 Implementation: [JWT_IMPLEMENTATION_SUMMARY.md](JWT_IMPLEMENTATION_SUMMARY.md)

**Basic usage:**
```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "john", "email": "john@test.com", "password": "pass123"}'

# Use the returned accessToken
curl -X GET http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## Deploy to AWS (Simple)

This repository includes a simple GitHub Actions workflow that builds the Docker image directly on your EC2 instance and runs it.

Prerequisites on EC2:
- Docker installed and running
- Your EC2 user is in the `docker` group (no sudo needed)

Required GitHub Secrets:
- `EC2_HOST` – Public IP/DNS of the instance
- `EC2_USER` – SSH user (e.g., `ubuntu` for Ubuntu AMIs)
- `EC2_SSH_PRIVATE_KEY` – Private key contents for SSH (BEGIN/END lines included)
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET` – Secret key for JWT token signing (generate with `openssl rand -base64 64`)

Trigger a deploy by pushing to `main` or running the workflow manually.

### Managing your deployment

Create `.ec2-config`:
```bash
cp .ec2-config.example .ec2-config
# Edit with your values
```
Use the helper script:
```bash
./manage-deployment.sh info
./manage-deployment.sh status
./manage-deployment.sh logs
./manage-deployment.sh health
```

## MCP Agent

The `com.runwithme.runwithme.api.mcp` folder contains a general-purpose MCP agent that runs with the calling user's permissions and creates an example flow on JSONPlaceholder + Gemini. The workflow is as follows:

1. `McpPromptRouter` maintains only the list of endpoints/functions you allow (HTTP method, path, description).
2. `GeminiClient.selectRoute` provides this list to the LLM in function-calling format and asks the model to select which route based on the user's prompt.
3. `McpPromptRouter` validates the selected route name against the whitelist; selections outside the whitelist are automatically rejected (policy enforcement).
4. `McpExternalApiClient` calls the validated route, forwarding the caller's `Authorization` header as-is.
5. `GeminiClient.generateAnswer` sends the prompt + API response to the `gemini-1.5-flash` model and generates a short action suggestion.

### Configuration

Add the following values to your `.env` file (examples are also in `.env.example`):
```
MCP_EXTERNAL_API_BASE_URL=https://jsonplaceholder.typicode.com
MCP_GEMINI_MODEL=gemini-1.5-flash
MCP_GEMINI_API_KEY=YOUR_API_KEY
```
`MCP_EXTERNAL_API_BASE_URL` is the base URL for all routes in the chain; default routes target JSONPlaceholder. Since the function-calling step also depends on Gemini, if `MCP_GEMINI_API_KEY` is left empty, route selection cannot be performed and the agent returns `success=false`.

### Usage

All `/api/v1/mcp/**` endpoints require JWT. After running the application, try this request:
```bash
curl -X POST http://localhost:8080/api/v1/mcp/run \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{
    "prompt": "Show the todo record and give tips about comments"
  }'
```

The agent analyzes the prompt via Gemini and explains the selection in the `routeDecisionReason` field. The response includes `success`, `routeName`, `requestedUrl`, `apiBody`, `llmMessage`, `routeDecisionReason`, `resolvedArguments`, `starterUserId`, and `error` fields. The backend automatically adds the identity of the user who started the conversation as `starterUserId` to the prompt; this way, when the user uses expressions like "my profile", the LLM can use this identity to select the correct route. If the model cannot select one of the routes from the list or fails whitelist validation, you will see `success=false` and an `error` message; endpoints outside the whitelist are never called.

You can find the default function list in the `McpPromptRouter` class, add new routes, or update descriptions. For parameterized endpoints, you can define a placeholder in the `pathTemplate` field (e.g., `api/v1/users/username/{username}`) and add keys like `username` to the `parameters` list. The function-calling prompt sent to Gemini asks it to return these parameters in the `arguments` field within JSON. The backend URL-encodes the incoming `arguments` values and substitutes them in the template; if any parameter is missing, the call is not made per policy.

Example: to fetch information by username, you can send the following prompt:
```bash
curl -X POST http://localhost:8080/api/v1/mcp/run \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{
    "prompt": "Get the profile of user minaaa"
  }'
```
The LLM selects the `User Info by Username` route, returns `arguments.username=minaaa`, and the result appears as `.../api/v1/users/username/minaaa` in the `requestedUrl` field.

### Testing

1. Fill in the MCP variables and JWT settings in `.env`, then run the application so you can login with JWT.
2. Run unit tests and format checks with `./gradlew clean test` command (JDK 17+ required).
3. To manually validate the MCP agent, generate an authorized user token and run the `curl` command above. For `success=false` cases, check that the error message matches the scenarios you expect.

## CI

Two CI workflows exist for Java build/tests. The main one is `.github/workflows/ci.yml`.
