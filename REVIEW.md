---
phase: 02-code-review
reviewed: 2026-05-28T08:55:00Z
depth: deep
files_reviewed: 16
files_reviewed_list:
  - hhs-frontend-v2/src/views/dashboard/Index.vue
  - hhs-frontend-v2/src/views/realtime/Monitor.vue
  - hhs-frontend-v2/src/views/wellness/Dashboard.vue
  - hhs-frontend-v2/src/views/health/Score.vue
  - hhs-frontend-v2/src/assets/styles/3d-enhance.css
  - hhs-frontend-v2/src/main.ts
  - hhs-frontend-v2/src/views/data-input/components/DeviceSync.vue
  - hhs-frontend-v2/src/api/score.ts
  - hhs-frontend-v2/src/utils/request.ts
  - hhs-frontend-v2/src/composables/useECharts.ts
  - hhs-backend/src/main/java/com/hhs/entity/HealthScoreHistory.java
  - hhs-backend/src/main/java/com/hhs/mapper/HealthScoreHistoryMapper.java
  - hhs-backend/src/main/java/com/hhs/service/ScoreHistoryService.java
  - hhs-backend/src/main/java/com/hhs/service/impl/ScoreHistoryServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/domain/handler/ScoreUpdatedEventListener.java
  - hhs-backend/src/main/java/com/hhs/domain/event/ScoreUpdatedEvent.java
  - hhs-backend/src/main/java/com/hhs/controller/HealthScoreController.java
  - hhs-backend/src/main/resources/sql/schema.sql
  - hhs-backend/src/test/java/com/hhs/service/impl/ScoreHistoryServiceImplTest.java
findings:
  critical: 4
  warning: 5
  info: 4
  total: 13
status: issues_found
---

# Phase 2: Code Review Report

**Reviewed:** 2026-05-28T08:55:00Z
**Depth:** deep
**Files Reviewed:** 16
**Status:** issues_found

## Summary

Reviewed 16 files spanning frontend Vue 3 components, backend Spring Boot services, the score history feature, and database schema. The review identified 4 critical bugs, 5 warnings, and 4 info-level findings. The most significant issues are: (1) a runtime crash in `Index.vue` where a Map-typed field is treated as an Array, (2) a frontend-backend data contract mismatch for score history that renders the history chart completely empty, (3) dimension scores (cardiovascular, metabolic, weight, lifestyle) are never persisted to history despite the schema supporting them, and (4) an `@Async` + `@EventListener` combination that creates a race condition with the publishing transaction.

---

## Critical Issues

### CR-01: `factors` type mismatch causes runtime crash in Dashboard

**File:** `hhs-frontend-v2/src/views/dashboard/Index.vue:78-84`
**Issue:** `scoreData.factors` is typed as `Record<string, any>` (a Map/Object) in `HealthScoreVO`, but the template treats it as an array by calling `.length` and `.slice(0, 3)`. The backend `HealthScoreVO.java` defines `factors` as `Map<String, Object>`. When `factors` is an object, `Object.keys(factors).length` works, but `factors.slice(0, 3)` will throw `TypeError: factors.slice is not a function` at runtime.

The same `factors` field is correctly treated as an object (iterated with `Object.entries`) in `Score.vue:327`, confirming the Index.vue usage is wrong.

**Fix:**
```vue
<!-- Replace lines 78-84 in Index.vue -->
<div v-if="scoreData.factors && Object.keys(scoreData.factors).length > 0" class="score-factors">
  <div class="factor-label">评分因素</div>
  <div class="factor-tags">
    <el-tag v-for="key in Object.keys(scoreData.factors).slice(0, 3)" :key="key" size="small">
      {{ getFactorLabel(key) }}
    </el-tag>
  </div>
</div>
```

---

### CR-02: Score history API response field mapping mismatch -- chart always empty

**File:** `hhs-frontend-v2/src/views/health/Score.vue:288-293` and `hhs-frontend-v2/src/api/score.ts:21-25`
**Issue:** The frontend `Score.vue` maps history data as `item.date`, `item.score`, and `item.level` (lines 292-293), but the backend `HealthScoreHistory` entity returns fields named `scoreDate`, `overallScore`, and has no `level` field at all. The API type in `scoreApi.getScoreHistory()` declares the return type as `Array<{ date: string; score: number; level: string }>` which does not match the actual backend response of `List<HealthScoreHistory>`.

This means `dates` will be an array of `undefined`, `scores` will be an array of `undefined`, and the history chart will render with no visible data -- silently broken.

**Fix:** Map the response in the frontend to match the actual backend entity:

```typescript
// In Score.vue fetchHistory(), after getting res.data:
const mapped = res.data.map((item: any) => ({
  date: item.scoreDate,
  score: item.overallScore,
}))
historyData = mapped
updateOption()
```

Also update the `scoreApi.getScoreHistory()` return type to match `HealthScoreHistory`.

