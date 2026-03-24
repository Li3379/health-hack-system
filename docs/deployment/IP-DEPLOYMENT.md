# IP-Based Deployment Guide

This guide covers deploying HHS to a server using IP address (e.g., `http://47.112.6.180`).

## Prerequisites

- Docker and Docker Compose installed on the server
- MySQL 8.0+ and Redis 7.0+ (or use the Docker Compose setup)
- Ports 80 and 443 open on the firewall

## Quick Configuration

### 1. Server Environment Variables

Create a `.env` file on your server:

```bash
# Required
SPRING_PROFILES_ACTIVE=prod
DB_PASSWORD=your_mysql_password
REDIS_PASSWORD=your_redis_password
JWT_SECRET=your_jwt_secret_at_least_32_characters

# Server URL Configuration (IMPORTANT!)
# Use your server's IP or domain WITHOUT any path suffix
BASE_URL=http://47.112.6.180
ALLOWED_ORIGINS=http://47.112.6.180

# Optional - AI Features
DASH_SCOPE_API_KEY=your_dashscope_api_key

# Optional - Device OAuth (if using health device sync)
DEVICE_ENCRYPTION_KEY=your_32_character_encryption_key
HUAWEI_CLIENT_ID=your_huawei_client_id
HUAWEI_CLIENT_SECRET=your_huawei_client_secret
XIAOMI_CLIENT_ID=your_xiaomi_client_id
XIAOMI_CLIENT_SECRET=your_xiaomi_client_secret

# Optional - Baidu OCR
BAIDU_OCR_API_KEY=your_baidu_api_key
BAIDU_OCR_SECRET_KEY=your_baidu_secret_key
```

### 2. GitHub Secrets Configuration

Configure these secrets in your GitHub repository (Settings → Secrets and variables → Actions):

| Secret Name | Value | Notes |
|-------------|-------|-------|
| `VITE_API_BASE_URL` | `http://47.112.6.180` | **No `/api` suffix!** |
| `VITE_WS_BASE_URL` | `ws://47.112.6.180` | **No `/ws` suffix!** |

Or for Docker deployment with nginx proxy:

| Secret Name | Value | Notes |
|-------------|-------|-------|
| `VITE_API_BASE_URL` | (empty) | Uses nginx proxy |
| `VITE_WS_BASE_URL` | (empty) | Auto-detected from browser URL |

### 3. Deploy

```bash
# Pull the latest images
docker compose pull

# Start the services
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Common Issues and Solutions

### Issue: API paths show `/api/api/...`

**Cause:** `VITE_API_BASE_URL` was set with `/api` suffix.

**Wrong:**
```
VITE_API_BASE_URL=http://47.112.6.180/api
```

**Correct:**
```
VITE_API_BASE_URL=http://47.112.6.180
```

The frontend already adds `/api` prefix to all API calls. Adding it to the base URL causes duplication.

### Issue: WebSocket connection fails

**Cause:** `VITE_WS_BASE_URL` was set with `/ws` suffix or not configured.

**Wrong:**
```
VITE_WS_BASE_URL=ws://47.112.6.180/ws
```

**Correct:**
```
VITE_WS_BASE_URL=ws://47.112.6.180
```

Or leave empty to auto-detect from browser URL:
```
VITE_WS_BASE_URL=
```

### Issue: CORS errors in browser console

**Cause:** `ALLOWED_ORIGINS` not configured or incorrect.

**Solution:** Ensure `ALLOWED_ORIGINS` matches your server URL exactly:
```bash
ALLOWED_ORIGINS=http://47.112.6.180
```

For multiple origins (comma-separated):
```bash
ALLOWED_ORIGINS=http://47.112.6.180,https://yourdomain.com
```

### Issue: Device OAuth redirects to localhost

**Cause:** `BASE_URL` not configured.

**Solution:** Set `BASE_URL` to your server address:
```bash
BASE_URL=http://47.112.6.180
```

## Configuration Checklist

Before deployment, verify:

- [ ] `BASE_URL` set to server address (no path suffix)
- [ ] `ALLOWED_ORIGINS` set to server address (no path suffix)
- [ ] `VITE_API_BASE_URL` set correctly (no `/api` suffix)
- [ ] `VITE_WS_BASE_URL` set correctly (no `/ws` suffix)
- [ ] `SPRING_PROFILES_ACTIVE=prod` is set
- [ ] All required secrets configured (`DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`)
- [ ] Frontend image rebuilt after changing GitHub secrets

## Rebuilding Frontend Image

The frontend Docker image bakes in environment variables at build time. After changing GitHub secrets:

1. Go to Actions tab in GitHub
2. Select "Docker Publish" workflow
3. Click "Run workflow" to rebuild images
4. Pull new images on server: `docker compose pull`
5. Restart services: `docker compose up -d`

## Port Configuration

By default, the application runs on port 80. To change:

```yaml
# docker-compose.prod.yml
services:
  frontend:
    ports:
      - "8080:80"  # Change 8080 to your desired port
```

Then update your URLs:
```bash
BASE_URL=http://47.112.6.180:8080
ALLOWED_ORIGINS=http://47.112.6.180:8080
VITE_API_BASE_URL=http://47.112.6.180:8080
VITE_WS_BASE_URL=ws://47.112.6.180:8080
```