# Phase 17 — 告警语义收敛

**Status:** complete 2026-11-19
**Method:** autoresearch loop（机械指标驱动），分支 `autoresearch/threshold-single-source`
**Requirement:** ROADMAP v1.4 成功标准 3 —「全站只有一套阈值区间语义：`ThresholdRangeChecker.classify` 是唯一判定源，无第二份比较实现」

---

## 前提失效（必须先记下的发现）

ROADMAP 原文把 Phase 17 定义为：

> 消除 `IntelligentAlertService` 内 4 处独立区间比较，统一到 `ThresholdRangeChecker`

**该目标类已不存在。** 提交 `19db185`（chore: remove unused gamification/export/audit modules）把整个 `com.hhs.service.alert` 包删除了，包含：

```
AlertAIAnalysisService, AlertDeduplicator, AlertFrequencyStrategy, AlertTemplateService,
IntelligentAlertService, RecoveryNotifier, TrendDirection, TrendPredictor, TrendResult
```

`git cat-file -e HEAD:.../service/alert/IntelligentAlertService.java` → 不存在；`git ls-tree HEAD -- .../service/alert/` → 空。

**但成功标准 3 仍然被违反。** 目标类只是那个标准的当年落点，标准本身描述的是一个跨文件性质。继续按标准核查，发现三份并存的区间判定：

| 文件 | 边界比较数 | 性质 |
|---|---|---|
| `ThresholdRangeChecker` | 2 | 合法：唯一实现的两个原语（`isBelowThreshold` / `isAboveThreshold`） |
| `ThresholdComparator` | **8** | **平行的第二套五区间实现**，且**没有任何测试文件** |
| `ThresholdEvaluator.findExceededThreshold` | **2** | 第三份比较 |

讽刺之处在源码注释里：`ThresholdRangeChecker.classify` 写着

> Bands are evaluated critical-side first, so each later match implicitly excludes the earlier band — no explicit two-sided inequalities are written, which would introduce a second comparison base (RESEARCH Pattern 1, **the root cause of the original inverted-band defect**).

而 `ThresholdComparator` 恰恰就是那些双面不等式，写了第二遍，且无人守护。

---

## 机械指标

`.auto/measure.sh` 输出 `METRIC dup_sites=<n>`（越低越好）：

- 统计**同一行内**「边界 getter 与关系比较相遇」的出现次数，覆盖三种写法：
  1. `getter ... compareTo|>/<`
  2. `compareTo(getter)`
  3. `>/< ... getter`
- 范围：引用 `UserThreshold` **实体**的文件（词边界，排除 `UserThresholdRequest` / `UserThresholdVO`）
- 排除 `ThresholdRangeChecker.java`（它的比较就是唯一判定源本身）

**指标防作弊验证**（三个作弊夹具 + 一个合法夹具）：

| 夹具 | 内容 | 期望 | 实测 |
|---|---|---|---|
| Cheat1 | `v.compareTo(t.getWarningHigh()) >= 0` | 计数 | 1 ✓ |
| Cheat2 | `v > t.getWarningHigh().doubleValue()` | 计数 | 1 ✓ |
| Cheat3 | `t.getCriticalLow().doubleValue() >= v` | 计数 | 1 ✓ |
| Legit | 仅 null 检查 `t.getWarningHigh() != null && ...` | 不计数 | 0 ✓ |

第一版指标只数 `compareTo(`，被 Cheat2 绕过 —— 改写成 `>` 就能归零而缺陷仍在。加入第三种分支后四种夹具全部正确。

## 正确性闸门

`.auto/checks.sh`：全量后端测试 + checkstyle。

**闸门有牙齿的证明**：向 `ThresholdRangeChecker.classify` 注入一处 band 反转（`CRITICAL_LOW` 改为 `WARNING_LOW`），`ThresholdRangeCheckerTest` 立刻 5 条变红、退出码 1。

闸门还强制断言 `tests_run >= 700` —— 早期版本用 `| tail -20` 吞掉了 `Tests run` 汇总行，导致「0 条测试执行」也能通过。已修。

