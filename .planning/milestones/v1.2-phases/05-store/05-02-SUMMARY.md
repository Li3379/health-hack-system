---
phase: 05-store
plan: 02
subsystem: frontend
tags: [format, localization, clean-code, backward-compatible, vitest]

# Dependency graph
requires:
  - 05-01
provides:
  - Backward-compatible getMetricDisplayName & getMetricLabel in src/utils/format.ts with zero raw key leaks
  - Standard unit resolution getMetricUnit for all 23 health and wellness metrics
  - Friendly Title Case tokenization for unknown/custom keys
  - Cleaned prevention/Metrics.vue using global getMetricLabel
  - Comprehensive format test matrix covering all aliases and edge cases
affects: [06-input, 07-ai]

# Actuals
actuals:
  tokens: 24000
  tasks: 2
  commits: 1
  plan_head_before: 4b66edf8885b5d19fbc7c0ca5e6da8ae687cb6f4

# Tech tracking
tech-stack:
  added: []
  patterns: [Transparent backward-compatible formatter wrapper, SSOT metadata delegation, Title Case word casing fallback]

key-files:
  created:
    - hhs-frontend-v2/src/__tests__/utils/format.spec.ts
  modified:
    - hhs-frontend-v2/src/utils/format.ts
    - hhs-frontend-v2/src/views/prevention/Metrics.vue

key-decisions:
  - "Preserved 100% backward-compatible function signatures for getMetricDisplayName, getMetricLabel, and getMetricUnit (D-03)"
  - "Eliminated private duplicate dictionary getMetricLabelLocal in prevention/Metrics.vue, delegating to the unified formatter (D-04)"
  - "Implemented formatUnknownKey fallback in format.ts so unknown metrics render as clean Title Case words (e.g. vo2_max -> Vo2 Max) with no raw code or underscores (D-05)"

patterns-established:
  - "Unified UI Metric Localization: All components consuming metric labels and units seamlessly benefit from normalizer and fallback metadata without template rewrites"

requirements-completed:
  - KEY-03

coverage:
  - id: D1
    description: "getMetricDisplayName and getMetricLabel return accurate Chinese names across all 23 metrics and their aliases"
    requirement: "KEY-03"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/format.spec.ts#should correctly format all 16 HEALTH canonical metrics"
        status: pass
    human_judgment: false
  - id: D2
    description: "getMetricUnit returns accurate measurement units across all 23 metrics and their aliases"
    requirement: "KEY-03"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/format.spec.ts#should return correct units for canonical keys"
        status: pass
    human_judgment: false
  - id: D3
    description: "Unknown/custom metric keys are transformed into Title Case phrases with zero underscores exposed"
    requirement: "KEY-03"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/format.spec.ts#should convert unknown keys to Title Case words without underscores"
        status: pass
    human_judgment: false
---

# Phase 05 Plan 02 Summary: 全站格式化防英文外露与组件硬编码清理

## 交付概述

本计划完成了全站指标标签与单位格式化的全面重构与防外露加固：
1. **重构 `src/utils/format.ts` (per D-03, D-05):**
   - 保持既有 `getMetricDisplayName`、`getMetricLabel` 与 `getMetricUnit` 导出签名 100% 兼容；
   - 内部透明调用 `toCanonicalKey` 归一化输入，优先从 `FALLBACK_METRIC_META` 读取权威中文名称与标准单位；
   - 保留 `LEGACY_UPPERCASE_MAP` 与 `LEGACY_UPPERCASE_UNIT_MAP` 作为极个别大写下划线历史配置的兼容保底；
   - 对未收录的全新指标调用 `formatUnknownKey` 进行可读单词化转换（例如 `vo2_max` 转换为 `Vo2 Max`），彻底消灭了界面中原始下划线与代码直接外露的隐患。
2. **清理 `src/views/prevention/Metrics.vue` (per D-04):**
   - 移除组件内部的私有硬编码函数 `getMetricLabelLocal`；
   - 统一导入并使用 `@/utils/format` 导出的全局 `getMetricLabel`，全站格式化收敛闭环。
3. **全量格式化测试套件 (`src/__tests__/utils/format.spec.ts`):**
   - 编写涵盖 23 项标准指标、下划线别名、医学缩写（`sbp`, `dbp`, `spo2`, `temp`, `glu` 等）、大写下划线、未知 Key 降级以及空值安全的 13 个独立测试场景；
   - 13/13 测试全绿通过，前端全量 36 项测试 100% 通过，生产打包编译零报错。

## 验证结论

- `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/utils/format.spec.ts`: 13 个用例全部通过。
- `cd hhs-frontend-v2 && npm run test:run`: 全量 36 个单元测试全部通过。
- `cd hhs-frontend-v2 && npm run build`: 生产构建（TypeScript 类型检查 + Vite 打包）成功完成。
