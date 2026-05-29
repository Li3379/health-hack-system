<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# health-hack-system

## Purpose
HHS (Health Hack System) — 健康监控平台，集成 AI 驱动的健康分析、实时数据追踪、预防性健康评估。Spring Boot 后端 + Vue 3 前端的 monorepo 架构，MySQL 8.0+ 存储，Redis 缓存。

## Key Files
| File | Description |
|------|-------------|
| `CLAUDE.md` | Claude Code 项目指引（技术栈、命令、约定） |
| `docker-compose.yml` | 开发环境 Docker 编排 (MySQL 3307, Redis 6380, backend 8082, frontend 80) |
| `docker-compose.prod.yml` | 生产环境覆盖配置 |
| `docker-compose.override.yml` | 本地开发覆盖配置 |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `hhs-backend/` | Spring Boot 3.2 后端 (Java 17, MyBatis-Plus, JWT, LangChain4j) — [see AGENTS.md](hhs-backend/AGENTS.md) |
| `hhs-frontend-v2/` | Vue 3 前端 (TypeScript, Vite, Element Plus, Pinia, ECharts) — [see AGENTS.md](hhs-frontend-v2/AGENTS.md) |
| `docs/` | 项目文档 (API, 架构, 部署, 安全) — [see AGENTS.md](docs/AGENTS.md) |
| `.github/workflows/` | CI/CD 流水线: backend-ci, frontend-ci, docker-publish, deploy |
| `scripts/` | 部署和运维脚本 |

## For AI Agents

### Working In This Directory
- Monorepo 结构：后端和前端是独立 Maven/npm 模块
- 修改代码前确认目标模块（`hhs-backend/` vs `hhs-frontend-v2/`）
- 遵循 `CLAUDE.md` 中的完整约定
- 后端 Google Checkstyle (4-space)，前端 ESLint + Prettier (2-space)

### Testing Requirements
- 后端: `cd hhs-backend && mvn clean verify -DskipITs`
- 前端: `cd hhs-frontend-v2 && npm run test:run`
- E2E: `cd hhs-frontend-v2 && npx playwright test` (需后端运行)

### Common Patterns
- API 响应: `Result<T>` (后端) ↔ `ApiResponse<T>` (前端)，`code: 200` 表示成功
- 认证: JWT Bearer token，无状态，401 自动登出
- 事件驱动: Spring `ApplicationEventPublisher` 发布领域事件
- 服务层: `FooService` 接口 + `FooServiceImpl` 实现

## Dependencies

### External
- **Backend**: Spring Boot 3.2, Java 17, MyBatis-Plus, LangChain4j, Spring Security
- **Frontend**: Vue 3, TypeScript 5.x, Vite, Element Plus, Pinia, ECharts, Axios
- **Infra**: MySQL 8.0+, Redis
- **AI/OCR**: Alibaba Tongyi Qianwen (qwen3.5-flash), Baidu OCR
- **Device**: Huawei Health SDK, Xiaomi Health SDK

<!-- MANUAL: -->
