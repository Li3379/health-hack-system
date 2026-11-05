<template>
  <div class="metrics-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>健康指标管理</span>
          <el-button type="primary" @click="showAddDialog">
            <el-icon><Plus /></el-icon>
            添加健康指标
          </el-button>
        </div>
      </template>

      <!-- 筛选区 (全量 16 项医学指标由元数据动态驱动 - HEALTH-01) -->
      <div class="filter-bar">
        <el-form :inline="true" :model="filters">
          <el-form-item label="指标类型">
            <el-select
              v-model="filters.metricKey"
              placeholder="全部指标"
              clearable
              style="width: 180px"
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
      </div>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="metrics" stripe>
        <el-table-column prop="metricKey" label="指标类型" width="130">
          <template #default="{ row }">
            {{ row.metricName || getMetricLabel(row.metricKey) }}
          </template>
        </el-table-column>
        <el-table-column prop="value" label="数值" width="100" />
        <el-table-column prop="unit" label="单位" width="90" />
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

    <!-- 添加/编辑对话框 (append-to-body 脱离动画包含块，全局平铺 - D-04) -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      destroy-on-close
      append-to-body
      class="health-metrics-dialog"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        status-icon
      >
        <!-- 指标类型选择 (动态元数据驱动，新增模式包含血压协同选项，编辑模式锁定 - HEALTH-01, HEALTH-02) -->
        <el-form-item label="指标类型" prop="metricKey">
          <el-select
            v-model="form.metricKey"
            placeholder="请选择健康指标"
            style="width: 100%"
            :disabled="editingId !== null"
            @change="handleMetricKeyChange"
          >
            <el-option
              v-if="editingId === null"
              label="血压 (高低压协同)"
              value="bloodPressure"
            />
            <el-option
              v-for="item in metricMetaStore.healthMetrics"
              :key="item.canonicalKey"
              :label="item.displayName"
              :value="item.canonicalKey"
            />
          </el-select>
        </el-form-item>

        <!-- 血压新增模式协同双字段并列网格 (HEALTH-02) -->
        <template v-if="isBloodPressure">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="收缩压 (高压)" prop="value">
                <el-input-number
                  v-model="form.value"
                  :precision="0"
                  :step="1"
                  :controls="true"
                  :placeholder="placeholder"
                  style="width: 100%"
                />
                <!-- 收缩压状态胶囊 (HEALTH-03) -->
                <div v-if="primaryEval" class="tier-pill-wrapper">
                  <el-tag :type="getTagType(primaryEval.status)" size="small" effect="light">
                    <el-icon><component :is="getTagIcon(primaryEval.status)" /></el-icon>
                    <span>{{ primaryEval.badgeText }}</span>
                  </el-tag>
                </div>
              </el-form-item>
            </el-col>

            <el-col :span="12">
              <el-form-item label="舒张压 (低压)" prop="diastolicValue">
                <el-input-number
                  v-model="form.diastolicValue"
                  :precision="0"
                  :step="1"
                  :controls="true"
                  :placeholder="diastolicPlaceholder"
                  style="width: 100%"
                />
                <!-- 舒张压状态胶囊 (HEALTH-03) -->
                <div v-if="diastolicEval" class="tier-pill-wrapper">
                  <el-tag :type="getTagType(diastolicEval.status)" size="small" effect="light">
                    <el-icon><component :is="getTagIcon(diastolicEval.status)" /></el-icon>
                    <span>{{ diastolicEval.badgeText }}</span>
                  </el-tag>
                </div>
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 血压生理倒置警示条与一键对调按钮 (HEALTH-02) -->
          <el-alert
            v-if="isInverted"
            type="warning"
            show-icon
            :closable="false"
            class="inversion-alert"
          >
            <template #title>
              <div class="inversion-banner-title">
                <span>{{ isBpEqual ? '检测到收缩压与舒张压数值相同，请核对后修改' : '检测到收缩压低于舒张压，疑似输入颠倒' }}</span>
                <el-button
                  v-if="!isBpEqual"
                  size="small"
                  type="warning"
                  plain
                  :icon="Switch"
                  class="swap-btn"
                  @click="handleSwap"
                >
                  对调高低压数值
                </el-button>
              </div>
            </template>
          </el-alert>
        </template>

        <!-- 单项常规指标排版 (编辑模式或非血压指标 - HEALTH-02, HEALTH-03) -->
        <template v-else>
          <el-form-item :label="definition?.displayName || '测量数值'" prop="value">
            <el-input-number
              v-model="form.value"
              :precision="precision"
              :step="step"
              :controls="true"
              :placeholder="placeholder"
              style="width: 100%"
            />
            <!-- 单指标四梯度状态胶囊 -->
            <div v-if="primaryEval" class="tier-pill-wrapper">
              <el-tag :type="getTagType(primaryEval.status)" size="small" effect="light">
                <el-icon><component :is="getTagIcon(primaryEval.status)" /></el-icon>
                <span>{{ primaryEval.badgeText }}</span>
              </el-tag>
              <span class="eval-message">{{ primaryEval.message }}</span>
            </div>
          </el-form-item>
        </template>

        <!-- 单位、记录日期与趋势行 -->
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="测量单位">
              <el-input :value="definition?.unit || form.unit || ''" disabled placeholder="自动获取" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="记录日期" prop="recordDate">
              <el-date-picker
                v-model="form.recordDate"
                type="date"
                placeholder="选择测量日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="趋势" prop="trend">
              <el-select v-model="form.trend" placeholder="请选择" style="width: 100%">
                <el-option label="上升" value="UP" />
                <el-option label="下降" value="DOWN" />
                <el-option label="稳定" value="STABLE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 常态化健康参考标准解读卡片 (HEALTH-03, D-04) -->
        <ReferenceGuideCard :definition="definition" :diastolic-def="diastolicDef" />
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="isSaveBlocked"
            @click="handleSubmit"
          >
            保存健康指标
          </el-button>
        </div>
      </template>

      <!-- 急危重症二次确认模态窗 (HEALTH-03, D-03) -->
      <CriticalConfirmDialog
        v-model="criticalDialogVisible"
        :metric-name="criticalMetricName"
        :value="criticalMetricValue"
        :unit="criticalMetricUnit"
        @confirm="executeSave"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, toRef, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Check, Warning, Bell, CircleClose, Switch, InfoFilled } from '@element-plus/icons-vue'
