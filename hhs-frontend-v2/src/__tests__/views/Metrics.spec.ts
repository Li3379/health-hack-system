import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import Metrics from '@/views/health/Metrics.vue'
import { healthApi } from '@/api/health'

vi.mock('@/api/health', () => ({
  healthApi: {
    getMetrics: vi.fn().mockResolvedValue({
      code: 200,
      message: 'success',
      data: {
        records: [
          {
            id: 1,
            metricKey: 'heartRate',
            metricName: '心率',
            value: 75,
            unit: '次/分',
            recordDate: '2026-11-05',
            trend: 'STABLE',
            createTime: '2026-11-05 10:00:00'
          },
          {
            id: 2,
            metricKey: 'systolicBP',
            metricName: '收缩压',
            value: 120,
            unit: 'mmHg',
            recordDate: '2026-11-05',
            trend: 'NORMAL',
            createTime: '2026-11-05 10:00:00'
          }
        ],
        total: 2
      }
    }),
    createMetric: vi.fn().mockResolvedValue({ code: 200, message: 'success' }),
    updateMetric: vi.fn().mockResolvedValue({ code: 200, message: 'success' }),
    deleteMetric: vi.fn().mockResolvedValue({ code: 200, message: 'success' })
  }
}))

vi.mock('@/api/metric-meta', () => ({
  metricMetaApi: {
    getMetadata: vi.fn().mockResolvedValue({
      code: 200,
      message: 'success',
      data: {}
    })
  }
}))

vi.mock('vue-router', async importOriginal => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: vi.fn(() => ({ query: {} }))
  }
})

vi.mock('@/composables/useGsap', () => ({
  scrollReveal: vi.fn(),
  cleanupScrollTriggers: vi.fn(),
  buttonPress: vi.fn()
}))

const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose', 'appendToBody']
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
    props: ['label', 'prop']
  },
  'el-row': {
    template: '<div class="el-row"><slot /></div>',
    props: ['gutter']
  },
  'el-col': {
    template: '<div class="el-col"><slot /></div>',
    props: ['span']
  },
  'el-select': {
    template: '<select class="el-select" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
    props: ['modelValue', 'placeholder', 'clearable', 'disabled']
  },
  'el-option': {
    template: '<option class="el-option" :value="value">{{ label }}</option>',
    props: ['label', 'value']
  },
  'el-input-number': {
    template: '<input class="el-input-number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
    props: ['modelValue', 'precision', 'step', 'controls', 'placeholder']
  },
  'el-input': {
    template: '<input class="el-input" :value="modelValue || value" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'value', 'disabled', 'placeholder']
  },
  'el-date-picker': {
    template: '<input class="el-date-picker" :value="modelValue" />',
    props: ['modelValue', 'type', 'placeholder', 'format', 'valueFormat']
  },
  'el-table': {
    template: '<div class="el-table"><slot /></div>',
    props: ['data', 'stripe']
  },
  'el-table-column': {
    template: '<div class="el-table-column"><slot name="default" :row="{}" /></div>',
    props: ['prop', 'label', 'width', 'fixed']
  },
  'el-pagination': {
    template: '<div class="el-pagination" />',
    props: ['currentPage', 'pageSize', 'total', 'pageSizes', 'layout']
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
    props: ['type', 'loading', 'disabled', 'size', 'plain', 'icon', 'link']
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  ReferenceGuideCard: {
    template: '<div class="reference-guide-card-stub">Reference Guide Card</div>',
    props: ['definition', 'diastolicDef']
  },
  CriticalConfirmDialog: {
    template: '<div class="critical-confirm-dialog-stub" v-if="modelValue"><button class="confirm-btn" @click="$emit(\'confirm\')">Confirm</button></div>',
    props: ['modelValue', 'metricName', 'value', 'unit']
  }
}

