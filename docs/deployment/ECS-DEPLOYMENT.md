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

### 2. 配置 Docker 镜像加速（可选）

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<EOF
{
  "registry-mirrors": [
    "https://your-mirror.mirror.aliyuncs.com"
  ]
}
EOF
sudo systemctl daemon-reload
sudo systemctl restart docker
```

### 3. 安装 Git

```bash
sudo apt install -y git
```

### 4. 配置防火墙

```bash
# 开放必要端口
sudo ufw allow 22/tcp   # SSH
sudo ufw allow 80/tcp   # HTTP
sudo ufw allow 443/tcp  # HTTPS
sudo ufw enable
```

## 项目部署

### 1. 克隆项目

```bash
# 创建目录
sudo mkdir -p /opt/hhs
sudo chown $USER:$USER /opt/hhs

# 克隆代码
cd /opt
git clone https://github.com/your-org/hhs.git hhs
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
# 数据库密码（强密码）
DB_PASSWORD=your_secure_mysql_password

# Redis 密码（强密码）
REDIS_PASSWORD=your_secure_redis_password

# JWT 密钥（至少 32 字符）
JWT_SECRET=your_256_bit_secret_key_at_least_32_characters_long
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
```

### 3. 初始化数据库

首次部署需要初始化数据库：

```bash
# 启动 MySQL
docker compose up -d mysql

# 等待 MySQL 就绪
sleep 30

# 执行初始化脚本
docker compose exec -T mysql mysql -uroot -p${DB_PASSWORD} hhs < hhs-backend/src/main/resources/sql/schema.sql
```

### 4. 启动服务

```bash
# 开发环境
docker compose up -d

# 生产环境
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 5. 验证部署

```bash
# 检查服务状态
docker compose ps

# 检查后端健康
curl http://localhost:8082/actuator/health

# 检查前端
curl http://localhost:80
```

## 域名配置

### 1. 解析域名

在域名服务商配置 A 记录指向 ECS 公网 IP。

### 2. 配置 Nginx（可选）

如果需要 HTTPS 或自定义域名：

```bash
# 安装 Certbot
sudo apt install -y certbot python3-certbot-nginx

# 申请证书
sudo certbot --nginx -d your-domain.com

# 自动续期
sudo systemctl enable certbot.timer
```

### 3. 更新环境变量

```env
ALLOWED_ORIGINS=https://your-domain.com
```

## SSL 证书配置

### 使用 Let's Encrypt

```bash
# 安装 Certbot
sudo apt install -y certbot

# 申请证书
sudo certbot certonly --standalone -d your-domain.com

# 证书位置
# /etc/letsencrypt/live/your-domain.com/fullchain.pem
# /etc/letsencrypt/live/your-domain.com/privkey.pem
```

### 更新 Nginx 配置

在 `hhs-frontend-v2/nginx.conf` 中添加：

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;
    
    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
    
    # ... 其他配置
}

server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}
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

### 重启服务

```bash
# 重启所有服务
docker compose restart

# 重启特定服务
docker compose restart backend
```

### 更新部署

```bash
# 拉取最新代码
git pull

# 重新构建并部署
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

### 数据备份

```bash
# MySQL 备份
docker compose exec mysql mysqldump -uroot -p${DB_PASSWORD} hhs > backup_$(date +%Y%m%d).sql

# 上传文件备份
tar -czf uploads_$(date +%Y%m%d).tar.gz /var/lib/docker/volumes/hhs_uploads_data
```

### 数据恢复

```bash
# 恢复 MySQL
docker compose exec -T mysql mysql -uroot -p${DB_PASSWORD} hhs < backup_20260301.sql
```

## 性能优化

### MySQL 调优

在 `docker-compose.prod.yml` 中添加：

```yaml
mysql:
  command:
    - --innodb-buffer-pool-size=1G
    - --max-connections=200
    - --innodb-log-file-size=256M
```

### JVM 调优

在 `docker-compose.prod.yml` 中添加：

```yaml
backend:
  environment:
    JAVA_OPTS: "-Xms512m -Xmx1024m -XX:+UseG1GC"
```

### Redis 调优

```yaml
redis:
  command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 256mb --maxmemory-policy allkeys-lru
```

## 安全加固

### 1. SSH 安全

```bash
# 编辑 SSH 配置
sudo nano /etc/ssh/sshd_config

# 禁用密码登录
PasswordAuthentication no
PubkeyAuthentication yes

# 重启 SSH
sudo systemctl restart sshd
```

### 2. 定期更新

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 更新 Docker 镜像
docker compose pull
docker compose up -d
```

### 3. 日志轮转

在 `docker-compose.prod.yml` 中配置：

```yaml
services:
  backend:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

## 故障排查

### 服务无法启动

1. 检查端口占用：`sudo netstat -tlnp`
2. 检查磁盘空间：`df -h`
3. 检查内存：`free -m`
4. 查看详细日志：`docker compose logs`

### 数据库连接失败

1. 检查 MySQL 是否运行：`docker compose ps mysql`
2. 检查密码是否正确
3. 检查网络：`docker compose exec backend ping mysql`

### 前端访问空白

1. 检查构建是否成功
2. 检查 Nginx 配置
3. 检查 API 地址配置