# Phase 06: 前端非破坏性录入引导、四梯度宽容校验与友好反馈 - Research

**Researched:** 2026-11-04  
**Domain:** 前端录入体验重构、生理四梯度校验模型、双字段协同联动与危急值二次确认 (Vue 3, Element Plus, Pinia, TypeScript, Vitest)  
**Confidence:** HIGH  

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01 (彻底移除失焦抹除):** 彻底删除 `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue` 中的失焦清空逻辑（移除 `handleValueBlur` 与 `handleDiastolicBlur` 中的 `form.value.value = undefined` 与 `form.value.diastolicValue = undefined`）。用户键入的任何数值均被忠实保留，绝不在用户失焦或切换输入框时清空输入。
- **D-02 (四梯度实时评估机制):** 创建可复用的输入评估逻辑（或 Composable / 工具函数），根据当前指标的 `MetricDefinitionVO` 评估输入值：
  - **Tier 1 正常 (Normal):** `normalMin <= val <= normalMax`。展示绿色状态徽章「正常参考区间」，对勾图标。
  - **Tier 2 预警偏差 (Warning):** `hardMin <= val < normalMin` 或 `normalMax < val <= hardMax`（未达危急线）。展示橙黄色状态胶囊「指标偏高/偏低」，附带临床测量状态说明（如空腹/餐后差异），**完全不阻断用户保存入库**。
  - **Tier 3 急危重症 (Critical):** 达到临床危急重症预警线。展示橙红色危急胶囊「严重生理异常」，提示核对测量，点击保存时触发二次确认模态弹窗（D-05）。
  - **Tier 4 生理不可能极限 (Hard Bound):** `val < hardMin` 或 `val > hardMax`。展示红色警示胶囊「超出人体存活极限」，禁用保存按钮，给出结构化提示（例如“心率有效生理区间为 20 ~ 300 次/分，请核对是否多输了数字”），但不清空字段。
- **D-03 (元数据动态绑定与参考卡片):** 废除 `QuickInputDialog.vue` 内部硬编码的 `METRIC_RANGES` 常量对象，全面替换为 `useMetricMetaStore().getDefinition(metricKey)`：
  - 输入框 `placeholder` 动态渲染为 `参考范围: ${def.normalMin} ~ ${def.normalMax} ${def.unit}`；
  - `el-input-number` 动态绑定 `:precision="def.precision"` 与 `:step="def.step"`；
  - 在表单下方集成常态化参考解读卡片（`ReferenceGuideCard`），清晰展示当前指标的正常参考区间、测量注意事项及 `guideHint` 临床指导文案。
- **D-04 (血压协同录入与一键对调):** 
  - 录入血压时，表单采用网格（Grid/Row）同屏并排展示收缩压与舒张压两个输入框，各自具备独立的参考引导；
  - 当同时录入高压和低压，且出现 `收缩压 <= 舒张压` 的生理倒置时（如高压误填 80，低压误填 120），表单下方立即展示内联友好警示条：“检测到收缩压（高压）低于舒张压（低压），疑似输入颠倒”；
  - 警示条右侧内嵌「一键对调数值」操作按钮，用户点击后瞬间交换高压与低压数值，并通过轻提示通知用户，消除倒置报错。
- **D-05 (急危重症二次确认弹窗):**
  - 定义常见核心指标的临床危急重症阈值（Panic Limits）：
    - 收缩压 (systolicBP) >= 180 或 <= 70 mmHg；舒张压 (diastolicBP) >= 110 或 <= 40 mmHg；
    - 空腹/普通血糖 (bloodGlucose) >= 16.7 或 <= 2.8 mmol/L；
    - 心率 (heartRate) >= 140 或 <= 45 次/分；
    - 体温 (bodyTemperature) >= 39.5 或 <= 35.0 °C；
    - 血氧 (bloodOxygen) <= 90%。
  - 当数值处于危急重症区间时，点击保存弹出友好的确认对话框：说明当前数值属于重度异常，提示防误触并提供急救或就医建议。提供两个操作选项：“返回核对修改”（主按钮）与“确认录入无误并保存”（次按钮）。用户选择确认后数据正常保存入库。

### Claude's Discretion
- `QuickInputDialog.vue` 内部四梯度胶囊视觉样式与 CSS 渐变微动画；
- 状态胶囊评估逻辑的封装形式（组件内部计算属性或抽取为组合式函数 `useMetricValidation`）；
- 针对 Element Plus 组件在深色模式下的对比度适配。

### Deferred Ideas (OUT OF SCOPE)
- **Phase 7 承接：** AI 智能对话文本提取 (`AiParseServiceImpl.java`) 与 OCR 体检报告识别 (`OcrServiceImpl.java`) 接入统一元数据与四梯度校验。
- **v1.2 规划：** 个性化基线定制（结合病史针对特定患者放宽或收紧正常区间，如老年糖尿病患者）。
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| GUIDE-01 | `QuickInputDialog.vue` 与各录入表单根据指标元数据动态绑定标准单位、推荐步长（step）与小数精度（precision），输入框占位符显式展示生理参考范围（如“参考范围：3.9 ~ 6.1 mmol/L”） | 通过 `useMetricMetaStore().getDefinition(metricKey)` 动态获取 `precision`, `step`, `unit`, `normalMin`, `normalMax`，动态生成 `:placeholder="`参考范围: ${def.normalMin} ~ ${def.normalMax} ${def.unit}`"` |
| GUIDE-02 | 在输入框下方或侧边常态化展示健康参考标准解读卡片（如正常指标参考值区间、常见测量场景注意事项） | 新建 `ReferenceGuideCard.vue` 常态化组件，挂载于录入表单下方，结合 `def.guideHint` 与特定临床场景（空腹/餐后、静息坐位等）渲染专业解读卡片 |
| GUIDE-03 | 血压录入支持收缩压（高压）与舒张压（低压）同屏联动展示与双向关联校验 | 在 `QuickInputDialog.vue` 中针对血压指标采用网格栅格同屏并排渲染收缩压（高压）与舒张压（低压），分别注入元数据定义，实现双字段同步评估 |
| VAL-01 | 彻底废除失焦清空输入框机制（移除 `form.value.value = undefined`），输入异常时保留用户键入原值，绝不强制抹除用户劳动 | 彻底删除 `handleValueBlur` 与 `handleDiastolicBlur` 中的破坏性抹除赋值，所有异常反馈均通过非阻塞胶囊与响应式校验呈现，用户键入原值始终保留 |
| VAL-02 | 建立“正常 → 异常警示（允许保存）→ 急危重症（二次确认后允许保存）→ 物理不可能极限（拦截）”的四梯度模型，严禁将高血压、高血糖等真实病理数据当作表单非法输入拦截 | 实现无状态评估引擎 `src/utils/metricValidation.ts`，精准划分 Tier 1 (正常)、Tier 2 (预警偏差-非阻塞)、Tier 3 (急危重症-二次确认)、Tier 4 (生理不可能极限-表单拦截)，并对齐后端 `MetricRecordValidator.java` |
| VAL-03 | 当输入超出正常参考范围时，以非阻塞的友好黄色/橙色状态胶囊及解释性文案替代生硬的红色报错（说明可能原因、测量状态区别，或单位误换算提示） | 构建状态胶囊视觉系统：Tier 2 触发时呈现 Warning 风格胶囊与临床原理解释文案，表单校验调用 `callback()` 正常放行，允许直接保存 |
| VAL-04 | 检测到收缩压低于舒张压等生理倒置情况时，给出清晰的逻辑解释，并提供“一键对调高低压数值”的便捷自纠按钮 | 实时监听 `form.value <= form.diastolicValue` 倒置状态，内联展示友好警示横幅与“一键对调数值”自纠按钮，点击后原子交换字段值并提示恢复正常 |
| VAL-05 | 录入达到严重危急值区间时，弹出友好的二次确认模态窗（提示防误触，同时提供急救与就医建议），用户点击“确认录入”后仍允许正常保存入库 | 构建 `CriticalConfirmDialog.vue`（或表单保存拦截中间件），定义核心指标 Panic Limits，拦截保存动作弹出友好警示窗并提供急救就医建议，用户二次确认后继续提交入库 |
</phase_requirements>

