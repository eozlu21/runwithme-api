#!/bin/bash

# Quick deployment setup for RunWithMe API
# This script guides you through the initial setup

set -e

echo "╔═══════════════════════════════════════════════════╗"
echo "║   RunWithMe API - AWS Deployment Quick Setup     ║"
echo "╚═══════════════════════════════════════════════════╝"
echo ""

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}This script will help you set up AWS deployment in 3 steps:${NC}"
echo ""
echo "  1. Test your Docker setup locally"
echo "  2. Setup your EC2 instance"
echo "  3. Configure GitHub secrets"
echo ""

# Step 1: Local Testing
echo "═══════════════════════════════════════════════════"
echo -e "${BLUE}STEP 1: Test Locally${NC}"
echo "═══════════════════════════════════════════════════"
echo ""

if [ ! -f .env ]; then
    echo -e "${YELLOW}Creating .env file from template...${NC}"
    cp .env.example .env
    echo -e "${GREEN}✓ Created .env${NC}"
    echo ""
    echo "⚠️  Please edit .env with your database credentials"
    echo "   Then run: ./test-deployment.sh"
    echo ""
else
    echo -e "${GREEN}✓ .env file exists${NC}"
    echo ""
    echo "Test your Docker setup:"
    echo "  ./test-deployment.sh"
    echo ""
fi

# Step 2: EC2 Setup
echo "═══════════════════════════════════════════════════"
echo -e "${BLUE}STEP 2: Setup EC2 Instance${NC}"
echo "═══════════════════════════════════════════════════"
echo ""
echo "On your EC2 instance (SSH first), run:"
echo ""
echo "  chmod +x setup-ec2.sh"
echo "  ./setup-ec2.sh"
echo ""
echo "Then log out and back in for docker group changes to take effect."
echo ""

# Step 3: GitHub Secrets
echo "═══════════════════════════════════════════════════"
echo -e "${BLUE}STEP 3: Configure GitHub Secrets${NC}"
echo "═══════════════════════════════════════════════════"
echo ""
echo "Go to: GitHub Repository → Settings → Secrets and variables → Actions"
echo ""
echo "Add these 6 secrets:"
echo ""
echo "  1. EC2_SSH_PRIVATE_KEY"
echo "     → Contents of your .pem file (entire file)"
echo ""
echo "  2. EC2_HOST"
echo "     → Your EC2 public IP: 3.120.179.202"
echo ""
echo "  3. EC2_USER"
echo "     → ec2-user (Amazon Linux) or ubuntu (Ubuntu)"
echo ""
echo "  4. SPRING_DATASOURCE_URL"
echo "     → jdbc:postgresql://3.120.179.202:5432/appdb"
echo ""
echo "  5. SPRING_DATASOURCE_USERNAME"
echo "     → Your database username"
echo ""
echo "  6. SPRING_DATASOURCE_PASSWORD"
echo "     → Your database password"
echo ""

# Step 4: Deploy
echo "═══════════════════════════════════════════════════"
echo -e "${BLUE}STEP 4: Deploy to AWS${NC}"
echo "═══════════════════════════════════════════════════"
echo ""
echo "Once secrets are configured, deploy:"
echo ""
echo "  git add ."
echo "  git commit -m \"Setup AWS deployment\""
echo "  git push origin main"
echo ""
echo "Watch deployment: GitHub → Actions tab"
echo ""

# Step 5: Verify
echo "═══════════════════════════════════════════════════"
echo -e "${BLUE}STEP 5: Verify Deployment${NC}"
echo "═══════════════════════════════════════════════════"
echo ""
echo "After deployment completes:"
echo ""
echo "  1. Setup local management:"
echo "     cp .ec2-config.example .ec2-config"
echo "     # Edit .ec2-config with your EC2 details"
echo ""
echo "  2. Check status:"
echo "     ./manage-deployment.sh status"
echo ""
echo "  3. Check health:"
echo "     ./manage-deployment.sh health"
echo ""
echo "  4. View logs:"
echo "     ./manage-deployment.sh logs"
echo ""
echo "  5. Access your API:"
echo "     http://YOUR_EC2_IP:8080/swagger-ui.html"
echo ""

# Documentation
echo "═══════════════════════════════════════════════════"
echo -e "${BLUE}📚 Documentation${NC}"
echo "═══════════════════════════════════════════════════"
echo ""
echo "  QUICKSTART.md              - 5-minute guide"
echo "  DEPLOYMENT_CHECKLIST.md    - Pre-deployment checklist"
echo "  DEPLOYMENT.md              - Complete guide"
echo "  DEPLOYMENT_SUMMARY.md      - Overview"
echo ""

# Security Reminders
echo "═══════════════════════════════════════════════════"
echo -e "${YELLOW}🔐 Security Reminders${NC}"
echo "═══════════════════════════════════════════════════"
echo ""
echo "  ⚠️  Never commit .env or .ec2-config"
echo "  ⚠️  Never commit .pem files"
echo "  ⚠️  Set SSH key permissions: chmod 400 your-key.pem"
echo "  ⚠️  Configure EC2 security group properly"
echo ""

# Final message
echo "═══════════════════════════════════════════════════"
echo -e "${GREEN}✨ You're all set!${NC}"
echo "═══════════════════════════════════════════════════"
echo ""
echo "Next: Read QUICKSTART.md for detailed instructions"
echo ""

