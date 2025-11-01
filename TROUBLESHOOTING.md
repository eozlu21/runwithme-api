# Troubleshooting Guide

This guide helps you solve common deployment issues.

## Docker Command Not Found in GitHub Actions

### Problem
GitHub Actions workflow fails with error:
```
-bash: line X: docker: command not found
```

But Docker works fine when you SSH manually.

### Cause
When GitHub Actions connects via SSH in non-interactive mode, the PATH environment variable doesn't include Docker's location. This is because non-interactive SSH sessions don't source the full bash profile.

### Solution 1: Update Workflows (RECOMMENDED - Already Done)
The workflows have been updated to use full paths to Docker:
- `/usr/bin/docker` instead of `docker`
- Set PATH explicitly at the start of SSH commands

### Solution 2: Verify Docker Installation on EC2
SSH into your EC2 instance and verify:

```bash
# Check Docker is installed and in the expected location
which docker
# Should output: /usr/bin/docker

# Check Docker version
docker --version

# Check current user is in docker group
groups
# Should include 'docker'

# Test Docker without sudo
docker ps
# Should work without permission errors
```

### Solution 3: Test Non-Interactive Docker Access
From your local machine, test how GitHub Actions sees Docker:

```bash
# Replace with your actual values
ssh -i your-key.pem ubuntu@your-ec2-ip '/usr/bin/docker --version'
```

If this fails, Docker isn't accessible in non-interactive mode.

### Solution 4: Re-run EC2 Setup Script
The `setup-ec2.sh` script has been updated to properly configure Docker for non-interactive sessions:

```bash
# On your EC2 instance
cd ~/runwithme-api
./setup-ec2.sh

# IMPORTANT: Log out and log back in
exit
# Then SSH back in
```

### Solution 5: Manual Docker Configuration
If the setup script doesn't work, configure manually:

```bash
# Ensure docker group exists and user is in it
sudo groupadd docker 2>/dev/null || true
sudo usermod -aG docker $USER

# Fix socket permissions
sudo chmod 666 /var/run/docker.sock

# Add PATH to .bashrc for non-interactive sessions
echo 'export PATH="/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin:$PATH"' >> ~/.bashrc

# Log out and back in
exit
```

---

## Container Won't Start

### Problem
Container immediately stops or keeps restarting.

### Check Logs
```bash
./manage-deployment.sh logs
# or
docker logs runwithme-api
```

### Common Causes

#### 1. Database Connection Failed
**Symptoms:** Logs show connection refused or timeout

**Solution:**
```bash
# Test database connection from EC2
psql -h YOUR_DB_HOST -U YOUR_USER -d YOUR_DB_NAME

# Check security group allows EC2 to reach database
# Check database credentials in GitHub secrets
```

#### 2. Port Already in Use
**Symptoms:** Logs show "address already in use"

**Solution:**
```bash
# Find what's using port 8080
sudo lsof -i :8080
# or
sudo netstat -tulpn | grep 8080

# Stop the conflicting service or use a different port
```

#### 3. Out of Memory
**Symptoms:** Container killed, EC2 has low memory

**Solution:**
```bash
# Check memory usage
free -m

# Reduce Java heap size in workflow:
# -e JAVA_OPTS="-Xms128m -Xmx256m"

# Or upgrade EC2 instance type
```

#### 4. Wrong Environment Variables
**Symptoms:** Application starts but fails immediately

**Solution:**
- Check all GitHub secrets are set correctly
- Verify database URL format: `jdbc:postgresql://host:5432/dbname`
- Check for typos in secret names

---

## GitHub Actions Workflow Fails

### Check Build Logs
1. Go to your GitHub repository
2. Click "Actions" tab
3. Click on the failed workflow run
4. Expand each step to see errors

### Common Issues

#### 1. SSH Connection Failed
**Error:** "Permission denied" or "Connection refused"

**Solutions:**
- Check EC2_HOST is correct (public IP)
- Check EC2_SSH_PRIVATE_KEY is complete (includes BEGIN/END lines)
- Check security group allows SSH from GitHub Actions IPs
- Check EC2 instance is running

#### 2. Secrets Not Set
**Error:** Variables appear empty in logs

**Solution:**
- Go to Repository → Settings → Secrets and variables → Actions
- Verify all required secrets exist
- Re-enter secrets if needed (copy-paste carefully)

#### 3. Build Fails
**Error:** Gradle build errors

