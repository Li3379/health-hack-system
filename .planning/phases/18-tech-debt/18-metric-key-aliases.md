# Phase 18（部分完成） — 指标 Key 别名消除

**Status:** segment complete 2026-11-19（技术债清理的一部分，提前完成）
**Method:** autoresearch loop，segment 3，分支 `autoresearch/threshold-single-source`
**Baseline → 结果:** `alias_sites` **6 → 0**，跨 4 个文件

---

## 为什么先做这一项

这是本项目**已知会静默失效的第三种形态**，且是记忆里明确记录「已复发两次」的同一模式：

> 硬编码中文名或旧别名查 `metric_key` 恒落空、不报错只数值偏低

`dup_sites`（阈值语义）归零后，按章程的「更宽镜头」扫描发现了它。与前两次不同的是：**这次是英文别名，且影响面覆盖显示层与评分层。**

## 六处缺陷，无一例外

| 文件 | 别名 | 落空后果 |
|---|---|---|
| `MetricDisplayFormatter`（显示名） | `glucose`, `temperature` | 血糖/体温显示英文 key 给用户 |
| `MetricDisplayFormatter`（单位） | `glucose`, `temperature` | 单位返回空串 |
| `RealtimeController`（显示名） | `glucose`, `temperature` | 同上（重复实现） |
| `RealtimeController`（单位） | `glucose`, `temperature` | 同上（重复实现） |
| `DimensionScoreCalculator` | `glucose` | **血糖永不计入代谢维度惩罚 → 分数虚高** |
| `MetricDeduplicationServiceImpl` | `glucose` | **血糖永远用默认去重窗口** |

最讽刺的一处证据：`MetricDisplayFormatter` 写着

```java
case "temperature", "bodyTemperature" -> "体温";
```

作者**明明知道**规范键是 `bodyTemperature`，把两套都列了；但旁边一行只写了 `case "glucose"`，漏了 `bloodGlucose`。同一处、同一人、同一类疏漏。

## 机械指标与防作弊

`alias_sites` —— 被用作匹配臂、且**不是规范键**的字符串字面量数。

**规范真值不硬编码**：从 `MetricKeyNormalizer` 自身的 `canonical.add(...)` 与 `aliasGroup.put(...)` 源码解析出来，指标无法与注册表漂移。实测解析出 **23 个规范键**，与 SSOT 一致。

**已扫描的作弊路径**（修完后复查，全部为空）：

```bash
grep -rE 'case "(glucose|temperature|spo2|sbp|dbp|tg|hdl|ldl|ua|cr|temp|pulse|step|glu)"'
grep -rE '\.equals\("(glucose|temperature|spo2|sbp|dbp)"\)'
```

## 四轮迭代

| run | status | alias_sites | 改动 |
|---|---|---|---|
| 1 | noop | 6 | 基线 |
| 2 | keep | 4 | `MetricDisplayFormatter` 两个 switch 前归一化 |
| 3 | keep | 2 | `RealtimeController` **删除重复 switch，委托给 formatter** |
| 4 | keep | **0** | `DimensionScoreCalculator` + `MetricDeduplicationServiceImpl` 归一化；补 `MetricDisplayFormatterTest` |

## 关键修法差异

**不是简单替换别名**。历史数据里可能真的存着 `glucose`。正确做法是**匹配前归一化**：

```java
String canonicalKey = MetricKeyNormalizer.toCanonicalKey(metricKey);
return switch (canonicalKey) { ... };
```

这样规范键与所有别名都能命中，且未来新增别名自动生效。

`RealtimeController` 更进一步：它的两个私有 switch 是 `MetricDisplayFormatter` 的**劣化子集**（缺血脂四项、血氧、空腹血糖），所以**删掉整份实现改为委托** —— 一次消除两处缺陷，而不是并行修两份。

## 新增测试缺口

**`MetricDisplayFormatter` 此前没有任何测试** —— 这正是同一个 bug 能横跨 4 个文件而不被发现的根本原因。新增 `MetricDisplayFormatterTest`（4 条）：

- 规范键与别名解析为同一中文名（血糖 / 体温，含 `BLOOD_GLUCOSE`、`TEMP`）
- 单位查询同样归一化，别名不落空串
- 未识别键原样返回

测试刻意用 `new MetricDisplayFormatter(null)` —— 强制走 switch 兜底路径，也就是**唯一曾经出问题的那条路**。有 registry 时 `getDefinition` 会代为归一化，反而掩盖缺陷。

**变异验证**：去掉归一化，2 条测试立刻变红。

## 验证

| 检查 | 结果 |
|---|---|
| 后端全量测试 | **707 / 0 / 0**（703 + 4 新增） |
| Checkstyle | 0 violations |
| `alias_sites` | 6 → **0** |
| `canonical_keys`（解析自 SSOT） | 23 |
| 其他别名作匹配臂 | 0 |
| `.equals()` 于遗留别名 | 0 |

## 未做的部分

Phase 18 原列的其他项**尚未处理**：

- `RiskAnalyzer` —— 零引用死代码（`grep` 确认仅自身声明）
- `RiskScorer` 改读归因快照
- `FileUploadController` 英文泄漏（初步 grep 未发现，需细查）
- **新发现的死逻辑**：`AiParseServiceImpl:505` 的 `isOxygenCritical` 完全冗余 —— `bloodOxygen` 的 panic 为 `{90, null}`，`isCritical` 已涵盖 `value <= 90`
