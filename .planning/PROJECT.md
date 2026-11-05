# Health Hack System (HHS) — Optimization & Redeployment

## What This Is

HHS 是一个健康管理平台，集成 AI 智能分析、实时健康追踪、预防性健康评估和设备数据同步功能。采用 Spring Boot 3.2 后端 + Vue 3 前端的单体架构，已部署在阿里云 ECS 服务器上，通过宝塔面板管理环境，GitHub Actions CI/CD 自动构建和部署。

## Core Value

平台核心价值是提供可靠、准确的健康评分和趋势分析 — 评分数据必须完整持久化（包括维度评分），历史趋势图必须正确展示，部署过程必须稳定可靠。

## Current Milestone: v1.2 全站健康与保健指标录入标准化与体验对齐 (Shipped: 2026-11-05)

**Goal:** 全站四大入口（快捷录入、健康管理、预防保健、生活方式仪表盘）指标录入组件通用封装与收敛，消除重复代码，实现四梯度宽容生理校验、血压协同对调与编辑模式自适应流转的一致性闭环。

## Requirements

### Validated

- ✓ JWT 认证与授权 — 已实现，生产环境通过环境变量管理密钥
- ✓ 健康指标采集与存储 — 已实现，支持手动输入、OCR、AI 解析和设备同步
- ✓ 健康评分计算 — 已实现，支持多维度评分（心血管、代谢、体重、生活方式）
- ✓ 维度评分持久化 (CR-03) — ScoreUpdatedEvent 扩展四维评分字段，正确持久化到 health_score_history
- ✓ DeviceSync isMockData (WR-02) — 基于实际数据源判断
- ✓ 智能告警系统 — 已实现，支持多通道推送（WebSocket、邮件、飞书、企业微信）
- ✓ 实时监控 — 已实现，WebSocket 推送实时指标
- ✓ 数据导出 — 已实现，支持 CSV/Excel 导出
- ✓ 统计分析仪表板 — 已实现
- ✓ Docker 容器化部署 — 已实现，四服务架构（MySQL、Redis、后端、前端）
- ✓ GitHub Actions CI/CD — 已实现，自动构建推送 Docker 镜像并 SSH 部署
- ✓ CI 检查作为 Docker 构建前置条件 — docker-publish.yml needs backend-ci + frontend-ci
- ✓ Docker 层缓存 — GHA cache type=gha,mode=max
- ✓ 生产环境后端端口不对外暴露 — docker-compose.prod.yml ports: []
- ✓ 健康检查重试循环 — scripts/deploy.sh 5 次重试，每次 15 秒
- ✓ 部署逻辑不重复 — docker-publish.yml 内联脚本，ECS 非 git repo 兼容
- ✓ 代码推送并触发 CI/CD 重新部署 — 已推送，Build & Deploy 成功 (run 26562079956)
- ✓ **INPUT-GUIDE**: 指标录入前生理范围、推荐步长与精度动态绑定，输入框占位符显式提示 — v1.1/v1.2
- ✓ **FRIENDLY-FEEDBACK**: 四梯度宽容校验与非破坏性保留（失焦绝不清空），危急值防误触二次确认 — v1.1/v1.2
- ✓ **UNIFIED-METRIC-META**: 后端不可变 HealthMetricMetaRegistry 与前端 useMetricMetaStore 权威元数据字典 (23 项) — v1.1/v1.2
- ✓ **KEY-NORMALIZATION**: MetricKeyNormalizer 双向兼容 camelCase/snake_case/大写与医学别名，全站防英文 key 外露 — v1.1/v1.2
- ✓ **REUSABLE-INPUT-DIALOG**: UniversalMetricDialog 通用弹窗封装，全站消除 1,750+ 行重复代码，支持自适应 API 路由 — v1.2
- ✓ 前端单元测试体系扩充 — 覆盖率提升至 13 个测试套件、90 项自动化测试用例，生产构建零报错 — v1.2

### Active (Next Milestone Candidates - v1.3)

- [ ] **AI-REPORT-DEEP-ALIGN**: AI 对话解析与 OCR 化验报告识别深度对齐到全量权威元数据注册表
- [ ] **BASELINE-PERSONALIZATION**: 针对特定病史用户的个性化生理基线动态调整与偏离提醒
- [ ] **CONTEXT-SMART-INPUT**: 基于时间段与生活场景的智能快捷录入指标推荐
- [ ] 数据库迁移策略：建立 Flyway/Liquibase schema 版本管理机制

### WIP (Unstaged Changes)

- [ ] AI 浮窗重构 — FloatingBubble + FloatingChatPanel 大幅改动
- [ ] AI Chat.vue 功能扩展 — +1407 行
- [ ] Mood API 扩展 — 后端 MoodController/MoodService + 前端 mood.ts
- [ ] Auth 页面优化 — Login.vue, Register.vue

### Out of Scope

- SSL/TLS 配置 — 需要 HTTPS 证书，后续通过宝塔面板配置或反向代理处理
- 零停机部署 — 当前 docker compose 重启导致的短暂停机可接受
- 小米健康 API 真实对接 — 当前为 stub 实现

## Context

### 技术栈

- **后端**: Spring Boot 3.2.0, Java 17, MyBatis-Plus 3.5.5, LangChain4j 0.35.0
- **前端**: Vue 3.4.15, TypeScript 5.5, Vite 5, Element Plus 2.5.6, ECharts
- **数据库**: MySQL 8.0, Redis 7
- **部署**: Docker Compose, GitHub Actions → GHCR → SSH 阿里云 ECS
- **环境管理**: 宝塔面板全权管理

### 当前状态

- 所有 3 个阶段已完成，CI/CD 流水线稳定运行
- 最新 Build & Deploy run 26562079956 成功
- 后端 489 测试全部通过
- 有 15 个未暂存的 WIP 变更（AI 浮窗重构、心情功能等）
- 数据库 schema 版本 v3.8.0，35 表

### 部署架构

| 服务 | 端口 | 说明 |
|------|------|------|
| frontend (nginx) | 80 | 前端静态文件 + 反向代理 /api → backend:8082 |
| backend | 8082 (内部) | Spring Boot API，生产环境不对外暴露 |
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
| 维度评分通过扩展 ScoreUpdatedEvent 传递 | 最小化变更，复用现有事件机制 | Done — 四维评分正确持久化 |
| 部署管道修复内联在 GitHub Actions 中 | 不引入额外部署工具，保持与宝塔面板兼容 | Done — docker-publish.yml 内联脚本 |
| ECS 非 git repo 条件 git pull | 部署路径可能不是 git 仓库 | Done — if [ -d .git ] 保护 |
| npm install 替代 npm ci | 跨平台 lockfile 差异 (npm 10 vs npm 11) | Done — CI 使用 npm install |
| 数据库迁移暂用手动方式 | 后续引入 Flyway/Liquibase 需要更多评估 | Deferred |
| 删除 vite.config.js 保留 .ts | 避免重复配置文件 | Done — .ts 版本使用 fileURLToPath |

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
*Last updated: 2026-05-29 — all 3 phases completed, CI/CD green*
