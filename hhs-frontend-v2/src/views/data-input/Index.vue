<template>
  <div class="data-input-page">
    <!-- 今日录入概览：三等分 -->
    <div class="overview-row">
      <el-card
        v-for="stat in overviewStats"
        :key="stat.key"
        shadow="hover"
        class="overview-card"
      >
        <div class="overview-content">
          <el-icon class="overview-icon"><component :is="stat.icon" /></el-icon>
          <div class="overview-info">
            <span class="overview-value" :data-target="stat.numericValue">{{ stat.display }}</span>
            <span class="overview-label">{{ stat.label }}</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 统计取不到时不能把三个 0 当成真实值：卡片显示破折号，这里给出可执行的下一步。 -->
    <p v-if="statsFailed" class="overview-failed" role="status" aria-live="polite">
      今日统计暂时无法加载，卡片显示的是占位符而非 0。
      <el-button text type="primary" size="small" :loading="statsLoading" @click="loadTodayStats">
        重新加载统计
      </el-button>
    </p>

    <!-- 主功能区：左快捷录入，右上 AI，右下 OCR -->
    <div class="work-grid">
      <div class="work-cell work-cell--tall">
        <QuickInput @refresh="refreshData" />
      </div>
      <div class="work-cell">
        <AIInput @refresh="refreshData" />
      </div>
      <div class="work-cell">
        <OcrInput @refresh="refreshData" />
      </div>
    </div>

    <!-- 历史与记录：标签页承载，避免三张长表纵向堆叠 -->
    <el-card shadow="never" class="history-card">
      <el-tabs v-model="activeHistory" class="history-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="最近录入" name="records">
          <RecentRecords ref="recentRecordsRef" />
        </el-tab-pane>
        <el-tab-pane label="OCR 识别历史" name="ocr">
          <OcrHistory ref="ocrHistoryRef" />
        </el-tab-pane>
        <el-tab-pane label="AI 解析历史" name="ai">
          <AiHistory ref="aiHistoryRef" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { Component } from 'vue'
import { DataLine, Sunny, MagicStick } from '@element-plus/icons-vue'
import {
  staggerReveal, countUp, cleanupScrollTriggers,
  hoverLift, DUR, EASE,
} from '@/composables/useGsap'
import QuickInput from './components/QuickInput.vue'
import AIInput from './components/AIInput.vue'
import OcrInput from './components/OcrInput.vue'
import RecentRecords from './components/RecentRecords.vue'
import OcrHistory from './components/OcrHistory.vue'
import AiHistory from './components/AiHistory.vue'
import { request } from '@/utils/request'

interface TodayStats {
  healthMetricsCount: number
  wellnessMetricsCount: number
  aiRecognizeCount: number
}

const todayHealthCount = ref(0)
const todayWellnessCount = ref(0)
const aiRecognizeCount = ref(0)
/** 首载尚未落定前也算「不可知」——0 不是真实值，只是计数器的初值。 */
const statsLoading = ref(true)
const statsFailed = ref(false)

const recentRecordsRef = ref()
const ocrHistoryRef = ref()
const aiHistoryRef = ref()

const activeHistory = ref<'records' | 'ocr' | 'ai'>('records')

/**
 * 一个计数卡片。
 *
 * `display` 是三态里唯一对外的东西：拿到数就是数字，拿不到就是破折号 —— 绝不让 0
 * 冒充「今天确实录了 0 条」。`numericValue` 只给入场 countUp 用（非数字时为 null）。
 */
interface OverviewStat {
  key: string
  label: string
  display: string
  numericValue: number | null
  icon: Component
}

// 三个数字都来自 /api/stats/today，只统计当日（健康/保健按 recordDate，AI 按 createTime）。
// 标签写明「今日」，与仪表盘那张全时段累计的「健康指标（累计）」区分开。
const overviewStats = computed<OverviewStat[]>(() => {
  const known = !statsLoading.value && !statsFailed.value
  const toStat = (key: string, label: string, count: number, icon: Component): OverviewStat => ({
    key,
    label,
    icon,
    display: known ? String(count) : '—',
    numericValue: known ? count : null,
  })
  return [
    toStat('health', '今日健康指标', todayHealthCount.value, DataLine),
    toStat('wellness', '今日保健指标', todayWellnessCount.value, Sunny),
    toStat('ai', '今日 AI 识别', aiRecognizeCount.value, MagicStick),
  ]
})

const loadTodayStats = async () => {
  statsLoading.value = true
  try {
    const res = await request.get<TodayStats>('/api/stats/today')
    if (res.code === 200 && res.data) {
      todayHealthCount.value = res.data.healthMetricsCount
      todayWellnessCount.value = res.data.wellnessMetricsCount
      aiRecognizeCount.value = res.data.aiRecognizeCount
      statsFailed.value = false
    } else {
      // 信封 200 但没有 data 同样是「拿不到数」，不能落回 0。
      statsFailed.value = true
    }
  } catch {
    // 统计不再是「装饰性数据」：失败时卡片显示破折号并由页面内提示承担呈现，
    // 不弹 toast（一次失败只呈现一次，重试入口就在提示里）。
    statsFailed.value = true
  } finally {
    statsLoading.value = false
  }
}

