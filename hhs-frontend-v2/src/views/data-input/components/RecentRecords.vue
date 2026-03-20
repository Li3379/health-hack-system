<template>
  <el-card shadow="hover" class="recent-records-card">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <el-icon><Clock /></el-icon>
          <span>最近录入记录</span>
        </div>
        <el-button type="primary" text size="small" @click="refresh">刷新</el-button>
      </div>
    </template>

    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column label="时间" width="100">
        <template #default="{ row }">
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="指标" width="120">
        <template #default="{ row }">
          {{ row.metricName || row.metricKey }}
        </template>
      </el-table-column>
      <el-table-column label="数值">
        <template #default="{ row }">{{ row.value }} {{ row.unit }}</template>
      </el-table-column>
      <el-table-column label="来源" width="100">
        <template #default="{ row }">
          <el-tag :type="getSourceType(row.source)" size="small">
            {{ getSourceName(row.source) }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Clock } from '@element-plus/icons-vue'
import { healthApi } from '@/api/health'
import { wellnessApi } from '@/api/wellness'

const loading = ref(false)
const records = ref<any[]>([])

/**
 * 加载最近录入记录（包括健康指标和保健指标）
 */
const loadRecords = async () => {
  loading.value = true
  try {
    // 并行请求健康指标和保健指标
    const [healthRes, wellnessRes] = await Promise.all([
      healthApi.getMetrics({ page: 1, size: 10 }),
      wellnessApi.getMetrics({ page: 1, size: 10 })
    ])

    // 合并两种指标记录
    const healthRecords = (healthRes.code === 200 ? healthRes.data?.records || [] : []).map(
      (r: any) => ({
        ...r,
        category: 'HEALTH'
      })
    )
    const wellnessRecords = (wellnessRes.code === 200 ? wellnessRes.data?.records || [] : []).map(
      (r: any) => ({
        ...r,
        category: 'WELLNESS'
      })
    )

    // 按时间排序并取前10条
    const allRecords = [...healthRecords, ...wellnessRecords]
      .sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime())
      .slice(0, 10)

    records.value = allRecords
  } catch (error) {
    console.error('加载记录失败', error)
  } finally {
    loading.value = false
  }
}

const refresh = () => {
  loadRecords()
}

const formatTime = (time: string) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const getSourceType = (source: string) => {
  switch (source) {
    case 'device':
      return 'warning'
    case 'ai':
      return 'success'
    case 'ocr':
      return 'info'
    default:
      return ''
  }
}

const getSourceName = (source: string) => {
  switch (source) {
    case 'device':
      return '设备同步'
    case 'ai':
      return 'AI识别'
    case 'ocr':
      return 'OCR识别'
    default:
      return '手动录入'
  }
}

onMounted(() => {
  loadRecords()
})

defineExpose({ refresh })
</script>

<style scoped>
.recent-records-card {
  margin-top: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
</style>
