# Phase 3: Wellness Dashboard UI - Research

**Researched:** 2026-03-03
**Domain:** Vue 3 Frontend Development with Element Plus and ECharts
**Confidence:** HIGH

## Summary

The HHS frontend-v2 codebase is a Vue 3.4 + TypeScript project with a well-structured architecture using Element Plus 2.5 for UI components, Pinia 2.1 for state management, and ECharts 5.4 for charting. The project follows a feature-based organization with clear patterns for API integration, view components, and store management.

**Primary recommendation:** Follow existing patterns from `Monitor.vue` and `prevention/Metrics.vue` for the wellness dashboard implementation. Create a new `wellness` module with its own view, API service, and Pinia store.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Vue | 3.4.15 | UI Framework | Project standard - Composition API with `<script setup>` |
| TypeScript | 5.3.3 | Type Safety | Project standard - full type coverage required |
| Element Plus | 2.5.6 | UI Components | Project standard - Chinese locale pre-configured |
| Pinia | 2.1.7 | State Management | Project standard - Composition API style stores |
| ECharts | 5.4.3 | Charts | Project standard - already used in Monitor.vue |
| Axios | 1.6.5 | HTTP Client | Project standard - wrapped in request.ts |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| vue-router | 4.2.5 | Routing | Navigation, lazy loading views |
| dayjs | (implicit) | Date formatting | Via format.ts utility |
| @element-plus/icons-vue | 2.3.1 | Icons | All Element Plus icons registered globally |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ECharts | Chart.js | ECharts already integrated, better Chinese support |
| Element Plus | Ant Design Vue | Element Plus already configured with zhCn locale |
| Pinia | Vuex | Pinia is Vue 3 standard, simpler Composition API |

**Installation:** No new packages required - all dependencies already installed.

## Architecture Patterns

### Recommended Project Structure
```
hhs-frontend-v2/src/
├── api/
│   └── wellness.ts           # NEW: Wellness API service
├── stores/
│   └── wellness.ts           # NEW: Wellness Pinia store
├── types/
│   └── api.ts                # EXTEND: Add wellness types
├── views/
│   └── wellness/
│       └── Dashboard.vue     # NEW: Main wellness dashboard
├── components/
│   └── charts/
│       └── TrendChart.vue    # NEW: Reusable trend chart component
└── utils/
    └── format.ts             # EXTEND: Add wellness metric labels
```

### Pattern 1: API Service Pattern
**What:** Centralized API calls with typed responses
**When to use:** All API interactions
**Example:**
```typescript
// Source: hhs-frontend-v2/src/api/health.ts
import { request } from '@/utils/request'
import type { HealthMetricVO, PageResult } from '@/types/api'

export const wellnessApi = {
  // GET /api/wellness - List wellness metrics
  getMetrics(params: { page?: number; size?: number; metricKey?: string }) {
    return request.get<PageResult<HealthMetricVO>>('/api/wellness', { params })
  },

  // POST /api/wellness - Create wellness metric
  createMetric(data: WellnessMetricRequest) {
    return request.post<HealthMetricVO>('/api/wellness', data)
  },

  // GET /api/wellness/trend/{metricKey} - Get trend data
  getTrend(metricKey: string, startDate: string, endDate: string) {
    return request.get<HealthMetricTrendVO>(`/api/wellness/trend/${metricKey}`, {
      params: { startDate, endDate }
    })
  },

  // GET /api/wellness/summary - Dashboard summary
  getSummary(days = 7) {
    return request.get<WellnessSummaryVO>('/api/wellness/summary', { params: { days } })
  },

  // GET /api/wellness/latest - Latest metrics
  getLatest() {
    return request.get<Record<string, HealthMetricVO>>('/api/wellness/latest')
  }
}
```

### Pattern 2: Pinia Store Pattern
**What:** Composition API style store with reactive state
**When to use:** State that needs to be shared or persisted
**Example:**
```typescript
// Source: hhs-frontend-v2/src/stores/auth.ts pattern
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { wellnessApi } from '@/api/wellness'

export const useWellnessStore = defineStore('wellness', () => {
  const summary = ref<WellnessSummaryVO | null>(null)
  const latestMetrics = ref<Record<string, HealthMetricVO>>({})
  const loading = ref(false)

  const fetchSummary = async (days = 7) => {
    loading.value = true
    try {
      const res = await wellnessApi.getSummary(days)
      summary.value = res.data
    } finally {
      loading.value = false
    }
  }

  return { summary, latestMetrics, loading, fetchSummary }
})
```

### Pattern 3: ECharts Integration Pattern
**What:** Chart component with lifecycle management
**When to use:** Trend visualization
**Example:**
```typescript
// Source: hhs-frontend-v2/src/views/realtime/Monitor.vue (lines 155-207)
import * as echarts from 'echarts'

const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

const renderChart = (data: HealthMetricTrendVO) => {
  if (!chartRef.value) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  const option = {
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: data.dates,
      boundaryGap: false
    },
    yAxis: { type: 'value' },
    series: [{
      data: data.values,
      type: 'line',
      smooth: true,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(103, 194, 58, 0.3)' },
          { offset: 1, color: 'rgba(103, 194, 58, 0.05)' }
        ])
      },
      lineStyle: { color: '#67c23a', width: 2 }
    }]
  }

  chartInstance.setOption(option)
}

// Resize on window resize
onMounted(() => {
  window.addEventListener('resize', () => chartInstance?.resize())
})

onUnmounted(() => {
  chartInstance?.dispose()
})
```

