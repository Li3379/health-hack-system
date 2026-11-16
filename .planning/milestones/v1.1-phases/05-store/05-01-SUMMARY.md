---
phase: 05-store
plan: 01
subsystem: frontend
tags: [metadata, pinia, normalizer, offline-fallback, typescript, vitest]

# Dependency graph
requires:
  - 04-01
  - 04-02
provides:
  - MetricDefinitionVO and MetricMetaMap TypeScript contracts in src/types/api.ts
  - FALLBACK_METRIC_META static dictionary covering all 23 health and wellness metrics in src/constants/metricMeta.ts
  - metricNormalizer utility with canonical key resolution, alias expansion, and Title Case word formatting in src/utils/metricNormalizer.ts
  - metricMetaApi REST client in src/api/metric-meta.ts
  - useMetricMetaStore Pinia store with offline fallback and reactive classification in src/stores/metricMeta.ts
  - App.vue preloading integration on onMounted lifecycle
affects: [05-02, 06-input, 07-ai]

# Actuals
actuals:
  tokens: 28000
  tasks: 2
  commits: 1
  plan_head_before: ffa2c9a2dca80bd7107f7fd5c28ae86efd0ed19e

# Tech tracking
tech-stack:
  added: []
  patterns: [Pinia composition store with offline fallback, client-side alias normalization, Title Case word tokenization for unknown keys]

key-files:
  created:
    - hhs-frontend-v2/src/constants/metricMeta.ts
    - hhs-frontend-v2/src/utils/metricNormalizer.ts
    - hhs-frontend-v2/src/api/metric-meta.ts
    - hhs-frontend-v2/src/stores/metricMeta.ts
    - hhs-frontend-v2/src/__tests__/utils/metricNormalizer.spec.ts
    - hhs-frontend-v2/src/__tests__/stores/metricMeta.spec.ts
  modified:
    - hhs-frontend-v2/src/types/api.ts
    - hhs-frontend-v2/src/App.vue

key-decisions:
  - "Constructed complete local static dictionary FALLBACK_METRIC_META containing all 23 metrics matching Phase 4 backend registry exactly (D-01)"
  - "Preloaded metric metadata silently in App.vue onMounted lifecycle hook to achieve 0ms screen blink and zero network lag (D-02)"
  - "Implemented formatUnknownKey to tokenize snake_case, camelCase, and kebab-case into friendly Title Case phrases without exposing underscores (D-05)"
  - "Configured useMetricMetaStore to initialize directly with deep copy of FALLBACK_METRIC_META and gracefully swallow fetch errors during offline/flaky network conditions (D-01)"

patterns-established:
  - "Offline-First Metadata Store: Pinia state initializes with full authoritative fallback, incrementally updating when the REST endpoint responds"
  - "Bidirectional Key Normalization: metricNormalizer simplifies and normalizes keys while protecting domain acronyms (systolicBP, bloodOxygen)"

requirements-completed:
  - META-03

coverage:
  - id: D1
    description: "Local FALLBACK_METRIC_META with 23 metrics matching backend definitions"
    requirement: "META-03"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/stores/metricMeta.spec.ts#should initialize immediately with 23 fallback metric definitions"
        status: pass
    human_judgment: false
  - id: D2
    description: "Key normalizer supporting camelCase, snake_case, medical acronyms, and Title Case unknown key formatting"
    requirement: "META-03"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/metricNormalizer.spec.ts"
        status: pass
    human_judgment: false
  - id: D3
    description: "Pinia useMetricMetaStore with silent fetchMetadata and resilient fallback"
    requirement: "META-03"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/stores/metricMeta.spec.ts#should gracefully handle network error and remain on fallback metadata"
        status: pass
    human_judgment: false
---

# Phase 05 Plan 01 Summary: 前端元数据 Store 与离线兜底体系

## 交付概述

本计划完成了前端权威指标元数据基座与离线防护体系的交付：
1. **类型定义 (`src/types/api.ts`):** 新增 `MetricDefinitionVO` 与 `MetricMetaMap` 接口契约，与后端 Java Record 保持 1:1 强类型对齐。
2. **静态权威常量 (`src/constants/metricMeta.ts`):** 全量收录 23 项指标（16 项医学指标 + 7 项生活保健指标），包括精度、步长、参考范围与生理极限，构建离线弱网的坚固后盾。
3. **Key 归一化工具 (`src/utils/metricNormalizer.ts`):** 实现了双向 Key 映射、医学缩写保护（`sbp`, `dbp`, `spo2`, `temp`, `glu`）与未知 Key 首字母大写词组化转换（`vo2_max` -> `Vo2 Max`），从源头杜绝下划线暴露。
4. **API 模块 (`src/api/metric-meta.ts`):** 封装对接后端 `GET /api/metrics/metadata`。
5. **Pinia 状态库 (`src/stores/metricMeta.ts`):** 构建组合式 Store，初始状态即包含全部 23 种离线指标，提供 `getDefinition`、`healthMetrics`、`wellnessMetrics` 响应式筛选与静默异步更新机制。
6. **自举挂载预加载 (`src/App.vue`):** 在全局应用根组件挂载时静默预加载元数据，用户无感知。
7. **全覆盖单元测试:** 编写并全部通过 18 项自动化单元测试，前端全量打包构建 `npm run build` 0 报错通过。

## 验证结论

- `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/utils/metricNormalizer.spec.ts`: 13 个用例全数通过。
- `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/stores/metricMeta.spec.ts`: 5 个用例全数通过。
- `cd hhs-frontend-v2 && npm run test:run`: 全量 23 个测试用例 100% 通过。
- `cd hhs-frontend-v2 && npm run build`: `vue-tsc` 与 `vite build` 生产构建成功通过。
