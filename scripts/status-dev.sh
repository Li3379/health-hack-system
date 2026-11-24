#!/usr/bin/env bash
# 一键排查 HHS 本地开发环境是否在跑，以及各端口背后到底是哪个进程/项目。
#
# 为什么需要这个脚本：
#   - Windows 上 netstat 只能给出 PID，给不出"这是哪个项目"。而本机同时跑着
#     OAISS，vite 端口会在 5173/5174 之间漂移，只看端口号必然误判。
#   - Docker 发布端口会出现两个 PID（com.docker.backend + wslrelay），
#     不能拿 PID 反查应用名。
#   - 后端探活要用 /actuator/health，根路径返回 401 是正常的（需鉴权）。
#
# Usage: bash scripts/status-dev.sh

set -uo pipefail

BACKEND_PORT=${BACKEND_PORT:-8082}
MYSQL_PORT=${MYSQL_PORT:-3307}
REDIS_PORT=${REDIS_PORT:-6380}

# 端口 -> PID 列表（去重、忽略 TIME_WAIT 等非 LISTENING 状态）
pids_on() {
    netstat -ano 2>/dev/null | grep LISTENING | grep -E "[:.]$1 " | awk '{print $NF}' | sort -u
}

# PID -> "进程名 | 命令行前 120 字"
describe_pid() {
    local pid=$1
    powershell -NoProfile -Command \
        "Get-CimInstance Win32_Process -Filter 'ProcessId=$pid' | ForEach-Object { \$_.Name + ' | ' + \$_.CommandLine.Substring(0,[Math]::Min(120,\$_.CommandLine.Length)) }" \
        2>/dev/null | tr -d '\r'
}

# 从命令行推断归属项目，避免把 OAISS 认成 HHS
project_of() {
    case "$1" in
        *health-hack-system*) echo "HHS" ;;
        *OAISS*|*oaiss*) echo "OAISS" ;;
        *com.docker.backend*|*wslrelay*) echo "docker" ;;
        *) echo "未知" ;;
    esac
}

echo "=============================================="
echo " HHS 开发环境状态  $(date '+%Y-%m-%d %H:%M:%S')"
echo "=============================================="

echo
echo "[容器]"
if [ "$(docker ps -q --filter 'name=health-hack-system' 2>/dev/null | wc -l)" -eq 0 ]; then
    echo "  ✗ 未运行  ->  docker compose up -d mysql redis"
else
    docker ps --filter "name=health-hack-system" \
        --format "  ✓ {{.Names}}  {{.Status}}  {{.Ports}}" 2>/dev/null
fi

echo
echo "[端口归属]"
for port in "$BACKEND_PORT" "$MYSQL_PORT" "$REDIS_PORT" 5173 5174 5175; do
    pids=$(pids_on "$port")
    if [ -z "$pids" ]; then
        echo "  $port  空闲"
        continue
    fi
    while read -r pid; do
        [ -z "$pid" ] && continue
        [ "$pid" = "0" ] && continue
        info=$(describe_pid "$pid")
        echo "  $port  PID $pid  [$(project_of "$info")]  $info"
    done <<< "$pids"
done

echo
echo "[前端]"
found_frontend=0
for port in 5173 5174 5175; do
    title=$(curl -s -m 2 "http://localhost:$port/" 2>/dev/null | grep -o '<title>[^<]*</title>' | sed 's/<[^>]*>//g')
    if [ -n "$title" ]; then
        found_frontend=1
        echo "  $port  HTTP 200  $title"
    fi
done
[ "$found_frontend" -eq 0 ] && echo "  ✗ 无前端在跑  ->  cd hhs-frontend-v2 && npm run dev"

echo
echo "[后端]"
health=$(curl -s -m 3 "http://localhost:$BACKEND_PORT/actuator/health" 2>/dev/null)
if [ -n "$health" ]; then
    echo "  ✓ :$BACKEND_PORT  $health"
    echo "     API 文档  http://localhost:$BACKEND_PORT/doc.html"
else
    echo "  ✗ :$BACKEND_PORT 无响应  ->  bash scripts/start-backend-dev.sh"
fi

echo
echo "提示：前端端口会漂移，认标题不认端口。HHS 标题为「HHS - 健康管理系统」。"
