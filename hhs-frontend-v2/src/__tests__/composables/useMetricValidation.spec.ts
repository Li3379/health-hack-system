import { describe, it, expect, beforeEach } from 'vitest'
import { ref } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { useMetricValidation } from '@/composables/useMetricValidation'

describe('useMetricValidation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should reactively evaluate general metric values', () => {
    const metricKey = ref<string | undefined>('bloodGlucose')
    const value = ref<number | undefined>(5.0)

    const { primaryEval, isBloodPressure, isSaveBlocked, hasCritical, definition } =
      useMetricValidation(metricKey, value)

    expect(isBloodPressure.value).toBe(false)
    expect(definition.value?.displayName).toBe('血糖')
    expect(primaryEval.value?.tier).toBe('NORMAL')
    expect(primaryEval.value?.canSave).toBe(true)
    expect(isSaveBlocked.value).toBe(false)
    expect(hasCritical.value).toBe(false)

    // Update to warning deviation (10.0 mmol/L)
    value.value = 10.0
    expect(primaryEval.value?.tier).toBe('WARNING')
    expect(primaryEval.value?.canSave).toBe(true)
    expect(isSaveBlocked.value).toBe(false)

    // Update to critical panic limit (18.0 mmol/L)
    value.value = 18.0
    expect(primaryEval.value?.tier).toBe('CRITICAL')
    expect(hasCritical.value).toBe(true)
    expect(isSaveBlocked.value).toBe(false) // Critical does not block save!

    // Update to impossible limit (40.0 mmol/L)
    value.value = 40.0
    expect(primaryEval.value?.tier).toBe('HARD_BOUND')
    expect(isSaveBlocked.value).toBe(true) // Hard bound blocks save
  })

  it('should correctly evaluate blood pressure and support dual-field linkage', () => {
    const metricKey = ref<string | undefined>('systolicBP')
    const systolic = ref<number | undefined>(120)
    const diastolic = ref<number | undefined>(80)

    const {
      isBloodPressure,
      primaryEval,
      diastolicEval,
      isInverted,
      isSaveBlocked,
      hasCritical
    } = useMetricValidation(metricKey, systolic, diastolic)

    expect(isBloodPressure.value).toBe(true)
    expect(primaryEval.value?.tier).toBe('NORMAL')
    expect(diastolicEval.value?.tier).toBe('NORMAL')
    expect(isInverted.value).toBe(false)
    expect(isSaveBlocked.value).toBe(false)
    expect(hasCritical.value).toBe(false)
  })

  it('should detect blood pressure inversion and perform atomic value swapping', () => {
    const metricKey = ref<string | undefined>('systolicBP')
    const systolic = ref<number | undefined>(80) // Mistakenly entered low pressure
    const diastolic = ref<number | undefined>(120) // Mistakenly entered high pressure

    const { isInverted, isSaveBlocked, swapBloodPressure } = useMetricValidation(
      metricKey,
      systolic,
      diastolic
    )

    // Inverted state: 80 <= 120
    expect(isInverted.value).toBe(true)
    expect(isSaveBlocked.value).toBe(true)

    // Execute atomic swap
    swapBloodPressure()

    expect(systolic.value).toBe(120)
    expect(diastolic.value).toBe(80)
    expect(isInverted.value).toBe(false)
    expect(isSaveBlocked.value).toBe(false)
  })

  it('should report hasCritical when either systolic or diastolic is in critical zone', () => {
    const metricKey = ref<string | undefined>('systolicBP')
    const systolic = ref<number | undefined>(120)
    const diastolic = ref<number | undefined>(115) // Panic limit: diastolic >= 110

    const { hasCritical, isSaveBlocked } = useMetricValidation(
      metricKey,
      systolic,
      diastolic
    )

    expect(hasCritical.value).toBe(true)
    expect(isSaveBlocked.value).toBe(false)
  })

  it('should disable blood pressure collaboration when collaborativeRef is false', () => {
    const metricKey = ref<string | undefined>('systolicBP')
    const systolic = ref<number | undefined>(120)
    const diastolic = ref<number | undefined>(80)
    const collaborative = ref(false)

    const {
      isBloodPressure,
      diastolicDef,
      diastolicEval,
      isInverted
    } = useMetricValidation(metricKey, systolic, diastolic, collaborative)

    expect(isBloodPressure.value).toBe(false)
    expect(diastolicDef.value).toBeUndefined()
    expect(diastolicEval.value).toBeNull()
    expect(isInverted.value).toBe(false)
  })
})