describe('health/Metrics.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('HEALTH-01: should dynamically render 16 health metrics in select without hardcoded dictionary', async () => {
    const wrapper = mount(Metrics, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    // Verify healthMetrics has 16 items from Pinia store
    expect(vm.metricMetaStore.healthMetrics.length).toBe(16)

    // Open add dialog
    vm.showAddDialog()
    await flushPromises()

    expect(vm.dialogVisible).toBe(true)
    expect(vm.form.metricKey).toBe('bloodPressure')

    // Change metric to glucose
    vm.form.metricKey = 'bloodGlucose'
    vm.handleMetricKeyChange('bloodGlucose')
    await flushPromises()

    expect(vm.form.unit).toBe('mmol/L')
    expect(vm.precision).toBe(1)
    expect(vm.step).toBe(0.1)
    expect(vm.placeholder).toContain('3.9 ~ 6.1 mmol/L')
  })

  it('HEALTH-02: should support blood pressure dual fields and atomic swapping on add mode', async () => {
    const wrapper = mount(Metrics, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showAddDialog()
    await flushPromises()

    expect(vm.isBloodPressure).toBe(true)
    expect(vm.isCollaborative).toBe(true)

    // Enter inverted values
    vm.form.value = 80 // systolic
    vm.form.diastolicValue = 120 // diastolic
    await flushPromises()

    expect(vm.isInverted).toBe(true)
    expect(vm.isSaveBlocked).toBe(true)

    // Execute swap
    vm.handleSwap()
    await flushPromises()

    expect(vm.form.value).toBe(120)
    expect(vm.form.diastolicValue).toBe(80)
    expect(vm.isInverted).toBe(false)
    expect(vm.isSaveBlocked).toBe(false)

    // Submit should save both with Promise.all
    await vm.handleSubmit()
    await flushPromises()

    expect(healthApi.createMetric).toHaveBeenCalledWith(
      expect.objectContaining({ metricKey: 'systolicBP', value: 120 })
    )
    expect(healthApi.createMetric).toHaveBeenCalledWith(
      expect.objectContaining({ metricKey: 'diastolicBP', value: 80 })
    )
  })

  it('HEALTH-02: should lock metricKey and disable collaboration on edit mode for single historical records', async () => {
    const wrapper = mount(Metrics, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    // Edit historical systolic record
    const row = {
      id: 99,
      metricKey: 'systolicBP',
      metricName: '收缩压',
      value: 135,
      unit: 'mmHg',
      recordDate: '2026-11-05',
      trend: 'STABLE',
      createTime: '2026-11-05 10:00:00'
    }

    vm.handleEdit(row)
    await flushPromises()

    expect(vm.dialogTitle).toBe('编辑健康指标')
    expect(vm.editingId).toBe(99)
    expect(vm.isCollaborative).toBe(false) // Collaboration disabled in edit mode
    expect(vm.isBloodPressure).toBe(false)
    expect(vm.form.value).toBe(135)
    expect(vm.form.diastolicValue).toBeUndefined()

    // Update value to 138
    vm.form.value = 138
    await vm.handleSubmit()
    await flushPromises()

    expect(healthApi.updateMetric).toHaveBeenCalledWith(
      99,
      expect.objectContaining({ metricKey: 'systolicBP', value: 138 })
    )
  })

  it('HEALTH-03: should non-destructively validate Tier 2, Tier 3, and Tier 4 values without wiping user inputs', async () => {
    const wrapper = mount(Metrics, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showAddDialog()
    vm.form.metricKey = 'heartRate'
    vm.handleMetricKeyChange('heartRate')
    await flushPromises()

    // Tier 2: 110 bpm (warning high, non-blocking)
    vm.form.value = 110
    await flushPromises()

    expect(vm.primaryEval?.tier).toBe('WARNING')
    expect(vm.primaryEval?.badgeText).toBe('指标偏高')
    expect(vm.isSaveBlocked).toBe(false)

    // Tier 3: 160 bpm (critical panic limit)
    vm.form.value = 160
    await flushPromises()

    expect(vm.primaryEval?.tier).toBe('CRITICAL')
    expect(vm.hasCritical).toBe(true)
    expect(vm.criticalDialogVisible).toBe(false)

    // Submit triggers confirmation modal
    await vm.handleSubmit()
    await flushPromises()
    expect(vm.criticalDialogVisible).toBe(true)

    // Tier 4: 400 bpm (hard limit impossible)
    vm.form.value = 400
    await flushPromises()

    expect(vm.primaryEval?.tier).toBe('HARD_BOUND')
    expect(vm.isSaveBlocked).toBe(true)
    // Value remains 400 (not wiped!)
    expect(vm.form.value).toBe(400)
  })

  it('CR-01 & WR-01 & WR-02 & WR-04: should handle action=add, key switching, legacy alias edit, and filter pagination reset', async () => {
    // Test WR-04: filter query resets page to 1
    const wrapper = mount(Metrics, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.pagination.page = 3
    vm.handleFilterQuery()
    expect(vm.pagination.page).toBe(1)

    // Test WR-01: handleMetricKeyChange resets values and sets unit for bloodPressure
    vm.showAddDialog()
    vm.form.value = 120
    vm.form.diastolicValue = 80
    vm.handleMetricKeyChange('bloodPressure')
    expect(vm.form.unit).toBe('mmHg')
    expect(vm.form.value).toBeUndefined()
    expect(vm.form.diastolicValue).toBeUndefined()

    // Test WR-02: handleEdit normalizes legacy aliases like glucose to bloodGlucose
    const legacyRow = {
      id: 101,
      metricKey: 'glucose',
      metricName: '血糖',
      value: 6.0,
      unit: 'mmol/L',
      recordDate: '2026-11-05',
      trend: 'STABLE',
      createTime: '2026-11-05 10:00:00'
    }
    vm.handleEdit(legacyRow)
    expect(vm.form.metricKey).toBe('bloodGlucose')
    expect(vm.form.unit).toBe('mmol/L')
  })
})
