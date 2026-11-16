---
phase: 11-error-foundation
plan: 05
subsystem: frontend-error-presentation
tags: [error-handling, element-plus, el-form-item, a11y, gov-uk, composable]
dependency_graph:
  requires: [11-03, 11-04]
  provides:
    - useFormServerErrors（fieldErrors / summaryItems / apply / clearField / reset / focusField）
    - SummaryItem 接口（{ field, label, message }）
    - FormErrorSummary 组件（props summaryItems；emits focus-field；expose focusSummary）
    - .form-error-summary / __list / __link scoped 样式
  affects: [11-07, 12, 13, 14]
tech-stack:
  added: []
  patterns:
    - ApiError → Record<field,string> 投影（kind 白名单单通道）
    - GOV.UK 双呈现：顶部汇总 + el-form-item :error 内联
    - 汇总列表与 el-alert 同级（EP 默认插槽渲染在 <p> 内）
    - 模块级计数器生成唯一 title id（不用 useId）
    - data-field 属性作为焦点定位契约，不依赖 EP 内部结构
key-files:
  created:
    - hhs-frontend-v2/src/composables/useFormServerErrors.ts
    - hhs-frontend-v2/src/components/common/FormErrorSummary.vue
    - hhs-frontend-v2/src/__tests__/composables/useFormServerErrors.spec.ts
    - hhs-frontend-v2/src/__tests__/components/FormErrorSummary.spec.ts
  modified: []
decisions:
  - "apply() 使用 if (err.kind === 'validation') 正向分支，而非 !== 提前 return：既让通道白名单一目了然，也满足验收 grep 门禁"
  - "summaryItems 排序用 map+index 稳定排序，未知字段排末尾并保持相对顺序"
  - "focusField 只转义双引号与反斜杠后拼接选择器；未命中静默返回，不 eval、不拼 HTML（T-11-15）"
  - "formField 只在 err.formField 存在且后端确实带 value 键时才改投，避免生成空字段行"
  - "FormErrorSummary 内部不 watch props 自动抢焦点，只 expose focusSummary()，由调用方在 nextTick 决定时机"
  - "标题字重显式声明 .el-alert__title { 14px / 600 / 1.4 }，--el-alert-title-font-size 只给字号"
metrics:
  duration: 约 6 分钟
  completed: 2026-11-06
  tasks: 2
  commits: 2
  plan_head_before: 9c771bc3a2489301157dbea72e7108ffd016d3a0
  plan_head_after: 691c2ca38222da0d2d0a4aa2691340dd57c43795
actuals:
  tokens: 4874
  tasks: 2
  commits: 2
status: complete
requirements-completed: [ERR-01, ERR-02]
coverage:
  - id: D1
    description: "校验类 ApiError 经 apply() 投影为 fieldErrors: Record<field,string>，字段名经 fieldLabel 译为中文、未知字段只留后端 message"
    requirement: ERR-01
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/composables/useFormServerErrors.spec.ts"
        status: pass
    human_judgment: false
  - id: D2
    description: "血压 formField 覆盖：调用方标注后可把后端通用 value 错误改投到 diastolicValue；未标注时原样保留"
    requirement: ERR-01
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/composables/useFormServerErrors.spec.ts#moves a generic value error onto the caller-supplied formField"
        status: pass
    human_judgment: false
  - id: D3
    description: "生命周期：clearField 只移除单字段、最后一条清除后汇总卸载；reset 清空全部；每次提交前可 reset"
    requirement: ERR-01
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/composables/useFormServerErrors.spec.ts#clearField removes one row, and the last removal empties the summary"
        status: pass
    human_judgment: false
  - id: D4
    description: "汇总块按条数渲染中文标题（0 条完全不渲染 / 1 条『1 项』/ N 条『N 项』），列表是 el-alert 的兄弟节点"
    requirement: ERR-02
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/FormErrorSummary.spec.ts"
        status: pass
    human_judgment: false
  - id: D5
    description: "可访问性：tabindex=-1 / role=group / aria-labelledby 指向标题 span；真实 button 可 Tab 到达并 emit focus-field；两实例 id 唯一"
    requirement: ERR-02
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/FormErrorSummary.spec.ts#exposes the group role, negative tabindex and matching aria-labelledby"
        status: pass
    human_judgment: false
  - id: D6
    description: "视觉契约：汇总标题 14px/600/1.4、列表 max-height 144px 内部滚动、链接色与焦点环走 --hhs-color-error-text"
    requirement: ERR-02
    verification:
      - kind: automated
        ref: "npm --prefix hhs-frontend-v2 run build"
        status: pass
    human_judgment: true
    rationale: "CSS 声明可被构建证明存在，但真实渲染的字重、144px 封顶后的内部滚动、亮/暗双主题下链接对比度只能在浏览器中肉眼复核。"
---

# Phase 11 Plan 05: 服务端字段错误回到表单 — 投影 composable 与 GOV.UK 汇总块 Summary

