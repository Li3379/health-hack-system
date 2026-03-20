<template>
  <div class="risk-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>风险评估</span>
          <el-button type="primary" @click="createAssessment">
            <el-icon><Plus /></el-icon>
            创建评估
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="assessments" stripe>
        <el-table-column prop="diseaseName" label="疾病名称" width="150" />
        <el-table-column prop="riskLevel" label="风险等级" width="120">
          <template #default="{ row }">
            <el-tag :type="getRiskLevelType(row.riskLevel)">
              {{ getRiskLevelLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskScore" label="风险评分" width="120">
          <template #default="{ row }">
            {{ row.riskScore || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="suggestion" label="建议措施" min-width="300" show-overflow-tooltip />
        <el-table-column prop="createTime" label="评估时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchAssessments"
          @current-change="fetchAssessments"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="评估详情" width="600px">
      <el-descriptions v-if="currentAssessment" :column="1" border>
        <el-descriptions-item label="疾病名称">
          {{ currentAssessment.diseaseName }}
        </el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="getRiskLevelType(currentAssessment.riskLevel)">
            {{ getRiskLevelLabel(currentAssessment.riskLevel) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="风险评分">
          {{ currentAssessment.riskScore || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="建议措施">
          {{ currentAssessment.suggestion || '暂无建议' }}
        </el-descriptions-item>
        <el-descriptions-item label="评估时间">
          {{ formatDateTime(currentAssessment.createTime) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { preventionApi } from '@/api/prevention'
import { formatDateTime } from '@/utils/format'
import type { RiskAssessmentVO } from '@/types/api'

const loading = ref(false)
const detailVisible = ref(false)
const assessments = ref<RiskAssessmentVO[]>([])
const currentAssessment = ref<RiskAssessmentVO | null>(null)

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const getRiskLevelType = (level: string): string => {
  const types: Record<string, string> = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger'
  }
  return types[level] || 'info'
}

const getRiskLevelLabel = (level: string): string => {
  const labels: Record<string, string> = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险'
  }
  return labels[level] || level
}

const fetchAssessments = async () => {
  loading.value = true
  try {
    const res = await preventionApi.getRiskAssessments({
      page: pagination.page,
      size: pagination.size
    })
    // 后端返回数组，不是分页对象
    const data = Array.isArray(res.data) ? res.data : []
    assessments.value = data
    pagination.total = data.length
  } catch (error) {
    ElMessage.error('获取评估列表失败')
  } finally {
    loading.value = false
  }
}

const createAssessment = async () => {
  try {
    await preventionApi.createRiskAssessment()
    ElMessage.success('评估创建成功')
    fetchAssessments()
  } catch (error) {
    ElMessage.error('创建评估失败')
  }
}

const viewDetail = (row: RiskAssessmentVO) => {
  currentAssessment.value = row
  detailVisible.value = true
}

onMounted(() => {
  fetchAssessments()
})
</script>

<style scoped>
.risk-page {
  padding: 20px;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
