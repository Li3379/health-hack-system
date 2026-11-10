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
      <!--
        `:inline="true"` 的行内布局无法在字段行之上容纳一个全宽汇总块 —— 把它塞成
        inline 表单的第一个子节点会让 EP 的 inline flex 把它压成一行里的一个单元格。
        因此汇总块放在 el-form **紧前方**、同一个 el-card 内（Contract 4.3 的位置规则是
        「位于所有 el-form-item 之上」；这是该规则意图的等价满足，已在台账 理由 列记录）。
      -->
      <FormErrorSummary
        ref="summaryRef"
        :summary-items="metricErrors.summaryItems"
        @focus-field="metricErrors.focusField"
      />
      <el-form ref="formRef" :inline="true" :model="form" :rules="rules" @submit.prevent="handleAdd">
        <el-form-item
          label="指标类型"
          prop="metricKey"
          data-field="metricKey"
          :error="metricErrors.fieldErrors['metricKey']"
        >
          <el-select
            v-model="form.metricKey"
            placeholder="请选择"
            style="width: 150px"
            @change="metricErrors.clearField('metricKey')"
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
        </el-form-item>
        <el-form-item
          label="数值"
          prop="value"
          data-field="value"
          :error="metricErrors.fieldErrors['value']"
        >
          <el-input-number
            v-model="form.value"
            :precision="2"
            :min="0"
            style="width: 150px"
            @update:model-value="metricErrors.clearField('value')"
          />
        </el-form-item>
        <el-form-item
          label="单位"
          prop="unit"
          data-field="unit"
          :error="metricErrors.fieldErrors['unit']"
        >
          <el-input
            v-model="form.unit"
            placeholder="如: bpm, mmHg"
            style="width: 120px"
            @update:model-value="metricErrors.clearField('unit')"
          />
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
import {
  staggerReveal, cleanupScrollTriggers,
  tilt3D, glowPulse, buttonPress, hoverLift, rippleClick, DUR, EASE,
} from '@/composables/useGsap'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
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
import { useFormServerErrors, collectInvalidFieldMessages } from '@/composables/useFormServerErrors'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import { showError } from '@/utils/showError'
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

const formRef = ref<FormInstance>()
const summaryRef = ref<InstanceType<typeof FormErrorSummary> | null>(null)

/**
 * 一个投影实例，字段顺序与行内表单的视觉顺序一致（GOV.UK：汇总顺序必须匹配页面）。
 *
 * 三个 prop 名（`metricKey` / `value` / `unit`）全部命中全局 `FIELD_LABELS`，
 * 因此不需要 `labels` 覆盖 —— 汇总条目读出「指标类型」「指标数值」「单位」。
 * `reactive()` 让模板里 `metricErrors.fieldErrors['value']` 读到字符串而非 Ref。
 */
const metricErrors = reactive(useFormServerErrors({
  fieldOrder: ['metricKey', 'value', 'unit']
}))

/**
 * 客户端规则镜像后端 `RealtimeMetricRequest`：`metricKey` @NotBlank、
 * `value` @NotNull + @DecimalMin("0.0")。空提交因此产出汇总 + 三项内联，
 * 取代原先那句「请填写完整信息」的含糊警告提示。
 */
const rules: FormRules = {
  metricKey: [{ required: true, message: '请选择指标类型', trigger: 'change' }],
  value: [
    { required: true, message: '请输入指标数值', trigger: 'change' },
    { type: 'number', min: 0, message: '指标数值不能为负数', trigger: 'change' }
  ]
}

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

/**
 * 趋势加载失败：本页不渲染任何错误状态块，`forbidden` / `notFound` 没有落点，
 * 因此显式 opt-in 一条常驻 toast —— 静默是唯一不可接受的结果（Contract 1.3）。
 */
const fetchTrend = async () => {
  if (!selectedMetric.value) return

  chartLoading.value = true
  try {
    const res = await realtimeApi.getMetricTrend(selectedMetric.value, 24)
    trendData = res.data
    updateOption()
  } catch (error) {
    showError(error, 'realtime.loadFailed', { presentStates: true })
  } finally {
    chartLoading.value = false
  }
}

const handleAdd = async () => {
  if (!formRef.value) return

  // 上一轮的残留投影不进入本次尝试；只清投影，绝不碰用户输入（ERR-05）。
  metricErrors.reset()

  await formRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      // 客户端校验失败也走同一份汇总通道（GOV.UK 双呈现），零 toast。
      // 这一支取代了原来那句「请填写完整信息」的警告 —— 保留两者会让
      // 一次空提交同时出现一条含糊警告和一份汇总，是同一次失败的两次呈现。
      metricErrors.setErrors(collectInvalidFieldMessages(invalidFields))
      await nextTick()
      summaryRef.value?.focusSummary()
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
      // 这一段重置**必须**留在这里、留在 try 内、留在成功之后：它是本文件唯一一处
      // 「一次失败可能摧毁用户正在输入的内容」的地方（ERR-05 / T-14-25）。
      // 把它移进 catch、或在失败时调用，都会让一次常规失败变成静默数据丢失。
      Object.assign(form, { metricKey: '', value: 0, unit: '' })
      fetchLatestMetrics()
    } catch (error) {
      // validation → 0 toast，汇总 + 内联承担呈现；其余 kind 由 showError 呈现。
      // 本页无状态块，缺 `presentStates` 会把一次权限或资源消失变成静默。
      const err = showError(error, 'realtime.saveFailed', { presentStates: true })
      if (err.kind === 'validation') {
        metricErrors.apply(err)
        await nextTick()
        summaryRef.value?.focusSummary()
      }
    }
  })
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

  // ── Metric cards: dramatic entrance with 3D ──
  staggerReveal('.metric-card', {
    y: 50,
    scale: 0.85,
    rotation: -3,
    stagger: 0.1,
    duration: DUR.slow,
    ease: EASE.back,
  })
  tilt3D('.metric-card', { maxTilt: 12, scale: 1.03 })

  // ── Other cards: scroll-driven ──
  staggerReveal('.el-card:not(.metric-card)', {
    y: 30,
    scale: 0.95,
    scrollTrigger: true,
    stagger: 0.1,
    duration: DUR.mid,
    ease: EASE.expo,
  })

  // ── Glow pulse on heart rate card ──
  const hrCard = document.querySelector('.metric-card') as HTMLElement
  if (hrCard) glowPulse(hrCard, { color: 'rgba(94, 234, 212, 0.25)', spread: 15 })

  // ── Interaction: hover lift on metric cards ──
  hoverLift('.metric-card', { y: -6, scale: 1.02 })

  // ── Interaction: press + ripple on buttons ──
  buttonPress('.el-button--primary')
  buttonPress('.el-button--danger')
  rippleClick('.metric-card', { color: 'rgba(94, 234, 212, 0.15)' })

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
  cleanupScrollTriggers()
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