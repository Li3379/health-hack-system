<template>
  <el-card shadow="hover" class="ai-history-card">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI 解析历史</span>
        </div>
        <el-button type="primary" text size="small" @click="loadHistory">刷新</el-button>
      </div>
    </template>

    <el-table v-loading="loading" :data="historyRecords" stripe empty-text="暂无解析记录">
      <el-table-column label="输入内容" min-width="200">
        <template #default="{ row }">
          <el-tooltip :content="row.inputText" placement="top" :show-after="500">
            <span class="input-text">{{ truncateText(row.inputText) }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.inputType === 'voice' ? 'warning' : 'primary'" size="small">
            {{ row.inputTypeDisplay || row.inputType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="指标数" width="80">
        <template #default="{ row }">
          <span>{{ row.metricsCount || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="确认状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.confirmed ? 'success' : 'info'" size="small">
            {{ row.confirmed ? '已录入' : '未确认' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="150">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="viewDetail(row)">查看</el-button>
          <el-popconfirm
            title="确定删除此记录？"
            confirm-button-text="确定"
            cancel-button-text="取消"
            @confirm="handleDelete(row.id)"
          >
            <template #reference>
              <el-button type="danger" text size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div v-if="total > 0" class="pagination-container">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        small
        @current-change="handlePageChange"
      />
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="AI 解析详情" width="600px" destroy-on-close>
      <div v-if="detailLoading" class="detail-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else-if="detailData">
        <div class="detail-summary">
          <el-text>{{ detailData.summary || '解析完成' }}</el-text>
        </div>

        <div v-if="detailData.warnings?.length" class="detail-warnings">
          <el-alert
            v-for="(warning, index) in detailData.warnings"
            :key="index"
            :title="warning"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>

        <div v-if="detailData.metrics?.length" class="detail-metrics">
          <h4>解析指标</h4>
          <el-table :data="detailData.metrics" stripe size="small">
            <el-table-column prop="metricName" label="指标" width="100" />
            <el-table-column label="数值" width="120">
              <template #default="{ row }">{{ row.value }} {{ row.unit }}</template>
            </el-table-column>
            <el-table-column label="分类" width="80">
              <template #default="{ row }">
                <el-tag :type="row.category === 'HEALTH' ? 'primary' : 'success'" size="small">
                  {{ row.category === 'HEALTH' ? '健康' : '保健' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="置信度" width="80">
              <template #default="{ row }">
                {{ Math.round((row.confidence || 0.9) * 100) }}%
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-empty v-else description="无解析结果" />
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Loading } from '@element-plus/icons-vue'
import { aiParseApi, type AiParseHistoryItem, type AiParseDetail } from '@/api/ai-parse'
import { showError } from '@/utils/showError'

// 失败呈现约定（本文件）：`@/utils/request` 的响应拦截器在信封 code !== 200 时
// 直接 reject，因此 `if (res.code === 200)` 之后不需要「非 200」分支。
// 本文件不渲染任何状态块，因此每个调用都显式 opt-in 状态类呈现 ——
// `AiParseServiceImpl:749`（详情）与 `:783`（删除）抛 RESOURCE_NOT_FOUND、
// `:776` 抛 AUTH_FORBIDDEN；缺第三个参数会让「查看已被删除的记录」彻底静默。

const loading = ref(false)
const historyRecords = ref<AiParseHistoryItem[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<AiParseDetail | null>(null)

/**
 * 加载AI解析历史记录
 */
const loadHistory = async () => {
  loading.value = true
  try {
    const res = await aiParseApi.getHistory(currentPage.value, pageSize.value)
    if (res.code === 200 && res.data) {
      historyRecords.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (error) {
    // 表格保留原有行（UI Considerations · error E7）：失败不清空 historyRecords。
    showError(error, 'ai.loadFailed', { presentStates: true })
  } finally {
    loading.value = false
  }
}

/**
 * 查看详情
 *
 * <p>打开即清空 detailData 是本函数既有的时序（先清、再请求），保留原样：
 * 失败时不清空刚拿到的东西只对「已有内容」有意义，而这里打开弹窗时本就没有内容。
 * 失败后弹窗保持打开，列表与分页状态不动（T-14-31：不得因一次详情失败打掉列表）。
 */
const viewDetail = async (record: AiParseHistoryItem) => {
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null

  try {
    const res = await aiParseApi.getHistoryDetail(record.id)
    if (res.code === 200 && res.data) {
      detailData.value = res.data
    }
  } catch (error) {
    // 键复用：字典无 ai.detailFailed，「无法加载 AI 会话」对一次详情读取贴切。
    // 这里刻意不实现 404 内容块 —— 480px 的块会占满这个 600px 的小弹窗；
    // 弹窗内的内容块是刻意推迟的后续项，不是遗漏。
    showError(error, 'ai.loadFailed', { presentStates: true })
  } finally {
    detailLoading.value = false
  }
}

/**
 * 删除记录
 */
const handleDelete = async (recordId: number) => {
  try {
    const res = await aiParseApi.deleteHistory(recordId)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadHistory()
    }
  } catch (error) {
    showError(error, 'ai.deleteFailed', { presentStates: true })
  }
}

/**
 * 分页切换
 */
const handlePageChange = (page: number) => {
  currentPage.value = page
  loadHistory()
}

/**
 * 格式化日期时间
 */
const formatDateTime = (datetime: string) => {
  if (!datetime) return ''
  const date = new Date(datetime)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

/**
 * 截断文本
 */
const truncateText = (text: string | null | undefined) => {
  if (!text) return '-'
  if (text.length <= 30) return text
  return text.substring(0, 27) + '...'
}

onMounted(() => {
  loadHistory()
})

defineExpose({ refresh: loadHistory })
</script>

<style scoped>
.ai-history-card {
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

.input-text {
  color: var(--color-text-secondary);
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: var(--color-text-secondary);
}

.detail-summary {
  margin-bottom: 16px;
}

.detail-warnings {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-metrics h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 500;
}
</style>
