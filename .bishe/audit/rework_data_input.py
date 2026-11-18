"""Rewrite data-input page: 3-up stat row, balanced 2x2 work area, staged entrance."""

import re
from pathlib import Path

P = Path(r"D:\Programming File\IDEA\health-hack-system\hhs-frontend-v2\src\views\data-input\Index.vue")
src = P.read_text(encoding="utf-8")

TEMPLATE = '''<template>
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
            <span class="overview-value" :data-target="stat.value">{{ stat.value }}</span>
            <span class="overview-label">{{ stat.label }}</span>
          </div>
        </div>
      </el-card>
    </div>

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
'''

script_start = src.index("<script setup")
script_end = src.index("</script>") + len("</script>")
old_script = src[script_start:script_end]

new_script = '''<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
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

const recentRecordsRef = ref()
const ocrHistoryRef = ref()
const aiHistoryRef = ref()

const activeHistory = ref<'records' | 'ocr' | 'ai'>('records')

const overviewStats = computed(() => [
  { key: 'health', label: '健康指标', value: todayHealthCount.value, icon: DataLine },
  { key: 'wellness', label: '保健指标', value: todayWellnessCount.value, icon: Sunny },
  { key: 'ai', label: 'AI 识别', value: aiRecognizeCount.value, icon: MagicStick },
])

const loadTodayStats = async () => {
  try {
    const res = await request.get<TodayStats>('/api/stats/today')
    if (res.code === 200 && res.data) {
      todayHealthCount.value = res.data.healthMetricsCount
      todayWellnessCount.value = res.data.wellnessMetricsCount
      aiRecognizeCount.value = res.data.aiRecognizeCount
    }
  } catch {
    // 统计是装饰性数据，失败时保留 0，不打扰用户主流程
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
      const num = parseInt(el.getAttribute('data-target') ?? '0', 10)
      if (num > 0) countUp(el as HTMLElement, num, { duration: DUR.dramatic })
    })
  }, 500)
})

onUnmounted(() => { cleanupScrollTriggers() })
</script>'''

STYLE = '''
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

/* ── 工作区：左高 + 右侧两行 ── */
.work-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}
.work-cell { min-width: 0; display: flex; }
.work-cell > * { flex: 1 1 auto; min-width: 0; }
.work-cell--tall { grid-row: span 2; }
.work-cell > :deep(.el-card) { height: 100%; display: flex; flex-direction: column; }
.work-cell > :deep(.el-card__body) { flex: 1 1 auto; }

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
'''

out = TEMPLATE + "\n" + new_script + "\n" + STYLE
P.write_text(out, encoding="utf-8")
print("data-input/Index.vue rewritten:", len(src), "->", len(out))