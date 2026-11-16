---
phase: 12-threshold-wizard
plan: 06
subsystem: ui
tags: [vue3, vitest, css-tokens, accessibility, threshold-wizard, thr-04]

# Dependency graph
requires:
  - phase: 12-05
    provides: "ThresholdDraft shape + thresholdValidation.ts pure functions the zone bar consumes; vitest component-spec conventions"
  - phase: 11-error-foundation
    provides: "--hhs-color-error-tint token family the zone bar's critical bands reuse"
provides:
  - "ThresholdZoneBar.vue — five-band live preview (严重低/警告低/正常/警告高/严重高) with props draft: ThresholdDraft + unit?: string"
  - "Theme tokens --hhs-color-warning-tint / --hhs-color-normal-tint declared in both the dark :root and [data-theme='light'] blocks"
  - "Component spec src/__tests__/components/ThresholdZoneBar.spec.ts pinning band count, order, null rendering, aria-label and reactivity (9 tests)"
affects: [12-07, 12-08, verify-work]

# Actuals (#2632) — pairs with the plan's `estimate` to calibrate future estimates.
actuals:
  tokens: 3178          # chars/4 over the realized diff (12710 bytes, 294 insertions / 0 deletions)
  tasks: 3
  commits: 3            # MEASURED: git rev-list --count fcb2da2..HEAD
  plan_head_before: fcb2da2de04ddcb7c88e3ce8f31e2484ad195f5d

# Tech tracking
tech-stack:
  added: []             # 零新增依赖（T-12-SC：无 npm 安装，未 import @vueuse/core）
  patterns: ["BEM 命名的纯展示组件（zone-bar__band--critical/warning/normal）", "三档底纹 token 家族（error/warning/normal tint）相邻声明"]

key-files:
  created:
    - hhs-frontend-v2/src/views/health/components/ThresholdZoneBar.vue
    - hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts
  modified:
    - hhs-frontend-v2/src/assets/styles/theme.css

key-decisions:
  - "显式 import { computed } from 'vue' 而非依赖 auto-import —— vitest.config.ts 只挂 vue() 插件、无 unplugin-auto-import，且仓内被测组件（ReferenceGuideCard/FormErrorSummary）均显式导入；仅禁 @/stores、@vueuse、element-plus 三类导入的计划约束不受影响"
  - "正常带的数值显示相邻两侧界构成的区间（X – Y）；两侧均未设置时显示 —，与边界带空态语义一致"
  - "aria-label 未设置替换规则按「段」而非按「边界逐一替换」：某段的边界未设置时该段恰好读一次 未设置（仅一侧设置时保留另一侧数值，如 正常：未设置 到 100），保证全 null 时五段恰好各出现一次 未设置（计划 Task 3 的五次断言）"
  - "完整 aria-label 格式与 UI-SPEC 示例逐字对齐：严重低：低于 40；警告低：40 到 50；正常：50 到 100；警告高：100 到 120；严重高：高于 120（mmHg），unit 缺省时省略末尾 （unit） 后缀"

patterns-established:
  - "四区预览条 BEM 类名：.zone-bar / .zone-bar__band / .zone-bar__band--{critical,warning,normal} / .zone-bar__name / .zone-bar__value"
  - "色带信息通道约定：颜色只作装饰（对比度 1.08-1.35:1），带内「名称 + 数值」（--text-1）承载信息，完整范围由根元素 aria-label 朗读（WCAG 1.4.1）"
  - "窄屏 3+2 换行范式：@media (max-width: 640px) 下 flex-wrap: wrap + flex: 1 1 30%，而不是压缩带宽"

requirements-completed: [THR-04]

