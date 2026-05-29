<template>
  <div class="data-input-page">
    <!-- 今日录入概览 -->
    <el-row :gutter="16" class="overview-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="overview-card">
          <div class="overview-content">
            <el-icon class="overview-icon health"><DataLine /></el-icon>
            <div class="overview-info">
              <span class="overview-value">{{ todayHealthCount }}</span>
              <span class="overview-label">健康指标</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="overview-card">
          <div class="overview-content">
            <el-icon class="overview-icon wellness"><Sunny /></el-icon>
            <div class="overview-info">
              <span class="overview-value">{{ todayWellnessCount }}</span>
              <span class="overview-label">保健指标</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="overview-card">
          <div class="overview-content">
            <el-icon class="overview-icon sync"><Connection /></el-icon>
            <div class="overview-info">
              <span class="overview-value">{{ deviceSyncCount }}</span>
              <span class="overview-label">设备同步</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="overview-card">
          <div class="overview-content">
            <el-icon class="overview-icon ai"><MagicStick /></el-icon>
            <div class="overview-info">
              <span class="overview-value">{{ aiRecognizeCount }}</span>
              <span class="overview-label">AI识别</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主功能区 -->
    <el-row :gutter="16">
      <!-- 快捷录入 -->
      <el-col :xs="24" :lg="12">
        <QuickInput @refresh="refreshData" />
      </el-col>

      <!-- AI智能录入 -->
      <el-col :xs="24" :lg="12">
        <AIInput @refresh="refreshData" />
      </el-col>

      <!-- 设备同步 -->
      <el-col :xs="24" :lg="12">
        <DeviceSync @refresh="refreshData" />
      </el-col>

      <!-- OCR识别 -->
      <el-col :xs="24" :lg="12">
        <OcrInput @refresh="refreshData" />
      </el-col>
    </el-row>

    <!-- 最近录入记录 -->
    <el-row>
      <el-col :span="24">
        <RecentRecords ref="recentRecordsRef" />
      </el-col>
    </el-row>

    <!-- OCR识别历史 -->
    <el-row>
      <el-col :span="24">
        <OcrHistory ref="ocrHistoryRef" />
      </el-col>
    </el-row>

    <!-- AI解析历史 -->
    <el-row>
      <el-col :span="24">
        <AiHistory ref="aiHistoryRef" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { DataLine, Sunny, Connection, MagicStick } from '@element-plus/icons-vue'
import {
  staggerReveal, countUp, cleanupScrollTriggers,
  tilt3D, buttonPress, hoverLift, hoverShine, DUR, EASE,
} from '@/composables/useGsap'
import QuickInput from './components/QuickInput.vue'
import AIInput from './components/AIInput.vue'
import DeviceSync from './components/DeviceSync.vue'
import OcrInput from './components/OcrInput.vue'
import RecentRecords from './components/RecentRecords.vue'
import OcrHistory from './components/OcrHistory.vue'
import AiHistory from './components/AiHistory.vue'
import { request } from '@/utils/request'

// TypeScript 接口定义
interface TodayStats {
  healthMetricsCount: number
  wellnessMetricsCount: number
  deviceSyncCount: number
  aiRecognizeCount: number
}

// 今日统计数据
const todayHealthCount = ref(0)
const todayWellnessCount = ref(0)
const deviceSyncCount = ref(0)
const aiRecognizeCount = ref(0)

const recentRecordsRef = ref()

/**
 * 加载今日统计数据
 */
const loadTodayStats = async () => {
  try {
    const res = await request.get<TodayStats>('/api/stats/today')
    if (res.code === 200 && res.data) {
      todayHealthCount.value = res.data.healthMetricsCount
      todayWellnessCount.value = res.data.wellnessMetricsCount
      deviceSyncCount.value = res.data.deviceSyncCount
      aiRecognizeCount.value = res.data.aiRecognizeCount
    }
  } catch (error) {
    // stats load failed
  }
}

// 刷新数据
const refreshData = () => {
  loadTodayStats()
  if (recentRecordsRef.value) {
    recentRecordsRef.value.refresh()
  }
}

onMounted(async () => {
  await loadTodayStats()

  // ── Overview cards: dramatic entrance ──
  staggerReveal('.overview-card', {
    y: 50,
    scale: 0.85,
    stagger: 0.1,
    duration: DUR.slow,
    ease: EASE.back,
  })
  tilt3D('.overview-card', { maxTilt: 10, scale: 1.03 })

  // ── Content cards: scroll-driven ──
  staggerReveal('.el-col > .el-card, .el-col > *', {
    y: 30,
    scale: 0.95,
    scrollTrigger: true,
    stagger: 0.1,
    duration: DUR.mid,
    ease: EASE.expo,
  })

  // ── Count up overview values ──
  setTimeout(() => {
    document.querySelectorAll('.overview-value').forEach((el) => {
      const num = parseInt(el.textContent?.trim() ?? '0', 10)
      if (num > 0) countUp(el as HTMLElement, num, { duration: DUR.dramatic })
    })
  }, 600)

  // ── Interaction: hover lift on overview cards ──
  hoverLift('.overview-card', { y: -6, scale: 1.02 })

  // ── Interaction: shine sweep on content cards ──
  hoverShine('.el-col > .el-card')

  // ── Interaction: press feedback on tab buttons ──
  buttonPress('.el-tabs__item')
})

onUnmounted(() => { cleanupScrollTriggers() })
</script>

<style scoped>
.data-input-page {
  padding: 32px;
  max-width: var(--content-max-width);
  margin: 0 auto;
}
.overview-row { margin-bottom: 24px; }

.overview-card {
  background: var(--surface-1) !important;
  border: 1px solid var(--border) !important;
  border-radius: var(--radius-lg) !important;
  box-shadow: none !important;
  transition: border-color var(--dur-mid) var(--ease), transform var(--dur-mid) var(--ease) !important;
}
.overview-card:hover {
  border-color: var(--border-strong) !important;
  transform: translateY(-2px) !important;
}
.overview-content { display: flex; align-items: center; gap: 14px; }
.overview-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  transition: transform var(--dur-mid) var(--ease);
}
.overview-card:hover .overview-icon { transform: scale(1.08); }
.overview-icon.health { background: var(--accent-cool-soft); color: var(--accent-cool); }
.overview-icon.wellness { background: var(--accent-cool-soft); color: var(--accent-cool); }
.overview-icon.sync { background: var(--accent-warm-soft); color: var(--accent-warm); }
.overview-icon.ai { background: var(--accent-cool-soft); color: var(--accent-cool); }
.overview-info { display: flex; flex-direction: column; }
.overview-value { font: 700 28px/1.2 var(--font-ui); color: var(--text-1); letter-spacing: -0.02em; }
.overview-label { font: 400 13px/1.4 var(--font-ui); color: var(--text-3); margin-top: 2px; }
@media (max-width: 768px) { .data-input-page { padding: 20px; } }
</style>
