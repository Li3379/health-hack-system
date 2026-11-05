---
phase: "10"
slug: "universal"
status: ready
nyquist_compliant: true
wave_0_complete: false
created: "2026-11-05"
---

# Phase 10 — Validation Strategy: 指标录入通用交互组件封装与全站验证

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 4.1.7 + @vue/test-utils 2.4.6 |
| **Config file** | `hhs-frontend-v2/vitest.config.ts` |
| **Quick run command** | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/components/UniversalMetricDialog.spec.ts` |
| **Full suite command** | `cd hhs-frontend-v2 && npm run test:run && npm run build` |
| **Estimated runtime** | ~8 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/components/UniversalMetricDialog.spec.ts`
- **After every plan wave:** Run `cd hhs-frontend-v2 && npm run test:run && npm run build`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 10-01-01 | 01 | 1 | REUSE-01, REUSE-02 | T-10-01 | 通用组件 UniversalMetricDialog 封装及 10 维全量测试用例覆盖 | unit/component | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/components/UniversalMetricDialog.spec.ts` | ❌ W0 | ⬜ pending |
| 10-01-02 | 01 | 1 | REUSE-01 | T-10-02 | QuickInputDialog 保持向前兼容重导出与 QuickInput 替换 | component/build | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/QuickInputDialog.spec.ts` | ✅ | ⬜ pending |
| 10-02-01 | 02 | 2 | REUSE-01, REUSE-02 | T-10-03 | health/Metrics、prevention/Metrics、wellness/Dashboard 三大页面全面收敛接入 | component/build | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/Metrics.spec.ts src/__tests__/views/PreventionAndWellness.spec.ts` | ✅ | ⬜ pending |
| 10-02-02 | 02 | 2 | REUSE-01, REUSE-02 | T-10-01..03 | 全站录入全链路回归测试与生产构建零报错审计 | integration | `cd hhs-frontend-v2 && npm run test:run && npm run build` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `hhs-frontend-v2/src/__tests__/components/UniversalMetricDialog.spec.ts` — 覆盖 REUSE-01, REUSE-02 的通用组件 10 维全量测试套件

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 全站四大录入入口统一平铺与交互一致性视觉审查 | REUSE-01 | 动画平滑度、弹窗居中全宽排版与各页面视觉一致性依赖真实浏览器人眼观察 | 分别从快捷录入、健康管理、预防保健、生活方式仪表盘打开弹窗，核验排版、四梯度胶囊与遮罩是否高度一致 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 15s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending 2026-11-05