import { healthApi } from '@/api/health'
import { formatDateTime, formatDate, getMetricLabel } from '@/utils/format'
import { toCanonicalKey } from '@/utils/metricNormalizer'
import { useMetricMetaStore } from '@/stores/metricMeta'
import { useMetricValidation } from '@/composables/useMetricValidation'
import ReferenceGuideCard from '@/views/data-input/components/ReferenceGuideCard.vue'
import CriticalConfirmDialog from '@/views/data-input/components/CriticalConfirmDialog.vue'
import type { HealthMetricVO, HealthMetricRequest } from '@/types/api'

const route = useRoute()
const metricMetaStore = useMetricMetaStore()
const loading = ref(false)
const submitting = ref(false)
const metrics = ref<HealthMetricVO[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加健康指标')
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

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

// 表单响应式模型
const form = reactive({
  metricKey: '',
  value: undefined as number | undefined,
  diastolicValue: undefined as number | undefined,
  recordDate: formatDate(new Date()),
  unit: '',
  trend: ''
})

// 响应式引用桥接至 useMetricValidation
const metricKeyRef = toRef(form, 'metricKey')
const valueRef = toRef(form, 'value')
const diastolicRef = toRef(form, 'diastolicValue')

// 仅在新增模式下开启血压高低压协同双字段联动，编辑单条历史记录时按单项指标精准评估 (D-02)
const isCollaborative = computed(() => {
  return editingId.value === null && (form.metricKey === 'bloodPressure' || form.metricKey === 'systolicBP')
})

const {
  isBloodPressure,
  definition,
  diastolicDef,
  primaryEval,
  diastolicEval,
  isInverted,
  hasCritical,
  isSaveBlocked,
  swapBloodPressure
} = useMetricValidation(metricKeyRef, valueRef, diastolicRef, isCollaborative)

// 动态步长与精度 (HEALTH-01)
const precision = computed(() => definition.value?.precision ?? 0)
const step = computed(() => definition.value?.step ?? 1)

// 动态参考区间占位符 (HEALTH-01)
const placeholder = computed(() => {
  if (definition.value?.normalMin !== undefined && definition.value?.normalMax !== undefined) {
    return `参考范围: ${definition.value.normalMin} ~ ${definition.value.normalMax} ${definition.value.unit}`
  }
  return '请输入数值'
})

const diastolicPlaceholder = computed(() => {
  if (diastolicDef.value?.normalMin !== undefined && diastolicDef.value?.normalMax !== undefined) {
    return `参考范围: ${diastolicDef.value.normalMin} ~ ${diastolicDef.value.normalMax} ${diastolicDef.value.unit}`
  }
  return '舒张压'
})

// 血压高低压相同判定
const isBpEqual = computed(() => {
  return (
    isBloodPressure.value &&
    form.value !== undefined &&
    form.diastolicValue !== undefined &&
    form.value === form.diastolicValue
  )
})

// 倒置状态解除后自动清除校验报错
watch(isInverted, inverted => {
  if (!inverted) {
    formRef.value?.clearValidate(['value', 'diastolicValue'])
  }
})

// 状态标签色彩与图标
const getTagType = (status: string) => {
  switch (status) {
    case 'success':
      return 'success'
    case 'warning':
      return 'warning'
    case 'danger':
    case 'hard_danger':
      return 'danger'
    default:
      return 'info'
  }
}

const getTagIcon = (status: string) => {
  switch (status) {
    case 'success':
      return Check
    case 'warning':
      return Warning
    case 'danger':
      return Bell
    case 'hard_danger':
      return CircleClose
    default:
      return InfoFilled
  }
}

// 血压一键对调事件处理 (HEALTH-02)
const handleSwap = () => {
  swapBloodPressure()
  ElMessage.success('已对调收缩压与舒张压数值')
  formRef.value?.validateField(['value', 'diastolicValue'])
}

// 表单四梯度宽容验证规则 (HEALTH-03)
const validateValue = (_rule: any, value: number, callback: any) => {
  if (value === undefined || value === null || isNaN(value)) {
    callback(new Error('请输入数值'))
    return
  }

  // Tier 4 生理不可能极限强行拦截
  if (primaryEval.value?.isHardLimit) {
    callback(new Error(primaryEval.value.message))
    return
  }

  // 血压倒置强行拦截
  if (isInverted.value) {
    callback(new Error('收缩压不能低于或等于舒张压，请核对或点击下方对调'))
    return
  }

  // Tier 1 正常、Tier 2 预警偏差、Tier 3 急危重症放行保存
  callback()
}

const validateDiastolicValue = (_rule: any, value: number, callback: any) => {
  if (!isBloodPressure.value) {
    callback()
    return
  }

  if (value === undefined || value === null || isNaN(value)) {
    callback(new Error('请输入舒张压数值'))
    return
  }

  if (diastolicEval.value?.isHardLimit) {
    callback(new Error(diastolicEval.value.message))
    return
  }

  if (isInverted.value) {
    callback(new Error('舒张压不能高于或等于收缩压'))
    return
  }

  callback()
}

const rules: FormRules = {
  metricKey: [{ required: true, message: '请选择指标类型', trigger: 'change' }],
  value: [{ required: true, validator: validateValue, trigger: 'change' }],
  diastolicValue: [{ required: true, validator: validateDiastolicValue, trigger: 'change' }],
  recordDate: [{ required: true, message: '请选择记录日期', trigger: 'change' }]
}

// 指标切换时动态读取单位并重置数值，避免跨指标数值污染 (HEALTH-01, WR-01)
const handleMetricKeyChange = (value: string) => {
  const key = value === 'bloodPressure' ? 'systolicBP' : value
  const def = metricMetaStore.getDefinition(key)
  form.unit = def?.unit || (value === 'bloodPressure' ? 'mmHg' : '')
  form.value = undefined
  form.diastolicValue = undefined
  formRef.value?.clearValidate()
}

// 趋势规范化工具
const normalizeTrend = (trend?: string) => {
  return trend ? trend.toUpperCase() : ''
}

const toBackendTrend = (trend?: string) => {
  if (!trend) return ''
  const upper = trend.toUpperCase()
  if (upper === 'UP') return 'up'
  if (upper === 'DOWN') return 'down'
  if (upper === 'STABLE') return 'stable'
  if (upper === 'NORMAL') return 'normal'
  return trend.toLowerCase()
}

// 筛选查询处理（重置页码 - WR-04）
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

// 打开添加弹窗
const showAddDialog = () => {
  dialogTitle.value = '添加健康指标'
  editingId.value = null
  Object.assign(form, {
    metricKey: 'bloodPressure', // 默认选中血压协同选项
    value: undefined,
    diastolicValue: undefined,
    recordDate: formatDate(new Date()),
    unit: 'mmHg',
    trend: ''
  })
  dialogVisible.value = true
  setTimeout(() => {
    formRef.value?.clearValidate()
  }, 0)
}

// 打开编辑弹窗 (单项指标精准适配与 Key 规范化 - HEALTH-02, WR-02)
const handleEdit = (row: HealthMetricVO) => {
  dialogTitle.value = '编辑健康指标'
  editingId.value = row.id
  const canonicalKey = toCanonicalKey(row.metricKey)
  const def = metricMetaStore.getDefinition(canonicalKey)
  Object.assign(form, {
    metricKey: canonicalKey,
    value: row.value,
    diastolicValue: undefined,
    recordDate: row.recordDate,
    unit: row.unit || def?.unit || '',
    trend: normalizeTrend(row.trend)
  })
  dialogVisible.value = true
  setTimeout(() => {
    formRef.value?.clearValidate()
  }, 0)
}

// 急危重症二次确认弹窗控制 (HEALTH-03)
const criticalDialogVisible = ref(false)

const criticalMetricName = computed(() => {
  if (isBloodPressure.value) {
    if (primaryEval.value?.isCritical && diastolicEval.value?.isCritical) {
      return '血压 (收缩压与舒张压)'
    }
    if (primaryEval.value?.isCritical) return '收缩压'
    if (diastolicEval.value?.isCritical) return '舒张压'
  }
  return definition.value?.displayName || form.metricKey || '指标'
})

const criticalMetricValue = computed<number | string>(() => {
  if (isBloodPressure.value) {
    if (primaryEval.value?.isCritical && diastolicEval.value?.isCritical) {
      return `${form.value} / ${form.diastolicValue}`
    }
    if (diastolicEval.value?.isCritical) {
      return form.diastolicValue ?? 0
    }
  }
  return form.value ?? 0
})

const criticalMetricUnit = computed(() => {
  return definition.value?.unit || form.unit || ''
})

// 提交入口：遇急危重症触发二次确认模态窗 (防重入 - WR-03)
const handleSubmit = async () => {
  if (submitting.value) return
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    if (hasCritical.value) {
      criticalDialogVisible.value = true
      return
    }

    await executeSave()
  })
}