## Summary

本阶段的核心使命是彻底解决健康监控平台在数据录入阶段存在的“误拦真病”、“失焦抹除”、“缺乏指引”三大交互顽疾。在原有实现中，`QuickInputDialog.vue` 存在 `form.value.value = undefined` 破坏性清空逻辑，并在表单验证规则中将收缩压高于 200 mmHg、血糖高于 25 mmol/L 的危急重症数据视同表单非法输入直接拦截，严重背离了医疗健康监测系统的核心目标。

通过 Phase 04 交付的后端 `HealthMetricMetaRegistry` (SSOT) 与 Phase 05 交付的前端 `useMetricMetaStore` (含 23 项指标静态离线字典 `FALLBACK_METRIC_META`)，本阶段将建立一套高度可复用、响应迅速、医学合规的**前端生理四梯度宽容校验与引导交互体系**：
1. **纯函数校验引擎 (`src/utils/metricValidation.ts`)**：提供无副作用的 Tier 1~Tier 4 分级评估、Panic Limits 判定、急救文案解析与血压倒置检测；
2. **响应式组合层 (`src/composables/useMetricValidation.ts`)**：封装对元数据 Store 的响应式检索、双字段联动校验及数值原子对调动作；
3. **友好解释性交互组件 (`ReferenceGuideCard.vue` & `CriticalConfirmDialog.vue`)**：常态化渲染测量参考卡片，并在急危重症录入时进行非阻塞的防误触二次确认与急救就医指引；
4. **彻底重构 `QuickInputDialog.vue`**：移除硬编码 `METRIC_RANGES`、删除失焦清空、接入自适应精度步长、重塑双字段同屏血压录入。

**Primary recommendation:** 将四梯度生理评估规则提取为纯函数工具 `src/utils/metricValidation.ts`，在前端表单层中使 Tier 2（预警）与 Tier 3（危急）的表单验证器 `validator` 均返回通过（调用 `callback()`），仅由 Tier 4（物理不可能）返回 `callback(new Error(...))` 阻断提交，而在保存入口处通过模态弹窗拦截 Tier 3 危急值进行二次确认，彻底实现非破坏性、宽容而安全的录入闭环。

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| **非破坏性输入保留 (VAL-01)** | Browser / Client (`QuickInputDialog.vue`) | — | 输入状态与焦点事件完全受控于客户端 DOM 与 Vue 响应式状态。彻底废除失焦抹除逻辑，确保用户键盘录入的字符不被意外销毁。 |
| **四梯度宽容校验评估 (VAL-02, VAL-03)** | Browser / Client (`src/utils/metricValidation.ts`) | API / Backend (`MetricRecordValidator.java`) | 客户端在用户键入时进行 0ms 毫秒级梯度分流与状态胶囊渲染；后端以 Jakarta Validation 兜底物理极限拦截，双端规则 100% 同源对齐。 |
| **元数据驱动与占位符引导 (GUIDE-01)** | Browser / Client (`useMetricMetaStore`) | API / Backend (`GET /api/metrics/metadata`) | 前端直接从 Pinia Store 读取指标精度、步长、单位与正常区间，弱网/离线时由 `FALLBACK_METRIC_META` 静态兜底，避免网络波动引起渲染空白。 |
| **常态化医学解读指南 (GUIDE-02)** | Browser / Client (`ReferenceGuideCard.vue`) | — | 将临床测量规范与参考区间直接嵌入输入界面下方，作为纯前端解释性 UI 呈现，不产生后端交互开销。 |
| **血压同屏联动与一键自纠 (GUIDE-03, VAL-04)** | Browser / Client (`QuickInputDialog.vue`) | — | 血压双字段网格布局、倒置实时判定 (`systolic <= diastolic`) 以及原子互换 (`swap`) 完全属于前端用户交互操作。 |
| **危急重症二次确认与急救指引 (VAL-05)** | Browser / Client (`CriticalConfirmDialog.vue`) | API / Backend (智能预警与推送子系统) | 保存前在浏览器端进行交互式防误触确认与紧急指引；入库后由后端事件分发系统异步触发多渠道警报。 |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| **Vue** | `^3.4.15` [VERIFIED: package.json:28] | 响应式前端框架 | 项目统一技术栈，基于 Composition API `<script setup>` 构建模块化组件与响应式计算属性 |
| **Element Plus** | `^2.5.6` [VERIFIED: package.json:23] | 企业级 UI 组件库 | 现存全站组件基座，提供 `el-dialog`, `el-form`, `el-input-number`, `el-tag`, `el-alert`, `el-button` 等完备控件 |
| **Pinia** | `^2.1.7` [VERIFIED: package.json:26] | 状态管理库 | 管理全局 `useMetricMetaStore`，提供响应式指标元数据字典缓存与离线回退 |
| **TypeScript** | `~5.5.0` [VERIFIED: package.json:48] | 强类型开发语言 | 保证 `MetricDefinitionVO` 与校验状态接口契约的类型安全 |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **@element-plus/icons-vue** | `^2.3.1` [VERIFIED: package.json:18] | 官方矢量图标库 | 提供 `Check` (正常), `Warning` (预警), `Bell` (危急), `CircleClose` (超限), `Switch` (血压对调) 等语义图标 |
| **Vitest** | `^4.1.0` [VERIFIED: package.json:52] | 前端单元测试框架 | 执行快速单元测试，秒级运行校验规则矩阵与组件行为断言 |
| **@vue/test-utils** | `^2.4.6` [VERIFIED: package.json:39] | Vue 官方测试工具 | 挂载 `QuickInputDialog`，模拟键入、失焦、对调点击与保存确认流 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| 纯函数 `metricValidation.ts` + 组合式函数 | 外部校验库（如 `yup` 或 `zod`） | 外部校验库增加依赖打包体积，且其默认行为倾向于“一票否决抛出错误”，难以优雅支持“非阻塞黄色胶囊”与“急危重症二次确认”的多态生理梯度分流；自研纯函数零外部依赖，100% 对齐已有 `MetricDefinitionVO` |
| 独立组件 `CriticalConfirmDialog.vue` | `ElMessageBox.confirm` 命令式调用 | `ElMessageBox.confirm` 若需展示复杂的急救指引排版与红框高亮，需使用 `dangerouslyUseHTMLString: true` 或手写 `h()` 渲染函数，不仅存在 XSS 风险，且样式难以受控；专用 Vue 弹窗组件语义清晰、样式易定制、测试极易断言 |

