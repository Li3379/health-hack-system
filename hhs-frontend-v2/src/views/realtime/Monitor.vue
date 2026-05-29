<template>
  <div class="realtime-monitor-page">
    <!-- 连接状态 -->
    <el-card class="status-card">
      <div class="connection-status">
        <div class="status-indicator">
          <el-icon :size="24" :color="statusColor">
            <Connection />
          </el-icon>
          <span :class="{ connected: realtimeStore.connected, connecting: realtimeStore.connecting }">
            {{ statusText }}
          </span>
        </div>
        <div class="button-group">
          <el-button 
            v-if="!realtimeStore.connected && !realtimeStore.connecting" 
            type="primary" 
            @click="connect"
          >
            连接
          </el-button>
          <el-button 
            v-if="realtimeStore.connecting" 
            type="info" 
            loading
            disabled
          >
            连接中...
          </el-button>
          <el-button 
            v-if="realtimeStore.connected" 
            type="danger" 
            @click="disconnect"
          >
            断开
          </el-button>
        </div>
      </div>
      <div v-if="realtimeStore.errorMessage" class="error-message">
        <el-alert :title="realtimeStore.errorMessage" type="error" :closable="false" show-icon />
      </div>
    </el-card>

    <!-- 最新指标 -->
    <el-skeleton :loading="metricsLoading" animated>
      <template #template>
        <el-row :gutter="20" class="metrics-row">
          <el-col v-for="n in 4" :key="n" :xs="24" :sm="12" :md="8" :lg="6">
            <el-card class="metric-card">
              <div style="text-align: center;">
                <el-skeleton-item variant="text" style="width: 80px; height: 14px; margin: 0 auto 12px;" />
                <el-skeleton-item variant="text" style="width: 100px; height: 36px; margin: 0 auto 8px;" />
                <el-skeleton-item variant="text" style="width: 60px; height: 12px; margin: 0 auto;" />
              </div>
            </el-card>
          </el-col>
        </el-row>
      </template>
      <template #default>
    <el-row :gutter="20" class="metrics-row">
      <el-col v-for="metric in latestMetrics" :key="metric.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="metric-card">
          <div class="metric-content">
            <div class="metric-header">
              <span class="metric-name">{{ metric.metricDisplayName }}</span>
              <el-tag size="small" type="info">{{ metric.source || '手动' }}</el-tag>
            </div>
            <div class="metric-value">
              {{ metric.value }}
              <span class="metric-unit">{{ metric.unit }}</span>
            </div>
            <div class="metric-time">
              {{ formatRelativeTime(metric.recordedAt) }}
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
      </template>
    </el-skeleton>

    <!-- 心率仪表盘 -->
    <el-card class="gauge-card">
      <template #header>
        <div class="header-actions">
          <span>实时心率</span>
          <el-tag v-if="heartRateValue !== null" :type="heartRateTagType" size="small">
            {{ heartRateStatusText }}
          </el-tag>
        </div>
      </template>
      <div class="gauge-content">
        <GaugeChart :value="heartRateValue" title="心率 (bpm)" />
      </div>
    </el-card>


    <!-- 实时图表 -->
    <el-card class="chart-card">
      <template #header>
        <div class="header-actions">
          <span>实时趋势</span>
          <el-select
            v-model="selectedMetric"
            placeholder="选择指标"
            style="width: 200px"
            @change="fetchTrend"
          >
            <el-option-group label="健康指标">
              <el-option label="心率" value="heartRate" />
              <el-option label="收缩压" value="systolicBP" />
              <el-option label="舒张压" value="diastolicBP" />
              <el-option label="血糖" value="glucose" />
              <el-option label="体重" value="weight" />
              <el-option label="体温" value="temperature" />
            </el-option-group>
            <el-option-group label="保健指标">
              <el-option label="睡眠时长" value="sleepDuration" />
              <el-option label="睡眠质量" value="sleepQuality" />
              <el-option label="步数" value="steps" />
              <el-option label="运动时长" value="exerciseMinutes" />
              <el-option label="饮水量" value="waterIntake" />
              <el-option label="心情" value="mood" />
              <el-option label="精力" value="energy" />
            </el-option-group>
          </el-select>
        </div>
      </template>
      <div ref="chartRef" v-loading="chartLoading" class="chart-container"></div>
    </el-card>

    <!-- 手动添加 -->
    <el-card class="add-card">
      <template #header>
        <span>手动添加指标</span>
      </template>
      <el-form :inline="true" :model="form" @submit.prevent="handleAdd">
        <el-form-item label="指标类型">
          <el-select v-model="form.metricKey" placeholder="请选择" style="width: 150px">
            <el-option-group label="健康指标">
              <el-option label="心率" value="heartRate" />
              <el-option label="收缩压" value="systolicBP" />
              <el-option label="舒张压" value="diastolicBP" />
              <el-option label="血糖" value="glucose" />
              <el-option label="体重" value="weight" />
              <el-option label="体温" value="temperature" />
            </el-option-group>
            <el-option-group label="保健指标">
              <el-option label="睡眠时长" value="sleepDuration" />
              <el-option label="睡眠质量" value="sleepQuality" />
              <el-option label="步数" value="steps" />
              <el-option label="运动时长" value="exerciseMinutes" />
              <el-option label="饮水量" value="waterIntake" />
              <el-option label="心情" value="mood" />
              <el-option label="精力" value="energy" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="数值">
          <el-input-number v-model="form.value" :precision="2" :min="0" style="width: 150px" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" placeholder="如: bpm, mmHg" style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleAdd">添加</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { TooltipComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, TooltipComponent, GridComponent, CanvasRenderer])
