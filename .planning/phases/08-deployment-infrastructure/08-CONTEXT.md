# Phase 8: Deployment Infrastructure - Context

**Gathered:** 2026-03-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Create containerized deployment configuration for the HHS health management system. Deliverables include Docker images for backend (Spring Boot) and frontend (Vue 3 + nginx), Docker Compose files for development and production environments, and nginx configuration for reverse proxy and SPA routing.

**Scope:**
- Backend Dockerfile (multi-stage)
- Frontend Dockerfile (multi-stage with nginx)
- nginx configuration
- docker-compose.yml (base)
- docker-compose.override.yml (dev overrides)
- docker-compose.prod.yml (production overrides)
- Health checks for all services

**Out of scope:**
- CI/CD workflows (Phase 9)
- Kubernetes deployment
- SSL certificate configuration
- Monitoring/observability (v2)
- Cloud deployment

</domain>

<decisions>
## Implementation Decisions

### Build Strategy
- Multi-stage Dockerfiles for both backend and frontend
- Backend: Maven build stage + JRE runtime stage
- Frontend: npm build stage + nginx runtime stage
- Self-contained builds, consistent across environments

### nginx Configuration
- Single nginx instance in frontend container
- Serves Vue SPA (static files)
- Reverse proxies `/api` and `/uploads` to backend container
- SPA routing support (fallback to index.html)
- Gzip compression enabled

### Configuration Pattern
- Base `docker-compose.yml` with common configuration
- `docker-compose.override.yml` for development (auto-loaded by Docker Compose)
- `docker-compose.prod.yml` for production (explicit `-f` flag)
- DRY principle, single source of truth

### Data Persistence
- Named volumes for all persistent data:
  - `mysql_data` for MySQL database
  - `redis_data` for Redis persistence
  - `uploads_data` for file uploads
- No bind mounts — cleaner, more portable

### Health Checks
- Backend: HTTP GET on `/actuator/health`
- MySQL: `mysqladmin ping -h localhost`
- Redis: `redis-cli ping`
- nginx: HTTP GET on `/` (frontend)
- Health check intervals: 30s, timeout: 10s, retries: 3

### Local Development Setup
- MySQL and Redis included in Docker Compose
- One-command dev environment: `docker compose up`
- Environment variables from `.env` file
- Backend runs with `dev` Spring profile
- Hot reload NOT included in Docker setup (use local dev for that)

### Claude's Discretion
- Exact base image versions (e.g., `eclipse-temurin:17-jre-alpine` vs `17-jre`)
- nginx configuration details (worker_processes, client_max_body_size)
- Specific health check timing values (can use sensible defaults)
- Docker network naming convention
- Whether to include `.dockerignore` files

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Configuration Files
- `HHS/hhs-backend/src/main/resources/application.yml` — Main Spring Boot config, server port 8082
- `HHS/hhs-backend/src/main/resources/application-dev.yml` — Dev profile, environment variables, allowed origins
- `HHS/hhs-backend/src/main/resources/application-prod.yml` — Production profile, security settings
- `HHS/hhs-frontend-v2/vite.config.ts` — Vite config, dev server proxy settings

### Build Configuration
- `HHS/hhs-backend/pom.xml` — Maven build, Spring Boot plugin, Java 17
- `HHS/hhs-frontend-v2/package.json` — npm scripts, build command: `vue-tsc && vite build`

### Documentation
- `HHS/README.md` — Project overview, environment variables
- `HHS/hhs-backend/.env.example` — Backend environment variable documentation
- `HHS/hhs-frontend-v2/.env.example` — Frontend environment variable documentation

</canonical_refs>

<code_context>
## Existing Code Insights

### Backend Structure
- Spring Boot 3.2.0 with Java 17
- Maven build with `spring-boot-maven-plugin`
- Server port: 8082
- Dependencies: MySQL, Redis, LangChain4j, JWT, MyBatis Plus
- File uploads to `./uploads` directory
- Actuator endpoints: `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/loggers`

### Frontend Structure
- Vue 3 + Vite + TypeScript
- Element Plus UI framework
- Dev server port: 5173
- Build output: `dist/` directory
- API proxy: `/api` → `http://localhost:8082`
- Uploads proxy: `/uploads` → `http://localhost:8082`

### Integration Points
- Frontend nginx must proxy `/api` to backend container
- Frontend nginx must proxy `/uploads` to backend container
- Backend needs MySQL connection (port 3306)
- Backend needs Redis connection (port 6379)
- All services on same Docker network

### Environment Variables (Required)
- `JWT_SECRET` — 256-bit secret for JWT tokens
- `DB_PASSWORD` — MySQL root password
- `REDIS_PASSWORD` — Redis password
- `DASH_SCOPE_API_KEY` — AI model API key
- `DEVICE_ENCRYPTION_KEY` — Device token encryption key
- `ALLOWED_ORIGINS` — CORS allowed origins (production)

</code_context>

<specifics>
## Specific Ideas

- "One-command dev environment" — user wants simple `docker compose up` to work
- Dev setup should include MySQL and Redis containers (no external setup required)
- Production compose should have proper restart policies (`always` or `unless-stopped`)

</specifics>

<deferred>
## Deferred Ideas

- Docker image registry publishing — Phase 9 (CI/CD)
- SSL/TLS certificate configuration — v2 milestone
- Monitoring with Prometheus/Grafana — v2 milestone
- Kubernetes deployment — out of scope (Docker Compose sufficient)

</deferred>

---

*Phase: 08-deployment-infrastructure*
*Context gathered: 2026-03-20*