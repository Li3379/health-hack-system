---
phase: "06"
slug: "input"
status: ready
nyquist_compliant: true
wave_0_complete: false
created: "2026-11-04"
---

# Phase 06 — Validation Strategy: 前端非破坏性录入引导、四梯度宽容校验与友好反馈

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 4.1.7 + @vue/test-utils 2.4.6 |
| **Config file** | `hhs-frontend-v2/vitest.config.ts` |
| **Quick run command** | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/utils/metricValidation.spec.ts` |
| **Full suite command** | `cd hhs-frontend-v2 && npm run test:run` |
| **Estimated runtime** | ~5 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/utils/metricValidation.spec.ts`
- **After every plan wave:** Run `cd hhs-frontend-v2 && npm run test:run && npm run build`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 06-01-01 | 01 | 1 | VAL-02, VAL-03 | T-06-01 | 四梯度模型准确判定正常/预警/危急/超限 | unit | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/utils/metricValidation.spec.ts` | ❌ W0 | ⬜ pending |
| 06-01-02 | 01 | 1 | VAL-04, GUIDE-03 | T-06-02 | 血压倒置检测与原子一键对调数据互换 | unit | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/composables/useMetricValidation.spec.ts` | ❌ W0 | ⬜ pending |
| 06-02-01 | 02 | 2 | GUIDE-01, GUIDE-02 | T-06-03 | 动态绑定占位符、精度步长及解读卡片 | component | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/components/ReferenceGuideCard.spec.ts` | ❌ W0 | ⬜ pending |
| 06-02-02 | 02 | 2 | VAL-01, VAL-05 | T-06-04 | 失焦原值保留与危急值二次确认弹窗流程 | integration | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/QuickInputDialog.spec.ts` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `hhs-frontend-v2/src/__tests__/utils/metricValidation.spec.ts` — stubs for VAL-02, VAL-03
- [ ] `hhs-frontend-v2/src/__tests__/composables/useMetricValidation.spec.ts` — stubs for VAL-04, GUIDE-03
- [ ] `hhs-frontend-v2/src/__tests__/components/ReferenceGuideCard.spec.ts` — stubs for GUIDE-02
- [ ] `hhs-frontend-v2/src/__tests__/views/QuickInputDialog.spec.ts` — stubs for VAL-01, VAL-05, GUIDE-01

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 快速录入弹窗视觉平滑动画与卡片渐变体验 | GUIDE-02 | CSS 动画平滑度与设计视觉美感依赖人眼主观感知 | 打开录入弹窗，观察参考卡片淡入与状态胶囊颜色切换是否自然流畅 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 15s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending 2026-11-04
