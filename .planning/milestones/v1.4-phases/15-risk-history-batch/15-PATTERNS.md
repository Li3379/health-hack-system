# Phase 15: risk-history-batch - Pattern Map

**Mapped:** 2026-11-17
**Files analyzed:** 2 (1 modified source, 1 modified test)
**Analogs found:** 2 / 2 (both partial-match; no exact analog for batch-collapse exists)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `hhs-frontend-v2/src/views/prevention/Risk.vue` | view (page component) | transform + request-response | `hhs-frontend-v2/src/views/prevention/Risk.vue` (self: `metricSources` computed, lines 361-397) | partial (in-file aggregation precedent) |
| `hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts` | test | request-response (mocked) | `hhs-frontend-v2/src/__tests__/views/DeviceSyncMigration.spec.ts` (lines 171-172) + `OcrMigration.spec.ts` (152-153) | role-match (collapse stub set) |

No backend file changes (CONTEXT §数据契约: `batchId` already exists; `listByUserId` already `orderByDesc(createTime)`).

---

## Pattern Assignments

### `hhs-frontend-v2/src/views/prevention/Risk.vue` (view, transform)

**Analog:** itself (the file is being restructured, not rewritten; preserve every existing block outside the history region).

#### 1. Collapsible grouped list — `el-collapse` usage

Best analog: `hhs-frontend-v2/src/views/data-input/components/DeviceSync.vue:200-208` — the only place in the repo that drives `el-collapse` from a `v-model` ref **and** renders a composed title via `#title` slot:

```vue
<!-- DeviceSync.vue:200-208 -->
<el-collapse v-model="activeConfigPanel" accordion>
  <el-collapse-item name="huawei">
    <template #title>
      <div class="config-item-title">
        <el-avatar :size="24" class="platform-avatar huawei">H</el-avatar>
        <span>华为运动健康</span>
        <el-tag type="success" size="small">推荐</el-tag>
      </div>
    </template>
```

Model declaration, `DeviceSync.vue:364`:
```ts
const activeConfigPanel = ref('huawei')
```

Multi-open (non-accordion) variant, `hhs-frontend-v2/src/views/data-input/components/DeviceConfigWizard.vue:119-124` + `:313`:
```vue
<el-collapse v-model="activeGuide">
  <el-collapse-item name="how-to">
    <template #title>
      <strong>如何获取 {{ selectedPlatformInfo.platformName }} 的 API 凭据？</strong>
    </template>
```
```ts
const activeGuide = ref(['how-to'])   // array form = multiple panels can be open
```

Single static panel, no model, `hhs-frontend-v2/src/views/data-input/components/OcrHistory.vue:125-129`:
```vue
<el-collapse v-if="detailData.rawText" class="detail-raw-text">
  <el-collapse-item title="原始识别文本" name="rawText">
    <pre class="raw-text-content">{{ detailData.rawText }}</pre>
  </el-collapse-item>
</el-collapse>
```

Title-row flex CSS, `DeviceSync.vue:839-843`:
```css
.config-item-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
```

**Apply to Phase 15:** use the **array** model (`ref<string[]>`) because D-01 wants only the newest batch open by default but the user must be able to open several. `accordion` would force one-at-a-time and is wrong here. There is **no** existing `:deep(.el-collapse-item__header)` override anywhere in `src/` (verified by grep) — so the batch header inherits EP defaults plus the theme.css token system; only add new classes (`.risk-batches__*`), do not fork EP internals unless a contrast/token violation appears.

#### 2. Derived grouped/aggregated data from a flat array

Best analog: the **in-file** `metricSources` computed, `hhs-frontend-v2/src/views/prevention/Risk.vue:361-397` — the repo's only `Map`-based aggregation over `assessments.value`:

```ts
// Risk.vue:361-397 (abridged; comment preserved because it states the invariant)
/**
 * 指标来源行：跨**全部**评估汇总，不能只看第一条。
 */
const metricSources = computed<DataSourceRow[]>(() => {
  const wanted: Array<{ key: string; label: string }> = [...]

  // 汇总所有评估的归因，取每个 key 第一次出现的非缺失项。
  const found = new Map<string, { value: string; state: string }>()
  for (const assessment of assessments.value) {
    for (const c of assessment.contributors ?? []) {
      if (!found.has(c.key) && c.state !== 'MISSING' && c.value) {
        found.set(c.key, { value: c.value, state: c.state })
      }
    }
  }

  return wanted.map(w => { ... })
})
```