### Anti-Patterns to Avoid
- **Direct axios calls in views:** Always use api/ services for consistency
- **Mutation of props:** Use emits or Pinia store for state changes
- **Inline chart options:** Extract to separate config or composable
- **Missing loading states:** Always show loading indicators during API calls

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Date formatting | Custom formatter | dayjs via format.ts | Already integrated, handles i18n |
| HTTP errors | Custom error handler | request.ts interceptor | Centralized error handling |
| Chart rendering | Custom canvas | ECharts | Already installed, rich features |
| Form validation | Custom validator | Element Plus FormRules | Built-in validation, consistent UX |
| Pagination | Custom pagination | Element Plus el-pagination | Already used in list views |

**Key insight:** The project has mature patterns - extending existing utilities is more maintainable than creating new ones.

## Common Pitfalls

### Pitfall 1: ECharts Instance Memory Leak
**What goes wrong:** Chart instances not disposed on component unmount
**Why it happens:** Developers forget lifecycle cleanup
**How to avoid:** Always dispose in onUnmounted, use null checks
**Warning signs:** Browser tab becomes slow after navigating between chart pages

### Pitfall 2: Missing Type Definitions
**What goes wrong:** TypeScript errors for API responses
**Why it happens:** Backend VOs not mirrored in frontend types
**How to avoid:** Add types to `src/types/api.ts` matching backend VOs
**Warning signs:** `any` types, runtime type errors

### Pitfall 3: Date Format Mismatch
**What goes wrong:** API returns `LocalDate` (YYYY-MM-DD), frontend expects different format
**Why it happens:** Java LocalDate vs JavaScript Date differences
**How to avoid:** Use string format from API, format for display only
**Warning signs:** Invalid date errors, timezone shifts

### Pitfall 4: Element Plus Locale Issues
**What goes wrong:** Date picker or form elements show English
**Why it happens:** Missing locale configuration
**How to avoid:** Already configured in main.ts with zhCn - use Element Plus components directly
**Warning signs:** Mixed language UI

## Code Examples

### Wellness Metric Types (to add to types/api.ts)
```typescript
// Wellness metric request
export interface WellnessMetricRequest {
  metricKey: string  // sleepDuration|sleepQuality|steps|exerciseMinutes|waterIntake|mood|energy
  value: number
  recordDate: string  // YYYY-MM-DD format
  unit?: string
  notes?: string
}

// Wellness summary for dashboard
export interface WellnessSummaryVO {
  summaryDate: string
  avgSleepDuration: number | null
  avgSleepQuality: number | null
  totalSteps: number | null
  totalExerciseMinutes: number | null
  totalWaterIntake: number | null
  avgMood: number | null
  avgEnergy: number | null
  metrics: WellnessMetricSummary[]
}

export interface WellnessMetricSummary {
  metricKey: string
  displayName: string
  latestValue: number | null
  unit: string
  avgValue: number | null
  trend: number | null  // percentage change
}
```

### Wellness Metric Display Names (to add to utils/format.ts)
```typescript
// Wellness metric display configuration
export const WELLNESS_METRICS: Record<string, { label: string; unit: string; icon: string; color: string }> = {
  sleepDuration: { label: '睡眠时长', unit: '小时', icon: 'Moon', color: '#9b59b6' },
  sleepQuality: { label: '睡眠质量', unit: '分', icon: 'Star', color: '#8e44ad' },
  steps: { label: '步数', unit: '步', icon: 'Aim', color: '#3498db' },
  exerciseMinutes: { label: '运动时长', unit: '分钟', icon: 'Timer', color: '#2ecc71' },
  waterIntake: { label: '饮水量', unit: '毫升', icon: 'Coffee', color: '#00bcd4' },
  mood: { label: '心情', unit: '分', icon: 'Sunny', color: '#f1c40f' },
  energy: { label: '精力', unit: '分', icon: 'Lightning', color: '#e74c3c' }
}

export const getWellnessMetricLabel = (key: string): string => {
  return WELLNESS_METRICS[key]?.label || key
}

export const getWellnessMetricUnit = (key: string): string => {
  return WELLNESS_METRICS[key]?.unit || ''
}
```

