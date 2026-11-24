import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { Mock } from 'vitest'

/**
 * ERR-03 + ERR-05 的端到端锚点（goals/Index.vue）。
 *
 * 三个用例各自证明一条**无法用 grep 证明**的性质：
 *  1. 服务端 400 → 汇总 + 内联 + 零 toast，且汇总里的标签是**本页覆盖值「目标标题」**
 *     而不是全局表的「标题」（Contract 4.5 规则 4 的 labels 覆盖）。
 *  2. 同一次 400 之后，用户已填的值一字未变、弹窗仍然打开（ERR-05，按值快照断言）。
 *  3. 删除一个已被删除的行（RESOURCE_NOT_FOUND 信封）仍然恰好一条提示 ——
 *     缺 `presentStates: true` 时这里会是 0（B1 的回归护栏）。
 *
 * element-plus 用 importOriginal 部分 mock（同 ErrorLayerSample.spec.ts:53-68）：
 * 保住组件导出，只替换 ElMessage / ElMessageBox，这样才能既挂载页面、又对每次
 * toast（含对象式）计数、并让确认框可控。
 */

vi.mock('@/api/goals', () => ({
  goalsApi: {
    create: vi.fn(),
    list: vi.fn(),
    get: vi.fn(),
    addProgress: vi.fn(),
    remove: vi.fn()
  }
}))

