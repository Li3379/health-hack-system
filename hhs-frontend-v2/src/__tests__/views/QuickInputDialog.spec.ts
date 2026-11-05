import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import QuickInputDialog from '@/views/data-input/components/QuickInputDialog.vue'
import { healthApi } from '@/api/health'
import { wellnessApi } from '@/api/wellness'

vi.mock('@/api/health', () => ({
  healthApi: {
    createMetric: vi.fn().mockResolvedValue({ code: 200, message: 'success' })
  }
}))

vi.mock('@/api/wellness', () => ({
  wellnessApi: {
    createMetric: vi.fn().mockResolvedValue({ code: 200, message: 'success' })
  }
}))

const globalStubs = {
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose']
  },
  'el-form': {
    template: '<form class="el-form"><slot /></form>',
    methods: {
      validate: (cb: (valid: boolean) => void) => cb(true),
      validateField: vi.fn(),
      clearValidate: vi.fn()
    }
  },
  'el-form-item': {
    template: '<div class="el-form-item"><slot /></div>',
    props: ['label', 'prop', 'labelWidth']
  },
  'el-row': {
    template: '<div class="el-row"><slot /></div>',
    props: ['gutter']
  },
  'el-col': {
    template: '<div class="el-col"><slot /></div>',
    props: ['span']
  },
  'el-input-number': {
    template: '<input class="el-input-number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
    props: ['modelValue', 'precision', 'step', 'controls', 'placeholder']
  },
  'el-input': {
    template: '<input class="el-input" :value="value" />',
    props: ['value', 'disabled']
  },
  'el-date-picker': {
    template: '<input class="el-date-picker" :value="modelValue" />',
    props: ['modelValue', 'type', 'placeholder', 'format', 'valueFormat']
  },
  'el-tag': {
    template: '<span class="el-tag"><slot /></span>',
    props: ['type', 'size', 'effect']
  },
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /><slot /></div>',
    props: ['type', 'showIcon', 'closable']
  },
  'el-button': {
    template: '<button class="el-button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'plain', 'icon']
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  ReferenceGuideCard: {
    template: '<div class="reference-guide-card-stub">Reference Guide</div>',
    props: ['definition', 'diastolicDef']
  },
  CriticalConfirmDialog: {
    template: '<div class="critical-confirm-dialog-stub" v-if="modelValue"><button class="confirm-btn" @click="$emit(\'confirm\')">Confirm</button></div>',
    props: ['modelValue', 'metricName', 'value', 'unit']
  }
}

