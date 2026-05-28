# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

HHS (Health Hack System) is a health monitoring platform with AI-powered features, real-time tracking, and preventive health assessments. It consists of a Spring Boot backend and a Vue 3 frontend as separate modules in a monorepo.

## Common Commands

### Backend (hhs-backend/)

```bash
cd hhs-backend

# Build and run unit tests (excludes integration tests)
mvn clean verify -DskipITs

# Run a single test class
mvn test -Dtest=AlertServiceTest

# Run a single test method
mvn test -Dtest=AlertServiceTest#testMethod

# Run integration tests (requires Docker for Testcontainers)
mvn verify -DskipTests failsafe:integration-test failsafe:verify

# Run all tests (unit + integration)
mvn clean verify

# Code coverage report (JaCoCo)
mvn test jacoco:report
# Report at: target/site/jacoco/index.html

# Checkstyle lint
mvn checkstyle:check

# Start backend only
mvn spring-boot:run
```

### Frontend (hhs-frontend-v2/)

```bash
cd hhs-frontend-v2

# Install dependencies
npm ci

# Dev server (port 5173, proxies /api to localhost:8082)
npm run dev

# Build (runs vue-tsc then vite build)
npm run build

# Lint
npm run lint:check    # check only
npm run lint          # auto-fix

# Format
npm run format:check  # check only
npm run format        # auto-fix

# Unit tests (Vitest)
npm run test:run      # single run
npm run test          # watch mode
npm run test:coverage

# E2E tests (Playwright, requires running backend)
npx playwright test
npx playwright test e2e/ai-input.spec.ts  # single spec
```

### Docker Compose

