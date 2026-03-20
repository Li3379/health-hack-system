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
    <el-row v-loading="wellnessStore.loading" :gutter="20" class="summary-row">
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
            <el-icon><component :is="metric.trend > 0 ? 'Top' : 'Bottom'" /></el-icon>
            {{ Math.abs(metric.trend).toFixed(1) }}%
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
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useWellnessStore } from '@/stores/wellness'
import { WELLNESS_METRICS, getWellnessMetricColor, getWellnessMetricIcon } from '@/utils/format'
import type { FormInstance, FormRules } from 'element-plus'
import dayjs from 'dayjs'

const wellnessStore = useWellnessStore()

// Chart refs
const sleepChartRef = ref<HTMLElement>()
const activityChartRef = ref<HTMLElement>()
const sleepChartInstance: echarts.ECharts | null = null
const activityChartInstance: echarts.ECharts | null = null

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
    icon: getWellnessMetricIcon(m.metricKey)
  }))
})

const formatValue = (value: number | null): string => {
  if (value === null) return '--'
  return value.toFixed(1)
}

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
  renderChart(
    sleepChartInstance,
    sleepChartRef.value,
    wellnessStore.trendData,
    getWellnessMetricColor(sleepMetric.value)
  )
}

const fetchActivityTrend = async () => {
  const { startDate, endDate } = getDateRange(dateRange.value)
  await wellnessStore.fetchTrend(activityMetric.value, startDate, endDate)
  renderChart(
    activityChartInstance,
    activityChartRef.value,
    wellnessStore.trendData,
    getWellnessMetricColor(activityMetric.value)
  )
}

const renderChart = (
  chartInstance: echarts.ECharts | null,
  chartRef: HTMLElement | undefined,
  data: any,
  color: string
) => {
  if (!chartRef || !data) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef)
  }

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: data.dates || [],
      boundaryGap: false,
      axisLabel: {
        formatter: (value: string) => dayjs(value).format('MM-DD')
      }
    },
    yAxis: {
      type: 'value',
      name: data.unit || ''
    },
    series: [
      {
        data: data.values || [],
        type: 'line',
        smooth: true,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: color + '4D' },
            { offset: 1, color: color + '0D' }
          ])
        },
        lineStyle: {
          color: color,
          width: 2
        },
        itemStyle: {
          color: color
        }
      }
    ],
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    }
  }

  chartInstance.setOption(option)
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
  // Fetch initial data
  await wellnessStore.fetchSummary(dateRange.value)
  await wellnessStore.fetchLatest()

  // Wait for DOM and render charts
  await nextTick()
  await fetchSleepTrend()
  await fetchActivityTrend()

  // Handle window resize
  window.addEventListener('resize', () => {
    sleepChartInstance?.resize()
    activityChartInstance?.resize()
  })
})

onUnmounted(() => {
  sleepChartInstance?.dispose()
  activityChartInstance?.dispose()
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
  color: #303133;
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
  color: #303133;
  margin-bottom: 4px;
}

.metric-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 2px;
}

.metric-unit {
  font-size: 12px;
  color: #909399;
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
  color: #67c23a;
}

.metric-trend.negative {
  color: #f56c6c;
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
