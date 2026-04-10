# ECS 部署指南

> 本文档描述 HHS 项目在阿里云 ECS 上的部署流程。

## 服务器要求

| 配置项 | 最低要求 | 推荐配置 |
|--------|----------|----------|
| CPU | 2 核 | 4 核 |
| 内存 | 4 GB | 8 GB |
| 磁盘 | 40 GB SSD | 100 GB SSD |
| 带宽 | 1 Mbps | 5 Mbps |
| 操作系统 | Ubuntu 22.04 / CentOS 8 | Ubuntu 22.04 |

## 环境准备

### 1. 安装 Docker

```bash
# Ubuntu
sudo apt update
sudo apt install -y docker.io docker-compose-plugin

# 启动 Docker
sudo systemctl enable docker
sudo systemctl start docker

# 添加当前用户到 docker 组
sudo usermod -aG docker $USER
newgrp docker
```

### 2. 安装 Git

```bash
sudo apt install -y git
```

### 3. 配置防火墙

```bash
# 开放必要端口
sudo ufw allow 22/tcp   # SSH
sudo ufw allow 80/tcp   # HTTP
sudo ufw allow 443/tcp  # HTTPS
sudo ufw enable
```

## 自动部署（CI/CD）

项目通过 GitHub Actions 自动构建镜像并部署到 ECS。

### 前提条件

1. GitHub 仓库配置了以下 Secrets：
   - `ECS_HOST` - 服务器 IP 地址
   - `ECS_USER` - SSH 用户名
   - `ECS_SSH_KEY` - SSH 私钥
   - `ECS_DEPLOY_PATH` - 项目路径（如 `/root/hhs`）
   - `GHCR_TOKEN` - GitHub PAT（read:packages 权限）
   - `VITE_API_BASE_URL` - 前端 API 地址（不含 `/api` 后缀）
   - `VITE_WS_BASE_URL` - WebSocket 地址（不含 `/ws` 后缀）

2. 服务器已安装 Docker 和 Docker Compose
3. 服务器已克隆项目代码

### 部署流程

```
Push to master → GitHub Actions 构建 Docker 镜像
→ 推送到 GHCR (ghcr.io)
→ SSH 到 ECS 拉取镜像
→ docker compose up -d --force-recreate
→ 健康检查
```

## 手动部署

### 1. 克隆项目

```bash
# 创建目录
sudo mkdir -p /root/hhs
sudo chown $USER:$USER /root/hhs

# 克隆代码
cd /root
git clone https://github.com/<owner>/hhs.git hhs
cd hhs
```

### 2. 配置环境变量

```bash
# 创建 .env 文件
cp .env.example .env
nano .env
```

**必须配置的变量**：

```env
# Spring 配置
SPRING_PROFILES_ACTIVE=prod

# 数据库密码（强密码）
DB_PASSWORD=your_secure_mysql_password

# Redis 密码（强密码）
REDIS_PASSWORD=your_secure_redis_password

# JWT 密钥（至少 32 字符）
JWT_SECRET=your_256_bit_secret_key_at_least_32_characters_long

# 服务器地址（不含路径后缀）
BASE_URL=http://your-server-ip
ALLOWED_ORIGINS=http://your-server-ip
```

**可选配置**：

```env
# AI 服务
DASH_SCOPE_API_KEY=sk-your-dashscope-api-key

# 设备同步加密密钥
DEVICE_ENCRYPTION_KEY=your_32_char_encryption_key

# 华为健康 OAuth
HUAWEI_CLIENT_ID=your_huawei_client_id
HUAWEI_CLIENT_SECRET=your_huawei_client_secret

# 小米健康 OAuth
XIAOMI_CLIENT_ID=your_xiaomi_client_id
XIAOMI_CLIENT_SECRET=your_xiaomi_client_secret

# 百度 OCR
BAIDU_OCR_API_KEY=your_baidu_api_key
BAIDU_OCR_SECRET_KEY=your_baidu_secret_key

# 邮件推送（可选）
MAIL_HOST=smtp.example.com
MAIL_PORT=465
MAIL_USERNAME=your_email@example.com
MAIL_PASSWORD=your_email_authorization_code
```

### 3. 初始化数据库

首次部署需要初始化数据库：

```bash
# 启动 MySQL 容器
docker compose up -d mysql

# 等待 MySQL 就绪
sleep 30

# 执行初始化脚本
docker compose exec -T mysql mysql -uroot -p${DB_PASSWORD} hhs \
  < hhs-backend/src/main/resources/sql/schema.sql
```

### 4. 启动服务

```bash
# 生产环境部署
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 5. 验证部署

```bash
# 检查服务状态
docker compose ps

# 检查后端健康
curl http://localhost/8082/actuator/health

# 检查前端（通过 nginx 代理）
curl http://localhost/80
```

## 域名配置

### 1. 解析域名

在域名服务商配置 A 记录指向 ECS 公网 IP。

### 2. 配置 HTTPS

```bash
# 安装 Certbot
sudo apt install -y certbot

# 申请证书（standalone 模式）
sudo certbot certonly --standalone -d your-domain.com

# 证书位置
# /etc/letsencrypt/live/your-domain.com/fullchain.pem
# /etc/letsencrypt/live/your-domain.com/privkey.pem
```

### 3. 更新环境变量

```env
BASE_URL=https://your-domain.com
ALLOWED_ORIGINS=https://your-domain.com
```

## 维护操作

### 查看日志

```bash
# 所有服务日志
docker compose logs -f

# 特定服务日志
docker compose logs -f backend
docker compose logs -f frontend
```

### 更新部署

```bash
# 拉取最新代码
cd /root/hhs && git pull origin master

# 拉取最新镜像并重启
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --force-recreate

# 清理旧镜像
docker image prune -f
```

### 数据备份

```bash
# MySQL 备份
docker compose exec mysql mysqldump -uroot -p${DB_PASSWORD} hhs > backup_$(date +%Y%m%d).sql

# 恢复
docker compose exec -T mysql mysql -uroot -p${DB_PASSWORD} hhs < backup_20260301.sql
```

### 性能调优

在 `docker-compose.prod.yml` 跻加或修改：

```yaml
# MySQL 调优
mysql:
  command:
    - --innodb-buffer-pool-size=1G
    - --max-connections=200

# JVM 调优
backend:
  environment:
    JAVA_OPTS: "-Xms512m -Xmx1024m -XX:+UseG1GC"

# Redis 调优
redis:
  command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 256mb --maxmemory-policy allkeys-lru
```

## 安全加固

### 1. SSH 安全

```bash
# 禁用密码登录
sudo nano /etc/ssh/sshd_config
# 设置: PasswordAuthentication no, PubkeyAuthentication yes
sudo systemctl restart sshd
```

### 2. 定期更新

```bash
sudo apt update && sudo apt upgrade -y
docker compose pull && docker compose up -d
```

## 故障排查

### 服务无法启动

1. 检查端口占用：`sudo netstat -tlnp`
2. 检查磁盘空间：`df -h`
3. 检查内存：`free -m`
4. 查看日志：`docker compose logs`

### 数据库连接失败

1. 检查 MySQL 状态：`docker compose ps mysql`
2. 检查密码配置
3. 检查网络：`docker compose exec backend ping mysql`

### 前端访问空白

1. 检查构建日志：`docker compose logs frontend`
2. 检查 API 地址配置（VITE_API_BASE_URL 不含 `/api` 后缀）
3. 检查 Nginx 配置