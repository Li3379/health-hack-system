<template>
  <div class="prevention-metrics-page">
    <el-card shadow="never">
      <template #header>
        <div class="header-actions">
          <span>保健指标记录</span>
          <el-button type="primary" :icon="Plus" @click="showAddDialog">
            添加保健指标
          </el-button>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="metrics" stripe style="width: 100%">
        <el-table-column prop="recordDate" label="记录日期" width="120" sortable />
        <el-table-column prop="metricKey" label="指标类型" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ getMetricLabel(row.metricKey) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="value" label="测量值" width="140">
          <template #default="{ row }">
            {{ formatValue(row.metricKey, row.value) }}
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="100" />
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

    <!-- 通用保健指标录入与编辑弹窗 (REUSE-01, REUSE-02, D-01, D-02) -->
    <UniversalMetricDialog
      ref="dialogRef"
      v-model="dialogVisible"
      category="WELLNESS"
      :title="editingRow ? '编辑保健指标' : '添加保健指标'"
      :initial-data="editingRow"
      @success="fetchMetrics"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { gsap, cleanupScrollTriggers, DUR, EASE } from '@/composables/useGsap'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { wellnessApi } from '@/api/wellness'
import { formatDateTime, getMetricLabel, getMoodDescription } from '@/utils/format'
import { useMetricMetaStore } from '@/stores/metricMeta'
import UniversalMetricDialog from '@/components/metric/UniversalMetricDialog.vue'
import type { WellnessMetricVO } from '@/types/api'

const metricMetaStore = useMetricMetaStore()
const loading = ref(false)
const metrics = ref<WellnessMetricVO[]>([])
const dialogVisible = ref(false)
const dialogRef = ref<any>()
const editingRow = ref<WellnessMetricVO | null>(null)

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

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
  editingRow.value = null
  dialogVisible.value = true
  if (dialogRef.value) {
    dialogRef.value.initForm()
  }
}

const handleEdit = (row: WellnessMetricVO) => {
  editingRow.value = row
  dialogVisible.value = true
  if (dialogRef.value) {
    dialogRef.value.initForm(row)
  }
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
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('删除失败')
    }
  }
}

// 格式化数值展示
const formatValue = (key: string, value: number): string => {
  if (key === 'mood') {
    return `${value} (${getMoodDescription(value)})`
  }
  return String(value)
}

onMounted(() => {
  metricMetaStore.fetchMetadata()
  fetchMetrics()

  nextTick(() => {
    const tl = gsap.timeline({ defaults: { ease: EASE.out } })
    tl.fromTo(
      '.el-card',
      { y: 20, autoAlpha: 0 },
      { y: 0, autoAlpha: 1, duration: DUR.mid, clearProps: 'transform,autoAlpha,visibility,opacity' }
    )
    tl.fromTo(
      '.header-actions',
      { y: -10, autoAlpha: 0 },
      { y: 0, autoAlpha: 1, duration: DUR.mid, clearProps: 'transform,autoAlpha,visibility,opacity' },
      '-=0.2'
    )
  })
})

onUnmounted(() => {
  cleanupScrollTriggers()
})

// 转发暴露属性与方法以实现 100% 单元测试向后兼容
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
  get editingId() {
    return editingRow.value?.id ?? null
  },
  get dialogTitle() {
    return dialogRef.value?.dialogTitle ?? (editingRow.value ? '编辑保健指标' : '添加保健指标')
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
  get moodValue() {
    return dialogRef.value?.moodValue
  },
  set moodValue(val) {
    if (dialogRef.value) dialogRef.value.moodValue = val
  },
  get primaryEval() {
    return dialogRef.value?.primaryEval
  },
  get hasCritical() {
    return dialogRef.value?.hasCritical
  },
  get isSaveBlocked() {
    return dialogRef.value?.isSaveBlocked
  },
  get criticalDialogVisible() {
    return dialogRef.value?.criticalDialogVisible
  },
  set criticalDialogVisible(val: boolean) {
    if (dialogRef.value) dialogRef.value.criticalDialogVisible = val
  },
  handleMoodChange: (val: number) => dialogRef.value?.handleMoodChange?.(val),
  handleMetricKeyChange: (key: string) => dialogRef.value?.handleMetricKeyChange?.(key),
  handleSubmit: () => dialogRef.value?.handleSubmit?.(),
  executeSave: () => dialogRef.value?.executeSave?.()
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
</style>
