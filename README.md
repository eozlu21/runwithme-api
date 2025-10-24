# RunWithMe API


Kotlin + Spring Boot backend for RunWithMe.


## Tech
- Kotlin, Spring Boot 3.x (Web, Security, Validation)
- Spring Data JPA + PostgreSQL + PostGIS
- Spring Data MongoDB for DMs
- Redis for caching
- STOMP over WebSocket for realtime feed/chat
- springdoc-openapi for OpenAPI 3
- JDK 21, Gradle 8


## Run
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'