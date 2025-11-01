#!/bin/bash

# Test Docker in Non-Interactive Mode
# This script simulates how GitHub Actions connects to your EC2 instance
# Run this from your LOCAL machine, not from EC2

set -e

echo "🧪 Testing Docker in Non-Interactive SSH Mode"
echo "This simulates how GitHub Actions will connect to your EC2"
echo ""

# Check if .ec2-config exists
if [ ! -f .ec2-config ]; then
    echo "❌ Error: .ec2-config file not found"
    echo "Please create it from .ec2-config.example and fill in your EC2 details"
    exit 1
fi

# Load EC2 configuration
source .ec2-config

# Validate configuration
if [ -z "$EC2_HOST" ] || [ -z "$EC2_USER" ] || [ -z "$EC2_KEY_PATH" ]; then
    echo "❌ Error: EC2 configuration incomplete"
    echo "Please set EC2_HOST, EC2_USER, and EC2_KEY_PATH in .ec2-config"
    exit 1
fi

echo "Configuration:"
echo "  Host: $EC2_HOST"
echo "  User: $EC2_USER"
echo "  Key:  $EC2_KEY_PATH"
echo ""

# Test 1: Basic SSH connection
echo "Test 1: Basic SSH Connection"
if ssh -o StrictHostKeyChecking=no -i "$EC2_KEY_PATH" "${EC2_USER}@${EC2_HOST}" 'echo "Connection successful"' 2>/dev/null; then
    echo "✅ SSH connection works"
else
    echo "❌ SSH connection failed"
    echo "Check your EC2_HOST, EC2_USER, and EC2_KEY_PATH"
    exit 1
fi
echo ""

# Test 2: Docker with just 'docker' command (likely to fail)
echo "Test 2: Docker command without full path"
if ssh -o StrictHostKeyChecking=no -i "$EC2_KEY_PATH" "${EC2_USER}@${EC2_HOST}" 'docker --version' 2>/dev/null; then
    echo "✅ 'docker' command works (PATH is configured)"
else
    echo "⚠️  'docker' command not found (this is the GitHub Actions issue)"
fi
echo ""

# Test 3: Docker with full path (should work)
echo "Test 3: Docker command with full path"
if ssh -o StrictHostKeyChecking=no -i "$EC2_KEY_PATH" "${EC2_USER}@${EC2_HOST}" '/usr/bin/docker --version' 2>/dev/null; then
    echo "✅ '/usr/bin/docker' works (this is what we use in fixed workflows)"
else
    echo "❌ '/usr/bin/docker' not found"
    echo "Docker might not be installed at /usr/bin/docker"
    echo "Run: ssh ... 'which docker' to find the correct path"
fi
echo ""

# Test 4: Check where docker is installed
echo "Test 4: Finding Docker installation"
DOCKER_PATH=$(ssh -o StrictHostKeyChecking=no -i "$EC2_KEY_PATH" "${EC2_USER}@${EC2_HOST}" 'which docker 2>/dev/null || echo "NOT_FOUND"')
if [ "$DOCKER_PATH" != "NOT_FOUND" ]; then
    echo "✅ Docker found at: $DOCKER_PATH"
else
    echo "❌ Docker not found in PATH"
    echo "You may need to install Docker on your EC2 instance"
    echo "Run: ./setup-ec2.sh on your EC2 instance"
fi
echo ""

# Test 5: Check Docker group membership
echo "Test 5: Checking Docker permissions"
if ssh -o StrictHostKeyChecking=no -i "$EC2_KEY_PATH" "${EC2_USER}@${EC2_HOST}" 'groups | grep docker' >/dev/null 2>&1; then
    echo "✅ User is in docker group"
else
    echo "⚠️  User is not in docker group"
    echo "Run on EC2: sudo usermod -aG docker \$USER"
    echo "Then log out and back in"
fi
echo ""

# Test 6: Test Docker ps command
echo "Test 6: Testing Docker ps (simulating deployment check)"
if ssh -o StrictHostKeyChecking=no -i "$EC2_KEY_PATH" "${EC2_USER}@${EC2_HOST}" '/usr/bin/docker ps' 2>/dev/null; then
    echo "✅ Docker ps works (can list containers)"
else
    echo "❌ Docker ps failed"
    echo "This could be a permissions issue or Docker is not running"
fi
echo ""

# Test 7: Simulate GitHub Actions deployment command
echo "Test 7: Simulating GitHub Actions deployment script"
TEST_OUTPUT=$(ssh -o StrictHostKeyChecking=no -i "$EC2_KEY_PATH" "${EC2_USER}@${EC2_HOST}" << 'EOF'
# Set up PATH for Docker
export PATH="/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin:$PATH"

# Test commands that GitHub Actions will run
echo "Testing: docker --version"
/usr/bin/docker --version

echo "Testing: docker ps"
/usr/bin/docker ps

echo "SUCCESS: All commands worked"
EOF
)

if echo "$TEST_OUTPUT" | grep -q "SUCCESS"; then
    echo "✅ GitHub Actions simulation successful!"
    echo ""
    echo "$TEST_OUTPUT"
else
    echo "❌ GitHub Actions simulation failed"
    echo ""
    echo "$TEST_OUTPUT"
fi
echo ""

# Summary
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📋 Test Summary"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "If all tests passed (✅), your EC2 is ready for GitHub Actions!"
echo ""
echo "If any tests failed (❌):"
echo "1. SSH into your EC2 instance"
echo "2. Run: cd ~/runwithme-api && ./setup-ec2.sh"
echo "3. Log out and back in"
echo "4. Run this test again"
echo ""
echo "If 'docker' command doesn't work but '/usr/bin/docker' does:"
echo "This is expected and fine! The GitHub Actions workflow uses full paths."
echo ""

