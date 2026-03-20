<template>
  <el-card shadow="hover" class="quick-input-card">
    <template #header>
      <div class="card-header">
        <el-icon><Edit /></el-icon>
        <span>快捷录入</span>
      </div>
    </template>

    <div class="quick-buttons">
      <!-- 健康指标 -->
      <div class="metric-group">
        <div class="group-title">健康指标</div>
        <div class="button-grid">
          <el-button
            v-for="metric in healthMetrics"
            :key="metric.key"
            :icon="metric.icon"
            @click="openInputDialog(metric)"
          >
            {{ metric.name }}
          </el-button>
        </div>
      </div>

      <!-- 保健指标 -->
      <div class="metric-group">
        <div class="group-title">保健指标</div>
        <div class="button-grid">
          <el-button
            v-for="metric in wellnessMetrics"
            :key="metric.key"
            :icon="metric.icon"
            type="success"
            plain
            @click="openInputDialog(metric)"
          >
            {{ metric.name }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 快捷录入弹窗 -->
    <QuickInputDialog v-model="dialogVisible" :metric="selectedMetric" @success="handleSuccess" />
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Edit, Timer, Histogram, Sunrise, Moon, Sunny } from '@element-plus/icons-vue'
import QuickInputDialog from './QuickInputDialog.vue'

const emit = defineEmits<{
  (e: 'refresh'): void
}>()

const dialogVisible = ref(false)
const selectedMetric = ref<any>(null)

// 健康指标
const healthMetrics = [
  { key: 'glucose', name: '血糖', icon: Timer, unit: 'mmol/L', category: 'HEALTH' },
  { key: 'systolicBP', name: '血压', icon: Histogram, unit: 'mmHg', category: 'HEALTH' },
  { key: 'heartRate', name: '心率', icon: Sunrise, unit: '次/分', category: 'HEALTH' },
  { key: 'temperature', name: '体温', icon: Sunny, unit: '°C', category: 'HEALTH' },
  { key: 'weight', name: '体重', icon: Histogram, unit: 'kg', category: 'HEALTH' },
  { key: 'bmi', name: 'BMI', icon: Histogram, unit: '', category: 'HEALTH' }
]

// 保健指标
const wellnessMetrics = [
  { key: 'sleepDuration', name: '睡眠', icon: Moon, unit: '小时', category: 'WELLNESS' },
  { key: 'steps', name: '步数', icon: Histogram, unit: '步', category: 'WELLNESS' },
  { key: 'waterIntake', name: '饮水', icon: Timer, unit: 'ml', category: 'WELLNESS' },
  { key: 'mood', name: '心情', icon: Sunny, unit: '', category: 'WELLNESS' },
  { key: 'energy', name: '精力', icon: Sunrise, unit: '', category: 'WELLNESS' },
  { key: 'exerciseMinutes', name: '运动', icon: Timer, unit: '分钟', category: 'WELLNESS' }
]

const openInputDialog = (metric: any) => {
  selectedMetric.value = metric
  dialogVisible.value = true
}

const handleSuccess = () => {
  emit('refresh')
}
</script>

<style scoped>
.quick-input-card {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.quick-buttons {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.metric-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.group-title {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.button-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.button-grid .el-button {
  width: 100%;
}

@media (max-width: 768px) {
  .button-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
