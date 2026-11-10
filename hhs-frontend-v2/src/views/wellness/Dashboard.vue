<template>
  <div class="wellness-dashboard">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>保健指标仪表盘</h2>
      <el-button type="primary" @click="showAddDialog = true">
        <el-icon><Plus /></el-icon>
        添加指标
      </el-button>
    </div>

    <!-- 摘要加载失败：ErrorState 独占呈现（D-02：一个失败只呈现一次，不叠加 toast）。
         与骨架屏互斥 —— 加载中显示骨架、加载完失败显示块，两态不同时出现。 -->
    <ErrorState
      v-if="summaryFailed"
      variant="server"
      title="无法加载保健数据"
      description="可能是网络波动或服务暂时不可用，请重试。"
      :retry="loadSummary"
      retry-label="重新加载保健数据"
      :retrying="wellnessStore.loading"
    />

    <!-- 摘要卡片 -->
    <el-skeleton v-else :loading="wellnessStore.loading" animated>
      <template #template>
        <el-row :gutter="20" class="summary-row">
          <el-col v-for="n in 6" :key="n" :xs="12" :sm="8" :md="4" :lg="3">
            <el-card class="metric-card" shadow="hover">
              <div style="text-align: center;">
                <el-skeleton-item variant="circle" style="width: 48px; height: 48px; margin: 0 auto 12px;" />
                <el-skeleton-item variant="text" style="width: 60px; height: 28px; margin: 0 auto 4px;" />
                <el-skeleton-item variant="text" style="width: 80px; height: 14px; margin: 0 auto;" />
              </div>
            </el-card>
          </el-col>
        </el-row>
      </template>
      <template #default>
    <el-row :gutter="20" class="summary-row">
      <el-col
        v-for="metric in summaryMetrics"
        :key="metric.metricKey"
        :xs="12"
        :sm="8"
        :md="4"
        :lg="3"
      >
        <el-card class="metric-card" shadow="hover">
          <div
            class="metric-icon"
            :style="{ background: metric.color + '20', color: metric.color }"
          >
            <el-icon><component :is="metric.icon" /></el-icon>
          </div>
          <div class="metric-value">{{ formatValue(metric.latestValue) }}</div>
          <div class="metric-label">{{ metric.displayName }}</div>
          <div class="metric-unit">{{ metric.unit }}</div>
          <div
            v-if="metric.trend !== null"
            class="metric-trend"
            :class="{ positive: metric.trend > 0, negative: metric.trend < 0 }"
          >
            <el-icon><Top v-if="metric.trend > 0" /><Bottom v-else /></el-icon>
            {{ Math.abs(metric.trend).toFixed(1) }}%
          </div>
        </el-card>
      </el-col>
    </el-row>
      </template>
    </el-skeleton>


    <!-- 活动环 -->
    <el-row :gutter="20" class="activity-row">
      <el-col :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="activity-card" shadow="hover">
          <template #header>
            <span>今日活动概览</span>
          </template>
          <div class="activity-body">
            <ActivityRings
              :exercise-percent="exercisePercent"
              :sleep-percent="sleepPercent"
              :habits-percent="habitsPercent"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图表 -->
    <el-row :gutter="20" class="charts-row">
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>睡眠趋势</span>
              <div class="header-controls">
                <el-select
                  v-model="sleepMetric"
                  size="small"
                  style="width: 120px"
                  @change="fetchSleepTrend"
                >
                  <el-option label="睡眠时长" value="sleepDuration" />
                  <el-option label="睡眠质量" value="sleepQuality" />
                </el-select>
                <el-select
                  v-model="dateRange"
                  size="small"
                  style="width: 100px"
                  @change="fetchSleepTrend"
                >
                  <el-option label="7天" :value="7" />
                  <el-option label="14天" :value="14" />
                  <el-option label="30天" :value="30" />
                </el-select>
              </div>
            </div>
          </template>
          <div class="chart-area">
            <!-- 趋势加载失败：状态块只落在图表区内（不包整页），
                 因此已渲染的摘要卡片保持可见 —— 局部降级，不是整页失效。 -->
            <ErrorState
              v-if="sleepTrendFailed"
              variant="server"
              title="无法加载睡眠趋势"
              description="保健数据仍在下方，只有这张图表暂时不可用。"
              :retry="fetchSleepTrend"
              retry-label="重新加载睡眠趋势"
              :retrying="wellnessStore.chartLoading"
            />
            <!-- v-show 而非 v-if：容器必须始终挂载，否则 ECharts 实例在重试后
                 找不到挂载点（useECharts.init 在 ref 为空时直接返回）。 -->
            <div
              v-show="!sleepTrendFailed"
              ref="sleepChartRef"
              v-loading="wellnessStore.chartLoading"
              class="chart-container"
            ></div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>活动趋势</span>
              <div class="header-controls">
                <el-select
                  v-model="activityMetric"
                  size="small"
                  style="width: 120px"
                  @change="fetchActivityTrend"
                >
                  <el-option label="步数" value="steps" />
                  <el-option label="运动时长" value="exerciseMinutes" />
                </el-select>
                <el-select
                  v-model="dateRange"
                  size="small"
                  style="width: 100px"
                  @change="fetchActivityTrend"
                >
                  <el-option label="7天" :value="7" />
                  <el-option label="14天" :value="14" />
                  <el-option label="30天" :value="30" />
                </el-select>
              </div>
            </div>
          </template>
          <div class="chart-area">
            <!-- 第二张图有自己独立的失败标志：一次趋势请求失败不该在成功的图表上
                 挂出失败说明。 -->
            <ErrorState
              v-if="activityTrendFailed"
              variant="server"
              title="无法加载活动趋势"
              description="保健数据仍在下方，只有这张图表暂时不可用。"
              :retry="fetchActivityTrend"
              retry-label="重新加载活动趋势"
              :retrying="wellnessStore.chartLoading"
            />
            <div
              v-show="!activityTrendFailed"
              ref="activityChartRef"
              v-loading="wellnessStore.chartLoading"
              class="chart-container"
            ></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 添加指标通用弹窗 (REUSE-01, D-01) -->
    <UniversalMetricDialog
      ref="dialogRef"
      v-model="showAddDialog"
      category="WELLNESS"
      title="添加生活方式数据"
      @success="handleDialogSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick, onUnmounted } from 'vue'