**Installation:**
本阶段**无需安装任何新的外部依赖**，所有核心库与图标库均已就绪并锁定在 `hhs-frontend-v2/package.json` 中。

---

## Package Legitimacy Audit

> 针对既有栈依赖项执行合法性审计。本阶段不引入任何新第三方依赖。

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `element-plus` | npm | 4 yrs | 769k/wk | github.com/element-plus/element-plus | [OK] [VERIFIED: npm registry] | Approved (既有依赖，继续使用) |
| `vue` | npm | 12 yrs | 24.8M/wk | github.com/vuejs/core | [OK] [VERIFIED: npm registry] | Approved (核心框架，继续使用) |
| `pinia` | npm | 5 yrs | 7.6M/wk | github.com/vuejs/pinia | [OK] [VERIFIED: npm registry] | Approved (状态管理，继续使用) |
| `@element-plus/icons-vue` | npm | 3 yrs | 1.8M/wk | github.com/element-plus/element-plus-icons | [OK] [VERIFIED: npm registry] | Approved (图标资产，继续使用) |

**Packages removed due to [SLOP] verdict:** None  
**Packages flagged as suspicious [SUS]:** None  

---

## Architecture Patterns

### System Architecture Diagram

```
+----------------------------------------------------------------------------------------------------+
|                                    User Interaction (Browser Tier)                                 |
+----------------------------------------------------------------------------------------------------+
                                      |
                                      | 1. Select Metric (e.g. 血压, 血糖, 心率)
                                      v
+----------------------------------------------------------------------------------------------------+
| QuickInputDialog.vue                                                                              |
|                                                                                                    |
|   +--------------------------------------------------------------------------------------------+   |
|   | Metadata Resolution: useMetricMetaStore.getDefinition(metricKey)                           |   |
|   | -> precision, step, unit, hardMin, hardMax, normalMin, normalMax, placeholder, guideHint   |   |
|   +--------------------------------------------------------------------------------------------+   |
|                                                                                                    |
|   +--------------------------------------------------------------------------------------------+   |
|   | Dual-Field Layout (if systolicBP):                                                         |   |
|   | [ 收缩压(高压) input-number ]                 [ 舒张压(低压) input-number ]                 |   |
|   |                                                                                            |   |
|   | Normal Metric Layout:                                                                      |   |
|   | [ 动态 precision/step 输入框: placeholder="参考范围: 3.9 ~ 6.1 mmol/L" ]                     |   |
|   +--------------------------------------------------------------------------------------------+   |
|                                      |                                                             |
|                                      | 2. User Types Number (Realtime Evaluation)                  |
|                                      v                                                             |
|   +--------------------------------------------------------------------------------------------+   |
|   | useMetricValidation / evaluateMetricValue                                                  |   |
|   |                                                                                            |   |
|   |  [Inversion Check] -> systolic <= diastolic ?                                              |   |
|   |         |-- Yes: Display Inline Alert Banner + [ 一键对调数值 ] Button                     |   |
|   |                                                                                            |   |
|   |  [Four-Tier Evaluation]:                                                                   |   |
|   |    Tier 1: normalMin <= val <= normalMax  --> 绿色正常胶囊 (对勾, 正常区间)                 |   |
|   |    Tier 2: In [hardMin, hardMax] but out  --> 橙黄预警胶囊 (感叹号, 偏高/偏低, 允许保存)     |   |
|   |    Tier 3: Hits Clinical Panic Limit      --> 橙红危急胶囊 (响铃, 严重异常, 二次确认)       |   |
|   |    Tier 4: val < hardMin || val > hardMax --> 暗红超限胶囊 (叉号, 存活极限, 拦截保存)       |   |
|   +--------------------------------------------------------------------------------------------+   |
|                                                                                                    |
|   +--------------------------------------------------------------------------------------------+   |
|   | ReferenceGuideCard.vue (常态化参考卡片)                                                    |   |
|   | - 临床标准参考区间与生理极限                                                               |   |
|   | - guideHint 与特定临床场景注意事项（空腹/餐后、静息坐位等）                                |   |
|   +--------------------------------------------------------------------------------------------+   |
|                                      |                                                             |
|                                      | 3. User Clicks "保存" (Submit Action)                       |
|                                      v                                                             |
|   +--------------------------------------------------------------------------------------------+   |
|   | Submit Interceptor Flow:                                                                   |   |
|   |   - Tier 4 active or Inverted? -> Form Validation Blocks with Helpful Error (NO ERASE!)    |   |
|   |   - Tier 3 active?                                                                         |   |
|   |       |-- Yes -> Show CriticalConfirmDialog (返回修改 vs 确认录入无误并保存)               |   |
|   |       |-- No  -> Proceed to Save immediately                                               |   |
|   +--------------------------------------------------------------------------------------------+   |
+----------------------------------------------------------------------------------------------------+
                                      |
                                      | 4. HTTP POST /api/metrics/health or /wellness
                                      v
+----------------------------------------------------------------------------------------------------+
| Backend Tier: MetricRecordValidator.java (@ValidMetricRecord) & HealthMetricMetaRegistry           |
| -> Pathological data passes safely; impossible data intercepted with structured guidance           |
+----------------------------------------------------------------------------------------------------+
```

