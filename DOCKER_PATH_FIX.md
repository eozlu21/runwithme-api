# Docker Command Not Found - Fix Summary

## Problem
GitHub Actions was failing with:
```
-bash: line X: docker: command not found
```

Even though Docker works fine when SSHing manually to the EC2 instance.

## Root Cause
When GitHub Actions connects via SSH in **non-interactive mode**, the shell doesn't source the full `.bash_profile` or `.bashrc`, so the PATH doesn't include Docker's location (`/usr/bin/docker`).

When you SSH manually (interactive session), the PATH is properly set and Docker works.

## Solutions Applied

### 1. Updated GitHub Actions Workflows ✅
**Files Changed:**
- `.github/workflows/deploy-to-aws-simple.yml`
- `.github/workflows/deploy-to-aws.yml`

**Changes:**
- Use full path to Docker: `/usr/bin/docker` instead of `docker`
- Use full path to AWS CLI: `/usr/bin/aws` instead of `aws`
- Explicitly set PATH at the start of SSH commands
- Source Docker environment if it exists

**Before:**
```bash
ssh ... << 'EOF'
  docker stop container
  docker build -t image .
EOF
```

**After:**
```bash
ssh ... << 'EOF'
  export PATH="/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin:$PATH"
  /usr/bin/docker stop container
  /usr/bin/docker build -t image .
EOF
```

### 2. Enhanced EC2 Setup Script ✅
**File Changed:**
- `setup-ec2.sh`

**Additions:**
- Automatic Docker installation (if not present)
- Automatic AWS CLI installation (if not present)
- Docker group configuration
- PATH configuration for non-interactive sessions
- Socket permissions fix
- Verification tests

**To apply the enhanced setup:**
```bash
# SSH into your EC2 instance
cd ~/runwithme-api
./setup-ec2.sh

# IMPORTANT: Log out and back in for changes to take effect
exit
# Then SSH back in and verify
docker ps
```

### 3. Created Comprehensive Troubleshooting Guide ✅
**New File:**
- `TROUBLESHOOTING.md`

Contains detailed solutions for:
- Docker command not found issue
- Container startup problems
- Database connection issues
- Network/security group problems
- Disk space issues
- Quick diagnostic commands

### 4. Updated Documentation ✅
**File Updated:**
- `DEPLOYMENT_CHECKLIST.md`

Added references to troubleshooting guide and common issues.

## Next Steps

### Option A: Just Push Your Code (Quick Fix)
The workflow files are now fixed. Simply:

```bash
git add .github/workflows/
git add setup-ec2.sh
git add TROUBLESHOOTING.md
git add DEPLOYMENT_CHECKLIST.md
git commit -m "Fix Docker command not found in non-interactive SSH"
git push origin main
```

The deployment should now work!

### Option B: Update Your EC2 Instance (Recommended)
For better long-term stability:

1. **Push the changes:**
   ```bash
   git add .
   git commit -m "Fix Docker path issues and enhance EC2 setup"
   git push origin main
   ```

2. **Update EC2 configuration:**
   ```bash
   # From your local machine, SSH to EC2
   ssh -i your-key.pem ubuntu@your-ec2-ip
   
   # Pull the latest changes
   cd ~/runwithme-api
   git pull origin main
   
   # Re-run the enhanced setup script
   ./setup-ec2.sh
   
   # IMPORTANT: Log out and back in
   exit
   ```

3. **Verify Docker works in non-interactive mode:**
   ```bash
   # From your local machine
   ssh -i your-key.pem ubuntu@your-ec2-ip '/usr/bin/docker --version'
   ```
   
   Should output Docker version without errors.

4. **Test deployment:**
   - Make a small change and push to main
   - Watch GitHub Actions complete successfully
   - Verify API is accessible

## Verification

After pushing, check:

1. ✅ GitHub Actions workflow completes without "command not found" errors
2. ✅ Container builds successfully
3. ✅ Container runs and stays up
4. ✅ API responds at `http://your-ec2-ip:8080/actuator/health`

## Why This Happens

**Interactive SSH (works):**
```bash
ssh -i key.pem user@host
# Loads: /etc/profile, ~/.bash_profile, ~/.bashrc
# PATH includes: /usr/bin, /usr/local/bin, etc.
# Docker command works!
```

**Non-Interactive SSH (GitHub Actions, was failing):**
```bash
ssh -i key.pem user@host 'docker ps'
# Only loads: ~/.bashrc (and only if configured for non-interactive)
# PATH might be minimal: /usr/bin might not be included
# Docker command not found!
```

**Fix:** Use full paths or explicitly set PATH in the SSH command.

## Additional Notes

- This is a common issue with Ubuntu/Debian-based systems
- Amazon Linux uses different paths and may not have this issue
- The fix works for both Ubuntu and Amazon Linux
- Using full paths is more reliable than relying on PATH

## Related Documentation

- **TROUBLESHOOTING.md** - Detailed solutions for all common issues
- **DEPLOYMENT_CHECKLIST.md** - Step-by-step deployment guide
- **DEPLOYMENT.md** - Complete deployment documentation

---

**Date Fixed:** November 1, 2025
**Issue:** Docker command not found in GitHub Actions
**Status:** ✅ Resolved

