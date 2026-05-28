# Roadmap: Health Hack System — Optimization & Redeployment

**Created:** 2026-05-28
**Granularity:** Coarse (3 phases)
**Status:** All phases completed

---

## Phase 1: Score Persistence Bug Fix ✅ COMPLETE
**Goal:** 修复维度评分持久化 Bug（CR-03）和前端 DeviceSync 问题（WR-02）
**Mode:** mvp

**Requirements:** FIX-01, FIX-02, FIX-03, FIX-04, FIX-05, FIX-06

**Success Criteria (all met):**
1. ✅ ScoreUpdatedEvent 包含四个维度评分字段，所有构造函数已更新
2. ✅ 所有事件发布点传递维度评分
3. ✅ 单元测试验证维度评分正确持久化到 health_score_history
4. ✅ DeviceSync isMockData 基于实际数据源判断
5. ✅ 后端单元测试全部通过 (`mvn clean verify -DskipITs`)

---

## Phase 2: Deployment Pipeline Hardening ✅ COMPLETE
**Goal:** 修复部署管道中的安全、性能和可靠性问题
**Mode:** mvp

**Requirements:** DEPLOY-01, DEPLOY-02, DEPLOY-03, DEPLOY-04, DEPLOY-05

**Success Criteria (all met):**
1. ✅ Docker 镜像构建前 CI 检查通过 (needs: [backend-ci, frontend-ci])
2. ✅ Docker 构建启用层缓存 (GHA cache type=gha,mode=max)
3. ✅ 生产环境后端端口不对外暴露 (ports: [])
4. ✅ 部署健康检查使用重试循环 (scripts/deploy.sh: 5 retries × 15s)
5. ✅ 部署逻辑不重复 — docker-publish.yml 内联脚本，ECS 非 git repo 兼容

---

## Phase 3: Git Push & Deploy ✅ COMPLETE
**Goal:** 推送代码到 GitHub 并验证 CI/CD 部署成功
**Mode:** mvp

**Requirements:** GIT-01, GIT-02, GIT-03, GIT-04

**Success Criteria (all met):**
1. ✅ Git 仓库已连接远程 GitHub 仓库 (Li3379/health-hack-system)
2. ✅ 所有本地变更已提交并推送
3. ✅ GitHub Actions CI/CD 流水线成功触发 (run 26562079956: success)
4. ✅ Docker 镜像成功推送到 GHCR
5. ✅ 阿里云 ECS 部署成功（健康检查通过）
6. ✅ 后端 API 正常响应 (/actuator/health)

---

## Phase Dependency Graph

```
Phase 1 (Bug Fix) ✅ → Phase 2 (Pipeline) ✅ → Phase 3 (Deploy) ✅
```

All phases completed successfully.

---

## Next Steps (Future Work)

- 数据库迁移策略 — 引入 Flyway/Liquibase
- 前端单元测试补充
- SSL/TLS 配置
- 审查并提交 WIP 变更（AI 浮窗重构、心情功能等）

---
*Roadmap created: 2026-05-28 | All phases completed: 2026-05-29*