### Recommended Project Structure
```
hhs-frontend-v2/src/
├── utils/
│   ├── metricNormalizer.ts           # [Existing] Key 规范化与别名查找
│   ├── metricValidation.ts           # [NEW] 纯函数：四梯度分流、危急值判定、急救文案与对调逻辑
│   └── format.ts                     # [Existing] 指标名称与单位格式化
├── composables/
│   └── useMetricValidation.ts        # [NEW] 组合式函数：响应式元数据检索、双向状态评估与一键对调
├── views/data-input/components/
│   ├── ReferenceGuideCard.vue        # [NEW] 常态化健康参考标准解读卡片组件
│   ├── CriticalConfirmDialog.vue     # [NEW] 急危重症二次确认与急救指引模态弹窗组件
│   ├── QuickInputDialog.vue          # [REFACTOR] 核心改造：彻底移除失焦抹除，接入四梯度与双向联动
│   └── QuickInput.vue                # [Existing/Tweak] 快捷录入入口按钮组
└── __tests__/
    ├── utils/
    │   └── metricValidation.spec.ts  # [NEW] 单元测试：覆盖四梯度阈值、Panic Limits、倒置对调
    └── views/
        └── QuickInputDialog.spec.ts  # [NEW] 组件集成测试：测试非破坏性失焦、血压对调与二次确认
```

### Pattern 1: Pure Evaluation Engine with Lenient Form Bridge
**What:** 将医学业务逻辑（正常值、生理极限、危急值）封装为无状态纯函数 `evaluateMetricValue`，与 Element Plus 表单验证规则解耦。  
**When to use:** 在需要对表单进行实时健康评估，但绝不能因为“指标偏高/偏低”而直接让 Element Plus 的 `validate` 失败的场景。  
**Details:**  
- Element Plus 的 `rules.value` 验证器：
  - 若处于 Tier 4（物理不可能极限），调用 `callback(new Error(evalResult.message))`，并在界面展示暗红警示条；
  - 若处于 Tier 1、Tier 2 或 Tier 3，验证器直接调用 `callback()` 表示通过！
  - 用户的键入值永远不被擦除（保留在 `form.value` 中）。

### Pattern 2: Dual-Field Blood Pressure Synchronization & Atomic Swapping
**What:** 当录入指标为血压（`systolicBP`）时，同屏渲染收缩压与舒张压两列输入框。实时计算 `isInverted = computed(() => systolic != null && diastolic != null && systolic <= diastolic)`。  
**When to use:** 针对存在生理配对关系的健康数据（如高压/低压）。  
**Details:**  
- 倒置时，呈现柔和的黄色/橙色警示横幅：`检测到收缩压 (${form.value} mmHg) 低于舒张压 (${form.diastolicValue} mmHg)，疑似输入颠倒`；
- 横幅内嵌「一键对调数值」按钮，点击时执行临时变量对调：
  ```ts
  const temp = form.value.value
  form.value.value = form.value.diastolicValue
  form.value.diastolicValue = temp
  ElMessage.success('已对调收缩压与舒张压数值')
  ```
- 对调后由于计算属性响应式刷新，警示横幅自动平滑消失，消除报错。

### Pattern 3: Intercept-on-Submit for Critical Value Confirmation
**What:** 将急危重症二次确认机制置于 `handleSubmit` 的流程前置门禁中，而非每次按键都弹窗打扰用户。  
**When to use:** 录入数据达到严重危急值区间（如收缩压 >= 180 mmHg，血糖 >= 16.7 mmol/L）。  
**Details:**  
- 用户键入危急值时，输入框下方仅展示橙红色高亮胶囊提示其为危急值；
- 只有当用户点击“保存”时，才触发拦截并弹出 `CriticalConfirmDialog`；
- 弹窗中提供两项选择：
  1. “返回核对修改”（主按钮，自动聚焦）：取消提交，保留当前所有已输入内容；
  2. “确认录入无误并保存”（次按钮）：关闭弹窗并继续执行原本的 `healthApi.createMetric` 保存入库逻辑。

### Anti-Patterns to Avoid
- **Anti-Pattern 1: 失焦强制重置用户输入 (Destructive Blur Wipeout)**  
  *Existing code:* `handleValueBlur() { form.value.value = undefined; }`  
  *Why bad:* 用户误输一位数字（如 1200）或血压偏高（150），焦点移开直接把整个输入框清空，导致用户必须从头重输，产生极强挫败感。  
  *What to do instead:* 100% 保留用户键入内容，通过界面状态胶囊和表单禁用按钮提示用户修改。
- **Anti-Pattern 2: 绑定 `:min` 和 `:max` 导致 `el-input-number` 静默强截断 (Silent Clamping)**  
  *Why bad:* 在 Element Plus 的 `el-input-number` 上直接绑定 `:max="hardMax"`，当用户误粘贴或键入 1200 时，组件失焦时会静默将数值改写为 300（最大值），导致用户可能在毫无察觉的情况下将篡改后的错误数值 300 保存入库！  
  *What to do instead:* 不在 `el-input-number` 上硬编码拦截边界，或者设置合理的宽松上限，通过业务层的 Tier 4 校验来拦截，显式告知用户“您输入了 1200，有效生理区间为 20 ~ 300，请核对是否多输了数字”，绝不静默改写。
