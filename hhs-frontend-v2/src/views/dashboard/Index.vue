<template>
  <div class="dashboard">
    <!-- Welcome -->
    <div class="welcome-section reveal">
      <div class="welcome-text">
        <div class="eyebrow">DASHBOARD</div>
        <h1 class="welcome-title">欢迎回来，{{ authStore.user?.nickname || authStore.user?.username }}</h1>
        <p class="welcome-sub">今日健康状态概览</p>
      </div>
      <div class="quick-actions">
        <el-button type="primary" @click="goToMetrics">
          <el-icon><Plus /></el-icon>添加指标
        </el-button>
        <el-button @click="goToAI">
          <el-icon><ChatDotRound /></el-icon>AI 咨询
        </el-button>
      </div>
    </div>

    <!-- Stats Grid -->
    <el-skeleton :loading="scoreLoading" animated>
      <template #template>
        <div class="stats-grid">
          <div v-for="n in 4" :key="n" class="stat-card">
            <el-skeleton-item variant="circle" style="width: 44px; height: 44px; flex-shrink: 0;" />
            <div class="stat-content">
              <el-skeleton-item variant="text" style="width: 60px; height: 28px;" />
              <el-skeleton-item variant="text" style="width: 80px; height: 16px; margin-top: 4px;" />
            </div>
          </div>
        </div>
      </template>
      <template #default>
        <div class="stats-grid">
          <div v-for="(card, idx) in statCards" :key="idx" class="stat-card reveal" :style="{ animationDelay: `${idx * 0.08}s` }" @click="card.action">
            <div class="stat-icon" :style="{ background: card.iconBg }">
              <el-icon :size="22"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
              <SparklineChart
                v-if="sparklineTrends[idx]?.length"
                :data="sparklineTrends[idx]"
                :height="28"
              />
            </div>
            <div v-if="card.trend" class="stat-trend" :class="card.trendClass">
              <el-icon><component :is="card.trendIcon" /></el-icon>
              {{ card.trend }}
            </div>
            <el-icon v-else class="stat-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </template>
    </el-skeleton>

    <!-- Content Grid -->
    <div class="content-grid">
      <!-- Health Score -->
      <div class="content-card reveal">
        <div class="card-header">
          <span class="card-title">健康评分</span>
          <el-button text type="primary" @click="goToScore">
            查看详情<el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <div class="score-content">
          <div v-if="scoreData" class="score-display">
            <HealthScoreCircle
              :score="scoreData.score"
              :level="getScoreLevelLabel(scoreData.level)"
              :size="140"
              :stroke-width="8"
            />
            <div class="score-info">
              <p class="score-tip">{{ getScoreTips(scoreData.level) }}</p>
              <div v-if="scoreData.factors && Object.keys(scoreData.factors).length > 0" class="score-factors">
                <div class="factor-label">评分因素</div>
                <div class="factor-tags">
                  <el-tag v-for="key in Object.keys(scoreData.factors).slice(0, 3)" :key="key" size="small">
                    {{ getFactorLabel(key) }}
                  </el-tag>
                </div>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无评分数据">
            <el-button type="primary" @click="goToMetrics">添加数据</el-button>
          </el-empty>
        </div>
      </div>

      <!-- Recent Alerts -->
      <div class="content-card reveal">
        <div class="card-header">
          <span class="card-title">最近预警</span>
          <el-button text type="primary" @click="goToAlerts">
            查看全部<el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <div v-loading="alertLoading" class="alerts-content">
          <div v-if="alertStore.recentAlerts.length > 0" class="alerts-list">
            <div
              v-for="alert in alertStore.recentAlerts"
              :key="alert.id"
              class="alert-item"
              :class="'alert-' + alert.alertLevel?.toLowerCase()"
            >
              <div class="alert-indicator"></div>
              <div class="alert-body">
                <div class="alert-header">
                  <span class="alert-title">{{ alert.title }}</span>
                  <span class="alert-time">{{ formatRelativeTime(alert.createdAt) }}</span>
                </div>
                <p class="alert-message">{{ alert.message }}</p>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无预警信息">
            <template #image>
              <el-icon :size="48" color="var(--accent-cool)"><CircleCheck /></el-icon>
            </template>
          </el-empty>
        </div>
      </div>

      <!-- Activity Rings -->
      <div class="content-card activity-card reveal">
        <div class="card-header">
          <span class="card-title">今日活动</span>
        </div>
        <div class="activity-body">
          <ActivityRings
            :exercise-percent="exercisePercent"
            :sleep-percent="sleepPercent"
            :habits-percent="habitsPercent"
          />
        </div>
      </div>
    </div>

    <!-- Quick Entry -->
    <div class="actions-card reveal">
      <div class="card-header">
        <span class="card-title">快速入口</span>
      </div>
      <div class="quick-entry">
        <div v-for="entry in entries" :key="entry.label" class="entry-item" @click="entry.action">
          <div class="entry-icon" :style="{ background: entry.bg }">
            <el-icon :size="24"><component :is="entry.icon" /></el-icon>
          </div>
          <span class="entry-label">{{ entry.label }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAlertStore } from '@/stores/alert'
