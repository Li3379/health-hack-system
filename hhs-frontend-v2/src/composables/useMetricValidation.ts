import { computed, type Ref } from 'vue'
import { useMetricMetaStore } from '@/stores/metricMeta'
import { toCanonicalKey } from '@/utils/metricNormalizer'
import {
  evaluateMetricValue,
  checkBloodPressureInversion,
  type MetricEvaluation
} from '@/utils/metricValidation'
import type { MetricDefinitionVO } from '@/types/api'

export function useMetricValidation(
  metricKeyRef: Ref<string | undefined>,
  valueRef: Ref<number | undefined>,
  diastolicRef?: Ref<number | undefined>
) {
  const metaStore = useMetricMetaStore()

  // 规范化指标标识符
  const canonicalKey = computed<string>(() => {
    return toCanonicalKey(metricKeyRef.value || '')
  })

  // 判定是否为血压协同指标
  const isBloodPressure = computed<boolean>(() => {
    const key = canonicalKey.value
    return key === 'systolicBP' || metricKeyRef.value === 'bloodPressure'
  })

  // 主指标权威元数据定义
  const definition = computed<MetricDefinitionVO | undefined>(() => {
    if (!metricKeyRef.value) return undefined
    return metaStore.getDefinition(metricKeyRef.value)
  })

  // 舒张压权威元数据定义
  const diastolicDef = computed<MetricDefinitionVO | undefined>(() => {
    if (!isBloodPressure.value) return undefined
    return metaStore.getDefinition('diastolicBP')
  })

  // 主指标四梯度生理评估
  const primaryEval = computed<MetricEvaluation | null>(() => {
    return evaluateMetricValue(valueRef.value, definition.value)
  })

  // 舒张压四梯度生理评估
  const diastolicEval = computed<MetricEvaluation | null>(() => {
    if (!isBloodPressure.value || !diastolicRef) return null
    return evaluateMetricValue(diastolicRef.value, diastolicDef.value)
  })

  // 血压生理倒置异常判定（收缩压 <= 舒张压）
  const isInverted = computed<boolean>(() => {
    if (!isBloodPressure.value || !diastolicRef) return false
    return checkBloodPressureInversion(valueRef.value, diastolicRef.value)
  })

  // 是否触碰急危重症 Panic Limits（主值或舒张压任一触发）
  const hasCritical = computed<boolean>(() => {
    return (primaryEval.value?.isCritical ?? false) || (diastolicEval.value?.isCritical ?? false)
  })

  // 保存阻断状态：仅在触碰 Tier 4 生理不可能极限或血压倒置时阻断保存
  // 正常(Tier 1)、预警偏差(Tier 2)、急危重症(Tier 3)均不属于阻断范畴
  const isSaveBlocked = computed<boolean>(() => {
    if (primaryEval.value?.isHardLimit) return true
    if (diastolicEval.value?.isHardLimit) return true
    if (isInverted.value) return true
    return false
  })

  // 血压高低压数值原子一键对调方法 (per D-04)
  const swapBloodPressure = () => {
    if (!isBloodPressure.value || !diastolicRef) return
    const temp = valueRef.value
    valueRef.value = diastolicRef.value
    diastolicRef.value = temp
  }

  return {
    canonicalKey,
    isBloodPressure,
    definition,
    diastolicDef,
    primaryEval,
    diastolicEval,
    isInverted,
    hasCritical,
    isSaveBlocked,
    swapBloodPressure
  }
}
