import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'
import type { UserThreshold } from '@/types/api'

/**
 * ThresholdWizard 组件契约（THR-01/02/03/05 前端/06、D-02、T-12-25）。
 *
 * stub 集合形状照抄 ErrorLayerSample.spec.ts：el-dialog 只在 modelValue 为真时渲染插槽，
 * el-input-number 渲染真实 input 以便断言禁用态与焦点规则。
 * element-plus 用 importOriginal 部分 mock：保住组件导出，只替换 ElMessageBox，
 * 以便对「未保存离开」确认计数（T-12-25 数据丢失前置条件）。
 */

vi.mock('@/api/threshold', () => ({
  thresholdApi: {
    getThresholds: vi.fn(),
    getByUserId: vi.fn(),
    getByMetricKey: vi.fn(),
    createThreshold: vi.fn(),
    updateThreshold: vi.fn(),
    deleteThreshold: vi.fn()
  }
}))

// 向导的 userId 来自认证 store 而非用户输入（T-12-26）
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: { id: 1, username: 'tester' } })
}))

vi.mock('element-plus', async importOriginal => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessageBox: Object.assign(vi.fn(), {
      confirm: vi.fn(),
      alert: vi.fn(),
      prompt: vi.fn()
    })
  }
})

import ThresholdWizard from '@/views/health/components/ThresholdWizard.vue'
import { ElMessageBox } from 'element-plus'
import { thresholdApi } from '@/api/threshold'
import { ApiError } from '@/utils/appError'

const confirmMock = ElMessageBox.confirm as unknown as Mock
const createThresholdMock = thresholdApi.createThreshold as unknown as Mock

const globalStubs = {
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width']
  },
  'el-steps': {
    template: '<div class="el-steps"><slot /></div>',
    props: ['active', 'finishStatus', 'alignCenter']
  },
  'el-step': {
    template: '<div class="el-step"><span class="el-step__title">{{ title }}</span></div>',
    props: ['title']
  },
  'el-select': {
    template:
      '<div class="el-select"><input class="el-select__input" :disabled="disabled" /><slot /></div>',
    props: ['modelValue', 'placeholder', 'disabled', 'filterable']
  },
  'el-option-group': {
    template:
      '<div class="el-option-group"><span class="el-option-group__title">{{ label }}</span><slot /></div>',
    props: ['label']
  },
  'el-option': {
    template: '<option class="el-option" :value="value">{{ label }}</option>',
    props: ['label', 'value']
  },
  'el-input-number': {
    template: '<input class="el-input-number__inner" :disabled="disabled" />',
    props: ['modelValue', 'min', 'max', 'precision', 'step', 'controlsPosition', 'disabled']
  },
  'el-form-item': {
    template:
      '<div class="el-form-item"><slot /><span v-if="error" class="el-form-item__error">{{ error }}</span></div>',
    props: ['error', 'prop', 'label']
  },
  'el-button': {
    // 裸 link 属性在无类型声明的 stub 里是 ''（无 Boolean 强转），
    // 因此用 link !== '' 区分主按钮与 link 按钮
    template:
      '<button class="el-button" :class="{ \'el-button--primary\': type === \'primary\' && link !== \'\', \'is-link\': link === \'\' }" :disabled="disabled || loading" type="button"><slot /></button>',
    props: ['type', 'disabled', 'loading', 'link']
  },
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /><slot /></div>',
    props: ['type', 'closable', 'showIcon']
  }
}

const EDITING_THRESHOLD: UserThreshold = {
  id: 5,
  userId: 1,
  metricKey: 'bloodGlucose',
  warningHigh: 7,
  criticalHigh: 11.1,
  warningLow: 3.9,
  criticalLow: 3,
  createdAt: '2026-01-01 00:00:00',
  updatedAt: '2026-01-01 00:00:00'
}

function mountWizard(editing: UserThreshold | null = null) {
  return mount(ThresholdWizard, {
    props: { modelValue: true, editing },
    global: { stubs: globalStubs }
  })
}

/**
 * defineExpose 的公共面：断言只落在这些暴露属性/方法上，不碰组件内部 ref。
 * 单独建模成接口，避免测试里到处 `as any`。
 */
interface WizardVM {
  currentStep: number
  selectedKey: string
  draft: {
    criticalLow: number | null
    warningLow: number | null
    warningHigh: number | null
    criticalHigh: number | null
  }
  handleMetricSelect: (key: string) => void
  restoreRecommended: () => void
  goToStep: (step: number) => void
  handleBeforeClose: (done: () => void) => void
  handleSubmit: () => Promise<void>
}

function getVM(wrapper: ReturnType<typeof mountWizard>): WizardVM {
  return wrapper.vm as unknown as WizardVM
}