import {
  TrendCharts, Bell, DataLine, Document, Plus, ChatDotRound,
  ArrowRight, CircleCheck, Top, Minus, Bottom, Upload, Monitor, Sunny
} from '@element-plus/icons-vue'
import { scoreApi } from '@/api/score'
import { healthApi } from '@/api/health'
import { statsApi } from '@/api/stats'
import { formatRelativeTime, getScoreLevelLabel } from '@/utils/format'
import HealthScoreCircle from '@/components/HealthScoreCircle.vue'
import SparklineChart from '@/components/SparklineChart.vue'
import ActivityRings from '@/components/ActivityRings.vue'
import type { HealthScoreVO } from '@/types/api'

const router = useRouter()
const authStore = useAuthStore()
const alertStore = useAlertStore()

const healthScore = ref(0)
const metricCount = ref(0)
const reportCount = ref(0)
const scoreLoading = ref(false)
const alertLoading = ref(false)
const scoreData = ref<HealthScoreVO | null>(null)

// Sparkline trend data
const scoreTrend = ref<number[]>([])
const heartRateTrend = ref<number[]>([])
const stepsTrend = ref<number[]>([])
const sleepTrend = ref<number[]>([])

const fetchTrends = async () => {
  try {
    const [scoreRes, hrRes, stepsRes, sleepRes] = await Promise.allSettled([
      statsApi.getTrends('health_score', 7),
      statsApi.getTrends('heart_rate', 7),
      statsApi.getTrends('steps', 7),
      statsApi.getTrends('sleep_duration', 7),
    ])
    if (scoreRes.status === 'fulfilled') scoreTrend.value = scoreRes.value.data[0]?.values ?? []
    if (hrRes.status === 'fulfilled') heartRateTrend.value = hrRes.value.data[0]?.values ?? []
    if (stepsRes.status === 'fulfilled') stepsTrend.value = stepsRes.value.data[0]?.values ?? []
    if (sleepRes.status === 'fulfilled') sleepTrend.value = sleepRes.value.data[0]?.values ?? []
  } catch {
    // Trends are non-critical, fail silently
  }
}

const scoreTrendClass = computed(() => {
  if (!scoreData.value) return ''
  const level = scoreData.value.level
  if (level === 'EXCELLENT' || level === 'GOOD') return 'positive'
  if (level === 'FAIR') return 'neutral'
  return 'negative'
})

const scoreTrendIcon = computed(() => {
  if (!scoreData.value) return Minus
  const level = scoreData.value.level
  if (level === 'EXCELLENT' || level === 'GOOD') return Top
  if (level === 'FAIR') return Minus
  return Bottom
})

const scoreTrendText = computed(() => {
  if (!scoreData.value) return '待评估'
  const level = scoreData.value.level
  if (level === 'EXCELLENT') return '优秀'
  if (level === 'GOOD') return '良好'
  if (level === 'FAIR') return '一般'
  if (level === 'POOR') return '需改善'
  return '无数据'
})

const goToScore = () => router.push('/health/score')
const goToAlerts = () => router.push('/health/alerts')
const goToMetrics = () => router.push('/health/metrics')
const goToAI = () => router.push('/ai/chat')
const goToScreening = () => router.push('/screening/list')
const goToRealtime = () => router.push('/realtime/monitor')
const goToWellness = () => router.push('/wellness/dashboard')

const sparklineTrends = computed(() => [
  scoreTrend.value,
  heartRateTrend.value,
  stepsTrend.value,
  sleepTrend.value,
])


// Activity ring percentages (mock data derived from available trends)
const exercisePercent = computed(() => {
  const latest = stepsTrend.value.length > 0 ? stepsTrend.value[stepsTrend.value.length - 1] : 0
  return Math.min(Math.round((latest / 10000) * 100), 100)
})

