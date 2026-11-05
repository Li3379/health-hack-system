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
            {{ row.metricName || metricMetaStore.getDefinition(row.metricKey)?.displayName || getMetricLabel(row.metricKey) }}
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
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      destroy-on-close
      append-to-body
      class="prevention-metrics-dialog"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        status-icon
      >
        <!-- 指标类型选择 (动态元数据驱动) -->
        <el-form-item label="指标类型" prop="metricKey">
          <el-select
            v-model="form.metricKey"
            placeholder="请选择保健指标"
            style="width: 100%"
            :disabled="editingId !== null"
            @change="handleMetricKeyChange"
          >
            <el-option
              v-for="item in metricMetaStore.wellnessMetrics"
              :key="item.canonicalKey"
              :label="item.displayName"
              :value="item.canonicalKey"
            />
          </el-select>
        </el-form-item>

        <!-- 心情特殊输入 (1-5 星级打分) -->
        <el-form-item v-if="form.metricKey === 'mood'" label="心情状态" prop="value">
          <div class="mood-input">
            <el-rate
              v-model="moodValue"
              :max="5"
              show-text
              :texts="['很差', '较差', '一般', '较好', '很好']"
              @change="handleMoodChange"
            />
          </div>
          <!-- 心情状态胶囊 -->
          <div v-if="primaryEval" class="tier-pill-wrapper">
            <el-tag :type="getTagType(primaryEval.status)" size="small" effect="light">
              <el-icon><component :is="getTagIcon(primaryEval.status)" /></el-icon>
              <span>{{ primaryEval.badgeText }}</span>
            </el-tag>
            <span class="eval-message">{{ primaryEval.message }}</span>
          </div>
        </el-form-item>

        <!-- 其他通用数值型生活方式指标 -->
        <el-form-item v-else :label="definition?.displayName || '测量数值'" prop="value">
          <el-input-number
            v-model="form.value"
            :precision="precision"
            :step="step"
            :controls="true"
            :placeholder="placeholder"
            style="width: 100%"
          />
          <!-- 四梯度状态胶囊微标签 -->
          <div v-if="primaryEval" class="tier-pill-wrapper">
            <el-tag :type="getTagType(primaryEval.status)" size="small" effect="light">
              <el-icon><component :is="getTagIcon(primaryEval.status)" /></el-icon>
              <span>{{ primaryEval.badgeText }}</span>
            </el-tag>
            <span class="eval-message">{{ primaryEval.message }}</span>
          </div>
        </el-form-item>

        <!-- 单位与记录日期行 -->
        <el-row :gutter="16">
          <el-col :span="10">
            <el-form-item label="测量单位">
              <el-input :value="definition?.unit || form.unit || ''" disabled placeholder="自动获取" />
            </el-form-item>
          </el-col>
          <el-col :span="14">
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
        </el-row>

        <!-- 常态化健康参考标准解读卡片 -->
        <ReferenceGuideCard :definition="definition" />
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
            {{ editingId ? '保存修改' : '确定' }}
          </el-button>
        </div>
      </template>

      <!-- 急危重症二次确认模态窗 -->
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
import { ref, reactive, computed, toRef, onMounted, onUnmounted, nextTick } from 'vue'
import { gsap, cleanupScrollTriggers, DUR, EASE } from '@/composables/useGsap'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Check, Warning, Bell, CircleClose, InfoFilled } from '@element-plus/icons-vue'
import { wellnessApi } from '@/api/wellness'
import { formatDateTime, formatDate, getMetricLabel, getMoodDescription } from '@/utils/format'
import { useMetricMetaStore } from '@/stores/metricMeta'
import { useMetricValidation } from '@/composables/useMetricValidation'
import ReferenceGuideCard from '@/views/data-input/components/ReferenceGuideCard.vue'
import CriticalConfirmDialog from '@/views/data-input/components/CriticalConfirmDialog.vue'
import type { WellnessMetricVO, WellnessMetricRequest } from '@/types/api'

const metricMetaStore = useMetricMetaStore()
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
  value: undefined as any,
  unit: '',
  recordDate: formatDate(new Date())
})

const metricKeyRef = toRef(form, 'metricKey')
const valueRef = toRef(form, 'value')

// 引入四梯度响应式生理校验与元数据
const {
  definition,
  primaryEval,
  hasCritical,
  isSaveBlocked
} = useMetricValidation(metricKeyRef, valueRef)

