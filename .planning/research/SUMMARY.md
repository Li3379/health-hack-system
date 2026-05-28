# Research Summary: Health Hack System — Optimization & Redeployment

## Stack

| Component | Current | Status |
|---|---|---|
| Spring Boot | 3.2.0 | Stable, supported |
| Java | 17 | LTS, stable |
| MyBatis-Plus | 3.5.5 | Stable |
| Vue | 3.4.15 | Current |
| Vite | 5.x | Current |
| Element Plus | 2.5.6 | Stable |
| MySQL | 8.0 | LTS, stable |
| Redis | 7-alpine | Current |
| Docker Compose | v2 (compose spec) | Current |
| GitHub Actions | Current runner | Stable |
| LangChain4j | 0.35.0 | Current for Alibaba Qwen integration |

## Architecture

### Current Deployment Flow

```
GitHub Push (master) → GitHub Actions → Build Docker images → Push to GHCR
→ SSH to Alibaba Cloud ECS → docker compose pull → docker compose up -d
→ Health check (curl /actuator/health)
```

### Deployment Issues Identified

1. **Duplicate deploy logic** — Same SSH script in `docker-publish.yml` and `deploy.yml`
2. **No CI gate before Docker build** — Broken code can be pushed and deployed
3. **Docker no-cache** — Every build re-downloads all dependencies
4. **Backend port exposed** — 8082 accessible from outside Docker network in prod
5. **Fragile health check** — Hardcoded `sleep 60` instead of retry loop
6. **No database migration** — Schema changes require manual SQL execution
7. **Auto-deploy bypasses environment protection** — `docker-publish.yml` has no approval gate

## Pitfalls

1. **Schema migration without tooling** — Manual SQL on production is error-prone. Start with versioned SQL scripts checked into the repo before considering Flyway/Liquibase.
2. **Changing ScoreUpdatedEvent** — This event is published from `HealthScoreServiceImpl`. All constructors must be updated, and all publishers must be checked to ensure dimension scores are passed.
3. **Docker layer caching** — Removing `no-cache: true` can cause stale builds if dependency changes aren't properly detected. The current Dockerfiles use multi-stage builds which handle this well for source code changes, but dependency cache may need explicit layer ordering.
4. **Backend port exposure** — Removing the port mapping requires ensuring nginx correctly proxies all `/api/*` traffic. The current nginx.conf already does this, but verify after change.
5. **CI as Docker build prerequisite** — Adding `needs: [build-backend, build-frontend]` to deploy job will slow down deployment (must wait for both CI pipelines). Consider running CI and Docker build in parallel with a merge step.