const sleepPercent = computed(() => {
  const latest = sleepTrend.value.length > 0 ? sleepTrend.value[sleepTrend.value.length - 1] : 0
  return Math.min(Math.round((latest / 8) * 100), 100)
})

const habitsPercent = computed(() => {
  const activeDays = stepsTrend.value.filter(v => v > 0).length
  return Math.min(Math.round((activeDays / 7) * 100), 100)
})


const statCards = computed(() => [
  { icon: TrendCharts, iconBg: 'var(--accent-cool-soft)', value: healthScore.value, label: '健康评分', trend: scoreTrendText.value, trendClass: scoreTrendClass.value, trendIcon: scoreTrendIcon.value, action: goToScore },
  { icon: Bell, iconBg: 'var(--accent-warm-soft)', value: alertStore.unreadCount, label: '未读预警', action: goToAlerts },
  { icon: DataLine, iconBg: 'var(--accent-cool-soft)', value: metricCount.value, label: '健康指标', action: goToMetrics },
  { icon: Document, iconBg: 'var(--accent-warm-soft)', value: reportCount.value, label: '体检报告', action: goToScreening }
])

const entries = [
  { icon: Plus, label: '添加指标', bg: 'var(--accent-cool-soft)', action: goToMetrics },
  { icon: ChatDotRound, label: 'AI 顾问', bg: 'var(--accent-warm-soft)', action: goToAI },
  { icon: Upload, label: '上传报告', bg: 'var(--accent-cool-soft)', action: goToScreening },
  { icon: Monitor, label: '实时监控', bg: 'var(--accent-warm-soft)', action: goToRealtime },
  { icon: Sunny, label: '保健中心', bg: 'var(--accent-cool-soft)', action: goToWellness }
]

const fetchHealthScore = async () => {
  scoreLoading.value = true
  try {
    const res = await scoreApi.getScore()
    scoreData.value = res.data
    healthScore.value = res.data.level === 'NO_DATA' ? 0 : res.data.score
  } catch { healthScore.value = 0 } finally { scoreLoading.value = false }
}

const fetchMetricCount = async () => {
  try {
    const res = await healthApi.getMetrics({ page: 1, size: 1 })
    metricCount.value = res.data.total
  } catch { void 0 }
}

const fetchAlerts = async () => {
  alertLoading.value = true
  try { await alertStore.fetchRecentAlerts(5) } finally { alertLoading.value = false }
}

const getScoreTips = (level: string): string => {
  const tips: Record<string, string> = {
    EXCELLENT: '您的健康状况非常好，请继续保持！',
    GOOD: '您的健康状况良好，继续保持健康的生活方式。',
    FAIR: '您的健康状况一般，建议关注健康指标变化。',
    POOR: '您的健康状况需要改善，建议咨询医生。',
    NO_DATA: '添加健康数据后即可获得健康评分'
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

let revealObserver: IntersectionObserver | null = null

onMounted(() => {
  fetchHealthScore()
  fetchMetricCount()
  fetchAlerts()
  fetchTrends()
  alertStore.fetchUnreadCount()

  // Scroll reveal
  const obs = new IntersectionObserver((entries) => {
    entries.forEach(e => { if (e.isIntersecting) { e.target.classList.add('in-view'); obs.unobserve(e.target) } })
  }, { threshold: 0.1 })
  revealObserver = obs
  document.querySelectorAll('.reveal').forEach(el => obs.observe(el))
})

onUnmounted(() => {
  revealObserver?.disconnect()
})
</script>

<style scoped>
.dashboard {
  padding: 32px;
  max-width: var(--content-max-width);
  margin: 0 auto;
}

/* Welcome */
.welcome-section {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 32px;
  flex-wrap: wrap;
  gap: 16px;
}
.welcome-title {
  font: 700 clamp(24px, 3vw, 32px)/1.15 var(--font-ui);
  color: var(--text-1);
  letter-spacing: -0.02em;
  margin-bottom: 4px;
}
.welcome-sub {
  font: 400 15px/1.5 var(--font-ui);
  color: var(--text-3);
}
.quick-actions {
  display: flex;
  gap: 12px;
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.stat-card {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;
  box-shadow: var(--shadow-md), 0 1px 0 var(--glass-border) inset;
  transition: transform var(--dur-spring) var(--ease-spring-soft),
              box-shadow var(--dur-mid) var(--ease);
}
.stat-card:hover {
  transform: translateY(-3px);
}
.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--accent-cool);
}
.stat-content { flex: 1; min-width: 0; }
.stat-value {
  font: 700 28px/1.2 var(--font-ui);
  color: var(--text-1);
  letter-spacing: -0.02em;
}
.stat-label {
  font: 400 13px/1.4 var(--font-ui);
  color: var(--text-3);
  margin-top: 2px;
}
.stat-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  font: 500 11px/1 var(--font-mono);
  padding: 4px 8px;
  border-radius: var(--radius-full);
  letter-spacing: 0.04em;
}
.stat-trend.positive { background: var(--accent-cool-soft); color: var(--accent-cool); }
.stat-trend.neutral { background: var(--surface-2); color: var(--text-3); }
.stat-trend.negative { background: rgba(248, 113, 113, 0.12); color: var(--error); }
.stat-arrow {
  color: var(--text-4);
  transition: transform var(--dur-mid) var(--ease);
}
.stat-card:hover .stat-arrow { transform: translateX(4px); }