**Solution:**
```bash
# Test build locally first
./gradlew clean build

# Fix any compilation errors
# Commit and push again
```

---

## Can't Access API

### Problem
Deployment succeeds but can't access the API.

### Checklist

#### 1. Container Running?
```bash
./manage-deployment.sh status
# or
docker ps
```
Should show container "Up" for several seconds/minutes.

#### 2. Application Started?
```bash
./manage-deployment.sh logs
```
Look for: "Started RunwithmeApiApplication in X seconds"

#### 3. Health Check from EC2
```bash
# SSH into EC2
curl http://localhost:8080/actuator/health
```
Should return: `{"status":"UP"}`

#### 4. Security Group
- Check security group allows port 8080
- Inbound rule: Port 8080, Source: 0.0.0.0/0 (or your IP)

#### 5. Try from Local Machine
```bash
curl http://YOUR_EC2_IP:8080/actuator/health
```

#### 6. Check Firewall on EC2
```bash
# Check if UFW is active
sudo ufw status

# If active, allow port 8080
sudo ufw allow 8080
```

---

## Database Connection Issues

### Problem
Application starts but database operations fail.

### Debug Steps

#### 1. Test from EC2
```bash
# SSH into EC2
psql -h YOUR_DB_HOST -U YOUR_USER -d YOUR_DB_NAME

# If this fails, the problem is network/credentials
# If this works, check application configuration
```

#### 2. Check Database Security Group
- Database security group must allow inbound from EC2 security group
- Port: 5432 (PostgreSQL) or appropriate port
- Source: EC2 security group ID or EC2 private IP

#### 3. Check Database is Running
```bash
# For RDS
aws rds describe-db-instances --region YOUR_REGION
```

#### 4. Verify Connection String
```
Format: jdbc:postgresql://HOST:PORT/DATABASE_NAME
Example: jdbc:postgresql://mydb.xxxxx.eu-central-1.rds.amazonaws.com:5432/runwithme
```

---

## Disk Space Issues

### Problem
Builds fail or container won't start due to no space.

### Check Disk Space
```bash
# SSH into EC2
df -h
```

### Clean Up
```bash
# Remove old Docker images
docker image prune -af

# Remove stopped containers
docker container prune -f

# Remove unused volumes
docker volume prune -f

# Check space again
df -h
```

### Prevent Future Issues
- Choose EC2 instance with more disk space
- Configure log rotation (already in setup-ec2.sh)
- Regularly clean up old images

---

## Quick Diagnostic Commands

Run these to quickly check your deployment:

```bash
# 1. Check container status
docker ps -a | grep runwithme-api

# 2. Check recent logs
docker logs --tail 50 runwithme-api

# 3. Check if port is listening
sudo netstat -tulpn | grep 8080

# 4. Test health endpoint locally
curl http://localhost:8080/actuator/health

# 5. Check disk space
df -h

# 6. Check memory
free -m

# 7. Check running processes
ps aux | grep java

# 8. Test database connection
psql -h YOUR_DB_HOST -U YOUR_USER -d YOUR_DB_NAME

# 9. Check Docker info
docker info

# 10. Check system logs
sudo journalctl -u docker --no-pager -n 50
```

---

## Getting Help

If you're still stuck:

1. ✅ Reviewed all sections above
2. ✅ Checked GitHub Actions logs
3. ✅ Checked application logs on EC2
4. ✅ Verified all secrets are correct
5. ✅ Tested database connection
6. ✅ Checked security groups

Then gather this information:
- GitHub Actions workflow logs (full output)
- Application logs from EC2: `docker logs runwithme-api`
- Container status: `docker ps -a`
- EC2 security group rules
- Database connection details (host, port)
- Any error messages

And seek help from your team or cloud provider support.

---

## Prevention Best Practices

To avoid issues:

1. **Test Locally First**
   - Always test with `./test-deployment.sh` before pushing
   - Verify all environment variables are set

2. **Monitor Resources**
   - Check disk space regularly
   - Monitor memory usage
   - Review logs periodically

3. **Keep Secrets Secure**
   - Never commit secrets to git
   - Use GitHub secrets for sensitive data
   - Rotate credentials periodically

4. **Document Changes**
   - Note any configuration changes
   - Keep security group rules documented
   - Track database schema changes

5. **Regular Updates**
   - Keep Docker updated
   - Update base images
   - Apply security patches

---

**Last Updated:** November 2025

