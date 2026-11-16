# Phase 6: 前端非破坏性录入引导、四梯度宽容校验与友好反馈 - Context

**Gathered:** 2026-11-04  
**Phase:** 6 — 前端非破坏性录入引导、四梯度宽容校验与友好反馈  
**Status:** Ready for planning  

<domain>
## Phase Boundary

本阶段重塑健康指标数据录入交互体验。核心交付物包括：
1. **彻底废除失焦清空输入框机制**（移除 `form.value.value = undefined`），输入异常时保留用户键入原值，绝不强制抹除用户输入（VAL-01）；
2. **构建前端生理四梯度模型**：正常、预警偏差、急危重症、生理不可能极限（VAL-02）；
3. **元数据驱动输入控件**：`QuickInputDialog.vue` 接入 `useMetricMetaStore`，动态绑定标准单位、推荐步长（step）与小数精度（precision），输入框占位符显式展示生理参考范围（如“参考范围：3.9 ~ 6.1 mmol/L”）（GUIDE-01）；
4. **常态化展示健康参考标准解读卡片**：在输入框下方展示指标正常参考值区间与临床测量场景注意事项（GUIDE-02）；
5. **宽容状态胶囊与解释性文案**：输入超出正常参考范围时，以非阻塞的友好黄色/橙色状态胶囊及解释性文案替代生硬的红色报错，允许用户正常保存入库（VAL-03）；
6. **血压同屏协同录入与一键对调**：血压录入支持收缩压（高压）与舒张压（低压）同屏联动展示与双向关联校验（GUIDE-03）；检测到高压低于低压倒置时，给出原理解释并提供“一键对调高低压数值”的快捷自纠按钮（VAL-04）；
7. **急危重症二次确认模态窗**：录入达到严重危急值区间时，弹出友好的二次确认模态窗（提示防误触，同时提供急救与就医建议），用户点击“确认录入”后仍允许正常保存入库（VAL-05）。

</domain>

<decisions>
## Implementation Decisions

### 1. 非破坏性输入保留原则 (VAL-01)
- **D-01 (彻底移除失焦抹除):** 彻底删除 `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue` 中的失焦清空逻辑（移除 `handleValueBlur` 与 `handleDiastolicBlur` 中的 `form.value.value = undefined` 与 `form.value.diastolicValue = undefined`）。用户键入的任何数值均被忠实保留，绝不在用户失焦或切换输入框时清空输入。

### 2. 四梯度宽容校验模型与状态胶囊 (VAL-02, VAL-03)
- **D-02 (四梯度实时评估机制):** 创建可复用的输入评估逻辑（或 Composable / 工具函数），根据当前指标的 `MetricDefinitionVO` 评估输入值：
  - **Tier 1 正常 (Normal):** `normalMin <= val <= normalMax`。展示绿色状态徽章「正常参考区间」，对勾图标。
  - **Tier 2 预警偏差 (Warning):** `hardMin <= val < normalMin` 或 `normalMax < val <= hardMax`（未达危急线）。展示橙黄色状态胶囊「指标偏高/偏低」，附带临床测量状态说明（如空腹/餐后差异），**完全不阻断用户保存入库**。
  - **Tier 3 急危重症 (Critical):** 达到临床危急重症预警线。展示橙红色危急胶囊「严重生理异常」，提示核对测量，点击保存时触发二次确认模态弹窗（D-05）。
  - **Tier 4 生理不可能极限 (Hard Bound):** `val < hardMin` 或 `val > hardMax`。展示红色警示胶囊「超出人体存活极限」，禁用保存按钮，给出结构化提示（例如“心率有效生理区间为 20 ~ 300 次/分，请核对是否多输了数字”），但不清空字段。

### 3. 元数据驱动与参考引导体系 (GUIDE-01, GUIDE-02)
- **D-03 (元数据动态绑定与参考卡片):** 废除 `QuickInputDialog.vue` 内部硬编码的 `METRIC_RANGES` 常量对象，全面替换为 `useMetricMetaStore().getDefinition(metricKey)`：
  - 输入框 `placeholder` 动态渲染为 `参考范围: ${def.normalMin} ~ ${def.normalMax} ${def.unit}`；
  - `el-input-number` 动态绑定 `:precision="def.precision"` 与 `:step="def.step"`；
  - 在表单下方集成常态化参考解读卡片（`ReferenceGuideCard`），清晰展示当前指标的正常参考区间、测量注意事项及 `guideHint` 临床指导文案。

