---
phase: 08-health
plan: 01
subsystem: frontend
tags: [metrics, validation, blood-pressure, composables, vitest]

# Dependency graph
requires:
  - 06-01
  - 06-02
provides:
  - useMetricValidation composable with optional collaborativeRef toggle in src/composables/useMetricValidation.ts
  - Metrics.vue dynamic metadata select options and blood pressure coordination in src/views/health/Metrics.vue
  - Test suite in src/__tests__/composables/useMetricValidation.spec.ts
affects: [08-02, 09-wellness]

# Actuals
actuals:
  tokens: 22000
  tasks: 2
  commits: 1
  plan_head_before: 03191aee3fc59db874136e0d37e6da62045c26b6

# Tech tracking
tech-stack:
  added: []
  patterns: [Collaborative blood pressure toggle, dynamic Pinia select options, atomic value swapping]

key-files:
  created: []
  modified:
    - hhs-frontend-v2/src/composables/useMetricValidation.ts
    - hhs-frontend-v2/src/__tests__/composables/useMetricValidation.spec.ts
    - hhs-frontend-v2/src/views/health/Metrics.vue

key-decisions:
  - "Extended useMetricValidation with collaborativeRef so single-metric editing of historical records accurately disables dual-field linkage (D-02)"
  - "Replaced static 7-item el-option and metricUnitMap in Metrics.vue with dynamic v-for over useMetricMetaStore().healthMetrics (HEALTH-01, D-01)"
  - "Integrated blood pressure dual-field grid layout and atomic swapBloodPressure in Metrics.vue (HEALTH-02, D-02)"

patterns-established:
  - "Decoupled Single/Dual Metric Validation: useMetricValidation seamlessly accommodates both add mode (collaborative: true) and edit mode (collaborative: false)"

requirements-completed:
  - HEALTH-01
  - HEALTH-02

coverage:
  - id: D1
    description: "Metrics.vue select dynamically loads 16 health metrics from useMetricMetaStore"
    requirement: "HEALTH-01"
    verification:
      - kind: component
        ref: "hhs-frontend-v2/src/__tests__/views/Metrics.spec.ts#HEALTH-01: should dynamically render 16 health metrics in select without hardcoded dictionary"
        status: pass
    human_judgment: false
  - id: D2
    description: "Blood pressure dual fields and swap button appear on add mode; disabled on edit mode"
    requirement: "HEALTH-02"
    verification:
      - kind: component
        ref: "hhs-frontend-v2/src/__tests__/views/Metrics.spec.ts#HEALTH-02: should support blood pressure dual fields and atomic swapping on add mode"
        status: pass
    human_judgment: false
---

# Phase 08 Plan 01 Summary: 元数据动态下拉与血压协同/单项模式解耦

## 交付概述

本计划完成了 `health/Metrics.vue` 指标数据源的全面重构与血压协同/单项模式解耦：
1. **扩展 `useMetricValidation.ts` (D-02):**
   - 增加可选参数 `collaborativeRef?: Ref<boolean>`；
   - 当 `collaborativeRef` 显式设为 `false` 时，强制将 `isBloodPressure` 置为 `false`，禁用舒张压关联校验，为历史单项编辑提供精准隔离。
2. **动态元数据选择器与清理私有字典 (`Metrics.vue`, HEALTH-01, D-01):**
   - 顶部筛选栏与弹窗下拉框完全改由 `useMetricMetaStore().healthMetrics` 动态循环渲染全量 16 项医学指标；
   - 彻底清除类内写死的 7 项静态 `<el-option>` 以及私有 `metricUnitMap` 常量；
   - 标准单位与精度直接从元数据读取，取消输入数值的粗暴清空。
3. **血压协同录入与一键对调 (`Metrics.vue`, HEALTH-02, D-02):**
   - 新增模式下选择“血压 (高低压协同)”切换为收缩压与舒张压并列网格双字段；
   - 倒置时即时展示告警条并支持「对调高低压数值」原子互换；
   - 提交时通过 `Promise.all` 协同持久化收缩压与舒张压两条独立记录。
4. **自动化测试验证:**
   - `src/__tests__/composables/useMetricValidation.spec.ts` 5/5 用例全绿；
   - 前端生产构建 `npm run build` 0 报错通过。

## 验证结论

- `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/composables/useMetricValidation.spec.ts`: 5 passed.
- `cd hhs-frontend-v2 && npm run build`: 0 errors.
