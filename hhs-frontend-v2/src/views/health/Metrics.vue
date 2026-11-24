<template>
  <div class="metrics-page">
    <el-card shadow="never" class="filter-bar">
      <el-form :inline="true" :model="filters">
        <el-form-item label="指标类型">
          <el-select
            v-model="filters.metricKey"
            placeholder="全部指标"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="item in metricMetaStore.healthMetrics"
              :key="item.canonicalKey"
              :label="item.displayName"
              :value="item.canonicalKey"
            />
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
          <el-button type="primary" @click="handleFilterQuery">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="header-actions">
          <span>健康指标记录</span>
          <el-button type="primary" :icon="Plus" @click="showAddDialog">添加健康指标</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="metrics" stripe style="width: 100%">
        <el-table-column prop="recordDate" label="记录日期" width="120" sortable />
        <el-table-column prop="metricKey" label="指标类型" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ getMetricLabel(row.metricKey) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="value" label="测量值" width="120" />
        <el-table-column prop="unit" label="单位" width="100" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag
              v-if="row.statusText"
              size="small"
              :type="
                row.status?.startsWith('CRITICAL')
                  ? 'danger'
                  : row.status?.startsWith('WARNING')
                    ? 'warning'
                    : 'success'
              "
            >
              {{ row.statusText }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="trend" label="趋势" width="100">
          <template #default="{ row }">
            <el-tag
              v-if="row.trend"
              size="small"
              :type="row.trend === 'UP' ? 'danger' : row.trend === 'DOWN' ? 'success' : 'info'"
            >
              {{ row.trend === 'UP' ? '上升' : row.trend === 'DOWN' ? '下降' : '稳定' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="录入时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="160">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

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

    <!-- 通用指标录入与编辑弹窗 (REUSE-01, REUSE-02, D-01, D-02) -->
    <UniversalMetricDialog
      ref="dialogRef"
      v-model="dialogVisible"
      category="HEALTH"
      :title="editingRow ? '编辑健康指标' : '添加健康指标'"
      :initial-data="editingRow"
      @success="fetchMetrics"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { healthApi } from '@/api/health'
import { showError } from '@/utils/showError'
import { formatDateTime, getMetricLabel } from '@/utils/format'
import { useMetricMetaStore } from '@/stores/metricMeta'
import UniversalMetricDialog from '@/components/metric/UniversalMetricDialog.vue'
import type { HealthMetricVO } from '@/types/api'

const route = useRoute()
const metricMetaStore = useMetricMetaStore()
const loading = ref(false)
const metrics = ref<HealthMetricVO[]>([])
const dialogVisible = ref(false)
const dialogRef = ref<any>()
const editingRow = ref<HealthMetricVO | null>(null)

// 筛选参数
const filters = reactive({
  metricKey: '',
  dateRange: [] as string[]
})

// 分页参数
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 筛选查询处理（重置页码）
const handleFilterQuery = () => {
  pagination.page = 1
  fetchMetrics()
}

// 拉取列表数据
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
    showError(error, 'metric.loadFailed', { presentStates: true })
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

// 打开添加弹窗
const showAddDialog = () => {
  editingRow.value = null
  dialogVisible.value = true
  if (dialogRef.value) {
    dialogRef.value.initForm()
  }
}

// 打开编辑弹窗
const handleEdit = (row: HealthMetricVO) => {
  editingRow.value = row
  dialogVisible.value = true
  if (dialogRef.value) {
    dialogRef.value.initForm(row)
  }
}

// 删除记录
const handleDelete = async (row: HealthMetricVO) => {
  try {
    await ElMessageBox.confirm('确定要删除这条健康指标记录吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    })
  } catch {
    // 取消确认框：刻意静默，零提示（Contract 7 §7.2 的第二种合法形态）。
    // 旧判断里的第二个哨兵值（关闭）半边是死代码 —— ElMessageBox.confirm 只以
    // 字符串 'cancel' 拒绝；'close' 来自 before-close / 关闭按钮，本调用点不使用。
    return
  }

  try {
    await healthApi.deleteMetric(row.id)
    ElMessage.success('删除成功')
    fetchMetrics()
  } catch (error) {
    showError(error, 'metric.deleteFailed', { presentStates: true })
  }
}

onMounted(() => {
  metricMetaStore.fetchMetadata()
  fetchMetrics()
  if (route.query.action === 'add') {
    showAddDialog()
  }
})

// 向前兼容暴露全部属性与方法 (保证既有单元测试 Metrics.spec.ts 100% 绿灯通过)
defineExpose({
  dialogRef,
  metricMetaStore,
  dialogVisible,
  showAddDialog,
  handleEdit,
  fetchMetrics,
  get form() {
    return dialogRef.value?.form
  },
  get isBloodPressure() {
    return dialogRef.value?.isBloodPressure
  },
  get isCollaborative() {
    return dialogRef.value?.isCollaborative
  },
  get isBpEqual() {
    return dialogRef.value?.isBpEqual
  },
  get isEditMode() {
    return dialogRef.value?.isEditMode
  },
  get definition() {
    return dialogRef.value?.definition
  },
  get diastolicDef() {
    return dialogRef.value?.diastolicDef
  },
  get primaryEval() {
    return dialogRef.value?.primaryEval
  },
  get diastolicEval() {
    return dialogRef.value?.diastolicEval
  },
  get isInverted() {
    return dialogRef.value?.isInverted
  },
  get hasCritical() {
    return dialogRef.value?.hasCritical
  },
  get isSaveBlocked() {
    return dialogRef.value?.isSaveBlocked
  },
  get precision() {
    return dialogRef.value?.precision
  },
  get step() {
    return dialogRef.value?.step
  },
  get placeholder() {
    return dialogRef.value?.placeholder
  },
  get editingId() {
    return editingRow.value?.id ?? null
  },
  get dialogTitle() {
    return dialogRef.value?.dialogTitle ?? (editingRow.value ? '编辑健康指标' : '添加健康指标')
  },
  get criticalDialogVisible() {
    return dialogRef.value?.criticalDialogVisible
  },
  set criticalDialogVisible(val: boolean) {
    if (dialogRef.value) dialogRef.value.criticalDialogVisible = val
  },
  get criticalMetricName() {
    return dialogRef.value?.criticalMetricName
  },
  get criticalMetricValue() {
    return dialogRef.value?.criticalMetricValue
  },
  get criticalMetricUnit() {
    return dialogRef.value?.criticalMetricUnit
  },
  handleMetricKeyChange: (key: string) => dialogRef.value?.handleMetricKeyChange?.(key),
  handleSwap: () => dialogRef.value?.handleSwap?.(),
  handleSubmit: () => dialogRef.value?.handleSubmit?.(),
  executeSave: () => dialogRef.value?.executeSave?.()
})
</script>

<style scoped>
.metrics-page {
  padding: 0;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 16px;
}

.filter-bar {
  margin-bottom: 16px;
  background: var(--el-fill-color-blank, #ffffff);
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
