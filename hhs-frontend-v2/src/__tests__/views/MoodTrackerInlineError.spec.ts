import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { Mock } from 'vitest'

/**
 * ERR-03 / ERR-05 的端到端锚点（`views/mood/MoodTracker.vue`）。
 *
 * 这个页面是本阶段唯一「改造前根本无法接收内联错误」的表单：`<el-form>` 没有
 * `:model`、也没有任何 `prop`，四个受后端约束的字段由裸 `reactive` 与裸按钮组驱动。
 * 因此本 spec 的每一条断言都对应一次**结构性**交付：
 *
 *  1. 一个 `moodScore` 的 400 在汇总里读作「心情评分」，**绝不**是驼峰键 `moodScore`
 *     —— 这是 plan 14-03 追加那 5 个 `FIELD_LABELS` 键的全部理由，也是本 plan 的
 *     存在理由。断言的形态刻意是两条 `not.toContain`，让失败信息直接点名漏出的键。
 *  2. 只有被拒的两个字段带 `is-error`；其余两个滑块保留用户选择的值（ERR-05）。
 *  3. validation 走汇总 + 内联，零 toast —— 一次 400 不做两次呈现。
 *
 * element-plus 用 importOriginal 部分 mock（同 GoalFormInlineError.spec.ts:43-60）。
 */

vi.mock('@/api/mood', () => ({
  moodApi: {
    create: vi.fn(),
    list: vi.fn(),
    getInsights: vi.fn(),
    remove: vi.fn()
  }
}))

// 页面 onMounted 消费一整组 gsap 函数（springPop / magneticHover / countUp …）。
vi.mock('@/composables/useGsap', () => ({
  springPop: vi.fn(),
  staggerReveal: vi.fn(),
  countUp: vi.fn(),
  cleanupScrollTriggers: vi.fn(),
  tilt3D: vi.fn(),
  magneticHover: vi.fn(),
  buttonPress: vi.fn(),
  hoverLift: vi.fn(),
  rippleClick: vi.fn(),
  DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
  EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
}))

vi.mock('element-plus', async importOriginal => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: Object.assign(vi.fn(() => ({ close: vi.fn() })), {
      error: vi.fn(),
      success: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
      closeAll: vi.fn()
    }),
    ElMessageBox: Object.assign(vi.fn(), {
      confirm: vi.fn(),
      alert: vi.fn(),
      prompt: vi.fn()
    })
  }
})

import MoodTracker from '@/views/mood/MoodTracker.vue'
import { moodApi } from '@/api/mood'
import { ElMessage, ElMessageBox } from 'element-plus'

const createMock = moodApi.create as unknown as Mock
const listMock = moodApi.list as unknown as Mock
const insightsMock = moodApi.getInsights as unknown as Mock
const confirmMock = ElMessageBox.confirm as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/**
 * showError 的 VNode 分支有模块级 2000ms 去重闩锁。同文案的用例会互相吞掉对方的
 * toast，让断言读成 0 —— 那是闩锁生效，不是呈现缺失。让每次 Date.now() 前进 60s。
 */
let nowCursor = 1_700_000_000_000
vi.spyOn(Date, 'now').mockImplementation(() => (nowCursor += 60_000))

/** 四通道统计：对象式调用 + 函数式旧 API（ElMessage.error / success …）。 */
function toastCount(): number {
  return (
    ElMessageMock.mock.calls.length +
    ElMessageMock.error.mock.calls.length +
    ElMessageMock.success.mock.calls.length +
    ElMessageMock.warning.mock.calls.length +
    ElMessageMock.info.mock.calls.length
  )
}

/**
 * 客户端校验的 stub 出口（同 GoalFormInlineError.spec.ts:132）。
 *
 * 默认 valid=true：本 spec 测的是服务端 400 分支，`validate()` 必须能放行到 API。
 */
let nextClientInvalid: Record<string, Array<{ message?: string }>> | undefined