/* Content Grid */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}
.content-card {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-md), 0 1px 0 var(--glass-border) inset;
}
.content-card:hover {
  transform: translateY(-2px);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.card-title {
  font: 600 15px/1 var(--font-ui);
  color: var(--text-1);
  letter-spacing: -0.01em;
}

/* Score */
.score-content { min-height: 240px; display: flex; align-items: center; justify-content: center; }
.score-display { display: flex; flex-direction: row; align-items: center; text-align: left; gap: 24px; }
.score-info { max-width: 260px; }
.score-tip { font: 400 14px/1.5 var(--font-ui); color: var(--text-3); margin-bottom: 12px; }
.score-factors { text-align: left; }

/* Responsive for score display */
@media (max-width: 768px) {
  .score-display { flex-direction: column; text-align: center; }
}
.factor-label { font: 500 11px/1 var(--font-mono); color: var(--text-4); letter-spacing: 0.08em; text-transform: uppercase; margin-bottom: 8px; }
.factor-tags { display: flex; flex-wrap: wrap; gap: 6px; }

/* Alerts */
.alerts-content { min-height: 240px; }
.alerts-list { display: flex; flex-direction: column; gap: 10px; }
.alert-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid var(--glass-border);
  transition: transform var(--dur-spring-fast) var(--ease-spring-soft);
}
.alert-item:hover {
  transform: translateX(4px);
}
.alert-indicator {
  width: 3px;
  height: 100%;
  min-height: 32px;
  border-radius: 2px;
  flex-shrink: 0;
}
.alert-item.alert-critical .alert-indicator,
.alert-item.alert-high .alert-indicator { background: var(--error); }
.alert-item.alert-medium .alert-indicator { background: var(--warning); }
.alert-item.alert-low .alert-indicator { background: var(--accent-cool); }
.alert-body { flex: 1; min-width: 0; }
.alert-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.alert-title { font: 500 13px/1.4 var(--font-ui); color: var(--text-1); }
.alert-time { font: 400 11px/1 var(--font-mono); color: var(--text-4); letter-spacing: 0.04em; }
.alert-message {
  font: 400 13px/1.5 var(--font-ui);
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* Activity Card */
.activity-card .activity-body {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 260px;
}

/* Quick Entry */
.actions-card {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-md), 0 1px 0 var(--glass-border) inset;
}
.quick-entry {
  display: flex;
  gap: 16px;
  overflow-x: auto;
}
.entry-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 12px;
  min-width: 80px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: transform var(--dur-spring-fast) var(--ease-spring-soft);
}
.entry-item:hover { 
  transform: translateY(-2px);
}
.entry-item:active {
  transform: translateY(0) scale(0.98);
}
.entry-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-cool);
  transition: transform var(--dur-spring) var(--ease-spring-bounce),
              box-shadow var(--dur-mid) var(--ease);
}
.entry-item:hover .entry-icon { 
  transform: translateY(-6px) scale(1.1); 
  box-shadow: 0 8px 20px rgba(var(--accent-cool-rgb), 0.3);
}
.entry-label {
  font: 400 12px/1 var(--font-ui);
  color: var(--text-3);
  white-space: nowrap;
  transition: color var(--dur-fast) var(--ease);
}
.entry-item:hover .entry-label { color: var(--text-1); }

/* Responsive */
@media (max-width: 1200px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 768px) {
  .dashboard { padding: 20px; }
  .welcome-section { flex-direction: column; align-items: flex-start; }
  .stats-grid { grid-template-columns: 1fr; }
  .content-grid { grid-template-columns: 1fr; }
  .stat-value { font-size: 24px; }
}
</style>
