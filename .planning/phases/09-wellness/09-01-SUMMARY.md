---
phase: 09-wellness
plan: 01
subsystem: frontend
tags: [wellness, metrics, dashboard, validation, rate, vitest]

# Dependency graph
requires:
  - 08-01
  - 08-02
provides:
  - Extended PANIC_LIMITS for lifestyle wellness metrics (sleepDuration, waterIntake) in src/utils/metricValidation.ts
  - Dynamic metadata select, 1-5 star mood rating, and four-tier non-destructive validation in src/views/prevention/Metrics.vue
  - Full metadata alignment, ReferenceGuideCard, CriticalConfirmDialog in src/views/wellness/Dashboard.vue
affects: [09-02, 10-universal]

# Actuals
actuals:
  tokens: 24000
  tasks: 2
  commits: 1
  plan_head_before: b4c161db060c5ea65882bf4cf1654a1be7ff804e

# Tech tracking
tech-stack:
  added: []
  patterns: [Lifestyle panic limit evaluation, star rating with verbal descriptor mapping, dual wellness view alignment]

key-files:
  created: []
  modified:
    - hhs-frontend-v2/src/utils/metricValidation.ts
    - hhs-frontend-v2/src/__tests__/utils/metricValidation.spec.ts
    - hhs-frontend-v2/src/views/prevention/Metrics.vue
    - hhs-frontend-v2/src/views/wellness/Dashboard.vue

key-decisions:
  - "Supplemented PANIC_LIMITS with sleepDuration and waterIntake bounds to prevent extreme erroneous lifestyle inputs (D-03)"
  - "Preserved and refined 1-5 star rating interaction (el-rate) with verbal descriptors for mood while routing numeric metrics to el-input-number (WELL-02, D-02)"
  - "Appended both dialogs to body with append-to-body and enabled label-position='top' for clean layout across all viewport sizes (D-04)"

patterns-established:
  - "Harmonized Wellness Validation: Both prevention and dashboard dialogs share the exact four-tier non-blocking feedback and panic limits reassurance mechanism"

requirements-completed:
  - WELL-01
  - WELL-02
  - WELL-03

coverage:
  - id: D1
    description: "prevention/Metrics and wellness/Dashboard dynamically render 7 wellness metrics from useMetricMetaStore"
    requirement: "WELL-01"
    verification:
      - kind: component
        ref: "hhs-frontend-v2/src/__tests__/views/PreventionAndWellness.spec.ts#WELL-01 & WELL-02 & WELL-03: prevention/Metrics.vue should dynamically bind wellness metrics, mood rating, and four-tier validation with critical modal"
        status: pass
    human_judgment: false
  - id: D2
    description: "Mood uses 1-5 star rating while numeric metrics bind step/precision with ReferenceGuideCard"
    requirement: "WELL-02"
    verification:
      - kind: component
        ref: "hhs-frontend-v2/src/__tests__/views/PreventionAndWellness.spec.ts#WELL-01 & WELL-02 & WELL-03: wellness/Dashboard.vue should dynamically bind wellness metrics, mood rating, and non-destructively validate inputs"
        status: pass
    human_judgment: false
---

# Phase 09 Plan 01 Summary: 生活方式 Panic Limits 扩充与两视图弹窗重构

## 交付概述

本计划完成了两大生活保健视图的数据源对齐、心情评星交互与四梯度医学校验引擎接入：
1. **生活方式 Panic Limits 扩充 (`src/utils/metricValidation.ts`, D-03):**
   - 扩充 `PANIC_LIMITS` 包含 `sleepDuration: { min: 3.0, max: 16.0 }` 与 `waterIntake: { max: 5000 }`，有效防范严重超负荷饮水与极度睡眠缺失误触入库；
   - 单测套件已更新并通过断言验证。
2. **重构 `src/views/prevention/Metrics.vue` (WELL-01, WELL-02, WELL-03, D-01~D-04):**
   - 弹窗指标类型完全由 `useMetricMetaStore().wellnessMetrics` 动态循环渲染全量 7 项保健指标；
   - 彻底清除组件内写死的旧字典与写死精度；
   - 选中 `mood` 呈现 1-5 星级打分（`el-rate`）并联动中文评级；非 mood 指标自动匹配步长、精度与 `ReferenceGuideCard`；
   - 接入 `useMetricValidation`，失焦不抹除原值，Tier 1/2 宽容放行，Tier 3 触碰危急阈值弹出 `CriticalConfirmDialog` 二次确认；
   - 弹窗显式添加 `append-to-body` 脱离 GSAP 动画层叠上下文并启用 `label-position="top"`。
3. **重构 `src/views/wellness/Dashboard.vue` (WELL-01, WELL-02, WELL-03, D-01~D-04):**
   - 添加指标弹窗同样全面接入元数据 Store 动态驱动、`ReferenceGuideCard` 与 `CriticalConfirmDialog`；
   - 增加防重入守卫 `if (adding.value) return`，数据保存后自动刷新活动环与统计数据。

## 验证结论

- `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/utils/metricValidation.spec.ts`: 11 passed.
- `cd hhs-frontend-v2 && npm run build`: 0 errors.
