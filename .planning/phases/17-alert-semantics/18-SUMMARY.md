# Phase 18 — 技术债清理与收尾

**Status:** complete 2026-11-19
**Method:** autoresearch segment 4，分支 `autoresearch/threshold-single-source`
**Baseline → 结果:** `dead_code` **3 → 0**

---

## 三个目标各自的性质（不是同一类东西）

| 项 | 判定 | 依据 |
|---|---|---|
| `RiskAnalyzer` | **死代码** | 零引用、无接口、无注入 |
| `PushStrategyService` | **死代码** | 三个公开方法全无外部调用 |
| `RiskAnalyzer` ↔ `RiskScorer` | **重复实现** | 逐字相同的查询与 100/70/40 映射 |

## `RiskAnalyzer` —— 与 `RiskScorer` 逐字重复

```java
// RiskAnalyzer.calculateRiskScore          // RiskScorer.calculate
.eq(RiskAssessment::getUserId, userId)      .eq(RiskAssessment::getUserId, userId)
.orderByDesc(...getCreateTime)              .orderByDesc(...getCreateTime)
.last("LIMIT 1")                            .last("LIMIT 1")
switch (risk.getRiskLevel()) {              switch (risk.getRiskLevel()) {
  case "LOW" -> 100;                          case "LOW" -> 100;
  case "MEDIUM" -> 70;                        case "MEDIUM" -> 70;
  case "HIGH" -> 40;                          case "HIGH" -> 40;
  default -> 70;                              default -> 70;
```

差异只有空结果分支：`RiskAnalyzer` 注释写 `// Neutral score`，`RiskScorer` 没有注释。`ScoreCalculator` 注入的是 `RiskScorer`，`RiskAnalyzer` 从来没人接。

删除它一次消掉两件事：死类 + 重复实现。这一点很重要——**如果把方法体合并进 `RiskScorer` 而不是删掉，死类消失了而重复还在**。指标特意把两者分开计数（`dead_classes` / `dup_risk_scorer`）就是为了防这个。

## `PushStrategyService` —— 被 `PushChannelManager` 取代

三个公开方法（`getChannelsForAlertLevel` / `getEnabledChannelsForUser` / `pushWithStrategy`）外部调用数均为 **0**。真实推送路径是 `PushChannelManager`，它提供同等能力且被 `PushConfigController` 注入：

```
push / pushMulti / pushAll / getChannel / hasChannel
```

删除后核实无孤儿：`ChannelType` 仍被 `PushConfigController` 正常消费。

## 指标设计中的两个陷阱（都踩过并修正）

**陷阱 1：把 Spring 组件当死代码。**
第一版分析报告 52 个「零引用类」，几乎全是误报——`@Service` / `@Controller` / `@Configuration` 由容器实例化，源码里当然没有静态引用。判据必须收窄为「非容器管理 + 零引用」。

**陷阱 2：把 `@PostConstruct` 的启动校验当死代码。**
`JwtSecretValidator` 零引用，但它在 `@PostConstruct` 里校验 JWT 密钥长度——**Bean 实例化就是它的调用**。删掉会静默移除一项安全校验。已加入排除规则：

```python
if re.search(r'@PostConstruct|implements\s+InitializingBean', t):
    continue
```

这条差别很关键：它区分了「没人调用」和「没人需要调用」。

## 迭代记录

| run | status | dead_code | 改动 |
|---|---|---|---|
| 1 | noop | 3 | 基线（2 死类 + 1 重复） |
| 2 | keep | 1 | 删除 `RiskAnalyzer`（死类与重复一并消除） |
| 3 | keep | **0** | 删除 `PushStrategyService` |

## 验证

| 检查 | 结果 |
|---|---|
| 后端全量测试 | **707 / 0 / 0** |
| Checkstyle | 0 violations |
| `dead_code` | 3 → **0** |
| `ChannelType` 孤儿检查 | 仍被 `PushConfigController` 使用 |
| `PushChannelManager` 功能完整性 | push / pushMulti / pushAll / getChannel / hasChannel 齐备 |

## 追加：冗余布尔判定（segment 6）

`redundant_guards` **1 → 0**。

`AiParseServiceImpl` 的 `isOxygenCritical`：