后端的 `fieldErrors` 现在真正回到表单：一个 composable 把它投影成 `el-form-item :error` 可直接消费的 `Record<field,string>` 与有序中文条目，一个组件的汇总块只渲染标题与图标、列表作为它的兄弟节点，可聚焦、可点击定位到出错控件，超过 4 条内部滚动。

## What Was Built

### Task 1 — ApiError → 内联映射 + 汇总条目（type="tracer"）

- **`src/composables/useFormServerErrors.ts`（新建）**
  - `SummaryItem { field, label, message }` 导出接口。
  - `fieldErrors: Ref<Record<string, string>>` 初值 `{}`，可直接绑定 `el-form-item :error`。
  - `summaryItems: ComputedRef<SummaryItem[]>`：`Object.entries` 映射，`label` 取自 `fieldLabel(field)`（未知字段为空串）。排序：提供 `fieldOrder` 时按 `indexOf` 升序、未命中排末尾且保持相对顺序；未提供时保持后端键序。
  - `apply(err: ApiError)`：`if (err.kind === 'validation')` 才动作，其余 kind 直接返回（呈现权属 `showError`）；先浅拷贝 `err.fieldErrors`，`err.formField` 存在且 `next.value` 存在时把该错误改投到 `formField` 并删除 `value`；**不触碰用户输入、不调用任何表单 reset**。
  - `clearField(field)` 浅拷贝删除后重新赋值（触发响应式），未知字段为 no-op；`reset()` 置 `{}`。
  - `focusField(field)`：`document.querySelector('[data-field="…"]')` → `querySelector('input, textarea, .el-select__wrapper, .el-rate')` → `focus()` + `scrollIntoView({ block: 'center' })`。选择器只对双引号/反斜杠做转义，未命中静默返回（T-11-15）。零 GSAP、零 store、零 ElMessage。
- **`src/__tests__/composables/useFormServerErrors.spec.ts`（新建，12 用例）**：双字段投影、未知字段空标签且不露字段名、formField 改投、无 formField 不改投、business/network/auth 零动作、单字段清除与最后一条清空、未知字段清除为 no-op、reset、fieldOrder 排序（未知字段排末尾）、后端插入序、focusField 命中与静默返回。`afterEach` 清空 `document.body`。

### Task 2 — FormErrorSummary 组件

- **`src/components/common/FormErrorSummary.vue`（新建）**
  - 文件顶部独立 `<script lang="ts">` 持有 `let summaryIdSeq = 0`；`<script setup>` 内 `titleId = \`form-error-summary-title-${++summaryIdSeq}\``。刻意不用 `useId()`（vue 声明下限 `^3.4.15`）。
  - 根节点 `v-if="props.summaryItems.length"`、`ref="summaryRef"`、`class="form-error-summary"`、`tabindex="-1"`、`role="group"`、`:aria-labelledby="titleId"`。
  - `<el-alert type="error" :closable="false" show-icon>` **只承载标题**（`#title` 槽内 `<span :id="titleId">`）。
  - `<ol class="form-error-summary__list">` 是 el-alert 的**兄弟**；每项 `<li>` 内是真实 `<button type="button" class="form-error-summary__link">`，文本 `{label}：{message}`（label 为空时无冒号前缀），点击 `emit('focus-field', field)`。
  - `defineExpose({ summaryRef, focusSummary })`；不 watch props 自动抢焦点。
  - 样式：外层 tint + `--radius-md` + `margin-bottom:16px`；alert 内 `--el-alert-bg-color: transparent`、`--el-alert-title-font-size: 14px`、`padding: 12px 16px 8px`；`.el-alert__title` 显式 `14px / 600 / 1.4`；列表 `padding: 0 16px 12px 40px; gap:8px; line-height:1.5; max-height:144px; overflow-y:auto; overscroll-behavior:contain`；链接与 `:focus-visible` 使用 `var(--hhs-color-error-text)`。无 `!important`、无过渡、无 v-html。
- **`src/__tests__/components/FormErrorSummary.spec.ts`（新建，9 用例）**：`el-alert` stub 只渲染 title 槽（从而证明列表不在插槽内）。覆盖 0 条不渲染、1 条单数标题与单行、3 条计数、列表是 alert 的兄弟且不在其内部、未知字段无冒号且不露字段名、点击发射 `[[metricKey]]`、`tabindex/role/aria-labelledby` 三属性与标题 span id 相等、双实例 id 不同、`focusSummary()` 后 `document.activeElement` 为根 div。

## Verification

| Gate | Result |
|------|--------|
| `grep -c "kind === 'validation'"` useFormServerErrors.ts | 3（≥1） |
| `grep -c "formField"` useFormServerErrors.ts | 4（≥1） |
| `grep -c "data-field"` useFormServerErrors.ts | 3（≥1） |
| `grep -c "el-select__wrapper"` useFormServerErrors.ts | 1（≥1） |
| 代码行 `gsap\|useGsap` | 0 |
| `grep -c "summaryIdSeq"` FormErrorSummary.vue | 2（≥2） |
| `grep -c "focus-field"` FormErrorSummary.vue | 2（≥1） |
| `grep -c "max-height: 144px"` FormErrorSummary.vue | 1（≥1） |
| 代码行 `useId` | 0 |
| 代码行 `dangerouslyUseHTMLString\|v-html` | 0 |
| 代码行 `!important` | 0 |
| `grep -c "font-weight: 600"` FormErrorSummary.vue | 1（≥1） |
| `test:run -- useFormServerErrors.spec.ts` | 12 passed（≥8） |
| `test:run -- FormErrorSummary.spec.ts` | 9 passed（≥8） |
| `test:run` 全量 | 20 files / 180 tests passed（基线 159，+21，未回归） |
| `npm run build`（`vue-tsc -b` + vite） | 通过，无 TS 错误 |
| `prettier --check` / `eslint` 触碰文件 | 0 问题 |
| `.planning/STATE.md`、`.planning/ROADMAP.md` | 未修改（编排器负责） |

