---
phase: 06-input
plan: 01
subsystem: frontend
tags: [validation, four-tier, composable, blood-pressure, vitest]

# Dependency graph
requires:
  - 05-01
  - 05-02
provides:
  - evaluateMetricValue pure function evaluation engine in src/utils/metricValidation.ts
  - PANIC_LIMITS clinical emergency threshold dictionary
  - checkBloodPressureInversion blood pressure inversion detection
  - useMetricValidation composable with reactive evaluation, inversion detection and atomic swap in src/composables/useMetricValidation.ts
affects: [06-02]

# Actuals
actuals:
  tokens: 22000
  tasks: 2
  commits: 1
  plan_head_before: bdf38fc9d0124f5a657cf762b3ef306daeec1932

# Tech tracking
tech-stack:
  added: []
  patterns: [Pure-function medical four-tier evaluator, decoupled validation composable, atomic value swap for dual-field linkage]

key-files:
  created:
    - hhs-frontend-v2/src/utils/metricValidation.ts
    - hhs-frontend-v2/src/__tests__/utils/metricValidation.spec.ts
    - hhs-frontend-v2/src/composables/useMetricValidation.ts
    - hhs-frontend-v2/src/__tests__/composables/useMetricValidation.spec.ts

key-decisions:
  - "Decoupled medical classification from form blocking: Tier 2 (Warning) and Tier 3 (Critical) both output canSave: true to permit authentic pathological data recording (D-02)"
  - "Reserved Tier 4 (Hard Limit) exclusively for biological impossibility with clear valid range explanation, non-destructively preserving user input (VAL-01, D-02)"
  - "Constructed PANIC_LIMITS for core vital signs (systolicBP, diastolicBP, bloodGlucose, heartRate, bodyTemperature, bloodOxygen) to drive critical confirmation (D-05)"
  - "Implemented checkBloodPressureInversion and swapBloodPressure for seamless one-click blood pressure correction (VAL-04, D-04)"

patterns-established:
  - "Four-Tier Physiological Engine: Pure function evaluateMetricValue guarantees zero side effects and evaluates inputs in order Tier 4 -> Tier 3 -> Tier 2 -> Tier 1"
  - "Reactive Validation Composable: useMetricValidation transparently binds to useMetricMetaStore and tracks dual-field metrics"

requirements-completed:
  - VAL-02
  - VAL-03
  - VAL-04
  - GUIDE-03

coverage:
  - id: D1
    description: "Four-tier evaluation engine correctly distinguishes Normal, Warning, Critical, and Hard Bound"
    requirement: "VAL-02"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/metricValidation.spec.ts#Tier 1 (Normal)"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/metricValidation.spec.ts#Tier 2 (Warning Deviation)"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/metricValidation.spec.ts#Tier 3 (Critical Panic)"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/metricValidation.spec.ts#Tier 4 (Hard Biological Bounds)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Warning deviation outputs canSave: true with explanatory guidance"
    requirement: "VAL-03"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/metricValidation.spec.ts#Tier 2 (Warning Deviation)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Blood pressure inversion detection and one-click atomic swapping"
    requirement: "VAL-04"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/composables/useMetricValidation.spec.ts#should detect blood pressure inversion and perform atomic value swapping"
        status: pass
    human_judgment: false
  - id: D4
    description: "Dual-field blood pressure coordinated validation"
    requirement: "GUIDE-03"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/composables/useMetricValidation.spec.ts#should correctly evaluate blood pressure and support dual-field linkage"
        status: pass
    human_judgment: false
---

# Phase 06 Plan 01 Summary: 四梯度纯函数评估引擎与组合层

## 交付概述

本计划完成了四梯度生理评估引擎与组合式函数的全流程构建与验证：
1. **纯函数评估引擎 (`src/utils/metricValidation.ts`):**
   - 实现无状态四梯度评估引擎 `evaluateMetricValue(value, def)`，涵盖 Tier 1 正常参考值、Tier 2 预警偏差、Tier 3 急危重症与 Tier 4 生理极限；
   - 确立 `PANIC_LIMITS` 权威急危重症阈值字典，精准覆盖收缩压、舒张压、空腹/普通血糖、心率、体温及血氧；
   - 彻底区分医学异常与非法输入：Tier 2 预警与 Tier 3 危急值均判定为 `canSave: true`，杜绝将真实高血压/高血糖病患数据拦截在外；
   - 实现生理倒置检测 `checkBloodPressureInversion(systolic, diastolic)`，精准捕获收缩压 <= 舒张压。
2. **响应式组合函数 (`src/composables/useMetricValidation.ts`):**
   - 对接 `useMetricMetaStore`，响应式追踪当前指标的标准定义与舒张压定义；
   - 提供 `primaryEval`、`diastolicEval`、`isInverted`、`hasCritical`、`isSaveBlocked` 等完整响应式计算属性；
   - 提供原子级一键对调方法 `swapBloodPressure()`，原地互换收缩压与舒张压数值，即时解除倒置警示。
3. **单元测试与回归覆盖:**
   - 编写 `src/__tests__/utils/metricValidation.spec.ts`（10 个测试用例，100% 通过）；
   - 编写 `src/__tests__/composables/useMetricValidation.spec.ts`（4 个测试用例，100% 通过）；
   - 前端全量测试 52 个用例全部通过。

## 验证结论

- `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/utils/metricValidation.spec.ts`: 10 passed.
- `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/composables/useMetricValidation.spec.ts`: 4 passed.
- `cd hhs-frontend-v2 && npm run test:run`: 52 passed.
