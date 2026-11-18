---
gsd_state_version: "1.0"
milestone: v1.4
milestone_name: 风险评估可信化与引擎收敛
current_phase: 18 — 技术债清理与收尾（已完成，v1.4 待归档）
current_phase_name: 技术债清理与收尾
status: planning
stopped_at: v1.4 all 4 phases complete — ready for milestone audit/archive
last_updated: "2026-11-18T16:20:32.712Z"
last_activity: 2026-11-19
last_activity_desc: Phase 18 complete (dead code zeroed); v1.4 ready to archive
state_head: b320f297f36fd22aa157047c2cc9661cb7ff25c9
progress:
  total_phases: 4
  completed_phases: 4
  total_plans: 7
  completed_plans: 7
---

# State: Health Hack System

**Created:** 2026-05-28
**Current Milestone:** v1.4 风险评估可信化与引擎收敛
**Current Phase:** v1.4 完成，待归档
**Status:** Ready to plan

## Project Reference

See: `.planning/PROJECT.md`

**Core value:** 平台核心价值是提供可靠、准确的健康评分和趋势分析 — 评分数据必须完整持久化（包括维度评分），历史趋势图必须正确展示，部署过程必须稳定可靠。
**Current focus:** Phase 16 — 风险评估引擎输入重组

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
| 16. 风险评估引擎输入重组 | plans complete | 5/5 | 100% (awaiting verify-work) |

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

Phase: 16 (风险评估引擎输入重组) — COMPLETE
Plan: Not started
Status: All plans executed; five gates green; ready for /gsd:verify-work
Last activity: 2026-11-19 — Phase 16 complete, transitioned to Phase 17

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
- 2026-11-18: Phase 16 plan execution complete (5/5). 16-05 closed the same-source gaps: one shared `format`, threshold-based `classify`, profile-sourced `bmi`, summary segment 2 reworded. Five gates green in one batch (backend 702/0, checkstyle 0, frontend 623/54, build, lint). Four-viewport readings and the interface-500 observation are recorded in `16-05-SUMMARY.md`. Note for the next phase: the long-running backend on 8082 serves classes from before 16-01 — restart it before any live UAT, otherwise the coverage panel will look like it still has the old defects.

## Session

**Last session:** 2026-11-18T15:20:00.000Z
**Stopped at:** Phase 16 complete, ready to plan Phase 17
**Resume file:** None

## Performance Metrics

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 04-key P01 | 18min | 2 tasks | 11 files |
| Phase 04 P02 | 25min | 2 tasks | 8 files |
| Phase 16 P01 | 32min | 2 tasks | 6 files |
| Phase 16 P02 | ~18 min | 2 tasks | 2 files |
| Phase 16 P03 | 30min | 3 tasks | 8 files |
| Phase 16 P04 | 35m | 2 tasks | 2 files |
| Phase 16 P05 | 95m | 3 tasks | 7 files |

## Decisions

- [Phase 4]: All authoritative metric keys standardized to camelCase with protected acronyms and lenient passthrough
- [Phase 4]: In-memory singleton HealthMetricMetaRegistry covering 23 health and wellness metrics
- [Phase 4]: Public `GET /api/metrics/metadata` with 24-hour Cache-Control and ETag support
- [Phase 4]: Physical-impossible values intercepted via `@ValidMetricRecord` class-level constraint
- [Phase 4]: Service-layer queries expand single key filter to full alias set via `LambdaQueryWrapper.in()`
- [Phase 4]: Idempotent SQL migration script `v1.1_normalize_metric_keys.sql` for historical records
- [Phase 16]: 风险阈值收敛到 RiskThresholdRegistry 的 (metricKey, disease) 规则表；引擎与覆盖面板同读一份声明，riskNote 由规则渲染（16-01）
- [Phase 16]: 零新增 token：8 处 --border-1 全部改指既有 --border，保持三档边框体系
- [Phase 16]: 覆盖面板断言写 >= fixture 长度，因分组谓词交叠导致的重复渲染是 16-04 的待修项
- [Phase 16]: 精确保留 2 处 --text-3（徽标 + 符号位），用计数与区域双断言守 T-16-08
- [Phase 16]: 心血管评估改为 9 项自有输入（年龄基线 5/15、BMI 24.0/20、SBP 130/30、DBP 85/15、血糖 6.1/10、TC 5.20/20、TG 1.70/10、HDL BELOW 1.00/15、LDL 3.40/15，上限 150），删除 halve 折半
- [Phase 16]: 归因措辞改为方向感知的「评估阈值」：上限型说「超过」、下限型说「低于」，不再把风险阈值称作参考值
- [Phase 16]: MetricKeyNormalizer 补 systolicBloodPressure / diastolicBloodPressure 两个别名；其余疑似别名一律不做
- [Phase 16]: 修 missingMetrics 而不是 usedMetrics：血脂四项确实参与评估（D-02），从参与组移走才是说谎
- [Phase 16]: 摘要第二段措辞刻意留到 16-05 与 classify 同批变更，避免文字声称阈值而数字仍按参考范围
- [Phase 16]: 数值格式化收敛为 MetricCoverageAssembler.format（public static，唯一算式）；引擎 11 处 stripTrailingZeros 全部改调，同源成为结构事实而非断言技巧
- [Phase 16]: classify 两级判定 —— 有规则按注册表 direction/threshold（严格 > / 严格 <，阈值等值点不计分），无规则才回退参考范围
- [Phase 16]: bmi 双源取值只由 profile.getBmi() 派生，档案无值即 MISSING 且 value 为 null，绝不回落 health_metric 同名行
- [Phase 16]: 摘要第二段改为「{n} 项超出评估阈值」，abnormal 计算式未动 —— 换了分类源后它自然变成按阈值统计

## Operator Next Steps

- Start the next milestone with /gsd-new-milestone