const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-skeleton': {
    template: '<div class="el-skeleton"><slot /></div>',
    props: ['loading', 'animated']
  },
  'el-skeleton-item': { template: '<div class="el-skeleton-item" />', props: ['variant', 'style'] },
  'el-empty': { template: '<div class="el-empty">{{ description }}</div>', props: ['description'] },
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': {
    template: '<div class="el-col"><slot /></div>',
    props: ['span', 'xs', 'sm', 'md', 'lg']
  },
  'el-divider': { template: '<hr class="el-divider" />' },
  // 真实 EP 的 validate 回调签名是 (valid, invalidFields)；stub 必须同形。
  'el-form': {
    template: '<form class="el-form"><slot /></form>',
    methods: {
      validate: (cb: (valid: boolean, invalidFields?: unknown) => void) => {
        if (nextClientInvalid) {
          const invalid = nextClientInvalid
          nextClientInvalid = undefined
          return Promise.resolve(cb(false, invalid))
        }
        return Promise.resolve(cb(true, undefined))
      },
      validateField: vi.fn(),
      clearValidate: vi.fn(),
      resetFields: vi.fn()
    }
  },
  // 内联错误必须真的渲染到 DOM（含 is-error 类），才能证明 :error 绑对了 prop。
  'el-form-item': {
    template:
      '<div class="el-form-item" :class="{ \'is-error\': !!error }"><label>{{ label }}</label><slot /><span v-if="error" class="el-form-item__error">{{ error }}</span></div>',
    props: ['label', 'prop', 'error']
  },
  // 滑块必须真的回写 modelValue —— 否则「滑块保留值」这条断言无从观察。
  'el-slider': {
    template:
      '<input class="el-slider" type="range" :min="min" :max="max" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value)); $emit(\'change\', Number($event.target.value))" />',
    props: ['modelValue', 'min', 'max', 'marks', 'showStops']
  },
  'el-input': {
    template: '<textarea class="el-input" :value="modelValue" />',
    props: ['modelValue', 'placeholder', 'type', 'rows', 'readonly']
  },
  'el-button': {
    template: '<button class="el-button"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'link']
  },
  // FormErrorSummary 内部用 el-alert 承载标题（默认插槽在 <p> 里，列表放在外面）。
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /></div>',
    props: ['type', 'closable', 'showIcon']
  }
}

function mountMood() {
  // attachTo 是必需的：focusSummary() 只在真实挂载到 document 的元素上才改变
  // document.activeElement，游离节点上断言 activeElement 永远读到 <body>。
  return mount(MoodTracker, {
    attachTo: document.body,
    global: { stubs: globalStubs }
  })
}

/**
 * 服务端 400：只拒 `moodScore` 与 `sleepQuality`，倒序下发以证明 fieldOrder 生效。
 * 文案与 `RecordMoodRequest` 的 `@NotNull` / `@Min` message 同形。
 */
function validationRejection() {
  return {
    response: {
      status: 400,
      data: {
        code: 400,
        message: '请检查以下填写内容',
        errorCode: 'validation.invalid_parameter',
        fieldErrors: [
          { field: 'sleepQuality', message: '睡眠质量不能为空' },
          { field: 'moodScore', message: '心情评分不能为空' }
        ]
      }
    }
  }
}

