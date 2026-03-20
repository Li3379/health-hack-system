<template>
  <el-card shadow="hover" class="ai-input-card">
    <template #header>
      <div class="card-header">
        <el-icon><MagicStick /></el-icon>
        <span>AI 智能录入</span>
        <el-tag v-if="remainingCount !== null" type="info" size="small" class="remaining-tag">
          今日剩余: {{ remainingCount }} 次
        </el-tag>
      </div>
    </template>

    <div class="ai-input-container">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="3"
        placeholder="输入描述，如：今天血糖5.6，心率72，走了8000步"
        :disabled="parsing"
        maxlength="2000"
        show-word-limit
      />

      <div class="input-actions">
        <el-button
          type="primary"
          :loading="parsing"
          :disabled="!inputText.trim() || inputText.length > 2000"
          @click="handleParse"
        >
          <el-icon v-if="!parsing"><MagicStick /></el-icon>
          智能解析
        </el-button>
      </div>

      <!-- 解析结果 -->
      <div v-if="parseResult" class="parse-result">
        <div class="result-header">
          <span>解析结果</span>
          <el-tag v-if="parseResult.summary" type="info" size="small">
            {{ parseResult.summary }}
          </el-tag>
        </div>

        <el-table :data="parseResult.metrics" stripe size="small">
          <el-table-column width="50">
            <template #default="{ row }">
              <el-checkbox v-model="row.selected" />
            </template>
          </el-table-column>
          <el-table-column prop="metricName" label="指标" width="80" />
          <el-table-column label="数值" width="120">
            <template #default="{ row }">
              <span>{{ row.value }} {{ row.unit }}</span>
            </template>
          </el-table-column>
          <el-table-column label="置信度" width="100">
            <template #default="{ row }">
              <el-progress
                :percentage="Math.round((row.confidence || 0) * 100)"
                :stroke-width="6"
                :color="getConfidenceColor(row.confidence)"
              />
            </template>
          </el-table-column>
        </el-table>

        <div class="result-actions">
          <el-button type="primary" :loading="confirming" @click="handleConfirm">
            确认录入
          </el-button>
        </div>
      </div>

      <!-- 警告信息 -->
      <el-alert
        v-if="parseResult?.warnings?.length"
        type="warning"
        :closable="false"
        class="warnings"
      >
        <template #title>
          <ul class="warning-list">
            <li v-for="(warning, index) in parseResult.warnings" :key="index">
              {{ warning }}
            </li>
          </ul>
        </template>
      </el-alert>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { request } from '@/utils/request'

// ============================================
// TypeScript 接口定义
// ============================================

/**
 * 解析出的单个指标
 */
interface ParsedMetric {
  metricKey: string
  metricName: string
  value: number
  unit: string
  confidence: number
  category: 'HEALTH' | 'WELLNESS'
  recordDate: string
  selected: boolean
}

/**
 * AI解析结果
 */
interface ParseResult {
  metrics: ParsedMetric[]
  summary: string
  warnings: string[]
  parseHistoryId: number
  remainingCount: number
}

/**
 * 确认录入请求
 */
interface ConfirmMetricsRequest {
  parseHistoryId: number
  metrics: ParsedMetric[]
}

// ============================================
// 组件逻辑
// ============================================

const emit = defineEmits<{
  (e: 'refresh'): void
}>()

const inputText = ref('')
const parsing = ref(false)
const confirming = ref(false)
const parseResult = ref<ParseResult | null>(null)
const remainingCount = ref<number | null>(null)

/**
 * 获取剩余次数
 */
const fetchRemainingCount = async () => {
  try {
    const res = await request.get<number>('/api/ai/parse-metrics/remaining')
    if (res.code === 200) {
      remainingCount.value = res.data
    }
  } catch (error) {
    console.error('获取剩余次数失败:', error)
  }
}

/**
 * 处理解析
 */
const handleParse = async () => {
  if (!inputText.value.trim()) return

  // 检查剩余次数
  if (remainingCount.value !== null && remainingCount.value <= 0) {
    ElMessage.warning('今日AI解析次数已用完，请明天再试')
    return
  }

  parsing.value = true
  try {
    const res = await request.post<ParseResult>('/api/ai/parse-metrics', {
      input: inputText.value,
      inputType: 'text'
    })

    if (res.code === 200 && res.data) {
      parseResult.value = res.data
      // 更新剩余次数
      remainingCount.value = res.data.remainingCount
      // 默认选中所有指标
      res.data.metrics?.forEach((m: ParsedMetric) => {
        m.selected = true
      })
    } else {
      ElMessage.error(res.message || '解析失败')
    }
  } catch (error: unknown) {
    handleApiError(error, '解析失败')
  } finally {
    parsing.value = false
  }
}

/**
 * 处理确认录入
 */
const handleConfirm = async () => {
  if (!parseResult.value?.metrics?.length) return

  const selectedMetrics = parseResult.value.metrics.filter((m: ParsedMetric) => m.selected)

  if (!selectedMetrics.length) {
    ElMessage.warning('请至少选择一个指标')
    return
  }

  confirming.value = true
  try {
    const requestBody: ConfirmMetricsRequest = {
      parseHistoryId: parseResult.value.parseHistoryId,
      metrics: selectedMetrics
    }

    const res = await request.post<number[]>('/api/ai/confirm-metrics', requestBody)

    if (res.code === 200 && res.data) {
      ElMessage.success(`成功录入 ${res.data.length} 个指标`)
      parseResult.value = null
      inputText.value = ''
      emit('refresh')
    } else {
      ElMessage.error(res.message || '录入失败')
    }
  } catch (error: unknown) {
    handleApiError(error, '录入失败')
  } finally {
    confirming.value = false
  }
}

/**
 * 统一错误处理
 */
const handleApiError = (error: unknown, defaultMessage: string) => {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosError = error as { response?: { data?: { message?: string }; status?: number } }
    if (axiosError.response?.data?.message) {
      ElMessage.error(axiosError.response.data.message)
      return
    }
    if (axiosError.response?.status === 429) {
      ElMessage.error('请求过于频繁，请稍后再试')
      return
    }
  }
  if (error instanceof Error) {
    ElMessage.error(error.message || defaultMessage)
  } else {
    ElMessage.error(defaultMessage)
  }
}

/**
 * 获取置信度颜色
 */
const getConfidenceColor = (confidence: number): string => {
  if (confidence >= 0.9) return '#67c23a'
  if (confidence >= 0.7) return '#e6a23c'
  return '#f56c6c'
}

// 组件挂载时获取剩余次数
onMounted(() => {
  fetchRemainingCount()
})
</script>

<style scoped>
.ai-input-card {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.remaining-tag {
  margin-left: auto;
}

.ai-input-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
}

.parse-result {
  border-top: 1px solid var(--color-border);
  padding-top: 16px;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 500;
}

.result-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.warnings {
  margin-top: 12px;
}

.warning-list {
  margin: 0;
  padding-left: 20px;
}
</style>
