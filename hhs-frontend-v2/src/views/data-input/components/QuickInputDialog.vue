<template>
  <el-dialog v-model="visible" :title="metric?.name + '录入'" width="420px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="数值" prop="value">
        <el-input-number
          v-model="form.value"
          :precision="precision"
          :step="step"
          :min="currentRange.min"
          :max="currentRange.max"
          placeholder="请输入数值"
          style="width: 100%"
          @blur="handleValueBlur"
        />
        <div class="range-hint">有效范围: {{ rangeHint }}</div>
      </el-form-item>

      <el-form-item v-if="metric?.key === 'systolicBP'" label="舒张压" prop="diastolicValue">
        <el-input-number
          v-model="form.diastolicValue"
          :precision="0"
          :step="1"
          :min="40"
          :max="200"
          placeholder="舒张压"
          style="width: 100%"
          @blur="handleDiastolicBlur"
        />
        <div class="range-hint">有效范围: 40 - 200 mmHg</div>
      </el-form-item>

      <el-form-item label="单位">
        <el-input :value="metric?.unit" disabled />
      </el-form-item>

      <el-form-item label="日期" prop="recordDate">
        <el-date-picker
          v-model="form.recordDate"
          type="date"
          placeholder="选择日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { healthApi } from '@/api/health'
import { wellnessApi } from '@/api/wellness'

interface MetricRange {
  min: number
  max: number
}

const METRIC_RANGES: Record<string, MetricRange> = {
  glucose: { min: 1.0, max: 25.0 },
  systolicBP: { min: 60, max: 200 },
  heartRate: { min: 30, max: 220 },
  temperature: { min: 30, max: 43 },
  weight: { min: 20, max: 200 },
  bmi: { min: 10, max: 50 },
  sleepDuration: { min: 0, max: 24 },
  steps: { min: 0, max: 100000 },
  waterIntake: { min: 0, max: 10000 },
  mood: { min: 1, max: 5 },
  energy: { min: 1, max: 5 },
  exerciseMinutes: { min: 0, max: 300 }
}

const DEFAULT_RANGE: MetricRange = { min: 0, max: 10000 }

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
  recordDate: new Date().toISOString().split('T')[0]
})

const currentRange = computed<MetricRange>(() => {
  if (!props.metric?.key) return DEFAULT_RANGE
  return METRIC_RANGES[props.metric.key] || DEFAULT_RANGE
})

const rangeHint = computed(() => {
  const r = currentRange.value
  const unit = props.metric?.unit || ''
  return `${r.min} - ${r.max}${unit ? ' ' + unit : ''}`
})

const precision = computed(() => {
  if (['glucose', 'temperature', 'weight', 'bmi'].includes(props.metric?.key)) {
    return 1
  }
  return 0
})

const step = computed(() => {
  if (props.metric?.key === 'glucose') return 0.1
  if (props.metric?.key === 'temperature') return 0.1
  return 1
})

const validateRange = (_rule: any, value: number, callback: any) => {
  if (value === undefined || value === null) {
    callback(new Error('请输入数值'))
    return
  }
  const r = currentRange.value
  if (value < r.min || value > r.max) {
    callback(new Error(`请重新输入，有效范围: ${r.min} - ${r.max}`))
  } else {
    callback()
  }
}

const validateDiastolicRange = (_rule: any, value: number, callback: any) => {
  if (value === undefined || value === null) {
    callback()
    return
  }
  if (value < 40 || value > 200) {
    callback(new Error('请重新输入，有效范围: 40 - 200'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  value: [
    { required: true, message: '请输入数值', trigger: 'change' },
    { validator: validateRange, trigger: 'change' }
  ],
  diastolicValue: [
    { validator: validateDiastolicRange, trigger: 'change' }
  ],
  recordDate: [{ required: true, message: '请选择日期', trigger: 'change' }]
}

watch(visible, val => {
  if (val) {
    form.value = {
      value: undefined,
      diastolicValue: undefined,
      recordDate: new Date().toISOString().split('T')[0]
    }
    setTimeout(() => {
      formRef.value?.clearValidate()
    }, 0)
  }
})

const handleValueBlur = () => {
  const val = form.value.value
  if (val === undefined || val === null) return
  const r = currentRange.value
  if (val < r.min || val > r.max) {
    ElMessage.warning(`数值超出有效范围 ${r.min} - ${r.max}，请重新输入`)
    form.value.value = undefined
    formRef.value?.validateField('value')
  }
}

const handleDiastolicBlur = () => {
  const val = form.value.diastolicValue
  if (val === undefined || val === null) return
  if (val < 40 || val > 200) {
    ElMessage.warning(`舒张压超出有效范围 40 - 200，请重新输入`)
    form.value.diastolicValue = undefined
    formRef.value?.validateField('diastolicValue')
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    loading.value = true
    try {
      const data: any = {
        metricKey: props.metric.key,
        value: form.value.value,
        unit: props.metric.unit,
        recordDate: form.value.recordDate
      }

      if (props.metric.category === 'WELLNESS') {
        await wellnessApi.createMetric(data)
      } else {
        await healthApi.createMetric(data)
      }

      if (props.metric.key === 'systolicBP' && form.value.diastolicValue) {
        const diastolicData = {
          metricKey: 'diastolicBP',
          value: form.value.diastolicValue,
          unit: 'mmHg',
          recordDate: form.value.recordDate
        }
        await healthApi.createMetric(diastolicData)
      }

      ElMessage.success('录入成功')
      emit('success')
      visible.value = false
    } catch (error: any) {
      ElMessage.error(error.message || '录入失败')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.range-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-info);
  line-height: 1.4;
}

.unit-label {
  margin-left: 8px;
  color: var(--color-text-secondary);
}
</style>