---

### CR-03: Dimension scores (cardiovascular, metabolic, weight, lifestyle) never persisted to history

**File:** `hhs-backend/src/main/java/com/hhs/domain/handler/ScoreUpdatedEventListener.java:43-48`
**Issue:** The `ScoreUpdatedEvent` only carries `newScore` (an `Integer`) and `factors` (a `Map<String, Object>`). When building the `HealthScoreHistory` entity in the listener, only `overallScore` is set. The fields `cardiovascularScore`, `metabolicScore`, `weightScore`, and `lifestyleScore` are left as `null`, even though the `health_score_history` table schema defines `DECIMAL(5,2)` columns for all of them and the `insertOrUpdate` mapper writes all six columns.

This is a data loss bug: every time a score is saved, dimension scores are permanently lost. The `ScoreHistoryServiceImplTest` also creates test data with all dimension scores populated, giving a false sense that they are being persisted.

**Fix:** Extend `ScoreUpdatedEvent` to carry dimension scores, and populate them in the listener:

```java
// In ScoreUpdatedEvent.java - add fields:
private final BigDecimal cardiovascularScore;
private final BigDecimal metabolicScore;
private final BigDecimal weightScore;
private final BigDecimal lifestyleScore;

// In ScoreUpdatedEventListener.java - populate the history:
HealthScoreHistory history = HealthScoreHistory.builder()
        .userId(event.getUserId())
        .scoreDate(LocalDate.now())
        .overallScore(BigDecimal.valueOf(event.getNewScore()))
        .cardiovascularScore(event.getCardiovascularScore())
        .metabolicScore(event.getMetabolicScore())
        .weightScore(event.getWeightScore())
        .lifestyleScore(event.getLifestyleScore())
        .factorsSnapshot(factorsJson)
        .build();
```

---

### CR-04: `@Async` + `@EventListener` race condition -- score history save may execute before publishing transaction commits

**File:** `hhs-backend/src/main/java/com/hhs/domain/handler/ScoreUpdatedEventListener.java:30-31`
**Issue:** The listener uses `@Async("eventExecutor")` combined with `@EventListener`. Spring's `@EventListener` fires synchronously within the same transaction context as the publisher. When combined with `@Async`, the listener is dispatched to a thread pool immediately, but the publishing transaction may not have committed yet. If the score calculation and event publishing happen within a `@Transactional` method, the `ScoreUpdatedEvent` is fired before commit, and the async listener could try to insert a history record that conflicts with uncommitted state or reads stale data.

The `OcrEventListener` in the same codebase correctly uses `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` -- the correct pattern already exists in this project but was not applied here.

**Fix:**
```java
@Async("eventExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onScoreUpdated(ScoreUpdatedEvent event) {
    // ... existing implementation
}
```

---

## Warnings

### WR-01: `historyData` in Score.vue is not reactive -- fragile chart update pattern

**File:** `hhs-frontend-v2/src/views/health/Score.vue:288`
**Issue:** `historyData` is declared as `let historyData: any[] = []` (a plain variable, not a `ref`). When `fetchHistory` assigns `historyData = res.data`, the `buildChartOption` closure captures the old reference until `updateOption()` is explicitly called. While the current code does call `updateOption()` after assignment, this pattern is fragile -- any future refactoring that forgets the explicit call will silently break chart updates.

**Fix:** Use `shallowRef` for `historyData`:
```typescript
const historyData = shallowRef<any[]>([])
// In fetchHistory:
historyData.value = res.data
```

---

### WR-02: DeviceSync `isMockData` computed property is always true in dev, regardless of actual data source

**File:** `hhs-frontend-v2/src/views/data-input/components/DeviceSync.vue:363-366`
**Issue:** `isMockData` is computed as `import.meta.env.DEV`, which is `true` whenever the Vite dev server is running. This means even when connected to a real backend with real device data, the "模拟数据" (mock data) badge is shown. The purpose of C7 (Xiaomi mock data labeling) is defeated -- users cannot distinguish real device data from mock data in development.

**Fix:** Check the actual data source from the API response rather than the build environment:
```typescript
const isMockData = computed(() => {
  return devices.value.some(d => d.dataSource === 'mock' || d.statusName?.includes('模拟'))
})
```

---

### WR-03: `onUnmounted` nested inside `onMounted` in Monitor.vue -- cleanup may not run if mounting fails

**File:** `hhs-frontend-v2/src/views/realtime/Monitor.vue:320-344`
**Issue:** `onUnmounted` is registered inside the `onMounted` callback (line 340). If `onMounted` throws before reaching `onUnmounted` registration (e.g., `fetchLatestMetrics()` throws an uncaught error), the cleanup callback will never be registered, causing the `refreshInterval` and `pingInterval` to leak. Standard Vue practice is to register `onUnmounted` at the top level of `<script setup>`.

