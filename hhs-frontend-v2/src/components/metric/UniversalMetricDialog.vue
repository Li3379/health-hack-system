<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="560px"
    destroy-on-close
    append-to-body
    class="universal-metric-dialog"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" status-icon>
      <!-- 汇总块必须是 <el-form> 的第一个子节点（Contract 4.3），弹窗与全页表单同一位置。 -->
      <FormErrorSummary
        ref="summaryRef"
        :summary-items="formErrors.summaryItems"
        @focus-field="formErrors.focusField"
      />
      <!-- 指标类型选择行 (仅在非限定单一指标且非编辑模式下允许自由选择) -->
      <el-form-item
        v-if="!isSingleMetricProp"
        label="指标类型"
        prop="metricKey"
        data-field="metricKey"
        :error="formErrors.fieldErrors['metricKey']"
      >
        <el-select
          v-model="form.metricKey"
          placeholder="请选择指标类型"
          :disabled="isEditMode"
          filterable
          style="width: 100%"
          @change="handleMetricKeyChange"
        >
          <el-option
            v-for="opt in metricOptions"
            :key="opt.key"
            :label="opt.displayName"
            :value="opt.key"
          />
        </el-select>
      </el-form-item>

      <!-- 血压高低压双字段并列网格 (仅在新增模式下且为血压协同输入时启用 - GUIDE-03) -->
      <template v-if="isBloodPressure">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item
              label="收缩压 (高压)"
              prop="value"
              data-field="value"
              :error="formErrors.fieldErrors['value']"
            >
              <el-input-number
                v-model="form.value"
                :precision="0"
                :step="1"
                :controls="true"
                :placeholder="placeholder"
                style="width: 100%"
                @update:model-value="handleValueChange"
              />
              <!-- 收缩压状态胶囊 -->
              <div v-if="primaryEval" class="tier-pill-wrapper">
                <el-tag :type="getTagType(primaryEval.status)" size="small" effect="light">
                  <el-icon><component :is="getTagIcon(primaryEval.status)" /></el-icon>
                  <span>{{ primaryEval.badgeText }}</span>
                </el-tag>
              </div>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item
              label="舒张压 (低压)"
              prop="diastolicValue"
              data-field="diastolicValue"
              :error="formErrors.fieldErrors['diastolicValue']"
            >
              <el-input-number
                v-model="form.diastolicValue"
                :precision="0"
                :step="1"
                :controls="true"
                :placeholder="diastolicPlaceholder"
                style="width: 100%"
                @update:model-value="handleDiastolicValueChange"
              />
              <!-- 舒张压状态胶囊 -->
              <div v-if="diastolicEval" class="tier-pill-wrapper">
                <el-tag :type="getTagType(diastolicEval.status)" size="small" effect="light">
                  <el-icon><component :is="getTagIcon(diastolicEval.status)" /></el-icon>
                  <span>{{ diastolicEval.badgeText }}</span>
                </el-tag>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 血压生理倒置警示条与一键对调按钮 (VAL-04) -->
        <el-alert
          v-if="isInverted"
          type="warning"
          show-icon
          :closable="false"
          class="inversion-alert"
        >
          <template #title>
            <div class="inversion-banner-title">
              <span>
                {{
                  isBpEqual
                    ? '检测到收缩压与舒张压数值相同，请核对后修改'
                    : '检测到收缩压低于舒张压，疑似输入颠倒'
                }}
              </span>
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

      <!-- 心情星级打分专用分支 (WELL-02) -->
      <template v-else-if="form.metricKey === 'mood'">
        <el-form-item
          label="心情评级"
          prop="value"
          data-field="value"
          :error="formErrors.fieldErrors['value']"
        >
          <div class="mood-rate-wrapper">
            <el-rate
              v-model="form.value"
              :max="5"
              show-text
              :texts="['很差', '较差', '一般', '较好', '很好']"
              @update:model-value="handleValueChange"
            />
            <span v-if="form.value" class="mood-desc">{{ getMoodDescription(form.value) }}</span>
          </div>
        </el-form-item>
      </template>

      <!-- 单一常规健康/保健指标排版 (编辑模式或非协同模式) -->
      <template v-else>
        <el-form-item
          :label="fieldLabel"
          prop="value"
          data-field="value"
          :error="formErrors.fieldErrors['value']"
        >
          <el-input-number
            v-model="form.value"
            :precision="precision"
            :step="step"
            :controls="true"
            :placeholder="placeholder"
            style="width: 100%"
            @update:model-value="handleValueChange"
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

      <!-- 单位、记录日期与趋势/备注行 -->
      <el-row :gutter="16">
        <el-col :span="isHealthCategory ? 8 : 10">
          <el-form-item label="测量单位">
            <el-input :value="unitDisplay" disabled placeholder="自动获取" />
          </el-form-item>
        </el-col>
        <el-col :span="isHealthCategory ? 10 : 14">
          <el-form-item
            label="记录日期"
            prop="recordDate"
            data-field="recordDate"
            :error="formErrors.fieldErrors['recordDate']"
          >
            <el-date-picker
              v-model="form.recordDate"
              type="date"
              placeholder="选择测量日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 100%"
              @update:model-value="formErrors.clearField('recordDate')"
            />
          </el-form-item>
        </el-col>
        <!-- 健康指标专属趋势选择器 -->
        <el-col v-if="isHealthCategory" :span="6">
          <el-form-item
            label="趋势"
            prop="trend"
            data-field="trend"
            :error="formErrors.fieldErrors['trend']"
          >
            <el-select
              v-model="form.trend"
              placeholder="请选择"
              style="width: 100%"
              clearable
              @change="formErrors.clearField('trend')"
            >
              <el-option label="上升" value="UP" />
              <el-option label="下降" value="DOWN" />
              <el-option label="稳定" value="STABLE" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 保健指标专属备注说明 -->
      <el-form-item
        v-if="isWellnessCategory"
        label="备注说明"
        prop="notes"
        data-field="notes"
        :error="formErrors.fieldErrors['notes']"
      >
        <el-input
          v-model="form.notes"
          type="textarea"
          :rows="2"
          placeholder="填写备忘或相关诱因（可选）"
          maxlength="200"
          show-word-limit
          @update:model-value="formErrors.clearField('notes')"
        />
      </el-form-item>

      <!-- 常态化健康参考标准解读卡片 (GUIDE-02) -->
      <ReferenceGuideCard :definition="definition" :diastolic-def="diastolicDef" />
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="isSaveBlocked"
          @click="handleSubmit"
        >
          {{ isEditMode ? '保存修改' : '保存指标数据' }}
        </el-button>
      </div>
    </template>

    <!-- 急危重症二次确认模态窗 (VAL-05) -->
    <CriticalConfirmDialog
      v-model="criticalDialogVisible"
      :metric-name="criticalMetricName"
      :value="criticalMetricValue"
      :unit="criticalMetricUnit"
      @confirm="executeSave"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, toRef, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Check, Warning, Bell, CircleClose, Switch, InfoFilled } from '@element-plus/icons-vue'
