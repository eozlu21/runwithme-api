# AWS Deployment Checklist

Use this checklist to ensure your deployment is set up correctly.

## ☑️ Pre-Deployment Setup

### AWS EC2 Instance
- [ ] EC2 instance is created and running
- [ ] Instance type is appropriate (t2.micro for testing, larger for production)
- [ ] Security group configured with rules:
  - [ ] Port 22 (SSH) - From your IP or GitHub Actions IPs
  - [ ] Port 8080 (API) - From 0.0.0.0/0 or specific IPs
  - [ ] Port 5432 (PostgreSQL) - If DB is separate, from EC2 security group
- [ ] EC2 instance has public IP address
- [ ] You have downloaded the SSH key (.pem file)

### EC2 Instance Software
- [ ] SSH into EC2 and run `setup-ec2.sh` script
  ```bash
  chmod +x setup-ec2.sh
  ./setup-ec2.sh
  ```
- [ ] Docker is installed: `docker --version`
- [ ] Docker is running: `docker ps`
- [ ] AWS CLI is installed: `aws --version`
- [ ] User can run Docker without sudo: Test with `docker ps`

### Database
- [ ] PostgreSQL database is running
- [ ] Database is accessible from EC2 instance
- [ ] You have database credentials:
  - [ ] Database URL
  - [ ] Database username
  - [ ] Database password
- [ ] Test connection from EC2:
  ```bash
  psql -h YOUR_DB_HOST -U YOUR_USER -d YOUR_DB_NAME
  ```

### GitHub Repository
- [ ] Repository has GitHub Actions enabled
- [ ] You have permissions to add secrets

## ☑️ GitHub Secrets Configuration

Go to: Repository → Settings → Secrets and variables → Actions

### Required Secrets (Minimum - for Simple Deployment)
- [ ] `EC2_SSH_PRIVATE_KEY` - Contents of your .pem file
- [ ] `EC2_HOST` - EC2 public IP address
- [ ] `EC2_USER` - Usually `ec2-user` (Amazon Linux) or `ubuntu`
- [ ] `SPRING_DATASOURCE_URL` - Full JDBC URL
- [ ] `SPRING_DATASOURCE_USERNAME` - Database username
- [ ] `SPRING_DATASOURCE_PASSWORD` - Database password

### Additional Secrets (for ECR Deployment)
- [ ] `AWS_ACCESS_KEY_ID` - IAM user access key
- [ ] `AWS_SECRET_ACCESS_KEY` - IAM user secret key

### Verify Secrets Format
- [ ] SSH key includes BEGIN/END lines
- [ ] No extra spaces or quotes around values
- [ ] Database URL format: `jdbc:postgresql://HOST:5432/dbname`

## ☑️ Local Testing

Before deploying to AWS, test locally:

- [ ] Create `.env` file from `.env.example`
- [ ] Fill in real database credentials in `.env`
- [ ] Run `./test-deployment.sh` successfully
- [ ] Access http://localhost:8080/actuator/health
- [ ] Access http://localhost:8080/swagger-ui.html
- [ ] Test API endpoints work
- [ ] Stop local container: `docker stop runwithme-api`

## ☑️ Workflow Configuration

- [ ] Review `.github/workflows/deploy-to-aws-simple.yml`
- [ ] Update `APP_PORT` if not using 8080
- [ ] Update `CONTAINER_NAME` if desired
- [ ] Workflow file is committed to repository

## ☑️ Deployment Management

- [ ] Create `.ec2-config` from `.ec2-config.example`
- [ ] Fill in EC2 details in `.ec2-config`
- [ ] Test management script: `./manage-deployment.sh info`
- [ ] Never commit `.ec2-config` (check it's in .gitignore)

## ☑️ First Deployment

- [ ] All above steps completed
- [ ] Commit all changes: `git add . && git commit -m "Setup AWS deployment"`
- [ ] Push to main: `git push origin main`
- [ ] Watch GitHub Actions: Repository → Actions tab
- [ ] Wait for workflow to complete (5-10 minutes)
- [ ] Check workflow logs if it fails

## ☑️ Post-Deployment Verification

### Check Container Status
```bash
./manage-deployment.sh status
```
- [ ] Container shows as "Up"
- [ ] No "Restarting" or "Exited" status

### Check Application Health
```bash
./manage-deployment.sh health
```
- [ ] Returns HTTP 200
- [ ] Shows `"status": "UP"`

### Check Logs
```bash
./manage-deployment.sh logs
```
- [ ] No error messages
- [ ] Shows "Started RunwithmeApiApplication"
- [ ] No database connection errors

### Test API Endpoints
- [ ] API responds: `curl http://YOUR_EC2_IP:8080/actuator/health`
- [ ] Swagger UI loads: http://YOUR_EC2_IP:8080/swagger-ui.html
- [ ] Test an actual endpoint through Swagger

## ☑️ Troubleshooting (Quick)

If deployment fails, check:

- GitHub Actions logs
- Verify all secrets are set correctly
- Check SSH key format (no extra spaces)
- Verify EC2_HOST is accessible from internet
- Check logs: `./manage-deployment.sh logs`
- Verify database connection from EC2
- Check environment variables are set
- Verify port 8080 is not already in use
- Test from EC2: `ssh ... "curl localhost:8080/actuator/health"`

## 🎯 Success Criteria

You've successfully deployed when:

- Workflow completes successfully
- Container is running on EC2
- Health check returns 200 OK
- Swagger UI is accessible
- API endpoints respond correctly
- Database queries work
- No errors in application logs
