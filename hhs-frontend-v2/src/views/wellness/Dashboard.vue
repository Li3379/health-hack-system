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

    <!-- 添加指标对话框 (append-to-body, 560px, 权威元数据与四梯度校验) -->
    <el-dialog
      v-model="showAddDialog"
      title="添加保健指标"
      width="560px"
      destroy-on-close
      append-to-body
      class="wellness-add-dialog"
    >
      <el-form
        ref="addFormRef"
        :model="addForm"
        :rules="addFormRules"
        label-position="top"
        status-icon
      >
        <el-form-item label="指标类型" prop="metricKey">
          <el-select
            v-model="addForm.metricKey"
            placeholder="请选择指标类型"
            style="width: 100%"
            @change="handleAddMetricKeyChange"
          >
            <el-option
              v-for="item in metricMetaStore.wellnessMetrics"
              :key="item.canonicalKey"
              :label="item.displayName"
              :value="item.canonicalKey"
            />
          </el-select>
        </el-form-item>

        <!-- 心情特殊输入 (1-5 星级打分) -->
        <el-form-item v-if="addForm.metricKey === 'mood'" label="心情状态" prop="value">
          <div class="mood-input">
            <el-rate
              v-model="addMoodValue"
              :max="5"
              show-text
              :texts="['很差', '较差', '一般', '较好', '很好']"
            />
          </div>
          <!-- 心情状态胶囊 -->
          <div v-if="addPrimaryEval" class="tier-pill-wrapper">
            <el-tag :type="getTagType(addPrimaryEval.status)" size="small" effect="light">
              <el-icon><component :is="getTagIcon(addPrimaryEval.status)" /></el-icon>
              <span>{{ addPrimaryEval.badgeText }}</span>
            </el-tag>
            <span class="eval-message">{{ addPrimaryEval.message }}</span>
          </div>
        </el-form-item>

        <!-- 其他通用数值型生活方式指标 -->
        <el-form-item v-else :label="addDefinition?.displayName || '测量数值'" prop="value">
          <el-input-number
            v-model="addForm.value"
            :precision="addPrecision"
            :step="addStep"
            :controls="true"
            :placeholder="addPlaceholder"
            style="width: 100%"
          />
          <!-- 四梯度状态胶囊微标签 -->
          <div v-if="addPrimaryEval" class="tier-pill-wrapper">
            <el-tag :type="getTagType(addPrimaryEval.status)" size="small" effect="light">
              <el-icon><component :is="getTagIcon(addPrimaryEval.status)" /></el-icon>
              <span>{{ addPrimaryEval.badgeText }}</span>
            </el-tag>
            <span class="eval-message">{{ addPrimaryEval.message }}</span>
          </div>
        </el-form-item>

        <!-- 单位与记录日期行 -->
        <el-row :gutter="16">
          <el-col :span="10">
            <el-form-item label="测量单位">
              <el-input :value="addDefinition?.unit || addForm.unit || ''" disabled placeholder="自动获取" />
            </el-form-item>
          </el-col>
          <el-col :span="14">
            <el-form-item label="记录日期" prop="recordDate">
              <el-date-picker
                v-model="addForm.recordDate"
                type="date"
                placeholder="选择测量日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注说明">
          <el-input v-model="addForm.notes" type="textarea" :rows="2" placeholder="可选备注信息" />
        </el-form-item>

        <!-- 常态化健康参考标准解读卡片 -->
        <ReferenceGuideCard :definition="addDefinition" />
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showAddDialog = false">取消</el-button>
          <el-button
            type="primary"
            :loading="adding"
            :disabled="isAddSaveBlocked"
            @click="handleAddMetric"
          >
            添加指标数据
          </el-button>
        </div>
      </template>

      <!-- 急危重症二次确认模态窗 -->
      <CriticalConfirmDialog
        v-model="addCriticalDialogVisible"
        :metric-name="addCriticalMetricName"
        :value="addCriticalMetricValue"
        :unit="addCriticalMetricUnit"
        @confirm="executeAddSave"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, toRef, onMounted, nextTick, onUnmounted } from 'vue'
import {
  staggerReveal, countUp, cleanupScrollTriggers,
  tilt3D, parallax, scrollScale, magneticHover,
  buttonPress, hoverLift, hoverShine, rippleClick,
  DUR, EASE,
} from '@/composables/useGsap'
import {
  Plus, Moon, Star, Aim, Timer, Coffee, Sunny, Lightning,
  DataLine, Top, Bottom, Check, Warning, Bell, CircleClose, InfoFilled
} from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { TooltipComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, TooltipComponent, GridComponent, CanvasRenderer])
import { useWellnessStore } from '@/stores/wellness'
import { useMetricMetaStore } from '@/stores/metricMeta'
import { useMetricValidation } from '@/composables/useMetricValidation'
import { getWellnessMetricColor, getWellnessMetricIcon } from '@/utils/format'
import { useECharts } from '@/composables/useECharts'
import ActivityRings from '@/components/ActivityRings.vue'
import ReferenceGuideCard from '@/views/data-input/components/ReferenceGuideCard.vue'
import CriticalConfirmDialog from '@/views/data-input/components/CriticalConfirmDialog.vue'
import type { FormInstance, FormRules } from 'element-plus'
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
const adding = ref(false)
const addFormRef = ref<FormInstance>()
const addForm = reactive({
  metricKey: '',
  value: undefined as number | undefined,
  unit: '',
  recordDate: dayjs().format('YYYY-MM-DD'),
  notes: ''
})

