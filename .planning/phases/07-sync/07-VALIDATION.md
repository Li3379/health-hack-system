---
phase: "07"
slug: "sync"
status: draft
nyquist_compliant: true
wave_0_complete: false
created: "2026-11-05"
---

# Phase 07 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (5.10.1) + Mockito (5.7.0) / Vitest (4.1.7) + @vue/test-utils (2.4.4) |
| **Config file** | `hhs-backend/pom.xml` / `hhs-frontend-v2/vite.config.ts` |
| **Quick run command** | `cd hhs-backend && mvn test -Dtest=AiParseServiceTest,OcrServiceTest,MetricRecordValidatorTest -Dcheckstyle.skip=true` / `cd hhs-frontend-v2 && npm run test:run` |
| **Full suite command** | `cd hhs-backend && mvn clean verify -DskipITs && cd ../hhs-frontend-v2 && npm run test:run` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run backend quick test or frontend vitest
- **After every plan wave:** Run full test suite for modified modules
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 07-01-01 | 01 | 1 | D-05 (HEALTH) | T-07-01 | 血压双字段协同与一键对调，输入异常不抹除原值 | unit | `cd hhs-frontend-v2 && npm run test:run -- HealthMetrics.spec.ts` | ❌ W0 | ⬜ pending |
| 07-01-02 | 01 | 1 | D-05 (WELL) | T-07-01 | 保健生活指标元数据绑定与心情星级打分 | unit | `cd hhs-frontend-v2 && npm run test:run -- WellnessMetrics.spec.ts` | ❌ W0 | ⬜ pending |
| 07-02-01 | 02 | 2 | SYNC-01 | T-07-02 | AI 解析系统提示词动态消费元数据注册表，废除静态表格 | unit | `cd hhs-backend && mvn test -Dtest=AiParseServiceTest#testDynamicPromptGeneration -Dcheckstyle.skip=true` | ❌ W0 | ⬜ pending |
| 07-02-02 | 02 | 2 | SYNC-01 | T-07-02 | OCR 识别消费注册表与化验单血常规扩展字典 | unit | `cd hhs-backend && mvn test -Dtest=OcrServiceTest#testRecognizeReportWithMetaRegistry -Dcheckstyle.skip=true` | ❌ W0 | ⬜ pending |
| 07-02-03 | 02 | 2 | SYNC-01 | T-07-03 | 全渠道入库 DTO 统一经由 @ValidMetricRecord 拦截不可能物理极限 | unit | `cd hhs-backend && mvn test -Dtest=MetricRecordValidatorTest#testMultiDtoPhysicalLimitInterception -Dcheckstyle.skip=true` | ❌ W0 | ⬜ pending |
| 07-03-01 | 03 | 3 | SYNC-01 | T-07-04 | 手动、AI提取、OCR确认三端产出 Key 与单位 100% 规则对齐 | integration | `cd hhs-backend && mvn test -Dtest=CrossChannelConsistencyTest -Dcheckstyle.skip=true` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `hhs-frontend-v2/src/__tests__/views/HealthMetrics.spec.ts` — 验证 `health/Metrics.vue` 弹窗动态元数据与血压对调
- [ ] `hhs-frontend-v2/src/__tests__/views/WellnessMetrics.spec.ts` — 验证 `prevention/Metrics.vue` 与 `wellness/Dashboard.vue` 弹窗对齐
- [ ] `hhs-backend/src/test/java/com/hhs/service/CrossChannelConsistencyTest.java` — 验证手动、AI、OCR 三渠道 Key 与单位 100% 对齐

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 真实体检报告图片上传与文字识别 | SYNC-01 | 需要百度 OCR 外部 API 与真实图像 | 在前端数据录入页上传真实化验单图像，确认识别解析表格与参考范围正确展示 |
| 大模型对话真实生成健康建议 | SYNC-01 | 需要 DashScope 外部大模型网络调用 | 在前端输入“今天血压 140/90，空腹血糖 7.2”，确认 AI 提取指标项及状态标签 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-11-05
