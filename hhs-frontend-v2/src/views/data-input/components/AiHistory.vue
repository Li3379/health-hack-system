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
    console.error('加载AI解析历史失败', error)
    ElMessage.error('加载历史记录失败')
  } finally {
    loading.value = false
  }
}

/**
 * 查看详情
 */
const viewDetail = async (record: AiParseHistoryItem) => {
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null

  try {
    const res = await aiParseApi.getHistoryDetail(record.id)
    if (res.code === 200 && res.data) {
      detailData.value = res.data
    } else {
      ElMessage.error(res.message || '获取详情失败')
    }
  } catch (error) {
    console.error('获取AI解析详情失败', error)
    ElMessage.error('获取详情失败')
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
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    console.error('删除AI解析记录失败', error)
    ElMessage.error('删除失败')
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
