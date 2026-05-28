# Health Hack System (HHS) — Optimization & Redeployment

## What This Is

HHS 是一个健康管理平台，集成 AI 智能分析、实时健康追踪、预防性健康评估和设备数据同步功能。采用 Spring Boot 3.2 后端 + Vue 3 前端的单体架构，已部署在阿里云 ECS 服务器上，通过宝塔面板管理环境，GitHub Actions CI/CD 自动构建和部署。

## Core Value

平台核心价值是提供可靠、准确的健康评分和趋势分析 — 评分数据必须完整持久化（包括维度评分），历史趋势图必须正确展示，部署过程必须稳定可靠。

## Requirements

### Validated

- ✓ JWT 认证与授权 — 已实现，生产环境通过环境变量管理密钥
- ✓ 健康指标采集与存储 — 已实现，支持手动输入、OCR、AI 解析和设备同步
- ✓ 健康评分计算 — 已实现，支持多维度评分（心血管、代谢、体重、生活方式）
- ✓ 智能告警系统 — 已实现，支持多通道推送（WebSocket、邮件、飞书、企业微信）
- ✓ 实时监控 — 已实现，WebSocket 推送实时指标
- ✓ 数据导出 — 已实现，支持 CSV/Excel 导出
- ✓ 统计分析仪表板 — 已实现
- ✓ Docker 容器化部署 — 已实现，四服务架构（MySQL、Redis、后端、前端）
- ✓ GitHub Actions CI/CD — 已实现，自动构建推送 Docker 镜像并 SSH 部署

### Active

- [ ] CR-03: 维度评分持久化到 health_score_history（心血管、代谢、体重、生活方式维度）
- [ ] 部署管道修复：消除重复部署逻辑（docker-publish.yml 和 deploy.yml）
- [ ] 部署管道修复：CI 检查作为 Docker 构建的前置条件
- [ ] 部署管道修复：移除 no-cache: true，启用 Docker 层缓存加速构建
- [ ] 部署管道修复：生产环境不暴露后端 8082 端口
- [ ] 部署管道修复：健康检查改用重试循环替代硬编码 sleep 60
- [ ] WR-02: DeviceSync isMockData 应检查实际数据源而非硬编码 false
- [ ] 数据库迁移策略：建立 schema 版本管理机制
- [ ] 代码推送并触发 CI/CD 重新部署

### Out of Scope

- SSL/TLS 配置 — 需要 HTTPS 证书，后续通过宝塔面板配置或反向代理处理
- 零停机部署 — 当前 docker compose 重启导致的短暂停机可接受
- 前端单元测试补充 — 仅有 1 个单元测试，但非本次优先级
- 小米健康 API 真实对接 — 当前为 stub 实现

## Context

### 技术栈

- **后端**: Spring Boot 3.2.0, Java 17, MyBatis-Plus 3.5.5, LangChain4j 0.35.0
- **前端**: Vue 3.4.15, TypeScript 5.5, Vite 5, Element Plus 2.5.6, ECharts
- **数据库**: MySQL 8.0, Redis 7
- **部署**: Docker Compose, GitHub Actions → GHCR → SSH 阿里云 ECS
- **环境管理**: 宝塔面板全权管理

### 当前状态

- 已完成二次优化（数据导出、统计分析、评分历史），但代码未推送
- REVIEW.md 发现 13 个问题，12/13 已修复
- CR-03（维度评分持久化）是唯一未修复的严重问题
- 数据库 schema 版本 v3.8.0，25 表 + 3 视图 + 2 存储过程
- 后端测试覆盖良好（47 个测试文件），前端测试薄弱（仅 1 个单元测试）

### 部署架构

| 服务 | 端口 | 说明 |
|------|------|------|
| frontend (nginx) | 80 | 前端静态文件 + 反向代理 |
| backend | 8082 | Spring Boot API |
| mysql | 3306 (内部) | 数据持久化 |
| redis | 6379 (内部) | 缓存 + 会话 |

### GitHub 远程仓库

`https://github.com/Li3379/health-hack-system`

## Constraints

- **部署环境**: 阿里云 ECS + 宝塔面板管理，不可更改基础设施
- **CI/CD**: GitHub Actions → GHCR → SSH 部署，必须保持此流程
- **技术栈**: 不可更换主要框架（Spring Boot / Vue 3）
- **零停机**: 当前阶段不需要，短暂停机可接受
- **安全性**: 生产环境密钥必须通过环境变量管理，已实现

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 维度评分通过扩展 ScoreUpdatedEvent 传递 | 最小化变更，复用现有事件机制 | — Pending |
| 部署管道修复内联在 GitHub Actions 中 | 不引入额外部署工具，保持与宝塔面板兼容 | — Pending |
| 数据库迁移暂用手动方式 | 后续引入 Flyway/Liquibase 需要更多评估 | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-28 after initialization*
