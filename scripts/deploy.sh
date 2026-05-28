#!/bin/bash
# =============================================================================
# HHS Shared Deployment Script
# =============================================================================
# Used by both docker-publish.yml and deploy.yml workflows.
# Must be sourced or executed on the ECS server.
#
# Environment variables required:
#   OWNER       - GitHub repository owner (lowercase)
#   IMAGE_TAG   - Docker image tag to deploy (default: latest)
#
# =============================================================================

set -euo pipefail

HEALTH_HOST="${HEALTH_HOST:-http://localhost}"
MAX_RETRIES="${MAX_RETRIES:-5}"
RETRY_INTERVAL="${RETRY_INTERVAL:-15}"

echo "=== HHS Deployment ($(date '+%Y-%m-%d %H:%M:%S')) ==="

# ---------------------------------------------------------------------------
# 1. Remove development override file
# ---------------------------------------------------------------------------
rm -f docker-compose.override.yml
echo "[1/5] Removed docker-compose.override.yml"

# ---------------------------------------------------------------------------
# 2. Pull latest code and docker-compose files
# ---------------------------------------------------------------------------
git pull origin master
rm -f docker-compose.override.yml
echo "[2/5] Updated from git repository"

# ---------------------------------------------------------------------------
# 3. Login to GHCR and pull images
# ---------------------------------------------------------------------------
echo "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_ACTOR}" --password-stdin 2>/dev/null
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull --ignore-buildable 2>&1
echo "[3/5] Pulled latest images"

# ---------------------------------------------------------------------------
# 4. Stop old containers and start new ones
# ---------------------------------------------------------------------------
docker compose -f docker-compose.yml -f docker-compose.prod.yml down
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --force-recreate
echo "[4/5] Containers restarted"

# ---------------------------------------------------------------------------
# 5. Health check with retry loop
# ---------------------------------------------------------------------------
echo "[5/5] Running health check (max ${MAX_RETRIES} retries, ${RETRY_INTERVAL}s interval)..."

ATTEMPT=0
while [ $ATTEMPT -lt $MAX_RETRIES ]; do
    ATTEMPT=$((ATTEMPT + 1))

    if curl -sf "${HEALTH_HOST}/actuator/health" > /dev/null 2>&1; then
        echo "Health check PASSED (attempt ${ATTEMPT}/${MAX_RETRIES})"
        echo ""
        echo "=== Running containers ==="
        docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
        echo ""
        echo "Deployment successful!"
        exit 0
    fi

    echo "  Attempt ${ATTEMPT}/${MAX_RETRIES} failed, retrying in ${RETRY_INTERVAL}s..."
    sleep "${RETRY_INTERVAL}"
done

echo ""
echo "ERROR: Health check FAILED after ${MAX_RETRIES} attempts"
echo "=== Container logs ==="
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs --tail=50 backend
exit 1