import { healthApi } from '@/api/health'
import { wellnessApi } from '@/api/wellness'
import { formatDate, getMoodDescription } from '@/utils/format'
import { toCanonicalKey } from '@/utils/metricNormalizer'
import { useMetricMetaStore } from '@/stores/metricMeta'
import { useMetricValidation } from '@/composables/useMetricValidation'
import { useFormServerErrors, collectInvalidFieldMessages } from '@/composables/useFormServerErrors'
import { showError } from '@/utils/showError'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import ReferenceGuideCard from '@/views/data-input/components/ReferenceGuideCard.vue'
import CriticalConfirmDialog from '@/views/data-input/components/CriticalConfirmDialog.vue'

interface Props {
  modelValue: boolean
  category?: 'HEALTH' | 'WELLNESS' | 'ALL'
  defaultMetricKey?: string
  metric?: any
  initialData?: any
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  category: 'ALL',
  defaultMetricKey: undefined,
  metric: undefined,
  initialData: null,
  title: undefined
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const metricMetaStore = useMetricMetaStore()
const formRef = ref<FormInstance>()
const summaryRef = ref<InstanceType<typeof FormErrorSummary> | null>(null)
const submitting = ref(false)

/**
 * 一个投影实例服务三种提交模式（血压协同 / 保健 / 标准）。
 *
 * 三种模式互斥：一次弹窗只会激活其中一种，而字段名本身重叠（`value` /
 * `diastolicValue` / `recordDate`），因此开三个实例会让未激活模式的陈旧投影
 * 留在汇总块里。`notes` 刻意为空 —— 它的全局标签就是「备注说明」
 * （Copywriting 契约的覆盖表：保持全局）。
 */
const formErrors = reactive(
  useFormServerErrors({
    fieldOrder: ['metricKey', 'value', 'diastolicValue', 'recordDate', 'trend', 'notes']
  })
)

/** 清投影只清错误文本，绝不清用户输入（Contract 7.1）。 */
const handleValueChange = () => formErrors.clearField('value')
const handleDiastolicValueChange = () => formErrors.clearField('diastolicValue')

const visible = computed({
  get: () => props.modelValue,
  set: val => emit('update:modelValue', val)
})

// 判断是否为编辑模式 (REUSE-02)
const isEditMode = computed(() => {
  return !!props.initialData && props.initialData.id !== undefined && props.initialData.id !== null
})

// 判断是否为固定单一指标模式 (例如由 QuickInput 传入特定 metric 对象)
const isSingleMetricProp = computed(() => {
  return !isEditMode.value && !!props.metric?.key
})

// 表单响应式模型
const form = reactive({
  metricKey: '',
  value: undefined as number | undefined,
  diastolicValue: undefined as number | undefined,
  recordDate: formatDate(new Date()),
  unit: '',
  trend: '',
  notes: ''
})

// 响应式引用桥接至四梯度校验
const metricKeyRef = toRef(form, 'metricKey')
const valueRef = toRef(form, 'value')
const diastolicRef = toRef(form, 'diastolicValue')

// 仅在新增模式下且选择血压时开启高低压协同，编辑单条历史记录按单项指标精准评估 (D-02)
const isCollaborative = computed(() => {
  return (
    !isEditMode.value && (form.metricKey === 'bloodPressure' || form.metricKey === 'systolicBP')
  )
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

// 判断当前指标大类
const isWellnessCategory = computed(() => {
  if (definition.value?.category) {
    return definition.value.category === 'WELLNESS'
  }
  if (props.metric?.category) {
    return props.metric.category === 'WELLNESS'
  }
  return props.category === 'WELLNESS'
})

const isHealthCategory = computed(() => {
  return !isWellnessCategory.value
})

// 动态弹窗标题
const dialogTitle = computed(() => {
  if (props.title) return props.title
  if (isEditMode.value) {
    const name = definition.value?.displayName || form.metricKey || '指标'
    return `编辑${name}`
  }
  if (props.category === 'HEALTH') return '添加健康指标'
  if (props.category === 'WELLNESS') return '添加保健指标'
  if (props.metric?.name) return `${props.metric.name}录入`
  return '添加指标数据'
})

// 指标选择下拉项
const metricOptions = computed(() => {
  if (props.category === 'HEALTH') {
    const list = metricMetaStore.healthMetrics.map(m => ({
      key: m.canonicalKey,
      displayName: m.displayName
    }))
    return isEditMode.value
      ? list
      : [{ key: 'bloodPressure', displayName: '血压 (高低压协同)' }, ...list]
  }
  if (props.category === 'WELLNESS') {
    return metricMetaStore.wellnessMetrics.map(m => ({
      key: m.canonicalKey,
      displayName: m.displayName
    }))
  }
  // ALL
  const healthList = metricMetaStore.healthMetrics.map(m => ({
    key: m.canonicalKey,
    displayName: m.displayName
  }))
  const wellnessList = metricMetaStore.wellnessMetrics.map(m => ({
    key: m.canonicalKey,
    displayName: m.displayName
  }))
  const combined = [...healthList, ...wellnessList]
  return isEditMode.value
    ? combined
    : [{ key: 'bloodPressure', displayName: '血压 (高低压协同)' }, ...combined]
})

// 字段标签
const fieldLabel = computed(() => {
  return definition.value?.displayName || props.metric?.name || '测量数值'
})

// 单位回显
const unitDisplay = computed(() => {
  return definition.value?.unit || form.unit || props.metric?.unit || ''
})

// 动态步长与精度
const precision = computed(() => definition.value?.precision ?? 0)
const step = computed(() => definition.value?.step ?? 1)

// 动态范围占位符
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

// 判断血压高低压是否完全相同
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

// 状态图标与标签颜色映射
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

// 血压一键对调事件处理
const handleSwap = () => {
  swapBloodPressure()
  // 对调改变了两个被校验的值，上一轮投影随之失效（只清错误，不动输入）。
  formErrors.clearField('value')
  formErrors.clearField('diastolicValue')
  ElMessage.success('已对调收缩压与舒张压数值')
  formRef.value?.validateField(['value', 'diastolicValue'])
}

// 心情评级辅助引用与变更方法
const moodValue = computed({
  get: () => (form.metricKey === 'mood' ? (form.value ?? 3) : 3),
  set: val => {
    form.value = val
  }
})

const handleMoodChange = (val: number) => {
  form.value = val
}

// 切换指标下拉项处理
const handleMetricKeyChange = (val: string) => {
  // 用户主动改选即视为放弃该字段的服务端投影（只清错误文本，不动输入值）。
  formErrors.clearField('metricKey')
  const canonical = val === 'bloodPressure' ? 'bloodPressure' : toCanonicalKey(val)
  form.metricKey = canonical
  const key = canonical === 'bloodPressure' ? 'systolicBP' : canonical
  const def = metricMetaStore.getDefinition(key)
  form.unit = def?.unit || (canonical === 'bloodPressure' ? 'mmHg' : '')
  if (canonical === 'mood') {
    form.value = 3
  } else {
    form.value = undefined
  }
  form.diastolicValue = undefined
  form.trend = ''
  form.notes = ''
  formRef.value?.clearValidate()
}

// 表单自定义宽容校验规则 (VAL-02, VAL-03)
const validateValue = (_rule: any, value: number, callback: any) => {
  if (form.metricKey === 'mood') {
    if (value === undefined || value === null || isNaN(value)) {
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

  if (primaryEval.value?.isHardLimit) {
    callback(new Error(primaryEval.value.message))
    return
  }

  if (isInverted.value) {
    callback(new Error('收缩压不能低于或等于舒张压，请核对或点击下方对调'))
    return
  }

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

// 规范化趋势
const normalizeTrend = (trend?: string) => {
  if (!trend) return ''
  const upper = trend.toUpperCase()
  if (upper === 'NORMAL') return 'STABLE'
  if (upper === 'UP' || upper === 'DOWN' || upper === 'STABLE') return upper
  return ''
}

const toBackendTrend = (trend?: string) => {
  if (!trend) return undefined
  const upper = trend.toUpperCase()
  if (upper === 'UP') return 'up'
  if (upper === 'DOWN') return 'down'
  if (upper === 'STABLE' || upper === 'NORMAL') return 'stable'
  return trend.toLowerCase()
}

let isFormFresh = false

// 初始化表单数据
const initForm = (explicitData?: any) => {
  isFormFresh = true
  const targetData = explicitData || props.initialData
  if ((isEditMode.value || !!targetData?.id) && targetData) {
    const rawKey = targetData.metricKey
    const canonicalKey = toCanonicalKey(rawKey)
    const def = metricMetaStore.getDefinition(canonicalKey)
    form.metricKey = canonicalKey
    form.value = targetData.value
    form.diastolicValue = undefined
    form.recordDate = targetData.recordDate
      ? formatDate(targetData.recordDate)
      : formatDate(new Date())
    form.unit = targetData.unit || def?.unit || ''
    form.trend = normalizeTrend(targetData.trend)
    form.notes = targetData.notes || ''
  } else {
    // 新增模式
    let key = ''
    if (props.metric?.key) {
      key = props.metric.key
    } else if (props.defaultMetricKey) {
      key = props.defaultMetricKey
    } else if (props.category === 'HEALTH') {
      key = 'bloodPressure'
    } else if (props.category === 'WELLNESS') {
      key = metricMetaStore.wellnessMetrics[0]?.canonicalKey || 'sleepDuration'
    } else {
      key = 'bloodPressure'
    }

    const canonicalKey = key === 'bloodPressure' ? 'bloodPressure' : toCanonicalKey(key)
    const defKey = canonicalKey === 'bloodPressure' ? 'systolicBP' : canonicalKey
    const def = metricMetaStore.getDefinition(defKey)

    form.metricKey = canonicalKey
    form.value = undefined
    form.diastolicValue = undefined
    form.recordDate = formatDate(new Date())
    form.unit = def?.unit || (canonicalKey === 'bloodPressure' ? 'mmHg' : props.metric?.unit || '')
    form.trend = ''
    form.notes = ''
  }

  nextTick(() => {
    formRef.value?.clearValidate()
  })
}

// 监听弹窗显隐与传入数据变动
watch(
  () => props.modelValue,
  (val, oldVal) => {
    if (val && !oldVal) {
      if (!isFormFresh) {
        initForm()
      }
      isFormFresh = false
    }
  },
  { immediate: true }
)

watch(
  () => props.initialData,
  newVal => {
    if (newVal) {
      initForm(newVal)
    }
  },
  { deep: true }
)

// 急危重症二次确认弹窗控制 (VAL-05)
const criticalDialogVisible = ref(false)

const criticalMetricName = computed(() => {
  if (isBloodPressure.value) {
    if (primaryEval.value?.isCritical && diastolicEval.value?.isCritical) {
      return '血压 (收缩压与舒张压)'
    }
    if (primaryEval.value?.isCritical) return '收缩压'
    if (diastolicEval.value?.isCritical) return '舒张压'
  }
  return definition.value?.displayName || props.metric?.name || form.metricKey || '指标'
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
  return definition.value?.unit || form.unit || props.metric?.unit || ''
})

// 提交入口：遇急危重症触发二次确认模态窗 (VAL-05, T-10-02 防重入)
const handleSubmit = async () => {
  if (submitting.value) return
  if (!formRef.value) return

  // 上一轮的残留投影不进入本次尝试；只清投影，不碰用户输入（ERR-05）。
  formErrors.reset()

  await formRef.value.validate(
    async (valid: boolean, invalidFields?: Record<string, Array<{ message?: string }>>) => {
      if (!valid) {
        // 客户端校验失败与服务端 400 共用同一份汇总通道（GOV.UK 双呈现），零 toast。
        formErrors.setErrors(collectInvalidFieldMessages(invalidFields))
        await nextTick()
        summaryRef.value?.focusSummary()
        return
      }

      if (hasCritical.value) {
        criticalDialogVisible.value = true
        return
      }

      await executeSave()
    }
  )
}

// 真正的数据入库执行 (自适应 API 路由与编辑流转 - REUSE-02)
const executeSave = async () => {
  if (submitting.value) return
  submitting.value = true

  try {
    const isWellness = isWellnessCategory.value
    const canonicalKey = definition.value?.canonicalKey || toCanonicalKey(form.metricKey)
    const currentUnit = definition.value?.unit || form.unit || props.metric?.unit || ''

    if (isEditMode.value) {
      const id = props.initialData.id
      if (isWellness) {
        const payload: any = {
          metricKey: canonicalKey,
          value: form.value as number,
          unit: currentUnit,
          recordDate: form.recordDate
        }
        if (form.notes) payload.notes = form.notes
        await wellnessApi.updateMetric(id, payload)
      } else {
        const payload: any = {
          metricKey: canonicalKey,
          value: form.value as number,
          unit: currentUnit,
          recordDate: form.recordDate
        }
        const backendTrend = toBackendTrend(form.trend)
        if (backendTrend) payload.trend = backendTrend
        await healthApi.updateMetric(id, payload)
      }
      ElMessage.success('修改成功')
    } else {
      // 新增模式
      // 血压是两条独立请求（收缩压 / 舒张压）。这里曾经用 Promise.all，任一 reject 就整体
      // 进 catch：另一条其实已经落库，界面却弹失败、也不发 success（QuickInput 因此不刷新），
      // 用户看到的是「失败」+ 未刷新，DB 里却已有数据。改用 allSettled 让「部分成功」可见：
      // 两条都失败才算失败，只剩一条时明确告诉用户哪一条没存上，并照常刷新列表。
      let partialNote = ''
      if (isBloodPressure.value && form.diastolicValue !== undefined) {
        const systolicData: any = {
          metricKey: 'systolicBP',
          value: form.value as number,
          unit: 'mmHg',
          recordDate: form.recordDate
        }
        const diastolicData: any = {
          metricKey: 'diastolicBP',
          value: form.diastolicValue,
          unit: 'mmHg',
          recordDate: form.recordDate
        }
        const backendTrend = toBackendTrend(form.trend)
        if (backendTrend) {
          systolicData.trend = backendTrend
          diastolicData.trend = backendTrend
        }
        const pressureSaves = [
          { name: '收缩压', request: healthApi.createMetric(systolicData) },
          { name: '舒张压', request: healthApi.createMetric(diastolicData) }
        ]
        const results = await Promise.allSettled(pressureSaves.map(s => s.request))
        const saved = pressureSaves.filter((_, i) => results[i].status === 'fulfilled').map(s => s.name)
        const missed = pressureSaves
          .map((s, i) => (results[i].status === 'rejected' ? { name: s.name, reason: (results[i] as PromiseRejectedResult).reason } : null))
          .filter((entry): entry is { name: string; reason: unknown } => entry !== null)

        if (saved.length === 0) {
          // 两条都没落库才是真的失败：沿用既有错误通道（forbidden / network 等分支照旧）
          throw missed[0].reason
        }
        if (missed.length > 0) {
          partialNote = `${saved.join(' / ')} 已保存；${missed.map(m => m.name).join(' / ')} 未能保存，请补录`
        }
      } else if (isWellness) {
        const data: any = {
          metricKey: canonicalKey,
          value: form.value as number,
          unit: currentUnit,
          recordDate: form.recordDate
        }
        if (form.notes) data.notes = form.notes
        await wellnessApi.createMetric(data)
      } else {
        const data: any = {
          metricKey: canonicalKey,
          value: form.value as number,
          unit: currentUnit,
          recordDate: form.recordDate
        }
        const backendTrend = toBackendTrend(form.trend)
        if (backendTrend) data.trend = backendTrend
        await healthApi.createMetric(data)
      }
      // 部分成功的血压录入不是「成功」也不是「失败」：用 warning 说清哪一半没存上，
      // 而不是用 success 掩盖一条丢失的读数。
      if (partialNote) {
        ElMessage.warning(partialNote)
      } else {
        ElMessage.success('录入成功')
      }
    }

    emit('success')
    visible.value = false
  } catch (error: any) {
    // 弹窗没有 ErrorState 块可落，因此 forbidden / notFound 必须由一条常驻 toast 承担 ——
    // 指标写入路径上 `HealthMetricServiceImpl:219` 会抛 AUTH_FORBIDDEN，裸调用等于静默。
    // 校验失败（validation）由汇总 + 内联呈现，showError 对它零 toast。
    // 刻意不再读 `error.message`：`showError` 已解析出应展示的文案。
    const err = showError(error, 'metric.saveFailed', { presentStates: true })
    if (err.kind === 'validation') {
      formErrors.apply(err)
      await nextTick()
      summaryRef.value?.focusSummary()
    }
  } finally {
    submitting.value = false
  }
}

// 暴露内部属性与方法便于单测与向下兼容
defineExpose({
  form,
  isEditMode,
  isBloodPressure,
  isCollaborative,
  isBpEqual,
  definition,
  diastolicDef,
  primaryEval,
  diastolicEval,
  isInverted,
  hasCritical,
  isSaveBlocked,
  criticalDialogVisible,
  criticalMetricName,
  criticalMetricValue,
  criticalMetricUnit,
  precision,
  step,
  placeholder,
  diastolicPlaceholder,
  dialogTitle,
  moodValue,
  metricOptions,
  isWellnessCategory,
  isHealthCategory,
  handleMoodChange,
  handleMetricKeyChange,
  handleSwap,
  handleSubmit,
  executeSave,
  initForm
})
</script>

<style scoped>
.universal-metric-dialog :deep(.el-dialog__body) {
  padding: 16px 20px;
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
}

.swap-btn {
  margin-left: 12px;
}

.mood-rate-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mood-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
