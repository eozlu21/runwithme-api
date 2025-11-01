#!/bin/bash

# Deployment Management Script
# Provides quick commands to manage your EC2 deployment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CONTAINER_NAME="runwithme-api"
APP_PORT="8080"

# Load EC2 credentials if .ec2-config exists
if [ -f .ec2-config ]; then
    source .ec2-config
fi

# Check if required vars are set
check_config() {
    if [ -z "$EC2_HOST" ] || [ -z "$EC2_USER" ] || [ -z "$EC2_KEY" ]; then
        echo -e "${RED}❌ Missing EC2 configuration!${NC}"
        echo ""
        echo "Create a .ec2-config file with:"
        echo "  EC2_HOST=your-ec2-ip"
        echo "  EC2_USER=ec2-user"
        echo "  EC2_KEY=/path/to/your-key.pem"
        echo ""
        echo "Or set environment variables:"
        echo "  export EC2_HOST=your-ec2-ip"
        echo "  export EC2_USER=ec2-user"
        echo "  export EC2_KEY=/path/to/your-key.pem"
        exit 1
    fi
}

# SSH command helper
ssh_exec() {
    ssh -i "$EC2_KEY" -o StrictHostKeyChecking=no "${EC2_USER}@${EC2_HOST}" "$@"
}

# Show usage
usage() {
    echo -e "${BLUE}RunWithMe API - Deployment Manager${NC}"
    echo ""
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  status      - Show container status"
    echo "  logs        - View container logs (last 50 lines)"
    echo "  logs-live   - Follow container logs in real-time"
    echo "  restart     - Restart the container"
    echo "  stop        - Stop the container"
    echo "  start       - Start the container"
    echo "  health      - Check application health"
    echo "  shell       - Open a shell in the container"
    echo "  ssh         - SSH into the EC2 instance"
    echo "  clean       - Remove old Docker images"
    echo "  info        - Show deployment information"
    echo ""
    echo "Example: $0 logs"
    echo ""
}

# Show container status
show_status() {
    echo -e "${BLUE}📊 Container Status${NC}"
    ssh_exec "docker ps --filter name=$CONTAINER_NAME"
}

# View logs
show_logs() {
    echo -e "${BLUE}📝 Container Logs (last 50 lines)${NC}"
    ssh_exec "docker logs --tail 50 $CONTAINER_NAME"
}

# Follow logs
follow_logs() {
    echo -e "${BLUE}📝 Following Container Logs (Ctrl+C to stop)${NC}"
    ssh_exec "docker logs -f --tail 50 $CONTAINER_NAME"
}

# Restart container
restart_container() {
    echo -e "${YELLOW}🔄 Restarting container...${NC}"
    ssh_exec "docker restart $CONTAINER_NAME"
    echo -e "${GREEN}✅ Container restarted${NC}"
}

# Stop container
stop_container() {
    echo -e "${YELLOW}🛑 Stopping container...${NC}"
    ssh_exec "docker stop $CONTAINER_NAME"
    echo -e "${GREEN}✅ Container stopped${NC}"
}

# Start container
start_container() {
    echo -e "${YELLOW}▶️  Starting container...${NC}"
    ssh_exec "docker start $CONTAINER_NAME"
    echo -e "${GREEN}✅ Container started${NC}"
}

# Check health
check_health() {
    echo -e "${BLUE}🏥 Checking Application Health${NC}"
    response=$(curl -s -o /dev/null -w "%{http_code}" "http://${EC2_HOST}:${APP_PORT}/actuator/health" || echo "000")

    if [ "$response" == "200" ]; then
        echo -e "${GREEN}✅ Application is healthy!${NC}"
        curl -s "http://${EC2_HOST}:${APP_PORT}/actuator/health" | python3 -m json.tool 2>/dev/null || cat
    else
        echo -e "${RED}❌ Application is not responding (HTTP $response)${NC}"
        echo "Check logs with: $0 logs"
    fi
}

# Shell access
container_shell() {
    echo -e "${BLUE}🐚 Opening shell in container${NC}"
    echo "Type 'exit' to return"
    ssh_exec -t "docker exec -it $CONTAINER_NAME /bin/sh"
}

# SSH to EC2
ssh_to_ec2() {
    echo -e "${BLUE}🔐 Connecting to EC2 instance${NC}"
    ssh -i "$EC2_KEY" -o StrictHostKeyChecking=no "${EC2_USER}@${EC2_HOST}"
}

# Clean old images
clean_images() {
    echo -e "${YELLOW}🧹 Cleaning old Docker images...${NC}"
    ssh_exec "docker image prune -af"
    echo -e "${GREEN}✅ Cleanup complete${NC}"
}

# Show info
show_info() {
    echo -e "${BLUE}ℹ️  Deployment Information${NC}"
    echo ""
    echo "Host: $EC2_HOST"
    echo "User: $EC2_USER"
    echo "Container: $CONTAINER_NAME"
    echo ""
    echo "URLs:"
    echo "  API:        http://${EC2_HOST}:${APP_PORT}"
    echo "  Swagger UI: http://${EC2_HOST}:${APP_PORT}/swagger-ui.html"
    echo "  Health:     http://${EC2_HOST}:${APP_PORT}/actuator/health"
    echo ""
}

# Main script
if [ $# -eq 0 ]; then
    usage
    exit 0
fi

command=$1

case $command in
    status)
        check_config
        show_status
        ;;
    logs)
        check_config
        show_logs
        ;;
    logs-live)
        check_config
        follow_logs
        ;;
    restart)
        check_config
        restart_container
        ;;
    stop)
        check_config
        stop_container
        ;;
    start)
        check_config
        start_container
        ;;
    health)
        check_config
        check_health
        ;;
    shell)
        check_config
        container_shell
        ;;
    ssh)
        check_config
        ssh_to_ec2
        ;;
    clean)
        check_config
        clean_images
        ;;
    info)
        check_config
        show_info
        ;;
    *)
        echo -e "${RED}Unknown command: $command${NC}"
        echo ""
        usage
        exit 1
        ;;
esac

