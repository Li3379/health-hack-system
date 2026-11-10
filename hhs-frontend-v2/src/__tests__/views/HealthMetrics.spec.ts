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
  // 与 UniversalMetricDialog.spec.ts 同形：转发 :error，让内联错误可观测。
  'el-form-item': {
    template:
      '<div class="el-form-item" :data-error="error"><slot /><span v-if="error" class="el-form-item__error">{{ error }}</span></div>',
    props: ['label', 'prop', 'error']
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
    template: '<input class="el-input-number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" @blur="$emit(\'blur\', $event)" />',
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

describe('HealthMetrics.spec.ts — health/Metrics.vue 动态元数据与弹窗交互测试', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('1. 动态加载元数据: 下拉框展示16项标准健康指标，不存在硬编码7项或旧key glucose', async () => {
    const wrapper = mount(Metrics, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any

    // 权威健康指标元数据包含 16 项
    expect(vm.metricMetaStore.healthMetrics.length).toBe(16)

    // 验证不存在历史旧 key glucose
    const keys = vm.metricMetaStore.healthMetrics.map((item: any) => item.canonicalKey)
    expect(keys).toContain('bloodGlucose')
    expect(keys).not.toContain('glucose')

    // 打开添加弹窗
    vm.showAddDialog()
    await flushPromises()
    expect(vm.dialogVisible).toBe(true)

    // 切换至血糖指标
    vm.form.metricKey = 'bloodGlucose'
    vm.handleMetricKeyChange('bloodGlucose')
    await flushPromises()

    expect(vm.form.unit).toBe('mmol/L')
    expect(vm.precision).toBe(1)
    expect(vm.step).toBe(0.1)
    expect(vm.placeholder).toContain('3.9 ~ 6.1 mmol/L')
  })

  it('2. 血压双字段协同与对调: 选择血压渲染并列字段，数值倒置时报警并支持一键原子对调', async () => {
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

    // 默认或选择血压时进入协同模式
    expect(vm.isBloodPressure).toBe(true)
    expect(vm.isCollaborative).toBe(true)

    // 模拟录入倒置数值: 收缩压 80，舒张压 120
    vm.form.value = 80
    vm.form.diastolicValue = 120
    await flushPromises()

    expect(vm.isInverted).toBe(true)
    expect(vm.isBpEqual).toBe(false)
    expect(vm.isSaveBlocked).toBe(true)

    // 点击一键对调按钮
    vm.handleSwap()
    await flushPromises()

    expect(vm.form.value).toBe(120)
    expect(vm.form.diastolicValue).toBe(80)
    expect(vm.isInverted).toBe(false)
    expect(vm.isSaveBlocked).toBe(false)

    // 测试收缩压等于舒张压时的提示
    vm.form.value = 95
    vm.form.diastolicValue = 95
    await flushPromises()

    expect(vm.isInverted).toBe(true)
    expect(vm.isBpEqual).toBe(true)
    expect(vm.isSaveBlocked).toBe(true)
  })

  it('3. 非破坏性校验与失焦保护: 录入高血糖12.0 mmol/L失焦保留数值并展示状态胶囊', async () => {
    const wrapper = mount(Metrics, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showAddDialog()
    vm.form.metricKey = 'bloodGlucose'
    vm.handleMetricKeyChange('bloodGlucose')
    await flushPromises()

    // 输入异常高血糖 (12.0 mmol/L - 偏高 Tier 2)
    vm.form.value = 12.0
    await flushPromises()

    // 数值原样保留，不被清空或重置
    expect(vm.form.value).toBe(12.0)
    expect(vm.primaryEval?.tier).toBe('WARNING')
    expect(vm.primaryEval?.badgeText).toBe('指标偏高')
    expect(vm.isSaveBlocked).toBe(false)
  })

  it('4. 危急值二次确认: 录入危急值收缩压190 mmHg触发 CriticalConfirmDialog 二次确认', async () => {
    const wrapper = mount(Metrics, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showAddDialog()
    vm.form.metricKey = 'systolicBP'
    vm.handleMetricKeyChange('systolicBP')
    await flushPromises()

    // 录入收缩压 190 mmHg (Tier 3 CRITICAL)
    vm.form.value = 190
    await flushPromises()

    expect(vm.primaryEval?.tier).toBe('CRITICAL')
    expect(vm.hasCritical).toBe(true)
    expect(vm.criticalDialogVisible).toBe(false)

    // 点击提交保存，触发二次确认对话框
    await vm.handleSubmit()
    await flushPromises()

    expect(vm.criticalDialogVisible).toBe(true)
    expect(vm.criticalMetricName).toBe('收缩压')
    expect(vm.criticalMetricValue).toBe(190)
    expect(vm.criticalMetricUnit).toBe('mmHg')

    // 确认后执行保存 API
    await vm.executeSave()
    await flushPromises()

    expect(healthApi.createMetric).toHaveBeenCalledWith(
      expect.objectContaining({
        metricKey: 'systolicBP',
        value: 190
      })
    )
  })

  it('5. 编辑模式锁定制: 编辑历史单条记录仅加载当前指标单项输入框且禁用协同', async () => {
    const wrapper = mount(Metrics, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    const row = {
      id: 88,
      metricKey: 'systolicBP',
      metricName: '收缩压',
      value: 132,
      unit: 'mmHg',
      recordDate: '2026-11-05',
      trend: 'NORMAL',
      createTime: '2026-11-05 10:00:00'
    }

    vm.handleEdit(row)
    await flushPromises()

    expect(vm.dialogTitle).toBe('编辑健康指标')
    expect(vm.editingId).toBe(88)
    expect(vm.isCollaborative).toBe(false)
    expect(vm.isBloodPressure).toBe(false)
    expect(vm.form.value).toBe(132)
    expect(vm.form.diastolicValue).toBeUndefined()

    // 修改并保存
    vm.form.value = 128
    await vm.handleSubmit()
    await flushPromises()

    expect(healthApi.updateMetric).toHaveBeenCalledWith(
      88,
      expect.objectContaining({ metricKey: 'systolicBP', value: 128 })
    )
  })
})
