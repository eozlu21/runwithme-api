# AWS Deployment Guide

This guide explains how to deploy the RunWithMe API to AWS using GitHub Actions.

## Prerequisites

1. **AWS Account** with access to:
   - EC2 (Elastic Compute Cloud)
   - ECR (Elastic Container Registry)
   - IAM (Identity and Access Management)

2. **EC2 Instance** running with:
   - Docker installed
   - AWS CLI installed
   - Security group allowing inbound traffic on port 8080 (or your app port)
   - IAM role or credentials to pull from ECR

3. **GitHub Repository** with Actions enabled

## Setup Instructions

### 1. Create an ECR Repository

```bash
aws ecr create-repository \
  --repository-name runwithme-api \
  --region eu-central-1 \
  --image-scanning-configuration scanOnPush=true
```

Note the repository URI (e.g., `123456789012.dkr.ecr.eu-central-1.amazonaws.com/runwithme-api`)

### 2. Configure EC2 Instance

SSH into your EC2 instance and ensure the following are installed:

```bash
# Install Docker
sudo yum update -y
sudo yum install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -a -G docker ec2-user

# Install AWS CLI (usually pre-installed on Amazon Linux)
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Verify installations
docker --version
aws --version
```

### 3. Set Up IAM Permissions

#### For EC2 Instance:
Create an IAM role with the following policy and attach it to your EC2 instance:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage"
      ],
      "Resource": "*"
    }
  ]
}
```

#### For GitHub Actions:
Create an IAM user with programmatic access and attach a policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload"
      ],
      "Resource": "*"
    }
  ]
}
```

### 4. Configure GitHub Secrets

Go to your GitHub repository → Settings → Secrets and variables → Actions → New repository secret

Add the following secrets:

| Secret Name | Description | Example |
|------------|-------------|---------|
| `AWS_ACCESS_KEY_ID` | AWS IAM access key for GitHub Actions | `AKIAIOSFODNN7EXAMPLE` |
| `AWS_SECRET_ACCESS_KEY` | AWS IAM secret key | `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY` |
| `EC2_SSH_PRIVATE_KEY` | SSH private key to access EC2 | Contents of your `.pem` file |
| `EC2_HOST` | Public IP or DNS of your EC2 instance | `3.120.179.202` or `ec2-3-120-179-202.eu-central-1.compute.amazonaws.com` |
| `EC2_USER` | SSH username for EC2 | `ec2-user` (Amazon Linux) or `ubuntu` (Ubuntu) |
| `SPRING_DATASOURCE_URL` | Database connection URL | `jdbc:postgresql://3.120.179.202:5432/appdb` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `appuser` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | Your secure password |

### 5. Update Workflow Configuration

Edit `.github/workflows/deploy-to-aws.yml` and update these variables:

```yaml
env:
  AWS_REGION: eu-central-1  # Your AWS region
  ECR_REPOSITORY: runwithme-api  # Your ECR repository name
  CONTAINER_NAME: runwithme-api  # Container name on EC2
  APP_PORT: 8080  # Your application port
```

### 6. Configure EC2 Security Group

Ensure your EC2 security group allows:
- **Inbound**: Port 8080 (or your APP_PORT) from `0.0.0.0/0` or your specific IP ranges
- **Inbound**: Port 22 for SSH (restrict to GitHub Actions IPs or your IP)
- **Outbound**: All traffic (to pull Docker images and access database)

## Deployment

Once everything is configured, deployment is automatic:

1. Push code to the `main` branch
2. GitHub Actions will:
   - Run tests
   - Build the application
   - Build Docker image
   - Push to ECR
   - Deploy to EC2
   - Start the container

You can also trigger deployment manually:
- Go to Actions tab in GitHub
- Select "Deploy to AWS" workflow
- Click "Run workflow"

## Verification

After deployment, verify the application is running:

```bash
# Check if container is running
ssh -i your-key.pem ec2-user@your-ec2-ip "docker ps"

# Check application logs
ssh -i your-key.pem ec2-user@your-ec2-ip "docker logs runwithme-api"

# Test the API
curl http://your-ec2-ip:8080/swagger-ui.html
```

## Accessing the Application

- **API**: `http://your-ec2-ip:8080`
- **Swagger UI**: `http://your-ec2-ip:8080/swagger-ui.html`
- **API Docs**: `http://your-ec2-ip:8080/v3/api-docs`

## Troubleshooting

### Container not starting
```bash
ssh -i your-key.pem ec2-user@your-ec2-ip
docker logs runwithme-api
```

### Cannot pull image from ECR
```bash
# Verify EC2 can authenticate to ECR
aws ecr get-login-password --region eu-central-1 | docker login --username AWS --password-stdin your-account-id.dkr.ecr.eu-central-1.amazonaws.com
```

### Database connection issues
- Verify database security group allows connections from EC2
- Check database credentials in GitHub secrets
- Verify database is running and accessible

### Port already in use
```bash
# Stop the existing container
docker stop runwithme-api
docker rm runwithme-api
# Or check what's using port 8080
sudo lsof -i :8080
```

## Alternative: Using Docker Compose

For more complex deployments, you can use Docker Compose. Create `docker-compose.yml`:

```yaml
version: '3.8'
services:
  api:
    image: ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}
    container_name: runwithme-api
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL}
      - SPRING_DATASOURCE_USERNAME=${SPRING_DATASOURCE_USERNAME}
      - SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD}
      - JAVA_OPTS=-Xms256m -Xmx512m
    networks:
      - app-network

networks:
  app-network:
    driver: bridge
```

## Production Considerations

For production deployments, consider:

1. **HTTPS/SSL**: Use AWS Load Balancer or nginx reverse proxy with SSL certificates
2. **Domain Name**: Set up Route 53 or your DNS provider
3. **Auto-scaling**: Use ECS/EKS for container orchestration
4. **Monitoring**: Set up CloudWatch logs and metrics
5. **Backup**: Regular database backups
6. **Secrets Management**: Use AWS Secrets Manager or Parameter Store
7. **CI/CD**: Add staging environment before production

## Cost Optimization

- Use spot instances for non-production environments
- Enable ECR image scanning to remove old images
- Monitor data transfer costs
- Consider using AWS Fargate for serverless containers

## Support

For issues or questions, contact the development team or create an issue in the repository.

