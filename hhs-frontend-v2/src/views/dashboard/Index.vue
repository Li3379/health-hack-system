<template>
  <div class="dashboard">
    <!-- Welcome Section -->
    <div class="welcome-section">
      <div class="welcome-text">
        <h1>欢迎回来，{{ authStore.user?.nickname || authStore.user?.username }}</h1>
        <p>今日健康状态概览</p>
      </div>
      <div class="quick-actions">
        <el-button type="primary" @click="goToMetrics">
          <el-icon><Plus /></el-icon>
          添加指标
        </el-button>
        <el-button @click="goToAI">
          <el-icon><ChatDotRound /></el-icon>
          AI 咨询
        </el-button>
      </div>
    </div>

    <!-- Stats Cards -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card" @click="goToScore">
          <div class="stat-icon" style="background: var(--color-primary); color: white">
            <el-icon :size="24"><TrendCharts /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ healthScore }}</div>
            <div class="stat-label">健康评分</div>
          </div>
          <div class="stat-trend" :class="scoreTrendClass">
            <el-icon><component :is="scoreTrendIcon" /></el-icon>
            {{ scoreTrendText }}
          </div>
        </div>
      </el-col>

      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card alert-card" @click="goToAlerts">
          <div class="stat-icon" :class="{ 'has-alerts': alertStore.unreadCount > 0 }">
            <el-icon :size="24"><Bell /></el-icon>
            <span v-if="alertStore.unreadCount > 0" class="alert-dot"></span>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ alertStore.unreadCount }}</div>
            <div class="stat-label">未读预警</div>
          </div>
          <el-icon class="stat-arrow"><ArrowRight /></el-icon>
        </div>
      </el-col>

      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card" @click="goToMetrics">
          <div class="stat-icon" style="background: var(--color-success); color: white">
            <el-icon :size="24"><DataLine /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ metricCount }}</div>
            <div class="stat-label">健康指标</div>
          </div>
          <el-icon class="stat-arrow"><ArrowRight /></el-icon>
        </div>
      </el-col>

      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card" @click="goToScreening">
          <div class="stat-icon" style="background: var(--color-warning); color: white">
            <el-icon :size="24"><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ reportCount }}</div>
            <div class="stat-label">体检报告</div>
          </div>
          <el-icon class="stat-arrow"><ArrowRight /></el-icon>
        </div>
      </el-col>
    </el-row>

    <!-- Main Content -->
    <el-row :gutter="20" class="content-row">
      <!-- Health Score Card -->
      <el-col :xs="24" :md="12">
        <el-card class="score-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">健康评分</span>
              <el-button text type="primary" @click="goToScore">
                查看详情
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>

          <div v-loading="scoreLoading" class="score-content">
            <div v-if="scoreData" class="score-display">
              <HealthScoreCircle
                :score="scoreData.score"
                :level="getScoreLevelLabel(scoreData.level)"
                :size="160"
                :stroke-width="10"
              />
              <div class="score-info">
                <p class="score-tip">{{ getScoreTips(scoreData.level) }}</p>
                <div v-if="scoreData.factors && scoreData.factors.length > 0" class="score-factors">
                  <div class="factor-label">评分因素:</div>
                  <div class="factor-tags">
                    <el-tag
                      v-for="factor in scoreData.factors.slice(0, 3)"
                      :key="factor"
                      size="small"
                      type="info"
                    >
                      {{ factor }}
                    </el-tag>
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无评分数据，添加健康数据后即可获得评分">
              <el-button type="primary" @click="goToMetrics">添加数据</el-button>
            </el-empty>
          </div>
        </el-card>
      </el-col>

      <!-- Recent Alerts Card -->
      <el-col :xs="24" :md="12">
        <el-card class="alerts-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">最近预警</span>
              <el-button text type="primary" @click="goToAlerts">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>

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
                <el-icon :size="48" color="var(--color-success)"><CircleCheck /></el-icon>
              </template>
            </el-empty>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Quick Actions Card -->
    <el-card class="actions-card">
      <template #header>
        <span class="card-title">快速入口</span>
      </template>
      <div class="quick-entry">
        <div class="entry-item" @click="goToMetrics">
          <div class="entry-icon">
            <el-icon :size="28"><Plus /></el-icon>
          </div>
          <span class="entry-label">添加指标</span>
        </div>
        <div class="entry-item" @click="goToAI">
          <div class="entry-icon">
            <el-icon :size="28"><ChatDotRound /></el-icon>
          </div>
          <span class="entry-label">AI 顾问</span>
        </div>
        <div class="entry-item" @click="goToScreening">
          <div class="entry-icon">
            <el-icon :size="28"><Upload /></el-icon>
          </div>
          <span class="entry-label">上传报告</span>
        </div>
        <div class="entry-item" @click="goToRealtime">
          <div class="entry-icon">
            <el-icon :size="28"><Monitor /></el-icon>
          </div>
          <span class="entry-label">实时监控</span>
        </div>
        <div class="entry-item" @click="goToWellness">
          <div class="entry-icon">
            <el-icon :size="28"><Sunny /></el-icon>
          </div>
          <span class="entry-label">保健中心</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAlertStore } from '@/stores/alert'
import { scoreApi } from '@/api/score'
import { healthApi } from '@/api/health'
import { formatRelativeTime, getScoreLevelLabel } from '@/utils/format'
import HealthScoreCircle from '@/components/HealthScoreCircle.vue'
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

// Score trend computed
const scoreTrendClass = computed(() => {
  if (!scoreData.value) return ''
  const level = scoreData.value.level
  if (level === 'EXCELLENT' || level === 'GOOD') return 'positive'
  if (level === 'FAIR') return 'neutral'
  return 'negative'
})