# Coverage metadata (#1602) — one entry per must-have truth from 12-06-PLAN.md.
coverage:
  - id: D1
    description: "色带恰好五段，顺序固定为 严重低 / 警告低 / 正常 / 警告高 / 严重高"
    requirement: THR-04
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts#renders exactly five bands when all four boundaries are present"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts#renders the band names in the fixed order 严重低 / 警告低 / 正常 / 警告高 / 严重高"
        status: pass
    human_judgment: false
  - id: D2
    description: "每段同时显示名称与数值（信息不靠颜色单独承载）"
    requirement: THR-04
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts#renders each boundary value in its own band"
        status: pass
      - kind: other
        ref: "hhs-frontend-v2/src/views/health/components/ThresholdZoneBar.vue (五段模板各含 zone-bar__name + zone-bar__value，grep 'zone-bar__band' = 10)"
        status: pass
    human_judgment: false
  - id: D3
    description: "null 边界渲染为破折号 —，渲染文本中绝无 undefined/null 字样"
    requirement: THR-04
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts#keeps five bands with null boundaries rendered as — and never leaks undefined/null text"
        status: pass
    human_judgment: false
  - id: D4
    description: "部分或全部边界为 null 时五段全部保持可见（不隐藏、不塌缩）"
    requirement: THR-04
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts#keeps five bands with null boundaries rendered as — and never leaks undefined/null text"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts#renders five em-dash bands and reads 未设置 five times when every boundary is null"
        status: pass
    human_judgment: false
  - id: D5
    description: "根元素 role=\"img\"（恰好一处）+ aria-label 按序读出五段范围，未设置的边界读作 未设置，unit 提供时追加 （unit） 后缀"
    requirement: THR-04
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts#reads all five zone prefixes and 未设置 for the null boundaries in the aria-label"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts#includes the unit in the aria-label when supplied"
        status: pass
      - kind: other
        ref: "grep -c 'role=\"img\"' ThresholdZoneBar.vue = 1"
        status: pass
    human_judgment: false
  - id: D6
    description: "draft 变化时带内数值纯 computed 实时更新，无网络请求"
    requirement: THR-04
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts#updates the rendered value reactively via setProps without remounting"
        status: pass
      - kind: other
        ref: "组件源码无 fetch/axios/api 导入（仅 vue + @/utils/thresholdValidation 类型导入）"
        status: pass
    human_judgment: false
  - id: D7
    description: "--hhs-color-warning-tint 与 --hhs-color-normal-tint 在暗色 :root 块与 [data-theme='light'] 块各声明一次"
    requirement: THR-04
    verification:
      - kind: other
        ref: "grep -c hhs-color-warning-tint theme.css = 2（:64 暗色 / :179 亮色）；grep -c hhs-color-normal-tint theme.css = 2（:65 / :180）"
        status: pass
      - kind: other
        ref: "npm --prefix hhs-frontend-v2 run build（vue-tsc + vite，两次运行均绿）"
        status: pass
    human_judgment: false
  - id: D8
    description: "带内文字用 --text-1、底色用三档 tint token（critical→error-tint / warning→warning-tint / normal→normal-tint）"
    requirement: THR-04
    verification:
      - kind: other
        ref: "hhs-frontend-v2/src/views/health/components/ThresholdZoneBar.vue:84-100（color: var(--text-1) + 三个 modifier 映射三个 tint token，与 UI-SPEC Contract 4 CSS 逐字一致）"
        status: pass
    human_judgment: false
  - id: D9
    description: "带内名称/数值 nowrap + ellipsis（band min-width: 0），未截断完整值仍由 aria-label 承载"
    requirement: THR-04
    verification:
      - kind: other
        ref: "grep -c 'text-overflow' ThresholdZoneBar.vue = 1（:146）；grep -c 'min-width: 0' ThresholdZoneBar.vue = 1（:107）"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts#keeps five bands and the full unit in the aria-label when the unit string is very long"
        status: pass
    human_judgment: false
  - id: D10
    description: "<640px 时五段换行为两行（3 + 2）而不是压缩，每段保持可读宽度"
    requirement: THR-04
    verification:
      - kind: other
        ref: "hhs-frontend-v2/src/views/health/components/ThresholdZoneBar.vue:152-157（@media (max-width: 640px) flex-wrap: wrap + flex: 1 1 30% → 3 段 ≈90%+16px 间隙后第 4 段换行）"
        status: pass
    human_judgment: true
    rationale: "换行行数是渲染后的布局属性，happy-dom DOM 断言无法覆盖；12-VALIDATION 已把四区色带的浏览器视觉检查列为 Manual-Only（真实浏览器三步流程 + 截图比对）"
  - id: D11
    description: "色带宽度恒为容器 100%，长单位/长值不造成向导主体横向溢出"
    requirement: THR-04
    verification:
      - kind: other
        ref: "hhs-frontend-v2/src/views/health/components/ThresholdZoneBar.vue:102（width: 100%）+ :107（min-width: 0）+ :146（text-overflow: ellipsis）"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts#keeps five bands and the full unit in the aria-label when the unit string is very long"
        status: pass
    human_judgment: true
    rationale: "「不产生横向溢出」是渲染后布局结果，DOM 单测只能保证带数量与类结构；最终确认依赖 12-VALIDATION 的浏览器 Manual-Only 行"

# Metrics
duration: 11min
completed: 2026-11-08
status: complete
---

# Phase 12 Plan 06: ThresholdZoneBar 预览条 Summary

**五段式阈值分区预览条（严重低/警告低/正常/警告高/严重高）：null 边界渲染 — 并朗读 未设置，数值随 draft 纯 computed 实时更新，新增 warning/normal 两枚双主题 tint token，9 条组件测试钉死契约（THR-04）**

## Performance

- **Duration:** 11 min
- **Started:** 2026-11-08T10:15:47Z
- **Completed:** 2026-11-08T10:26:41Z
- **Tasks:** 3
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments

