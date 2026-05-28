---
phase: 02-code-review
reviewed: 2026-05-28T09:38:00+08:00
depth: deep
files_reviewed: 20
files_reviewed_list:
  - hhs-frontend-v2/src/utils/echarts-theme.ts
  - hhs-frontend-v2/src/composables/useECharts.ts
  - hhs-frontend-v2/src/components/SparklineChart.vue
  - hhs-frontend-v2/src/views/health/Score.vue
  - hhs-frontend-v2/src/views/realtime/Monitor.vue
  - hhs-frontend-v2/src/views/dashboard/Index.vue
  - hhs-frontend-v2/src/views/data-input/components/DeviceSync.vue
  - hhs-frontend-v2/src/api/score.ts
  - hhs-frontend-v2/src/api/stats.ts
  - hhs-frontend-v2/src/types/api.ts
  - hhs-frontend-v2/src/main.ts
  - hhs-backend/src/main/java/com/hhs/domain/event/ScoreUpdatedEvent.java
  - hhs-backend/src/main/java/com/hhs/domain/handler/ScoreUpdatedEventListener.java
  - hhs-backend/src/main/java/com/hhs/service/impl/HealthScoreServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/StatsServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/ExportServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/controller/StatsController.java
  - hhs-backend/src/main/java/com/hhs/controller/ExportController.java
  - hhs-backend/src/main/java/com/hhs/controller/HealthScoreController.java
  - hhs-backend/src/main/java/com/hhs/service/impl/HealthReportServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/vo/HealthReportVO.java
  - hhs-backend/src/main/java/com/hhs/entity/HealthReport.java
  - hhs-backend/src/main/java/com/hhs/entity/ExportJob.java
  - hhs-backend/src/main/java/com/hhs/dto/ExportRequest.java
  - hhs-backend/src/main/java/com/hhs/vo/HealthScoreVO.java
  - hhs-backend/src/main/java/com/hhs/vo/TrendDataVO.java
  - hhs-backend/src/main/java/com/hhs/vo/DashboardSummaryVO.java
  - hhs-backend/src/main/java/com/hhs/vo/WeeklyStatsVO.java
  - hhs-backend/src/main/java/com/hhs/vo/MonthlyStatsVO.java
  - hhs-backend/src/test/java/com/hhs/service/HealthScoreServiceTest.java
  - hhs-backend/src/test/java/com/hhs/service/impl/ScoreHistoryServiceImplTest.java
findings:
  critical: 3
  warning: 3
  info: 3
  total: 9
status: issues_found
---

# Phase 2: Code Review Report

**Reviewed:** 2026-05-28T09:38:00+08:00
**Depth:** deep
**Files Reviewed:** 20
**Status:** issues_found

## Summary

Reviewed Phase 2 changes covering the ECharts theme system, chart refactoring with `useECharts` composable, sparklines, stats API endpoints, data export (CSV/Excel), AI report `isEstimated` flag, and verification of 10 prior Phase 1 review fixes.

The prior Phase 1 fixes are correctly implemented: factors use `Object.keys()`, history maps `scoreDate`/`overallScore`, `ScoreUpdatedEvent` carries level and factors, `TransactionalEventListener(AFTER_COMMIT)` is used, `historyData` uses `shallowRef`, `isMockData` returns false, `onUnmounted` is outside `onMounted` in Monitor.vue, `IntersectionObserver` is disconnected on unmount, days parameter is bounded in `HealthScoreController`, and stats.ts types are aligned with backend VOs.

However, three critical issues were found: a `@Async` self-invocation that silently degrades to synchronous execution, a memory leak from an unremoved resize listener, and fabricated data displayed as historical comparison in the radar chart.

## Critical Issues

### CR-01: Export @Async self-invocation bypasses Spring proxy -- export runs synchronously

**File:** `hhs-backend/src/main/java/com/hhs/service/impl/ExportServiceImpl.java:77`

**Issue:** `createExport()` calls `this.processExportAsync(job.getId())` at line 77. Since `processExportAsync` is a method on the same class, this is a self-invocation that bypasses the Spring AOP proxy. The `@Async("taskExecutor")` annotation on line 108 will not take effect. The method executes synchronously in the HTTP request thread, blocking until the entire export (CSV/Excel generation, file I/O) completes. For large datasets, this will cause HTTP timeouts and exhaust the servlet thread pool.

**Fix:** Inject the service into itself using `@Lazy` to break the proxy cycle, or extract the async method into a separate bean:

```java
// Option A: Self-injection (add to ExportServiceImpl)
@Lazy
private final ExportServiceImpl self;

// Then change line 77 to:
self.processExportAsync(job.getId());

// Option B: Extract to a separate @Service bean (preferred)
@Service
public class ExportProcessor {
    @Async("taskExecutor")
    public void processExportAsync(Long jobId, ExportJobMapper exportJobMapper, ...) { ... }
}
```

### CR-02: SparklineChart leaks resize event listeners -- never removed

**File:** `hhs-frontend-v2/src/components/SparklineChart.vue:64`

**Issue:** The component adds a `window.addEventListener('resize', ...)` on mount (line 64) but never removes it in `onUnmounted` (lines 67-69, which only disposes the chart). Each mount/unmount cycle leaks one listener. On pages like the dashboard with 4 sparklines, navigating away and back accumulates unbounded listeners, causing memory leaks and phantom resize callbacks on disposed chart instances.

**Fix:** Store the listener reference and remove it on unmount:

```typescript
const handleResize = () => chartInstance?.resize()

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
```