const scoreTrendIcon = computed(() => {
  if (!scoreData.value) return 'Minus'
  const level = scoreData.value.level
  if (level === 'EXCELLENT' || level === 'GOOD') return 'Top'
  if (level === 'FAIR') return 'Minus'
  return 'Bottom'
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

// Fetch functions
const fetchHealthScore = async () => {
  scoreLoading.value = true
  try {
    const res = await scoreApi.getScore()
    scoreData.value = res.data
    if (res.data.level === 'NO_DATA') {
      healthScore.value = 0
    } else {
      healthScore.value = res.data.score
    }
  } catch (error) {
    console.error('Failed to fetch health score:', error)
    healthScore.value = 0
  } finally {
    scoreLoading.value = false
  }
}

const fetchMetricCount = async () => {
  try {
    const res = await healthApi.getMetrics({ page: 1, size: 1 })
    metricCount.value = res.data.total
  } catch (error) {
    console.error('Failed to fetch metric count:', error)
  }
}

const fetchAlerts = async () => {
  alertLoading.value = true
  try {
    await alertStore.fetchRecentAlerts(5)
  } finally {
    alertLoading.value = false
  }
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

// Navigation
const goToScore = () => router.push('/health/score')
const goToAlerts = () => router.push('/health/alerts')
const goToMetrics = () => router.push('/health/metrics')
const goToAI = () => router.push('/ai/chat')
const goToScreening = () => router.push('/screening/list')
const goToRealtime = () => router.push('/realtime/monitor')
const goToWellness = () => router.push('/wellness/dashboard')

onMounted(() => {
  fetchHealthScore()
  fetchMetricCount()
  fetchAlerts()
  alertStore.fetchUnreadCount()
})
</script>

<style scoped>
.dashboard {
  padding: var(--spacing-lg);
  max-width: var(--content-max-width);
  margin: 0 auto;
}

/* Welcome Section */
.welcome-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
  flex-wrap: wrap;
  gap: var(--spacing-md);
}

.welcome-text h1 {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  margin: 0 0 4px;
}

.welcome-text p {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  margin: 0;
}

.quick-actions {
  display: flex;
  gap: var(--spacing-sm);
}

/* Stats Cards */
.stats-row {
  margin-bottom: var(--spacing-lg);
}

.stat-card {
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  padding: var(--spacing-lg);
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  cursor: pointer;
  transition: var(--transition-shadow), var(--transition-transform);
  border: 1px solid var(--color-border-light);
  height: 100%;
  min-height: 100px;
}

.stat-card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-tertiary);
  color: var(--color-text-secondary);
  flex-shrink: 0;
  position: relative;
}

.stat-icon.has-alerts {
  background: var(--color-danger);
  color: white;
}

.alert-dot {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 12px;
  height: 12px;
  background: var(--color-danger);
  border-radius: 50%;
  border: 2px solid var(--color-surface);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.2);
    opacity: 0.7;
  }
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-top: 2px;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  padding: 4px 8px;
  border-radius: var(--radius-full);
}

.stat-trend.positive {
  background: rgba(16, 185, 129, 0.1);
  color: var(--color-success);
}

.stat-trend.neutral {
  background: rgba(107, 114, 128, 0.1);
  color: var(--color-info);
}

.stat-trend.negative {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-danger);
}

.stat-arrow {
  color: var(--color-text-tertiary);
  transition: var(--transition-transform);
}

.stat-card:hover .stat-arrow {
  transform: translateX(4px);
}

/* Content Cards */
.content-row {
  margin-bottom: var(--spacing-lg);
}

.score-card,
.alerts-card,
.actions-card {
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border-light);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

/* Score Content */
.score-content {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.score-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: var(--spacing-lg);
}

.score-info {
  max-width: 280px;
}

.score-tip {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin: 0 0 var(--spacing-md);
}

.score-factors {
  text-align: left;
}

.factor-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-bottom: var(--spacing-xs);
}

.factor-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
}

/* Alerts Content */
.alerts-content {
  min-height: 280px;
}

.alerts-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.alert-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  border-radius: var(--radius-lg);
  background: var(--color-bg-secondary);
  transition: var(--transition-colors);
}

.alert-item:hover {
  background: var(--color-bg-tertiary);
}

.alert-indicator {
  width: 4px;
  height: 100%;
  min-height: 40px;
  border-radius: var(--radius-full);
  background: var(--color-info);
  flex-shrink: 0;
}

.alert-item.alert-critical .alert-indicator,
.alert-item.alert-high .alert-indicator {
  background: var(--color-danger);
}

.alert-item.alert-medium .alert-indicator {
  background: var(--color-warning);
}

.alert-item.alert-low .alert-indicator {
  background: var(--color-success);
}

.alert-body {
  flex: 1;
  min-width: 0;
}

.alert-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.alert-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.alert-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.alert-message {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* Quick Entry */
.quick-entry {
  display: flex;
  gap: var(--spacing-md);
  overflow-x: auto;
  padding-bottom: var(--spacing-sm);
}

.entry-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  min-width: 80px;
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: var(--transition-colors);
}

.entry-item:hover {
  background: var(--color-bg-tertiary);
}

.entry-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary-light), var(--color-primary));
  color: white;
}

.entry-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  white-space: nowrap;
}

/* Responsive */
@media (max-width: 768px) {
  .dashboard {
    padding: var(--spacing-md);
  }

  .welcome-section {
    flex-direction: column;
    align-items: flex-start;
  }

  .quick-actions {
    width: 100%;
  }

  .quick-actions .el-button {
    flex: 1;
  }

  .stat-card {
    margin-bottom: var(--spacing-md);
  }

  .score-card,
  .alerts-card {
    margin-bottom: var(--spacing-md);
  }
}
</style>
