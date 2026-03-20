<template>
  <div class="screening-detail-page">
    <el-page-header title="返回" @back="goBack">
      <template #content>
        <span class="page-title">体检报告详情</span>
      </template>
    </el-page-header>

    <el-card v-loading="loading" class="detail-card">
      <template #header>
        <div class="header-info">
          <span>{{ report?.reportName }}</span>
          <el-tag v-if="report" :type="getOcrStatusType(report.ocrStatus)">
            {{ getOcrStatusLabel(report.ocrStatus) }}
          </el-tag>
        </div>
      </template>

      <el-descriptions v-if="report" :column="2" border>
        <el-descriptions-item label="报告名称">
          {{ report.reportName }}
        </el-descriptions-item>
        <el-descriptions-item label="体检机构">
          {{ report.institution || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="体检日期">
          {{ report.reportDate || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="报告类型">
          {{ report.reportType || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="上传时间">
          {{ formatDateTime(report.createTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ formatDateTime(report.updateTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="异常摘要" :span="2">
          {{ report.abnormalSummary || '暂无异常' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 检验指标 -->
    <el-card v-if="labResults.length > 0" class="results-card">
      <template #header>
        <span>检验指标</span>
      </template>
      <el-table :data="labResults" stripe>
        <el-table-column prop="name" label="指标名称" width="200" />
        <el-table-column prop="category" label="类别" width="120" />
        <el-table-column prop="value" label="检测值" width="120">
          <template #default="{ row }">
            <span :class="{ 'abnormal-value': row.isAbnormal }">
              {{ row.value }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="100" />
        <el-table-column prop="referenceRange" label="参考范围" width="150" />
        <el-table-column prop="isAbnormal" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isAbnormal" type="danger" size="small">异常</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="trend" label="趋势" width="100">
          <template #default="{ row }">
            <span v-if="row.trend">
              <el-icon v-if="row.trend === 'UP'" color="#f56c6c"><Top /></el-icon>
              <el-icon v-else-if="row.trend === 'DOWN'" color="#67c23a"><Bottom /></el-icon>
              <el-icon v-else color="#909399"><Minus /></el-icon>
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 报告文件预览 -->
    <el-card v-if="report?.fileUrl" class="preview-card">
      <template #header>
        <span>报告文件</span>
      </template>
      <div class="file-preview">
        <el-image
          v-if="isImage(report.fileUrl)"
          :src="report.fileUrl"
          fit="contain"
          :preview-src-list="[report.fileUrl]"
        />
        <div v-else class="pdf-preview">
          <el-icon :size="64" color="#909399"><Document /></el-icon>
          <p>PDF文件</p>
          <el-button type="primary" @click="downloadFile">下载查看</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { screeningApi } from '@/api/screening'
import { formatDateTime } from '@/utils/format'
import type { ExaminationReportVO, LabResultVO } from '@/types/api'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const report = ref<ExaminationReportVO | null>(null)
const labResults = ref<LabResultVO[]>([])

const reportId = parseInt(route.params.id as string)

const getOcrStatusType = (status: string): string => {
  const types: Record<string, string> = {
    COMPLETED: 'success',
    SUCCESS: 'success',
    PROCESSING: 'warning',
    FAILED: 'danger',
    PENDING: 'info'
  }
  return types[status] || 'info'
}

const getOcrStatusLabel = (status: string): string => {
  const labels: Record<string, string> = {
    COMPLETED: '已识别',
    SUCCESS: '已识别',
    PROCESSING: '识别中',
    FAILED: '识别失败',
    PENDING: '待识别'
  }
  return labels[status] || status
}

const isImage = (url: string): boolean => {
  return /\.(jpg|jpeg|png|gif|bmp|webp)$/i.test(url)
}

const fetchReportDetail = async () => {
  loading.value = true
  try {
    const res = await screeningApi.getReportDetail(reportId)
    report.value = res.data
  } catch (error) {
    ElMessage.error('获取报告详情失败')
  } finally {
    loading.value = false
  }
}

const fetchLabResults = async () => {
  try {
    const res = await screeningApi.getLabResults(reportId)
    labResults.value = res.data
  } catch (error) {
    console.error('Failed to fetch lab results:', error)
  }
}

const downloadFile = () => {
  if (report.value?.fileUrl) {
    window.open(report.value.fileUrl, '_blank')
  }
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  fetchReportDetail()
  fetchLabResults()
})
</script>

<style scoped>
.screening-detail-page {
  padding: 20px;
}

.page-title {
  font-size: 18px;
  font-weight: 500;
}

.detail-card,
.results-card,
.preview-card {
  margin-top: 20px;
}

.header-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.abnormal-value {
  color: #f56c6c;
  font-weight: 500;
}

.file-preview {
  text-align: center;
}

.pdf-preview {
  padding: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.pdf-preview p {
  font-size: 16px;
  color: #606266;
}
</style>