```java
boolean isOxygenCritical = "bloodOxygen".equals(canonicalKey) && value.compareTo(panic[0]) <= 0;
boolean isCritical = (panic[0] != null && value.compareTo(panic[0]) <= 0)
                  || (panic[1] != null && value.compareTo(panic[1]) >= 0);
if (isOxygenCritical || isCritical) { ... }
```

`bloodOxygen` 的 `panic[0]` 是 `90` 且非 null，所以 `isCritical` 的第一项精确覆盖了它。判定输出完全一致。

**危害不在运行时，在阅读时**：这个命名暗示血氧需要特殊处理，后来的人会去改这一行，而不是 `PANIC_LIMITS` 表。删除后低界/高界在同一个表达式里对称可见。

**先写测试再删**：新增 `validateMetricRange_bloodOxygenBelow90_reportsCriticalTier`，固定血氧 85 必须落 Tier 3。

**变异验证**：把 `isCritical` 的低界那一半删掉，血氧 85 退化成普通「偏低（正常参考范围：95~100 %）」，测试立刻红。

## 两个被证伪的猜测（记录以免重复走）

**「PANIC_LIMITS 与 registry 硬限冲突」——不成立。**
`ThresholdRangeValidator` 的注释写着「registry-derived limits, not PANIC_LIMITS」，看起来像两套互斥的数字。实测八个指标：

| key | registry hardMin~hardMax | PANIC_LIMITS | 关系 |
|---|---|---|---|
| systolicBP | 40~300 | 70~180 | 内含 |
| diastolicBP | 20~200 | 40~110 | 内含 |
| bloodGlucose / fasting / postprandial | 1.0~33.3 | 2.8~16.7 | 内含 |
| heartRate | 20~300 | 45~140 | 内含 |
| bodyTemperature | 34.0~42.5 | 35.0~39.5 | 内含 |
| bloodOxygen | 50~100 | 90~null | 内含 |

全部严格内含，语义分工清晰（生理绝对极限 vs 临床急危阈值）。**两套都该存在**，不合并。

顺带记一个自己犯的错：第一次解析这些数字时按 `MetricDefinition` 的字段顺序猜错了一位（把 `step` 当 `hardMin`），得出「收缩压上限 40 mmHg」这种荒谬结论，八个指标全部误报「倒挂」。核对 record 定义后才纠正。

**「VO 投影重复值得提取」——不成立。** 见 `prompt.md` 的 segment 5 discard 记录。

## Phase 18 剩余项（未覆盖）

ROADMAP 里 Phase 18 还列了这些，本轮**没有处理**：

- `RiskScorer` 改读归因快照 —— 当前仍按 `riskLevel` 映射，未读 `contributors` 快照
- `FileUploadController` 英文泄漏 —— 初步 grep 未发现 `Result.failure(n, "English")` 形态，需细查其他路径
- 清理未消费字典键
- **本轮新发现的死逻辑**：`AiParseServiceImpl:505` 的 `isOxygenCritical` 完全冗余 ——

  ```java
  BigDecimal[] panic = PANIC_LIMITS.get(canonicalKey);   // bloodOxygen -> {90, null}
  boolean isOxygenCritical = "bloodOxygen".equals(canonicalKey) && value.compareTo(panic[0]) <= 0;
  boolean isCritical = (panic[0] != null && value.compareTo(panic[0]) <= 0) || ...;
  if (isOxygenCritical || isCritical)                     // 第一项永远被第二项涵盖
  ```

  保留它不影响正确性，但它是「同一判定写两遍」的又一实例——与 Phase 17 清除的那类同源。

## 四个 segment 的累计结果

| segment | 指标 | 起 → 止 |
|---|---|---|
| 1–2 | `dup_sites`（阈值语义单一源） | 10 → **0** |
| 3 | `alias_sites`（指标 key 别名） | 6 → **0** |
| 4 | `dead_code`（死代码 + 重复实现） | 3 → **0** |
| 5 | `dup_bodies`（VO 投影重复） | 1 → 1（**已接受**，提取代价高于收益） |
| 6 | `redundant_guards`（冗余布尔判定） | 1 → **0** |

测试从 702 增至 **708**（+1 交叉区间回归守卫，+4 格式化器归一化，+1 血氧急危梯度）。
