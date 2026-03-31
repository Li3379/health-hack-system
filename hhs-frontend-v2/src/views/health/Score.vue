<template>
  <div class="score-page">
    <!-- 当前评分 -->
    <el-card class="score-card">
      <div v-loading="loading" class="score-display">
        <!-- 无数据状态 -->
        <div v-if="scoreData?.level === 'NO_DATA'" class="no-data-content">
          <el-empty description="">
            <template #image>
              <div class="no-data-icon">
                <el-icon :size="80"><Monitor /></el-icon>
              </div>
            </template>
            <div class="no-data-message">{{ scoreData.message || '暂无健康数据' }}</div>
            <div class="no-data-tips">添加以下健康数据后可获得评分：</div>
            <div class="required-metrics">
              <el-tag
                v-for="metric in scoreData.factors?.requiredMetrics"
                :key="metric"
                type="info"
                effect="plain"
              >
                {{ metric }}
              </el-tag>
            </div>
            <el-button type="primary" size="large" class="add-data-btn" @click="goToAddMetric">
              <el-icon><Plus /></el-icon>
              立即添加健康数据
            </el-button>
          </el-empty>
        </div>
        <!-- 正常评分显示 -->
        <div v-else-if="scoreData" class="score-content">
          <div class="score-main">
            <div class="score-circle">
              <div class="score-number">{{ scoreData.score }}</div>
              <div class="score-level">{{ getScoreLevelLabel(scoreData.level) }}</div>
            </div>
            <div class="score-info">
              <p class="score-tips">{{ getScoreTips(scoreData.level) }}</p>
              <div class="score-meta">
                <span>计算时间: {{ formatDateTime(scoreData.calculatedAt) }}</span>
                <span v-if="scoreData.isCached" class="cache-badge">
                  <el-tag type="info" size="small">缓存</el-tag>
                </span>
              </div>
              <el-button type="primary" :loading="recalculating" @click="recalculate">
                重新计算
              </el-button>
              <el-button type="success" :loading="reportLoading" @click="generateReport">
                生成健康报告
              </el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无评分数据" />
      </div>
    </el-card>

    <!-- 健康报告对话框 -->
    <el-dialog
      v-model="reportVisible"
      title="AI健康报告"
      width="800px"
      top="5vh"
      @close="onReportDialogClose"
    >
      <div v-if="reportData" class="report-content">
        <!-- 报告头部信息 -->
        <div class="report-header">
          <span class="report-time">
            报告生成时间: {{ formatDateTime(reportData.generatedAt) }}
          </span>
          <el-button type="primary" size="small" :loading="reportLoading" @click="regenerateReport">
            重新生成
          </el-button>
        </div>

        <!-- 基本信息 -->
        <el-descriptions title="基本信息" :column="3" border class="report-section">
          <el-descriptions-item label="性别">
            {{ reportData.userInfo?.gender }}
          </el-descriptions-item>
          <el-descriptions-item label="年龄">{{ reportData.userInfo?.age }}岁</el-descriptions-item>
          <el-descriptions-item label="BMI">
            {{ reportData.userInfo?.bmi?.toFixed(1) }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 综合评分 -->
        <el-card class="report-section" shadow="never">
          <template #header>
            <span>综合健康评分</span>
          </template>
          <div class="overall-score">
            <div class="score-value">{{ reportData.overallScore }}</div>
            <div class="score-level">({{ getScoreLevelLabel(reportData.scoreLevel) }})</div>
          </div>
        </el-card>

        <!-- 维度分析 -->
        <el-card v-if="reportData.dimensions?.length" class="report-section" shadow="never">
          <template #header>
            <span>健康维度分析</span>
          </template>
          <el-row :gutter="16">
            <el-col v-for="dim in reportData.dimensions" :key="dim.name" :span="12">
              <div class="dimension-item">
                <div class="dimension-header">
                  <span class="dimension-name">{{ dim.name }}</span>
                  <el-tag :type="getStatusType(dim.status)">{{ dim.status }}</el-tag>
                </div>
                <el-progress :percentage="dim.score" :color="getScoreColor(dim.score)" />
                <div class="dimension-desc">{{ dim.description }}</div>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <!-- 风险提示 -->
        <el-card v-if="reportData.riskAlerts?.length" class="report-section" shadow="never">
          <template #header>
            <span style="color: #e6a23c">风险提示</span>
          </template>
          <el-alert
            v-for="(alert, idx) in reportData.riskAlerts"
            :key="idx"
            :title="alert.category"
            :description="alert.description"
            :type="alert.level === '高' ? 'error' : 'warning'"
            show-icon
            class="risk-alert"
          >
            <template #default>
              <div>
                <strong>建议：</strong>
                {{ alert.recommendation }}
              </div>
            </template>
          </el-alert>
        </el-card>

        <!-- 改善建议 -->
        <el-card v-if="reportData.suggestions?.length" class="report-section" shadow="never">
          <template #header>
            <span>改善建议</span>
          </template>
          <el-timeline>
            <el-timeline-item
              v-for="(sug, idx) in reportData.suggestions"
              :key="idx"
              :type="sug.priority === '高' ? 'danger' : sug.priority === '中' ? 'warning' : 'info'"
            >
              <div class="suggestion-item">
                <div class="suggestion-category">
                  <el-tag size="small">{{ sug.category }}</el-tag>
                  <el-tag size="small" :type="sug.priority === '高' ? 'danger' : 'info'">
                    {{ sug.priority }}优先级
                  </el-tag>
                </div>
                <div class="suggestion-content">{{ sug.suggestion }}</div>
                <div class="suggestion-action">行动建议：{{ sug.action }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <!-- 总结 -->
        <el-card class="report-section" shadow="never">
          <template #header>
            <span>健康总结</span>
          </template>
          <p class="summary-text">{{ reportData.summary }}</p>
        </el-card>
      </div>
      <div v-else-if="reportLoading" class="report-loading">
        <el-icon class="is-loading" :size="48"><Loading /></el-icon>
        <p>正在生成健康报告，请稍候...</p>
        <el-button @click="cancelGenerate">取消</el-button>
      </div>
    </el-dialog>

    <!-- 评分因子 -->
    <el-card v-if="scoreData && scoreData.level !== 'NO_DATA'" class="factors-card">
      <template #header>
        <span>评分因子详情</span>
      </template>
      <el-row :gutter="20">
        <el-col v-for="(value, key) in scoreData.factors" :key="key" :xs="24" :sm="12" :md="8">
          <div class="factor-item">
            <div class="factor-header">
              <span class="factor-name">{{ getFactorLabel(key) }}</span>
              <span class="factor-value">{{ formatFactorValue(value) }}</span>
            </div>
            <el-progress
              :percentage="getFactorPercentage(value)"
              :color="getFactorColor(value)"
              :stroke-width="8"
            />
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 评分历史 -->
    <el-card v-if="scoreData && scoreData.level !== 'NO_DATA'" class="history-card">
      <template #header>
        <div class="header-actions">
          <span>评分历史趋势</span>
          <el-radio-group v-model="historyRange" @change="fetchHistory">
            <el-radio-button value="7">最近7天</el-radio-button>
            <el-radio-button value="30">最近30天</el-radio-button>
            <el-radio-button value="90">最近90天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="chartRef" v-loading="historyLoading" class="chart-container"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Monitor } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { scoreApi } from '@/api/score'
import { formatDateTime, getScoreLevelLabel } from '@/utils/format'
import type { HealthScoreVO, HealthReportVO } from '@/types/api'

const router = useRouter()
const loading = ref(false)
const recalculating = ref(false)
const historyLoading = ref(false)
const reportLoading = ref(false)
const reportVisible = ref(false)
const scoreData = ref<HealthScoreVO | null>(null)
const reportData = ref<HealthReportVO | null>(null)
const historyRange = ref('30')
const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

// AbortController for cancelling report generation
let reportAbortController: AbortController | null = null

const goToAddMetric = () => {
  router.push('/health/metrics?action=add')
}

const fetchScore = async () => {
  loading.value = true
  try {
    const res = await scoreApi.getScore()
    scoreData.value = res.data
  } catch (error) {
    ElMessage.error('获取评分失败')
  } finally {
    loading.value = false
  }
}

const recalculate = async () => {
  recalculating.value = true
  try {
    await scoreApi.recalculateScore()
    ElMessage.success('重新计算成功')
    await fetchScore()
  } catch (error) {
    ElMessage.error('计算失败')
  } finally {
    recalculating.value = false
  }
}

/**
 * Open report dialog - tries to get cached report first
 */
const generateReport = async () => {
  // If we already have report data, just show it
  if (reportData.value) {
    reportVisible.value = true
    return
  }

  // Try to get cached report from backend
  reportLoading.value = true
  reportVisible.value = true

  try {
    const res = await scoreApi.getReport()
    reportData.value = res.data
  } catch (error: any) {
    // No cached report, close dialog and show error
    reportVisible.value = false
    ElMessage.error(error.response?.data?.message || '获取报告失败，请点击重新生成')
  } finally {
    reportLoading.value = false
  }
}

/**
 * Force regenerate report
 */
const regenerateReport = async () => {
  // Cancel any pending request
  reportAbortController?.abort()
  reportAbortController = new AbortController()

  reportLoading.value = true
  reportData.value = null

  try {
    const res = await scoreApi.generateReport()
    reportData.value = res.data
    ElMessage.success('报告生成成功')
  } catch (error: any) {
    if (error.name === 'CanceledError' || error.name === 'AbortError') {
      console.log('Report generation cancelled')
    } else {
      ElMessage.error(error.response?.data?.message || '生成报告失败')
    }
  } finally {
    reportLoading.value = false
    reportAbortController = null
  }
}

/**
 * Cancel report generation
 */
const cancelGenerate = () => {
  reportAbortController?.abort()
  reportLoading.value = false
  reportVisible.value = false
  reportData.value = null
}

/**
 * Handle dialog close
 */
const onReportDialogClose = () => {
  // If still loading, cancel the request
  if (reportLoading.value) {
    cancelGenerate()
  }
}

const fetchHistory = async () => {
  historyLoading.value = true
  try {
    const days = parseInt(historyRange.value)
    const res = await scoreApi.getScoreHistory(days)
    renderChart(res.data)
  } catch (error) {
    ElMessage.error('获取历史数据失败')
  } finally {
    historyLoading.value = false
  }
}

const renderChart = (data: any[]) => {
  if (!chartRef.value) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  const dates = data.map(item => item.date)
  const scores = data.map(item => item.score)

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: '{b}<br/>评分: {c}'
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      name: '评分'
    },
    series: [
      {
        data: scores,
        type: 'line',
        smooth: true,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
          ])
        },
        lineStyle: {
          color: '#409eff',
          width: 2
        },
        itemStyle: {
          color: '#409eff'
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

const getScoreTips = (level: string): string => {
  const tips: Record<string, string> = {
    EXCELLENT: '您的健康状况非常好，请继续保持！',
    GOOD: '您的健康状况良好，继续保持健康的生活方式。',
    FAIR: '您的健康状况一般，建议关注健康指标变化。',
    POOR: '您的健康状况需要改善，建议咨询医生。'
  }
  return tips[level] || '暂无评分数据'
}

const getFactorLabel = (key: string): string => {
  const labels: Record<string, string> = {
    healthProfile: '健康档案',
    latestMetrics: '医学指标',
    riskAssessment: '风险评估',
    screeningReport: '筛查报告',
    wellness: '保健指标',
    bloodGlucose: '血糖',
    bloodPressure: '血压',
    heartRate: '心率',
    bodyTemperature: '体温',
    bloodOxygen: '血氧',
    bmi: 'BMI',
    sleep: '睡眠',
    exercise: '运动'
  }
  return labels[key] || key
}

const formatFactorValue = (value: any): string => {
  // Handle new factor structure: { score, weight, contribution }
  if (typeof value === 'object' && value !== null && 'score' in value) {
    const score = value.score
    const weight = value.weight
    return `${score}分 (权重${Math.round(weight * 100)}%)`
  }
  if (typeof value === 'number') {
    return value.toFixed(1)
  }
  return String(value)
}

const getFactorPercentage = (value: any): number => {
  // Handle new factor structure: { score, weight, contribution }
  if (typeof value === 'object' && value !== null && 'score' in value) {
    return Math.min(100, Math.max(0, value.score))
  }
  if (typeof value === 'number') {
    return Math.min(100, Math.max(0, value))
  }
  return 0
}

const getFactorColor = (value: any): string => {
  // Handle new factor structure
  let score: number
  if (typeof value === 'object' && value !== null && 'score' in value) {
    score = value.score
  } else if (typeof value === 'number') {
    score = value
  } else {
    return '#409eff'
  }

  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

const getStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' => {
  const types: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    优秀: 'success',
    良好: 'success',
    一般: 'warning',
    需改善: 'danger',
    需关注: 'warning'
  }
  return types[status] || 'info'
}

