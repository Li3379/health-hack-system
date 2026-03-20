<template>
  <div class="screening-list-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>体检报告</span>
          <el-button type="primary" @click="showUploadDialog">
            <el-icon><Upload /></el-icon>
            上传报告
          </el-button>
        </div>
      </template>

      <!-- 报告列表 -->
      <el-row :gutter="20">
        <el-col v-for="report in reports" :key="report.id" :xs="24" :sm="12" :md="8" :lg="6">
          <el-card class="report-card" shadow="hover" @click="viewDetail(report.id)">
            <div class="report-icon">
              <el-icon :size="48" color="#409eff"><Document /></el-icon>
            </div>
            <div class="report-info">
              <div class="report-name">{{ report.reportName }}</div>
              <div class="report-meta">
                <span>{{ report.institution || '未知机构' }}</span>
                <span>{{ report.reportDate || '未知日期' }}</span>
              </div>
              <div class="report-status">
                <el-tag
                  v-if="report.ocrStatus === 'COMPLETED' || report.ocrStatus === 'SUCCESS'"
                  type="success"
                  size="small"
                >
                  已识别
                </el-tag>
                <el-tag v-else-if="report.ocrStatus === 'PROCESSING'" type="warning" size="small">
                  识别中
                </el-tag>
                <el-tag v-else-if="report.ocrStatus === 'FAILED'" type="danger" size="small">
                  识别失败
                </el-tag>
                <el-tag v-else type="info" size="small">待识别</el-tag>
              </div>
            </div>
            <div class="report-actions" @click.stop>
              <el-button link type="danger" @click="handleDelete(report.id)">删除</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-empty v-if="!loading && reports.length === 0" description="暂无报告" />

      <!-- 分页 -->
      <div v-if="reports.length > 0" class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[12, 24, 48]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchReports"
          @current-change="fetchReports"
        />
      </div>
    </el-card>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadVisible" title="上传体检报告" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="报告名称" prop="reportName">
          <el-input v-model="form.reportName" placeholder="请输入报告名称" />
        </el-form-item>
        <el-form-item label="体检机构" prop="institution">
          <el-input v-model="form.institution" placeholder="请输入体检机构" />
        </el-form-item>
        <el-form-item label="体检日期" prop="reportDate">
          <el-date-picker
            v-model="form.reportDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="报告文件" prop="file">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            accept="image/*,.pdf"
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持图片和PDF格式，文件大小不超过10MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
  type UploadInstance
} from 'element-plus'
import { useRouter } from 'vue-router'
import { screeningApi } from '@/api/screening'
import type { ExaminationReportVO } from '@/types/api'

const router = useRouter()
const loading = ref(false)
const uploading = ref(false)
const uploadVisible = ref(false)
const reports = ref<ExaminationReportVO[]>([])
const formRef = ref<FormInstance>()
const uploadRef = ref<UploadInstance>()
const selectedFile = ref<File | null>(null)

const pagination = reactive({
  page: 1,
  size: 12,
  total: 0
})

const form = reactive({
  reportName: '',
  institution: '',
  reportDate: '',
  file: null as File | null
})

const rules: FormRules = {
  reportName: [{ required: true, message: '请输入报告名称', trigger: 'blur' }],
  reportDate: [{ required: true, message: '请选择体检日期', trigger: 'change' }]
}

const fetchReports = async () => {
  loading.value = true
  try {
    const res = await screeningApi.listReports({
      page: pagination.page,
      size: pagination.size
    })
    reports.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) {
    ElMessage.error('获取报告列表失败')
  } finally {
    loading.value = false
  }
}

const showUploadDialog = () => {
  Object.assign(form, {
    reportName: '',
    institution: '',
    reportDate: '',
    file: null
  })
  selectedFile.value = null
  uploadVisible.value = true
}

const handleFileChange = (file: any) => {
  selectedFile.value = file.raw
  form.file = file.raw
}

const handleUpload = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    if (!selectedFile.value) {
      ElMessage.warning('请选择文件')
      return
    }

    uploading.value = true
    try {
      await screeningApi.uploadReport(selectedFile.value, {
        reportName: form.reportName,
        institution: form.institution || undefined,
        reportDate: form.reportDate || undefined
      })
      ElMessage.success('上传成功，正在进行OCR识别...')
      uploadVisible.value = false
      fetchReports()
    } catch (error) {
      ElMessage.error('上传失败')
    } finally {
      uploading.value = false
    }
  })
}

const viewDetail = (id: number) => {
  router.push(`/screening/detail/${id}`)
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这份报告吗？', '提示', {
      type: 'warning'
    })
    await screeningApi.deleteReport(id)
    ElMessage.success('删除成功')
    fetchReports()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchReports()
})
</script>

<style scoped>
.screening-list-page {
  padding: 20px;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.report-card {
  cursor: pointer;
  transition: all 0.3s;
  margin-bottom: 20px;
}

.report-card:hover {
  transform: translateY(-4px);
}

.report-icon {
  text-align: center;
  margin-bottom: 16px;
}

.report-info {
  text-align: center;
}

.report-name {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-meta {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.report-status {
  margin-bottom: 12px;
}

.report-actions {
  text-align: center;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
