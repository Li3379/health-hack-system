---
phase: 07-sync
plan: 01
subsystem: ui
tags: [vue3, element-plus, pinia, validation, four-tier, blood-pressure, metric-meta]

# Dependency graph
requires:
  - phase: 06-guide
    provides: ReferenceGuideCard, CriticalConfirmDialog, and useMetricValidation
provides:
  - health/Metrics.vue dynamic metadata loading from useMetricMetaStore and collaborative blood pressure input with one-click swap
  - prevention/Metrics.vue dynamic wellness metrics binding, 1-5 star mood rating, and four-tier validation badges
  - wellness/Dashboard.vue dynamic wellness metrics binding, four-tier validation badges, ReferenceGuideCard, and CriticalConfirmDialog
affects: [07-sync, health, prevention, wellness]

# Actuals
actuals:
  tokens: 12316
  tasks: 2
  commits: 3
  plan_head_before: 1c5e933325fc6ddc2638eb00e5ef3729a1970326

# Tech tracking
tech-stack:
  added: []
  patterns: [dynamic-meta-store, four-tier-validation, non-destructive-input, collaborative-blood-pressure, panic-confirm-modal]

key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/PreventionAndWellness.spec.ts
  modified:
    - hhs-frontend-v2/src/views/health/Metrics.vue
    - hhs-frontend-v2/src/views/prevention/Metrics.vue
    - hhs-frontend-v2/src/views/wellness/Dashboard.vue
    - hhs-frontend-v2/src/__tests__/views/Metrics.spec.ts

key-decisions:
  - "彻底废除各视图私有硬编码选项与字典（metricUnitMap 与 WELLNESS_METRICS），全量改由 useMetricMetaStore 动态驱动"
  - "实现非破坏性录入交互：异常数值失焦时不重置或清空输入框，四梯度标签动态指示状态，仅在 Tier 4 物理超限或血压倒置时阻止提交"
  - "保留心情 (mood) 专属 1-5 星级打分控件交互，其他生活方式指标根据元数据自动匹配步长、精度与参考范围"

patterns-established:
  - "全站录入弹窗统一采用 560px 宽度、label-position=top、destroy-on-close 与 append-to-body 布局"
  - "所有数值型指标统一接入 useMetricValidation 驱动四梯度胶囊与 ReferenceGuideCard 参考指南卡片"

requirements-completed:
  - SYNC-01

coverage:
  - id: D1
    description: "health/Metrics.vue 动态元数据驱动与血压双字段协同"
    requirement: SYNC-01
    verification:
      - kind: unit
        ref: "src/__tests__/views/Metrics.spec.ts#HEALTH-01 & HEALTH-02"
        status: pass
    human_judgment: false
  - id: D2
    description: "health/Metrics.vue 四梯度非破坏性校验与危急值确认"
    requirement: SYNC-01
    verification:
      - kind: unit
        ref: "src/__tests__/views/Metrics.spec.ts#HEALTH-03"
        status: pass
    human_judgment: false
  - id: D3
    description: "prevention/Metrics.vue 废除私有字典，接入权威元数据、心情打分与四梯度校验"
    requirement: SYNC-01
    verification:
      - kind: unit
        ref: "src/__tests__/views/PreventionAndWellness.spec.ts#prevention/Metrics.vue"
        status: pass
    human_judgment: false
  - id: D4
    description: "wellness/Dashboard.vue 废除私有字典，接入权威元数据与四梯度宽容校验"
    requirement: SYNC-01
    verification:
      - kind: unit
        ref: "src/__tests__/views/PreventionAndWellness.spec.ts#wellness/Dashboard.vue"
        status: pass
    human_judgment: false

duration: 15min
completed: 2026-11-05
status: complete
---

# Phase 07 Plan 01: 前端指标录入弹窗全渠道对齐与四梯度校验重构 Summary

**全面重构健康指标管理页、保健指标管理页与生活方式看板录入弹窗，彻底废除私有硬编码字典，统一接入权威元数据、血压协同、心情打分、四梯度非破坏性校验与危急值二次确认保护。**

## Performance