Second precedent for `Map`-keyed lookups built once and read in a computed, `DeviceSync.vue:409-422` + `:378-383`:

```ts
const map = new Map<string, PlatformMetadata>()
res.data.forEach(meta => { map.set(meta.platform, meta) })
platformMetadata.value = map
...
const enrichedDevices = computed(() => {
  return devices.value.map(device => ({
    ...device,
    platformMetadata: platformMetadata.value.get(device.platform)
  }))
})
```

**Gap:** no file in `src/` currently renders **`Map.entries()` → array of group objects** in a template (grep for `Object.entries`/`groupBy` found only `Score.vue:349` and `PushSettings.vue:269`, both field-map loops). The `batches` computed in CONTEXT §数据契约 is therefore new shape; follow `metricSources` for the loop/`Map` idiom and rely on insertion order (JS `Map` preserves it, and input is already `createTime DESC` per `RiskAssessmentServiceImpl.java:195`, so batches come out newest-first for free — do **not** add a re-sort).

Type shape recommendation consistent with existing local interfaces (`DataSourceRow` is declared as a local interface right below its computed, `Risk.vue:413-419`):

```ts
interface DataSourceRow {
  key: string
  label: string
  value: string
  origin: string
  tone: 'ok' | 'assumed' | 'missing'
}
```

So declare `BatchGroup` / `BatchRow` interfaces in the same style, adjacent to the computed.

#### 3. "Delta vs previous" / trend-arrow rendering

Closest analogs — both render a **backend-provided** direction, neither computes a client-side delta:

`hhs-frontend-v2/src/views/wellness/Dashboard.vue:59-66`:
```vue
<div
  v-if="metric.trend !== null"
  class="metric-trend"
  :class="{ positive: metric.trend > 0, negative: metric.trend < 0 }"
>
  <el-icon><Top v-if="metric.trend > 0" /><Bottom v-else /></el-icon>
  {{ Math.abs(metric.trend).toFixed(1) }}%
</div>
```
CSS, `wellness/Dashboard.vue:587-602`:
```css
.metric-trend {
  font-size: 12px;
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.metric-trend.positive { color: var(--color-health-excellent); }
.metric-trend.negative { color: var(--color-health-poor); }
```

`hhs-frontend-v2/src/views/screening/Detail.vue:82-91` (three-state, incl. flat):
```vue
<el-table-column prop="trend" label="趋势" width="100">
  <template #default="{ row }">
    <span v-if="row.trend">
      <el-icon v-if="row.trend === 'UP'" color="#f56c6c"><Top /></el-icon>
      <el-icon v-else-if="row.trend === 'DOWN'" color="#67c23a"><Bottom /></el-icon>
      <el-icon v-else color="#909399"><Minus /></el-icon>
    </span>
    <span v-else>-</span>
  </template>
</el-table-column>
```
Import line, `screening/Detail.vue:122`:
```ts
import { Top, Bottom, Minus, Document } from '@element-plus/icons-vue'
```

Computed-returning-an-icon-component pattern, `hhs-frontend-v2/src/views/dashboard/Index.vue:282-288`:
```ts
const scoreTrendIcon = computed(() => {
  if (!scoreData.value) return Minus
  const level = scoreData.value.level
  if (level === 'EXCELLENT' || level === 'GOOD') return Top
  if (level === 'FAIR') return Minus
  return Bottom
})
```
Rendered with a dynamic component, `dashboard/Index.vue:67-70`:
```vue
<div v-if="card.trend" class="stat-trend" :class="card.trendClass">
  <el-icon><component :is="card.trendIcon" /></el-icon>
  {{ card.trend }}
</div>
```
CSS, `dashboard/Index.vue:593-603` (`.stat-trend` base plus `.positive` / `.neutral` modifiers, using `--accent-cool-soft` / `--surface-2`).

