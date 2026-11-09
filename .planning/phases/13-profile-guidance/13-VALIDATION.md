---
phase: "13"
slug: "profile-guidance"
status: draft
nyquist_compliant: true
wave_0_complete: false
created: "2026-11-09"
---

# Phase 13 — Validation Strategy

> 健康档案完善引导（非阻塞）的逐需求验证契约。
> 来源：`13-RESEARCH.md` §Validation Architecture。

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (backend)** | JUnit 5 + Mockito（`spring-boot-starter-test`）；Surefire 3.0.0；Failsafe + Testcontainers 1.19.3 |
| **Framework (frontend)** | Vitest 4.1.7 + @vue/test-utils（`happy-dom`, `globals: true`） |
| **Config file** | `hhs-backend/pom.xml:301-314`；`hhs-frontend-v2/vitest.config.ts` |
| **Quick run (backend)** | `mvn -f hhs-backend/pom.xml test -Dtest=ProfileCompletenessCalculatorTest` |
| **Quick run (frontend)** | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/components/ProfileCompleteness.spec.ts` |
| **Full suite (backend)** | `mvn -f hhs-backend/pom.xml clean verify -DskipITs` |
| **Full suite (frontend)** | `npm --prefix hhs-frontend-v2 run test:run` |
| **Lint / 类型** | `mvn -f hhs-backend/pom.xml checkstyle:check` + `npm --prefix hhs-frontend-v2 run lint:check` + `npm --prefix hhs-frontend-v2 run build` |
| **Baseline（2026-11-09 实测）** | 前端 29 files / **263 tests** 全绿；后端 `HealthScoreServiceTest` 退出码 0 |
| **Estimated runtime** | 前端单 spec ~8s；前端全量 ~12s；后端单类 ~20s；后端全量 ~90s |

> 命名纪律：`*IntegrationTest` 由 Failsafe 拾取，**不会**被 Surefire 执行。新增 MVC slice 测试用 `@WebMvcTest` + `*Test` 命名，留在 Surefire。

---

## Sampling Rate

- **After every task commit:** 该任务所属语言的单类 / 单 spec 命令
- **After every plan wave:** `mvn -f hhs-backend/pom.xml clean verify -DskipITs` + `npm --prefix hhs-frontend-v2 run test:run`
- **Before `/gsd:verify-work`:** 后端全量单测 + 前端全量（≥263 项全绿）+ `checkstyle:check` + `lint:check` + `build` 全通过
- **Max feedback latency:** 90 秒

---

## Per-Task Verification Map

| Req ID | Behavior | Test Type | Automated Command | File Exists | 预期失败信号 | Status |
|--------|----------|-----------|-------------------|-------------|-------------|--------|
| PRF-01 | 加权完整度：9 字段、三等级边界（59/60/84/85）、空档案 0%、全填 100% | unit (backend) | `mvn … -Dtest=ProfileCompletenessCalculatorTest` | ❌ W0 | `expected: <0> but was: <70>`（当前 ProfileScorer 返回 70） | ⬜ |
| PRF-01 | 完整度端点返回 `fields[]` 长度 9 且 `totalCount == fields.size()` | MVC slice | `mvn … -Dtest=ProfileCompletenessControllerTest` | ❌ W0 | `Status expected:<200> but was:<404>` | ⬜ |
| PRF-01 | store 单源、404 合成 0%、加载失败保持 null（不画 0%） | unit (frontend) | `npm … -- src/__tests__/stores/profileCompleteness.spec.ts` | ❌ W0 | store 未实现 → import 失败 | ⬜ |
| PRF-01 | `ProfileCompleteness.vue` 分段数 = `fields.length`；图例 ✓/○ + 读屏文本 | unit (component) | `npm … -- src/__tests__/components/ProfileCompleteness.spec.ts` | ❌ W0 | 分段数硬编码为 8 而非 9 | ⬜ |
| PRF-02 | 9 个字段 `reason` 非空且与消费方一致（birthDate→年龄、bmi→22.0、familyHistory→糖尿病） | unit (backend) | `mvn … -Dtest=ProfileFieldCatalogTest` | ❌ W0 | 某字段 reason 为空或缺消费方关键词 | ⬜ |
| PRF-02 | 档案页渲染 9 条 `.field-hint`，文案来自后端而非硬编码 | unit (component) | `npm … -- src/__tests__/views/ProfileCompleteness.spec.ts` | ❌ W0 | 渲染 0 条或文案为前端常量 | ⬜ |
| PRF-03 | 三档 `importance` 与 `.hhs-imp-badge--*` 类映射正确；承诺句**只出现一次** | unit (component) | 同上 | ❌ W0 | 承诺句出现 9 次 | ⬜ |
| PRF-04 | 横幅文案点名缺失字段 + 下游后果；`score>=60` 不渲染 | unit (component) | `npm … -- src/__tests__/components/ProfileReminderBanner.spec.ts` | ❌ W0 | score=60 仍渲染 | ⬜ |
| PRF-05 | `profile == null` → 1 条假设 + 固定分 30/25/25；`bmi==null` → 「按 22.0 估算」 | unit (backend) | `mvn … -Dtest=RiskAssessmentAssumptionsTest` | ❌ W0 | `expected size 1 but was 0` | ⬜ |
| PRF-05 | `ScoreCalculator` 汇总的 `assumptions` 与各 scorer 实际默认代入一致 | unit (backend) | `mvn … -Dtest=ScoreCalculatorAssumptionsTest` | ❌ W0 | `assumptions` 为 null → NPE | ⬜ |
| PRF-05 | **旧签名 `calculate(Long)` 行为不变**（既有 19 个 domain 测试必须保持绿） | unit (backend) | `mvn … -Dtest=WellnessScorerTest,ScreeningScorerTest` | ✅ 存在 | 改签名的实现 → 19 用例全崩 | ⬜ |
| PRF-06 | 徽标仅当 `completeness != null && < 100`；`null` 不渲染（绝不画 0%） | unit (component) | `npm … -- src/__tests__/components/EstimateBadge.spec.ts` | ❌ W0 | null 时仍渲染 | ⬜ |
| PRF-06 | 点击徽标 → `router.push({path:'/prevention/profile', query:{focus}})` | unit (component) | 同上 | ❌ W0 | 未带 query.focus | ⬜ |
| PRF-07 | 关闭后 7 天内不渲染；第 7 天+1ms 恢复；`dismissedAt` 非法值视为未静默 | unit (fake timers) | `npm … -- src/__tests__/stores/profileCompleteness.spec.ts` | ❌ W0 | 第 8 天仍渲染 → `expected false but was true` | ⬜ |
| PRF-07 | 全站单实例：仅 `dashboard/Index.vue` 渲染横幅 | unit (component) | `npm … -- src/__tests__/views/DashboardProfileBanner.spec.ts` | ❌ W0 | 多页同时渲染 | ⬜ |
| PRF-08 | 判定顺序：假设为空 → 直接创建（面板不展开）；假设非空且 `<100` → 展开不调 API | unit (component) | `npm … -- src/__tests__/views/RiskBackfill.spec.ts` | ❌ W0 | 面板无条件展开 | ⬜ |
| PRF-08 | 「直接创建（按估算）」「放弃创建」均可用；保存失败不清空输入 | unit (component) | 同上 | ❌ W0 | 按钮禁用或输入被清空 | ⬜ |
| 回归 | `ProfileErrorSample.spec.ts` 3 个既有用例保持绿（需补 `vue-router` 与 `getCompleteness` mock） | unit (component) | `npm … -- src/__tests__/views/ProfileErrorSample.spec.ts` | ✅ 存在（需改 mock） | `TypeError: Cannot read properties of undefined (reading 'query')` | ⬜ |
| 回归 | `errorMessages.spec.ts` 18 键精确断言不被破坏 | unit (frontend) | `npm … -- src/__tests__/utils/errorMessages.spec.ts` | ✅ 存在（不得加键） | 加键 → 精确相等断言失败 | ⬜ |
| 回归 | `HealthScoreServiceTest` 12 用例保持绿（VO 加字段不影响） | unit (backend) | `mvn … -Dtest=HealthScoreServiceTest` | ✅ 存在 | 序列化断言失败 | ⬜ |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

必须在实现前先建立（并看到上表的预期失败信号）：

- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ProfileCompletenessCalculatorTest.java` — PRF-01（9 字段、等级边界 59/60/84/85、空档案 0、全填 100、陈旧 bmi 免疫）
- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ProfileFieldCatalogTest.java` — PRF-02（9 条 reason 非空且含真实消费方关键词）
- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ScoreCalculatorAssumptionsTest.java` — PRF-05（collector 汇总正确、旧签名行为不变）
- [ ] `hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java` — PRF-05（`profile==null` / `bmi==null` / 缺家族史三条路径）
- [ ] `hhs-backend/src/test/java/com/hhs/controller/ProfileCompletenessControllerTest.java` — PRF-01（`@WebMvcTest` slice，断言 `fields.length==9`）
- [ ] `hhs-frontend-v2/src/__tests__/stores/profileCompleteness.spec.ts` — PRF-01/07（单源、404 合成、失败保持 null、7 天 fake timers）
- [ ] `hhs-frontend-v2/src/__tests__/components/ProfileCompleteness.spec.ts` — PRF-01（分段数 = `fields.length`、图例可访问性）
- [ ] `hhs-frontend-v2/src/__tests__/components/ProfileReminderBanner.spec.ts` — PRF-04/07
- [ ] `hhs-frontend-v2/src/__tests__/components/EstimateBadge.spec.ts` — PRF-06（null 不渲染、点击带 focus）
- [ ] `hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts` — PRF-08（判定顺序三序）
- [ ] `hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts` — PRF-07（全站单实例）
- [ ] 修改 `hhs-frontend-v2/src/__tests__/views/ProfileErrorSample.spec.ts` — 补 `vi.mock('vue-router')` 与 `getCompleteness` mock（否则既有 3 用例转红）
- [ ] 框架安装：**无需** —— 两端测试基建均已就绪

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 徽标与风险等级 `el-tag` 在同一视区的视觉共存 | PRF-06 | 间距/语汇/行高隔离的实际观感需人眼判断 | 在风险页同时触发两条（档案不完整 + 高风险），确认两个徽标不混淆、最小 16px 行距 |
| 三档重要度徽标的形状冗余在低视力下可辨 | PRF-03 | 形状差异（实心/实线/虚线）的可辨性需真实人眼 | 切浅色/深色主题各看一遍；确认文字标签是唯一语义载体（1.4.1 由文字满足） |
| 仪表盘横幅在真实滚动位置不遮挡内容 | PRF-07 | 布局上下文依赖真实视口高度 | 在 1080p 与 768p 视口各看一次，确认横幅不压住 HealthScoreCircle |
| 7 天静默的真实跨会话行为 | PRF-07 | localStorage 跨浏览器重启的持久性 | 关闭横幅 → 关闭浏览器 → 重开 → 确认 7 天内不再出现；手动改时间戳到 8 天前 → 确认恢复 |
| 风险评估就地补填的完整交互流 | PRF-08 | 表单聚焦、提交、失败保留输入的整体手感 | 无档案账号创建评估：确认面板先展开假设列表 → 「直接创建」可跳过 → 补填后重算假设 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 90s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-11-09（research 阶段收口）