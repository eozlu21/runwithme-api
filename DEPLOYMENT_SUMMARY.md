# 🎉 AWS Deployment Setup Complete!

Your RunWithMe API is now ready to deploy to AWS automatically via GitHub Actions.

## 📦 What Was Created

### GitHub Workflows
1. **`.github/workflows/deploy-to-aws.yml`** (Production-grade)
   - Builds with Gradle
   - Pushes to AWS ECR
   - Deploys to EC2
   - Best for: Production environments

2. **`.github/workflows/deploy-to-aws-simple.yml`** (Quick & Easy)
   - Builds Docker image directly on EC2
   - No ECR required
   - Best for: Getting started quickly

### Documentation
- **`QUICKSTART.md`** - 5-minute setup guide (START HERE!)
- **`DEPLOYMENT.md`** - Complete deployment documentation
- **`setup-ec2.sh`** - Automated EC2 instance setup script
- **`test-deployment.sh`** - Test Docker deployment locally

### Application Updates
- ✅ Added Spring Boot Actuator for health checks
- ✅ Configured health endpoints at `/actuator/health`
- ✅ Enhanced monitoring capabilities

## 🚀 Next Steps (Choose One Path)

### Path A: Quick Start (Recommended for First Time)

1. **Test locally first**
   ```bash
   ./test-deployment.sh
   ```

2. **Setup your EC2 instance**
   ```bash
   # SSH into EC2, then run:
   curl -fsSL https://your-repo/setup-ec2.sh | bash
   # OR copy setup-ec2.sh and run it
   ```

3. **Add GitHub Secrets**
   - `EC2_SSH_PRIVATE_KEY` - Your .pem file contents
   - `EC2_HOST` - Your EC2 public IP
   - `EC2_USER` - Usually `ec2-user` or `ubuntu`
   - `SPRING_DATASOURCE_URL` - Database URL
   - `SPRING_DATASOURCE_USERNAME` - Database username
   - `SPRING_DATASOURCE_PASSWORD` - Database password

4. **Push and Deploy**
   ```bash
   git add .
   git commit -m "Add AWS deployment"
   git push origin main
   ```

5. **Access Your API**
   - API: `http://YOUR_EC2_IP:8080`
   - Swagger: `http://YOUR_EC2_IP:8080/swagger-ui.html`
   - Health: `http://YOUR_EC2_IP:8080/actuator/health`

### Path B: Production Setup (with ECR)

Follow the detailed guide in `DEPLOYMENT.md` which includes:
- ECR repository setup
- IAM permissions configuration
- Advanced security settings
- Production best practices

## 🔧 What Changed in Your Project

### New Dependencies
```kotlin
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

### New Configuration
```properties
# application.properties
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when-authorized
management.health.defaults.enabled=true
```

### New Endpoints Available
- `GET /actuator/health` - Application health status
- `GET /actuator/info` - Application information
- `GET /actuator/metrics` - Application metrics

## 📋 Pre-Deployment Checklist

- [ ] EC2 instance is running
- [ ] Docker installed on EC2
- [ ] Security group allows port 8080 and 22
- [ ] Database is accessible from EC2
- [ ] GitHub secrets configured
- [ ] Tested locally with `./test-deployment.sh`
- [ ] `.env` file created (for local testing)

## 🔐 Required GitHub Secrets

### For Simple Deployment (5 secrets minimum)
| Secret | Description |
|--------|-------------|
| `EC2_SSH_PRIVATE_KEY` | SSH private key (.pem file) |
| `EC2_HOST` | EC2 public IP or DNS |
| `EC2_USER` | SSH username (ec2-user/ubuntu) |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |

### Additional for ECR Deployment
| Secret | Description |
|--------|-------------|
| `AWS_ACCESS_KEY_ID` | AWS IAM access key |
| `AWS_SECRET_ACCESS_KEY` | AWS IAM secret key |

## 🎯 Which Workflow Should I Use?

### Use `deploy-to-aws-simple.yml` if:
- ✅ You're just getting started
- ✅ You want the simplest setup
- ✅ You don't need ECR
- ✅ Building on EC2 is acceptable

### Use `deploy-to-aws.yml` if:
- ✅ You need production-grade deployment
- ✅ You want container registry benefits
- ✅ You're deploying to multiple environments
- ✅ You need image versioning

### Keep Both:
Both workflows can coexist! Use simple for development and full version for production.

## 🧪 Testing Commands

```bash
# Test locally
./test-deployment.sh

# Check EC2 container status
ssh -i your-key.pem ec2-user@YOUR_EC2_IP "docker ps"

# View logs
ssh -i your-key.pem ec2-user@YOUR_EC2_IP "docker logs -f runwithme-api"

# Test health endpoint locally
curl http://localhost:8080/actuator/health

# Test health endpoint on EC2
curl http://YOUR_EC2_IP:8080/actuator/health
```

## 🐛 Common Issues & Solutions

### Issue: Container won't start
```bash
# Check logs
docker logs runwithme-api

# Common causes:
# 1. Database connection failed - check credentials and security groups
# 2. Port already in use - stop existing container
# 3. Out of memory - adjust JAVA_OPTS
```

### Issue: Can't SSH to EC2
```bash
# Check key permissions
chmod 400 your-key.pem

# Verify security group allows SSH from your IP
# AWS Console → EC2 → Security Groups → Check inbound rules
```

### Issue: Workflow fails
```bash
# Check GitHub Actions logs
# Common causes:
# 1. Missing secrets
# 2. Wrong EC2_USER (try ec2-user or ubuntu)
# 3. SSH key format issues (should be entire .pem file)
```

## 📊 Monitoring Your Deployment

### Health Check
```bash
curl http://YOUR_EC2_IP:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

### Container Status
```bash
ssh -i your-key.pem ec2-user@YOUR_EC2_IP "docker ps"
```

### Application Logs
```bash
ssh -i your-key.pem ec2-user@YOUR_EC2_IP "docker logs --tail 100 runwithme-api"
```

## 📚 Documentation Reference

- **Quick Start**: `QUICKSTART.md` - 5 min setup
- **Full Guide**: `DEPLOYMENT.md` - Complete documentation
- **Workflows**: `.github/workflows/` - GitHub Actions configs
- **Scripts**: 
  - `setup-ec2.sh` - EC2 setup automation
  - `test-deployment.sh` - Local testing

## 🎓 Learning Resources

- [GitHub Actions Docs](https://docs.github.com/en/actions)
- [AWS EC2 Guide](https://docs.aws.amazon.com/ec2/)
- [Docker Documentation](https://docs.docker.com/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

## 🆘 Getting Help

If you encounter issues:

1. Check the troubleshooting section in `DEPLOYMENT.md`
2. Review GitHub Actions logs
3. Check EC2 container logs
4. Verify all secrets are configured correctly
5. Ensure security groups are properly configured

## ✅ Verification Checklist

After deployment, verify:

- [ ] Container is running: `docker ps`
- [ ] Health check passes: `curl http://YOUR_EC2_IP:8080/actuator/health`
- [ ] Swagger UI loads: `http://YOUR_EC2_IP:8080/swagger-ui.html`
- [ ] Database connection works
- [ ] Logs show no errors: `docker logs runwithme-api`

## 🎊 You're All Set!

Your RunWithMe API is now configured for automated AWS deployment!

Start with the **QUICKSTART.md** guide to deploy in the next 5 minutes.

---

**Happy Deploying! 🚀**