**Gap (important):** nothing in the repo compares *row N* against *row N+1* of a grouped history. `dashboard/Index.vue:274-298` compares a **level** to a fixed enum; `wellness` reads a precomputed `metric.trend` percentage. So the D-02/D-03 delta is genuinely new logic: compute `current.riskScore - previous.riskScore` where `previous` is the same `diseaseName` inside the **next** batch. D-03 forbids percent — the wellness `{{ Math.abs(x).toFixed(1) }}%` tail is the wrong template; take only the icon-switch plus class-modifier idiom.

Reuse these existing helpers rather than adding new ones (`Risk.vue:421-437`):
```ts
const getRiskLevelType = (level: string): string => {
  const types: Record<string, string> = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' }
  return types[level] || 'info'
}
const getRiskLevelLabel = (level: string): string => {
  const labels: Record<string, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }
  return labels[level] || level
}
```
Backend thresholds for the batch header's "本次最高风险等级" — `hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java:562-572`: `>= 60 → HIGH`, `>= 35 → MEDIUM`, else `LOW`. The front end already receives `riskLevel` per row, so rank by `{HIGH:3, MEDIUM:2, LOW:1}` rather than re-deriving from score; do not duplicate the 60/35 constants.

#### 4. `el-tag` / badge for level-change indicators

In-file precedent (same token mapping), `Risk.vue:157-165`:
```vue
<el-table-column prop="riskLevel" label="风险等级" width="120">
  <template #default="{ row }">
    <el-tag :type="getRiskLevelType(row.riskLevel)">
      {{ getRiskLevelLabel(row.riskLevel) }}
    </el-tag>
  </template>
</el-table-column>
```
Same pattern reused in the dialog, `Risk.vue:204-208`.

Composed title-row tag (tag living inside a collapse header) — this is the exact precedent for "风险等级 tag + 变化徽标" on one header line, `DeviceSync.vue:203-207` (quoted in §1).

Tag size variants across views: `dashboard/Index.vue:100`, `goals/Index.vue:32`, `health/Score.vue:98` (`type="warning" size="small" effect="dark"` for the "预估数据" badge — the closest existing "this number is qualified" marker).

**Gap:** no template in `src/` renders an `A → B` transition (grep for `→` inside templates found only comments and CSS). Compose it from primitives: `{{ getRiskLevelLabel(prev) }}<el-icon><Right /></el-icon>{{ getRiskLevelLabel(curr) }}`, or a single tag whose text is the transition and whose `type` follows the **worse** level. Prefer the single-tag variant so the spec's `el-tag` stub (props `type/size/effect`) still captures a stable `.el-tag` node.

#### 5. Batch-card container, loading, and empty state

- `v-loading` on a **plain div** (not a table): `hhs-frontend-v2/src/views/dashboard/Index.vue:130` — `<div v-loading="alertLoading" class="alerts-content">`. Reuse on the new `.risk-batches` wrapper so the existing `loading` ref (`Risk.vue:280`) keeps working.
- Empty state: `hhs-frontend-v2/src/views/goals/Index.vue:26` — `<el-empty v-if="!loading && goals.length === 0" description="暂无健康目标" />`; also `dashboard/Index.vue:107-109`. Replaces `el-table`'s implicit empty frame.
- Section/panel container conventions: `Risk.vue:764-770` (`.risk-preparing`) and `:799-805` (`.risk-coverage`) — copy that box model, not the table.
- Date formatting: keep `formatDateTime` from `hhs-frontend-v2/src/utils/format.ts:29-31` (`dayjs(date).format('YYYY-MM-DD HH:mm:ss')`), already imported at `Risk.vue:274`.
- Legacy rows (`contributors === null` / `[]`) must not break the delta: `Risk.vue:224` already guards with `v-if="currentAssessment?.contributors?.length"`, and `types/api.ts:330-336` documents that an empty array means "该记录创建时未保存明细 … 此时不得渲染明细". Batch expansion for `legacy-*` therefore shows the same sentence as text, not a broken list.

#### 6. Contracts that must survive the rewrite (source-level tests)

