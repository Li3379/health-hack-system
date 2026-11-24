# HHS - Health Hack System

健康监控平台：AI 健康分析、健康指标追踪、实时监控、预防性健康评估。
Spring Boot 3 后端 + Vue 3 前端。

[English](#english) | [中文](#中文)

---

<a name="english"></a>
## English

### Overview

- **Health Metric Tracking** — blood glucose, blood pressure, heart rate, lipids, uric acid and more
- **Real-time Monitoring** — WebSocket live metric streaming
- **AI Health Advisor** — Alibaba Tongyi Qianwen via LangChain4j
- **Smart Alerts** — configurable thresholds, deduplication, multi-channel push
- **AI Metric Parsing** — natural-language input turned into structured metrics
- **OCR Data Entry** — Baidu OCR for health report images
- **Preventive Care** — health profile, wellness metrics, risk assessment
- **Screening Comparison** — diff two examination reports

### Technology Stack

**Backend:** Java 17, Spring Boot 3.2.0, Spring Security 6 (JWT), MySQL 8.0 + MyBatis-Plus 3.5.5, Redis 7, LangChain4j 0.35.0

**Frontend:** Vue 3.4, TypeScript 5.5, Vite 5, Element Plus 2.5, Pinia 2.1, ECharts 5.4

### Quick Start

Prerequisites: Java 17+, Node.js 18+, MySQL 8.0+, Redis 7+

```bash
# Backend
cd hhs-backend
cp .env.example .env            # fill in DB / Redis / JWT secrets
mysql -u root -p -e "CREATE DATABASE hhs CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p hhs < src/main/resources/sql/schema.sql
mvn spring-boot:run             # http://localhost:8082  (API docs: /doc.html)

# Frontend
cd ../hhs-frontend-v2
npm install
cp .env.example .env.development
npm run dev                     # http://localhost:5173
```

> Incremental migrations live in `hhs-backend/src/main/resources/sql/migration/`. Apply them in version order on an existing database.

### Environment Variables

**Backend (`hhs-backend/.env`)**

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_PASSWORD` | Yes | MySQL password |
| `REDIS_PASSWORD` | Yes | Redis password |
| `JWT_SECRET` | Yes | JWT signing secret, min 32 chars |
| `DASH_SCOPE_API_KEY` | Recommended | Alibaba AI API key |
| `BAIDU_OCR_API_KEY` / `BAIDU_OCR_SECRET_KEY` | Optional | Baidu OCR |
| `ALLOWED_ORIGINS` | Prod | CORS allowlist |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | Optional | Email push |

**Frontend (`hhs-frontend-v2/.env.development`)**

| Variable | Description |
|----------|-------------|
| `VITE_API_BASE_URL` | Backend URL, default `http://localhost:8082` — no `/api` suffix |
| `VITE_WS_BASE_URL` | WebSocket URL, default `ws://localhost:8082` — no `/ws` suffix |

Generate secrets: `openssl rand -base64 32`

### Development

```bash
cd hhs-backend && mvn test              # backend unit tests
cd hhs-frontend-v2 && npm run test:run  # frontend unit tests
cd hhs-frontend-v2 && npx playwright test   # E2E (backend must be running)
```

### Project Structure

```
health-hack-system/
├── hhs-backend/        # Spring Boot backend (25 tables, 20 controllers, 117 endpoints)
├── hhs-frontend-v2/    # Vue 3 frontend (24 routes, 34 pages)
├── docs/               # project documentation (Chinese)
├── docker-compose.yml  # dev/prod orchestration
└── .github/workflows/  # CI/CD
```

Per-directory guidance lives in each `AGENTS.md`; commands and conventions are in `CLAUDE.md`.

### API Endpoints (sample)

| Endpoint | Description |
|----------|-------------|
| `POST /api/auth/login` | Login |
| `POST /api/auth/register` | Register |
| `GET /api/user/profile` | Health profile |
| `GET /api/metrics` | Health metrics |
| `GET /api/health/score` | Health score |
| `POST /api/ai/chat` | AI health advisor |
| `GET /ws/realtime` | WebSocket real-time stream |

### License

MIT — see [LICENSE](LICENSE).

---

<a name="中文"></a>
## 中文

### 项目概述

- **健康指标追踪** — 血糖、血压、心率、血脂、尿酸等
- **实时监控** — 基于 WebSocket 的实时数据流
- **AI 健康顾问** — 阿里通义千问（LangChain4j）
- **智能预警** — 可配置阈值、去重、多渠道推送
- **AI 智能解析** — 自然语言描述转为结构化指标
- **OCR 数据录入** — 百度 OCR 识别健康报告图片
- **预防保健** — 健康档案、保健指标、风险评估
- **筛查对比** — 两次体检报告差异分析

### 快速开始

前置：Java 17+、Node.js 18+、MySQL 8.0+、Redis 7+

```bash
# 后端
cd hhs-backend
cp .env.example .env            # 填写数据库 / Redis / JWT 密钥
mysql -u root -p -e "CREATE DATABASE hhs CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p hhs < src/main/resources/sql/schema.sql
mvn spring-boot:run             # http://localhost:8082  （接口文档 /doc.html）

# 前端
cd ../hhs-frontend-v2
npm install
cp .env.example .env.development
npm run dev                     # http://localhost:5173
```

> 增量迁移脚本在 `hhs-backend/src/main/resources/sql/migration/`，已有库请按版本号顺序执行。

### 环境变量

后端必需：`DB_PASSWORD`、`REDIS_PASSWORD`、`JWT_SECRET`（至少 32 字符）。
AI 功能：`DASH_SCOPE_API_KEY`。OCR：`BAIDU_OCR_API_KEY` / `BAIDU_OCR_SECRET_KEY`。

生成密钥：`openssl rand -base64 32`

前端 `VITE_API_BASE_URL` / `VITE_WS_BASE_URL` **不要带 `/api`、`/ws` 后缀**——前端会自动拼接。

### 测试

```bash
cd hhs-backend && mvn clean verify -DskipITs   # 后端单元测试
cd hhs-frontend-v2 && npm run test:run         # 前端单测
cd hhs-frontend-v2 && npx playwright test      # E2E（需后端运行）
```

### 许可证

MIT — 详见 [LICENSE](LICENSE)。

---

## Contributing

1. Fork 并创建分支：`git checkout -b feature/my-feature`
2. 提交：`git commit -am 'Add my feature'`
3. 推送并发起 Pull Request

问题与建议请使用 [GitHub Issues](../../issues)。
