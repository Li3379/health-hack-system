# HHS - Health Hack System

A comprehensive health monitoring platform with AI-powered features, real-time tracking, and preventive health assessments.

[English](#english) | [中文](#中文)

---

<a name="english"></a>
## English

### Overview

HHS (Health Hack System) is a modern health monitoring platform built with Spring Boot 3 and Vue 3. It provides:

- **Health Metric Tracking**: Blood glucose, blood pressure, heart rate, and more
- **Real-time Monitoring**: WebSocket-based live metric streaming
- **AI Health Advisor**: Powered by Alibaba Tongyi Qianwen
- **Smart Alerts**: Configurable threshold-based health alerts
- **Device Integration**: Sync with Huawei Health and Xiaomi Health
- **OCR Data Entry**: Baidu OCR for quick health record input

### Technology Stack

**Backend:**
- Java 17, Spring Boot 3.2.0
- Spring Security 6.x with JWT authentication
- MySQL 8.0+ with MyBatis-Plus 3.5.5
- Redis 7.0+ for caching and rate limiting
- LangChain4j 0.35.0 for AI integration

**Frontend:**
- Vue 3.4, TypeScript 5.3, Vite 5
- Element Plus 2.5, Pinia 2.1
- ECharts 5.4 for data visualization

### Quick Start

#### Prerequisites

- Java 17+
- Node.js 18+
- MySQL 8.0+
- Redis 7.0+

#### Backend Setup

```bash
cd hhs-backend

# Copy environment template
cp .env.example .env
# Edit .env with your database credentials

# Create database
mysql -u root -p -e "CREATE DATABASE hhs CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Initialize schema
mysql -u root -p hhs < src/main/resources/sql/schema.sql

# Run backend
mvn spring-boot:run
```

Backend will be available at http://localhost:8082

API Documentation: http://localhost:8082/doc.html

#### Frontend Setup

```bash
cd hhs-frontend-v2

# Install dependencies
npm install

# Copy environment template
cp .env.example .env.development

# Run development server
npm run dev
```

Frontend will be available at http://localhost:5173

### Environment Variables

#### Backend (hhs-backend/.env)

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_PASSWORD` | Yes | MySQL database password |
| `REDIS_PASSWORD` | Yes | Redis password |
| `JWT_SECRET` | Yes | JWT signing secret (min 32 chars) |
| `DASH_SCOPE_API_KEY` | Recommended | Alibaba AI API key |
| `DEVICE_ENCRYPTION_KEY` | Optional | AES-256 key for OAuth tokens |
| `HUAWEI_CLIENT_ID` | Optional | Huawei Health OAuth client ID |
| `HUAWEI_CLIENT_SECRET` | Optional | Huawei Health OAuth secret |
| `XIAOMI_CLIENT_ID` | Optional | Xiaomi Health OAuth client ID |
| `XIAOMI_CLIENT_SECRET` | Optional | Xiaomi Health OAuth secret |
| `BAIDU_OCR_API_KEY` | Optional | Baidu OCR API key |
| `BAIDU_OCR_SECRET_KEY` | Optional | Baidu OCR secret key |

Generate secrets:
```bash
# JWT Secret (256 bits)
openssl rand -base64 32

# Encryption Key (256 bits)
openssl rand -base64 32
```

#### Frontend (hhs-frontend-v2/.env.development)

| Variable | Description |
|----------|-------------|
| `VITE_API_BASE_URL` | Backend API URL (default: http://localhost:8082) |
| `VITE_WS_BASE_URL` | WebSocket URL (default: ws://localhost:8082) |

### Development

```bash
# Backend tests
cd hhs-backend
mvn test

# Frontend tests
cd hhs-frontend-v2
npm run test

# E2E tests (requires running backend)
npm run test:e2e
```

### Project Structure

```
HHS/
├── hhs-backend/           # Spring Boot backend
│   ├── src/main/java/     # Java source code
│   ├── src/main/resources/# Configuration files
│   └── pom.xml            # Maven dependencies
├── hhs-frontend-v2/       # Vue 3 frontend
│   ├── src/               # Vue source code
│   └── package.json       # NPM dependencies
└── README.md              # This file
```

### API Endpoints

| Endpoint | Description |
|----------|-------------|
| `POST /api/auth/login` | User login |
| `POST /api/auth/register` | User registration |
| `GET /api/profile` | Health profile |
| `GET /api/metrics` | Health metrics |
| `POST /api/ai/chat` | AI health advisor |
| `GET /api/health-score` | Health score calculation |
| `GET /ws/realtime` | WebSocket for real-time data |

### License

MIT License - see [LICENSE](LICENSE) for details.

---

<a name="中文"></a>
## 中文

### 项目概述

HHS (Health Hack System) 是一个现代化的健康监测平台，基于 Spring Boot 3 和 Vue 3 构建。主要功能：

- **健康指标追踪**: 血糖、血压、心率等
- **实时监控**: 基于 WebSocket 的实时数据流
- **AI 健康顾问**: 由阿里通义千问驱动
- **智能提醒**: 可配置阈值的健康警报
- **设备集成**: 华为健康、小米健康同步
- **OCR 数据录入**: 百度 OCR 快速录入健康记录

### 快速开始

#### 后端设置

```bash
cd hhs-backend

# 复制环境变量模板
cp .env.example .env
# 编辑 .env 填写数据库凭据

# 创建数据库
mysql -u root -p -e "CREATE DATABASE hhs CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 初始化数据库结构
mysql -u root -p hhs < src/main/resources/sql/schema.sql

# 启动后端
mvn spring-boot:run
```

后端地址: http://localhost:8082
API 文档: http://localhost:8082/doc.html

#### 前端设置

```bash
cd hhs-frontend-v2

# 安装依赖
npm install

# 复制环境变量模板
cp .env.example .env.development

# 启动开发服务器
npm run dev
```

前端地址: http://localhost:5173

### 环境变量

后端必需变量:
- `DB_PASSWORD`: MySQL 数据库密码
- `REDIS_PASSWORD`: Redis 密码
- `JWT_SECRET`: JWT 签名密钥 (至少 32 字符)

生成密钥:
```bash
openssl rand -base64 32
```

### 许可证

MIT 许可证 - 详见 [LICENSE](LICENSE)

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit changes: `git commit -am 'Add my feature'`
4. Push to the branch: `git push origin feature/my-feature`
5. Submit a pull request

## Support

For issues and feature requests, please use the [GitHub Issues](../../issues) page.