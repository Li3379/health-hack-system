# Roadmap: Health Hack System — Optimization & Redeployment

**Created:** 2026-05-28
**Granularity:** Coarse (3 phases)

---

## Phase 1: Score Persistence Bug Fix
**Goal:** 修复维度评分持久化 Bug（CR-03）和前端 DeviceSync 问题（WR-02）
**Mode:** mvp

**Requirements:** FIX-01, FIX-02, FIX-03, FIX-04, FIX-05, FIX-06

**Success Criteria:**
1. ScoreUpdatedEvent 包含四个维度评分字段，所有构造函数已更新
2. 所有事件发布点传递维度评分
3. 单元测试验证维度评分正确持久化到 health_score_history
4. DeviceSync isMockData 基于实际数据源判断
5. 后端单元测试全部通过 (`mvn clean verify -DskipITs`)

**Tasks:**
1. 扩展 ScoreUpdatedEvent 添加 cardiovascularScore, metabolicScore, weightScore, lifestyleScore 字段
2. 添加新的构造函数或在现有构造函数中支持维度评分
3. 修改 HealthScoreServiceImpl 发布事件时传递维度评分
4. 修改 ScoreUpdatedEventListener 构建历史记录时设置维度评分
5. 更新 ScoreHistoryServiceImplTest 验证维度评分持久化
6. 修复 DeviceSync.vue isMockData 计算属性
7. 运行后端测试验证

---

## Phase 2: Deployment Pipeline Hardening
**Goal:** 修复部署管道中的安全、性能和可靠性问题
**Mode:** mvp

**Requirements:** DEPLOY-01, DEPLOY-02, DEPLOY-03, DEPLOY-04, DEPLOY-05

**Success Criteria:**
1. Docker 镜像构建前 CI 检查通过
2. Docker 构建启用层缓存，构建时间显著减少
3. 生产环境后端端口不对外暴露
4. 部署健康检查使用重试循环
5. 部署逻辑不重复（docker-publish.yml 和 deploy.yml 共享脚本）

**Tasks:**
1. 创建 scripts/deploy.sh 提取共享部署逻辑
2. 修改 docker-publish.yml deploy job 引用共享脚本
3. 修改 deploy.yml 引用共享脚本
4. docker-publish.yml 添加 CI 前置条件（needs backend-ci + frontend-ci）
5. 移除两个构建 job 的 no-cache: true
6. docker-compose.prod.yml 移除后端 8082 端口映射
7. deploy.sh 中的健康检查改用重试循环（5 次，每次 15 秒）
8. 本地 Docker Compose 构建测试

---

## Phase 3: Git Push & Deploy
**Goal:** 推送代码到 GitHub 并验证 CI/CD 部署成功
**Mode:** mvp

**Requirements:** GIT-01, GIT-02, GIT-03, GIT-04

**Success Criteria:**
1. Git 仓库已连接远程 GitHub 仓库
2. 所有本地变更已提交并推送
3. GitHub Actions CI/CD 流水线成功触发
4. Docker 镜像成功推送到 GHCR
5. 阿里云 ECS 部署成功（健康检查通过）
6. 前端页面可正常访问，后端 API 正常响应

**Tasks:**
1. git add 所有变更文件
2. 创建初始提交
3. git remote add origin 连接 GitHub 仓库
4. git push -u origin master
5. 监控 GitHub Actions 流水线状态
6. 验证 ECS 部署成功（curl /actuator/health）
7. 验证前端页面可访问

---

## Phase Dependency Graph

```
Phase 1 (Bug Fix) → Phase 2 (Pipeline) → Phase 3 (Deploy)
```

All phases are sequential — Phase 2 depends on Phase 1 code being correct, Phase 3 depends on Phase 2 pipeline being ready.

---

*Roadmap created: 2026-05-28*
