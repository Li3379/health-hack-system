---
phase: "08"
slug: "health"
status: ready
nyquist_compliant: true
wave_0_complete: false
created: "2026-11-05"
---

# Phase 08 — Validation Strategy: 健康指标管理页录入与编辑对齐

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 4.1.7 + @vue/test-utils 2.4.6 |
| **Config file** | `hhs-frontend-v2/vitest.config.ts` |
| **Quick run command** | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/Metrics.spec.ts` |
| **Full suite command** | `cd hhs-frontend-v2 && npm run test:run && npm run build` |
| **Estimated runtime** | ~6 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/Metrics.spec.ts`
- **After every plan wave:** Run `cd hhs-frontend-v2 && npm run test:run && npm run build`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 1 | HEALTH-01 | T-08-01 | 筛选栏和对话框动态渲染 16 项指标且无硬编码字典 | unit/component | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/Metrics.spec.ts` | ❌ W0 | ⬜ pending |
| 08-01-02 | 01 | 1 | HEALTH-02 | T-08-02 | 新增时血压双字段协同对调与编辑单项适配 | unit/component | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/Metrics.spec.ts` | ❌ W0 | ⬜ pending |
| 08-02-01 | 02 | 2 | HEALTH-03 | T-08-03 | 四梯度宽容放行、危急值确认与失焦非破坏性保留 | unit/component | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/Metrics.spec.ts` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `hhs-frontend-v2/src/__tests__/views/Metrics.spec.ts` — 覆盖 HEALTH-01, HEALTH-02, HEALTH-03 的完整页面测试套件

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 健康指标管理页弹窗平铺与长表格响应式布局 | HEALTH-01 | CSS 动画平滑度与设计视觉美感依赖真实浏览器人眼观察 | 打开健康指标管理页，测试添加指标弹窗全屏遮罩与输入框置顶标签排版是否工整 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 15s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending 2026-11-05
