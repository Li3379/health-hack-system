# Docker 配置

> 本文档描述 HHS 项目的 Docker 部署配置。

## 服务架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         Docker Network                           │
│                        (hhs-network)                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────┐ │
│  │   frontend  │  │   backend   │  │    mysql    │  │  redis  │ │
│  │   (Nginx)   │  │(Spring Boot)│  │   (MySQL 8) │  │(Redis 7)│ │
│  │   Port: 80  │  │ Port: 8082  │  │Port: 3306   │  │Port:6379│ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────┘ │
│         │               │                │               │       │
│         └───────────────┴────────────────┴───────────────┘       │
│                          hhs-network                              │
└─────────────────────────────────────────────────────────────────┘
```

## 服务配置

### Frontend (Nginx)

```yaml
frontend:
  build:
    context: ./hhs-frontend-v2
    dockerfile: Dockerfile
  ports:
    - "80:80"
  depends_on:
    backend:
      condition: service_healthy
  networks:
    - hhs-network
  restart: unless-stopped
```

**功能**：
- 提供 Vue.js SPA 静态文件服务
- 反向代理 `/api` 到后端
- WebSocket 代理 `/ws` 到后端

### Backend (Spring Boot)

```yaml
backend:
  build:
    context: ./hhs-backend
    dockerfile: Dockerfile
  ports:
    - "8082:8082"
  environment:
    SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}
    SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/hhs?...
    SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
    SPRING_DATA_REDIS_HOST: redis
    SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
    JWT_SECRET: ${JWT_SECRET}
    DASH_SCOPE_API_KEY: ${DASH_SCOPE_API_KEY}
  depends_on:
    mysql:
      condition: service_healthy
    redis:
      condition: service_healthy
  volumes:
    - uploads_data:/app/uploads
  networks:
    - hhs-network
  restart: unless-stopped
```

**环境变量**：

| 变量 | 说明 | 必需 |
|------|------|------|
| SPRING_PROFILES_ACTIVE | Spring 配置环境 | 否 |
| DB_PASSWORD | MySQL root 密码 | 是 |
| REDIS_PASSWORD | Redis 密码 | 是 |
| JWT_SECRET | JWT 签名密钥 | 是 |
| DASH_SCOPE_API_KEY | 阿里 AI API 密钥 | 否 |
| DEVICE_ENCRYPTION_KEY | 设备 Token 加密密钥 | 否 |

### MySQL

```yaml
mysql:
  image: mysql:8.0
  environment:
    MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
    MYSQL_DATABASE: hhs
  volumes:
    - mysql_data:/var/lib/mysql
  networks:
    - hhs-network
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 30s
  restart: unless-stopped
```

**持久化**：`mysql_data` 卷存储数据文件

### Redis

```yaml
redis:
  image: redis:7-alpine
  command: redis-server --requirepass ${REDIS_PASSWORD}
  volumes:
    - redis_data:/data
  networks:
    - hhs-network
  healthcheck:
    test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 10s
  restart: unless-stopped
```

**持久化**：`redis_data` 卷存储 RDB/AOF 文件

## 网络配置

```yaml
networks:
  hhs-network:
    driver: bridge
```

所有服务在同一个 bridge 网络中，通过服务名互相访问：

- Frontend → Backend: `http://backend:8082`
- Backend → MySQL: `mysql:3306`
- Backend → Redis: `redis:6379`

## 数据卷

```yaml
volumes:
  mysql_data:
    driver: local
  redis_data:
    driver: local
  uploads_data:
    driver: local
```

| 卷名 | 用途 |
|------|------|
| mysql_data | MySQL 数据文件 |
| redis_data | Redis 持久化文件 |
| uploads_data | 用户上传文件 |

## 部署命令

### 开发环境

```bash
# 创建 .env 文件
cp .env.example .env
# 编辑 .env 填写凭据

# 启动服务（自动加载 docker-compose.override.yml）
docker compose up -d

# 查看日志
docker compose logs -f

# 停止服务
docker compose down
```

### 生产环境

```bash
# 使用生产配置启动
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# 查看状态
docker compose ps

# 重启服务
docker compose restart

# 停止并删除
docker compose -f docker-compose.yml -f docker-compose.prod.yml down
```

### 生产配置差异

`docker-compose.prod.yml` 主要差异：

1. **资源限制**：设置 CPU 和内存限制
2. **日志配置**：日志驱动和轮转策略
3. **安全加固**：只读文件系统、非 root 用户
4. **健康检查**：更严格的健康检查间隔

## 健康检查

各服务都配置了健康检查：

| 服务 | 检查方式 | 间隔 | 超时 | 重试 |
|------|----------|------|------|------|
| Backend | HTTP /actuator/health | 30s | 10s | 3 |
| MySQL | mysqladmin ping | 30s | 10s | 3 |
| Redis | redis-cli ping | 30s | 10s | 3 |

## Nginx 配置

前端容器内置 Nginx 配置：

```nginx
server {
    listen 80;
    
    # 静态文件
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }
    
    # API 代理
    location /api {
        proxy_pass http://backend:8082;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    # WebSocket 代理
    location /ws {
        proxy_pass http://backend:8082;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
    
    # 上传文件
    location /uploads {
        alias /app/uploads;
    }
}
```

## 故障排查

### 查看容器日志

```bash
# 所有服务
docker compose logs -f

# 特定服务
docker compose logs -f backend
```

### 进入容器

```bash
# 进入 backend 容器
docker compose exec backend sh

# 进入 mysql 容器
docker compose exec mysql mysql -uroot -p
```

### 检查网络

```bash
# 列出网络
docker network ls

# 检查网络详情
docker network inspect hhs-network
```

### 数据备份

```bash
# MySQL 备份
docker compose exec mysql mysqldump -uroot -p hhs > backup.sql

# 恢复
docker compose exec -T mysql mysql -uroot -p hhs < backup.sql
```

## 常用命令

```bash
# 构建镜像
docker compose build

# 重新创建容器
docker compose up -d --force-recreate

# 查看资源使用
docker stats

# 清理未使用资源
docker system prune
```