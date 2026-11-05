---
phase: "09"
slug: "wellness"
status: ready
nyquist_compliant: true
wave_0_complete: false
created: "2026-11-05"
---

# Phase 09 — Validation Strategy: 保健指标管理页录入对齐

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 4.1.7 + @vue/test-utils 2.4.6 |
| **Config file** | `hhs-frontend-v2/vitest.config.ts` |
| **Quick run command** | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/PreventionAndWellness.spec.ts` |
| **Full suite command** | `cd hhs-frontend-v2 && npm run test:run && npm run build` |
| **Estimated runtime** | ~6 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/PreventionAndWellness.spec.ts`
- **After every plan wave:** Run `cd hhs-frontend-v2 && npm run test:run && npm run build`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 09-01-01 | 01 | 1 | WELL-01, WELL-03 | T-09-01 | PANIC_LIMITS 扩充与 prevention/Metrics 动态元数据及四梯度接入 | unit/component | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/utils/metricValidation.spec.ts` | ✅ | ⬜ pending |
| 09-01-02 | 01 | 1 | WELL-01, WELL-02 | T-09-02 | wellness/Dashboard 动态元数据、心情星级交互与全局平铺优化 | component/build | `cd hhs-frontend-v2 && npm run build` | ✅ | ⬜ pending |
| 09-02-01 | 02 | 2 | WELL-01..03 | T-09-01..03 | 两大保健管理视图全流程自动化集成测试覆盖与断言 | integration | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/PreventionAndWellness.spec.ts` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `hhs-frontend-v2/src/__tests__/views/PreventionAndWellness.spec.ts` — 覆盖 prevention/Metrics 与 wellness/Dashboard 的集成测试套件

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 保健指标管理页与仪表盘弹窗全宽平铺与星级评定视觉体验 | WELL-02 | CSS 动画平滑度、星级高亮渐变及视觉美感依赖真实浏览器人眼观察 | 打开预防保健页与生活方式仪表盘，测试心情星级悬停变色与输入框置顶排版是否美观工整 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 15s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending 2026-11-05