### 4. 血压双字段同屏联动与倒置一键对调 (GUIDE-03, VAL-04)
- **D-04 (血压协同录入与一键对调):** 
  - 录入血压时，表单采用网格（Grid/Row）同屏并排展示收缩压与舒张压两个输入框，各自具备独立的参考引导；
  - 当同时录入高压和低压，且出现 `收缩压 <= 舒张压` 的生理倒置时（如高压误填 80，低压误填 120），表单下方立即展示内联友好警示条：“检测到收缩压（高压）低于舒张压（低压），疑似输入颠倒”；
  - 警示条右侧内嵌「一键对调数值」操作按钮，用户点击后瞬间交换高压与低压数值，并通过轻提示通知用户，消除倒置报错。

### 5. 急危重症二次确认模态窗与急救指引 (VAL-05)
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 上游设计与阶段成果
- `.planning/phases/04-key/04-CONTEXT.md` — Phase 4 权威 23 项指标定义与生理极限区间
- `.planning/phases/05-store/05-CONTEXT.md` — Phase 5 前端 Pinia Store 与本地静态离线兜底体系
- `.planning/phases/05-store/05-01-SUMMARY.md` — `useMetricMetaStore` 与 `metricNormalizer` 接口细节
- `.planning/phases/05-store/05-02-SUMMARY.md` — 全站格式化与防英文外露交付成果
- `.planning/REQUIREMENTS.md` — GUIDE-01 ~ GUIDE-03, VAL-01 ~ VAL-05 需求验收准则

### 待改造与相关前端组件
- `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue` — 核心改造目标组件
- `hhs-frontend-v2/src/views/data-input/components/QuickInput.vue` — 快捷录入入口与卡片
- `hhs-frontend-v2/src/stores/metricMeta.ts` — 元数据响应式状态库
- `hhs-frontend-v2/src/constants/metricMeta.ts` — 静态离线指标字典
- `hhs-frontend-v2/src/utils/metricNormalizer.ts` — 指标 Key 规范化工具

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `useMetricMetaStore`: 提供 `getDefinition(key)`、`healthMetrics`、`wellnessMetrics` 与 `allDefinitions`；
- `toCanonicalKey`: 兼容任意大小写与医学缩写；
- Element Plus 组件库: `el-dialog`, `el-alert`, `el-tag`, `el-input-number`, `el-message-box`, `el-icon`。

### Established Patterns
- Pinia Store 依赖注入；
- Composition API + `<script setup lang="ts">`；
- 表单验证基于 Element Plus `FormRules` 与自定义 validator 函数。

### Integration Points
- `QuickInputDialog.vue` 移除失焦抹除代码，引入 `useMetricMetaStore`；
- 表单验证器改造为四梯度非阻塞分流；
- 保存前危急值二次确认弹窗接入。

</code_context>

<specifics>
## Specific Ideas

- **状态胶囊色彩体系：**
  - Tier 1 (正常): `success` 浅绿背景 + 深绿文字 + `Check` 图标；
  - Tier 2 (预警): `warning` 浅黄背景 + 橙色文字 + `Warning` 图标；
  - Tier 3 (危急): `danger` 浅红/深橙背景 + 红色文字 + `Bell` 图标；
  - Tier 4 (超限): 纯深红描边 + 警示文字 + `CircleClose` 图标。
- **一键对调按钮：**
  - 当高压 < 低压时，在两个输入框右侧或下方居中出现对调按钮，点击带平滑切换效果。

</specifics>

<deferred>
## Deferred Ideas

- **Phase 7 承接：** AI 智能对话文本提取 (`AiParseServiceImpl.java`) 与 OCR 体检报告识别 (`OcrServiceImpl.java`) 接入统一元数据与四梯度校验。
- **v1.2 规划：** 个性化基线定制（结合病史针对特定患者放宽或收紧正常区间，如老年糖尿病患者）。

</deferred>

---

*Phase: 06-前端非破坏性录入引导、四梯度宽容校验与友好反馈*  
*Context gathered: 2026-11-04*  
