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
            <el-option label="血糖" value="BLOOD_GLUCOSE" />
            <el-option label="血压" value="BLOOD_PRESSURE" />
            <el-option label="心率" value="HEART_RATE" />
            <el-option label="体温" value="BODY_TEMPERATURE" />
            <el-option label="血氧" value="BLOOD_OXYGEN" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { thresholdApi } from '@/api/threshold'
import { formatDateTime, getMetricLabel } from '@/utils/format'
import type { UserThreshold, UserThresholdRequest } from '@/types/api'

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
    ElMessage.error('获取阈值失败')
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
      ElMessage.error('操作失败')
    }
  })
}

const handleDelete = async (row: UserThreshold) => {
  try {
    await ElMessageBox.confirm('确定要删除这个阈值设置吗？', '提示', {
      type: 'warning'
    })
    await thresholdApi.deleteThreshold(row.id)
    ElMessage.success('删除成功')
    fetchThresholds()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchThresholds()
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
