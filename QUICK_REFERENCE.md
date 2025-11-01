# RunWithMe API - Quick Reference

## 🚀 Deployment Commands

```bash
# Deploy to AWS
git push origin main

# Watch deployment
# Go to GitHub → Actions tab
```

## 🧪 Local Testing

```bash
# Test Docker locally
./test-deployment.sh

# Access locally
http://localhost:8080/swagger-ui.html
http://localhost:8080/actuator/health
```

## 🛠️ Management Commands

```bash
# Status & Health
./manage-deployment.sh status      # Container status
./manage-deployment.sh health      # Health check
./manage-deployment.sh info        # Show URLs

# Logs
./manage-deployment.sh logs        # Last 50 lines
./manage-deployment.sh logs-live   # Follow in real-time

# Operations
./manage-deployment.sh restart     # Restart container
./manage-deployment.sh stop        # Stop container
./manage-deployment.sh start       # Start container
./manage-deployment.sh clean       # Remove old images

# Access
./manage-deployment.sh shell       # Shell in container
./manage-deployment.sh ssh         # SSH to EC2
```

## 🌐 API Endpoints

```
http://YOUR_EC2_IP:8080                    # Base API
http://YOUR_EC2_IP:8080/swagger-ui.html   # Swagger UI
http://YOUR_EC2_IP:8080/actuator/health   # Health Check
http://YOUR_EC2_IP:8080/actuator/info     # Application Info
http://YOUR_EC2_IP:8080/v3/api-docs       # OpenAPI Docs
```

## 🔧 Manual Operations

```bash
# SSH to EC2
ssh -i your-key.pem ec2-user@YOUR_EC2_IP

# View container logs
docker logs -f runwithme-api

# Check container status
docker ps

# Restart container
docker restart runwithme-api

# Stop container
docker stop runwithme-api

# Start container
docker start runwithme-api

# Remove old images
docker image prune -af

# Check disk space
df -h

# Check memory
free -m
```

## 🐛 Troubleshooting

```bash
# Container won't start
./manage-deployment.sh logs

# Check if running
./manage-deployment.sh status

# Test health locally on EC2
ssh ... "curl localhost:8080/actuator/health"

# View all containers
ssh ... "docker ps -a"

# Restart Docker service
ssh ... "sudo systemctl restart docker"
```

## 📝 GitHub Secrets Required

| Secret | Example |
|--------|---------|
| `EC2_SSH_PRIVATE_KEY` | Contents of .pem file |
| `EC2_HOST` | `3.120.179.202` |
| `EC2_USER` | `ec2-user` or `ubuntu` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://...` |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |

## 🔐 Security Group Rules

| Type | Port | Source | Purpose |
|------|------|--------|---------|
| SSH | 22 | Your IP | Deployment |
| Custom TCP | 8080 | 0.0.0.0/0 | API Access |
| PostgreSQL | 5432 | EC2 SG | Database |

## 📚 Documentation Files

- `QUICKSTART.md` - 5-minute setup guide
- `DEPLOYMENT_CHECKLIST.md` - Pre-deployment checklist
- `DEPLOYMENT.md` - Complete deployment guide
- `DEPLOYMENT_SUMMARY.md` - Full overview

## 💡 Quick Tips

1. **Always test locally first**: `./test-deployment.sh`
2. **Check logs after deploy**: `./manage-deployment.sh logs`
3. **Monitor health**: `./manage-deployment.sh health`
4. **Keep secrets secure**: Never commit .env or .pem files
5. **Use management script**: It's your best friend!

## 🎯 Common Tasks

### Deploy new version
```bash
git add .
git commit -m "Your changes"
git push origin main
```

### Check if deployment succeeded
```bash
./manage-deployment.sh status
./manage-deployment.sh health
```

### View recent logs
```bash
./manage-deployment.sh logs
```

### Restart after config change
```bash
./manage-deployment.sh restart
```

### Access Swagger UI
```bash
open http://YOUR_EC2_IP:8080/swagger-ui.html
```

## 🆘 Emergency Commands

```bash
# Container crashed - check logs
./manage-deployment.sh logs

# Out of memory - check usage
ssh ... "free -m"

# Out of disk - clean images
./manage-deployment.sh clean

# Complete restart
./manage-deployment.sh stop
./manage-deployment.sh start

# Nuclear option - rebuild
ssh ... "docker stop runwithme-api && docker rm runwithme-api"
# Then push to main to redeploy
```

## ✅ Health Check Responses

**Healthy**:
```json
{"status": "UP"}
```

**Unhealthy**:
```json
{"status": "DOWN"}
```

If unhealthy, check:
1. Database connection
2. Application logs
3. Container status

---

**Keep this handy!** 📌