import {
  staggerReveal, countUp, cleanupScrollTriggers,
  tilt3D, parallax, scrollScale, magneticHover,
  buttonPress, hoverLift, hoverShine, rippleClick,
  DUR, EASE,
} from '@/composables/useGsap'
import {
  Plus, Moon, Star, Aim, Timer, Coffee, Sunny, Lightning,
  DataLine, Top, Bottom
} from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { TooltipComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, TooltipComponent, GridComponent, CanvasRenderer])
import { useWellnessStore } from '@/stores/wellness'
import { useMetricMetaStore } from '@/stores/metricMeta'
import { getWellnessMetricColor, getWellnessMetricIcon } from '@/utils/format'
import { useECharts } from '@/composables/useECharts'
import ActivityRings from '@/components/ActivityRings.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import UniversalMetricDialog from '@/components/metric/UniversalMetricDialog.vue'
import type { EChartsCoreOption } from 'echarts/core'
import dayjs from 'dayjs'

const wellnessIconMap: Record<string, any> = {
  Moon, Star, Aim, Timer, Coffee, Sunny, Lightning, DataLine
}

const wellnessStore = useWellnessStore()
const metricMetaStore = useMetricMetaStore()

// Chart refs and data
const sleepChartRef = ref<HTMLElement>()
const activityChartRef = ref<HTMLElement>()
let sleepTrendData: any = null
let sleepColor = ''
let activityTrendData: any = null
let activityColor = ''

const buildSleepOption = (): EChartsCoreOption => {
  if (!sleepTrendData) return {}
  return {
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: sleepTrendData.dates || [],
      boundaryGap: false,
      axisLabel: { formatter: (value: string) => dayjs(value).format('MM-DD') }
    },
    yAxis: { type: 'value', name: sleepTrendData.unit || '' },
    series: [{
      data: sleepTrendData.values || [],
      type: 'line',
      smooth: true,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: sleepColor + '4D' },
          { offset: 1, color: sleepColor + '0D' }
        ])
      },
      lineStyle: { width: 2 },
      itemStyle: {}
    }],
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true }
  }
}

const buildActivityOption = (): EChartsCoreOption => {
  if (!activityTrendData) return {}
  return {
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: activityTrendData.dates || [],
      boundaryGap: false,
      axisLabel: { formatter: (value: string) => dayjs(value).format('MM-DD') }
    },
    yAxis: { type: 'value', name: activityTrendData.unit || '' },
    series: [{
      data: activityTrendData.values || [],
      type: 'line',
      smooth: true,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: activityColor + '4D' },
          { offset: 1, color: activityColor + '0D' }
        ])
      },
      lineStyle: { width: 2 },
      itemStyle: {}
    }],
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true }
  }
}