// 动态步长与精度
const precision = computed(() => definition.value?.precision ?? 0)
const step = computed(() => definition.value?.step ?? 1)

// 动态参考区间占位符
const placeholder = computed(() => {
  if (definition.value?.normalMin !== undefined && definition.value?.normalMax !== undefined) {
    return `参考范围: ${definition.value.normalMin} ~ ${definition.value.normalMax} ${definition.value.unit}`
  }
  return '请输入数值'
})

// 心情星级值（1-5）
const moodValue = computed({
  get: () => (form.value !== undefined ? Math.round(form.value) : 3),
  set: (val: number) => {
    form.value = val
  }
})

const handleMoodChange = (val: number) => {
  form.value = val
}

const handleMetricKeyChange = (key: string) => {
  const def = metricMetaStore.getDefinition(key)
  form.unit = def?.unit || ''
  if (key === 'mood') {
    if (!form.value || form.value < 1 || form.value > 5) {
      form.value = 3
    }
  }
  formRef.value?.clearValidate('value')
}

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

// 表单非破坏性宽容校验规则
const validateValue = (_rule: any, value: number, callback: any) => {
  if (form.metricKey === 'mood') {
    if (value === undefined || value === null || value < 1 || value > 5) {
      callback(new Error('请选择心情评级'))
      return
    }
    callback()
    return
  }

  if (value === undefined || value === null || isNaN(value)) {
    callback(new Error('请输入数值'))
    return
  }

  // Tier 4 生理不可能极限强行拦截
  if (primaryEval.value?.isHardLimit) {
    callback(new Error(primaryEval.value.message))
    return
  }

  // Tier 1 正常、Tier 2 预警偏差、Tier 3 急危重症放行
  callback()
}

const rules: FormRules = {
  metricKey: [{ required: true, message: '请选择指标类型', trigger: 'change' }],
  value: [{ required: true, validator: validateValue, trigger: 'change' }],
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
  const defaultKey = metricMetaStore.wellnessMetrics[0]?.canonicalKey || 'steps'
  const def = metricMetaStore.getDefinition(defaultKey)
  Object.assign(form, {
    metricKey: defaultKey,
    value: undefined,
    unit: def?.unit || '',
    recordDate: formatDate(new Date())
  })
  dialogVisible.value = true
  setTimeout(() => {
    formRef.value?.clearValidate()
  }, 0)
}

const handleEdit = (row: WellnessMetricVO) => {
  dialogTitle.value = '编辑保健指标'
  editingId.value = row.id
  const def = metricMetaStore.getDefinition(row.metricKey)
  Object.assign(form, {
    metricKey: row.metricKey,
    value: row.value,
    unit: row.unit || def?.unit || '',
    recordDate: row.recordDate
  })
  dialogVisible.value = true
  setTimeout(() => {
    formRef.value?.clearValidate()
  }, 0)
}

// 急危重症二次确认模态窗控制
const criticalDialogVisible = ref(false)

const criticalMetricName = computed(() => {
  return definition.value?.displayName || form.metricKey || '指标'
})

const criticalMetricValue = computed<number | string>(() => {
  return form.value ?? 0
})

const criticalMetricUnit = computed(() => {
  return definition.value?.unit || form.unit || ''
})

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

const executeSave = async () => {
  if (submitting.value) return
  submitting.value = true
  try {
    const canonicalKey = definition.value?.canonicalKey || form.metricKey
    const payload: WellnessMetricRequest = {
      metricKey: canonicalKey,
      value: form.value!,
      unit: form.unit || definition.value?.unit,
      recordDate: form.recordDate
    }

    if (editingId.value) {
      await wellnessApi.updateMetric(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await wellnessApi.createMetric(payload)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    fetchMetrics()
  } catch (error) {
    ElMessage.error(editingId.value ? '更新失败' : '添加失败')
  } finally {
    submitting.value = false
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
    tl.fromTo('.el-card',
      { y: 20, autoAlpha: 0 },
      { y: 0, autoAlpha: 1, duration: DUR.mid, clearProps: 'transform,autoAlpha,visibility,opacity' }
    )
    tl.fromTo('.header-actions',
      { y: -10, autoAlpha: 0 },
      { y: 0, autoAlpha: 1, duration: DUR.mid, clearProps: 'transform,autoAlpha,visibility,opacity' }
    , '-=0.2')
  })
})

onUnmounted(() => {
  cleanupScrollTriggers()
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
  padding: 8px 0;
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
