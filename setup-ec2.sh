#!/bin/bash

# EC2 Setup Script for RunWithMe API
# Run this script on your EC2 instance to prepare it for deployments

set -e

echo "🚀 Setting up EC2 instance for RunWithMe API deployment..."

# Install Docker Compose (optional but useful)
echo "📝 Installing Docker Compose..."
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Create application directory
echo "📁 Creating application directory..."
mkdir -p ~/runwithme-api
cd ~/runwithme-api

# Setup logrotate for Docker logs
echo "📋 Configuring log rotation..."
sudo tee /etc/logrotate.d/docker-runwithme > /dev/null <<EOF
/var/lib/docker/containers/*/*.log {
    rotate 7
    daily
    compress
    size=50M
    missingok
    delaycompress
    copytruncate
}
EOF

# Configure Docker daemon
echo "⚙️  Configuring Docker daemon..."
sudo tee /etc/docker/daemon.json > /dev/null <<EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF

sudo systemctl restart docker

# Verify installations
echo ""
echo "✅ Setup complete! Verifying installations..."
echo ""
echo "Docker version:"
docker --version
echo ""
echo "Docker Compose version:"
docker-compose --version
echo ""
echo "AWS CLI version:"
aws --version
echo ""

# Display next steps
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 EC2 instance is ready for deployment!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Next steps:"
echo "1. Log out and log back in for docker group changes to take effect"
echo "2. Configure GitHub secrets with this instance's details"
echo "3. Push to main branch to trigger deployment"
echo ""
echo "📝 Important: Configure security group to allow:"
echo "   - Inbound: Port 8080 (application)"
echo "   - Inbound: Port 22 (SSH)"
echo ""
echo "🔐 If using ECR, configure IAM role with ECR permissions"
echo ""

