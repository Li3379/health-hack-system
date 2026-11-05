import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import PreventionMetrics from '@/views/prevention/Metrics.vue'
import WellnessDashboard from '@/views/wellness/Dashboard.vue'
import { wellnessApi } from '@/api/wellness'

vi.mock('@/api/wellness', () => ({
  wellnessApi: {
    getMetrics: vi.fn().mockResolvedValue({
      code: 200,
      message: 'success',
      data: {
        records: [
          {
            id: 1,
            metricKey: 'sleepDuration',
            metricName: '睡眠时长',
            value: 7.5,
            unit: '小时',
            recordDate: '2026-11-05',
            createTime: '2026-11-05 10:00:00'
          },
          {
            id: 2,
            metricKey: 'mood',
            metricName: '心情',
            value: 4,
            unit: '级',
            recordDate: '2026-11-05',
            createTime: '2026-11-05 10:00:00'
          }
        ],
        total: 2
      }
    }),
    getSummary: vi.fn().mockResolvedValue({
      code: 200,
      message: 'success',
      data: {
        summaryDate: '2026-11-05',
        totalSteps: 8000,
        avgSleepDuration: 7.5,
        metrics: []
      }
    }),
    getLatest: vi.fn().mockResolvedValue({
      code: 200,
      message: 'success',
      data: {}
    }),
    getTrend: vi.fn().mockResolvedValue({
      code: 200,
      message: 'success',
      data: { dates: ['2026-11-01', '2026-11-05'], values: [7, 8], unit: '小时' }
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

vi.mock('@/composables/useGsap', () => ({
  gsap: {
    timeline: () => ({
      fromTo: vi.fn().mockReturnThis()
    })
  },
  cleanupScrollTriggers: vi.fn(),
  staggerReveal: vi.fn(),
  countUp: vi.fn(),
  tilt3D: vi.fn(),
  parallax: vi.fn(),
  scrollScale: vi.fn(),
  magneticHover: vi.fn(),
  buttonPress: vi.fn(),
  hoverLift: vi.fn(),
  hoverShine: vi.fn(),
  rippleClick: vi.fn(),
  DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
  EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
}))

vi.mock('@/composables/useECharts', () => ({
  useECharts: () => ({
    init: vi.fn(),
    updateOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn()
  })
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
    props: ['span', 'xs', 'sm', 'md', 'lg']
  },
  'el-select': {
    template: '<select class="el-select" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
    props: ['modelValue', 'placeholder', 'clearable', 'disabled', 'size']
  },
  'el-option': {
    template: '<option class="el-option" :value="value">{{ label }}</option>',
    props: ['label', 'value']
  },
  'el-input-number': {
    template: '<input class="el-input-number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" @blur="$emit(\'blur\', $event)" />',
    props: ['modelValue', 'precision', 'step', 'controls', 'placeholder']
  },
  'el-input': {
    template: '<input class="el-input" :value="modelValue || value" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'value', 'disabled', 'placeholder', 'type', 'rows']
  },
  'el-rate': {
    template: '<div class="el-rate" :data-value="modelValue" @click="$emit(\'update:modelValue\', 4); $emit(\'change\', 4)">Rate</div>',
    props: ['modelValue', 'max', 'showText', 'texts']
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
  'el-button': {
    template: '<button class="el-button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'plain', 'icon', 'link']
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  'el-skeleton': {
    template: '<div class="el-skeleton"><slot /></div>',
    props: ['loading', 'animated']
  },
  'el-skeleton-item': {
    template: '<div class="el-skeleton-item" />',
    props: ['variant']
  },
  ActivityRings: {
    template: '<div class="activity-rings-stub" />',
    props: ['exercisePercent', 'sleepPercent', 'habitsPercent']
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

describe('WellnessMetrics.spec.ts — prevention/Metrics.vue 与 wellness/Dashboard.vue 弹窗测试', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('1. prevention/Metrics.vue: 加载7项保健指标，废除私有字典，通用数值绑定动态精度与参考卡片', async () => {
    const wrapper = mount(PreventionMetrics, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any

    // 验证由元数据 Store 提供 7 项指标，无私有硬编码字典
    expect(vm.metricMetaStore.wellnessMetrics.length).toBe(7)
    const keys = vm.metricMetaStore.wellnessMetrics.map((m: any) => m.canonicalKey)
    expect(keys).toEqual([
      'sleepDuration', 'sleepQuality', 'steps', 'exerciseMinutes', 'waterIntake', 'mood', 'energy'
    ])

    // 打开添加弹窗
    vm.showAddDialog()
    await flushPromises()
    expect(vm.dialogVisible).toBe(true)
    expect(vm.editingId).toBeNull()

    // 默认首项为睡眠时长
    expect(vm.form.metricKey).toBe('sleepDuration')
    expect(vm.form.unit).toBe('小时')
    expect(vm.precision).toBe(1)
    expect(vm.step).toBe(0.5)

    // 切换为饮水量
    vm.form.metricKey = 'waterIntake'
    vm.handleMetricKeyChange('waterIntake')
    await flushPromises()

    expect(vm.form.unit).toBe('ml')
    expect(vm.precision).toBe(0)
    expect(vm.step).toBe(50)
    expect(vm.placeholder).toContain('1500 ~ 2500 ml')

    // 切换为步数
    vm.form.metricKey = 'steps'
    vm.handleMetricKeyChange('steps')
    await flushPromises()

    expect(vm.form.unit).toBe('步')
    expect(vm.precision).toBe(0)
    expect(vm.step).toBe(100)
    expect(vm.placeholder).toContain('6000 ~ 10000 步')
  })

  it('2. prevention/Metrics.vue: 选择心情指标渲染1-5星级打分控件并交互', async () => {
    const wrapper = mount(PreventionMetrics, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showAddDialog()
    await flushPromises()

    // 切换到心情
    vm.form.metricKey = 'mood'
    vm.handleMetricKeyChange('mood')
    await flushPromises()

    // 默认心情评分居中 (3 分)
    expect(vm.moodValue).toBe(3)
    expect(vm.form.value).toBe(3)

    // 用户交互评分为 5 分
    vm.handleMoodChange(5)
    expect(vm.moodValue).toBe(5)
    expect(vm.form.value).toBe(5)

    // 提交保存
    await vm.handleSubmit()
    await flushPromises()

    expect(wellnessApi.createMetric).toHaveBeenCalledWith(
      expect.objectContaining({
        metricKey: 'mood',
        value: 5
      })
    )
  })

  it('3. prevention/Metrics.vue: 非破坏性校验与失焦保护，不强制清空异常数值', async () => {
    const wrapper = mount(PreventionMetrics, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showAddDialog()
    vm.form.metricKey = 'steps'
    vm.handleMetricKeyChange('steps')
    await flushPromises()

    // 输入物理极限数值 200,000 步 (超出 150,000)
    vm.form.value = 200000
    await flushPromises()

    // 触发 Tier 4 物理界限拦截，保存被拦截但数值原样保留
    expect(vm.primaryEval?.tier).toBe('HARD_BOUND')
    expect(vm.isSaveBlocked).toBe(true)
    expect(vm.form.value).toBe(200000)

    // 修改为正常的 8,500 步
    vm.form.value = 8500
    await flushPromises()

    expect(vm.isSaveBlocked).toBe(false)
    await vm.handleSubmit()
    await flushPromises()

    expect(wellnessApi.createMetric).toHaveBeenCalledWith(
      expect.objectContaining({
        metricKey: 'steps',
        value: 8500
      })
    )
  })

  it('4. wellness/Dashboard.vue: 快速录入弹窗动态接入权威元数据并支持非破坏性校验', async () => {
    const wrapper = mount(WellnessDashboard, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    expect(vm.metricMetaStore.wellnessMetrics.length).toBe(7)

    // 打开仪表盘快速添加弹窗
    vm.showAddDialog = true
    await flushPromises()

    expect(vm.addForm.metricKey).toBe('sleepDuration')
    expect(vm.addPrecision).toBe(1)
    expect(vm.addStep).toBe(0.5)

    // 切换到饮水量 (waterIntake)
    vm.addForm.metricKey = 'waterIntake'
    vm.handleAddMetricKeyChange('waterIntake')
    await flushPromises()

    expect(vm.addForm.unit).toBe('ml')
    expect(vm.addPrecision).toBe(0)
    expect(vm.addStep).toBe(50)
    expect(vm.addPlaceholder).toContain('1500 ~ 2500 ml')

    // 录入物理极限数值 50,000 ml
    vm.addForm.value = 50000
    await flushPromises()

    expect(vm.addPrimaryEval?.tier).toBe('HARD_BOUND')
    expect(vm.isAddSaveBlocked).toBe(true)
    expect(vm.addForm.value).toBe(50000)

    // 修正为正常的 2000 ml 并提交
    vm.addForm.value = 2000
    await flushPromises()

    expect(vm.isAddSaveBlocked).toBe(false)
    await vm.handleAddMetric()
    await flushPromises()

    expect(wellnessApi.createMetric).toHaveBeenCalledWith(
      expect.objectContaining({
        metricKey: 'waterIntake',
        value: 2000
      })
    )
  })
})
