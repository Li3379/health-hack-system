# Roadmap: Health Hack System

## Milestones

- ✅ **v1.0 Optimization & Redeployment** — Phases 1-3 (completed 2026-05-29)
- ✅ **v1.1 智能健康指标填报引导与友好校验** — Phases 4-7 (completed 2026-11-05)
- ✅ **v1.2 全站健康与保健指标录入标准化与体验对齐** — Phases 8-10 ([archive](milestones/v1.2-ROADMAP.md)) (completed 2026-11-05)
- ✅ **v1.3 引导式配置与友好错误反馈** — Phases 11-14 ([archive](milestones/v1.3-ROADMAP.md)) (completed 2026-11-17)
- 🚧 **v1.4 风险评估可信化与引擎收敛** — Phases 15-18 (in progress)

## Phases

<details>
<summary>✅ v1.0 Optimization & Redeployment (Phases 1-3) — SHIPPED 2026-05-29</summary>

- [x] Phase 1: Score Persistence Bug Fix — completed 2026-05-29
- [x] Phase 2: Deployment Pipeline Hardening — completed 2026-05-29
- [x] Phase 3: Git Push & Deploy — completed 2026-05-29

</details>

<details>
<summary>✅ v1.1 智能健康指标填报引导与友好校验 (Phases 4-7) — SHIPPED 2026-11-05</summary>

- [x] Phase 4: 后端统一指标元数据注册中心与 Key 归一化 (2/2 plans) — completed 2026-11-04
- [x] Phase 5: 前端元数据 Store 与全站格式化防英文外露 (2/2 plans) — completed 2026-11-04
- [x] Phase 6: 前端非破坏性录入引导、四梯度宽容校验与友好反馈 (2/2 plans) — completed 2026-11-05
- [x] Phase 7: 全渠道校验对齐与跨端测试验证 (3/3 plans) — completed 2026-11-05

</details>

<details>
<summary>✅ v1.2 全站健康与保健指标录入标准化与体验对齐 (Phases 8-10) — SHIPPED 2026-11-05</summary>

- [x] Phase 8: 健康指标管理页录入与编辑对齐 (2/2 plans) — completed 2026-11-05
- [x] Phase 9: 保健指标管理页录入对齐 (2/2 plans) — completed 2026-11-05
- [x] Phase 10: 指标录入通用交互组件封装与全站验证 (2/2 plans) — completed 2026-11-05

Full detail: [milestones/v1.2-ROADMAP.md](milestones/v1.2-ROADMAP.md)

</details>

<details>
<summary>✅ v1.3 引导式配置与友好错误反馈 (Phases 11-14) — SHIPPED 2026-11-17</summary>

- [x] Phase 11: 全站统一友好错误反馈地基 (8/8 plans) — completed 2026-11-07
- [x] Phase 12: 阈值配置向导化与告警正确性修复 (9/9 plans) — completed 2026-11-09
- [x] Phase 13: 健康档案完善引导（非阻塞） (10/10 plans) — completed 2026-11-09
- [x] Phase 14: 全站收尾排查与回归验证 (14/14 plans) — completed 2026-11-11

Full detail: [milestones/v1.3-ROADMAP.md](milestones/v1.3-ROADMAP.md)
Audit: [milestones/v1.3-MILESTONE-AUDIT.md](milestones/v1.3-MILESTONE-AUDIT.md) — 29/29 requirements, status passed

</details>

<details>
<summary>🚧 v1.4 风险评估可信化与引擎收敛 (Phases 15-18) — IN PROGRESS</summary>

- [x] **Phase 15: 风险历史按批次折叠与趋势** — 把已写入的 `batchId` 用起来：历史按「一次评估」折叠，显示各项相对上次的分数变化
- [ ] **Phase 16: 风险评估引擎输入重组** — 用现有 23 项指标重新设计三种疾病的评估（心血管用血脂/血压/BMI/年龄，不再只是前两项折半），不新增疾病
- [ ] **Phase 17: 告警语义收敛** — 消除 `IntelligentAlertService` 内 4 处独立区间比较，统一到 `ThresholdRangeChecker`，避免接线时分叉出 FIX-01 缺陷
- [ ] **Phase 18: 技术债清理与收尾** — 删除无引用的 `RiskAnalyzer`、`RiskScorer` 改读归因快照、修复 `FileUploadController` 英文泄漏、清理未消费字典键

**Success criteria**

1. 风险历史按批次可折叠，每个批次显示时间与三条疾病，并标注相对上一批次的分数变化（上升/下降/持平）
2. 三种疾病的评估都使用其临床相关的全部已有指标，且每项输入的贡献在详情里可核对（各项之和 = 总分）
3. 全站只有一套阈值区间语义：`ThresholdRangeChecker.classify` 是唯一判定源，无第二份比较实现
4. 无无引用类、无用户可见英文异常原文、无未消费字典键；后端与前端门禁全绿

</details>

## Phase Details

### Phase 15: 风险历史按批次折叠与趋势

**Goal**: 把已写入的 `batchId` 真正呈现给用户 —— 风险评估历史按「一次评估」折叠成批次卡片，并显示各项相对上一批次的分数与等级变化，让用户看得见自己的风险在变好还是变差

**Depends on**: Nothing（`batchId` 与 `contributors` 快照已在 v1.3 收尾改动中写入数据库与 API，本阶段为纯前端呈现）

**Requirements**: 无独立 REQ-ID（v1.4 未新建 REQUIREMENTS.md，本阶段范围由 `15-CONTEXT.md` 的验收标准界定）

**Success Criteria** (what must be TRUE):

  1. 风险页历史区域按 `batchId` 折叠，每个批次一个可展开面板；最近批次默认展开，其余默认折叠
  2. 每项疾病显示与上一批次同疾病的分数差（↑ / ↓ / 持平 / 首次评估），不出现「↑0」这类噪音
  3. 跨等级阈值时额外显示等级变化（如「低风险 → 中风险」），与分数箭头分开呈现
  4. `legacy-*` 批次正常渲染，`contributors` 为空时不渲染明细且给出说明
  5. 只有 1 个批次时全部显示「首次评估」
  6. 前端门禁全绿（`test:run` + `build`），后端零改动

**UI hint**: yes

**Plans:** 2 plans

Plans:
**Wave 1**

- [ ] 15-01-PLAN.md — 批次折叠端到端贯通（tracer）：batchId 分组、可折叠面板、legacy 兼容、两 spec 补齐 collapse stub

**Wave 2** *(blocked on Wave 1 completion)*

- [ ] 15-02-PLAN.md — 相邻批次分差与等级变化徽标、批次卡片样式、趋势测试锚点

## Progress

| Milestone | Phases | Plans | Status | Completed |
|-----------|--------|-------|--------|-----------|
| v1.0 Optimization & Redeployment | 3/3 | — | Complete | 2026-05-29 |
| v1.1 智能健康指标填报引导与友好校验 | 4/4 | 9/9 | Complete | 2026-11-05 |
| v1.2 全站健康与保健指标录入标准化与体验对齐 | 3/3 | 6/6 | Complete | 2026-11-05 |
| v1.3 引导式配置与友好错误反馈 | 4/4 | 41/41 | Complete | 2026-11-17 |
| v1.4 风险评估可信化与引擎收敛 | 1/4 | 2/2 | In progress | — |
