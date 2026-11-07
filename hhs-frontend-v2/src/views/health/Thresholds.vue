<template>
  <div class="thresholds-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>阈值设置</span>
          <el-button type="primary" @click="showAddDialog">
            <el-icon><Plus /></el-icon>
            添加阈值
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="thresholds" stripe>
        <el-table-column prop="metricKey" label="指标类型" width="150">
          <template #default="{ row }">
            {{ getMetricLabel(row.metricKey) }}
          </template>
        </el-table-column>
        <el-table-column label="严重高值" width="120">
          <template #default="{ row }">
            <span class="threshold-value critical">{{ row.criticalHigh || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="警告高值" width="120">
          <template #default="{ row }">
            <span class="threshold-value warning">{{ row.warningHigh || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="警告低值" width="120">
          <template #default="{ row }">
            <span class="threshold-value warning">{{ row.warningLow || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="严重低值" width="120">
          <template #default="{ row }">
            <span class="threshold-value critical">{{ row.criticalLow || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="指标类型" prop="metricKey">
          <el-select
            v-model="form.metricKey"
            placeholder="请选择"
            style="width: 100%"
            :disabled="!!editingId"
          >
            <el-option label="血糖" value="glucose" />
            <el-option label="收缩压" value="systolicBP" />
            <el-option label="舒张压" value="diastolicBP" />
            <el-option label="心率" value="heartRate" />
            <el-option label="体温" value="temperature" />
            <el-option label="血氧" value="bloodOxygen" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">高值阈值</el-divider>
        <el-form-item label="严重高值">
          <el-input-number
            v-model="form.criticalHigh"
            :precision="2"
            :min="0"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="警告高值">
          <el-input-number v-model="form.warningHigh" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>

        <el-divider content-position="left">低值阈值</el-divider>
        <el-form-item label="警告低值">
          <el-input-number v-model="form.warningLow" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="严重低值">
          <el-input-number v-model="form.criticalLow" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { gsap, cleanupScrollTriggers, DUR, EASE } from '@/composables/useGsap'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { thresholdApi } from '@/api/threshold'
import { showError } from '@/utils/showError'
import { formatDateTime, getMetricLabel } from '@/utils/format'
import type { UserThreshold, UserThresholdRequest } from '@/types/api'

// Phase 14: 其余 toast 调用点待迁移

const authStore = useAuthStore()
const loading = ref(false)
const thresholds = ref<UserThreshold[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加阈值')
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive<UserThresholdRequest>({
  userId: authStore.user?.id || 0,
  metricKey: '',
  warningHigh: undefined,
  criticalHigh: undefined,
  warningLow: undefined,
  criticalLow: undefined
})

const rules: FormRules = {
  metricKey: [{ required: true, message: '请选择指标类型', trigger: 'change' }]
}

const fetchThresholds = async () => {
  loading.value = true
  try {
    const res = await thresholdApi.getThresholds({ page: 1, size: 100 })
    thresholds.value = res.data.records || []
  } catch (error) {
    // 单一错误出口（D-05/D-10）：后端中文优先，场景字典兜底。
    showError(error, 'threshold.loadFailed')
  } finally {
    loading.value = false
  }
}

const showAddDialog = () => {
  dialogTitle.value = '添加阈值'
  editingId.value = null
  Object.assign(form, {
    userId: authStore.user?.id || 0,
    metricKey: '',
    warningHigh: undefined,
    criticalHigh: undefined,
    warningLow: undefined,
    criticalLow: undefined
  })
  dialogVisible.value = true
}

const handleEdit = (row: UserThreshold) => {
  dialogTitle.value = '编辑阈值'
  editingId.value = row.id
  Object.assign(form, {
    userId: row.userId,
    metricKey: row.metricKey,
    warningHigh: row.warningHigh,
    criticalHigh: row.criticalHigh,
    warningLow: row.warningLow,
    criticalLow: row.criticalLow
  })
  dialogVisible.value = true
}

/** 恢复动作：关闭新增/失败对话框并打开已存在阈值的编辑态。 */
const openExistingThreshold = (row: UserThreshold) => {
  dialogVisible.value = false
  handleEdit(row)
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    try {
      if (editingId.value) {
        await thresholdApi.updateThreshold(editingId.value, form)
        ElMessage.success('更新成功')
      } else {
        await thresholdApi.createThreshold(form)
        ElMessage.success('添加成功')
      }
      dialogVisible.value = false
      fetchThresholds()
    } catch (error) {
      // 错误码判定完全由 showError 内部完成，本页不写任何错误码分支；
      // 标签由 ERROR_CODE_ACTIONS 自动补成「打开编辑」——此处只传 onClick，不传 label。
      // 只在列表里确实找到该指标时才给恢复动作，否则不渲染死按钮（T-11-24）。
      // 失败保持对话框打开（ERR-05 前提）。
      const existing = thresholds.value.find(t => t.metricKey === form.metricKey)
      // 本页未接入 useFormServerErrors、没有内联绑定：显式放行校验失败，
      // 否则后端 400（如 metricKey 超长）会完全静默（WR-13）。仍走单一 toast 出口。
      showError(error, 'threshold.duplicate', {
        presentValidation: true,
        ...(existing ? { action: { onClick: () => openExistingThreshold(existing) } } : {})
      })
    }
  })
}

const handleDelete = async (row: UserThreshold) => {
  try {
    await ElMessageBox.confirm('确定要删除这个阈值设置吗？', '提示', {
      type: 'warning'
    })
  } catch {
    // 用户取消：零提示（D-12），不进入错误出口。
    return
  }
  await deleteThresholdConfirmed(row)
}

/**
 * 真正执行删除。
 *
 * 失败时的重试回调重新执行**整个删除动作**（含成功后的列表刷新与成功提示），
 * 而不是只重发 HTTP 请求 —— 否则重试成功后列表仍显示已删除的行。
 * showError 只在网络/传输层 5xx 时渲染「重试」，业务失败不会出现死按钮（D-03）。
 */
const deleteThresholdConfirmed = async (row: UserThreshold) => {
  try {
    await thresholdApi.deleteThreshold(row.id)
    ElMessage.success('删除成功')
    fetchThresholds()
  } catch (error) {
    showError(error, 'threshold.deleteFailed', {
      retry: () => deleteThresholdConfirmed(row)
    })
  }
}

onMounted(() => {
  fetchThresholds()

  nextTick(() => {
    const tl = gsap.timeline({ defaults: { ease: EASE.out } })
    tl.fromTo(
      '.el-card',
      { y: 20, autoAlpha: 0 },
      {
        y: 0,
        autoAlpha: 1,
        duration: DUR.mid,
        clearProps: 'transform,autoAlpha,visibility,opacity'
      }
    )
    tl.fromTo(
      '.header-actions',
      { y: -10, autoAlpha: 0 },
      {
        y: 0,
        autoAlpha: 1,
        duration: DUR.mid,
        clearProps: 'transform,autoAlpha,visibility,opacity'
      },
      '-=0.2'
    )
  })
})
onUnmounted(() => {
  cleanupScrollTriggers()
})
</script>

<style scoped>
.thresholds-page {
  padding: 20px;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.threshold-value {
  font-weight: 500;
}

.threshold-value.critical {
  color: #f56c6c;
}

.threshold-value.warning {
  color: #e6a23c;
}
</style>
