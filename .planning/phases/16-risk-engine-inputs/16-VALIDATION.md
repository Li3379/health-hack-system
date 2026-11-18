---
phase: "16"
slug: "risk-engine-inputs"
status: validated
nyquist_compliant: true
wave_0_complete: false
created: "2026-11-18"
source: 16-RESEARCH.md § Validation Architecture
---

# Phase 16 — Validation Strategy

> 风险评估引擎输入重组的逐需求验证契约。
> 来源：`16-RESEARCH.md:1002` §Validation Architecture（134 行，实测基线 + 逐条测试映射）。

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (backend)** | JUnit 5（`spring-boot-starter-parent` 3.2.0）+ Mockito `MockitoExtension`（STRICT_STUBS） |
| **Framework (frontend)** | Vitest `^4.1.0` + `@vue/test-utils ^2.4.6`，`environment: happy-dom` |
| **Config file** | `hhs-backend/pom.xml`（surefire 3.0.0，排除 `*IntegrationTest`/`*PerformanceTest`）；`hhs-frontend-v2/vitest.config.ts` |
| **Quick run (backend, one class)** | `mvn -f hhs-backend/pom.xml -o -DskipITs -Dtest=RiskAssessmentAssumptionsTest test` |
| **Quick run (backend, two classes)** | `mvn -f hhs-backend/pom.xml -o -DskipITs -Dtest='RiskAssessmentAssumptionsTest,MetricCoverageAssemblerTest' test` |
| **Quick run (frontend)** | `npx --prefix hhs-frontend-v2 vitest run --root hhs-frontend-v2 src/__tests__/views/RiskBackfill.spec.ts` |
| **Full suite (backend gate)** | `mvn -f hhs-backend/pom.xml clean verify -DskipITs` + `checkstyle:check` |
| **Full suite (frontend gate)** | `npm --prefix hhs-frontend-v2 run test:run` + `run build` |
| **Baseline (measured 2026-11-18, pre-change)** | `MetricCoverageAssemblerTest` 9 tests · `RiskAssessmentAssumptionsTest` 26 tests · `RiskBackfill.spec.ts` 29 tests · checkstyle **0 errors** |

> ⚠ `*IntegrationTest` 归 Failsafe，Surefire 不执行。本阶段新增测试一律 `*Test` / `*.spec.ts`。

---

## Sampling Rate

- **Per task commit:** 单类快跑（后端 `-Dtest=<改动类>`；前端 `npx vitest run <改动文件>`）
- **Per wave merge:** `mvn -f hhs-backend/pom.xml -o -DskipITs test` + `npm --prefix hhs-frontend-v2 run test:run`
- **Phase gate:** `clean verify -DskipITs` + `checkstyle:check` + `test:run` + `build`，全绿后进 `/gsd:verify-work`
- **Max feedback latency:** 90 秒

---

## Per-Task Verification Map