- **Anti-Pattern 3: 将高血压/高血糖当作表单校验错误拦截 (Treating Sickness as Form Error)**  
  *Why bad:* 传统表单常将 `normalMax` 作为表单上限，导致真实高血压（如 160 mmHg）或高血糖（如 12.5 mmol/L）无法录入，严重违反医疗系统初衷。  
  *What to do instead:* 正常范围之外（非物理极限）划归为 Tier 2 预警或 Tier 3 危急，允许且支持正常保存入库。

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 指标元数据与范围定义 | 组件内部私有 `METRIC_RANGES` 字典 | `useMetricMetaStore().getDefinition(metricKey)` + `FALLBACK_METRIC_META` | Phase 04/05 已建立全系统唯一权威注册中心，自建私有字典会导致指标名称、步长、单位和范围在各页面割裂不一致 |
| Key 规范化与缩写兼容 | 自写正则替换或硬编码 if-else | `toCanonicalKey` from `@/utils/metricNormalizer` | 已收录所有别名及医学缩写（`sbp`, `dbp`, `spo2`, `glu` 等），具备保护机制 |
| 弹窗二次确认与布局 | 原生浏览器 `window.confirm` 或纯文字提示 | `CriticalConfirmDialog.vue` (基于 `el-dialog`) | 原生 `window.confirm` 无法排版医学急救指南，无法适配深色模式与移动端自适应，且阻塞主线程 |
| 状态胶囊色彩与徽章样式 | 纯行内硬编码 style | Element Plus CSS 变量 (`--el-color-success`, `--el-color-warning`, `--el-color-danger`) 与语义类 | 保证 Element Plus 全局主题换肤与深色模式（Dark Mode）下的最佳对比度和一致性 |

**Key insight:** 健康指标校验的本质不是“输入框字符格式检查”，而是“临床生理梯度的智能化分流”。依托既有的元数据注册中心与语义化状态机，可以做到零外部笨重依赖、毫秒级响应与零网络开销。

---

## Runtime State Inventory

> 本阶段为前端录入引导、宽容校验与反馈体验重构，涉及既有组件重构与新增校验工具。

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| **Stored data** | None — 后端数据库表 `health_metrics` 与 `wellness_metrics` 已经支持标准 `metricKey` 与数值存储，Phase 4 已经完成历史 Key 数据归一化清洗 [VERIFIED: .planning/phases/04-key/04-02-SUMMARY.md:38-42] | None (无需数据迁移) |
| **Live service config** | None — 无外部平台 UI 配置依赖 | None |
| **OS-registered state** | None — 无操作系统级注册服务 | None |
| **Secrets/env vars** | None — 前端无新增环境变量 | None |
| **Build artifacts** | `hhs-frontend-v2/dist/` 打包构建产物将由前端构建更新 | 执行 `npm run build` 重新编译构建 [VERIFIED: CLAUDE.md:53-54] |

**Nothing found in category:** 存储数据、实时服务配置、系统注册状态与环境变量均无需调整。

---

## Common Pitfalls

### Pitfall 1: `el-input-number` 失焦自动纠偏（Clamping）导致数据篡改
**What goes wrong:** 若给 `el-input-number` 设置了 `:min="def.hardMin"` 和 `:max="def.hardMax"`，当用户手误输入 350（心率）时，Element Plus 组件在失焦时会自动把输入框的数值强行修改为 300，并在界面显示 300。用户可能根本没有注意到数字被改动，直接点击保存，导致篡改后的数据入库。  
**Why it happens:** `el-input-number` 内部默认开启边界钳制逻辑。  
**How to avoid:** 不要对 `el-input-number` 强制绑定严格的物理极限界限，或使用自定义校验器处理超限情况；让输入框保留用户输入的原始 350，由 Tier 4 状态胶囊提示“有效区间为 20 ~ 300，请核对是否多输了数字”，并禁用保存按钮，迫使用户主动删除多余字符。  
**Warning signs:** 单元测试中输入超大数值后，表单模型的 `form.value` 变成了最大值而不是原始输入值。

### Pitfall 2: Element Plus Form `validator` 中对 Tier 2 / Tier 3 错误调用 `callback(new Error(...))`
**What goes wrong:** 在编写自定义验证规则时，习惯性地将“偏高/偏低”写为 `callback(new Error('指标偏高'))`。这样会导致 `formRef.value.validate()` 返回 `false`，导致高血压或危急重症患者完全无法保存数据。  
**Why it happens:** 混淆了“数据有效性（是否属于人体可能数值）”与“生理病理状态（是否属于正常区间）”。  
**How to avoid:** 只有在 Tier 4（物理不可能极限）或收缩压/舒张压倒置未修正时，才调用 `callback(new Error(...))`；Tier 2 与 Tier 3 必须无条件调用 `callback()` 放行表单验证！  
**Warning signs:** 无法成功保存高于正常参考范围的数值（如收缩压 150 mmHg 或空腹血糖 8.0 mmol/L）。

### Pitfall 3: 血压录入时收缩压与舒张压的非对称提交
**What goes wrong:** 用户录入收缩压 130 和舒张压 85 时，如果后台保存逻辑未统一处理，可能导致只保存了收缩压而遗漏了舒张压，或者并发请求时产生竞态。  
**Why it happens:** 原有代码中舒张压是在保存收缩压之后追加一次 `healthApi.createMetric(diastolicData)`，若收缩压成功而舒张压网络失败，会导致数据半提交。  
**How to avoid:** 在 `QuickInputDialog.vue` 中封装提交逻辑，统一使用 `Promise.all` 或串行顺序保存，并在发生任一失败时给出明确的反馈提示；同时确保对调时两个字段的值同步原子交换。  
**Warning signs:** 最近录入列表中只出现收缩压而没有对应的舒张压记录。

### Pitfall 4: 0 值的空值判断漏洞（Falsy Check Bug）
**What goes wrong:** 对于某些保健指标（例如运动时长 `exerciseMinutes` 或步数 `steps`），用户录入 `0`（今日未运动）是完全合法的生理数值，但如果代码中使用 `if (!form.value)` 进行判断，会将 `0` 判定为未填写，从而阻断提交或展示错误的胶囊。  
**Why it happens:** JavaScript 中 `0` 为 falsy 值。  
**How to avoid:** 严格使用 `val === undefined || val === null || val === '' || isNaN(val)` 来判断空输入。  
**Warning signs:** 输入 0 时提示“请输入数值”。

---

## Code Examples

### 1. In-Repo Verbatim Source References

以下引文均为本次会话通过 `Read` 工具直接读取的源码真实定义，禁止转义与推测：