**Fix:** Move cleanup registration to the top level:
```typescript
let refreshInterval: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  // ... setup
  refreshInterval = setInterval(() => { fetchLatestMetrics() }, 10000)
  startPing()
})

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
  if (pingInterval) clearInterval(pingInterval)
})
```

---

### WR-04: IntersectionObserver not disconnected on unmount in Dashboard

**File:** `hhs-frontend-v2/src/views/dashboard/Index.vue:293-297`
**Issue:** The `IntersectionObserver` created in `onMounted` is never disconnected in `onUnmounted`. While `obs.unobserve(e.target)` removes individual targets after they become visible, the observer instance itself persists. If the component remounts (e.g., due to route changes with keep-alive), observers accumulate. This is a minor memory leak.

**Fix:**
```typescript
let revealObserver: IntersectionObserver | null = null

onMounted(() => {
  // ... existing code
  const obs = new IntersectionObserver(/* ... */)
  revealObserver = obs
  document.querySelectorAll('.reveal').forEach(el => obs.observe(el))
})

onUnmounted(() => {
  revealObserver?.disconnect()
})
```

---

### WR-05: No input validation on `days` parameter in score history endpoint

**File:** `hhs-backend/src/main/java/com/hhs/controller/HealthScoreController.java:79`
**Issue:** The `getScoreHistory` endpoint accepts `@RequestParam(defaultValue = "30") Integer days` with no validation. A caller could pass `days=-1` or `days=0`. `LocalDate.now().minusDays(-1)` adds a day (unexpected behavior), and `minusDays(0)` returns today only. While the frontend constrains this to 7/30/90, the API is publicly accessible behind JWT auth.

**Fix:**
```java
@GetMapping("/score/history")
public Result<List<HealthScoreHistory>> getScoreHistory(
        @RequestParam(defaultValue = "30")
        @Min(value = 1, message = "days must be at least 1")
        @Max(value = 365, message = "days must not exceed 365")
        Integer days) {
```

---

## Info

### IN-01: `console.log` / `console.error` debug artifacts in production code

**Files:**
- `hhs-frontend-v2/src/views/health/Score.vue:488` -- `console.log('Report generation cancelled')`
- `hhs-frontend-v2/src/views/realtime/Monitor.vue:269` -- `console.error('Failed to fetch latest metrics:', error)`
- `hhs-frontend-v2/src/views/data-input/components/DeviceSync.vue:408` -- `console.error('加载设备列表失败', error)`
- `hhs-frontend-v2/src/views/data-input/components/DeviceSync.vue:423` -- `console.error('加载平台元数据失败', error)`
- `hhs-frontend-v2/src/views/data-input/components/DeviceSync.vue:656` -- `console.error('加载同步历史失败', error)`

**Issue:** `console.log` and `console.error` statements should not remain in production code. The `request.ts` interceptor already has centralized error logging, making most of these redundant.

**Fix:** Remove or replace with a proper logging utility that can be stripped in production builds.

---

### IN-02: `Score.vue` radar chart generates fake "previous week" data with `Math.random()`

**File:** `hhs-frontend-v2/src/views/health/Score.vue:331`
**Issue:** The radar chart shows a "上周" (last week) comparison series using `Math.max(0, score - Math.floor(Math.random() * 15))`. This generates random data on every render, giving a false impression of historical comparison. The data changes on every page load and is not derived from actual history.

**Fix:** Remove the fake comparison series or fetch actual historical data for comparison:
```typescript
// Option 1: Only show current data
data: [
  { value: currentValues, name: '当前', areaStyle: { opacity: 0.2 } }
]
```

---

### IN-03: Duplicate metric option groups between Monitor.vue select and form

**File:** `hhs-frontend-v2/src/views/realtime/Monitor.vue:93-109, 124-140`
**Issue:** The metric type options (heartRate, systolicBP, glucose, sleepDuration, steps, etc.) are duplicated between the chart selector and the manual add form. If a new metric type is added, both must be updated independently. This violates DRY and increases the risk of inconsistency.

**Fix:** Extract the metric options into a shared constant:
```typescript
const METRIC_OPTIONS = [
  { group: '健康指标', options: [
    { label: '心率', value: 'heartRate' },
    // ...
  ]},
  // ...
]
```

---

### IN-04: Test class uses `@MockitoSettings(strictness = Strictness.LENIENT)` unnecessarily

**File:** `hhs-backend/src/test/java/com/hhs/service/impl/ScoreHistoryServiceImplTest.java:33`
**Issue:** LENIENT strictness suppresses warnings about unused stubs and unnecessary stubbing. All tests in this class appear to use their stubs correctly. Using LENIENT globally can mask future test issues where stubs are set up but never invoked.

**Fix:** Remove `@MockitoSettings(strictness = Strictness.LENIENT)` and let Mockito's default STRICT_STUBS catch any future issues.

---

_Reviewed: 2026-05-28T08:55:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
