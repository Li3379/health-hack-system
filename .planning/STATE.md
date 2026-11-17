---
gsd_state_version: "1.0"
milestone: v1.4
milestone_name: 风险评估可信化与引擎收敛
current_phase: 15
current_phase_name: 风险历史按批次折叠与趋势
status: executing
stopped_at: Phase 15 planned (2 plans, 2 waves); ready for /gsd:execute-phase 15
last_updated: "2026-11-17T12:15:00.000Z"
last_activity: 2026-11-17
last_activity_desc: Phase 15 planned and verified (3 revision rounds)
state_head: 98e686a9e84a311ddd6cb6766639b54be15c09c4
progress:
  total_phases: 1
  completed_phases: 11
  total_plans: 2
  completed_plans: 41
---

# State: Health Hack System

**Created:** 2026-05-28
**Current Milestone:** v1.4 风险评估可信化与引擎收敛
**Current Phase:** 15
**Status:** Ready to execute

## Project Reference

See: `.planning/PROJECT.md`

**Core value:** 平台核心价值是提供可靠、准确的健康评分和趋势分析 — 评分数据必须完整持久化（包括维度评分），历史趋势图必须正确展示，部署过程必须稳定可靠。
**Current focus:** Phase 15 — 风险历史按批次折叠与趋势

## Milestone Progress

| Milestone | Status | Phases | Completed |
|-----------|--------|--------|-----------|
| v1.0 Optimization & Redeployment | done | 3/3 | 2026-05-29 |
| v1.1 智能健康指标填报引导与友好校验 | done | 4/4 | 2026-11-05 |
| v1.2 全站健康与保健指标录入标准化与体验对齐 | done | 3/3 | 2026-11-05 |
| v1.3 引导式配置与友好错误反馈 | done | 4/4 | 2026-11-11 |
| v1.4 风险评估可信化与引擎收敛 | in progress | 0/4 | — |

## Phase Progress

| Phase | Status | Plans | Progress |
|-------|--------|-------|----------|
| 11. 全站统一友好错误反馈地基 | done | 8/8 | 100% |
| 12. 阈值配置向导化与告警正确性修复 | done | 9/9 | 100% |
| 13. 健康档案完善引导（非阻塞） | done | 10/10 | 100% |
| 14. 全站收尾排查与回归验证 | done | 14/14 | 100% |

### Completed Phases (v1.0 – v1.2)

| Phase | Status | Plans | Progress |
|-------|--------|-------|----------|
| 1. Score Persistence Bug Fix | done | 0/0 | 100% |
| 2. Deployment Pipeline Hardening | done | 0/0 | 100% |
| 3. Git Push & Deploy | done | 0/0 | 100% |
| 4. 后端统一指标元数据注册中心与 Key 归一化 | done | 2/2 | 100% |
| 5. 前端元数据 Store 与全站格式化防英文外露 | done | 2/2 | 100% |
| 6. 前端非破坏性录入引导、四梯度宽容校验与友好反馈 | done | 2/2 | 100% |
| 7. 全渠道校验对齐与跨端测试验证 | done | 3/3 | 100% |
| 8. 健康指标管理页录入与编辑对齐 | done | 2/2 | 100% |
| 9. 保健指标管理页录入对齐 | done | 2/2 | 100% |
| 10. 指标录入通用交互组件封装与全站验证 | done | 2/2 | 100% |

## Current Position

Phase: 15 (风险历史按批次折叠与趋势) — READY TO EXECUTE
Plan: —
Status: Awaiting next milestone
Last activity: 2026-11-17 — Milestone v1.3 completed and archived

## Accumulated Context

### Key Decisions

- 错误处理改为"翻译而非展示"：`request.ts` 只抛类型化 `ApiError`，由调用方或统一层决定呈现 — v1.3 Phase 11
- 错误反馈地基必须先于所有页面级错误迁移落地，否则重复 toast 会被逐页重新引入 — v1.3 排序约束
- 健康档案引导采非阻塞式（徽标/横幅/可稍后提醒），不做路由门禁、不新增必填字段 — v1.3 Phase 13
- 阈值推荐值来自元数据注册表 + 告警种子规则派生，不新建阈值模板表 — v1.3 Phase 12
- 阈值 key 规范化（写入路径）与存量数据修复必须与向导同阶段且先行，否则向导只会制造更多失效阈值行 — v1.3 Phase 12
- 建立不可变 HealthMetricMetaRegistry 作为全系统唯一指标元数据权威源 (SSOT)
- 采用四梯度校验模型（正常、警告、危急、硬限制），废除破坏性失焦清空，急危重症允许二次确认保存
- 提炼 UniversalMetricDialog 统一通用弹窗组件，消灭全站 1,750+ 行重复弹窗表单代码

### Blockers

- **无阻塞项** — v1.3 已交付并通过里程碑审计（29/29 需求）。
- 遗留环境限制：Testcontainers 无法从 Maven JVM 访问 Docker socket（`docker info` 正常但 IT 报 Could not find a valid Docker environment），需在 socket 可达环境重跑集成测试（WINDOWS.md #3）。
- 结转技术债（非阻塞，已记入 `MILESTONES.md` 与 `v1.3-MILESTONE-AUDIT.md`）：`FileUploadController:104` 的英文异常回显；`IntelligentAlertService` 的休眠重复区间逻辑；零请求取消接线；6 个未消费字典键。

### Session Continuity

- Previous: v1.2 milestone completed (Phases 8-10, 7/7 requirements).
- Now: **v1.3 shipped and archived** — 4 phases (11-14), 41/41 plans, backend 849/0, frontend 644/59.
- 2026-11-11: Phases 11 and 13 went `stale` only because Phase 14's migration commits touched their `covered_files`; both re-tested live (6/6 each), digests refreshed, no defects.
- 2026-11-16: `ee4d534` changed the envelope-code contract, invalidating two recorded truths. Both were *amended*, not refreshed — Phase 11 truth 7 no longer claims `Result.code` semantics are unchanged.
- 2026-11-17: Milestone audit found three real defects (two silent-error gaps introduced by `ee4d534`, one route-meta inheritance bug). All fixed with mutation-verified tests, then archived.
- Next step: `/gsd:new-milestone`.

## Session

**Last session:** 2026-11-17T01:35:00.000Z
**Stopped at:** v1.3 shipped and audited; ready for /gsd:new-milestone
**Resume file:** none — start a new milestone

## Performance Metrics

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 04-key P01 | 18min | 2 tasks | 11 files |
| Phase 04 P02 | 25min | 2 tasks | 8 files |

## Decisions

- [Phase 4]: All authoritative metric keys standardized to camelCase with protected acronyms and lenient passthrough
- [Phase 4]: In-memory singleton HealthMetricMetaRegistry covering 23 health and wellness metrics
- [Phase 4]: Public `GET /api/metrics/metadata` with 24-hour Cache-Control and ETag support
- [Phase 4]: Physical-impossible values intercepted via `@ValidMetricRecord` class-level constraint
- [Phase 4]: Service-layer queries expand single key filter to full alias set via `LambdaQueryWrapper.in()`
- [Phase 4]: Idempotent SQL migration script `v1.1_normalize_metric_keys.sql` for historical records

## Operator Next Steps

- Start the next milestone with /gsd-new-milestone
