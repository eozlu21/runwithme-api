# RunWithMe API

Kotlin + Spring Boot backend for RunWithMe.

## Tech
- Kotlin, Spring Boot 3.x (Web, Security, Validation, Actuator)
- Spring Data JPA + PostgreSQL + PostGIS
- Spring Data MongoDB for DMs
- Redis for caching
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
