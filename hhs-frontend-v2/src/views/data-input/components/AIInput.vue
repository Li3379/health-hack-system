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
      <Transition name="form-section">
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
      </Transition>

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
import { ref, onMounted, onUnmounted } from 'vue'
import { cleanupScrollTriggers } from '@/composables/useGsap'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { request } from '@/utils/request'
import { showError } from '@/utils/showError'

// 失败呈现约定（本文件）：`@/utils/request` 的响应拦截器在信封 code !== 200 时
// 直接 reject，因此 `if (res.code === 200)` 之后不需要「非 200」分支 —— 那种分支
// 永远不可达。所有失败只从 catch 进入 `showError` 单一出口。
// 本文件不渲染任何状态块，因此每个调用都显式 opt-in 状态类呈现。

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

/**
 * 确认录入响应。
 *
 * 后端只回报 savedIds 时，「成功录入 N 个」会把选中了 3 个、只存了 2 个的结果说成成功；
 * 现在一并返回 failedMetrics，前端才能说出哪几条没存上，并保留面板让用户只补录缺的。
 */
interface ConfirmSaveResult {
  savedIds: number[]
  failedMetrics: string[]
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
    // remaining count fetch failed
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
    }
  } catch (error) {
    showError(error, 'ai.parseFailed', { presentStates: true })
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

    const res = await request.post<ConfirmSaveResult>('/api/ai/confirm-metrics', requestBody)

    if (res.code === 200 && res.data) {
      const savedCount = res.data.savedIds?.length ?? 0
      const failed = res.data.failedMetrics ?? []

      // 全部成功：照旧提示并清空面板。与 OcrInput 同构，写成「先处理成功再 return」，
      // 避免在确认路径上出现 else 兜底分支。
      if (failed.length === 0) {
        ElMessage.success(`成功录入 ${savedCount} 个指标`)
        parseResult.value = null
        inputText.value = ''
        emit('refresh')
        return
      }

      // 部分失败：不能报「成功录入」，也不能清空面板 —— 后端的记录此时仍是未确认，
      // 用户按重新勾选的失败项补录即可（已写入的会被同日同值覆盖，不会重复）。
      ElMessage.warning(`成功录入 ${savedCount} 个指标，${failed.length} 个未保存：${failed.join('、')}`)
      const failedSet = new Set(failed)
      parseResult.value.metrics.forEach(m => {
        m.selected = failedSet.has(m.metricName) || failedSet.has(m.metricKey)
      })
      emit('refresh')
    }
  } catch (error) {
    showError(error, 'metric.saveFailed', { presentStates: true })
  } finally {
    confirming.value = false
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
onUnmounted(() => { cleanupScrollTriggers() })
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