const { init: initSleepChart, updateOption: updateSleepChart } = useECharts(sleepChartRef, buildSleepOption)
const { init: initActivityChart, updateOption: updateActivityChart } = useECharts(activityChartRef, buildActivityOption)

// Selection state
const sleepMetric = ref('sleepDuration')
const activityMetric = ref('steps')
const dateRange = ref(7)

// Add dialog state
const showAddDialog = ref(false)
const dialogRef = ref<any>()

/**
 * 三个加载失败标志（D-02 的分工：状态块承担呈现，因此本文件不走 toast 出口）。
 *
 * <p>摘要与两条趋势各一个，而不是「摘要 + 趋势」两个：
 * 两个 `fetchTrend` 调用是**独立**的，一个失败另一个可能成功。共用单一趋势标志
 * 会让成功的图表上挂出「无法加载活动趋势」——那是把一次成功报告成失败，
 * 比沉默更糟。分开后每个图表区的状态块只描述自己那一次请求的结果。
 */
const summaryFailed = ref(false)
const sleepTrendFailed = ref(false)
const activityTrendFailed = ref(false)

/** 摘要加载：失败只置标志，绝不 rethrow（未处理的拒绝会在挂载期炸掉页面）。 */
const loadSummary = async () => {
  summaryFailed.value = false
  try {
    await wellnessStore.fetchSummary(dateRange.value)
  } catch {
    summaryFailed.value = true
  }
}

const handleDialogSuccess = async () => {
  await loadSummary()
  await wellnessStore.fetchLatest()
  await fetchSleepTrend()
  await fetchActivityTrend()
}

watch(showAddDialog, val => {
  if (val && dialogRef.value) {
    dialogRef.value.initForm()
  }
})

// Computed summary metrics with display config
const summaryMetrics = computed(() => {
  if (!wellnessStore.summary?.metrics) return []
  return wellnessStore.summary.metrics.map(m => ({
    ...m,
    color: getWellnessMetricColor(m.metricKey),
    icon: wellnessIconMap[getWellnessMetricIcon(m.metricKey)] || DataLine
  }))
})

const formatValue = (value: number | null): string => {
  if (value === null) return '--'
  return value.toFixed(1)
}


// Activity ring percentages (derived from wellness summary)
const exercisePercent = computed(() => {
  const steps = wellnessStore.summary?.totalSteps ?? 0
  return Math.min(Math.round((steps / 10000) * 100), 100)
})

const sleepPercent = computed(() => {
  const hours = wellnessStore.summary?.avgSleepDuration ?? 0
  return Math.min(Math.round((hours / 8) * 100), 100)
})

const habitsPercent = computed(() => {
  if (!wellnessStore.summary?.metrics?.length) return 0
  const active = wellnessStore.summary.metrics.filter(m => m.latestValue !== null).length
  return Math.round((active / wellnessStore.summary.metrics.length) * 100)
})


const getDateRange = (days: number) => {
  const end = dayjs()
  const start = end.subtract(days - 1, 'day')
  return {
    startDate: start.format('YYYY-MM-DD'),
    endDate: end.format('YYYY-MM-DD')
  }
}

const fetchSleepTrend = async () => {
  const { startDate, endDate } = getDateRange(dateRange.value)
  sleepTrendFailed.value = false
  try {
    await wellnessStore.fetchTrend(sleepMetric.value, startDate, endDate)
    sleepTrendData = wellnessStore.trendData
    sleepColor = getWellnessMetricColor(sleepMetric.value)
    updateSleepChart()
  } catch {
    // 状态块在图表容器内渲染；已渲染的摘要不受影响（局部降级）。
    sleepTrendFailed.value = true
  }
}

const fetchActivityTrend = async () => {
  const { startDate, endDate } = getDateRange(dateRange.value)
  activityTrendFailed.value = false
  try {
    await wellnessStore.fetchTrend(activityMetric.value, startDate, endDate)
    activityTrendData = wellnessStore.trendData
    activityColor = getWellnessMetricColor(activityMetric.value)
    updateActivityChart()
  } catch {
    activityTrendFailed.value = true
  }
}

