<template>
  <div class="prevention-metrics-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>保健指标</span>
          <el-button type="primary" @click="showAddDialog">
            <el-icon><Plus /></el-icon>
            添加指标
          </el-button>
        </div>
      </template>

      <!-- 指标列表 -->
      <el-table v-loading="loading" :data="metrics" stripe>
        <el-table-column prop="metricKey" label="指标类型" width="150">
          <template #default="{ row }">
            {{ row.metricName || getMetricLabelLocal(row.metricKey) }}
          </template>
        </el-table-column>
        <el-table-column prop="value" label="数值" width="120">
          <template #default="{ row }">
            {{ formatValue(row.metricKey, row.value) }}
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="recordDate" label="记录日期" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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
          @size-change="fetchMetrics"
          @current-change="fetchMetrics"
        />
      </div>
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="指标类型" prop="metricKey">
          <el-select
            v-model="form.metricKey"
            placeholder="请选择"
            style="width: 100%"
            :disabled="!!editingId"
          >
            <el-option
              v-for="(config, key) in WELLNESS_METRICS"
              :key="key"
              :label="config.label"
              :value="key"
            />
          </el-select>
        </el-form-item>

        <!-- 心情特殊输入 -->
        <el-form-item v-if="form.metricKey === 'mood'" label="心情" prop="value">
          <div class="mood-input">
            <el-rate
              v-model="moodValue"
              :max="5"
              show-text
              :texts="['很差', '较差', '一般', '较好', '很好']"
              @change="handleMoodChange"
            />
          </div>
        </el-form-item>

        <!-- 其他指标普通输入 -->
        <el-form-item v-else label="数值" prop="value">
          <el-input-number v-model="form.value" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>

        <el-form-item label="记录日期" prop="recordDate">
          <el-date-picker
            v-model="form.recordDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { wellnessApi } from '@/api/wellness'
import { formatDateTime, WELLNESS_METRICS } from '@/utils/format'
import type { WellnessMetricVO, WellnessMetricRequest } from '@/types/api'

const loading = ref(false)
const submitting = ref(false)
const metrics = ref<WellnessMetricVO[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加保健指标')
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const form = reactive<WellnessMetricRequest>({
  metricKey: '',
  value: 0,
  recordDate: ''
})

// 心情星级值（1-5）
const moodValue = computed({
  get: () => Math.round(form.value) || 3,
  set: (val: number) => {
    form.value = val
  }
})

const rules: FormRules = {
  metricKey: [{ required: true, message: '请选择指标类型', trigger: 'change' }],
  value: [{ required: true, message: '请输入数值', trigger: 'blur' }],
  recordDate: [{ required: true, message: '请选择记录日期', trigger: 'change' }]
}

const fetchMetrics = async () => {
  loading.value = true
  try {
    const res = await wellnessApi.getMetrics({
      page: pagination.page,
      size: pagination.size
    })
    metrics.value = res.data.records
    pagination.total = res.data.total
  } catch (error) {
    ElMessage.error('获取数据失败')
  } finally {
    loading.value = false
  }
}

const showAddDialog = () => {
  dialogTitle.value = '添加保健指标'
  editingId.value = null
  Object.assign(form, {
    metricKey: '',
    value: 0,
    recordDate: new Date().toISOString().split('T')[0]
  })
  dialogVisible.value = true
}

const handleEdit = (row: WellnessMetricVO) => {
  dialogTitle.value = '编辑保健指标'
  editingId.value = row.id
  Object.assign(form, {
    metricKey: row.metricKey,
    value: row.value,
    recordDate: row.recordDate
  })
  dialogVisible.value = true
}

const handleMoodChange = (val: number) => {
  form.value = val
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    submitting.value = true
    try {
      if (editingId.value) {
        await wellnessApi.updateMetric(editingId.value, form)
        ElMessage.success('更新成功')
      } else {
        await wellnessApi.createMetric(form)
        ElMessage.success('添加成功')
      }
      dialogVisible.value = false
      fetchMetrics()
    } catch (error) {
      ElMessage.error(editingId.value ? '更新失败' : '添加失败')
    } finally {
      submitting.value = false
    }
  })
}

const handleDelete = async (row: WellnessMetricVO) => {
  try {
    await ElMessageBox.confirm('确定要删除这条记录吗？', '提示', {
      type: 'warning'
    })
    await wellnessApi.deleteMetric(row.id)
    ElMessage.success('删除成功')
    fetchMetrics()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// Get metric label from WELLNESS_METRICS config
const getMetricLabelLocal = (key: string): string => {
  return WELLNESS_METRICS[key]?.label || key
}

// Format value display (especially for mood)
const formatValue = (key: string, value: number): string => {
  if (key === 'mood') {
    const levels = ['很差', '较差', '一般', '较好', '很好']
    const idx = Math.max(0, Math.min(4, Math.round(value) - 1))
    return `${value} (${levels[idx]})`
  }
  return String(value)
}

onMounted(() => {
  fetchMetrics()
})
</script>

<style scoped>
.prevention-metrics-page {
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

.mood-input {
  width: 100%;
  padding: 10px 0;
}
</style>