- **Duration:** 15 min
- **Started:** 2026-11-05T10:32:00Z
- **Completed:** 2026-11-05T10:48:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- **彻底废除硬编码字典：** 在 `health/Metrics.vue` 中彻底废除了写死的 7 个指标选项与 `metricUnitMap` 私有字典；在 `prevention/Metrics.vue` 与 `wellness/Dashboard.vue` 中彻底废除了私有 `WELLNESS_METRICS` 字典与写死的 `:precision="2"` / `:precision="1"` 限制，全部指标选项统一由 `useMetricMetaStore` 动态驱动。
- **血压双字段协同与原子对调：** 在 `health/Metrics.vue` 新增模式下支持收缩压与舒张压并列录入与生理倒置黄色警示，提供“对调高低压数值”按钮实现原子对调；在编辑模式下锁定单指标编辑，规避历史记录修改异常。
- **四梯度非破坏性生理校验：** 全面接入 `useMetricValidation`，输入框异常失焦时完整保留用户数值，原位展示绿色/黄色/橙色状态胶囊与解释文本，仅在超出 Tier 4 物理生理极限时阻断提交。
- **参考指南与危急二次确认：** 所有管理页弹窗表单底部常态化挂载 `ReferenceGuideCard` 临床生理参考指南，并在输入 Tier 3 急危重症数值提交时弹出 `CriticalConfirmDialog` 二次确认模态窗，用户确认后仍可安全入库。
- **心情 1-5 星交互保留：** 在保健指标与生活看板中，当切换为 `mood` 指标时无缝渲染 `<el-rate>` 1-5 星级打分控件与文字描述，切换其他指标时动态适配步长、精度与占位符。

## Task Commits

Each task was committed atomically:

1. **Task 1: Tracer slice — 重构健康指标管理页 (health/Metrics.vue) 录入与编辑弹窗** - `6e5d3ae` (feat)
2. **Task 2: 重构保健指标管理页 (prevention/Metrics.vue) 与生活方式看板 (wellness/Dashboard.vue) 弹窗** - `4c69586` (feat)

## Files Created/Modified

- `hhs-frontend-v2/src/views/health/Metrics.vue` - 健康指标管理页，接入权威元数据驱动、血压协同与四梯度非破坏性校验
- `hhs-frontend-v2/src/views/prevention/Metrics.vue` - 保健指标管理页，废除私有字典，保留心情星级，接入通用元数据与四梯度校验
- `hhs-frontend-v2/src/views/wellness/Dashboard.vue` - 生活方式看板，重构添加指标弹窗，接入元数据 Store、四梯度校验与危急确认
- `hhs-frontend-v2/src/__tests__/views/Metrics.spec.ts` - 健康指标管理页单元测试，覆盖动态元数据、血压协同、单项编辑与四梯度校验
- `hhs-frontend-v2/src/__tests__/views/PreventionAndWellness.spec.ts` - 保健与看板指标弹窗单元测试，覆盖 7 项保健指标动态绑定、心情打分与非破坏性校验

## Decisions Made

- 统一全站录入弹窗容器规范为 `width="560px"`, `destroy-on-close`, `append-to-body`, `label-position="top"`，确保多弹窗视觉体验与空间尺度高度一致。
- 非破坏性失焦保护：去除任何在 `@blur` 或 `@change` 中清空用户输入的逻辑，严格保障输入异常值时的编辑可修改性。
- 保留 `mood` 独特的星级打分交互，当 `metricKey === 'mood'` 时呈现 `<el-rate>`，其它数值型生活方式指标呈现 `:precision` 与 `:step` 驱动的 `<el-input-number>`。

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Mock `@/api/metric-meta` in unit tests**
- **Found during:** Task 1 & Task 2 (Vitest testing in happy-dom environment)
- **Issue:** Adding `metricMetaStore.fetchMetadata()` in `onMounted` triggered network request to `/api/metrics/metadata`, producing ECONNREFUSED network errors in mock tests.
- **Fix:** Added `vi.mock('@/api/metric-meta', ...)` to return mock metadata in `Metrics.spec.ts` and `PreventionAndWellness.spec.ts`.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/Metrics.spec.ts`, `hhs-frontend-v2/src/__tests__/views/PreventionAndWellness.spec.ts`
- **Verification:** All 10 test suites and 71 unit tests passed with 0 errors.

---

**Total deviations:** 1 auto-fixed (Rule 3 - Blocking)  
**Impact on plan:** 保证单元测试套件在脱机/无后端服务环境下稳定通过，无作用域蔓延。

## Next Phase Readiness

- 前端三大核心管理页的指标录入弹窗已全部对齐 Phase 6 的权威元数据、四梯度校验与组件规范。
- 为 Plan 07-02（后端 AiParseServiceImpl 与 OcrServiceImpl 动态元数据对齐）及 Plan 07-03（全渠道端到端集成测试验证）打下了坚实的前端基石。

## Self-Check: PASSED

All created files and commits have been verified on disk and in git history:
- FOUND: `hhs-frontend-v2/src/views/health/Metrics.vue`
- FOUND: `hhs-frontend-v2/src/views/prevention/Metrics.vue`
- FOUND: `hhs-frontend-v2/src/views/wellness/Dashboard.vue`
- FOUND: `hhs-frontend-v2/src/__tests__/views/PreventionAndWellness.spec.ts`
- FOUND: `.planning/phases/07-sync/07-01-SUMMARY.md`
- FOUND: Commit `6e5d3ae`
- FOUND: Commit `4c69586`

---
*Phase: 07-sync*
*Completed: 2026-11-05*