#### (1) 前端指标元数据接口规范
```typescript
// [VERIFIED: hhs-frontend-v2/src/types/api.ts:424-438]
// 指标元数据相关
export interface MetricDefinitionVO {
  canonicalKey: string
  aliases: string[]
  displayName: string
  category: 'HEALTH' | 'WELLNESS'
  unit: string
  precision: number
  step: number
  hardMin: number
  hardMax: number
  normalMin: number
  normalMax: number
  placeholder: string
  guideHint: string
}
```

#### (2) 必须彻底删除的既有失焦抹除代码
```typescript
// [VERIFIED: hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue:181-200]
const handleValueBlur = () => {
  const val = form.value.value
  if (val === undefined || val === null) return
  const r = currentRange.value
  if (val < r.min || val > r.max) {
    ElMessage.warning(`数值超出有效范围 ${r.min} - ${r.max}，请重新输入`)
    form.value.value = undefined
    formRef.value?.validateField('value')
  }
}

const handleDiastolicBlur = () => {
  const val = form.value.diastolicValue
  if (val === undefined || val === null) return
  if (val < 40 || val > 200) {
    ElMessage.warning(`舒张压超出有效范围 40 - 200，请重新输入`)
    form.value.diastolicValue = undefined
    formRef.value?.validateField('diastolicValue')
  }
}
```

#### (3) 后端物理极限拦截错误文案规范
```java
// [VERIFIED: hhs-backend/src/main/java/com/hhs/validation/MetricRecordValidator.java:64-72]
            String errorMessage = def.displayName()
                    + "有效生理区间为 "
                    + minStr
                    + " ~ "
                    + maxStr
                    + " "
                    + unitStr
                    + "，请核对后重新输入";
```

#### (4) 血压权威元数据定义
```typescript
// [VERIFIED: hhs-frontend-v2/src/constants/metricMeta.ts:24-53]
  systolicBP: {
    canonicalKey: 'systolicBP',
    aliases: ['systolic_bp', 'systolicBp', 'SYSTOLIC_BP', 'sbp', 'SBP'],
    displayName: '收缩压',
    category: 'HEALTH',
    unit: 'mmHg',
    precision: 0,
    step: 1,
    hardMin: 40,
    hardMax: 300,
    normalMin: 90,
    normalMax: 139,
    placeholder: '例如: 120',
    guideHint: '正常收缩压 90 ~ 139 mmHg'
  },
  diastolicBP: {
    canonicalKey: 'diastolicBP',
    aliases: ['diastolic_bp', 'diastolicBp', 'DIASTOLIC_BP', 'dbp', 'DBP'],
    displayName: '舒张压',
    category: 'HEALTH',
    unit: 'mmHg',
    precision: 0,
    step: 1,
    hardMin: 20,
    hardMax: 200,
    normalMin: 60,
    normalMax: 89,
    placeholder: '例如: 80',
    guideHint: '正常舒张压 60 ~ 89 mmHg'
  },
```

---

### 2. 四梯度校验引擎核心实现 (`src/utils/metricValidation.ts`)

```typescript
import type { MetricDefinitionVO } from '@/types/api'

export type MetricTier = 'NORMAL' | 'WARNING' | 'CRITICAL' | 'HARD_BOUND'

export interface MetricEvaluation {
  tier: MetricTier
  status: 'success' | 'warning' | 'danger' | 'hard_danger'
  badgeText: string
  message: string
  canSave: boolean
  isCritical: boolean
  isHardLimit: boolean
}

// 临床危急重症阈值 (Panic Limits) [CITED: 06-CONTEXT.md D-05]
export const PANIC_LIMITS: Record<string, { min?: number; max?: number }> = {
  systolicBP: { min: 70, max: 180 },
  diastolicBP: { min: 40, max: 110 },
  bloodGlucose: { min: 2.8, max: 16.7 },
  fastingGlucose: { min: 2.8, max: 16.7 },
  postprandialGlucose: { min: 2.8, max: 16.7 },
  heartRate: { min: 45, max: 140 },
  bodyTemperature: { min: 35.0, max: 39.5 },
  bloodOxygen: { min: 90 } // 血氧 <= 90%
}

/**
 * 评估健康数值生理梯度
 */
export function evaluateMetricValue(
  value: number | undefined | null,
  def?: MetricDefinitionVO
): MetricEvaluation | null {
  if (value === undefined || value === null || isNaN(value)) {
    return null
  }

  if (!def) {
    return {
      tier: 'NORMAL',
      status: 'success',
      badgeText: '已录入',
      message: '指标正常录入',
      canSave: true,
      isCritical: false,
      isHardLimit: false
    }
  }

  const { hardMin, hardMax, normalMin, normalMax, unit, displayName } = def

  // Tier 4: 生理不可能极限 (拦截保存，但不抹除原值)
  if (value < hardMin || value > hardMax) {
    return {
      tier: 'HARD_BOUND',
      status: 'hard_danger',
      badgeText: '超出存活极限',
      message: `${displayName}有效生理区间为 ${hardMin} ~ ${hardMax} ${unit}，请核对是否多输了数字`,
      canSave: false,
      isCritical: false,
      isHardLimit: true
    }
  }

  // Tier 3: 临床急危重症 (Panic Limits)
  const panic = PANIC_LIMITS[def.canonicalKey]
  const isPanic = panic && ((panic.min !== undefined && value <= panic.min) || (panic.max !== undefined && value >= panic.max))
  if (isPanic) {
    return {
      tier: 'CRITICAL',
      status: 'danger',
      badgeText: '严重危急值',
      message: `达到临床危急重症预警线 (${value} ${unit})，保存前请二次确认`,
      canSave: true,
      isCritical: true,
      isHardLimit: false
    }
  }

  // Tier 2: 预警偏差 (非阻塞，允许保存)
  if (value < normalMin || value > normalMax) {
    const isHigh = value > normalMax
    return {
      tier: 'WARNING',
      status: 'warning',
      badgeText: isHigh ? '指标偏高' : '指标偏低',
      message: `指标偏${isHigh ? '高' : '低'}（正常参考为 ${normalMin} ~ ${normalMax} ${unit}），可正常保存`,
      canSave: true,
      isCritical: false,
      isHardLimit: false
    }
  }

  // Tier 1: 正常参考区间
  return {
    tier: 'NORMAL',
    status: 'success',
    badgeText: '正常参考区间',
    message: `处于正常参考区间 (${normalMin} ~ ${normalMax} ${unit})`,
    canSave: true,
    isCritical: false,
    isHardLimit: false
  }
}

/**
 * 检测血压是否存在收缩压 <= 舒张压生理倒置
 */
export function checkBloodPressureInversion(
  systolic: number | undefined | null,
  diastolic: number | undefined | null
): boolean {
  if (systolic === undefined || systolic === null || isNaN(systolic)) return false
  if (diastolic === undefined || diastolic === null || isNaN(diastolic)) return false
  return systolic <= diastolic
}
```