- `showError(error, 'profile.loadFailed', { presentStates: true })` at `Risk.vue:453` and `'profile.saveFailed'` at `:479` — asserted verbatim at `PreventionModuleMigration.spec.ts:459-465`.
- Zero bare `ElMessage.error` — asserted at `PreventionModuleMigration.spec.ts:434-437`; the file has exactly one `ElMessage.success` (`Risk.vue:475`), which stays.
- Read-only panel contract — asserted at `PreventionModuleMigration.spec.ts:467-477` (`dataSources`, `risk-preparing`, `contributors` literals must remain).
- `defineExpose({ viewDetail, handleCreateClick })` at `Risk.vue:639` — extend, never shrink, this expose (tests drive `vm.viewDetail(row)`).
- Pagination block `Risk.vue:185-195` binds `pagination` (`:285-289`) and `fetchAssessments` (`:439-457`). It sits **outside** the table today, so removing the table leaves it intact. Note `getRiskAssessments` ignores its params entirely (`src/api/prevention.ts:72-74` returns the whole array) and `pagination.total = data.length` (`:449`) — pagination is effectively cosmetic. Keep or drop it, but do not "fix" it in this phase.

---

### `hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts` (test)

**Analogs:** `hhs-frontend-v2/src/__tests__/views/DeviceSyncMigration.spec.ts:171-172` and `OcrMigration.spec.ts:152-153` for the collapse stubs; the file's **own** stub block for everything else.

#### Current stub set (must be extended, `RiskBackfill.spec.ts:88-130`)

```ts
const globalStubs = {
  'el-card': { template: '<div class="el-card"><slot name="header" /><slot /></div>' },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-table': {
    template: '<div class="el-table"><slot /></div>',
    props: ['data', 'stripe']
  },
  'el-table-column': {
    template: '<div class="el-table-column" />',
    props: ['prop', 'label', 'width', 'minWidth', 'showOverflowTooltip', 'fixed']
  },
  'el-tag': {
    template: '<span class="el-tag"><slot /></span>',
    props: ['type', 'size', 'effect']
  },
  ... el-pagination / el-button / el-alert / el-skeleton / el-dialog / el-descriptions(-item)
}
```

**There is no `el-collapse` / `el-collapse-item` stub here**, and none in `PreventionModuleMigration.spec.ts:174-231` either (verified). Vitest mounts without a global EP install, so an unstubbed `<el-collapse>` renders as an unknown element and its default slot content is **not** rendered — every batch card body would silently vanish, while the existing `.risk-dialog-breakdown__item` / `.risk-estimate__source` queries would still pass.

Add, copied from the two proven stubs (`DeviceSyncMigration.spec.ts:171-172` — keeps `modelValue`/`accordion`, so a `v-model="activeBatchNames"` binding does not warn):
```ts
'el-collapse': { template: '<div class="el-collapse"><slot /></div>', props: ['modelValue', 'accordion'] },
'el-collapse-item': { template: '<div class="el-collapse-item"><slot /></div>', props: ['name', 'title'] },
```
`OcrMigration.spec.ts:152-153` is the slimmer variant (no `modelValue`); use the DeviceSync one because Phase 15 needs the model.

Also add `'el-empty': { template: '<div class="el-empty" />', props: ['description'] }` — the shape already used at `PreventionModuleMigration.spec.ts:226`.

These stubs render `<slot />` unconditionally, so collapsed panels are **always visible** in tests. Assertions must key off class names and text, never off "visible only when expanded". If a "collapsed by default" assertion is needed, assert the model array (`vm.activeBatchNames`) rather than DOM visibility.

#### What the file actually asserts about the table — correction to CONTEXT 已知约束

CONTEXT says "现有 `RiskBackfill.spec.ts` 断言表格渲染 3 行，改为批次卡片后需同步更新". **No such assertion exists in the current file.** Exhaustive grep for row/table assertions yields only:

- `RiskBackfill.spec.ts:93-100` — the `el-table` / `el-table-column` *stubs*. Scaffolding only; the `el-table-column` stub renders nothing, so no cell content is observable at all.
- `RiskBackfill.spec.ts:286-287` — `wrapper.findAll('.risk-estimate__source')` with `expect(rows.length).toBeGreaterThan(0)`: the **data-source panel**, unrelated to the table.
- `RiskBackfill.spec.ts:376-377` — `expect(items.length).toBe(4)` on `.risk-dialog-breakdown__item`: the **dialog breakdown**, unrelated to the table.
- The "badge never inside `.el-table`" assertion lives in `ScoreEstimateBadge.spec.ts:298` (for `Score.vue`), not here.

