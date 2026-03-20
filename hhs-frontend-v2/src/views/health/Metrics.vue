<template>
  <div class="metrics-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>健康指标管理</span>
          <el-button type="primary" @click="showAddDialog">
            <el-icon><Plus /></el-icon>
            添加指标
          </el-button>
        </div>
      </template>

      <!-- 筛选区 -->
      <div class="filter-bar">
        <el-form :inline="true" :model="filters">
          <el-form-item label="指标类型">
            <el-select
              v-model="filters.metricKey"
              placeholder="全部"
              clearable
              style="width: 180px"
            >
              <el-option label="血糖" value="glucose" />
              <el-option label="收缩压" value="systolicBP" />
              <el-option label="舒张压" value="diastolicBP" />
              <el-option label="心率" value="heartRate" />
              <el-option label="体温" value="temperature" />
              <el-option label="体重" value="weight" />
              <el-option label="BMI" value="bmi" />
            </el-select>
          </el-form-item>
          <el-form-item label="日期范围">
            <el-date-picker
              v-model="filters.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="fetchMetrics">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="metrics" stripe>
        <el-table-column prop="metricKey" label="指标类型" width="120">
          <template #default="{ row }">
            {{ row.metricName || getMetricLabel(row.metricKey) }}
          </template>
        </el-table-column>
        <el-table-column prop="value" label="数值" width="100" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="recordDate" label="记录日期" width="120" />
        <el-table-column prop="trend" label="趋势" width="100">
          <template #default="{ row }">
            <el-tag v-if="normalizeTrend(row.trend) === 'UP'" type="danger">上升</el-tag>
            <el-tag v-else-if="normalizeTrend(row.trend) === 'DOWN'" type="success">下降</el-tag>
            <el-tag v-else type="info">稳定</el-tag>
          </template>
        </el-table-column>
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
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
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
            @change="handleMetricKeyChange"
          >
            <el-option label="血糖" value="glucose" />
            <el-option label="收缩压" value="systolicBP" />
            <el-option label="舒张压" value="diastolicBP" />
            <el-option label="心率" value="heartRate" />
            <el-option label="体温" value="temperature" />
            <el-option label="体重" value="weight" />
            <el-option label="BMI" value="bmi" />
          </el-select>
        </el-form-item>
        <el-form-item label="数值" prop="value">
          <el-input-number v-model="form.value" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="form.unit" disabled placeholder="自动填充" />
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
        <el-form-item label="趋势" prop="trend">
          <el-select v-model="form.trend" placeholder="请选择" style="width: 100%">
            <el-option label="上升" value="UP" />
            <el-option label="下降" value="DOWN" />
            <el-option label="稳定" value="STABLE" />
          </el-select>
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
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { healthApi } from '@/api/health'
import { formatDateTime, getMetricLabel } from '@/utils/format'
import type { HealthMetricVO, HealthMetricRequest } from '@/types/api'

const route = useRoute()
const loading = ref(false)
const metrics = ref<HealthMetricVO[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加指标')
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const filters = reactive({
  metricKey: '',
  dateRange: [] as string[]
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const form = reactive<HealthMetricRequest>({
  metricKey: '',
  value: 0,
  recordDate: '',
  unit: '',
  trend: ''
})

const rules: FormRules = {
  metricKey: [{ required: true, message: '请选择指标类型', trigger: 'change' }],
  value: [{ required: true, message: '请输入数值', trigger: 'blur' }],
  recordDate: [{ required: true, message: '请选择记录日期', trigger: 'change' }]
}

// 指标类型与单位的映射关系
const metricUnitMap: Record<string, string> = {
  glucose: 'mmol/L',
  systolicBP: 'mmHg',
  diastolicBP: 'mmHg',
  heartRate: '次/分',
  temperature: '°C',
  weight: 'kg',
  bmi: ''
}

// 根据指标类型自动填充单位
const handleMetricKeyChange = (value: string) => {
  form.unit = metricUnitMap[value] || ''
  form.value = 0 // 重置数值，让用户重新填写
}

// 将后端趋势值标准化为大写，便于前端展示与选择
const normalizeTrend = (trend?: string) => {
  return trend ? trend.toUpperCase() : ''
}

// 将前端选择的趋势值转换为后端期望的小写格式
const toBackendTrend = (trend?: string) => {
  if (!trend) return ''
  const upper = trend.toUpperCase()
  if (upper === 'UP') return 'up'
  if (upper === 'DOWN') return 'down'
  if (upper === 'STABLE') return 'stable'
  if (upper === 'NORMAL') return 'normal'
  return trend.toLowerCase()
}

const fetchMetrics = async () => {
  loading.value = true
  try {
    const params: any = {
      page: pagination.page,
      size: pagination.size
    }
    if (filters.metricKey) params.metricKey = filters.metricKey
    if (filters.dateRange?.length === 2) {
      params.startDate = filters.dateRange[0]
      params.endDate = filters.dateRange[1]
    }

    const res = await healthApi.getMetrics(params)
    metrics.value = res.data.records
    pagination.total = res.data.total
  } catch (error) {
    ElMessage.error('获取数据失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.metricKey = ''
  filters.dateRange = []
  pagination.page = 1
  fetchMetrics()
}

const showAddDialog = () => {
  dialogTitle.value = '添加指标'
  editingId.value = null
  Object.assign(form, {
    metricKey: '',
    value: 0,
    recordDate: '',
    unit: '',
    trend: ''
  })
  dialogVisible.value = true
}

const handleEdit = (row: HealthMetricVO) => {
  dialogTitle.value = '编辑指标'
  editingId.value = row.id
  Object.assign(form, {
    metricKey: row.metricKey,
    value: row.value,
    recordDate: row.recordDate,
    unit: row.unit || '',
    trend: normalizeTrend(row.trend)
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    // 构造发送给后端的请求载荷，处理趋势字段大小写
    const payload: HealthMetricRequest = {
      metricKey: form.metricKey,
      value: form.value,
      recordDate: form.recordDate,
      unit: form.unit,
      trend: toBackendTrend(form.trend)
    }

    try {
      if (editingId.value) {
        await healthApi.updateMetric(editingId.value, payload)
        ElMessage.success('更新成功')
      } else {
        await healthApi.createMetric(payload)
        ElMessage.success('添加成功')
      }
      dialogVisible.value = false
      fetchMetrics()
    } catch (error) {
      ElMessage.error('操作失败')
    }
  })
}

const handleDelete = async (row: HealthMetricVO) => {
  try {
    await ElMessageBox.confirm('确定要删除这条记录吗？', '提示', {
      type: 'warning'
    })
    await healthApi.deleteMetric(row.id)
    ElMessage.success('删除成功')
    fetchMetrics()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchMetrics()
  // 检查是否有 add 参数，有则自动打开添加对话框
  if (route.query.action === 'add') {
    showAddDialog()
  }
})
</script>

<style scoped>
.metrics-page {
  padding: 20px;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