import { useAuthStore } from '@/stores/auth'
import { useRealtimeStore } from '@/stores/realtime'
import { realtimeApi } from '@/api/realtime'
import { formatRelativeTime } from '@/utils/format'
import { useECharts } from '@/composables/useECharts'
import GaugeChart from '@/components/charts/GaugeChart.vue'
import type { RealtimeMetricVO } from '@/types/api'

const authStore = useAuthStore()
const realtimeStore = useRealtimeStore()
const chartLoading = ref(false)
const metricsLoading = ref(false)
const selectedMetric = ref('heartRate')
const latestMetrics = ref<RealtimeMetricVO[]>([])

const heartRateValue = computed(() => {
  const hr = realtimeStore.latestMetrics.find(m => m.metricKey === 'heartRate')
  return hr?.value ?? null
})

const heartRateTagType = computed(() => {
  const v = heartRateValue.value
  if (v === null) return 'info' as const
  if (v < 60) return 'info' as const
  if (v <= 100) return 'success' as const
  if (v <= 120) return 'warning' as const
  return 'danger' as const
})

const heartRateStatusText = computed(() => {
  const v = heartRateValue.value
  if (v === null) return '无数据'
  if (v < 60) return '偏低'
  if (v <= 100) return '正常'
  if (v <= 120) return '偏高'
  return '过高'
})

const chartRef = ref<HTMLElement>()
let trendData: any = null

const buildChartOption = (): echarts.EChartsCoreOption => {
  if (!trendData) return {}
  const times = trendData.dataPoints.map((p: any) => new Date(p.timestamp).toLocaleTimeString())
  const values = trendData.dataPoints.map((p: any) => p.value)
  return {
    tooltip: {
      trigger: 'axis',
      formatter: '{b}<br/>{a}: {c} ' + trendData.unit
    },
    xAxis: {
      type: 'category',
      data: times,
      boundaryGap: false
    },
    yAxis: {
      type: 'value',
      name: trendData.unit
    },
    series: [
      {
        name: trendData.metricDisplayName,
        data: values,
        type: 'line',
        smooth: true,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(94, 234, 212, 0.3)' },
            { offset: 1, color: 'rgba(94, 234, 212, 0.05)' }
          ])
        },
        lineStyle: { width: 2 },
        itemStyle: { borderWidth: 2 }
      }
    ],
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    }
  }
}

const { init: initChart, updateOption } = useECharts(chartRef, buildChartOption)
let pingInterval: ReturnType<typeof setInterval> | null = null