---

### 3. 组合式封装 (`src/composables/useMetricValidation.ts`)

```typescript
import { computed, type Ref } from 'vue'
import { useMetricMetaStore } from '@/stores/metricMeta'
import { toCanonicalKey } from '@/utils/metricNormalizer'
import {
  evaluateMetricValue,
  checkBloodPressureInversion,
  PANIC_LIMITS,
  type MetricEvaluation
} from '@/utils/metricValidation'

export function useMetricValidation(
  metricKeyRef: Ref<string | undefined>,
  valueRef: Ref<number | undefined>,
  diastolicRef?: Ref<number | undefined>
) {
  const metaStore = useMetricMetaStore()

  const canonicalKey = computed(() => {
    return metricKeyRef.value ? toCanonicalKey(metricKeyRef.value) : ''
  })

  const isBloodPressure = computed(() => {
    return canonicalKey.value === 'systolicBP' || metricKeyRef.value === 'bloodPressure'
  })

  const definition = computed(() => {
    return metricKeyRef.value ? metaStore.getDefinition(metricKeyRef.value) : undefined
  })

  const diastolicDef = computed(() => {
    return isBloodPressure.value ? metaStore.getDefinition('diastolicBP') : undefined
  })

  // 主字段评估
  const primaryEval = computed<MetricEvaluation | null>(() => {
    return evaluateMetricValue(valueRef.value, definition.value)
  })

  // 舒张压字段评估
  const diastolicEval = computed<MetricEvaluation | null>(() => {
    if (!isBloodPressure.value || !diastolicRef) return null
    return evaluateMetricValue(diastolicRef.value, diastolicDef.value)
  })

  // 倒置检测
  const isInverted = computed(() => {
    if (!isBloodPressure.value || !diastolicRef) return false
    return checkBloodPressureInversion(valueRef.value, diastolicRef.value)
  })

  // 是否存在危急值需要二次确认
  const hasCritical = computed(() => {
    return Boolean(primaryEval.value?.isCritical || diastolicEval.value?.isCritical)
  })

  // 是否被物理极限拦截或倒置拦截
  const isSaveBlocked = computed(() => {
    if (primaryEval.value?.isHardLimit || diastolicEval.value?.isHardLimit) return true
    if (isInverted.value) return true
    return false
  })

  // 血压数值一键对调
  const swapBloodPressure = () => {
    if (!isBloodPressure.value || !diastolicRef) return
    const temp = valueRef.value
    valueRef.value = diastolicRef.value
    diastolicRef.value = temp
  }

  return {
    canonicalKey,
    isBloodPressure,
    definition,
    diastolicDef,
    primaryEval,
    diastolicEval,
    isInverted,
    hasCritical,
    isSaveBlocked,
    swapBloodPressure
  }
}
```

---

## State of the Art

| Old Approach | Current Approach (Phase 06) | When Changed | Impact |
|--------------|-----------------------------|--------------|--------|
| **失焦强制清空** (`form.value.value = undefined`) | **非破坏性输入保留**，异常仅通过状态胶囊与提示高亮展示 | Phase 06 (VAL-01) | 用户输入原值始终保留，杜绝误触抹除与重复录入劳动 |
| **私有硬编码范围** (`METRIC_RANGES` 对象) | **Pinia Store 统一元数据驱动** (`useMetricMetaStore`) | Phase 06 (GUIDE-01) | 精度、步长、单位、正常区间与生理极限 100% 同步后端 SSOT |
| **生硬单极报错拦截**（偏高数据直接禁止提交） | **生理四梯度宽容模型**（Tier 1~4 分级分流） | Phase 06 (VAL-02, VAL-03) | 真实病理数据（高血压、高血糖）顺畅入库，彻底消灭医疗监控盲区 |
| **血压单字段/无关联校验** | **收缩压/舒张压同屏协同** + 倒置友好警示 + **一键对调** | Phase 06 (GUIDE-03, VAL-04) | 杜绝高低压误填倒置入库，提供极简自纠交互 |
| **危急重症直接保存或一票否决** | **智能拦截二次确认** + 现场急救就医建议卡 | Phase 06 (VAL-05) | 防范致命性手误，同时在生命健康危急关头提供第一现场急救指南 |

**Deprecated/outdated:**
- `METRIC_RANGES`: 废除，由 `useMetricMetaStore().getDefinition()` 替代。
- `handleValueBlur` / `handleDiastolicBlur`: 废除抹除逻辑。

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 血压同屏录入时，若用户已输入收缩压但未输入舒张压，系统允许其继续输入或提供友好辅助提示，避免强制阻塞单一收缩压的特殊补录场景 | Architecture Patterns | 低。若临床严格要求必须双字段成对录入，可通过表单 rules 将舒张压设为必填。已在设计中保持灵活校验。 |

---

## Open Questions (RESOLVED)

1. **生活方式保健指标（如 steps, sleepDuration）是否需要 Tier 3 危急值？**
   - What we know: D-05 明确列出的危急值指标全为生理体征指标（血压、血糖、心率、体温、血氧）。
   - What's unclear: 运动步数 0 步或睡眠 2 小时是否需要模态确认？
   - RESOLVED: 保健指标（WELLNESS）不存在突发生命危险，不设 Tier 3 危急模态确认，仅保留 Tier 1 正常与 Tier 2 预警（如睡眠不足），避免过度打扰用户。所有 Panic Limits 严格收敛于 5 大生命健康指标。

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| **Node.js** | 前端构建与测试 | ✓ | v24.15.0 [VERIFIED: node --version] | — |
| **npm** | 包管理 | ✓ | 11.12.1 [VERIFIED: npm --version] | — |
| **Vitest** | 前端单元测试 | ✓ | v4.1.7 [VERIFIED: vitest run] | — |
| **Element Plus** | UI 组件库 | ✓ | ^2.5.6 [VERIFIED: package.json:23] | — |
| **Pinia** | 状态管理 | ✓ | ^2.1.7 [VERIFIED: package.json:26] | — |