describe('QuickInputDialog.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('VAL-01: should non-destructively preserve user input even when value is outside normal bounds', async () => {
    const wrapper = mount(QuickInputDialog, {
      props: {
        modelValue: true,
        metric: { key: 'heartRate', name: '心率', unit: '次/分', category: 'HEALTH' }
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    // Enter an abnormal high value (150 bpm)
    vm.form.value = 150
    await flushPromises()

    // Assert the value remains intact and is never wiped to undefined
    expect(vm.form.value).toBe(150)
  })

  it('GUIDE-01 & VAL-02: should bind metadata and evaluate Tier 2 warning without blocking save', async () => {
    const wrapper = mount(QuickInputDialog, {
      props: {
        modelValue: true,
        metric: { key: 'bloodGlucose', name: '血糖', unit: 'mmol/L', category: 'HEALTH' }
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    // Normal is 3.9 ~ 6.1. Set 9.5 (Tier 2 Warning)
    vm.form.value = 9.5
    await flushPromises()

    expect(vm.primaryEval?.tier).toBe('WARNING')
    expect(vm.primaryEval?.badgeText).toBe('指标偏高')
    expect(vm.isSaveBlocked).toBe(false) // Not blocked!

    // Save should proceed directly to API
    await vm.handleSubmit()
    await flushPromises()

    expect(healthApi.createMetric).toHaveBeenCalledWith({
      metricKey: 'bloodGlucose',
      value: 9.5,
      unit: 'mmol/L',
      recordDate: expect.any(String)
    })
  })

  it('GUIDE-03 & VAL-04: should support blood pressure dual fields, detect inversion, and swap atomically', async () => {
    const wrapper = mount(QuickInputDialog, {
      props: {
        modelValue: true,
        metric: { key: 'systolicBP', name: '血压', unit: 'mmHg', category: 'HEALTH' }
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    expect(vm.isBloodPressure).toBe(true)

    // Mistakenly enter inverted values: systolic 80, diastolic 120
    vm.form.value = 80
    vm.form.diastolicValue = 120
    await flushPromises()

    expect(vm.isInverted).toBe(true)
    expect(vm.isSaveBlocked).toBe(true) // Blocked while inverted

    // Click swap
    vm.handleSwap()
    await flushPromises()

    expect(vm.form.value).toBe(120)
    expect(vm.form.diastolicValue).toBe(80)
    expect(vm.isInverted).toBe(false)
    expect(vm.isSaveBlocked).toBe(false)
  })

  it('VAL-05: should trigger CriticalConfirmDialog when entering panic limits and save upon confirmation', async () => {
    const wrapper = mount(QuickInputDialog, {
      props: {
        modelValue: true,
        metric: { key: 'systolicBP', name: '血压', unit: 'mmHg', category: 'HEALTH' }
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    // Set critical panic value (systolic 190 >= 180)
    vm.form.value = 190
    vm.form.diastolicValue = 90
    await flushPromises()

    expect(vm.hasCritical).toBe(true)
    expect(vm.criticalDialogVisible).toBe(false)

    // Attempt to submit
    await vm.handleSubmit()
    await flushPromises()

    // Must trigger confirmation modal before executing save
    expect(vm.criticalDialogVisible).toBe(true)
    expect(healthApi.createMetric).not.toHaveBeenCalled()

    // Confirm save
    await vm.executeSave()
    await flushPromises()

    expect(healthApi.createMetric).toHaveBeenCalledTimes(2) // Systolic and Diastolic
  })

  it('CR-01 & CR-02: should resolve definition for bloodPressure key and format dual critical readings', async () => {
    const wrapper = mount(QuickInputDialog, {
      props: {
        modelValue: true,
        metric: { key: 'bloodPressure', name: '血压', unit: 'mmHg' }
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    expect(vm.isBloodPressure).toBe(true)
    expect(vm.definition?.displayName).toBe('收缩压')
    expect(vm.diastolicDef?.displayName).toBe('舒张压')

    // Both are critical (190 / 115)
    vm.form.value = 190
    vm.form.diastolicValue = 115
    await flushPromises()

    expect(vm.hasCritical).toBe(true)
    expect(vm.criticalMetricName).toBe('血压 (收缩压与舒张压)')
    expect(vm.criticalMetricValue).toBe('190 / 115')

    await vm.executeSave()
    await flushPromises()

    expect(healthApi.createMetric).toHaveBeenCalledWith(
      expect.objectContaining({ metricKey: 'systolicBP', value: 190 })
    )
    expect(healthApi.createMetric).toHaveBeenCalledWith(
      expect.objectContaining({ metricKey: 'diastolicBP', value: 115 })
    )
  })

  it('VAL-02: Tier 4 hard biological bounds should block save', async () => {
    const wrapper = mount(QuickInputDialog, {
      props: {
        modelValue: true,
        metric: { key: 'heartRate', name: '心率', unit: '次/分', category: 'HEALTH' }
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    // Impossible heart rate 400 (hardMax is 300)
    vm.form.value = 400
    await flushPromises()

    expect(vm.primaryEval?.tier).toBe('HARD_BOUND')
    expect(vm.primaryEval?.isHardLimit).toBe(true)
    expect(vm.isSaveBlocked).toBe(true)
    // Value remains 400 (not wiped!)
    expect(vm.form.value).toBe(400)
  })

  it('should call wellnessApi.createMetric when saving a WELLNESS category metric', async () => {
    const wrapper = mount(QuickInputDialog, {
      props: {
        modelValue: true,
        metric: { key: 'steps', name: '步数', unit: '步', category: 'WELLNESS' }
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    vm.form.value = 8000
    await flushPromises()

    await vm.handleSubmit()
    await flushPromises()

    expect(wellnessApi.createMetric).toHaveBeenCalledWith({
      metricKey: 'steps',
      value: 8000,
      unit: '步',
      recordDate: expect.any(String)
    })
  })
})