---

## 迭代记录

| run | status | dup_sites | 说明 |
|---|---|---|---|
| 1 | noop | 10 | 基线：`ThresholdComparator` 8 + `ThresholdEvaluator` 2 |
| 2 | keep | **0** | 删除 `ThresholdComparator`，所有判定经 `bandOf` |
| 3 | noop | 0 | 交叉区间回归守卫测试（指标不变，按审计规则记 noop） |

## 改动

1. **`ThresholdRangeChecker` 新增 `public Band bandOf(Double, UserThreshold)`** —— `classify(BigDecimal, UserThreshold)` 的公开面。需要方向或严重度的组件读 band，不再自己重算边界。
2. **`ThresholdEvaluator`** —— severity、direction、被超越的边界值三者全部由同一个 `band` 投影得出。`findExceededThreshold` 改为 `switch (band)`，接收 band 而非重算比较。移除 `comparator` 字段。
3. **删除 `ThresholdComparator` 整个类** —— 它唯一的活方法 `isHigh` 只有一处调用，`compare()` 与 `isLow()` 早已是死代码。净删 40 行平行五区间逻辑。

净变化：**-60 行**（`ThresholdComparator` -81，其余 +21）。

---

## 顺带修复的既有缺陷

收敛改变了**一个**边界情形。当 `warningLow > warningHigh`（写入期 `ThresholdRangeValidator` 会拒绝，但历史行可能早于该校验存在）时：

| | severity 来源 | direction 来源 | 渲染结果 |
|---|---|---|---|
| 旧 | `classify()` → `WARNING_LOW` | 独立 OR 链 → `HIGH` | 「**警告: X偏低**」，却指向高边界 |
| 新 | 同一个 band | 同一个 band | 一致 |

实测数据 `user_threshold` / `alert_rule` 中交叉阈值计数均为 **0**，且写入路径有校验拦截 —— 所以这是潜在缺陷而非现网故障。但旧代码在这种情况下产出**自相矛盾**的告警文案。

新增测试 `evaluate_crossedWarningBand_directionAgreesWithSeverity` 固定该性质。**变异验证**：把 direction 改回旧 OR 链，该测试立刻变红。

---

## 验证

| 检查 | 结果 |
|---|---|
| 后端全量测试 | **703 / 0 / 0**（702 原有 + 1 新增） |
| Checkstyle | 0 violations |
| `dup_sites` | 10 → **0** |
| `fork_files` | 2 → **0** |
| 全库「边界 getter + 比较」组合 | **0**（`MetricRecommendedThresholdProvider` 只做 null 检查后搬运值；`ThresholdRangeValidator` 比较边界之间的顺序，是不同操作） |
| `ThresholdComparator` 残留引用 | **0** |
| 语义等价性证明 | 合法阈值序下 1500 个采样点逐点等价，0 分歧 |

## 未纳入本阶段

`ThresholdRangeValidator` 的 4 处比较**正确排除**：它比较边界**之间**的先后（`criticalLow < warningLow < warningHigh < criticalHigh`），不是「取值落在哪个区间」。不同操作，不同语义。

`AiParseServiceImpl:505-506` 的 `PANIC_LIMITS` 比较也排除：那是录入校验的四梯度（生理极限 / 急危重症 / 参考范围），不是用户个性化阈值判定。**但发现一处真实死逻辑**：`bloodOxygen` 的 panic 为 `{90, null}`，`isCritical` 已涵盖 `value <= panic[0]`，故 `isOxygenCritical` 完全冗余 —— 归入 Phase 18。

## 产物位置

- 分支：`autoresearch/threshold-single-source`
- 已 cherry-pick 到 `chore/remove-unused-modules`：
  - `75382e4` experiment: Delegate every band decision to bandOf, delete ThresholdComparator
  - `606721d` test(threshold): pin direction/severity single-source on a crossed band
  - `43deaf6` chore: keep .auto/ ignored in the main tree（worktree 专用的 ignore 变更不回流）
- 会话状态：`.auto/`（worktree `D:/hhs-ar17`，已加入 .gitignore）
