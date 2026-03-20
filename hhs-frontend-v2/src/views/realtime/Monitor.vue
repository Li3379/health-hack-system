<template>
  <div class="realtime-monitor-page">
    <!-- 连接状态 -->
    <el-card class="status-card">
      <div class="connection-status">
        <div class="status-indicator">
          <el-icon :size="24" :color="realtimeStore.connected ? '#67c23a' : '#f56c6c'">
            <Connection />
          </el-icon>
          <span :class="{ connected: realtimeStore.connected }">
            {{ realtimeStore.connected ? '已连接' : '未连接' }}
          </span>
        </div>
        <el-button v-if="!realtimeStore.connected" type="primary" @click="connect">连接</el-button>
        <el-button v-else type="danger" @click="disconnect">断开</el-button>
      </div>
    </el-card>

    <!-- 最新指标 -->
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
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { useAuthStore } from '@/stores/auth'
import { useRealtimeStore } from '@/stores/realtime'
import { realtimeApi } from '@/api/realtime'
import { formatRelativeTime } from '@/utils/format'
import type { RealtimeMetricVO } from '@/types/api'

const authStore = useAuthStore()
const realtimeStore = useRealtimeStore()
const chartLoading = ref(false)
const selectedMetric = ref('heartRate')
const latestMetrics = ref<RealtimeMetricVO[]>([])
const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

const form = reactive({
  metricKey: '',
  value: 0,
  unit: ''
})

const connect = () => {
  if (authStore.token) {
    realtimeStore.connect(authStore.token)
    ElMessage.success('正在连接...')
  }
}

const disconnect = () => {
  realtimeStore.disconnect()
  ElMessage.info('已断开连接')
}

const fetchLatestMetrics = async () => {
  try {
    const res = await realtimeApi.getLatestMetrics()
    latestMetrics.value = res.data
  } catch (error) {
    console.error('Failed to fetch latest metrics:', error)
  }
}

const fetchTrend = async () => {
  if (!selectedMetric.value) return

  chartLoading.value = true
  try {
    const res = await realtimeApi.getMetricTrend(selectedMetric.value, 24)
    renderChart(res.data)
  } catch (error) {
    ElMessage.error('获取趋势数据失败')
  } finally {
    chartLoading.value = false
  }
}

const renderChart = (data: any) => {
  if (!chartRef.value) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  const times = data.dataPoints.map((p: any) => new Date(p.timestamp).toLocaleTimeString())
  const values = data.dataPoints.map((p: any) => p.value)

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: '{b}<br/>{a}: {c} ' + data.unit
    },
    xAxis: {
      type: 'category',
      data: times,
      boundaryGap: false
    },
    yAxis: {
      type: 'value',
      name: data.unit
    },
    series: [
      {
        name: data.metricDisplayName,
        data: values,
        type: 'line',
        smooth: true,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(103, 194, 58, 0.3)' },
            { offset: 1, color: 'rgba(103, 194, 58, 0.05)' }
          ])
        },
        lineStyle: {
          color: '#67c23a',
          width: 2
        },
        itemStyle: {
          color: '#67c23a'
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

onMounted(async () => {
  if (authStore.token && !realtimeStore.connected) {
    realtimeStore.connect(authStore.token)
  }

  await fetchLatestMetrics()
  await nextTick()
  await fetchTrend()

  window.addEventListener('resize', () => {
    chartInstance?.resize()
  })

  // 定时刷新
  const interval = setInterval(() => {
    if (realtimeStore.connected) {
      fetchLatestMetrics()
    }
  }, 10000)

  onUnmounted(() => {
    clearInterval(interval)
  })
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
  color: #67c23a;
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
  color: #606266;
}

.metric-value {
  font-size: 36px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.metric-unit {
  font-size: 16px;
  color: #909399;
  margin-left: 4px;
}

.metric-time {
  font-size: 12px;
  color: #909399;
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
</style>