onMounted(async () => {
  metricMetaStore.fetchMetadata()
  // loadSummary 与 fetchSleep/ActivityTrend 内部各自 catch：摘要失败不得跳过
  // 图表初始化（否则一个失败的摘要会连带让两张图都不渲染）。
  await loadSummary()
  await wellnessStore.fetchLatest()

  await nextTick()
  await fetchSleepTrend()
  initSleepChart()
  await fetchActivityTrend()
  initActivityChart()

  // ── Metric cards: dramatic stagger with 3D ──
  staggerReveal('.metric-card', {
    y: 50,
    scale: 0.85,
    rotation: -5,
    stagger: 0.1,
    duration: DUR.slow,
    ease: EASE.back,
  })
  tilt3D('.metric-card', { maxTilt: 10, scale: 1.03 })

  // ── Activity card: scroll scale ──
  scrollScale('.activity-card', { from: 0.8, to: 1 })
  parallax('.activity-card', { speed: 0.12 })

  // ── Charts: stagger with parallax ──
  staggerReveal('.charts-row .el-card', {
    y: 40,
    scale: 0.9,
    scrollTrigger: true,
    stagger: 0.12,
    duration: DUR.slow,
    ease: EASE.expo,
  })
  parallax('.charts-row .el-card', { speed: 0.08 })

  // ── Count up values ──
  setTimeout(() => {
    document.querySelectorAll('.metric-value').forEach((el) => {
      const text = el.textContent?.trim() ?? ''
      const num = parseFloat(text)
      if (!isNaN(num) && num > 0) countUp(el as HTMLElement, num, { decimals: 1, duration: DUR.dramatic })
    })
  }, 600)

  // ── Magnetic hover on metric cards ──
  magneticHover('.metric-card', { strength: 0.25, radius: 100 })

  // ── Interaction: hover lift on metric cards ──
  hoverLift('.metric-card', { y: -6, scale: 1.02 })

  // ── Interaction: shine sweep on chart cards ──
  hoverShine('.charts-row .el-card')

  // ── Interaction: press + ripple on add button ──
  buttonPress('.page-header .el-button')
  rippleClick('.page-header .el-button', { color: 'rgba(255,255,255,0.3)' })
})

onUnmounted(() => {
  cleanupScrollTriggers()
})

// 转发暴露属性与方法以实现 100% 单元测试向后兼容
defineExpose({
  dialogRef,
  metricMetaStore,
  showAddDialog,
  get addForm() {
    return dialogRef.value?.form
  },
  get addPrecision() {
    return dialogRef.value?.precision
  },
  get addStep() {
    return dialogRef.value?.step
  },
  get addPlaceholder() {
    return dialogRef.value?.placeholder
  },
  get addPrimaryEval() {
    return dialogRef.value?.primaryEval
  },
  get hasAddCritical() {
    return dialogRef.value?.hasCritical
  },
  get isAddSaveBlocked() {
    return dialogRef.value?.isSaveBlocked
  },
  get addCriticalDialogVisible() {
    return dialogRef.value?.criticalDialogVisible
  },
  set addCriticalDialogVisible(val: boolean) {
    if (dialogRef.value) dialogRef.value.criticalDialogVisible = val
  },
  handleAddMetricKeyChange: (key: string) => dialogRef.value?.handleMetricKeyChange?.(key),
  handleAddMetric: () => dialogRef.value?.handleSubmit?.(),
  executeAddSave: () => dialogRef.value?.executeSave?.()
})
</script>

<style scoped>
.wellness-dashboard {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-1);
}

.summary-row {
  margin-bottom: 20px;
}

.metric-card {
  text-align: center;
  padding: 10px;
  margin-bottom: 20px;
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  margin-bottom: 12px;
}

.metric-icon .el-icon {
  font-size: 24px;
}

.metric-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--text-1);
  margin-bottom: 4px;
}

.metric-label {
  font-size: 14px;
  color: var(--text-2);
  margin-bottom: 2px;
}

.metric-unit {
  font-size: 12px;
  color: var(--text-3);
}

.metric-trend {
  font-size: 12px;
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.metric-trend.positive {
  color: var(--color-health-excellent);
}

.metric-trend.negative {
  color: var(--color-health-poor);
}

.activity-row {
  margin-bottom: 20px;
}

.activity-card {
  margin-bottom: 20px;
}

.activity-body {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 10px 0;
}

.charts-row {
  margin-bottom: 20px;
}

.charts-row .el-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-controls {
  display: flex;
  gap: 10px;
}

.chart-container {
  height: 300px;
  width: 100%;
}

/* 图表区外壳：状态块与画布互斥显示，但容器本身必须始终存在，
   否则重试后 ECharts 找不到挂载点。 */
.chart-area {
  min-height: 300px;
}

.tier-pill-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.eval-message {
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
  line-height: 1.3;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.mood-input {
  width: 100%;
  padding: 8px 0;
}
</style>