const getScoreColor = (score: number): string => {
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

onMounted(async () => {
  await fetchScore()
  await nextTick()
  await fetchHistory()

  window.addEventListener('resize', () => {
    chartInstance?.resize()
  })
})
</script>

<style scoped>
.score-page {
  padding: 20px;
}

.score-card {
  margin-bottom: 20px;
}

.score-display {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.no-data-content {
  text-align: center;
  padding: 40px 20px;
}

.no-data-icon {
  color: #909399;
  margin-bottom: 20px;
}

.no-data-message {
  font-size: 20px;
  color: #303133;
  margin-bottom: 16px;
  font-weight: 500;
}

.no-data-tips {
  font-size: 14px;
  color: #606266;
  margin-bottom: 20px;
}

.required-metrics {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-bottom: 30px;
  flex-wrap: wrap;
}

.add-data-btn {
  margin-top: 10px;
}

.score-content {
  width: 100%;
}

.score-main {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 60px;
}

.score-circle {
  text-align: center;
}

.score-number {
  font-size: 96px;
  font-weight: bold;
  color: #409eff;
  line-height: 1;
}

.score-level {
  font-size: 24px;
  color: #606266;
  margin-top: 16px;
}

.score-info {
  flex: 1;
  max-width: 400px;
}

.score-tips {
  font-size: 16px;
  color: #606266;
  margin-bottom: 16px;
  line-height: 1.6;
}

.score-meta {
  font-size: 14px;
  color: #909399;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.factors-card {
  margin-bottom: 20px;
}

.factor-item {
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 16px;
}

.factor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.factor-name {
  font-size: 14px;
  color: #606266;
}

.factor-value {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.history-card {
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

@media (max-width: 768px) {
  .score-main {
    flex-direction: column;
    gap: 30px;
  }

  .score-number {
    font-size: 64px;
  }

  .score-level {
    font-size: 18px;
  }
}

/* 健康报告样式 */
.report-content {
  max-height: 70vh;
  overflow-y: auto;
}

.report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.report-time {
  font-size: 14px;
  color: #606266;
}

.report-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 16px;
}

.report-loading p {
  font-size: 16px;
  color: #606266;
}

.report-section {
  margin-bottom: 20px;
}

.overall-score {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.overall-score .score-value {
  font-size: 64px;
  font-weight: bold;
  color: #409eff;
}

.overall-score .score-level {
  font-size: 24px;
  color: #606266;
}

.dimension-item {
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 16px;
}

.dimension-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.dimension-name {
  font-size: 16px;
  font-weight: 500;
}

.dimension-desc {
  font-size: 14px;
  color: #606266;
  margin-top: 8px;
}

.risk-alert {
  margin-bottom: 12px;
}

.suggestion-item {
  padding: 8px 0;
}

.suggestion-category {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.suggestion-content {
  font-size: 15px;
  color: #303133;
  margin-bottom: 4px;
}

.suggestion-action {
  font-size: 14px;
  color: #606266;
}

.summary-text {
  font-size: 16px;
  line-height: 1.8;
  color: #303133;
}
</style>
