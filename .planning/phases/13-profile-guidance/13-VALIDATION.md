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
| PRF-01 | 完整度端点返回 `fields[]` 长度 9 且 `totalCount == fields.size()`；无档案时 200 而非 404 | MVC slice | `mvn … -Dtest=ProfileCompletenessControllerTest` | ❌ W0 | `Status expected:<200> but was:<404>` | ⬜ |
| PRF-01 | store 单源、404 合成 0%、加载失败保持 null（不画 0%）；`hasProfile`/`assumptions` 原样透传 | unit (frontend) | `npm … -- src/__tests__/stores/profileCompleteness.spec.ts` | ❌ W0 | store 未实现 → import 失败；404 合成缺 `assumptions` → 面板对无档案用户静默失效 | ⬜ |
| PRF-01 | `ProfileCompleteness.vue` 分段数 = `fields.length`；图例 ✓/○ + 读屏文本；影响句按 `hasProfile` 先分支 | unit (component) | `npm … -- src/__tests__/components/ProfileCompleteness.spec.ts` | ❌ W0 | 分段数硬编码为 8 而非 9；`hasProfile:false` 仍渲染「35 岁」句 | ⬜ |
| PRF-01 | 档案页保存成功后重新拉取完整度，头部/仪表/字段徽标随之后移 | unit (component) | `npm … -- src/__tests__/views/ProfileCompleteness.spec.ts` | ❌ W0 | 保存后头部仍显示保存前 40%（`completenessStore` 只在 onMounted 调用） | ⬜ |
| PRF-02 | 9 个字段 `reason` 非空且**逐字段**与真实消费方一致（birthDate→年龄、bmi→22.0、familyHistory→糖尿病、gender→AI/健康报告、lifestyleHabits→健康评分）；无一条含「体重维度」 | unit (backend) | `mvn … -Dtest=ProfileFieldCatalogTest` | ❌ W0 | 某字段 reason 为空；或 heightCm/bmi 声称「体重维度评分」；或 gender 声称「风险」 | ⬜ |
| PRF-02 | 档案页渲染 9 条 `.field-hint`，文案来自后端而非硬编码 | unit (component) | `npm … -- src/__tests__/views/ProfileCompleteness.spec.ts` | ❌ W0 | 渲染 0 条或文案为前端常量 | ⬜ |
| PRF-03 | 三档 `importance` 与 `.hhs-imp-badge--*` 类映射正确；承诺句**只出现一次** | unit (component) | 同上 | ❌ W0 | 承诺句出现 9 次 | ⬜ |
| PRF-04 | 横幅文案点名缺失字段 + 下游后果；`score>=60` 不渲染 | unit (component) | `npm … -- src/__tests__/components/ProfileReminderBanner.spec.ts` | ❌ W0 | score=60 仍渲染 | ⬜ |
| PRF-05 | `profile == null` → 1 条通用假设 + 固定分 30/25/25；`bmi==null` → 「按 22.0 估算」 | unit (backend) | `mvn … -Dtest=RiskAssessmentAssumptionsTest` | ❌ W0 | `expected size 1 but was 0`；或 null-profile 下发 3 条逐字段句 | ⬜ |
| PRF-05 | `assumptionsFor` 分支：无档案=1 条；已填但未提及糖尿病 → 「家族病史未提及糖尿病…」而非「缺少家族病史…」 | unit (backend) | `mvn … -Dtest=ProfileCompletenessCalculatorTest,ProfileFieldCatalogTest` | ❌ W0 | `父亲有高血压` 触发「缺少家族病史」 | ⬜ |
| PRF-05 | `ScoreCalculator` 汇总的 `assumptions` 与各 scorer 实际默认代入一致 | unit (backend) | `mvn … -Dtest=ScoreCalculatorAssumptionsTest` | ❌ W0 | `assumptions` 为 null → NPE | ⬜ |
| PRF-05 | **旧签名 `calculate(Long)` 行为不变**（既有 19 个 domain 测试必须保持绿） | unit (backend) | `mvn … -Dtest=WellnessScorerTest,ScreeningScorerTest` | ✅ 存在 | 改签名的实现 → 19 用例全崩 | ⬜ |
| PRF-06 | 徽标仅当 `completeness != null && < 100`；`null` 不渲染（绝不画 0%） | unit (component) | `npm … -- src/__tests__/components/EstimateBadge.spec.ts` | ❌ W0 | null 时仍渲染 | ⬜ |
| PRF-06 | 点击徽标 → `router.push({path:'/prevention/profile', query:{focus}})` | unit (component) | 同上 | ❌ W0 | 未带 query.focus | ⬜ |
| PRF-07 | 关闭后 7 天内不渲染；第 7 天+1ms 恢复；`dismissedAt` 非法值视为未静默 | unit (fake timers) | `npm … -- src/__tests__/stores/profileCompleteness.spec.ts` | ❌ W0 | 第 8 天仍渲染 → `expected false but was true` | ⬜ |
| PRF-07 | 全站单实例：仅 `dashboard/Index.vue` 渲染横幅 | unit (component) | `npm … -- src/__tests__/views/DashboardProfileBanner.spec.ts` | ❌ W0 | 多页同时渲染 | ⬜ |
| PRF-08 | **首次访问**：`onMounted` 即拉取完整度，徽标无需交互即出现；store 为空时点击先拉再判定 | unit (component) | `npm … -- src/__tests__/views/RiskBackfill.spec.ts` | ❌ W0 | mount 未调 `getCompleteness` → 徽标不出现、面板永不展开 | ⬜ |
| PRF-08 | 判定顺序：假设为空 → 直接创建（面板不展开）；假设非空且 `<100` → 展开不调 API | unit (component) | 同上 | ❌ W0 | 面板无条件展开 | ⬜ |
| PRF-08 | 面板假设列表 = `completeness.assumptions`（与结果页同源）；无档案时渲染 1 条而非 3 条 | unit (component) | 同上 | ❌ W0 | 面板列出 3 条逐字段句，结果页却只有 1 条 | ⬜ |
| PRF-08 | 「直接创建（按估算）」「放弃创建」均可用；保存失败不清空输入；身高体重可选留空不阻断保存 | unit (component) | 同上 | ❌ W0 | 按钮禁用或输入被清空；留空可选字段触发校验 | ⬜ |
| 回归 | `ProfileErrorSample.spec.ts` 3 个既有用例保持绿（需补 `vue-router` 与 `getCompleteness` mock，fixture 带 `hasProfile`/`assumptions`） | unit (component) | `npm … -- src/__tests__/views/ProfileErrorSample.spec.ts` | ✅ 存在（需改 mock） | `TypeError: Cannot read properties of undefined (reading 'query')` | ⬜ |
| 回归 | `errorMessages.spec.ts` 18 键精确断言不被破坏 | unit (frontend) | `npm … -- src/__tests__/utils/errorMessages.spec.ts` | ✅ 存在（不得加键） | 加键 → 精确相等断言失败 | ⬜ |
| 回归 | `HealthScoreServiceTest` 12 用例保持绿 + 2 条新增（NO_DATA null completeness、缓存 VO 往返） | unit (backend) | `mvn … -Dtest=HealthScoreServiceTest` | ✅ 存在（需加 2 条） | 序列化断言失败；NO_DATA 路径 `profileCompleteness` 非 null | ⬜ |
| 文案 | 重写后的 `prevention/Profile.vue` 与 `health/Score.vue` 无裸 `取消`/`确定`/`关闭` 可见按钮文本 | grep gate | `grep -Ec '>(取消\|确定\|关闭)<' hhs-frontend-v2/src/views/prevention/Profile.vue hhs-frontend-v2/src/views/health/Score.vue` | ❌ 当前命中 2 处 | 两文件各命中 1（`Profile.vue:9` / `Score.vue:198`） | ⬜ |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

