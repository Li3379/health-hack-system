<template>
  <el-card shadow="hover" class="ocr-input-card">
    <template #header>
      <div class="card-header">
        <el-icon><Camera /></el-icon>
        <span>OCR 图片识别</span>
      </div>
    </template>

    <div class="ocr-type-selector">
      <el-radio-group v-model="ocrType" size="small">
        <el-radio-button value="report">体检报告</el-radio-button>
        <el-radio-button value="medicine">药品标签</el-radio-button>
        <el-radio-button value="nutrition">营养标签</el-radio-button>
      </el-radio-group>
    </div>

    <el-upload
      ref="uploadRef"
      class="ocr-uploader"
      drag
      :auto-upload="false"
      :show-file-list="false"
      :on-change="handleFileChange"
      accept="image/*"
    >
      <div class="upload-area">
        <el-icon class="upload-icon"><Upload /></el-icon>
        <div class="upload-text">
          <span>拖拽或点击上传图片</span>
          <span class="upload-hint">支持 JPG、PNG 格式，最大 10MB</span>
        </div>
      </div>
    </el-upload>

    <div v-if="previewUrl" class="preview-container">
      <img :src="previewUrl" alt="预览" class="preview-image" />
      <el-button type="danger" size="small" circle class="remove-btn" @click="clearPreview">
        <el-icon><Close /></el-icon>
      </el-button>
    </div>

    <div class="ocr-actions">
      <el-button
        type="primary"
        :disabled="!selectedFile"
        :loading="recognizing"
        @click="handleRecognize"
      >
        开始识别
      </el-button>
    </div>

    <!-- 识别结果 -->
    <div v-if="recognizeResult" class="recognize-result">
      <div class="result-header">
        <span>识别结果</span>
        <el-tag :type="recognizeResult.status === 'success' ? 'success' : 'danger'">
          {{ recognizeResult.status === 'success' ? '识别成功' : '识别失败' }}
        </el-tag>
      </div>

      <el-table
        v-if="recognizeResult.metrics?.length"
        :data="recognizeResult.metrics"
        stripe
        size="small"
      >
        <el-table-column width="50">
          <template #default="{ row }">
            <el-checkbox v-model="row.selected" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="指标" width="100" />
        <el-table-column label="数值" width="120">
          <template #default="{ row }">{{ row.value }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="100">
          <template #default="{ row }">
            <el-tag :type="row.category === 'HEALTH' ? 'primary' : 'success'" size="small">
              {{ row.category === 'HEALTH' ? '健康' : '保健' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="80">
          <template #default="{ row }">
            <span :class="getConfidenceClass(row.confidence)">
              {{ Math.round((row.confidence || 0.9) * 100) }}%
            </span>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="recognizeResult.summary" class="result-summary">
        <el-text type="info">{{ recognizeResult.summary }}</el-text>
      </div>

      <!-- 原始识别文本 -->
      <el-collapse v-if="recognizeResult.rawText" class="raw-text-collapse">
        <el-collapse-item title="原始识别文本" name="rawText">
          <pre class="raw-text-content">{{ recognizeResult.rawText }}</pre>
        </el-collapse-item>
      </el-collapse>

      <div class="result-actions">
        <el-button @click="recognizeResult = null">取消</el-button>
        <el-button type="primary" :loading="confirming" @click="handleConfirm">确认录入</el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, Upload, Close } from '@element-plus/icons-vue'
import type { UploadFile } from 'element-plus'
import { request } from '@/utils/request'

// ============================================
// TypeScript 接口定义
// ============================================

/**
 * 识别出的单个指标
 */
interface RecognizedMetric {
  metricKey: string
  name: string
  value: number
  unit: string
  confidence: number
  category: 'HEALTH' | 'WELLNESS'
  recordDate: string
  referenceRange?: string
  abnormal?: boolean
  selected: boolean
}

/**
 * OCR识别结果
 */
interface OcrRecognizeResult {
  status: string
  ocrType: string
  metrics: RecognizedMetric[]
  rawText: string
  summary: string
  errorMessage?: string
  ocrRecordId?: number
}

/**
 * 确认录入请求
 */
interface OcrConfirmRequest {
  ocrRecordId?: number
  metrics: RecognizedMetric[]
}

// ============================================
// 组件逻辑
// ============================================

const emit = defineEmits<{
  (e: 'refresh'): void
}>()

const ocrType = ref('report')
const selectedFile = ref<File | null>(null)
const previewUrl = ref('')
const recognizing = ref(false)
const confirming = ref(false)
const recognizeResult = ref<OcrRecognizeResult | null>(null)

/**
 * 文件选择处理
 */
const handleFileChange = (file: UploadFile) => {
  if (file.raw) {
    // 验证文件大小 (10MB)
    if (file.raw.size > 10 * 1024 * 1024) {
      ElMessage.error('文件大小不能超过10MB')
      return
    }

    // 验证文件类型
    if (!file.raw.type.startsWith('image/')) {
      ElMessage.error('只能上传图片文件')
      return
    }

    selectedFile.value = file.raw
    previewUrl.value = URL.createObjectURL(file.raw)
    recognizeResult.value = null
  }
}

/**
 * 清除预览
 */
const clearPreview = () => {
  selectedFile.value = null
  previewUrl.value = ''
  recognizeResult.value = null
}

/**
 * 执行OCR识别
 */
const handleRecognize = async () => {
  if (!selectedFile.value) return

  recognizing.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('ocrType', ocrType.value)

    const res = await request.upload<OcrRecognizeResult>('/api/ocr/health-image', formData)

    if (res.code === 200 && res.data) {
      recognizeResult.value = res.data
      // 默认选中所有指标
      res.data.metrics?.forEach((m: RecognizedMetric) => {
        m.selected = true
      })
    } else {
      recognizeResult.value = {
        status: 'failed',
        ocrType: ocrType.value,
        metrics: [],
        rawText: '',
        summary: '',
        errorMessage: res.message || '识别失败'
      } as OcrRecognizeResult
    }
  } catch (error: unknown) {
    const errorMessage = error instanceof Error ? error.message : '识别失败'
    recognizeResult.value = {
      status: 'failed',
      ocrType: ocrType.value,
      metrics: [],
      rawText: '',
      summary: '',
      errorMessage
    } as OcrRecognizeResult
    ElMessage.error(errorMessage)
  } finally {
    recognizing.value = false
  }
}

