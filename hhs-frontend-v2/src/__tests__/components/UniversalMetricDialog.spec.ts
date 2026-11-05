import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import UniversalMetricDialog from '@/components/metric/UniversalMetricDialog.vue'
import { healthApi } from '@/api/health'
import { wellnessApi } from '@/api/wellness'

vi.mock('@/api/health', () => ({
  healthApi: {
    createMetric: vi.fn().mockResolvedValue({ code: 200, message: 'success' }),
    updateMetric: vi.fn().mockResolvedValue({ code: 200, message: 'success' })
  }
}))

vi.mock('@/api/wellness', () => ({
  wellnessApi: {
    createMetric: vi.fn().mockResolvedValue({ code: 200, message: 'success' }),
    updateMetric: vi.fn().mockResolvedValue({ code: 200, message: 'success' })
  }
}))

const globalStubs = {
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
  'el-select': {
    template: '<div class="el-select"><slot /></div>',
    props: ['modelValue', 'disabled', 'placeholder']
  },
  'el-option': {
    template: '<div class="el-option" :data-key="value">{{ label }}</div>',
    props: ['label', 'value']
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
  'el-rate': {
    template: '<div class="el-rate" :data-value="modelValue"><slot /></div>',
    props: ['modelValue', 'max', 'showText', 'texts']
  },
  'el-input': {
    template: '<input class="el-input" :value="modelValue || value" />',
    props: ['modelValue', 'value', 'disabled', 'type', 'placeholder', 'rows', 'maxlength', 'showWordLimit']
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

describe('UniversalMetricDialog.vue - 10-Dimensional Specification Suite', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  // 1. category="HEALTH" 动态过滤 16 项健康指标及血压协同项
  it('Dim 1: category="HEALTH" should dynamically list health metrics including collaborative blood pressure', async () => {
    const wrapper = mount(UniversalMetricDialog, {
      props: {
        modelValue: true,
        category: 'HEALTH'
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    const options = vm.metricOptions
    expect(options.length).toBeGreaterThanOrEqual(16)
    expect(options.some((o: any) => o.key === 'bloodPressure')).toBe(true)
    expect(options.some((o: any) => o.key === 'heartRate')).toBe(true)
    expect(options.some((o: any) => o.key === 'sleepDuration')).toBe(false)
  })

  // 2. category="WELLNESS" 动态过滤 7 项保健指标
  it('Dim 2: category="WELLNESS" should dynamically list only 7 wellness metrics', async () => {
    const wrapper = mount(UniversalMetricDialog, {
      props: {
        modelValue: true,
        category: 'WELLNESS'
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    const options = vm.metricOptions
    expect(options).toHaveLength(7)
    expect(options.some((o: any) => o.key === 'sleepDuration')).toBe(true)
    expect(options.some((o: any) => o.key === 'steps')).toBe(true)
    expect(options.some((o: any) => o.key === 'heartRate')).toBe(false)
  })

  // 3. 血压协同新增与一键对调（收缩压 80/舒张压 120 倒置报警，对调后解除）
  it('Dim 3: should support collaborative BP, detect physiological inversion, and swap atomically', async () => {
    const wrapper = mount(UniversalMetricDialog, {
      props: {
        modelValue: true,
        category: 'HEALTH',
        defaultMetricKey: 'bloodPressure'
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    expect(vm.isBloodPressure).toBe(true)

    // Mistaken inverted input: 80 / 120
    vm.form.value = 80
    vm.form.diastolicValue = 120
    await flushPromises()

    expect(vm.isInverted).toBe(true)
    expect(vm.isSaveBlocked).toBe(true)

    // Click swap button
    vm.handleSwap()
    await flushPromises()

    expect(vm.form.value).toBe(120)
    expect(vm.form.diastolicValue).toBe(80)
    expect(vm.isInverted).toBe(false)
    expect(vm.isSaveBlocked).toBe(false)
  })

  // 4. 心情 1-5 星级打分与数值双向映射
  it('Dim 4: should handle mood 1-5 star rating correctly', async () => {
    const wrapper = mount(UniversalMetricDialog, {
      props: {
        modelValue: true,
        category: 'WELLNESS',
        defaultMetricKey: 'mood'
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    expect(vm.form.metricKey).toBe('mood')
    expect(vm.isWellnessCategory).toBe(true)

    // Set mood rating to 4 stars
    vm.form.value = 4
    await flushPromises()

    expect(vm.form.value).toBe(4)
    expect(vm.isSaveBlocked).toBe(false)

    await vm.handleSubmit()
    await flushPromises()

    expect(wellnessApi.createMetric).toHaveBeenCalledWith(
      expect.objectContaining({
        metricKey: 'mood',
        value: 4
      })
    )
  })

  // 5. 常规指标动态精度、步长与占位符
  it('Dim 5: should bind dynamic precision, step, and placeholder from metadata', async () => {
    const wrapper = mount(UniversalMetricDialog, {
      props: {
        modelValue: true,
        category: 'HEALTH',
        defaultMetricKey: 'bloodGlucose'
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    expect(vm.definition?.canonicalKey).toBe('bloodGlucose')
    expect(vm.precision).toBe(1)
    expect(vm.step).toBe(0.1)
    expect(vm.placeholder).toContain('3.9 ~ 6.1')
  })

  // 6. 四梯度非破坏性输入保留（Tier 4 阻断保存但不抹除原值）
  it('Dim 6: should non-destructively preserve Tier 4 impossible limits while blocking save', async () => {
    const wrapper = mount(UniversalMetricDialog, {
      props: {
        modelValue: true,
        category: 'HEALTH',
        defaultMetricKey: 'heartRate'
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    // Biologically impossible heart rate: 450 bpm
    vm.form.value = 450
    await flushPromises()

    expect(vm.primaryEval?.tier).toBe('HARD_BOUND')
    expect(vm.primaryEval?.isHardLimit).toBe(true)
    expect(vm.isSaveBlocked).toBe(true)
    // Non-destructive preservation: value is NEVER cleared
    expect(vm.form.value).toBe(450)
  })

  // 7. Tier 3 危急值触发 CriticalConfirmDialog 模态窗
  it('Dim 7: should prompt CriticalConfirmDialog for Tier 3 panic values before saving', async () => {
    const wrapper = mount(UniversalMetricDialog, {
      props: {
        modelValue: true,
        category: 'HEALTH',
        defaultMetricKey: 'bloodGlucose'
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    // Panic limit: bloodGlucose >= 16.7. Set 18.0
    vm.form.value = 18.0
    await flushPromises()

    expect(vm.hasCritical).toBe(true)
    expect(vm.criticalDialogVisible).toBe(false)

    // Trigger submit
    await vm.handleSubmit()
    await flushPromises()

    // Must open confirmation dialog first without calling API
    expect(vm.criticalDialogVisible).toBe(true)
    expect(healthApi.createMetric).not.toHaveBeenCalled()

    // Confirm execution
    await vm.executeSave()
    await flushPromises()

    expect(healthApi.createMetric).toHaveBeenCalledWith(
      expect.objectContaining({
        metricKey: 'bloodGlucose',
        value: 18.0
      })
    )
  })

  // 8. initialData 进入编辑模式，禁用类型下拉框并精准评估历史数值
  it('Dim 8: initialData should activate edit mode, disable metric selection, and accurately load data', async () => {
    const wrapper = mount(UniversalMetricDialog, {
      props: {
        modelValue: true,
        initialData: {
          id: 99,
          metricKey: 'heartRate',
          value: 78,
          unit: 'bpm',
          recordDate: '2026-11-05',
          trend: 'STABLE'
        }
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    expect(vm.isEditMode).toBe(true)
    expect(vm.form.metricKey).toBe('heartRate')
    expect(vm.form.value).toBe(78)
    expect(vm.dialogTitle).toBe('编辑心率')
    expect(vm.primaryEval?.tier).toBe('NORMAL')
  })

  // 9. 自适应 API 分流路由（HEALTH 调用 healthApi，WELLNESS 调用 wellnessApi；编辑调用 updateMetric）
  it('Dim 9: should adaptively route API calls based on category and edit mode', async () => {
    // 9a: Edit Health Metric
    const healthWrapper = mount(UniversalMetricDialog, {
      props: {
        modelValue: true,
        initialData: {
          id: 101,
          metricKey: 'heartRate',
          value: 82,
          unit: 'bpm',
          recordDate: '2026-11-05',
          trend: 'UP'
        }
      },
      global: { stubs: globalStubs }
    })

    const healthVm = healthWrapper.vm as any
    healthVm.form.value = 85
    await healthVm.executeSave()
    await flushPromises()

    expect(healthApi.updateMetric).toHaveBeenCalledWith(
      101,
      expect.objectContaining({
        metricKey: 'heartRate',
        value: 85,
        trend: 'up'
      })
    )

    // 9b: Edit Wellness Metric
    const wellnessWrapper = mount(UniversalMetricDialog, {
      props: {
        modelValue: true,
        initialData: {
          id: 202,
          metricKey: 'waterIntake',
          value: 2200,
          unit: 'ml',
          recordDate: '2026-11-05',
          notes: '午后补水'
        }
      },
      global: { stubs: globalStubs }
    })

    const wellnessVm = wellnessWrapper.vm as any
    wellnessVm.form.value = 2500
    await wellnessVm.executeSave()
    await flushPromises()

    expect(wellnessApi.updateMetric).toHaveBeenCalledWith(
      202,
      expect.objectContaining({
        metricKey: 'waterIntake',
        value: 2500,
        notes: '午后补水'
      })
    )
  })

  // 10. 兼容旧版 metric prop 传参 (保持与 QuickInput 兼容)
  it('Dim 10: should support legacy metric prop and single-metric quick input mode', async () => {
    const wrapper = mount(UniversalMetricDialog, {
      props: {
        modelValue: true,
        metric: { key: 'steps', name: '步数', unit: '步', category: 'WELLNESS' }
      },
      global: { stubs: globalStubs }
    })

    const vm = wrapper.vm as any
    expect(vm.form.metricKey).toBe('steps')
    expect(vm.dialogTitle).toBe('步数录入')
    expect(vm.isSingleMetricProp).toBe(true)

    vm.form.value = 10000
    await flushPromises()

    await vm.handleSubmit()
    await flushPromises()

    expect(wellnessApi.createMetric).toHaveBeenCalledWith(
      expect.objectContaining({
        metricKey: 'steps',
        value: 10000,
        unit: '步'
      })
    )
  })
})
