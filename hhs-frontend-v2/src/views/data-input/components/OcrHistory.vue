<template>
  <el-card shadow="hover" class="ocr-history-card">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <el-icon><Clock /></el-icon>
          <span>OCR 识别历史</span>
        </div>
        <el-button type="primary" text size="small" @click="loadHistory">刷新</el-button>
      </div>
    </template>

    <el-table v-loading="loading" :data="historyRecords" stripe empty-text="暂无识别记录">
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="getOcrTypeTag(row.ocrType)" size="small">
            {{ row.ocrTypeDisplay || row.ocrType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">
            {{ row.status === 'success' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="指标数" width="80">
        <template #default="{ row }">
          <span>{{ row.metricsCount || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="文件名" min-width="150">
        <template #default="{ row }">
          <el-tooltip :content="row.originalFilename" placement="top" :show-after="500">
            <span class="filename-text">{{ truncateFilename(row.originalFilename) }}</span>
          </el-tooltip>
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
    <el-dialog v-model="detailVisible" title="OCR 识别详情" width="600px" destroy-on-close>
      <div v-if="detailLoading" class="detail-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else-if="detailData">
        <div class="detail-header">
          <el-tag :type="getOcrTypeTag(detailData.ocrType)">
            {{ getOcrTypeName(detailData.ocrType) }}
          </el-tag>
          <el-tag :type="detailData.status === 'success' ? 'success' : 'danger'">
            {{ detailData.status === 'success' ? '识别成功' : '识别失败' }}
          </el-tag>
        </div>

        <div v-if="detailData.errorMessage" class="detail-error">
          <el-alert type="error" :closable="false">
            {{ detailData.errorMessage }}
          </el-alert>
        </div>

        <div v-if="detailData.metrics?.length" class="detail-metrics">
          <h4>识别指标</h4>
          <el-table :data="detailData.metrics" stripe size="small">
            <el-table-column prop="name" label="指标" width="100" />
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

        <el-collapse v-if="detailData.rawText" class="detail-raw-text">
          <el-collapse-item title="原始识别文本" name="rawText">
            <pre class="raw-text-content">{{ detailData.rawText }}</pre>
          </el-collapse-item>
        </el-collapse>
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
import { Clock, Loading } from '@element-plus/icons-vue'
import { ocrApi, type OcrHistoryItem, type OcrRecordDetail } from '@/api/ocr'

const loading = ref(false)
const historyRecords = ref<OcrHistoryItem[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<OcrRecordDetail | null>(null)

/**
 * 加载OCR历史记录
 */
const loadHistory = async () => {
  loading.value = true
  try {
    const res = await ocrApi.getHistory(currentPage.value, pageSize.value)
    if (res.code === 200 && res.data) {
      historyRecords.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (error) {
    console.error('加载OCR历史失败', error)
    ElMessage.error('加载历史记录失败')
  } finally {
    loading.value = false
  }
}

/**
 * 查看详情
 */
const viewDetail = async (record: OcrHistoryItem) => {
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null

  try {
    const res = await ocrApi.getRecordDetail(record.id)
    if (res.code === 200 && res.data) {
      detailData.value = res.data
    } else {
      ElMessage.error(res.message || '获取详情失败')
    }
  } catch (error) {
    console.error('获取OCR详情失败', error)
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
    const res = await ocrApi.deleteRecord(recordId)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadHistory()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    console.error('删除OCR记录失败', error)
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
 * 截断文件名
 */
const truncateFilename = (filename: string | null | undefined) => {
  if (!filename) return '-'
  if (filename.length <= 20) return filename
  const ext = filename.substring(filename.lastIndexOf('.'))
  return filename.substring(0, 16) + '...' + ext
}

/**
 * 获取OCR类型标签颜色
 */
const getOcrTypeTag = (type: string) => {
  switch (type) {
    case 'report':
      return 'primary'
    case 'medicine':
      return 'warning'
    case 'nutrition':
      return 'success'
    default:
      return 'info'
  }
}

/**
 * 获取OCR类型名称
 */
const getOcrTypeName = (type: string) => {
  switch (type) {
    case 'report':
      return '体检报告'
    case 'medicine':
      return '药品标签'
    case 'nutrition':
      return '营养标签'
    default:
      return type
  }
}

onMounted(() => {
  loadHistory()
})

defineExpose({ refresh: loadHistory })
</script>

<style scoped>
.ocr-history-card {
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

.filename-text {
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

.detail-header {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.detail-error {
  margin-bottom: 16px;
}

.detail-metrics h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 500;
}

.detail-raw-text {
  margin-top: 16px;
}

.raw-text-content {
  margin: 0;
  padding: 12px;
  background: var(--color-bg-secondary);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}
</style>