Related but separate: `PreventionModuleMigration.spec.ts:410-424` reads `vm.assessments` and asserts `toHaveLength(1)`. That is the **flat array**, and it must still hold after grouping — grouping must be a *derived* computed, never a replacement of `assessments.value`, or that test breaks.

Required edits are therefore: **extend the stub set** (collapse + empty; the now-unused `el-table*` stubs may stay, harmless), and **add** batch assertions. Nothing needs deleting. Flag this discrepancy to the planner instead of scheduling a removal that has no target.

#### Fixture and helper shapes to preserve

Fixture `RiskBackfill.spec.ts:133-166` — a single `diabetesAssessment` with **no `batchId`** (`createTime: '2026-11-17 10:00:00'`). It will land in a `legacy-<createTime>` group, which is convenient: acceptance criterion 5 (legacy compat) is exercised by the existing fixture as-is. Add a second fixture with two batches (same three disease names, different scores, real `batchId: 'b...'`) for delta and level-change assertions.

`beforeEach` mock contract, `RiskBackfill.spec.ts:198-206`:
```ts
getRiskAssessments.mockResolvedValue({ data: [diabetesAssessment] })
getProfile.mockResolvedValue({ data: profileWithGaps })
getCompleteness.mockResolvedValue({ data: completenessPayload })
createRiskAssessment.mockResolvedValue({ data: [diabetesAssessment] })
```
Note `{ data: [...] }` only — **no `code: 200`**. `Risk.vue:442-449` reads `res.data` directly and normalizes with `Array.isArray`. New fixtures must follow this envelope.

Dialog driver that must keep working, `RiskBackfill.spec.ts:361-369`:
```ts
async function openDetail(wrapper: ReturnType<typeof mountRisk>) {
  const vm = wrapper.vm as unknown as { viewDetail: (row: RiskAssessmentVO) => void }
  vm.viewDetail(diabetesAssessment)
  await flushPromises()
}
```
Batch-card rows have no reachable button in the stub world either — keep driving `viewDetail` from the VM (`defineExpose`).

The "no contributors" case, `RiskBackfill.spec.ts:406-414`, passes `{ ...diabetesAssessment, contributors: [] }` and expects `.risk-dialog-breakdown` absent. After the rewrite, a batch of such rows must render the legacy sentence instead — mirror that same "empty snapshot ⇒ no detail list" rule.

---

## Shared Patterns

### Error surface
**Source:** `hhs-frontend-v2/src/views/prevention/Risk.vue:450-456`
**Apply to:** the rewritten history loader (behavior unchanged — keep rows on failure)
```ts
} catch (error) {
  // 表格保留原有行（UI Considerations · error E7）：失败时不清空 assessments。
  showError(error, 'profile.loadFailed', { presentStates: true })
}
```
`PreventionModuleMigration.spec.ts:394-426` asserts array identity is preserved (`expect(vm.assessments).toBe(before)`), so the batch computed must derive from `assessments.value` and never write back to it.

### Design tokens for the new markup
**Source:** `hhs-frontend-v2/src/views/prevention/Risk.vue:798-943` (`.risk-coverage` block)
**Apply to:** `.risk-batches`, `.risk-batch__*`

Confirmed-defined tokens: `--surface-1` / `--surface-2` (`theme.css:17-18`, `:135-136`), `--text-1/2/3` (`:28-30`, `:146-148`), `--radius-md` / `--radius-lg` (`:84-85`), `--border` (`:23` dark, `:141` light), `--border-strong` (`:24`, `:142`), `--hhs-color-error-text` (`:58`, `:173`).
Severity triples as already used at `Risk.vue:904-932`: error = `var(--hhs-color-error-text)` + `var(--hhs-color-error-tint)`; warning = `var(--warning-700, #b45309)` + `var(--warning-50, #fffbeb)`; neutral = `var(--text-2)` + `var(--surface-2)`. `--warning-700`, `--warning-50`, `--success-700`, `--success-50` are **not** defined in theme.css, so keep the `var(--x, #hex)` fallback form exactly as the file does.

**Pre-existing defect — do NOT copy verbatim.** The container rule at `Risk.vue:802` is