/**
 * 确认录入
 */
const handleConfirm = async () => {
  if (!recognizeResult.value?.metrics?.length) return

  const selectedMetrics = recognizeResult.value.metrics.filter((m: RecognizedMetric) => m.selected)

  if (!selectedMetrics.length) {
    ElMessage.warning('请至少选择一个指标')
    return
  }

  confirming.value = true
  try {
    const requestBody: OcrConfirmRequest = {
      ocrRecordId: recognizeResult.value.ocrRecordId,
      metrics: selectedMetrics
    }

    const res = await request.post<number[]>('/api/ocr/confirm', requestBody)

    if (res.code === 200 && res.data) {
      ElMessage.success(`成功录入 ${res.data.length} 个指标`)
      recognizeResult.value = null
      clearPreview()
      emit('refresh')
    } else {
      ElMessage.error(res.message || '录入失败')
    }
  } catch (error: unknown) {
    const errorMessage = error instanceof Error ? error.message : '录入失败'
    ElMessage.error(errorMessage)
  } finally {
    confirming.value = false
  }
}

/**
 * 获取置信度样式类
 */
const getConfidenceClass = (confidence: number): string => {
  if (confidence >= 0.9) return 'confidence-high'
  if (confidence >= 0.7) return 'confidence-medium'
  return 'confidence-low'
}

/**
 * 组件卸载时清理资源
 */
onUnmounted(() => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
})
</script>

<style scoped>
.ocr-input-card {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.ocr-type-selector {
  margin-bottom: 16px;
}

.ocr-uploader {
  width: 100%;
}

.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  border: 2px dashed var(--color-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.upload-area:hover {
  border-color: var(--color-primary);
  background: var(--color-bg-secondary);
}

.upload-icon {
  font-size: 48px;
  color: var(--color-text-secondary);
  margin-bottom: 12px;
}

.upload-text {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.upload-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.preview-container {
  position: relative;
  margin-top: 16px;
  text-align: center;
}

.preview-image {
  max-width: 100%;
  max-height: 200px;
  border-radius: 8px;
}

.remove-btn {
  position: absolute;
  top: 8px;
  right: 8px;
}

.ocr-actions {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.recognize-result {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 500;
}

.result-summary {
  margin-top: 8px;
}

.raw-text-collapse {
  margin-top: 12px;
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

.result-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

/* 置信度样式 */
.confidence-high {
  color: #67c23a;
  font-weight: 500;
}

.confidence-medium {
  color: #e6a23c;
  font-weight: 500;
}

.confidence-low {
  color: #f56c6c;
  font-weight: 500;
}
</style>
