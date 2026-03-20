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
import { ref, onMounted } from 'vue'
import { DataLine, Sunny, Connection, MagicStick } from '@element-plus/icons-vue'
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
    console.error('加载统计数据失败', error)
  }
}

// 刷新数据
const refreshData = () => {
  loadTodayStats()
  if (recentRecordsRef.value) {
    recentRecordsRef.value.refresh()
  }
}

onMounted(() => {
  loadTodayStats()
})
</script>

<style scoped>
.data-input-page {
  padding: 20px;
}

.overview-row {
  margin-bottom: 20px;
}

.overview-card {
  border-radius: 12px;
}

.overview-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.overview-icon {
  font-size: 40px;
  padding: 12px;
  border-radius: 12px;
}

.overview-icon.health {
  background: rgba(64, 158, 255, 0.1);
  color: #409eff;
}

.overview-icon.wellness {
  background: rgba(103, 194, 58, 0.1);
  color: #67c23a;
}

.overview-icon.sync {
  background: rgba(230, 162, 60, 0.1);
  color: #e6a23c;
}

.overview-icon.ai {
  background: rgba(144, 97, 249, 0.1);
  color: #9061f9;
}

.overview-info {
  display: flex;
  flex-direction: column;
}

.overview-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.overview-label {
  font-size: 14px;
  color: var(--color-text-secondary);
}
</style>
