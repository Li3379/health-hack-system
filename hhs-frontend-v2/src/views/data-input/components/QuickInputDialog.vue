<template>
  <el-dialog v-model="visible" :title="metric?.name + '录入'" width="400px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="数值" prop="value">
        <el-input-number
          v-model="form.value"
          :precision="precision"
          :step="step"
          :min="min"
          :max="max"
          placeholder="请输入数值"
          style="width: 100%"
        />
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
        />
        <span class="unit-label">mmHg</span>
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

// 根据指标类型设置精度和步长
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

const min = computed(() => {
  if (props.metric?.key === 'glucose') return 0
  if (props.metric?.key === 'heartRate') return 20
  if (props.metric?.key === 'temperature') return 30
  if (props.metric?.key === 'weight') return 20
  if (props.metric?.key === 'systolicBP') return 60
  if (props.metric?.key === 'steps') return 0
  if (props.metric?.key === 'sleepDuration') return 0
  return 0
})

const max = computed(() => {
  if (props.metric?.key === 'glucose') return 50
  if (props.metric?.key === 'heartRate') return 300
  if (props.metric?.key === 'temperature') return 45
  if (props.metric?.key === 'weight') return 300
  if (props.metric?.key === 'systolicBP') return 250
  if (props.metric?.key === 'steps') return 100000
  if (props.metric?.key === 'sleepDuration') return 24
  return 10000
})

// 指标范围验证器
const validateRange = (rule: any, value: number, callback: any) => {
  if (value === undefined || value === null) {
    callback(new Error('请输入数值'))
    return
  }
  const minVal = min.value
  const maxVal = max.value
  if (value < minVal || value > maxVal) {
    callback(new Error(`数值应在 ${minVal}-${maxVal} 之间`))
  } else {
    callback()
  }
}

const rules: FormRules = {
  value: [
    { required: true, message: '请输入数值', trigger: 'blur' },
    { validator: validateRange, trigger: 'blur' }
  ],
  recordDate: [{ required: true, message: '请选择日期', trigger: 'change' }]
}

// 重置表单
watch(visible, val => {
  if (val) {
    form.value = {
      value: undefined,
      diastolicValue: undefined,
      recordDate: new Date().toISOString().split('T')[0]
    }
  }
})

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

      // 如果是血压，还需要保存舒张压
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
.unit-label {
  margin-left: 8px;
  color: var(--color-text-secondary);
}
</style>
