---
phase: "12"
slug: "threshold-wizard"
status: draft
nyquist_compliant: true
wave_0_complete: false
created: "2026-11-08"
---

# Phase 12 — Validation Strategy

> 阈值告警正确性修复 + 配置向导化的逐需求验证契约。
> 来源：`12-RESEARCH.md` §Validation Architecture。

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (backend)** | JUnit 5 + Mockito + AssertJ（`spring-boot-starter-test`）；Surefire 3.0.0；Failsafe 3.0.0 + Testcontainers 1.19.3 |
| **Framework (frontend)** | Vitest 4.1.7 + @vue/test-utils（`environment: 'happy-dom'`, `globals: true`） |
| **Config file** | `hhs-backend/pom.xml:206-231`；`hhs-frontend-v2/vitest.config.ts` |
| **Quick run command (backend, 单类)** | `mvn -f hhs-backend/pom.xml test -Dtest=ThresholdRangeCheckerTest` |
| **Quick run command (frontend, 单 spec)** | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/ThresholdWizard.spec.ts` |
| **Full suite command (backend)** | `mvn -f hhs-backend/pom.xml clean verify -DskipITs` |
| **Full suite command (frontend)** | `npm --prefix hhs-frontend-v2 run test:run` |
| **Lint / 类型** | `mvn -f hhs-backend/pom.xml checkstyle:check` + `npm --prefix hhs-frontend-v2 run lint:check` + `npm --prefix hhs-frontend-v2 run build`（含 `vue-tsc -b`） |
| **Estimated runtime** | 后端单类 ~15s；后端全量 ~90s；前端单 spec ~8s；前端全量 ~40s |

> Surefire 排除 `**/*IntegrationTest.java` 与 `**/*PerformanceTest.java`（`pom.xml:211-214`），
> 因此 `-DskipITs` 与单纯 `mvn test` 等价。集成测试必须命名为 `*IntegrationTest` 才会被 Failsafe 拾取。

---

## Sampling Rate

- **After every task commit:** 该任务所属语言的单类 / 单 spec 命令（见 Per-Task Verification Map）
- **After every plan wave:** `mvn -f hhs-backend/pom.xml clean verify -DskipITs` + `npm --prefix hhs-frontend-v2 run test:run`
- **Before `/gsd:verify-work`:** 全量单测 + `checkstyle:check` + `lint:check` + `build` 全部通过
- **Max feedback latency:** 90 秒

---

## Per-Task Verification Map

> Task ID 在 PLAN.md 生成后回填。下表按**需求**绑定验证方式，planner 在拆任务时把每个
> REQ-ID 落到具体 task 上即可继承对应命令。

| Req ID | 行为 | Test Type | Automated Command | File Exists | 预期失败信号 | Status |
|--------|------|-----------|-------------------|-------------|-------------|--------|
| FIX-01 | 四档判定正确，`isNormalRange` 与 `classify` 严格互补 | unit | `mvn -f hhs-backend/pom.xml test -Dtest=ThresholdRangeCheckerTest` | ❌ W0 | `expected: <WARNING> but was: <NORMAL>` | ⬜ |
| FIX-02 | `glucose` 写入落库为 `bloodGlucose`；别名撞已有行返回业务错误而非 500 | unit | `mvn -f hhs-backend/pom.xml test -Dtest=UserThresholdServiceTest` | ⚠️ 存在需扩展 | `expected: "bloodGlucose" but was: "glucose"` | ⬜ |
| FIX-03 | 存量 `glucose` 行经 `bloodGlucose` 查询可命中 | unit + integration | `… -Dtest=ThresholdEvaluatorTest` | ❌ W0 | mock `selectList` 未收到 `.in` → `expected not null` | ⬜ |
| FIX-04 | 脚本幂等、重复行合并且保留最新 | manual UAT + SQL 断言 | `docker compose exec -T mysql mysql -uroot -p"$DB_PASSWORD" hhs < hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql`，比对前后 COUNT | ❌ W0 | 第二次执行后行数变化（非幂等） | ⬜ |
| FIX-05 | D-18 全覆盖：正常 / 警告双侧 / 危急双侧 / 单侧 null / 全 null / 恰等边界 | unit | `mvn -f hhs-backend/pom.xml test -Dtest=ThresholdRangeCheckerTest` | ❌ W0 | 任一分支断言 `expected: <X> but was: <Y>` | ⬜ |
| THR-01 | 选择器只列 alertable（6 项）、按 category 分组、文案 `显示名 (单位)` | unit（组件） | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/ThresholdWizard.spec.ts` | ❌ W0 | `expected 6 options, found 0` 或裸值 `glucose` 出现 | ⬜ |
| THR-02 | 选定后四项预填推荐值 + 「推荐」徽标 + 「恢复推荐值」 | unit（组件） | 同上 | ❌ W0 | 四项仍为空 / 无徽标 | ⬜ |
| THR-03 | 四项说明文案含单位插值 | unit（组件） | 同上 | ❌ W0 | 说明文案缺 `（mmHg）` 等 | ⬜ |
| THR-04 | 四区条随输入实时更新；null 显示 `—`；`aria-label` 完整 | unit（组件） | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/components/ThresholdZoneBar.spec.ts` | ❌ W0 | 渲染 4 段而非 5 段，或 null 渲染为 `undefined` | ⬜ |
| THR-05 | 前端纯函数拦截 + 禁用保存；后端 400 + **两条** `fieldErrors` | unit ×2 + integration | `npm … -- src/__tests__/utils/thresholdValidation.spec.ts`；`mvn … -Dtest=ThresholdRangeValidatorTest` | ❌ W0 ×2 | `expected size 2 but was 0` | ⬜ |
| THR-06 | 确认语句仅含已填边界；全 null 降级 | unit（组件） | `npm … -- src/__tests__/views/ThresholdWizard.spec.ts` | ❌ W0 | 未填边界仍出现在句子中 | ⬜ |
| THR-07 | 空状态展示默认规则（6 项）+ 「使用推荐阈值」CTA | unit（组件） | `npm … -- src/__tests__/views/Thresholds.spec.ts` | ⚠️ 可扩展 `ErrorLayerSample.spec.ts` | 仍渲染 `ElEmpty` 的「暂无数据」 | ⬜ |
| THR-08 | 重复保存给 1 条 toast + 「打开编辑」 | unit（组件） | `npm … -- src/__tests__/views/ErrorLayerSample.spec.ts` | ✅ 用例 6/7/8 已存在，**必须保持通过** | 现有用例转红 | ⬜ |
| 元数据 | `GET /api/metrics/metadata` 返回 `alertable` 与 `recommended` | unit（MVC slice） | `mvn … -Dtest=MetricMetadataControllerTest` | ⚠️ 存在需扩展 | `jsonPath $.data.heartRate.alertable` 不存在 | ⬜ |
| 元数据完整性 | `alertable=true` ⟹ 至少一推荐值非 null；推荐值落在 `hardMin/hardMax` 内 | unit | `mvn … -Dtest=MetricMetadataRegistryTest` | ⚠️ 存在需扩展 | 某 alertable 指标推荐值越界 | ⬜ |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

必须在实现前先建立（并看到上表的预期失败信号）：

- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java` — FIX-01 / FIX-05（六类边界用例）
- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ThresholdEvaluatorTest.java` — FIX-03（别名 IN + 确定性取首）
- [ ] `hhs-backend/src/test/java/com/hhs/validation/ThresholdRangeValidatorTest.java` — THR-05 后端（断言一次违规 → **两条** violation，字段名不含点）
- [ ] `hhs-frontend-v2/src/__tests__/utils/thresholdValidation.spec.ts` — THR-05 前端纯函数
- [ ] `hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts` — THR-04
- [ ] `hhs-frontend-v2/src/__tests__/views/ThresholdWizard.spec.ts` — THR-01/02/03/06
- [ ] `hhs-frontend-v2/src/__tests__/views/Thresholds.spec.ts` — THR-07（或扩展 `ErrorLayerSample.spec.ts`）
- [ ] 扩展 `UserThresholdServiceTest`（规范化断言）
- [ ] 扩展 `MetricMetadataControllerTest` / `MetricMetadataRegistryTest`（alertable + recommended）
- [ ] 框架安装：**无需** —— 两端测试基建均已就绪

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 别名写入 → 标准 key 落库的数据库级端到端（原 Wave 0 集成测试项 `ThresholdAliasIntegrationTest`） | FIX-02 / FIX-03 | Surefire 排除 `*IntegrationTest`（Failsafe 才拾取且需 Docker）；别名 IN 查询与写入规范化已由 plan 02 的 wrapper 级单测断言（`selectList` 收到 `.in` 集合参数、落库 key 断言），数据库级真值由 plan 03 的手工脚本 UAT（前后 COUNT 比对，操作真实 MySQL 并触碰 `uk_user_metric`）承接 | ①通过 API 以别名 `glucose` 保存一条阈值；②`SELECT metric_key FROM user_threshold WHERE user_id=<me>` 确认落库为 `bloodGlucose`；③用 `bloodGlucose` 查询阈值命中该行；④别名撞已有行返回业务错误而非 500 |
| SQL 脚本在生产规模数据上的幂等性与重复行合并正确性 | FIX-04 | 需要真实的多用户、多别名历史数据；单测无法复现 MySQL 的 UNIQUE 约束交互 | ①执行前记录 `SELECT user_id, metric_key, COUNT(*) FROM user_threshold GROUP BY 1,2 HAVING COUNT(*)>1` 与总行数；②执行脚本；③再次执行脚本并确认总行数与第一次执行后一致；④确认无重复 `(user_id, metric_key)` 组合；⑤确认 `alert_rule.metric_key` 全部是标准 key |
| 修复后 WARNING 告警的真实推送量与频率 | FIX-01（副作用） | 需要真实用户与真实推送渠道；D-21 明确本阶段不加限流，须人工观察 | 用一次真实测量落库触发警告区间，观察 WebSocket 与 Email 是否各收到 1 条、是否在 1 小时内重复；记录到 UAT |
| 向导在真实浏览器中的三步流程、四区色带渲染、主题切换 | THR-01..THR-07 | 视觉与跨主题对比度无法由 DOM 断言覆盖 | 浏览器走一遍新增 → 预填 → 改值 → 预览 → 确认 → 保存；分别在浅色/深色主题下截图比对 UI-SPEC 的色带与错误色 |
| 教学型空状态在真实空数据下的呈现 | THR-07 | 需要清空当前用户的阈值记录 | 删除该用户全部 `user_threshold` 行后刷新页面，确认出现 6 项默认规则 + 「使用推荐阈值」 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references（`ThresholdAliasIntegrationTest` 已于 2026-11-08 计划审查后降级为上方 Manual-Only 行，理由见该行 Why Manual 列）
- [x] No watch-mode flags
- [x] Feedback latency < 90s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-11-08（plan-checker 审查后收口）