```bash
# Development (auto-loads docker-compose.override.yml)
docker compose up

# Production
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

Services: MySQL (port 3307 in dev), Redis (port 6380 in dev), backend (8082), frontend (80).

## Architecture

### Backend Layered Structure (com.hhs)

- **controller/** — REST endpoints. All return `Result<T>` wrapper (`code`, `message`, `data`).
- **service/** — Business logic interfaces.
- **service/impl/** — Service implementations.
- **service/domain/** — Stateless domain logic (scoring, validation, risk analysis). No Spring annotations, pure logic.
- **service/alert/** — Intelligent alert subsystem: deduplication, trend prediction, AI analysis, frequency strategy, recovery notification.
- **service/push/** — Multi-channel push system. `PushChannel` interface with implementations in `push/channel/`: WebSocket, Email, Feishu, WeCom.
- **entity/** — MyBatis-Plus entity classes (23 tables).
- **mapper/** — MyBatis-Plus mapper interfaces.
- **dto/** — Request/response DTOs and VOs.
- **domain/event/** — Spring application events: `AlertGeneratedEvent`, `MetricRecordedEvent`, `OcrProcessingEvent`, `ScoreUpdatedEvent`.
- **domain/handler/** — Event listeners for cache invalidation, alert processing, OCR pipeline.
- **config/** — Spring configuration classes.
- **security/** — JWT auth filter, `JwtUtil`, `SecurityUtils`, `UserDetailsServiceImpl`.
- **exception/** — `BusinessException` (expected errors with `ErrorCode`), `SystemException` (transient errors), `GlobalExceptionHandler`.
- **common/** — `Result<T>`, `PageResult`, `ErrorCode` enum, `Constants`, enums.
- **component/** — Infrastructure components: `BaiduOcrClient`, `ContentFilter`, rate limiters.

### Key Backend Patterns

- **Service Interface + Impl**: All services follow `FooService` (interface) + `FooServiceImpl` (implementation). The impl classes live in `service/impl/`.
- **Domain services** (`service/domain/`): Pure logic classes like `ScoreCalculator`, `RiskScorer`, `MetricValidator`, `ThresholdEvaluator`. These are stateless and testable without Spring context.
- **Error handling**: Throw `BusinessException(ErrorCode.XXX)` for expected errors. `GlobalExceptionHandler` translates to HTTP responses with i18n support (Chinese default, English via `Accept-Language: en`).
- **Response wrapper**: All API responses use `Result<T>` with `code: 200` for success. Frontend Axios interceptor checks `code === 200`.
- **MyBatis-Plus**: Entities use `@TableName`, `@TableId`, `@TableField` annotations. Mappers extend `BaseMapper<T>`. No XML mappers for simple CRUD.
- **Domain events**: Use `ApplicationEventPublisher` to publish events. Listeners in `domain/handler/` handle cross-cutting concerns (cache invalidation, alert processing).
- **Rate limiting**: `AIRateLimiter` for AI endpoints, `DeviceSyncRateLimiter` for device sync.
- **Spring profiles**: `dev` (default, debug logging, permissive CORS, mock device data), `prod` (strict CORS via `ALLOWED_ORIGINS` env var), `test` (fully permissive).

### Frontend Structure (hhs-frontend-v2/src/)

- **api/** — API modules matching backend controllers 1:1. Use `request` from `@/utils/request.ts`.
- **stores/** — Pinia stores: `auth`, `ai`, `alert`, `floatingAi`, `push`, `realtime`, `theme`, `wellness`.
- **views/** — Page components organized by domain: `dashboard/`, `health/`, `ai/`, `data-input/`, `prevention/`, `screening/`, `realtime/`, `wellness/`, `user/`, `settings/`, `auth/`, `oauth/`.
- **components/** — Shared components: `ai-floating-ball/` (floating AI chat), `device/`, `layout/`, `HealthScoreCircle.vue`.
- **composables/** — Vue composables: `useDraggable`, `useTilt3D`, `useWebGLBall`.
- **types/** — TypeScript types: `api.ts` (all API types), `platform.ts`.
- **utils/** — `request.ts` (Axios wrapper), `storage.ts` (localStorage), `format.ts`.

### Key Frontend Patterns

- **HTTP client**: `@/utils/request.ts` wraps Axios. Auto-attaches JWT Bearer token. Interceptor checks `res.code === 200` for success. 401 triggers `authStore.logout()`.
- **Routing**: `vue-router` with `createWebHistory`. Auth guard in `beforeEach` redirects unauthenticated users to `/login`. Route meta `requiresAdmin` for admin pages.
- **Auto-import**: `unplugin-auto-import` and `unplugin-vue-components` with Element Plus resolver. Vue APIs (`ref`, `computed`, etc.) and Element Plus components are auto-imported.
- **API response type**: All API calls return `Promise<ApiResponse<T>>` where `ApiResponse = { code: number, message: string, data: T }`.
- **AI floating ball**: Global floating AI chat component (`ai-floating-ball/`) with its own Pinia store (`floatingAi`).

### Database

- MySQL 8.0+ with 25 tables defined in `hhs-backend/src/main/resources/sql/schema.sql`.
- Single schema file for initialization. Run: `mysql -u root -p hhs < src/main/resources/sql/schema.sql`.
- MyBatis-Plus handles camelCase to underscore mapping automatically.

### External Integrations

- **AI**: Alibaba Tongyi Qianwen via LangChain4j (model: qwen3.5-flash). Requires `DASH_SCOPE_API_KEY` env var.
- **OCR**: Baidu OCR for health record image parsing. Requires `BAIDU_OCR_API_KEY` and `BAIDU_OCR_SECRET_KEY`.
- **Device sync**: Huawei Health and Xiaomi Health via OAuth2. Requires `DEVICE_ENCRYPTION_KEY` (AES-256) for token storage. Mock data available in dev (`device.mock.enabled: true`).
- **Push channels**: WebSocket (real-time), Email (SMTP), Feishu webhook, WeCom webhook.

### Security

- JWT-based stateless authentication. `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter`.
- JWT secret must be set via `JWT_SECRET` env var (min 32 chars). No default in production.
- CORS: dev allows localhost and LAN IPs; prod requires explicit `ALLOWED_ORIGINS` env var.
- Public endpoints: `/api/auth/**`, `/uploads/**`, `/ws/**`, `/doc.html`, `/actuator/health`, `/actuator/info`.
- WebSocket auth via `WebSocketAuthInterceptor` (token in query param).
- File uploads limited to 10MB. Path traversal protection via `PathValidationUtil`.

### CI/CD

- **Backend CI** (`.github/workflows/backend-ci.yml`): 2-job pipeline. Job 1: `mvn clean verify -DskipITs`. Job 2: integration tests with Testcontainers (needs Docker).
- **Frontend CI** (`.github/workflows/frontend-ci.yml`): Single job: `npm ci` → `npm run build` → `npm run lint:check` → `npm run test:run`.
- Both CI workflows trigger on pushes to any branch that modify their respective directories.

### Testing

- **Backend unit tests**: JUnit 5 + Mockito. Test classes in `src/test/java/com/hhs/service/`. Surefire excludes `*IntegrationTest.java` and `*PerformanceTest.java`.
- **Backend integration tests**: Testcontainers (MySQL). Base class: `AbstractIntegrationTest`. Tests in `src/test/java/com/hhs/integration/`.
- **Backend security tests**: CORS and path traversal tests in `src/test/java/com/hhs/security/`.
- **Frontend unit tests**: Vitest + @vue/test-utils. Tests in `src/__tests__/`.
- **Frontend E2E tests**: Playwright. Specs in `e2e/`. Requires running backend.
- **Code coverage**: JaCoCo with 30% line / 25% branch minimum. Excludes entity, dto, vo, exception, config, component, controller, and mapper classes.

## Code Style

- **Backend**: Java 17, Google Checkstyle (enforced via `maven-checkstyle-plugin`). 4-space indent. Lombok for getters/setters/builders.
- **Frontend**: ESLint + Prettier. 2-space indent. Vue 3 Composition API with `<script setup>`.
- **Line endings**: LF (`.editorconfig`).
- **Charset**: UTF-8 everywhere.

## Environment Variables

Required for startup: `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`.

For AI features: `DASH_SCOPE_API_KEY`.

Optional: `DEVICE_ENCRYPTION_KEY`, `HUAWEI_CLIENT_ID/SECRET`, `XIAOMI_CLIENT_ID/SECRET`, `BAIDU_OCR_API_KEY/SECRET_KEY`, `MAIL_HOST/PORT/USERNAME/PASSWORD`.

Frontend env: `VITE_API_BASE_URL` (default: `http://localhost:8082`), `VITE_WS_BASE_URL` (default: `ws://localhost:8082`).

## API Documentation

Knife4j (Swagger) available at http://localhost:8082/doc.html when backend is running.

## CodeGraph

This project has a CodeGraph MCP server configured. Use `codegraph_*` tools for structural code queries (symbol lookup, call graph, impact analysis). See `.cursor/rules/codegraph.mdc` for usage guide.
