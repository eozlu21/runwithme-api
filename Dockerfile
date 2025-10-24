# Build stage
FROM gradle:8.9-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle clean bootJar --no-daemon


# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]