// 计算状态显示
const statusColor = computed(() => {
  if (realtimeStore.connected) return 'var(--color-health-excellent)'
  if (realtimeStore.connecting) return 'var(--color-health-fair)'
  return 'var(--color-health-poor)'
})

const statusText = computed(() => {
  if (realtimeStore.connected) return '已连接'
  if (realtimeStore.connecting) return '连接中...'
  return '未连接'
})

const form = reactive({
  metricKey: '',
  value: 0,
  unit: ''
})

const connect = () => {
  if (authStore.token) {
    realtimeStore.connect(authStore.token)
    ElMessage.info('正在连接...')
  } else {
    ElMessage.warning('请先登录')
  }
}

const disconnect = () => {
  realtimeStore.disconnect()
  ElMessage.info('已断开连接')
}

const fetchLatestMetrics = async () => {
  metricsLoading.value = true
  try {
    const res = await realtimeApi.getLatestMetrics()
    latestMetrics.value = res.data
  } catch (error) {
    // latest metrics fetch failed
  } finally {
    metricsLoading.value = false
  }
}

const fetchTrend = async () => {
  if (!selectedMetric.value) return

  chartLoading.value = true
  try {
    const res = await realtimeApi.getMetricTrend(selectedMetric.value, 24)
    trendData = res.data
    updateOption()
  } catch (error) {
    ElMessage.error('获取趋势数据失败')
  } finally {
    chartLoading.value = false
  }
}

const handleAdd = async () => {
  if (!form.metricKey || !form.value) {
    ElMessage.warning('请填写完整信息')
    return
  }

  try {
    await realtimeApi.addMetric({
      metricKey: form.metricKey,
      value: form.value,
      unit: form.unit,
      source: 'MANUAL'
    })
    ElMessage.success('添加成功')
    Object.assign(form, { metricKey: '', value: 0, unit: '' })
    fetchLatestMetrics()
  } catch (error) {
    ElMessage.error('添加失败')
  }
}

// 启动心跳
const startPing = () => {
  if (pingInterval) clearInterval(pingInterval)
  pingInterval = setInterval(() => {
    if (realtimeStore.connected) {
      realtimeStore.sendPing()
    }
  }, 30000) // 每30秒发送心跳
}

let refreshInterval: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  // 页面加载时自动连接
  if (authStore.token && !realtimeStore.connected && !realtimeStore.connecting) {
    realtimeStore.connect(authStore.token)
  }

  await fetchLatestMetrics()
  await nextTick()
  await fetchTrend()
  initChart()

  // 启动心跳
  startPing()

  // 定时刷新数据
  refreshInterval = setInterval(() => {
    fetchLatestMetrics()
  }, 10000)
})

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
  if (pingInterval) clearInterval(pingInterval)
})
</script>

<style scoped>
.realtime-monitor-page {
  padding: 20px;
}

.status-card {
  margin-bottom: 20px;
}

.connection-status {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 500;
}

.status-indicator .connected {
  color: var(--color-health-excellent);
}

.status-indicator .connecting {
  color: var(--color-health-fair);
}

.button-group {
  display: flex;
  gap: 8px;
}

.error-message {
  margin-top: 12px;
}

.metrics-row {
  margin-bottom: 20px;
}

.metric-card {
  margin-bottom: 20px;
}

.metric-content {
  text-align: center;
}

.metric-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.metric-name {
  font-size: 14px;
  color: var(--text-2);
}

.metric-value {
  font-size: 36px;
  font-weight: bold;
  color: var(--text-1);
  margin-bottom: 8px;
}

.metric-unit {
  font-size: 16px;
  color: var(--text-3);
  margin-left: 4px;
}

.metric-time {
  font-size: 12px;
  color: var(--text-3);
}

.chart-card,
.add-card {
  margin-bottom: 20px;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-container {
  height: 400px;
  width: 100%;
}

.gauge-card {
  margin-bottom: 20px;
}

.gauge-content {
  display: flex;
  justify-content: center;
}
</style>