| Req | Behavior | Type | Command | File exists | 预期失败信号 | Status |
|---|---|---|---|---|---|---|
| AC-1 | 心血管归因不含 `来自`/`折半`/`（折半计入` | unit | `-Dtest=RiskAssessmentAssumptionsTest` | ✅ 加 1 条 | 出现 `来自糖尿病` 等字样 | ⬜ |
| AC-1 | 心血管归因恰 9 条，key 集合精确 | unit | 同上 | ✅ 加 1 条 | 条数或 key 集不符 | ⬜ |
| AC-2 | 血脂四项全缺 → 4 条 `MISSING`，无 `ASSUMED`，无 0 分伪造 | unit | 同上 | ✅ 加 1 条 | 出现 `ASSUMED` 或伪造 0 值 | ⬜ |
| AC-3 | SBP 135 计 30（参考范围 139 下会计 0）；DBP 87 计 15 | unit | 同上 | ✅ 参数化 | 得 0 分（说明用了参考范围） | ⬜ |
| AC-3 | `RiskThresholdRegistry` 12 条规则、`consumedKeys().size()==8`、每键 `isCanonicalKey` | unit | `-Dtest=RiskThresholdRegistryTest` | ❌ **Wave 0** | 规则数或键集不符 | ⬜ |
| AC-4 | `riskNote` 键集恰 8 个；每条匹配形状正则且去空白 ≤24 | unit | `-Dtest=MetricCoverageAssemblerTest` | ✅ 改 `onlyEngineInputsCarryRiskNote` | 键集不符 / 超长 | ⬜ |
| AC-4 | 每个有 `riskNote` 的键都被引擎读过（`consumedKeys()` ⊆ 引擎读取集） | unit | 同上 + 引擎 | ✅ 加 1 条 | 面板说参与但引擎不读 | ⬜ |
| AC-5 | 合计 150 的夹具 → `riskScore==100` 且 `Σpoints==100` 且 explanation 含「按比例折算」 | unit | `-Dtest=RiskAssessmentAssumptionsTest` | ✅ 加 1 条 | 未触发 `scaleTo` 或明细与总分不符 | ⬜ |
| AC-5 | `bmi` 两侧 `value()` 字符串相等（`"22.9"`）且 `≠ "22.86"` | unit | 同上 | ✅ 加 1 条（A(i)） | 两侧不等（精度分叉） | ⬜ |
| AC-6 | `glucose` 别名仍计分；`systolicBloodPressure` 别名生效 | unit | 同上 | ✅ 既有 2 条 + 1 条 | 别名未命中 → 计 0 分 | ⬜ |
| AC-7 | 历史批次读时返回快照原文（含「折半」），不重算 | unit | `-Dtest=RiskAssessmentAssumptionsTest` | ✅ 加 1 条 | 读时重算出新形态 | ⬜ |
| AC-8 | 后端全绿 | gate | `clean verify -DskipITs` + `checkstyle:check` | ✅ | 任一失败 | ⬜ |
| AC-8 | 前端全绿 | gate | `test:run` + `build` | ✅ | 任一失败 | ⬜ |
| 3(a) | `Risk.vue` 中 `var(--border-1)` 计数 == 0 | 源码扫描 | `npx vitest run …RiskBackfill.spec.ts` | ✅ 仿 `ResidueGate.spec.ts:518-526` | 计数 > 0 | ⬜ |
| 3(b) | 有 `riskNote` 且 `MISSING` 的指标只渲染一次；三组之和 == `coverage.length` | 组件 | 同上 | ✅ 加 2 条 | 重复渲染 / 和不等于总数 | ⬜ |
| 3(b) | `usedMissing>0` 时渲染「（其中 N 项未测）」，且不出现「缺数据」 | 组件 | 同上 | ✅ 加 1 条 | 用错词表 | ⬜ |
| 3(c) | 面板 `bmi` 行 `value` 来自 profile；档案无值报 `MISSING` 而非读指标行 | domain | `-Dtest=MetricCoverageAssemblerTest` | ✅ 加 2 条 | 面板读指标行 → 与引擎不同值 | ⬜ |
| UI-SPEC C4 | 弹窗首列 ≥ 88px | 源码扫描 | `npx vitest run …RiskBackfill.spec.ts` | ✅ 加 1 条 | 列宽不足 | ⬜ |
| UI-SPEC C6 | 两套词表各自渲染（`未测` / `按默认值`） | 组件 | 同上 | ✅ 加 2 条 | 词表混用 | ⬜ |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/RiskThresholdRegistryTest.java` —— **新建**。覆盖 AC-3（12 条规则的键/方向/阈值/分值）、AC-4 的 `consumedKeys()` 精确集合、构造期 canonical key 自检（`assertThrows`）
- [ ] `RiskBackfill.spec.ts:22-31` 的 `vi.mock('@/api/prevention', …)` 工厂**追加** `getMetricCoverage: vi.fn()` —— 否则 ①②③⑦A(ii)/A(iii) 全部在断言空数组。**这是所有前端断言的唯一前置**
- [ ] `RiskAssessmentAssumptionsTest.stubMetrics`（`:373-383`）支持两位小数（`new BigDecimal(String.valueOf(v))` 或 `Number` 重载）—— 否则 T-3/T-4/T-5 无法表达
- [ ] `RiskAssessmentAssumptionsTest` 的 `service` 构造（`:62-64`）同步 +`RiskThresholdRegistry`（与 `MetricCoverageAssembler` 共用同一实例）
- [ ] `MetricCoverageAssemblerTest` 的 `assembler` 构造（`:36`）同步 +`RiskThresholdRegistry`；9 处 `assemble(List.of(...))` 改为 `assemble(null, List.of(...))` 或带 profile
- [ ] 无框架安装缺口（JUnit / Vitest 均已就位）

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|---|---|---|---|
| 四行连续 MISSING 的可读性 | UI-SPEC C3.2 | 密度观感需人眼判断 | 打开 `/prevention/risk` 详情弹窗，确认四行灰色 MISSING 靠 1px 分隔线 + 灰 pill + 斜体句三重区分，不糊成一片 |
| `riskNote` 在 1024px / 1280px 的实际行数 | UI-SPEC Contract 2 | `1fr` 宽度随视口变化（608px 只成立于 1120px 口径） | 两档视口各量一次实际行数，确认不出现 3 行以上导致面板失衡 |
| 长 `explanation` 不横向溢出 | UI-SPEC long-text | D-01 后的新句子长度未知 | 767px 与 1024px 各实跑一次，确认无横向滚动、无字形裁切 |
| 窄屏 375px 无横向溢出 | 既有回归 | Phase 15 曾在此档溢出 69px | `document.documentElement.scrollWidth <= clientWidth` |
| 接口 500 时面板不误导 | UI-SPEC error backstop | 需真实故障 | 停后端后刷新，确认面板不会让用户误以为「没有指标」 |

---

## Validation Sign-Off

- [x] 每条需求有自动化验证或 Wave 0 依赖
- [x] 采样连续：无连续 3 个任务缺少自动化验证
- [x] Wave 0 覆盖全部 MISSING 引用
- [x] 无 watch-mode 标志
- [x] 反馈延迟 < 90 秒
- [x] `nyquist_compliant: true`

**Approval:** approved 2026-11-18（research 阶段收口，5 轮 plan-checker 验证后）