### Dashboard View Structure (reference pattern)
```vue
<!-- Based on views/dashboard/Index.vue and views/realtime/Monitor.vue patterns -->
<template>
  <div class="wellness-dashboard">
    <!-- Summary Cards Row -->
    <el-row :gutter="20" class="summary-row">
      <el-col v-for="metric in summaryMetrics" :key="metric.metricKey" :xs="12" :sm="8" :md="4">
        <el-card class="metric-card">
          <div class="metric-icon" :style="{ background: metric.color + '20', color: metric.color }">
            <el-icon><component :is="metric.icon" /></el-icon>
          </div>
          <div class="metric-value">{{ metric.latestValue ?? '--' }}</div>
          <div class="metric-label">{{ metric.displayName }}</div>
          <div class="metric-unit">{{ metric.unit }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Trend Charts -->
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>睡眠趋势</span>
              <el-select v-model="sleepMetric" size="small">
                <el-option label="睡眠时长" value="sleepDuration" />
                <el-option label="睡眠质量" value="sleepQuality" />
              </el-select>
            </div>
          </template>
          <div ref="sleepChartRef" class="chart-container" v-loading="chartLoading"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>活动趋势</span>
              <el-select v-model="activityMetric" size="small">
                <el-option label="步数" value="steps" />
                <el-option label="运动时长" value="exerciseMinutes" />
              </el-select>
            </div>
          </template>
          <div ref="activityChartRef" class="chart-container" v-loading="chartLoading"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Options API | Composition API with `<script setup>` | Vue 3 standard | Better TypeScript inference, cleaner code |
| Vuex | Pinia | Vue 3 ecosystem | Simpler API, better DevTools |
| Moment.js | dayjs | Industry trend | Smaller bundle, same API surface |

**Deprecated/outdated:**
- Options API: Use `<script setup lang="ts">` for all new components
- Inline styles: Use scoped CSS or Element Plus theme variables

## Open Questions

1. **Route Placement**
   - What we know: Routes are defined in `src/router/index.ts`
   - What's unclear: Should wellness be a sub-menu under "预防保健" or its own top-level menu?
   - Recommendation: Add as sub-menu under "预防保健" (prevention) to match the backend category separation

2. **Mobile Responsiveness**
   - What we know: Element Plus has responsive grid system
   - What's unclear: Specific breakpoints for chart visibility on mobile
   - Recommendation: Use `:xs="24"` for single column on mobile, charts should resize automatically

## Validation Architecture

> Skipped - workflow.nyquist_validation not configured

## Sources

### Primary (HIGH confidence)
- `hhs-frontend-v2/package.json` - Dependency versions
- `hhs-frontend-v2/PROJECT_DESIGN.md` - Architecture decisions
- `hhs-backend/src/main/java/com/hhs/controller/WellnessController.java` - API endpoints
- `hhs-backend/src/main/java/com/hhs/vo/WellnessSummaryVO.java` - Response types
- `hhs-frontend-v2/src/views/realtime/Monitor.vue` - ECharts pattern reference
- `hhs-frontend-v2/src/views/prevention/Metrics.vue` - Metric list pattern reference
- `hhs-frontend-v2/src/stores/auth.ts` - Pinia store pattern reference

### Secondary (MEDIUM confidence)
- `hhs-frontend-v2/src/api/health.ts` - API service pattern
- `hhs-frontend-v2/src/utils/format.ts` - Formatting utilities
- `hhs-frontend-v2/src/router/index.ts` - Route configuration

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Direct inspection of package.json and existing code
- Architecture: HIGH - Well-documented patterns in PROJECT_DESIGN.md and consistent code style
- Pitfalls: MEDIUM - Based on common Vue 3 patterns and existing code review

**Research date:** 2026-03-03
**Valid until:** 30 days (stable Vue 3 ecosystem)

---

## Implementation Checklist

### Files to Create
1. `src/api/wellness.ts` - API service for wellness endpoints
2. `src/stores/wellness.ts` - Pinia store for wellness state
3. `src/views/wellness/Dashboard.vue` - Main dashboard view
4. `src/components/charts/TrendChart.vue` - Reusable chart component (optional)

### Files to Extend
1. `src/types/api.ts` - Add wellness types
2. `src/utils/format.ts` - Add wellness metric display helpers
3. `src/router/index.ts` - Add wellness route
4. `src/components/layout/MainLayout.vue` - Add menu item (optional)

### API Endpoints to Consume
| Endpoint | Method | Purpose | UI Component |
|----------|--------|---------|--------------|
| `/api/wellness` | GET | List metrics | Metric history table |
| `/api/wellness` | POST | Create metric | Add metric dialog |
| `/api/wellness/trend/{metricKey}` | GET | Trend data | ECharts line chart |
| `/api/wellness/summary` | GET | Dashboard summary | Summary cards |
| `/api/wellness/latest` | GET | Latest values | Quick stats cards |

### Wellness Metrics Configuration
| Key | Display Name | Unit | Icon | Color |
|-----|--------------|------|------|-------|
| sleepDuration | 睡眠时长 | 小时 | Moon | #9b59b6 |
| sleepQuality | 睡眠质量 | 分 | Star | #8e44ad |
| steps | 步数 | 步 | Aim | #3498db |
| exerciseMinutes | 运动时长 | 分钟 | Timer | #2ecc71 |
| waterIntake | 饮水量 | 毫升 | Coffee | #00bcd4 |
| mood | 心情 | 分 | Sunny | #f1c40f |
| energy | 精力 | 分 | Lightning | #e74c3c |