// 页面 onMounted 消费 gsap 动效；形状照抄 ErrorLayerSample.spec.ts:36-46。
vi.mock('@/composables/useGsap', () => ({
  staggerReveal: vi.fn(),
  progressAnimate: vi.fn(),
  cleanupScrollTriggers: vi.fn(),
  tilt3D: vi.fn(),
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

import Goals from '@/views/goals/Index.vue'
import { goalsApi } from '@/api/goals'
import { ElMessage, ElMessageBox } from 'element-plus'

const createMock = goalsApi.create as unknown as Mock
const listMock = goalsApi.list as unknown as Mock
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

/** 错误类调用：对象式且 type='error'，或函数式 ElMessage.error。 */
function errorToastCount(): number {
  const objectCalls = ElMessageMock.mock.calls.filter(
    ([options]) => (options as { type?: string } | undefined)?.type === 'error'
  ).length
  return objectCalls + ElMessageMock.error.mock.calls.length
}

/**
 * 客户端校验的 stub 出口。
 *
 * 默认返回 valid=true（绝大多数用例测的是服务端分支）。把 `nextClientInvalid`
 * 设成一批 `invalidFields` 后，下一次 validate 就按 Element Plus 的真实回调签名
 * `(valid, invalidFields)` 回放一次客户端失败 —— 这是唯一能把
 * `collectInvalidFieldMessages → setErrors → 汇总块` 这条链路跑起来的办法，
 * 否则「客户端失败也要汇总」（Contract 4.3）只能靠读代码相信。
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
  'el-empty': {
    template: '<div class="el-empty">{{ description }}</div>',
    props: ['description']
  },
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': {
    template: '<div class="el-col"><slot /></div>',
    props: ['span', 'xs', 'sm', 'md', 'lg']
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
  'el-progress': { template: '<div class="el-progress" />', props: ['percentage', 'color', 'strokeWidth'] },
  // el-dialog 只在 modelValue 为真时渲染插槽 —— formRef 依赖它存在。
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width']
  },
  // 真实 EP 的 validate 回调签名是 (valid, invalidFields)；stub 必须同形，
  // 否则被测分支拿不到 invalidFields。
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
      clearValidate: vi.fn()
    }
  },
  // 内联错误必须真的渲染到 DOM（含 is-error 类），才能证明 :error 绑对了 prop。
  'el-form-item': {
    template:
      '<div class="el-form-item" :class="{ \'is-error\': !!error }"><label>{{ label }}</label><slot /><span v-if="error" class="el-form-item__error">{{ error }}</span></div>',
    props: ['label', 'prop', 'error']
  },
  'el-input': {
    template: '<input class="el-input" :value="modelValue" />',
    props: ['modelValue', 'placeholder', 'type', 'rows', 'readonly']
  },
  'el-input-number': {
    template: '<input class="el-input-number" :value="modelValue" />',
    props: ['modelValue', 'precision', 'min', 'max']
  },
  'el-select': {
    template: '<select class="el-select"><slot /></select>',
    props: ['modelValue', 'placeholder', 'style', 'disabled']
  },
  'el-option': {
    template: '<option class="el-option" :value="value">{{ label }}</option>',
    props: ['label', 'value']
  },
  'el-date-picker': {
    template: '<input class="el-date-picker" />',
    props: ['modelValue', 'type', 'rangeSeparator', 'startPlaceholder', 'endPlaceholder', 'valueFormat']
  },
  'el-button': {
    template: '<button class="el-button"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'link']
  },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  // FormErrorSummary 内部用 el-alert 承载标题（默认插槽在 <p> 里，列表放在外面）。
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /></div>',
    props: ['type', 'closable', 'showIcon']
  }
}

function mountGoals() {
  // attachTo 是必需的：focusSummary() 只在真实挂载到 document 的元素上才改变
  // document.activeElement，游离节点上断言 activeElement 永远读到 <body>。
  return mount(Goals, {
    attachTo: document.body,
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

/** 服务端 400：标题与目标值两个字段被拒，**倒序**下发以证明 fieldOrder 生效。 */
function validationRejection() {
  return {
    response: {
      status: 400,
      data: {
        code: 400,
        message: '请检查以下填写内容',
        errorCode: 'validation.invalid_parameter',
        fieldErrors: [
          { field: 'targetValue', message: '目标值必须大于 0' },
          { field: 'title', message: '目标标题不能为空' }
        ]
      }
    }
  }
}

describe('goals/Index.vue — 400 字段校验的内联 + 汇总双呈现（ERR-03 / ERR-05）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    nextClientInvalid = undefined
    createMock.mockReset()
    listMock.mockReset()
    listMock.mockResolvedValue({ code: 200, message: 'success', data: [] })
    confirmMock.mockReset()
    confirmMock.mockResolvedValue('confirm')
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('400：汇总首条是「目标标题：…」（覆盖值而非全局「标题」），两项内联，零 toast', async () => {
    const wrapper = mountGoals()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showCreateDialog()
    await flushPromises()
    vm.createForm.title = '降低体重至70kg'
    vm.createForm.metricKey = 'weight'
    vm.createForm.targetValue = 70
    vm.createForm.unit = 'kg'
    vm.createForm.dateRange = ['2026-01-01', '2026-03-01']

    createMock.mockRejectedValueOnce(validationRejection())

    await vm.handleCreate()
    await flushPromises()

    // 1. 零 toast —— validation 由汇总 + 内联承担呈现（D-01）。
    expect(toastCount()).toBe(0)

    // 2. 汇总块存在，且条目顺序按 fieldOrder（title 先于 targetValue），
    //    逐字断言标签是覆盖值「目标标题」——全局表的「标题」会拼出
    //    「标题：目标标题不能为空」，与期望串不相等，因此这条断言不可能空过。
    const summary = wrapper.find('.form-error-summary')
    expect(summary.exists()).toBe(true)
    expect(summary.text()).toContain('请修正以下 2 项填写问题')

    const items = summary.findAll('li')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toBe('目标标题：目标标题不能为空')
    expect(items[1].text()).toBe('目标值：目标值必须大于 0')

    // 3. 恰好两个 el-form-item 带 is-error（:error 真的绑定到被拒字段）。
    expect(wrapper.findAll('.el-form-item.is-error')).toHaveLength(2)
    expect(wrapper.findAll('.el-form-item__error').map(node => node.text())).toEqual([
      '目标标题不能为空',
      '目标值必须大于 0'
    ])

    // 4. 焦点只去汇总块一次（Contract 4.4），绝不去第一个字段：
    //    首个无效字段的可见性由 is-error 描边承担，不由焦点承担。
    const summaryEl = summary.element as HTMLElement
    expect(document.activeElement).toBe(summaryEl)

    // 5. data-field 锚点齐备：五个创建字段 + 一个进度字段。
    expect(wrapper.findAll('[data-field]').length).toBeGreaterThanOrEqual(5)

    wrapper.unmount()
  })

  it('客户端校验失败（未发出请求）：同样填充汇总、聚焦汇总、零 toast', async () => {
    const wrapper = mountGoals()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showCreateDialog()
    await flushPromises()
    vm.createForm.title = '降低体重至70kg'

    // 客户端规则失败：EP 只会在字段下方内联提示，没有「一共错了几项」。
    nextClientInvalid = {
      metricKey: [{ message: '请选择指标类型' }],
      unit: [{ message: '请输入单位' }]
    }

    await vm.handleCreate()
    await flushPromises()

    // 1. 汇总块按 fieldOrder 排序（metricKey 在 unit 之前）。
    const items = wrapper.findAll('.form-error-summary li')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toBe('指标类型：请选择指标类型')
    expect(items[1].text()).toBe('单位：请输入单位')

    // 2. 焦点去汇总，零 toast，且请求一次都没发。
    expect(document.activeElement).toBe(wrapper.find('.form-error-summary').element as HTMLElement)
    expect(toastCount()).toBe(0)
    expect(createMock).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('两次提交之间 reset()：上一轮投影不残留（第二次失败走 toast，汇总必须消失）', async () => {
    const wrapper = mountGoals()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showCreateDialog()
    await flushPromises()
    vm.createForm.title = '降低体重至70kg'
    vm.createForm.metricKey = 'weight'
    vm.createForm.targetValue = 70
    vm.createForm.unit = 'kg'
    vm.createForm.dateRange = ['2026-01-01', '2026-03-01']

    // 第一次：400，留下两行服务端投影。
    createMock.mockRejectedValueOnce(validationRejection())
    await vm.handleCreate()
    await flushPromises()
    expect(wrapper.findAll('.form-error-summary li')).toHaveLength(2)

    // 第二次：传输层 500 → 一条 toast，`apply` 不动作（kind 不是 validation）。
    // 若提交前的 reset() 缺失，上一轮的两行汇总会与这条 toast 并存 —— 一次失败的
    // 两种呈现、且其中一种是陈旧内容。弹窗在两种失败下都保持打开，因此汇总块确实
    // 还在渲染树里，这条断言才有意义。
    createMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })
    await vm.handleCreate()
    await flushPromises()

    expect(wrapper.find('.form-error-summary').exists()).toBe(false)
    expect(wrapper.findAll('.el-form-item.is-error')).toHaveLength(0)
    expect(errorToastCount()).toBe(1)
    // ERR-05：两次失败都不许碰用户输入。
    expect(vm.createForm.title).toBe('降低体重至70kg')
    expect(vm.createVisible).toBe(true)

    wrapper.unmount()
  })

  it('400 之后：已填值一字未变，弹窗仍然打开（ERR-05 值快照）', async () => {
    const wrapper = mountGoals()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showCreateDialog()
    await flushPromises()
    vm.createForm.title = '降低体重至70kg'
    vm.createForm.metricKey = 'weight'
    vm.createForm.targetValue = 70
    vm.createForm.unit = 'kg'
    vm.createForm.dateRange = ['2026-01-01', '2026-03-01']

    // 提交前快照：失败后必须逐字相等。
    const before = {
      title: vm.createForm.title,
      metricKey: vm.createForm.metricKey,
      targetValue: vm.createForm.targetValue,
      unit: vm.createForm.unit,
      dateRange: [...vm.createForm.dateRange]
    }

    createMock.mockRejectedValueOnce(validationRejection())

    await vm.handleCreate()
    await flushPromises()

    expect({
      title: vm.createForm.title,
      metricKey: vm.createForm.metricKey,
      targetValue: vm.createForm.targetValue,
      unit: vm.createForm.unit,
      dateRange: vm.createForm.dateRange
    }).toEqual(before)

    // 弹窗未关闭：关闭只发生在创建解析成功之后。
    expect(vm.createVisible).toBe(true)
    // 汇总仍然在页面上 —— 这是「失败可见」而不是「失败后清空」。
    expect(wrapper.find('.form-error-summary').exists()).toBe(true)

    wrapper.unmount()
  })

  // 删除功能的相关用例（AbortError 取消、RESOURCE_NOT_FOUND 信封）已随功能一并移除：
  // 后端 DELETE /api/goals/{id} 端点已回滚，前端「删除」按钮与 handleDelete 也撤掉了，
  // 保留这两个用例只会测一个不存在的方法。
})