const addMetricKeyRef = toRef(addForm, 'metricKey')
const addValueRef = toRef(addForm, 'value')

// 引入四梯度响应式生理校验与元数据
const {
  definition: addDefinition,
  primaryEval: addPrimaryEval,
  hasCritical: hasAddCritical,
  isSaveBlocked: isAddSaveBlocked
} = useMetricValidation(addMetricKeyRef, addValueRef)

const addPrecision = computed(() => addDefinition.value?.precision ?? 0)
const addStep = computed(() => addDefinition.value?.step ?? 1)
const addPlaceholder = computed(() => {
  if (addDefinition.value?.normalMin !== undefined && addDefinition.value?.normalMax !== undefined) {
    return `参考范围: ${addDefinition.value.normalMin} ~ ${addDefinition.value.normalMax} ${addDefinition.value.unit}`
  }
  return '请输入数值'
})

const addMoodValue = computed({
  get: () => (addForm.value !== undefined ? Math.round(addForm.value) : 3),
  set: (val: number) => {
    addForm.value = val
  }
})

const handleAddMetricKeyChange = (key: string) => {
  const def = metricMetaStore.getDefinition(key)
  addForm.unit = def?.unit || ''
  if (key === 'mood') {
    if (!addForm.value || addForm.value < 1 || addForm.value > 5) {
      addForm.value = 3
    }
  }
  addFormRef.value?.clearValidate('value')
}

// 监听弹窗打开，初始化默认指标与状态
watch(showAddDialog, visible => {
  if (visible) {
    const defaultKey = metricMetaStore.wellnessMetrics[0]?.canonicalKey || 'steps'
    const def = metricMetaStore.getDefinition(defaultKey)
    addForm.metricKey = defaultKey
    addForm.value = undefined
    addForm.unit = def?.unit || ''
    addForm.recordDate = dayjs().format('YYYY-MM-DD')
    addForm.notes = ''
    setTimeout(() => {
      addFormRef.value?.clearValidate()
    }, 0)
  }
})

const getTagType = (status: string) => {
  switch (status) {
    case 'success':
      return 'success'
    case 'warning':
      return 'warning'
    case 'danger':
    case 'hard_danger':
      return 'danger'
    default:
      return 'info'
  }
}

const getTagIcon = (status: string) => {
  switch (status) {
    case 'success':
      return Check
    case 'warning':
      return Warning
    case 'danger':
      return Bell
    case 'hard_danger':
      return CircleClose
    default:
      return InfoFilled
  }
}

const validateAddValue = (_rule: any, value: number, callback: any) => {
  if (addForm.metricKey === 'mood') {
    if (value === undefined || value === null || value < 1 || value > 5) {
      callback(new Error('请选择心情评级'))
      return
    }
    callback()
    return
  }

  if (value === undefined || value === null || isNaN(value)) {
    callback(new Error('请输入数值'))
    return
  }

  // Tier 4 生理不可能极限强行拦截
  if (addPrimaryEval.value?.isHardLimit) {
    callback(new Error(addPrimaryEval.value.message))
    return
  }

  callback()
}

const addFormRules: FormRules = {
  metricKey: [{ required: true, message: '请选择指标类型', trigger: 'change' }],
  value: [{ required: true, validator: validateAddValue, trigger: 'change' }],
  recordDate: [{ required: true, message: '请选择日期', trigger: 'change' }]
}

// 急危重症二次确认模态窗控制
const addCriticalDialogVisible = ref(false)

const addCriticalMetricName = computed(() => {
  return addDefinition.value?.displayName || addForm.metricKey || '指标'
})

const addCriticalMetricValue = computed<number | string>(() => {
  return addForm.value ?? 0
})

const addCriticalMetricUnit = computed(() => {
  return addDefinition.value?.unit || addForm.unit || ''
})

const handleAddMetric = async () => {
  if (!addFormRef.value) return

  await addFormRef.value.validate(async valid => {
    if (!valid) return

    if (hasAddCritical.value) {
      addCriticalDialogVisible.value = true
      return
    }

    await executeAddSave()
  })
}

const executeAddSave = async () => {
  adding.value = true
  try {
    const canonicalKey = addDefinition.value?.canonicalKey || addForm.metricKey
    const success = await wellnessStore.addMetric({
      metricKey: canonicalKey,
      value: addForm.value!,
      unit: addForm.unit || addDefinition.value?.unit,
      recordDate: addForm.recordDate,
      notes: addForm.notes
    })
    if (success) {
      showAddDialog.value = false
    }
  } finally {
    adding.value = false
  }
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

onMounted(async () => {
  metricMetaStore.fetchMetadata()
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