必须在实现前先建立（并看到上表的预期失败信号）：

- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ProfileCompletenessCalculatorTest.java` — PRF-01（9 字段、等级边界 59/60/84/85、空档案 0、全填 100、陈旧 bmi 免疫、`assumptionsFor` 四分支）
- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ProfileFieldCatalogTest.java` — PRF-02（9 条 reason 非空且**逐字段**含各自真实消费方关键词；无「体重维度」；`mentionsDiabetes` 四例）
- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ScoreCalculatorAssumptionsTest.java` — PRF-05（collector 汇总正确、旧签名行为不变）
- [ ] `hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java` — PRF-05（`profile==null` / `bmi==null` / 家族史为空 / 家族史已填但未提糖尿病 四条路径 + 读时重算 + 与 `assumptionsFor` 的等值断言）
- [ ] `hhs-backend/src/test/java/com/hhs/controller/ProfileCompletenessControllerTest.java` — PRF-01（`@WebMvcTest` slice，断言 `fields.length==9`）
- [ ] `hhs-backend/src/test/java/com/hhs/service/HealthScoreServiceTest.java` — PRF-05/回归（新增 2 条：NO_DATA 保持 `profileCompleteness == null`；缓存 VO 往返保留新字段；12 条既有用例不动）
- [ ] `hhs-frontend-v2/src/__tests__/stores/profileCompleteness.spec.ts` — PRF-01/07（单源、404 合成、失败保持 null、`hasProfile`/`assumptions` 透传、7 天 fake timers）
- [ ] `hhs-frontend-v2/src/__tests__/components/ProfileCompleteness.spec.ts` — PRF-01（分段数 = `fields.length`、图例可访问性、影响句按 `hasProfile` 先分支）
- [ ] `hhs-frontend-v2/src/__tests__/components/ProfileReminderBanner.spec.ts` — PRF-04/07
- [ ] `hhs-frontend-v2/src/__tests__/components/EstimateBadge.spec.ts` — PRF-06（null 不渲染、点击带 focus）
- [ ] `hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts` — PRF-08（首次访问即拉取、判定顺序三序、面板列表 = `assumptions`、可选身高体重留空不阻断）
- [ ] `hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts` — PRF-07（全站单实例）
- [ ] `hhs-frontend-v2/src/__tests__/views/ProfileCompleteness.spec.ts` — PRF-01/02/03（9 条 hint、徽标映射、承诺句单例、**保存后完整度重拉**、`放弃编辑` 标签）
- [ ] `hhs-frontend-v2/src/__tests__/views/ScoreEstimateBadge.spec.ts` — PRF-06（徽标门禁/落点/导航/NO_DATA、`放弃生成` 标签）
- [ ] 修改 `hhs-frontend-v2/src/__tests__/views/ProfileErrorSample.spec.ts` — 补 `vi.mock('vue-router')` 与 `getCompleteness` mock（fixture 必须含 `hasProfile`/`assumptions`，否则 vue-tsc 类型错；否则既有 3 用例转红）
- [ ] 框架安装：**无需** —— 两端测试基建均已就绪

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 徽标与风险等级 `el-tag` 在同一视区的视觉共存 | PRF-06 | 间距/语汇/行高隔离的实际观感需人眼判断 | 在风险页同时触发两条（档案不完整 + 高风险），确认两个徽标不混淆、最小 16px 行距 |
| 三档重要度徽标的形状冗余在低视力下可辨 | PRF-03 | 形状差异（实心/实线/虚线）的可辨性需真实人眼 | 切浅色/深色主题各看一遍；确认文字标签是唯一语义载体（1.4.1 由文字满足） |
| 仪表盘横幅在真实滚动位置不遮挡内容 | PRF-07 | 布局上下文依赖真实视口高度 | 在 1080p 与 768p 视口各看一次，确认横幅不压住 HealthScoreCircle |
| 7 天静默的真实跨会话行为 | PRF-07 | localStorage 跨浏览器重启的持久性 | 关闭横幅 → 关闭浏览器 → 重开 → 确认 7 天内不再出现；手动改时间戳到 8 天前 → 确认恢复 |
| 风险评估就地补填的完整交互流 | PRF-08 | 表单聚焦、提交、失败保留输入的整体手感 | 无档案账号创建评估：确认面板先展开假设列表（**无档案时只有 1 条通用句**）→「直接创建」可跳过 → 补填性别+出生日期后重算假设 → 再补身高体重后 BMI 假设消失 |
| 档案页保存后完整度即时可见提升 | PRF-01 | 「填完→看到分数涨」是产品的核心反馈回路，需真实观感确认它不像是卡住 | 空档案账号进档案页（0% · 基础档案）→ 填性别+出生日期+身高+体重 → 保存 → 确认头部变为 60%/70% 区间、分段与 3 个字段徽标同时翻转，无需刷新 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 90s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-11-09（research 阶段收口）