describe('mood/MoodTracker.vue — 400 字段校验的内联 + 汇总双呈现（ERR-03 / ERR-05）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    nextClientInvalid = undefined
    createMock.mockReset()
    listMock.mockReset()
    insightsMock.mockReset()
    listMock.mockResolvedValue({ code: 200, message: 'success', data: [] })
    insightsMock.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { avgMood: null, avgEnergy: null, avgStress: null, avgSleep: null, entryCount: 0, trend: 'flat' }
    })
    confirmMock.mockReset()
    confirmMock.mockResolvedValue('confirm')
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('400：汇总读作「心情评分」与「睡眠质量」，两个驼峰键一个都不许露出来', async () => {
    const wrapper = mountMood()
    await flushPromises()

    const vm = wrapper.vm as any
    // 用户在两个滑块上做了选择，这次提交会被服务端拒掉那两项之外的字段。
    vm.form.energyLevel = 7
    vm.form.stressLevel = 3
    const beforeEnergy = vm.form.energyLevel
    const beforeStress = vm.form.stressLevel

    createMock.mockRejectedValueOnce(validationRejection())

    await vm.handleSubmit()
    await flushPromises()

    // 1. 零 toast —— validation 由汇总 + 内联承担呈现（D-01）。
    expect(toastCount()).toBe(0)

    // 2. 汇总块存在，条目按 fieldOrder（moodScore 先于 sleepQuality）。
    const summary = wrapper.find('.form-error-summary')
    expect(summary.exists()).toBe(true)
    expect(summary.text()).toContain('请修正以下 2 项填写问题')

    const items = summary.findAll('li')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toBe('心情评分：心情评分不能为空')
    expect(items[1].text()).toBe('睡眠质量：睡眠质量不能为空')

    // 3. 渲染文本里的中文标签确实来自 14-03 追加的那 5 个 FIELD_LABELS 键。
    const summaryText = summary.text()
    expect(summaryText).toContain('心情评分')
    expect(summaryText).toContain('睡眠质量')

    // 4. 原始驼峰键绝不能出现在用户可读文本里（D-15 / Contract 4.5 规则 2）。
    //    两条独立断言而不是一条合并正则：失败信息会直接点名漏出的那个键。
    expect(summaryText).not.toContain('moodScore')
    expect(summaryText).not.toContain('sleepQuality')
    // 内联文案同理 —— 它们只承载后端消息，但同样不该把键名带出来。
    const inlineText = wrapper.findAll('.el-form-item__error').map(node => node.text()).join('|')
    expect(inlineText).not.toContain('moodScore')
    expect(inlineText).not.toContain('sleepQuality')

    // 5. 恰好两个 el-form-item 带 is-error（:error 真的绑定到被拒字段）。
    expect(wrapper.findAll('.el-form-item.is-error')).toHaveLength(2)
    expect(wrapper.findAll('.el-form-item__error').map(node => node.text())).toEqual([
      '心情评分不能为空',
      '睡眠质量不能为空'
    ])

    // 6. 焦点只去汇总块一次（Contract 4.4），绝不去第一个字段。
    expect(document.activeElement).toBe(summary.element as HTMLElement)

    // 7. ERR-05：未被拒的两个滑块保留用户输入的值。
    expect(vm.form.energyLevel).toBe(beforeEnergy)
    expect(vm.form.stressLevel).toBe(beforeStress)

    // 8. 五个 prop 都带 data-field 锚点（Contract 4.1 第 ② 条）。
    expect(wrapper.findAll('[data-field]').length).toBeGreaterThanOrEqual(5)

    wrapper.unmount()
  })

  it('客户端校验失败（未发出请求）：同样填充汇总、聚焦汇总、零 toast', async () => {
    const wrapper = mountMood()
    await flushPromises()

    const vm = wrapper.vm as any
    // EP 只会在字段下方内联提示，没有「一共错了几项」；汇总必须由 setErrors 补上。
    nextClientInvalid = {
      sleepQuality: [{ message: '请设置睡眠质量' }],
      moodScore: [{ message: '请选择心情评分' }]
    }

    await vm.handleSubmit()
    await flushPromises()

    const items = wrapper.findAll('.form-error-summary li')
    expect(items).toHaveLength(2)
    // fieldOrder 排序：moodScore 在前（即使 invalidFields 的键顺序相反）。
    expect(items[0].text()).toBe('心情评分：请选择心情评分')
    expect(items[1].text()).toBe('睡眠质量：请设置睡眠质量')

    expect(document.activeElement).toBe(wrapper.find('.form-error-summary').element as HTMLElement)
    expect(toastCount()).toBe(0)
    expect(createMock).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('删除确认框以 AbortError 取消：零提示，且不发 DELETE（ERR-08）', async () => {
    // 用 AbortError 而不是字符串 'cancel'：旧的手写判断 `e !== 'cancel'` 对字符串
    // 恰好能用，因此字符串形态证明不了任何事 —— 它对 AbortError 判成真失败并弹红灯。
    confirmMock.mockRejectedValueOnce(Object.assign(new Error('canceled'), { name: 'AbortError' }))

    const wrapper = mountMood()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.handleDelete(1)
    await flushPromises()

    expect(toastCount()).toBe(0)
    expect(moodApi.remove).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('删除已被删除的行（FORBIDDEN 信封）：恰好一条提示，且文案是后端消息（presentStates 护栏）', async () => {
    // 真实形态：BusinessException(AUTH_FORBIDDEN) → HTTP 200 + 信封 code 500 + errorCode。
    // 缺 presentStates 时 showError 在 resolveMessage 之前就返回 → 0 条。
    vi.mocked(moodApi.remove).mockRejectedValueOnce({
      response: {
        status: 200,
        data: { code: 500, errorCode: 'auth.forbidden', message: '心情记录不存在或无权删除' }
      }
    })

    const wrapper = mountMood()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.handleDelete(1)
    await flushPromises()

    // forbidden 走 VNode 分支：一条常驻 toast，文案取后端消息。
    expect(ElMessageMock).toHaveBeenCalledTimes(1)
    const options = ElMessageMock.mock.calls[0][0] as { message: { children?: unknown[] } }
    const text = (options.message.children as Array<{ children?: unknown }>)[0]
    expect(text?.children).toBe('心情记录不存在或无权删除')

    wrapper.unmount()
  })
})