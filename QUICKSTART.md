# AWS Deployment - Quick Start Guide

## 🚀 Quick Setup (5 minutes)

### Option 1: Simple Deployment (Recommended for Getting Started)

This builds the Docker image directly on your EC2 instance - no ECR needed!

#### Step 1: Prepare Your EC2 Instance

SSH into your EC2 instance and run:

```bash
# Copy and run this command
curl -fsSL https://raw.githubusercontent.com/your-repo/runwithme-api/main/setup-ec2.sh | bash
```

Or manually copy the `setup-ec2.sh` file and run:

```bash
chmod +x setup-ec2.sh
./setup-ec2.sh
```

#### Step 2: Configure GitHub Secrets

Go to your GitHub repository → **Settings** → **Secrets and variables** → **Actions**

Add these 5 secrets:

| Secret Name | Value | Where to Find |
|------------|-------|---------------|
| `EC2_SSH_PRIVATE_KEY` | Your SSH private key | Contents of your `.pem` file |
| `EC2_HOST` | EC2 public IP | AWS Console → EC2 → Your instance |
| `EC2_USER` | `ec2-user` or `ubuntu` | Depends on your AMI |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://...` | Your database URL |
| `SPRING_DATASOURCE_USERNAME` | Your DB username | From your DB setup |
| `SPRING_DATASOURCE_PASSWORD` | Your DB password | From your DB setup |

#### Step 3: Enable the Workflow

The workflow `.github/workflows/deploy-to-aws-simple.yml` is ready to use!

Just push to main branch:

```bash
git add .
git commit -m "Setup AWS deployment"
git push origin main
```

#### Step 4: Access Your API

Once deployed, visit:
- **API**: `http://YOUR_EC2_IP:8080`
- **Swagger UI**: `http://YOUR_EC2_IP:8080/swagger-ui.html`

---

### Option 2: Production Deployment (with ECR)

Use this for production with proper container registry.

#### Additional Requirements:
- AWS ECR repository
- IAM credentials for GitHub Actions
- ECR permissions on EC2

See full guide in `DEPLOYMENT.md`

---

## 🧪 Test Locally First

Before deploying to AWS, test your Docker setup locally:

```bash
# Make sure .env file exists with your credentials
./test-deployment.sh
```

This will:
- Build your Docker image
- Run it locally
- Show you any errors
- Give you access to http://localhost:8080

---

## 📋 EC2 Security Group Checklist

Make sure your EC2 security group allows:

| Type | Port | Source | Purpose |
|------|------|--------|---------|
| SSH | 22 | Your IP | Deployment |
| Custom TCP | 8080 | 0.0.0.0/0 | API Access |
| PostgreSQL | 5432 | EC2 Security Group | Database (if DB on same VPC) |

---

## 🔍 Troubleshooting

### Check if container is running:
```bash
ssh -i your-key.pem ec2-user@YOUR_EC2_IP "docker ps"
```

### View application logs:
```bash
ssh -i your-key.pem ec2-user@YOUR_EC2_IP "docker logs runwithme-api"
```

### Restart the application:
```bash
ssh -i your-key.pem ec2-user@YOUR_EC2_IP "docker restart runwithme-api"
```

### Container won't start?
Most common issues:
1. **Database connection failed** - Check security groups and credentials
2. **Port already in use** - Stop the old container first
3. **Out of memory** - Adjust JAVA_OPTS in workflow

---

## 🎯 What Each Workflow Does

### `deploy-to-aws.yml` (Production)
- Runs tests
- Builds application
- Creates Docker image
- Pushes to AWS ECR
- Deploys to EC2
- **Best for**: Production deployments

### `deploy-to-aws-simple.yml` (Quick & Easy)
- Copies code to EC2
- Builds Docker image on EC2
- Runs container
- **Best for**: Getting started, small projects

### `ci.yml` (Already exists)
- Runs on PRs
- Code quality checks
- Tests

---

## 💡 Next Steps

1. ✅ Run `./test-deployment.sh` locally
2. ✅ Setup EC2 with `setup-ec2.sh`
3. ✅ Add GitHub secrets
4. ✅ Push to main branch
5. ✅ Watch GitHub Actions run
6. ✅ Access your deployed API!

---

## 📚 Additional Resources

- Full deployment guide: `DEPLOYMENT.md`
- AWS EC2 docs: https://docs.aws.amazon.com/ec2/
- Docker docs: https://docs.docker.com/

---

## 🆘 Need Help?

Common commands:

```bash
# SSH into your EC2
ssh -i your-key.pem ec2-user@YOUR_EC2_IP

# View all containers
docker ps -a

# View logs in real-time
docker logs -f runwithme-api

# Check disk space
df -h

# Check memory usage
free -m

# Restart Docker
sudo systemctl restart docker
```

---

**That's it!** Your API should now automatically deploy to AWS whenever you push to main. 🎉