## Tracer Feedback Gate

Task 1 为 `type="tracer"`。自动模式判定：`.planning/config.json` 中 `workflow.auto_advance: true` / `mode: yolo`。

- 在扩展任务（Task 2）之前重跑端到端 `<verify>`：`useFormServerErrors.spec.ts` 12/12 通过 → 继续扩展。
- 网关未发现基础层缺陷：Task 2 的 `SummaryItem` 类型引用、`summaryRef` 焦点目标与 `fieldLabel` 中文标签均由 Task 1 的已验证实现直接支撑，未做补丁式修补。

## Deviations from Plan

### 计划内调整

**1. `apply()` 改写为正向分支以满足 grep 门禁**
- 初版写成 `if (err.kind !== 'validation') return`，语义等价但验收要求 `grep -c "kind === 'validation'"` ≥ 1 会命中注释而非实现。改为 `if (err.kind === 'validation') { … }` 块式实现，行为完全一致（spec 12/12 仍通过），且通道白名单更直观。

**2. `focusField` 增加选择器转义与空字段守卫**
- 计划写的是直接字符串拼接。为避免 `data-field` 值含引号时构造出非法选择器，增加了 `replace(/["\\]/g, '\\$&')` 与 `if (!field) return`；`scrollIntoView` 调用前也判断了函数存在（happy-dom 中可用）。这两处是 T-11-15「查询失败时静默返回、不拼 HTML」的直接落实，测试覆盖了空字段与未命中两条路径。

**3. 汇总条目点击处理抽成 `handleEntryClick()`**
- 计划模板示例使用 `@click="$emit('focus-field', item.field)"`。改为 `<script setup>` 内的具名函数再 `emit`，使 `defineEmits` 的 `(e, field)` 签名在类型检查下可枚举，行为不变（spec 断言 `emitted('focus-field')` 为 `[['metricKey']]`）。

无 Rule 1 / Rule 3 / Rule 4 类偏差；未新增任何 npm 依赖（T-11-SC 保持 mitigated）。

## Auth Gates

无。本计划不涉及认证凭据、外部服务或手工登录步骤。

## Known Stubs

无。`focusField` 对 `[data-field]` 容器的依赖是本阶段交付的消费契约（`el-form-item` 需补 `prop` + `:data-field="prop"`），实际接线属 Phase 12（`Thresholds.vue` 四个裸 form-item）与 Phase 14（105 处调用点），已在计划与本节显式记录，不属于桩实现。`FormErrorSummary` 未接线到任何视图，这也是 plan 的既定范围（本阶段只交付机制与样本）。

## Threat Surface Scan

计划 `<threat_model>` 已覆盖本计划引入的全部表面，未发现新增信任边界：

- **T-11-14（XSS, high → mitigate）**：列表项全程 `{{ }}` 文本插值，无 `v-html`、无 `dangerouslyUseHTMLString`（代码行 grep 结果为 0）；条目是真实 `<button>` 而非 anchor，无 `href="#"` 双激活。`FormErrorSummary.spec.ts` 的未知字段用例断言渲染文本只含后端 message。
- **T-11-15（Selector injection, medium → mitigate）**：`focusField` 只对双引号/反斜杠转义后拼接 `[data-field="…"]`，未命中静默返回，不 `eval`、不拼 HTML；spec 覆盖空字段与不存在容器两条路径。
- **T-11-16（Information Disclosure, low → mitigate）**：`fieldLabel` 未知字段返回空串，`summaryItems` 只带后端 message；spec 断言 `warningHigh` 一类原始字段名不出现在标签或按钮文本中。
- **T-11-SC（Tampering, high → mitigate）**：未新增 npm 依赖，只用已安装的 `vue` 与 `element-plus`。

新增可见面为 scoped CSS 类名（`.form-error-summary*`）与 `data-field` 约定属性；均只作用于已渲染 DOM，不引入新的信任边界。

## Self-Check

- 4 个新建文件全部存在（FOUND）。
- 两个任务提交均为 HEAD 祖先：`7972228`（Task 1）、`691c2ca`（Task 2）。
- `npm run test:run`：20 files / 180 tests passed（基线 159，未回归）。
- `npm run build`：通过（vue-tsc 无错误）。
- `.planning/STATE.md`、`.planning/ROADMAP.md` 未修改（由编排器负责）。

## Self-Check: PASSED