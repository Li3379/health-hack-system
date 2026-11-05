<template>
  <UniversalMetricDialog
    ref="dialogRef"
    v-bind="$attrs"
    :model-value="modelValue"
    :metric="metric"
    :category="metric?.category || 'ALL'"
    @update:model-value="$emit('update:modelValue', $event)"
    @success="$emit('success')"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import UniversalMetricDialog from '@/components/metric/UniversalMetricDialog.vue'

defineProps<{
  modelValue: boolean
  metric?: any
}>()

defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const dialogRef = ref<any>()

// 向前兼容暴露全部属性与方法 (保持 QuickInputDialog.spec.ts 零修改通过)
defineExpose({
  get form() {
    return dialogRef.value?.form
  },
  get isBloodPressure() {
    return dialogRef.value?.isBloodPressure
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
  get criticalDialogVisible() {
    return dialogRef.value?.criticalDialogVisible
  },
  set criticalDialogVisible(val) {
    if (dialogRef.value) {
      dialogRef.value.criticalDialogVisible = val
    }
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
  handleSwap: () => dialogRef.value?.handleSwap(),
  handleSubmit: () => dialogRef.value?.handleSubmit(),
  executeSave: () => dialogRef.value?.executeSave()
})
</script>
