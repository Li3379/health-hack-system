# CI/CD 流程

> 本文档描述 HHS 项目的持续集成和持续部署流程。

## 流程概览

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Push to   │────>│   GitHub    │────>│   Build &   │────>│   Deploy    │
│    main     │     │   Actions   │     │    Test     │     │   Server    │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
                          │                    │
                          │                    ▼
                          │            ┌─────────────┐
                          │            │    Docker   │
                          │            │   Registry  │
                          │            └─────────────┘
                          │                    │
                          └────────────────────┘
```

## GitHub Actions 工作流

### 触发条件

```yaml
on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]
```

### 工作流步骤

1. **代码检出**：拉取最新代码
2. **环境设置**：配置 Java、Node.js 环境
3. **后端构建**：Maven 编译、测试
4. **前端构建**：npm 构建
5. **Docker 构建**：构建镜像并推送
6. **部署**：SSH 到服务器执行部署脚本

## 环境配置

### GitHub Secrets

| Secret | 说明 |
|--------|------|
| `SERVER_HOST` | 服务器 IP 地址 |
| `SERVER_USER` | SSH 用户名 |
| `SERVER_SSH_KEY` | SSH 私钥 |
| `DB_PASSWORD` | 数据库密码 |
| `REDIS_PASSWORD` | Redis 密码 |
| `JWT_SECRET` | JWT 签名密钥 |
| `DASH_SCOPE_API_KEY` | 阿里 AI API 密钥 |

### 环境变量

在服务器 `.env` 文件中配置：

```env
# 数据库
DB_PASSWORD=your_secure_password

# Redis
REDIS_PASSWORD=your_redis_password

# JWT
JWT_SECRET=your_256_bit_secret_key_at_least_32_characters

# AI 服务
DASH_SCOPE_API_KEY=sk-your-api-key

# 设备集成（可选）
DEVICE_ENCRYPTION_KEY=your_encryption_key
HUAWEI_CLIENT_ID=your_client_id
HUAWEI_CLIENT_SECRET=your_client_secret
XIAOMI_CLIENT_ID=your_client_id
XIAOMI_CLIENT_SECRET=your_client_secret

# OCR（可选）
BAIDU_OCR_API_KEY=your_api_key
BAIDU_OCR_SECRET_KEY=your_secret_key
```

## 部署脚本

服务器端的部署脚本执行以下操作：

```bash
#!/bin/bash
set -e

# 1. 进入项目目录
cd /opt/hhs

# 2. 拉取最新代码
git pull origin main

# 3. 停止旧容器
docker compose -f docker-compose.yml -f docker-compose.prod.yml down

# 4. 拉取最新镜像
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull

# 5. 构建并启动
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

# 6. 清理旧镜像
docker image prune -f

# 7. 健康检查
sleep 30
curl -f http://localhost:8082/actuator/health || exit 1

echo "Deployment successful!"
```

## 构建优化

### Docker 多阶段构建

**后端 Dockerfile**：

```dockerfile
# 构建阶段
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**前端 Dockerfile**：

```dockerfile
# 构建阶段
FROM node:18-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# 运行阶段
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 缓存策略

- Maven 依赖缓存
- npm node_modules 缓存
- Docker 层缓存

## 测试策略

### 后端测试

```bash
# 单元测试
mvn test

# 集成测试
mvn verify

# 测试覆盖率
mvn jacoco:report
```

### 前端测试

```bash
# 单元测试
npm run test

# E2E 测试
npm run test:e2e
```

## 回滚策略

### 快速回滚

```bash
# 回滚到上一版本
docker compose -f docker-compose.yml -f docker-compose.prod.yml down
docker tag hhs-backend:previous hhs-backend:latest
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 版本标签

每次部署都打上版本标签：

```bash
docker tag hhs-backend:latest hhs-backend:v1.2.3
docker tag hhs-frontend:latest hhs-frontend:v1.2.3
```

## 监控与告警

### 健康检查端点

- Backend: `/actuator/health`
- Frontend: `/` (返回 200)

### 日志收集

```bash
# 查看实时日志
docker compose logs -f --tail=100

# 日志文件位置
/var/lib/docker/containers/[container-id]/[container-id]-json.log
```

### 告警配置

- 容器退出告警
- 健康检查失败告警
- 资源使用率告警

## 安全措施

1. **SSH 密钥认证**：禁用密码登录
2. **防火墙配置**：只开放必要端口
3. **HTTPS**：配置 SSL 证书
4. **密钥管理**：敏感信息存储在 GitHub Secrets
5. **最小权限**：容器非 root 用户运行

## 手动部署

如果需要手动部署：

```bash
# 1. SSH 到服务器
ssh user@server-ip

# 2. 进入项目目录
cd /opt/hhs

# 3. 拉取代码
git pull

# 4. 重新部署
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

## 故障排查

### 部署失败

1. 检查 GitHub Actions 日志
2. 检查服务器磁盘空间
3. 检查 Docker 服务状态
4. 检查环境变量配置

### 服务无法访问

1. 检查容器状态：`docker compose ps`
2. 检查端口占用：`netstat -tlnp`
3. 检查防火墙规则
4. 检查 Nginx 配置