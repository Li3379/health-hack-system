# Requirements: Health Hack System — Optimization & Redeployment

**Defined:** 2026-05-28
**Core Value:** 平台核心价值是提供可靠、准确的健康评分和趋势分析

## v1 Requirements

### Bug Fix — Score Persistence

- [ ] **FIX-01**: ScoreUpdatedEvent 携带四个维度评分字段（cardiovascularScore、metabolicScore、weightScore、lifestyleScore）
- [ ] **FIX-02**: ScoreUpdatedEventListener 将维度评分持久化到 health_score_history
- [ ] **FIX-03**: HealthScoreServiceImpl 发布事件时传递维度评分
- [ ] **FIX-04**: ScoreUpdatedEvent 的所有发布点更新以传递维度评分
- [ ] **FIX-05**: ScoreHistoryServiceImplTest 更新以验证维度评分持久化

### Bug Fix — Frontend

- [ ] **FIX-06**: DeviceSync isMockData 检查实际数据源而非硬编码 false

### Deployment Pipeline — CI/CD

- [ ] **DEPLOY-01**: docker-publish.yml deploy job 添加 CI 前置条件（needs build + integration test）
- [ ] **DEPLOY-02**: 移除 no-cache: true，启用 Docker 层缓存
- [ ] **DEPLOY-03**: docker-compose.prod.yml 不暴露后端 8082 端口
- [ ] **DEPLOY-04**: 部署健康检查改用重试循环（最多 5 次，每次 15 秒间隔）
- [ ] **DEPLOY-05**: 合并 docker-publish.yml 和 deploy.yml 的重复部署逻辑为共享脚本

### Git & Deployment

- [ ] **GIT-01**: 初始化 Git 仓库并连接远程仓库
- [ ] **GIT-02**: 提交所有本地代码变更
- [ ] **GIT-03**: 推送到 GitHub master 触发 CI/CD 重新部署
- [ ] **GIT-04**: 验证部署成功（后端健康检查 + 前端可访问）

## v2 Requirements

Deferred to future release.

### Database Migration

- **DB-01**: 建立 schema 版本管理机制（版本化 SQL 脚本目录）
- **DB-02**: 评估引入 Flyway 或 Liquibase

### Testing

- **TEST-01**: 补充前端单元测试（当前仅 1 个）
- **TEST-02**: 补充维度评分相关的集成测试

### Security

- **SEC-01**: 清除 application-dev.yml 中的硬编码密码
- **SEC-02**: 配置 SSL/TLS（通过宝塔面板或 nginx）

## Out of Scope

| Feature | Reason |
|---------|--------|
| 零停机部署 | 当前 docker compose 重启的短暂停机可接受 |
| 小米健康 API 真实对接 | 当前 stub 实现足够，业务优先级低 |
| SSL/TLS 配置 | 需要域名和证书，通过宝塔面板单独处理 |
| 前端测试大幅补充 | 非本次优化优先级，后续迭代 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| FIX-01 | Phase 1 | Pending |
| FIX-02 | Phase 1 | Pending |
| FIX-03 | Phase 1 | Pending |
| FIX-04 | Phase 1 | Pending |
| FIX-05 | Phase 1 | Pending |
| FIX-06 | Phase 1 | Pending |
| DEPLOY-01 | Phase 2 | Pending |
| DEPLOY-02 | Phase 2 | Pending |
| DEPLOY-03 | Phase 2 | Pending |
| DEPLOY-04 | Phase 2 | Pending |
| DEPLOY-05 | Phase 2 | Pending |
| GIT-01 | Phase 3 | Pending |
| GIT-02 | Phase 3 | Pending |
| GIT-03 | Phase 3 | Pending |
| GIT-04 | Phase 3 | Pending |

**Coverage:**
- v1 requirements: 14 total
- Mapped to phases: 14
- Unmapped: 0 ✓

---
*Requirements defined: 2026-05-28*
*Last updated: 2026-05-28 after initial definition*