const refreshData = () => {
  loadTodayStats()
  recentRecordsRef.value?.refresh()
  ocrHistoryRef.value?.refresh()
  aiHistoryRef.value?.refresh()
}

const handleTabChange = (name: string | number) => {
  const target = name === 'ocr' ? ocrHistoryRef : name === 'ai' ? aiHistoryRef : recentRecordsRef
  target.value?.refresh?.()
}

onMounted(async () => {
  await loadTodayStats()

  // 概览卡：自下而上错峰入场
  staggerReveal('.overview-card', {
    y: 40, scale: 0.9, stagger: 0.08, duration: DUR.slow, ease: EASE.back,
  })
  hoverLift('.overview-card', { y: -4, scale: 1.02 })

  // 工作区：按视觉顺序（左上 -> 右上 -> 右下）依次浮现
  staggerReveal('.work-cell', {
    y: 28, scale: 0.96, stagger: 0.09, duration: DUR.mid, ease: EASE.expo,
  })

  // 历史区：最后进场，不与主工作区抢注意力
  staggerReveal('.history-card', {
    y: 24, scale: 0.98, scrollTrigger: true, duration: DUR.mid, ease: EASE.expo,
  })

  setTimeout(() => {
    document.querySelectorAll('.overview-value').forEach((el) => {
      const raw = el.getAttribute('data-target')
      // 破折号占位没有 data-target（Vue 会移除 null 属性）：跳过，绝不从占位符滚到 0。
      if (raw === null) return
      const num = parseInt(raw, 10)
      if (num > 0) countUp(el as HTMLElement, num, { duration: DUR.dramatic })
    })
  }, 500)
})

onUnmounted(() => { cleanupScrollTriggers() })
</script>

<style scoped>
.data-input-page {
  padding: 28px 32px 36px;
  max-width: var(--content-max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ── 概览：三等分，等高 ── */
.overview-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.overview-card {
  background: var(--surface-1) !important;
  border: 1px solid var(--border) !important;
  border-radius: var(--radius-lg) !important;
  box-shadow: none !important;
  transition: border-color var(--dur-mid) var(--ease),
              transform var(--dur-mid) var(--ease) !important;
}
.overview-card:hover { border-color: var(--border-strong) !important; }
.overview-card :deep(.el-card__body) { padding: 18px 20px; }

.overview-content { display: flex; align-items: center; gap: 14px; }
.overview-icon {
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  background: var(--accent-cool-soft);
  color: var(--accent-cool);
  transition: transform var(--dur-mid) var(--ease);
}
.overview-card:hover .overview-icon { transform: scale(1.08); }
.overview-info { display: flex; flex-direction: column; min-width: 0; }
.overview-value {
  font: 700 28px/1.15 var(--font-ui);
  color: var(--text-1);
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.overview-label { font: 400 13px/1.4 var(--font-ui); color: var(--text-3); margin-top: 2px; }

/* 统计失败提示：说明 + 重试入口。用错误文本 token，但保持为一条状态说明而不是卡片。 */
.overview-failed {
  margin: -6px 0 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font: 400 13px/1.5 var(--font-ui);
  color: var(--hhs-color-error-text);
}
.overview-failed :deep(.el-button) {
  font: 400 13px/1.5 var(--font-ui) !important;
  padding: 0;
  height: auto;
}

/* ── 工作区：左高 + 右侧两行 ── */
.work-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  grid-auto-rows: min-content;
  gap: 16px;
  align-items: start;
}
.work-cell { min-width: 0; display: flex; }
.work-cell > * { flex: 1 1 auto; min-width: 0; }
/* 左栏跨两行，但高度由自身内容决定；右栏两卡各占一行 */
.work-cell--tall { grid-row: span 2; align-self: stretch; }
.work-cell > :deep(.el-card) { display: flex; flex-direction: column; }
.work-cell--tall > :deep(.el-card) { height: 100%; }

/* ── 历史区 ── */
.history-card {
  border: 1px solid var(--border) !important;
  border-radius: var(--radius-lg) !important;
  box-shadow: none !important;
  background: var(--surface-1) !important;
}
.history-card :deep(.el-card__body) { padding: 4px 20px 16px; }
.history-tabs :deep(.el-tabs__header) { margin-bottom: 12px; }
.history-tabs :deep(.el-tabs__item) { font: 500 13px/1 var(--font-ui); }

/* ── 响应式：窄屏回到单列 ── */
@media (max-width: 1100px) {
  .work-grid { grid-template-columns: minmax(0, 1fr); }
  .work-cell--tall { grid-row: auto; }
}
@media (max-width: 768px) {
  .data-input-page { padding: 20px; }
  .overview-row { grid-template-columns: minmax(0, 1fr); }
}
</style>