```css
border: 1px solid var(--border-1);
```

`--border-1` is **never defined** anywhere in `hhs-frontend-v2/src` (grep for `--border-1:` across the whole frontend: 0 hits) while being *used* 8 times, all inside `Risk.vue` (`705, 706, 767, 802, 851, 863, 980, 992`). With no fallback, an unresolved `var()` invalidates the declaration at computed-value time, so those borders render as **none**. New batch-card markup must use `var(--border)` instead.

Do not silently "fix" the eight existing usages either: no test covers them (`PreventionModuleMigration.spec.ts:434-477` asserts specific literals, none being `--border-1`), so it would be an unverified visual change. Record as a separate follow-up; if the planner wants it in-phase, it needs a declared scope addition plus a stated verification step.

### Typography override rule
**Source:** `Risk.vue:1081-1091` (Contract 6 normalization)
**Apply to:** any new button/label in the batch header — the page already forces `600 14px/1.5 var(--font-ui)` on primary buttons and neutralizes EP's `line-height: 1`. New markup inherits this; do not add a second competing rule. Also note the global `ul { list-style: none; margin: 0; padding: 0 }` in theme.css (comment at `Risk.vue:684-685`), so any real `<ul>` must re-declare as `Risk.vue:686-694` does.

### Test stub hygiene
**Source:** `hhs-frontend-v2/src/__tests__/views/PreventionModuleMigration.spec.ts:174-231`
**Apply to:** both specs — `baseStubs`/`globalStubs` is the canonical EP surface for this page. If a new EP component (`el-collapse`, `el-collapse-item`, `el-empty`) is introduced it must be stubbed in **both** `RiskBackfill.spec.ts` and `PreventionModuleMigration.spec.ts`, or the second spec's mount silently diverges from the first.

---

## No Analog Found

| File / concern | Role | Data Flow | Reason |
|---|---|---|---|
| batch-to-batch delta computation (`current.score - previous.score` per `diseaseName`) | view logic | transform | No file compares row N to row N+1 of a history; `wellness` uses a backend-computed `trend`, `screening` a backend `UP/DOWN` enum |
| `Map.entries()` → grouped array rendered via `v-for` | view logic | transform | Only `Map`-for-lookup exists (`Risk.vue:369`, `DeviceSync.vue:413`); no grouped-array render precedent |
| `A → B` level-transition badge | component | transform | No template in `src/` renders a transition string; only CSS/comments contain `→` |
| "首次评估" (no-previous-batch) state | view logic | transform | No "first record in series" wording anywhere; nearest phrasing precedent is empty-state strings (`goals/Index.vue:26`, `dashboard/Index.vue:107`) |

For these four, use CONTEXT §关键决策 D-02/D-03/D-04 as the spec, and lift only the *idioms* (icon switch, class modifiers, local interface + computed) from the analogs above.

## Metadata

**Analog search scope:** `hhs-frontend-v2/src/views/**`, `hhs-frontend-v2/src/components/**`, `hhs-frontend-v2/src/__tests__/views/**`, `hhs-frontend-v2/src/utils/format.ts`, `hhs-frontend-v2/src/types/api.ts`, `hhs-frontend-v2/src/assets/styles/theme.css`, plus read-only inspection of `hhs-backend/.../RiskAssessmentServiceImpl.java` for level thresholds and list ordering.
**Files scanned:** 12 read, ~40 grepped (searched `el-collapse`, `el-badge`, `Top/Bottom/Minus`, `trend|delta|previous`, `new Map<`, `→`, `el-empty`, `defineExpose`, `--border-1`).
**Tracked-source check:** every cited path verified with `git ls-files --` (non-empty output): `Risk.vue`, `RiskBackfill.spec.ts`, `PreventionModuleMigration.spec.ts`, `DeviceSyncMigration.spec.ts`, `OcrMigration.spec.ts`, `DeviceSync.vue`, `DeviceConfigWizard.vue`, `OcrHistory.vue`, `OcrInput.vue`, `dashboard/Index.vue`, `wellness/Dashboard.vue`, `screening/Detail.vue`, `goals/Index.vue`, `health/Score.vue`, `utils/format.ts`, `types/api.ts`. No gitignored mirror paths used.
**Pattern extraction date:** 2026-11-17