describe('ThresholdWizard — 三步向导组件契约', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // 默认让确认框悬挂：脏分支里 done 永不被调用的断言才成立
    confirmMock.mockReturnValue(new Promise(() => {}))
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('1. 第 1 步恰好列出 6 个可告警指标，文案为 显示名 (单位) 且不含血氧（THR-01）', async () => {
    const wrapper = mountWizard()
    await flushPromises()

    const options = wrapper.findAll('option.el-option')
    expect(options).toHaveLength(6)
    const texts = options.map(o => o.text())
    expect(texts).toContain('血糖 (mmol/L)')
    expect(texts).toContain('心率 (次/分)')
    // D-19：weight 四列全 NULL、bloodOxygen 无种子规则 → 不可配
    expect(texts.some(t => t.includes('血氧'))).toBe(false)
    expect(texts.some(t => t.includes('体重'))).toBe(false)
    wrapper.unmount()
  })

  it('2. 无可告警保健指标时保健分组整体不渲染（D-04 空组隐藏）', async () => {
    const wrapper = mountWizard()
    await flushPromises()

    expect(wrapper.findAll('.el-option-group')).toHaveLength(1)
    expect(wrapper.find('.el-option-group__title').text()).toBe('健康指标')
    expect(wrapper.text()).not.toContain('保健指标')
    wrapper.unmount()
  })

  it('3. 选定血糖即写入推荐值 3.9/7.0/3.0/11.1，四枚推荐徽标，下一步可用（THR-02）', async () => {
    const wrapper = mountWizard()
    await flushPromises()
    const vm = getVM(wrapper)

    vm.handleMetricSelect('bloodGlucose')
    await nextTick()

    expect(vm.draft.warningLow).toBe(3.9)
    expect(vm.draft.warningHigh).toBe(7)
    expect(vm.draft.criticalLow).toBe(3)
    expect(vm.draft.criticalHigh).toBe(11.1)
    expect((wrapper.find('button.el-button--primary').element as HTMLButtonElement).disabled).toBe(
      false
    )

    vm.goToStep(1)
    await nextTick()
    expect(wrapper.findAll('.recommend-badge')).toHaveLength(4)
    // 无错误时 GOV.UK 汇总块整体不渲染（UI-SPEC empty 契约）
    expect(wrapper.find('.form-error-summary').exists()).toBe(false)
    wrapper.unmount()
  })

  it('4. 四项说明文案逐字含单位插值 mmol/L（THR-03）', async () => {
    const wrapper = mountWizard()
    await flushPromises()
    const vm = getVM(wrapper)

    vm.handleMetricSelect('bloodGlucose')
    vm.goToStep(1)
    await nextTick()

    const hints = wrapper.findAll('.threshold-field__hint')
    expect(hints).toHaveLength(4)
    expect(hints.map(h => h.text())).toEqual([
      '高于此值发出红色紧急提醒（mmol/L）',
      '高于此值发出黄色提醒（mmol/L）',
      '低于此值发出黄色提醒（mmol/L）',
      '低于此值发出红色紧急提醒（mmol/L）'
    ])
    wrapper.unmount()
  })

  it('5. 警告低值高于警告高值：主按钮禁用，两个字段挂同一条内联错误（THR-05 前端）', async () => {
    const wrapper = mountWizard()
    await flushPromises()
    const vm = getVM(wrapper)

    vm.handleMetricSelect('bloodGlucose')
    vm.goToStep(1)
    await nextTick()
    vm.draft.warningLow = 10
    await nextTick()

    expect((wrapper.find('button.el-button--primary').element as HTMLButtonElement).disabled).toBe(
      true
    )
    // 违规时第 2 步顶部出现汇总块（GOV.UK 双呈现），全程零 toast（D-12）
    expect(wrapper.find('.form-error-summary').exists()).toBe(true)
    const messages = wrapper.findAll('.el-form-item__error').map(e => e.text())
    expect(messages).toHaveLength(2)
    expect(messages[0]).toBe('警告低值必须小于警告高值，请调整其中一个')
    expect(messages[1]).toBe(messages[0])
    const markedFields = wrapper
      .findAll('.el-form-item[data-field]')
      .filter(item => item.find('.el-form-item__error').exists())
      .map(item => item.attributes('data-field'))
      .sort()
    expect(markedFields).toEqual(['warningHigh', 'warningLow'])
    // D-12：校验失败绝不清空用户输入
    expect(vm.draft.warningLow).toBe(10)
    wrapper.unmount()
  })

  it('6. 确认语句含全部四段分句，criticalHigh 置空后严重高分句整体消失（THR-06）', async () => {
    const wrapper = mountWizard()
    await flushPromises()
    const vm = getVM(wrapper)

    vm.handleMetricSelect('bloodGlucose')
    vm.goToStep(1)
    vm.goToStep(2)
    await nextTick()

    const sentence = () => wrapper.find('.confirm-sentence').text()
    expect(sentence()).toContain('当 血糖')
    expect(sentence()).toContain('≥ 7 时发出黄色提醒')
    expect(sentence()).toContain('≥ 11.1 时发出红色紧急提醒')
    expect(sentence()).toContain('≤ 3.9 时发出黄色提醒')
    expect(sentence()).toContain('≤ 3 时发出红色紧急提醒')
    expect((sentence().match(/红色紧急提醒/g) ?? []).length).toBe(2)

    vm.draft.criticalHigh = null
    await nextTick()
    expect(sentence()).not.toContain('≥ 11.1')
    expect((sentence().match(/红色紧急提醒/g) ?? []).length).toBe(1)
    // 数值以 strong 加粗（同字号只加字重）
    expect(wrapper.find('.confirm-sentence').html()).toContain('<strong')
    // 部分填写合法：置空一项后仍有边界，主按钮不被禁用（D-10）
    expect((wrapper.find('button.el-button--primary').element as HTMLButtonElement).disabled).toBe(
      false
    )
    wrapper.unmount()
  })

  it('7. 未选指标时四项输入禁用且说明可见；四项全空时确认语句降级（THR-06）', async () => {
    const wrapper = mountWizard()
    await flushPromises()
    const vm = getVM(wrapper)

    // 先进入第 2 步：未选指标 → 输入全部禁用，但说明文案仍在（教学先行）
    vm.goToStep(1)
    await nextTick()
    const inputs = wrapper.findAll('input.el-input-number__inner')
    expect(inputs).toHaveLength(4)
    for (const input of inputs) {
      expect((input.element as HTMLInputElement).disabled).toBe(true)
    }
    expect(wrapper.findAll('.threshold-field__hint')).toHaveLength(4)

    // 四项全空 → 确认语句降级
    vm.goToStep(2)
    await nextTick()
    expect(wrapper.find('.confirm-sentence').text()).toContain('尚未设置任何有效阈值')
    wrapper.unmount()
  })

  it('8. 编辑模式打开即在第 2 步，回退后选择器禁用且第 1 步显示 指标已锁定（D-02）', async () => {
    const wrapper = mountWizard(EDITING_THRESHOLD)
    await flushPromises()
    const vm = getVM(wrapper)

    expect(vm.currentStep).toBe(1)
    expect(wrapper.text()).toContain('指标已锁定')
    expect(wrapper.find('.el-step.is-skipped').exists()).toBe(true)
    // 编辑草稿直接来自已有阈值行
    expect(vm.draft.warningHigh).toBe(7)
    expect(vm.draft.criticalLow).toBe(3)
    // 上一步回退到第 1 步：指标锁定，选择器不可改
    vm.goToStep(0)
    await nextTick()
    expect((wrapper.find('.el-select input').element as HTMLInputElement).disabled).toBe(true)
    wrapper.unmount()
  })

  it('9. 未保存守卫：草稿偏离快照触发确认且不关闭；与快照一致时直接关闭（T-12-25）', async () => {
    // 脏分支：选定指标后草稿偏离打开快照 → 确认框弹出、done 不被调用
    const doneDirty = vi.fn()
    const wrapperDirty = mountWizard()
    await flushPromises()
    ;getVM(wrapperDirty).handleMetricSelect('bloodGlucose')
    ;getVM(wrapperDirty).handleBeforeClose(doneDirty)
    await flushPromises()
    expect(confirmMock).toHaveBeenCalledTimes(1)
    // 确认文案逐字来自 dialog.unsavedConfirm 字典键
    expect(confirmMock.mock.calls[0][0]).toBe('内容尚未保存，确定离开？')
    expect(doneDirty).not.toHaveBeenCalled()
    wrapperDirty.unmount()

    // 干净分支：未做任何修改 → 直接关闭，零确认调用
    const doneClean = vi.fn()
    const wrapperClean = mountWizard()
    await flushPromises()
    ;getVM(wrapperClean).handleBeforeClose(doneClean)
    await flushPromises()
    expect(doneClean).toHaveBeenCalledTimes(1)
    expect(confirmMock).toHaveBeenCalledTimes(1)
    wrapperClean.unmount()
  })

  it('10. 焦点规则：编辑打开聚焦第一个数值输入框；新会话聚焦选择器输入（Contract 1）', async () => {
    const focusSpy = vi.spyOn(HTMLElement.prototype, 'focus').mockImplementation(() => {})

    // 编辑模式：打开即第 2 步 → 第一个 el-input-number 的 input 获得焦点
    const wrapperEdit = mountWizard(EDITING_THRESHOLD)
    await flushPromises()
    const firstInput = wrapperEdit.findAll('input.el-input-number__inner')[0].element
    expect(focusSpy.mock.contexts).toContain(firstInput)
    wrapperEdit.unmount()

    // 新会话：第 1 步 → 选择器 input 获得焦点，选定指标后焦点规则不回退
    const wrapperFresh = mountWizard()
    await flushPromises()
    const pickerInput = wrapperFresh.find('.el-select input').element
    expect(focusSpy.mock.contexts).toContain(pickerInput)
    ;getVM(wrapperFresh).handleMetricSelect('bloodGlucose')
    await nextTick()
    expect(focusSpy.mock.contexts).toContain(pickerInput)
    wrapperFresh.unmount()
  })

  it('11. 修改后恢复推荐值：四项回到推荐值且徽标重现（THR-02 / D-08）', async () => {
    const wrapper = mountWizard()
    await flushPromises()
    const vm = getVM(wrapper)

    vm.handleMetricSelect('bloodGlucose')
    vm.goToStep(1)
    await nextTick()
    vm.draft.warningLow = 5
    vm.draft.criticalHigh = 20
    await nextTick()
    // 改过的字段不再等于推荐值 → 徽标消失
    expect(wrapper.findAll('.recommend-badge')).toHaveLength(2)

    vm.restoreRecommended()
    await nextTick()
    expect(vm.draft.warningLow).toBe(3.9)
    expect(vm.draft.criticalHigh).toBe(11.1)
    expect(wrapper.findAll('.recommend-badge')).toHaveLength(4)
    wrapper.unmount()
  })

  it('12. 保存成功：提交一次、emit saved 并关闭；请求悬挂时闩锁阻止重复提交', async () => {
    let resolveSave: (value: unknown) => void = () => {}
    createThresholdMock.mockReturnValue(
      new Promise(resolve => {
        resolveSave = resolve
      })
    )
    const wrapper = mountWizard()
    await flushPromises()
    const vm = getVM(wrapper)

    vm.handleMetricSelect('bloodGlucose')
    vm.goToStep(1)
    vm.goToStep(2)
    await nextTick()

    const firstSubmit = vm.handleSubmit()
    const secondSubmit = vm.handleSubmit()
    await flushPromises()
    // 闩锁：第二次调用直接短路，只发一次请求；主按钮 loading 态禁用
    expect(createThresholdMock).toHaveBeenCalledTimes(1)
    expect((wrapper.find('button.el-button--primary').element as HTMLButtonElement).disabled).toBe(
      true
    )

    resolveSave({ code: 200 })
    await Promise.all([firstSubmit, secondSubmit])
    await flushPromises()
    const savedEvents = wrapper.emitted('saved') ?? []
    expect(savedEvents).toHaveLength(1)
    // 保存成功后关闭弹窗
    const closeEvents = wrapper.emitted('update:modelValue') ?? []
    expect(closeEvents[closeEvents.length - 1]).toEqual([false])
    // 载荷：userId 来自认证 store，metricKey 为所选指标
    expect(createThresholdMock.mock.calls[0][0]).toMatchObject({
      userId: 1,
      metricKey: 'bloodGlucose',
      warningLow: 3.9,
      warningHigh: 7,
      criticalLow: 3,
      criticalHigh: 11.1
    })
    wrapper.unmount()
  })

  it('13. 保存被校验拒绝：内联错误点亮输入框、草稿原样、弹窗不关、零 toast 出口', async () => {
    // HTTP 400 + fieldErrors → normalizeApiError 归为 validation kind
    createThresholdMock.mockRejectedValueOnce(
      new ApiError('请检查填写内容', 'validation', 400, undefined, {
        warningHigh: '警告高值必须小于严重高值'
      })
    )
    const wrapper = mountWizard()
    await flushPromises()
    const vm = getVM(wrapper)

    vm.handleMetricSelect('bloodGlucose')
    vm.goToStep(1)
    vm.goToStep(2)
    await nextTick()
    await vm.handleSubmit()
    await flushPromises()

    // 校验拒绝发生在第 3 步：向导退回第 2 步让汇总块与内联错误可见（GOV.UK 双呈现）
    expect(vm.currentStep).toBe(1)
    expect(wrapper.find('.form-error-summary').exists()).toBe(true)
    // 后端 fieldErrors 点亮对应输入框（服务端与客户端共用同一投影）
    const messages = wrapper.findAll('.el-form-item__error').map(e => e.text())
    expect(messages).toContain('警告高值必须小于严重高值')
    // D-12：草稿绝不被失败重置，弹窗保持打开，也不 emit error 交给 toast
    expect(vm.draft.warningHigh).toBe(7)
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.emitted('error')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })
})
