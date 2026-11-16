---
phase: 06-input
plan: 02
subsystem: frontend
tags: [input-dialog, non-destructive, guide-card, critical-confirm, vitest, element-plus]

# Dependency graph
requires:
  - 06-01
provides:
  - Non-destructive QuickInputDialog retaining user input without blur-wipeout in src/views/data-input/components/QuickInputDialog.vue
  - ReferenceGuideCard constant clinical interpretation component in src/views/data-input/components/ReferenceGuideCard.vue
  - CriticalConfirmDialog emergency alert & reassurance modal in src/views/data-input/components/CriticalConfirmDialog.vue
  - Dual-field coordinated blood pressure grid input with inline inversion alert and atomic swap
  - Integration test suite in src/__tests__/views/QuickInputDialog.spec.ts and src/__tests__/components/ReferenceGuideCard.spec.ts
affects: [07-ai]

# Actuals
actuals:
  tokens: 28000
  tasks: 2
  commits: 1
  plan_head_before: 0c2a91176b50e26d91f211516e8775267f5b7aa2

# Tech tracking
tech-stack:
  added: []
  patterns: [Non-destructive input preservation, four-tier non-blocking form validation, modal confirmation for panic values, dual-field grid alignment]

key-files:
  created:
    - hhs-frontend-v2/src/views/data-input/components/ReferenceGuideCard.vue
    - hhs-frontend-v2/src/views/data-input/components/CriticalConfirmDialog.vue
    - hhs-frontend-v2/src/__tests__/components/ReferenceGuideCard.spec.ts
    - hhs-frontend-v2/src/__tests__/views/QuickInputDialog.spec.ts
  modified:
    - hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue

key-decisions:
  - "Completely eliminated blur wipeout logic (removed handleValueBlur and handleDiastolicBlur assignments to undefined) ensuring user input is 100% preserved (VAL-01, D-01)"
  - "Integrated Pinia useMetricMetaStore to dynamically drive input precision, step size, and normal range placeholder strings (GUIDE-01, D-03)"
  - "Mounted ReferenceGuideCard underneath the form to consistently display clinical baseline references and measurement instructions (GUIDE-02, D-03)"
  - "Arranged blood pressure fields into a side-by-side grid, dynamically showing an inline warning with atomic one-click swap when inverted (GUIDE-03, VAL-04, D-04)"
  - "Triggered CriticalConfirmDialog upon saving values in the Panic Limits zone, providing reassurance and first-aid instructions while permitting final save upon user confirmation (VAL-05, D-05)"

patterns-established:
  - "Non-Destructive User Experience: Erroneous or extreme values are reflected in status badges rather than destroying entered text"
  - "Two-Tier Safety Gate: Form validation passes for warnings and critical values, delegating panic-level verification to user-friendly confirmation dialogs"

requirements-completed:
  - GUIDE-01
  - GUIDE-02
  - VAL-01
  - VAL-05

coverage:
  - id: D1
    description: "User input is never wiped on blur even when outside physiological ranges"
    requirement: "VAL-01"
    verification:
      - kind: component
        ref: "hhs-frontend-v2/src/__tests__/views/QuickInputDialog.spec.ts#VAL-01: should non-destructively preserve user input even when value is outside normal bounds"
        status: pass
    human_judgment: false
  - id: D2
    description: "Metadata dynamically binds placeholder, step, and precision"
    requirement: "GUIDE-01"
    verification:
      - kind: component
        ref: "hhs-frontend-v2/src/__tests__/views/QuickInputDialog.spec.ts#GUIDE-01 & VAL-02: should bind metadata and evaluate Tier 2 warning without blocking save"
        status: pass
    human_judgment: false
  - id: D3
    description: "Reference guide card persistently displays baseline ranges and guidelines"
    requirement: "GUIDE-02"
    verification:
      - kind: component
        ref: "hhs-frontend-v2/src/__tests__/components/ReferenceGuideCard.spec.ts#should render single metric reference range and guide hint correctly"
        status: pass
      - kind: component
        ref: "hhs-frontend-v2/src/__tests__/components/ReferenceGuideCard.spec.ts#should render dual systolic and diastolic reference ranges for blood pressure"
        status: pass
    human_judgment: false
  - id: D4
    description: "Critical panic values trigger emergency confirmation dialog and permit final save"
    requirement: "VAL-05"
    verification:
      - kind: component
        ref: "hhs-frontend-v2/src/__tests__/views/QuickInputDialog.spec.ts#VAL-05: should trigger CriticalConfirmDialog when entering panic limits and save upon confirmation"
        status: pass
    human_judgment: false
---

# Phase 06 Plan 02 Summary: 表现层组件与 QuickInputDialog 全面重构

## 交付概述

本计划完成了健康指标录入体验的全面重塑与交付：
1. **彻底废除失焦抹除机制 (VAL-01, D-01):**
   - 彻底删除 `QuickInputDialog.vue` 中的 `handleValueBlur` 与 `handleDiastolicBlur`；
   - 移除任何 `value = undefined` 破坏性清空赋值，用户键入的数值 100% 忠实保留在输入框内。
2. **元数据驱动与占位符引导 (GUIDE-01, D-03):**
   - 彻底废除本地硬编码的 `METRIC_RANGES`，全量接入 `useMetricMetaStore` 权威字典；
   - 动态绑定 `:precision="definition?.precision ?? 0"` 与 `:step="definition?.step ?? 1"`；
   - 移除 `el-input-number` 紧凑边界截断，动态渲染 `:placeholder="参考范围: ${min} ~ ${max} ${unit}"`。
3. **常态化解读卡片组件 (`ReferenceGuideCard.vue`, GUIDE-02):**
   - 挂载于表单下方，展示指标正常参考范围、标准单位以及 `guideHint` 临床测量建议；
   - 针对血压场景支持高低压双参考区间并列排版。
4. **血压同屏网格协同与一键对调 (GUIDE-03, VAL-04, D-04):**
   - 采用 12/12 栅格并排展示收缩压与舒张压；
   - 检测到收缩压 <= 舒张压倒置时，实时弹出黄色告警条，内嵌「对调高低压数值」原子交换操作，一键清除倒置阻断。
5. **四梯度宽容验证与状态胶囊 (VAL-02, VAL-03):**
   - 正常(Tier 1)、预警偏差(Tier 2)、急危重症(Tier 3)在表单验证器中全部予以放行，允许真实病理数据入库；
   - 仅物理不可能极限(Tier 4)或未纠正的血压倒置时禁用保存按钮并报错，原值依然保留。
6. **急危重症二次确认模态窗 (`CriticalConfirmDialog.vue`, VAL-05, D-05):**
   - 录入达到 Panic Limits 时，点击保存拦截直接提交并弹出二次确认模态窗；
   - 提供“返回核对修改”（主操作）与“确认录入无误并保存”（次操作）及急救处置建议，确认后顺利持久化入库。
7. **全覆盖组件与集成测试:**
   - `ReferenceGuideCard.spec.ts` 3/3 用例全绿；
   - `QuickInputDialog.spec.ts` 6/6 用例全绿；
   - 前端全套 8 个测试套件、61 个单元与集成测试用例全部通过，`npm run build` 0 报错。

## 验证结论

- `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/components/ReferenceGuideCard.spec.ts`: 3 passed.
- `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/QuickInputDialog.spec.ts`: 6 passed.
- `cd hhs-frontend-v2 && npm run test:run`: 61 passed.
- `cd hhs-frontend-v2 && npm run build`: 生产构建成功。