**Missing dependencies with no fallback:** None  
**Missing dependencies with fallback:** None  

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 4.1.7 + @vue/test-utils 2.4.6 |
| Config file | `hhs-frontend-v2/vitest.config.ts` [VERIFIED: vitest.config.ts:1-23] |
| Quick run command | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/utils/metricValidation.spec.ts` |
| Full suite command | `cd hhs-frontend-v2 && npm run test:run` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| **VAL-01** | 失焦不抹除原值：输入异常值后触发 blur，数值忠实保留在 `form.value` 中 | component unit | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/QuickInputDialog.spec.ts` | ❌ Wave 0 Gap |
| **VAL-02** | 四梯度生理模型：覆盖正常(T1)、预警(T2)、危急(T3)、极限(T4)的分流计算 | unit | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/utils/metricValidation.spec.ts` | ❌ Wave 0 Gap |
| **VAL-03** | 预警偏差展示非阻塞状态胶囊，`canSave` 为 true，允许表单验证通过并入库 | unit | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/utils/metricValidation.spec.ts` | ❌ Wave 0 Gap |
| **VAL-04** | 血压倒置检测与一键对调：检测收缩压<=舒张压报错，点击对调按钮后完成数值互换 | unit & component | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/composables/useMetricValidation.spec.ts` | ❌ Wave 0 Gap |
| **VAL-05** | 危急值拦截与二次确认：达到 Panic Limit 时点击保存触发模态确认，确认后保存 | component integration | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/QuickInputDialog.spec.ts` | ❌ Wave 0 Gap |
| **GUIDE-01** | 元数据动态绑定：输入框占位符显示正常参考范围，动态设置 precision 与 step | component integration | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/QuickInputDialog.spec.ts` | ❌ Wave 0 Gap |
| **GUIDE-02** | 常态化参考解读卡片：展示标准正常参考区间与场景注意事项 | component unit | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/components/ReferenceGuideCard.spec.ts` | ❌ Wave 0 Gap |
| **GUIDE-03** | 血压双字段同屏渲染：收缩压与舒张压并列展示，独立参考引导 | component integration | `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/QuickInputDialog.spec.ts` | ❌ Wave 0 Gap |

### Sampling Rate
- **Per task commit:** `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/utils/metricValidation.spec.ts`
- **Per wave merge:** `cd hhs-frontend-v2 && npm run test:run && npm run build`
- **Phase gate:** 全量 Vitest 测试（含新增测试）100% 绿色通过，且 `npm run build` 零报错编译完成。

### Wave 0 Gaps
- [ ] `hhs-frontend-v2/src/__tests__/utils/metricValidation.spec.ts` — 覆盖 VAL-02, VAL-03, VAL-04 核心逻辑
- [ ] `hhs-frontend-v2/src/__tests__/composables/useMetricValidation.spec.ts` — 覆盖组合式联动与对调动作
- [ ] `hhs-frontend-v2/src/__tests__/views/QuickInputDialog.spec.ts` — 覆盖 VAL-01, VAL-05, GUIDE-01, GUIDE-03
- [ ] `hhs-frontend-v2/src/__tests__/components/ReferenceGuideCard.spec.ts` — 覆盖 GUIDE-02 解读卡片渲染

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| **V5 Input Validation** | Yes | 客户端四梯度模型校验生理极限（Tier 4），后端 `MetricRecordValidator` 类级别注解强校验拦截不可能极限数值，防止非法数据污染核心评分算法 [VERIFIED: MetricRecordValidator.java:21-80] |
| **V4 Access Control** | Yes | 请求通过 Axios 统一拦截器携带 JWT Bearer Token [VERIFIED: CLAUDE.md:130] |
| **V3 Session Management** | Yes | 401 自动触发登出与页面重定向，不在录入界面缓存敏感凭据 |
| **V2 Authentication** | Yes | 继承既有 JWT 认证体系 |
| **V6 Cryptography** | No | 录入界面不涉及前端加解密算法，传输使用标准 HTTPS/WSS |

### Known Threat Patterns for Frontend Input Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| **恶性极大/极小数值注入 (DoS on Scoring Engine)** | Tampering | 前后端双重硬边界校验 (`hardMin` / `hardMax`)，防止极大数字导致下游评分算法浮点数溢出或数据库崩溃 |
| **XSS 跨站脚本攻击** | Tampering | 动态提示文案与单位渲染均通过 Vue 模板插值语法（`{{ text }}`）自动 HTML 转义，严禁对用户或动态元数据使用 `v-html` |
| **表单重复提交与重放 (Double Submit Race)** | Tampering | 提交按钮绑定 `:loading="loading"` 状态，并在异步处理期间禁用点击，保存完成后立即置灰 |

---

## Sources

### Primary (HIGH confidence)
- `hhs-frontend-v2/src/types/api.ts:424-438` — `MetricDefinitionVO` 接口契约
- `hhs-frontend-v2/src/constants/metricMeta.ts:1-372` — 权威 23 项指标元数据静态兜底字典与字段定义
- `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue:1-258` — 现有录入组件逻辑与待废除的失焦清空实现
- `hhs-backend/src/main/java/com/hhs/validation/MetricRecordValidator.java:1-92` — 后端 Jakarta Validation 生理极限拦截校验器与错误提示模板
- `hhs-backend/src/main/java/com/hhs/service/domain/HealthMetricMetaRegistry.java:1-150` — 后端指标注册中心标准 Key 与极限区间定义
- `.planning/phases/06-input/06-CONTEXT.md:1-129` — 用户关于 D-01 ~ D-05 的锁定决策与验收标准

### Secondary (MEDIUM confidence)
- Element Plus Official Documentation — `el-input-number`, `el-dialog`, `el-tag`, `el-alert` 组件规范与属性
- Vue 3 Official Documentation — Composition API、`computed` 响应式依赖追踪

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — 全量依赖已存在且通过 `package.json` 与测试验证
- Architecture: HIGH — 遵循纯函数 + Composable + 表现层组件的解耦模式，对齐已完成的 Phase 04/05
- Pitfalls: HIGH — 精确识别了 `el-input-number` 静默截断、失焦抹除与 falsy 0 等真实开发陷阱

**Research date:** 2026-11-04  
**Valid until:** 2026-12-04 (30 days)  
