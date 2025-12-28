# Build stage
FROM gradle:8.9-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle clean bootJar --no-daemon


# Runtime stage with Python for ML inference
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install Python 3 and pip
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    python3-venv \
    && rm -rf /var/lib/apt/lists/* \
    && ln -sf /usr/bin/python3 /usr/bin/python

# Install Python dependencies (CPU-only PyTorch to keep image smaller)
RUN pip3 install --no-cache-dir numpy && \
    pip3 install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu

# Copy the built JAR
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

# Copy Python scripts and data to filesystem (can't access from inside JAR)
COPY src/main/resources/python /app/python
COPY src/main/resources/data /app/data

ENV JAVA_OPTS=""
ENV PYTHON_PATH="python3"
ENV INFERENCE_SCRIPT_DIR="/app/python"
ENV EMBEDDINGS_DIR="/app/data"

# Mail credentials - pass at runtime via -e or docker-compose (not stored in image)
ENV MAIL_USERNAME=""
# Note: MAIL_PASSWORD should be passed at runtime via -e flag, not set here
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]