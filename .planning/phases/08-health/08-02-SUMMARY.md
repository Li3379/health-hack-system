---
phase: 08-health
plan: 02
subsystem: frontend
tags: [metrics, validation, four-tier, dialog, vitest]

# Dependency graph
requires:
  - 08-01
provides:
  - Four-tier clinical validation integration in src/views/health/Metrics.vue
  - ReferenceGuideCard and CriticalConfirmDialog mounting
  - append-to-body and label-position="top" layout optimization
  - Comprehensive integration test suite in src/__tests__/views/Metrics.spec.ts
affects: [09-wellness, 10-universal]

# Actuals
actuals:
  tokens: 26000
  tasks: 2
  commits: 1
  plan_head_before: 51664b182bf97217db5204481c00f7e452a26569

# Tech tracking
tech-stack:
  added: []
  patterns: [Four-tier non-blocking form validation, Panic Limits modal confirmation, global dialog layering with append-to-body]

key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/Metrics.spec.ts
  modified:
    - hhs-frontend-v2/src/views/health/Metrics.vue

key-decisions:
  - "Integrated useMetricValidation into Metrics.vue with non-destructive retention, allowing Tier 1 and Tier 2 warning values to be saved directly (HEALTH-03, D-03)"
  - "Mounted CriticalConfirmDialog to intercept Tier 3 Panic Limits before saving, providing 120 emergency guidelines while permitting final save upon user confirmation (HEALTH-03, D-03)"
  - "Appended el-dialog to body with append-to-body to escape GSAP transform containing blocks, and enabled label-position='top' for unclipped placeholder visibility (D-04)"
  - "Constructed complete Vitest integration test suite Metrics.spec.ts verifying HEALTH-01, HEALTH-02, and HEALTH-03 across all four tiers and dual-field modes"

patterns-established:
  - "Full Clinical Alignment on Management Views: Main management views now offer identical four-tier reassurance, reference guidance, and panic confirmation to the quick-input modal"

requirements-completed:
  - HEALTH-03

coverage:
  - id: D1
    description: "Four-tier non-destructive validation and critical confirmation dialog in Metrics.vue"
    requirement: "HEALTH-03"
    verification:
      - kind: component
        ref: "hhs-frontend-v2/src/__tests__/views/Metrics.spec.ts#HEALTH-03: should non-destructively validate Tier 2, Tier 3, and Tier 4 values without wiping user inputs"
        status: pass
    human_judgment: false
  - id: D2
    description: "Single-metric edit mode locks selection and isolates historical updates"
    requirement: "HEALTH-02"
    verification:
      - kind: component
        ref: "hhs-frontend-v2/src/__tests__/views/Metrics.spec.ts#HEALTH-02: should lock metricKey and disable collaboration on edit mode for single historical records"
        status: pass
    human_judgment: false
---

# Phase 08 Plan 02 Summary: 四梯度校验、危急弹窗、全局平铺与页面单测

## 交付概述

本计划完成了 `health/Metrics.vue` 医学校验引擎、参考卡片、危急确认窗及全局平铺的全面接入：
1. **四梯度医学校验引擎与非破坏性输入保留 (HEALTH-03, D-03):**
   - 彻底废除失焦清空输入框反模式，用户键入的数值 100% 忠实保留在表单中；
   - Tier 1（正常）与 Tier 2（预警偏差）在表单验证器中直接放行通过，允许真实病患数据顺利入库；
   - Tier 3（急危重症）触碰 Panic Limits 时，点击保存弹出 `CriticalConfirmDialog` 模态窗，提供 120 急救指引与“确认录入无误并保存”二次确认；
   - Tier 4（存活极限）拦截表单提交并展示结构化指引，但不清空输入框文本。
2. **表现层挂载与全局平铺优化 (D-04):**
   - 对话框添加 `append-to-body`，使其 DOM 脱离外层卡片的 GSAP transform 动画层叠上下文，居中平铺全屏视口；
   - 表单启用 `label-position="top"` 顶置排版，彻底释放输入框横向宽度；
   - 表单底部常态化挂载 `ReferenceGuideCard.vue` 临床解读卡片。
3. **全覆盖页面集成测试套件 (`Metrics.spec.ts`):**
   - 编写涵盖全量 16 项元数据动态下拉、单项与协同模式切换、血压倒置检测与原子对调、四梯度分流与危急值确认的 4 个场景测试；
   - 4/4 用例全绿通过，前端全量 9 个测试套件、68 个单元与集成测试用例 100% 通过，生产打包构建 0 报错。

## 验证结论

- `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/Metrics.spec.ts`: 4 passed.
- `cd hhs-frontend-v2 && npm run test:run`: 68 passed.
- `cd hhs-frontend-v2 && npm run build`: 0 errors.