### CR-03: Score radar chart displays fabricated random data as "previous week" comparison

**File:** `hhs-frontend-v2/src/views/health/Score.vue:331`

**Issue:** The radar chart generates "previous week" data using `Math.random()`:
```typescript
prevValues.push(Math.max(0, (value as any).score - Math.floor(Math.random() * 15)))
```
This fabricated data is displayed alongside real current data with the legend labels "本周" and "上周", misleading users into believing they are viewing actual historical comparison. The random values change on every page load, creating an inconsistent and untrustworthy experience. This is a data integrity issue in a health monitoring application where users rely on accuracy for health decisions.

**Fix:** Either fetch actual historical factor data from the backend, or remove the fabricated "上周" series entirely and show only current data:

```typescript
// Option A: Remove fabricated data, show only current
series: [{
  type: 'radar',
  data: [{
    value: currentValues,
    name: '当前',
    areaStyle: { opacity: 0.2 },
    lineStyle: { width: 2 },
  }],
}]

// Option B: Fetch real historical data (requires backend endpoint)
// Add an API endpoint that returns previous period's factor scores
```

## Warnings

### WR-01: Stats trends endpoint has no upper bound on days parameter

**File:** `hhs-backend/src/main/java/com/hhs/controller/StatsController.java:107`

**Issue:** The `/api/stats/trends` endpoint accepts `days` with only a default value of 30 and no upper bound. A malicious or careless client can pass `days=999999`, causing the backend to query an unbounded date range. The `buildMetricCountTrend` method (StatsServiceImpl:442) executes one SQL query per day in the range, so `days=36500` (100 years) would execute 36,500 queries in a single request. Compare with `HealthScoreController.getScoreHistory` which correctly bounds `days` to `[1, 365]`.

**Fix:** Add bounds validation matching the pattern used in `HealthScoreController`:

```java
public Result<List<TrendDataVO>> getTrends(
        @RequestParam(required = false) String metric,
        @RequestParam(defaultValue = "30") int days) {
    if (days < 1) days = 1;
    if (days > 365) days = 365;
    // ...
}
```

### WR-02: Stats API uses wrong import -- raw Axios instance instead of typed request wrapper

**File:** `hhs-frontend-v2/src/api/stats.ts:1`

**Issue:** `stats.ts` uses `import request from '@/utils/request'` (default import, the raw Axios instance) instead of `import { request } from '@/utils/request'` (named import, the typed wrapper). All other API modules except `push.ts` use the named import. The typed wrapper provides `Promise<ApiResponse<T>>` return types that match the project's API contract, while the raw Axios instance returns `AxiosPromise<AxiosResponse<T>>`. The response interceptor makes this work at runtime, but TypeScript types are incorrect -- callers see `AxiosResponse` instead of `ApiResponse`, causing type confusion at call sites.

**Fix:** Change to the named import:

```typescript
import { request } from '@/utils/request'
```

### WR-03: Trends date range off-by-one -- returns one extra day of data

**File:** `hhs-backend/src/main/java/com/hhs/service/impl/StatsServiceImpl.java:182`

**Issue:** `getTrends` calculates the start date as `endDate.minusDays(days - 1L)` and queries with `.ge(startDate)`, creating an inclusive range of `days` days. However, the query also uses `.le(endDate)` which includes today. For `days=7`, this yields 7 days (today minus 6 through today), which appears correct. But the frontend Dashboard calls `statsApi.getTrends('health_score', 7)` and the backend `ScoreHistoryServiceImpl.getHistory` uses `LocalDate.now().minusDays(days)` with `.ge(fromDate)` -- a different convention. The two date range conventions are inconsistent, and the trends endpoint returns one extra day compared to the history endpoint for the same `days` value. This causes subtle data mismatches if both are displayed on the same page.

**Fix:** Align the convention. Use `minusDays(days)` (exclusive start) consistently:

```java
LocalDate startDate = endDate.minusDays(days);  // was: minusDays(days - 1L)
```

## Info

### IN-01: Unused variables in Dashboard/Index.vue

**File:** `hhs-frontend-v2/src/views/dashboard/Index.vue:168-169`

**Issue:** `healthScore` (line 168) is computed in `fetchHealthScore` but never referenced in the template -- the template uses `scoreData.score` directly via `HealthScoreCircle`. `reportCount` (line 169) is declared but never populated by any API call, permanently displaying 0 in the stats card.

**Fix:** Remove `healthScore` or use it in the template. Add an API call to populate `reportCount`, or remove the stat card if there is no report count endpoint yet.

### IN-02: Export endpoint missing explicit authorization annotation

**File:** `hhs-backend/src/main/java/com/hhs/controller/ExportController.java:36`

**Issue:** `ExportController` does not use `@PreAuthorize("isAuthenticated()")` on its endpoints, unlike `StatsController` which uses it consistently. While the JWT filter provides authentication, the explicit annotation provides defense-in-depth and is the established pattern in this codebase.

**Fix:** Add `@PreAuthorize("isAuthenticated()")` to each endpoint method, matching StatsController's pattern.

### IN-03: Import inconsistency in stats.ts

**File:** `hhs-frontend-v2/src/api/stats.ts:1`

**Issue:** Uses `import request from '@/utils/request'` (default import) while 13 of 15 other API modules use `import { request } from '@/utils/request'` (named import). Only `push.ts` shares this inconsistency. This is a code style issue that also affects type correctness (see WR-02).

**Fix:** Align with the majority convention: `import { request } from '@/utils/request'`

---

_Reviewed: 2026-05-28T09:38:00+08:00_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
