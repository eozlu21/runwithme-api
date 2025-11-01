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

# Or test with Docker
./test-deployment.sh
```

### API Endpoints
- **API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API Docs**: http://localhost:8080/v3/api-docs
- **Health Check**: http://localhost:8080/actuator/health


## 🚀 Deploy to AWS

### Quick Deploy (5 minutes)
See **[QUICKSTART.md](QUICKSTART.md)** for rapid AWS deployment setup.

### Production Deploy
See **[DEPLOYMENT.md](DEPLOYMENT.md)** for complete AWS deployment guide with ECR.

### Management
Once deployed, use the management script:
```bash
# Configure your EC2 details
cp .ec2-config.example .ec2-config
# Edit .ec2-config with your instance details

# Use the manager
./manage-deployment.sh status    # Check container status
./manage-deployment.sh logs      # View logs
./manage-deployment.sh health    # Check health
./manage-deployment.sh restart   # Restart container
```

See **[DEPLOYMENT_SUMMARY.md](DEPLOYMENT_SUMMARY.md)** for complete deployment overview.


## Development

### Build & Test
```bash
./gradlew build
./gradlew test
./gradlew spotlessCheck  # Check formatting
./gradlew spotlessApply  # Auto-format code
```

## CI/CD

GitHub Actions workflows auto-deploy to AWS on push to `main`.
