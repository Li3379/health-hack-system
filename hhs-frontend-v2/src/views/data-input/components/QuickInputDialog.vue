<template>
  <el-dialog
    v-model="visible"
    :title="(definition?.displayName || metric?.name || '指标') + '录入'"
    width="560px"
    destroy-on-close
    append-to-body
    class="quick-input-dialog"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" status-icon>
      <!-- 血压双字段同屏并列协同排版 (GUIDE-03) -->
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
            <el-form-item label="舒张压 (低压)" prop="diastolicValue">
              <el-input-number
                v-model="form.diastolicValue"
                :precision="0"
                :step="1"
                :controls="true"
                :placeholder="diastolicPlaceholder"
                style="width: 100%"
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

      <!-- 单一常规健康/保健指标排版 -->
      <template v-else>
        <el-form-item :label="definition?.displayName || metric?.name || '测量数值'" prop="value">
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

      <!-- 单位与日期行 -->
      <el-row :gutter="16">
        <el-col :span="10">
          <el-form-item label="测量单位">
            <el-input :value="definition?.unit || metric?.unit || ''" disabled />
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

      <!-- 常态化健康参考标准解读卡片 (GUIDE-02) -->
      <ReferenceGuideCard :definition="definition" :diastolic-def="diastolicDef" />
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="loading"
          :disabled="isSaveBlocked"
          @click="handleSubmit"
        >
          保存指标数据
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
import { ref, computed, watch, toRef } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Check, Warning, Bell, CircleClose, Switch, InfoFilled } from '@element-plus/icons-vue'
import { healthApi } from '@/api/health'
import { wellnessApi } from '@/api/wellness'
import { formatDate } from '@/utils/format'
import { useMetricValidation } from '@/composables/useMetricValidation'
import ReferenceGuideCard from './ReferenceGuideCard.vue'
import CriticalConfirmDialog from './CriticalConfirmDialog.vue'

const props = defineProps<{
  modelValue: boolean
  metric: any
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: val => emit('update:modelValue', val)
})

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = ref({
  value: undefined as number | undefined,
  diastolicValue: undefined as number | undefined,
  recordDate: formatDate(new Date())
})

const metricKeyRef = computed(() => props.metric?.key)
const valueRef = toRef(form.value, 'value')
const diastolicRef = toRef(form.value, 'diastolicValue')

// 引入四梯度校验组合函数 (VAL-02, VAL-04, GUIDE-03)
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
} = useMetricValidation(metricKeyRef, valueRef, diastolicRef)

// 判断血压高低压是否完全相同
const isBpEqual = computed(() => {
  return (
    isBloodPressure.value &&
    form.value.value !== undefined &&
    form.value.diastolicValue !== undefined &&
    form.value.value === form.value.diastolicValue
  )
})

// 倒置状态解除后自动清除校验报错
watch(isInverted, inverted => {
  if (!inverted) {
    formRef.value?.clearValidate(['value', 'diastolicValue'])
  }
})

// 动态步长与精度 (GUIDE-01)
const precision = computed(() => definition.value?.precision ?? 0)
const step = computed(() => definition.value?.step ?? 1)

// 动态范围占位符 (GUIDE-01)
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

// 状态图标与标签颜色映射
const getTagType = (status: string) => {
  switch (status) {
    case 'success':
      return 'success'
    case 'warning':
      return 'warning'
    case 'danger':
      return 'danger'
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

// 血压一键对调事件处理 (VAL-04)
const handleSwap = () => {
  swapBloodPressure()
  ElMessage.success('已对调收缩压与舒张压数值')
  formRef.value?.validateField(['value', 'diastolicValue'])
}

// 表单自定义宽容校验规则 (VAL-02, VAL-03: 允许正常与预警偏差通过)
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

  // Tier 1 正常、Tier 2 预警偏差、Tier 3 急危重症均放行
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
  value: [{ required: true, validator: validateValue, trigger: 'change' }],
  diastolicValue: [{ required: true, validator: validateDiastolicValue, trigger: 'change' }],
  recordDate: [{ required: true, message: '请选择记录日期', trigger: 'change' }]
}

// 弹窗重置处理
watch(visible, val => {
  if (val) {
    form.value.value = undefined
    form.value.diastolicValue = undefined
    form.value.recordDate = formatDate(new Date())
    setTimeout(() => {
      formRef.value?.clearValidate()
    }, 0)
  }
})

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
  return definition.value?.displayName || props.metric?.name || '指标'
})

const criticalMetricValue = computed<number | string>(() => {
  if (isBloodPressure.value) {
    if (primaryEval.value?.isCritical && diastolicEval.value?.isCritical) {
      return `${form.value.value} / ${form.value.diastolicValue}`
    }
    if (diastolicEval.value?.isCritical) {
      return form.value.diastolicValue ?? 0
    }
  }
  return form.value.value ?? 0
})

const criticalMetricUnit = computed(() => {
  return definition.value?.unit || props.metric?.unit || ''
})

// 提交入口：遇急危重症触发二次确认弹窗 (VAL-05)
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    // 检查是否存在急危重症 Panic Limits
    if (hasCritical.value) {
      criticalDialogVisible.value = true
      return
    }

    await executeSave()
  })
}

// 真正的数据入库执行
const executeSave = async () => {
  loading.value = true
  try {
    const canonical = definition.value?.canonicalKey || props.metric?.key
    const data: any = {
      metricKey: canonical,
      value: form.value.value,
      unit: definition.value?.unit || props.metric?.unit,
      recordDate: form.value.recordDate
    }

    const isWellness = (definition.value?.category || props.metric?.category) === 'WELLNESS'

    if (isBloodPressure.value && form.value.diastolicValue !== undefined) {
      const diastolicData = {
        metricKey: 'diastolicBP',
        value: form.value.diastolicValue,
        unit: 'mmHg',
        recordDate: form.value.recordDate
      }
      await Promise.all([
        healthApi.createMetric(data),
        healthApi.createMetric(diastolicData)
      ])
    } else if (isWellness) {
      await wellnessApi.createMetric(data)
    } else {
      await healthApi.createMetric(data)
    }

    ElMessage.success('录入成功')
    emit('success')
    visible.value = false
  } catch (error: any) {
    ElMessage.error(error.message || '录入失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.quick-input-dialog :deep(.el-dialog__body) {
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
