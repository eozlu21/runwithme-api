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

### Local Development
```bash
# Copy environment template
cp .env.example .env
# Edit .env with your credentials

# Run the application
./gradlew bootRun

# Build & test
./gradlew clean build test
```

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

## CI

Two CI workflows exist for Java build/tests. The main one is `.github/workflows/ci.yml`.
