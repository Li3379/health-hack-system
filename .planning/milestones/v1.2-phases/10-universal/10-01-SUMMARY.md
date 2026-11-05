---
phase: 10-universal
plan: 01
subsystem: frontend
tags: [universal-dialog, component, validation, vitest, reuse]

# Dependency graph
requires:
  - 09-01
  - 09-02
provides:
  - Universal metric input and edit dialog in src/components/metric/UniversalMetricDialog.vue
  - Fully backward-compatible thin wrapper in src/views/data-input/components/QuickInputDialog.vue
  - 10-dimensional automated test suite in src/__tests__/components/UniversalMetricDialog.spec.ts
affects: [10-02]

# Actuals
actuals:
  tokens: 28000
  tasks: 2
  commits: 1

# Tech tracking
tech-stack:
  added: []
  patterns: [Single-Source Universal Metric Dialog, Adaptive API Routing for Create & Edit, Backward-Compatible Thin Component Proxy]

key-files:
  created:
    - hhs-frontend-v2/src/components/metric/UniversalMetricDialog.vue
    - hhs-frontend-v2/src/__tests__/components/UniversalMetricDialog.spec.ts
  modified:
    - hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue
    - hhs-frontend-v2/src/views/data-input/components/QuickInput.vue

key-decisions:
  - "Encapsulated all metric input & edit capabilities into src/components/metric/UniversalMetricDialog.vue supporting category filtering ('HEALTH' | 'WELLNESS' | 'ALL') (D-01)"
  - "Implemented adaptive internal API routing: automatically routes to healthApi or wellnessApi for create and update based on authoritative definition.category (D-02, REUSE-02)"
  - "Maintained 100% backward compatibility in QuickInputDialog.vue by wrapping UniversalMetricDialog with property and method delegation (D-01, REUSE-01)"

patterns-established:
  - "Universal Metric Dialog Architecture: A single, robust dialog component encapsulating collaborative BP, 1-5 star mood rating, ReferenceGuideCard, CriticalConfirmDialog, append-to-body, and four-tier validation"

requirements-completed:
  - REUSE-01
  - REUSE-02

coverage:
  - id: D1
    description: "UniversalMetricDialog dynamically filters health (16) and wellness (7) metrics by category"
    requirement: "REUSE-01"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/UniversalMetricDialog.spec.ts#Dim 1 & Dim 2"
        status: pass
  - id: D2
    description: "Collaborative blood pressure input, inversion detection, and atomic swap"
    requirement: "REUSE-01"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/UniversalMetricDialog.spec.ts#Dim 3"
        status: pass
  - id: D3
    description: "Edit mode via initialData, locking metric key and adapting API submission"
    requirement: "REUSE-02"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/UniversalMetricDialog.spec.ts#Dim 8 & Dim 9"
        status: pass
  - id: D4
    description: "QuickInputDialog backward-compatible delegation passes all existing tests"
    requirement: "REUSE-01"
    verification:
      - kind: component
        ref: "hhs-frontend-v2/src/__tests__/views/QuickInputDialog.spec.ts"
        status: pass
---

# Phase 10-01 Summary: UniversalMetricDialog 提炼封装与向后兼容适配

完成了通用指标录入弹窗组件（`UniversalMetricDialog.vue`）的完整封装、10 维全量单元测试覆盖，并在 `QuickInputDialog.vue` 建立了向前兼容代理，确保既有快捷录入功能与测试零破坏、零回归。
