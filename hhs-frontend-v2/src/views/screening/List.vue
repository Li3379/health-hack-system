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
        <!-- 汇总块必须是 <el-form> 的第一个子节点（Contract 4.3），弹窗与全页表单同一位置。 -->
        <FormErrorSummary
          ref="summaryRef"
          :summary-items="uploadErrors.summaryItems"
          @focus-field="uploadErrors.focusField"
        />
        <el-form-item
          label="报告名称"
          prop="reportName"
          data-field="reportName"
          :error="uploadErrors.fieldErrors['reportName']"
        >
          <el-input
            v-model="form.reportName"
            placeholder="请输入报告名称"
            @update:model-value="uploadErrors.clearField('reportName')"
          />
        </el-form-item>
        <el-form-item
          label="体检机构"
          prop="institution"
          data-field="institution"
          :error="uploadErrors.fieldErrors['institution']"
        >
          <el-input
            v-model="form.institution"
            placeholder="请输入体检机构"
            @update:model-value="uploadErrors.clearField('institution')"
          />
        </el-form-item>
        <el-form-item
          label="体检日期"
          prop="reportDate"
          data-field="reportDate"
          :error="uploadErrors.fieldErrors['reportDate']"
        >
          <el-date-picker
            v-model="form.reportDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            @change="uploadErrors.clearField('reportDate')"
          />
        </el-form-item>
        <!-- 上传控件的绑定值是文件列表而非 model-value：清除必须挂在控件自己的
             on-change / on-remove 上，否则换一个文件后上一轮的「报告文件」错误会留着。 -->
        <el-form-item
          label="报告文件"
          prop="file"
          data-field="file"
          :error="uploadErrors.fieldErrors['file']"
        >
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
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
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadInstance } from 'element-plus'
import { useRouter } from 'vue-router'
import { Upload, Document } from '@element-plus/icons-vue'
import { screeningApi } from '@/api/screening'
import { useFormServerErrors, collectInvalidFieldMessages } from '@/composables/useFormServerErrors'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import { showError } from '@/utils/showError'
import { staggerReveal, cleanupScrollTriggers } from '@/composables/useGsap'
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

/**
 * 上传表单的字段错误投影。
 *
 * 本页不渲染任何错误状态块，因此三个调用点都显式 opt-in 状态类失败的呈现
 * （14-05 建立的呈现通道规则）：`forbidden` / `notFound` 没有页面块可以落，不 opt-in
 * 就会把一次可见的失败变成完全静默。
 */
const uploadErrors = reactive(useFormServerErrors({
  fieldOrder: ['reportName', 'institution', 'reportDate', 'file']
}))

const summaryRef = ref<InstanceType<typeof FormErrorSummary> | null>(null)

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
    // 本页无错误状态块 → 显式 opt-in，403 / 404 也至少有一条常驻 toast。
    showError(error, 'screening.loadFailed', { presentStates: true })
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
  // 重新打开弹窗时清掉上一轮尝试留下的投影（只清错误文本，不涉及用户输入）。
  uploadErrors.reset()
  uploadVisible.value = true
}

const handleFileChange = (file: any) => {
  selectedFile.value = file.raw
  form.file = file.raw
  // 换了文件就清掉上一轮的「报告文件」错误（:error 的清除不是 model-value 更新驱动的）。
  uploadErrors.clearField('file')
}

const handleFileRemove = () => {
  selectedFile.value = null
  form.file = null
  uploadErrors.clearField('file')
}

const handleUpload = async () => {
  if (!formRef.value) return

  // 上一轮的投影不进入本次尝试；只清投影，不碰用户输入（ERR-05）。
  uploadErrors.reset()

  await formRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      // 客户端校验失败也走同一份汇总通道（GOV.UK 双呈现），零 toast。
      uploadErrors.setErrors(collectInvalidFieldMessages(invalidFields))
      await nextTick()
      summaryRef.value?.focusSummary()
      return
    }

    if (!selectedFile.value) {
      // 没有文件不是**服务端**失败：这条提示是本地前置条件，不是一次请求的结果，
      // 因此保留 warning（不是 error 通道），且不清空任何已填内容。
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
      // 关闭弹窗只在解析成功之后 —— 失败时 dialog 保持打开（Contract 7.1）。
      uploadVisible.value = false
      fetchReports()
    } catch (error) {
      // validation → 0 toast，汇总 + 内联承担呈现；其余 kind 由 showError 呈现。
      const err = showError(error, 'ocr.uploadFailed', { presentStates: true })
      if (err.kind === 'validation') {
        uploadErrors.apply(err)
        await nextTick()
        summaryRef.value?.focusSummary()
      }
    } finally {
      uploading.value = false
    }
  })
}

const viewDetail = (id: number) => {
  router.push(`/screening/detail/${id}`)
}

/**
 * 删除：确认与请求彻底分离。
 *
 * 取消路径是一个裸 `return` —— 用户按了「取消」不是失败，零提示（D-12 / ERR-08）。
 * 原先的手写取消判断（把拒绝值与字符串形式的取消哨兵逐字比较）对 `AbortError`
 * 与归一化后的 `ApiError('', 'cancel')` 都会误判成真失败，因此整段删掉。
 * `ScreeningServiceImpl:197` 对「报告不存在」与「别人的报告」都抛 `RESOURCE_NOT_FOUND`，
 * 后者是常规竞态；第三个参数让它仍然产生恰好一条提示而不是静默。
 */
const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这份报告吗？', '提示', {
      type: 'warning'
    })
  } catch {
    // 取消确认框：刻意静默，不弹任何提示（Contract 7.2）。
    return
  }

  try {
    await screeningApi.deleteReport(id)
    ElMessage.success('删除成功')
    fetchReports()
  } catch (error) {
    showError(error, 'screening.deleteFailed', { presentStates: true })
  }
}

onMounted(async () => {
  await fetchReports()
  staggerReveal('.report-card', { stagger: 0.06, y: 24 })
})

onUnmounted(() => { cleanupScrollTriggers() })
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
