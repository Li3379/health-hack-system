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

    <!-- 摘要卡片 -->
    <el-skeleton :loading="wellnessStore.loading" animated>
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
          <div
            ref="sleepChartRef"
            v-loading="wellnessStore.chartLoading"
            class="chart-container"
          ></div>
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
          <div
            ref="activityChartRef"
            v-loading="wellnessStore.chartLoading"
            class="chart-container"
          ></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 添加指标对话框 -->
    <el-dialog v-model="showAddDialog" title="添加保健指标" width="500px">
      <el-form ref="addFormRef" :model="addForm" :rules="addFormRules" label-width="100px">
        <el-form-item label="指标类型" prop="metricKey">
          <el-select v-model="addForm.metricKey" placeholder="请选择指标类型" style="width: 100%">
            <el-option
              v-for="(config, key) in WELLNESS_METRICS"
              :key="key"
              :label="config.label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数值" prop="value">
          <el-input-number v-model="addForm.value" :precision="1" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="记录日期" prop="recordDate">
          <el-date-picker
            v-model="addForm.recordDate"
            type="date"
            placeholder="选择日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.notes" type="textarea" :rows="2" placeholder="可选备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="handleAddMetric">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick, onUnmounted } from 'vue'
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
import { WELLNESS_METRICS, getWellnessMetricColor, getWellnessMetricIcon } from '@/utils/format'
import { useECharts } from '@/composables/useECharts'
import ActivityRings from '@/components/ActivityRings.vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { EChartsCoreOption } from 'echarts/core'
import dayjs from 'dayjs'

const wellnessIconMap: Record<string, any> = {
  Moon, Star, Aim, Timer, Coffee, Sunny, Lightning, DataLine
}

const wellnessStore = useWellnessStore()

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
const adding = ref(false)
const addFormRef = ref<FormInstance>()
const addForm = reactive({
  metricKey: '',
  value: 0,
  recordDate: dayjs().format('YYYY-MM-DD'),
  notes: ''
})

const addFormRules: FormRules = {
  metricKey: [{ required: true, message: '请选择指标类型', trigger: 'change' }],
  value: [{ required: true, message: '请输入数值', trigger: 'blur' }],
  recordDate: [{ required: true, message: '请选择日期', trigger: 'change' }]
}

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
  await wellnessStore.fetchTrend(sleepMetric.value, startDate, endDate)
  sleepTrendData = wellnessStore.trendData
  sleepColor = getWellnessMetricColor(sleepMetric.value)
  updateSleepChart()
}

const fetchActivityTrend = async () => {
  const { startDate, endDate } = getDateRange(dateRange.value)
  await wellnessStore.fetchTrend(activityMetric.value, startDate, endDate)
  activityTrendData = wellnessStore.trendData
  activityColor = getWellnessMetricColor(activityMetric.value)
  updateActivityChart()
}

const handleAddMetric = async () => {
  if (!addFormRef.value) return

  await addFormRef.value.validate(async valid => {
    if (!valid) return

    adding.value = true
    try {
      const success = await wellnessStore.addMetric({
        metricKey: addForm.metricKey,
        value: addForm.value,
        recordDate: addForm.recordDate,
        notes: addForm.notes
      })
      if (success) {
        showAddDialog.value = false
        // Reset form
        addForm.metricKey = ''
        addForm.value = 0
        addForm.recordDate = dayjs().format('YYYY-MM-DD')
        addForm.notes = ''
      }
    } finally {
      adding.value = false
    }
  })
}

onMounted(async () => {
  await wellnessStore.fetchSummary(dateRange.value)
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
</style>