- `ThresholdZoneBar.vue`：五段色带预览条，每段带「名称 + 数值」双文字（对比度仅 1.08-1.35:1 的色带只作装饰，满足 WCAG 1.4.1）；根元素 `role="img"` + 完整 aria-label（格式与 UI-SPEC Contract 4 示例逐字一致）
- null 边界显示 `—`、朗读 `未设置`，全 null 时五段全部保持可见且 aria-label 恰好出现五次 `未设置`
- `theme.css`：`--hhs-color-warning-tint` / `--hhs-color-normal-tint` 与既有 `--hhs-color-error-tint` 相邻声明，暗色（rgba 底纹）与亮色（实色）两块齐全
- 溢出防护：标签/数值 nowrap + ellipsis + `min-width: 0`，长单位撑不破向导弹窗；<640px 时 3+2 换行
- 纯展示组件：零 store / composable / Element Plus 依赖，零新增 npm 包，无任何原始 HTML 注入（T-12-20/21/SC 全部 mitigate）

## Task Commits

Each task was committed atomically:

1. **Task 1: Theme tokens + the five-band component (tracer)** - `3ba58fd` (feat)
2. **Task 2: Component spec — band count, order, null rendering, reactive update** - `fdd292e` (test)
3. **Task 3: Accessibility and overflow guards recorded as assertions** - `e4de897` (feat，含 prettier 格式化修正)

**Plan metadata:** 待定（docs 提交在 SUMMARY 落盘后进行）

_Note: Task 1 为 tracer 任务——auto 模式下已完成端到端复验（`npm run build` 两次通过）后才扩展到 Task 2/3。_

## Files Created/Modified

- `hhs-frontend-v2/src/views/health/components/ThresholdZoneBar.vue`（新建）— 五段预览条组件：props `draft: ThresholdDraft` + `unit?: string`，computed aria-label 与正常带区间值，Contract 4 CSS + 溢出防护 + 窄屏换行
- `hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts`（新建）— 9 条测试：五段数量/顺序、边界值、null 的 `—`、aria-label 前缀与 `未设置`、setProps 响应性、unit 后缀、长单位、全 null
- `hhs-frontend-v2/src/assets/styles/theme.css`（修改）— 暗色 `:root` :64-65、亮色 `[data-theme='light']` :179-180 各新增两枚 tint token

## Decisions Made

- **显式导入 `computed`**：vitest.config.ts 只挂 `vue()` 插件（无 unplugin-auto-import），仓内被测组件先例（ReferenceGuideCard / FormErrorSummary）均为显式导入；计划的 acceptance 只禁 `@/stores`、`@vueuse`、`element-plus` 导入，不受影响
- **正常带数值格式**：显示相邻两界区间 `X – Y`；两侧均 null 时显示 `—`（使全 null 断言「五段值全为 —」成立）；单侧 null 时保留另一侧数值
- **aria-label 的 未设置 计数规则**：按段替换而非按边界逐一替换——某段任一边界未设置时该段恰好读一次 `未设置`（如 `正常：未设置 到 100`），从而全 null 时恰好五次；同时保留已填边界的可读性
- **`role="img"` 与 `v-html` 字样不写入注释**：避免污染 acceptance grep 计数

## Deviations from Plan

None - plan executed exactly as written.（Task 1 的 tracer 端到端复验、Task 3 的 prettier 格式化并入原任务提交，均属计划内流程；theme.css 在本计划之前就存在 prettier 不达标问题，属预存问题，未触碰。）

## Issues Encountered

- vitest.config.ts 不含 unplugin-auto-import 插件，组件若依赖 auto-import 的 `computed` 会在测试中运行时未定义——以显式导入解决（见 Decisions）
- 仓库整体 `lint:check` 有 200 条预存 warning（0 error），全部位于本计划未触碰的文件，按 scope boundary 不处理；本计划三个文件 ESLint 0 警告、prettier 全部达标

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 12-07（ThresholdWizard.vue）可直接 `import ThresholdZoneBar from '@/views/health/components/ThresholdZoneBar.vue'`，传入向导第 2 步的 draft + 指标 unit 即得实时预览；12-08（Thresholds.vue 重写）同样可复用
- 遗留人工验证（12-VALIDATION Manual-Only 行）：四区色带在真实浏览器中的渲染、浅/深主题切换对比度、<640px 的 3+2 换行——已通过 coverage D10/D11 的 human_judgment 路由到 UAT

## Self-Check: PASSED

- All 3 created/modified artifacts exist on disk (verified 2026-11-08)
- All 3 task commits found in history: `3ba58fd`, `fdd292e`, `e4de897`
- Measured commits from ledger `fcb2da2..HEAD` = 3, matching frontmatter `actuals.commits`
- Latest runs: spec 9/9 pass, full suite 244/244 pass, `lint:check` 0 errors, `npm run build` green (x2)

---
*Phase: 12-threshold-wizard*
*Completed: 2026-11-08*
