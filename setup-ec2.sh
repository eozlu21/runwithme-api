#!/bin/bash

# EC2 Setup Script for RunWithMe API
# Run this script on your EC2 instance to prepare it for deployments

set -e

echo "🚀 Setting up EC2 instance for RunWithMe API deployment..."

# Update system
echo "📦 Updating system packages..."
sudo apt-get update

# Install Docker
echo "🐋 Installing Docker..."
if ! command -v docker &> /dev/null; then
    # Install prerequisites
    sudo apt-get install -y \
        ca-certificates \
        curl \
        gnupg \
        lsb-release

    # Add Docker's official GPG key
    sudo mkdir -p /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

    # Set up the repository
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

    # Install Docker Engine
    sudo apt-get update
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    # Start and enable Docker
    sudo systemctl start docker
    sudo systemctl enable docker

    # Add current user to docker group
    sudo usermod -aG docker $USER

    echo "✅ Docker installed successfully"
else
    echo "✅ Docker is already installed"
fi

# Ensure docker group permissions
echo "🔐 Configuring Docker permissions..."
sudo usermod -aG docker $USER

# Install AWS CLI v2
echo "☁️  Installing AWS CLI..."
if ! command -v aws &> /dev/null; then
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
    sudo apt-get install -y unzip
    unzip awscliv2.zip
    sudo ./aws/install
    rm -rf aws awscliv2.zip
    echo "✅ AWS CLI installed successfully"
else
    echo "✅ AWS CLI is already installed"
fi

# Install Docker Compose (standalone, optional but useful)
echo "📝 Installing Docker Compose standalone..."
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

# Configure PATH for non-interactive SSH sessions
echo "🔧 Configuring PATH for non-interactive SSH sessions..."
# Add Docker and common paths to .bashrc for non-interactive sessions
if ! grep -q "# Docker PATH for non-interactive sessions" ~/.bashrc; then
    cat >> ~/.bashrc <<'EOF'

# Docker PATH for non-interactive sessions
export PATH="/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin:$PATH"
EOF
    echo "✅ PATH configuration added to .bashrc"
fi

# Ensure docker socket has correct permissions
sudo chmod 666 /var/run/docker.sock || true

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

# Test Docker in non-interactive mode (simulating GitHub Actions)
echo "🧪 Testing Docker in non-interactive SSH mode..."
if ssh localhost -o StrictHostKeyChecking=no "/usr/bin/docker --version" 2>/dev/null; then
    echo "✅ Docker works in non-interactive mode"
else
    echo "⚠️  Could not test non-interactive mode (SSH to localhost not configured)"
    echo "   This is normal if you haven't set up SSH keys for localhost"
fi

# Display next steps
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 EC2 instance is ready for deployment!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Next steps:"
echo "1. IMPORTANT: Log out and log back in for docker group changes to take effect"
echo "2. Test Docker works: docker ps"
echo "3. Configure GitHub secrets with this instance's details"
echo "4. Push to main branch to trigger deployment"
echo ""
echo "📝 Important: Configure security group to allow:"
echo "   - Inbound: Port 8080 (application)"
echo "   - Inbound: Port 22 (SSH) from GitHub Actions IPs"
echo ""
echo "🔐 If using ECR, configure IAM role with ECR permissions"
echo ""
echo "💡 To verify Docker works in non-interactive mode (like GitHub Actions):"
echo "   From your local machine, run:"
echo "   ssh -i your-key.pem ubuntu@your-ec2-ip '/usr/bin/docker ps'"
echo ""

