#!/bin/bash

# Local deployment test script
# Use this to test your Docker setup locally before deploying to AWS

set -e

echo "🧪 Testing RunWithMe API Docker deployment locally..."

# Check if .env file exists
if [ ! -f .env ]; then
    echo "⚠️  .env file not found. Creating from .env.example..."
    if [ -f .env.example ]; then
        cp .env.example .env
        echo "📝 Please edit .env file with your actual credentials"
        exit 1
    else
        echo "❌ .env.example not found!"
        exit 1
    fi
fi

# Load environment variables
export $(cat .env | grep -v '^#' | xargs)

# Stop existing container if running
echo "🛑 Stopping existing container..."
docker stop runwithme-api 2>/dev/null || true
docker rm runwithme-api 2>/dev/null || true

# Build the Docker image
echo "🔨 Building Docker image..."
docker build -t runwithme-api:local .

# Run the container
echo "🚀 Starting container..."
docker run -d \
  --name runwithme-api \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL}" \
  -e SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}" \
  -e SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}" \
  -e JAVA_OPTS="-Xms256m -Xmx512m" \
  runwithme-api:local

# Wait for application to start
echo "⏳ Waiting for application to start..."
sleep 20

# Check if container is running
if docker ps | grep -q runwithme-api; then
    echo "✅ Container is running!"
    echo ""
    echo "📊 Container status:"
    docker ps --filter name=runwithme-api
    echo ""
    echo "📝 Recent logs:"
    docker logs --tail 20 runwithme-api
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🎉 Application is running!"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "🌐 API: http://localhost:8080"
    echo "📚 Swagger UI: http://localhost:8080/swagger-ui.html"
    echo "📖 API Docs: http://localhost:8080/v3/api-docs"
    echo ""
    echo "📋 Useful commands:"
    echo "  View logs:        docker logs -f runwithme-api"
    echo "  Stop container:   docker stop runwithme-api"
    echo "  Remove container: docker rm runwithme-api"
    echo "  Restart:          docker restart runwithme-api"
    echo ""
else
    echo "❌ Container failed to start!"
    echo "📝 Checking logs:"
    docker logs runwithme-api
    exit 1
fi

