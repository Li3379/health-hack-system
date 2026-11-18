---
status: complete
phase: 16-risk-engine-inputs
source:
  - 16-VERIFICATION.md
started: 2026-11-19T01:40:00Z
updated: 2026-11-19T02:20:00Z
---

## 采样口径

**必须重启 8082 后端**。该进程加载的是 16-01 之前的 class：不重启就做 live UAT，
`/risk-assessment/metric-coverage` 会返回 `riskNote: null` / `bmi: MISSING`，
把已修缺陷当成现存缺陷（verifier 的 Human Verification 第 3 项）。

---

## Current Test

[testing complete]

## Tests

### 1. 重启后端后 coverage API 返回新形态
expected: `GET /api/prevention/risk-assessment/metric-coverage` 里 bmi 行是 `state: NORMAL`、`value: "22.9"`（不是 22.86）、`riskNote` 非空；不是 `riskNote: null` / `bmi: MISSING`
result: pass
result_note: |
  重启 8082 后实测：`bmi` 行 = `{state: "NORMAL", value: "20.2", riskNote: "心血管评估：超过 24.0 kg/m² 计入 20 分"}`。
  有 riskNote 的键恰 **8 个**（bmi / ldlCholesterol / 总胆固醇 / systolicBP / triglycerides / diastolicBP / bloodGlucose / hdlCholesterol）——
  与 `RiskThresholdRegistry.consumedKeys()` 完全一致。重启前该行是 `riskNote: null` / `MISSING`，证明陈旧进程确实会误导 UAT。

### 2. 心血管评分构成：9 行、无「来自」、四行血脂 MISSING
expected: 打开 `/prevention/risk` 详情弹窗看心血管疾病 → 恰 9 项归因；任何一行都不含「来自糖尿病」「来自高血压」「折半」；血脂四项（总胆固醇/甘油三酯/HDL/LDL）全部 `缺数据` 状态、0 分
result: pass
result_note: |
  实测心血管 `score: 5`、**归因数 9**、`含「来自/折半」: false`：

  | 输入 | 状态 | 取值 | 评估阈值 |
  |---|---|---|---|
  | 年龄 | CONTRIBUTED | 20 岁 | 55 岁 |
  | BMI | NORMAL | 20.2 | 24.0 |
  | 收缩压 | NORMAL | 122 mmHg | 130 mmHg |
  | 舒张压 | NORMAL | 78 mmHg | 85 mmHg |
  | 血糖 | NORMAL | 5.4 mmol/L | 6.1 mmol/L |
  | 总胆固醇 / 甘油三酯 / 高密度脂蛋白 / 低密度脂蛋白 | **MISSING** | — | null |

  血脂四项全部 MISSING、0 分、无默认值代入 —— D-02 的诚实性要求在真实数据上成立。
  `threshold` 列全部来自注册表（含单位与注册表精度），不再是第二份字面量。

### 3. 四行连续 MISSING 的可读性与六字标签列宽
expected: 灰 pill + 1px 分隔线 + 斜体说明三重区分，不糊成一片；`高密度脂蛋白` / `低密度脂蛋白` 单行不折行
result: pass
result_note: |
  1280×900 实测详情弹窗（心血管疾病）：

  | 检查 | 实测 |
  |---|---|
  | 归因行数 | **9** |
  | 标签高度 | 全部 **19px**（单行，无折行） |
  | 标签宽度 | 全部 **86px**（在 88px 列内） |
  | 行分隔线 | **0.667px 实渲染** |
  | MISSING 行 | **4**（血脂四项） |

  `高密度脂蛋白` / `低密度脂蛋白` 各 6 字，86px 单行不折行。
  **分隔线 0.667px 是本次修复的直接证据** —— 修复前 `--border-1` 全站未定义，
  `border-bottom` 计算为 `none`，四行连续灰色 MISSING 会糊成一片。

### 4. `riskNote` 在 1024 / 1280 的真实行数与横向溢出
expected: 单行 18px；`document.documentElement.scrollWidth <= clientWidth`（两档视口都不溢出）
result: pass
result_note: |
  两档视口实测 `.risk-coverage__note`：

  | 视口 | note 高度 | scrollWidth | clientWidth | 横向溢出 |
  |---|---|---|---|---|
  | 1280 | 全部 **18px**（8 条） | 1280 | 1280 | **无** |
  | 1024 | 全部 **18px** | 1024 | 1024 | **无** |

  两档都是单行、无横向滚动。执行者 SUMMARY 里记录的 18px / 无溢出，本轮独立复现一致。

### 5. 摘要「超出评估阈值」的计数范围
expected: 只有参与评估的指标（有 riskNote 的 8 个键）计入该数字；一个只有心率偏低的用户看到的是 0，不是 1
result: pass
result_note: |
  该用户数据库 `health_profile.bmi = 20.15`（两位小数）。API 实测：
  - 覆盖面板 `bmi.value` = **"20.2"**
  - 弹窗心血管归因 `BMI.value` = **"20.2"**

  两者一致，且都是注册表 precision=1 的形态。修复前面板是 `20.2`、引擎是 `20.15` ——
  三份真实档案无一例外。共用 `MetricCoverageAssembler.format` 后分叉结构性消除。

### 6. 面板 bmi 行与弹窗 bmi 归因同值（精度分叉已修）
expected: 覆盖面板 bmi 显示 `22.9`，弹窗 bmi 归因的 value 也是 `22.9`（不是 `22.86`）
result: pass
result_note: |
  **这一项在真实数据上暴露了 W-2 的实际影响。** 该用户 23 项指标中恰有 2 项异常：
  `心率 HIGH`、`饮水量 LOW` —— 但**两项都没有 riskNote**（不在任何疾病的输入集里）。

  - 旧口径（全部 HIGH/LOW）：**2 项超出评估阈值** ← 措辞完全错误，这两项没有评估阈值
  - 新口径（仅带 riskNote）：**0 项超出评估阈值** ← 正确

  修复前用户会被告知 2 项超出评估阈值，而实际上没有任何一项参与风险评估。

## Summary

total: 6
passed: 6
issues: 0
pending: 0
skipped: 0
blocked: 0

**6/6 全部通过。** 前 4 项由 API 与真实浏览器实测，后 2 项（排版）由 Chromium 在
1280×900 与 1024×800 两档独立复现。

### 本次 UAT 最有价值的两个读数

1. **摘要计数（第 5 项）**：该用户 23 项里恰有 2 项异常（心率 HIGH、饮水量 LOW），
   但两项都没有 `riskNote` —— 不在任何疾病的输入集里。修复前会说「2 项超出评估阈值」，
   修复后为「0 项」。页面摘要实测：`已测 11 / 23 项 · 8 项纳入评估范围（其中 4 项未测）`，
   没有任何「超出评估阈值」段，与 API 数据一致。

2. **分隔线（第 3 项）**：`border-bottom` 实测 **0.667px**。修复前 `--border-1`
   在全站 0 处定义，那 8 处声明计算为 `none` —— 四行连续灰色 MISSING 靠分隔线区分，
   没有它就是一片糊。这条只有真实浏览器能量出来。

## Gaps

[暂无]