// 真正的数据入库执行 (HEALTH-02: 血压协同提交 Promise.all, 编辑单项精准更新, 防重入 - WR-03)
const executeSave = async () => {
  if (submitting.value) return
  submitting.value = true
  try {
    if (isCollaborative.value && form.diastolicValue !== undefined) {
      const systolicPayload: HealthMetricRequest = {
        metricKey: 'systolicBP',
        value: form.value!,
        recordDate: form.recordDate,
        unit: 'mmHg',
        trend: toBackendTrend(form.trend)
      }
      const diastolicPayload: HealthMetricRequest = {
        metricKey: 'diastolicBP',
        value: form.diastolicValue!,
        recordDate: form.recordDate,
        unit: 'mmHg',
        trend: toBackendTrend(form.trend)
      }
      await Promise.all([
        healthApi.createMetric(systolicPayload),
        healthApi.createMetric(diastolicPayload)
      ])
      ElMessage.success('血压数据协同录入成功')
    } else {
      const canonicalKey = definition.value?.canonicalKey || form.metricKey
      const payload: HealthMetricRequest = {
        metricKey: canonicalKey,
        value: form.value!,
        recordDate: form.recordDate,
        unit: form.unit || definition.value?.unit,
        trend: toBackendTrend(form.trend)
      }

      if (editingId.value) {
        await healthApi.updateMetric(editingId.value, payload)
        ElMessage.success('更新成功')
      } else {
        await healthApi.createMetric(payload)
        ElMessage.success('添加成功')
      }
    }

    dialogVisible.value = false
    fetchMetrics()
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
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
    await healthApi.deleteMetric(row.id)
    ElMessage.success('删除成功')
    fetchMetrics()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  metricMetaStore.fetchMetadata()
  fetchMetrics()
  if (route.query.action === 'add') {
    showAddDialog()
  }
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

.health-metrics-dialog :deep(.el-dialog__body) {
  padding: 16px 24px;
}

.tier-pill-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.eval-message {
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
  line-height: 1.3;
}

.inversion-alert {
  margin-bottom: 16px;
}

.inversion-banner-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}

.inversion-banner-title span {
  font-size: 12px;
  font-weight: 500;
}

.swap-btn {
  margin-left: auto;
  font-weight: 600;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
