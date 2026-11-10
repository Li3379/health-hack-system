import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { Mock } from 'vitest'

/**
 * ERR-03 / ERR-05 / ERR-08 的端到端锚点（reminders/Index.vue）。
 *
 * 四个用例各自证明一条**无法用 grep 证明**的性质：
 *  1. 服务端 400 → 汇总 + 内联 + 零 toast，汇总标签是本页覆盖值「提醒标题」
 *     而不是全局表的「标题」（Contract 4.5 规则 4）。注意：后端 `/api/reminders`
 *     的 DTO 目前没有 `@Valid`，所以服务端字段校验在真实环境里**不可达** ——
 *     这条连线是防御性的，ledger 的 `kind 覆盖` 列已注明。
 *  2. 切换失败只发一次请求：旧实现在 catch 里回退成一条 toast 再重新 `list()`，
 *     于是「一次失败触发第二个请求」。这里断言 `list` 调用次数在失败处理前后不变。
 *  3. 切换一条已被删除的提醒（RESOURCE_NOT_FOUND 信封）仍然恰好一条提示，
 *     且文案是后端消息 —— 缺 `presentStates: true` 时这里会是 0（B1 回归）。
 *  4. 确认框以 AbortError 取消：零提示。手写 cancel 判断已删除，取消路径是裸 return。
 */

vi.mock('@/api/reminders', () => ({
  remindersApi: {
    create: vi.fn(),
    list: vi.fn(),
    toggle: vi.fn(),
    remove: vi.fn()
  }
}))

// 页面 onMounted 消费 staggerReveal；形状照抄 ErrorLayerSample.spec.ts 的 useGsap mock。
vi.mock('@/composables/useGsap', () => ({
  staggerReveal: vi.fn(),
  cleanupScrollTriggers: vi.fn(),
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

import Reminders from '@/views/reminders/Index.vue'
import { remindersApi } from '@/api/reminders'
import { ElMessage, ElMessageBox } from 'element-plus'

const createMock = remindersApi.create as unknown as Mock
const listMock = remindersApi.list as unknown as Mock
const toggleMock = remindersApi.toggle as unknown as Mock
const confirmMock = ElMessageBox.confirm as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/**
 * showError 的 VNode 分支有模块级 2000ms 去重闩锁；同文案的用例会互相吞掉对方的
 * toast，让断言读成 0。让每次 Date.now() 前进 60s。
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

interface VNodeLike {
  type?: unknown
  props?: Record<string, unknown>
  children?: unknown
}

/** 深度遍历 VNode，返回第一个带指定 class 片段的节点（同 ErrorLayerSample.spec.ts:110）。 */
function findNodeByClass(node: unknown, className: string): VNodeLike | undefined {
  if (!node || typeof node === 'string' || !Array.isArray((node as VNodeLike).children)) {
    return undefined
  }
  const target = node as VNodeLike
  for (const child of target.children as unknown[]) {
    if (!child || typeof child !== 'object') continue
    const candidate = child as VNodeLike
    const classes = String(candidate.props?.class ?? '')
    if (classes.includes(className)) return candidate
    const nested = findNodeByClass(candidate, className)
    if (nested) return nested
  }
  return undefined
}

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
  'el-switch': {
    template: '<input class="el-switch" type="checkbox" :checked="modelValue" @change="$emit(\'change\')" />',
    props: ['modelValue', 'activeText', 'inactiveText', 'inlinePrompt'],
    emits: ['change', 'update:modelValue']
  },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width']
  },
  // 真实 EP 的 validate 回调签名是 (valid, invalidFields)。
  'el-form': {
    template: '<form class="el-form"><slot /></form>',
    methods: {
      validate: (cb: (valid: boolean, invalidFields?: unknown) => void) =>
        Promise.resolve(cb(true, undefined)),
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
    props: ['modelValue', 'placeholder', 'type', 'rows']
  },
  'el-select': {
    template: '<select class="el-select"><slot /></select>',
    props: ['modelValue', 'placeholder', 'style']
  },
  'el-option': {
    template: '<option class="el-option" :value="value">{{ label }}</option>',
    props: ['label', 'value']
  },
  'el-button': {
    template: '<button class="el-button"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'link']
  },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /></div>',
    props: ['type', 'closable', 'showIcon']
  }
}

function mountReminders() {
  return mount(Reminders, {
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

const REMINDER = {
  id: 9,
  userId: 1,
  title: '每天八点喝水',
  description: '',
  reminderType: 'water',
  cronExpression: '0 0 8 * * *',
  isActive: true,
  lastTriggeredAt: null,
  createdAt: '2026-01-01 00:00:00',
  updatedAt: '2026-01-01 00:00:00'
}

describe('reminders/Index.vue — 表单双呈现、切换去重取、状态类失败可见（ERR-03/05/08）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    createMock.mockReset()
    listMock.mockReset()
    toggleMock.mockReset()
    listMock.mockResolvedValue({ code: 200, message: 'success', data: [REMINDER] })
    confirmMock.mockReset()
    confirmMock.mockResolvedValue('confirm')
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('400：汇总首条是「提醒标题：…」（覆盖值而非全局「标题」），一项内联，零 toast，值保留', async () => {
    const wrapper = mountReminders()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showCreateDialog()
    await flushPromises()
    vm.form.title = '每天八点喝水'
    vm.form.reminderType = 'water'
    vm.form.cronExpression = '0 0 8 * * *'

    createMock.mockRejectedValueOnce({
      response: {
        status: 400,
        data: {
          code: 400,
          message: '请检查以下填写内容',
          errorCode: 'validation.invalid_parameter',
          fieldErrors: [{ field: 'title', message: '提醒标题不能为空' }]
        }
      }
    })

    await vm.handleCreate()
    await flushPromises()

    // 1. 零 toast —— validation 由汇总 + 内联承担（D-01）。
    expect(toastCount()).toBe(0)

    // 2. 汇总逐字断言标签是覆盖值「提醒标题」；全局表的「标题」会拼出
    //    「标题：提醒标题不能为空」，与期望串不相等，因此不可能空过。
    const summary = wrapper.find('.form-error-summary')
    expect(summary.exists()).toBe(true)
    expect(summary.text()).toContain('请修正以下 1 项填写问题')

    const items = summary.findAll('li')
    expect(items).toHaveLength(1)
    expect(items[0].text()).toBe('提醒标题：提醒标题不能为空')

    // 3. 恰好一个 el-form-item 带 is-error。
    expect(wrapper.findAll('.el-form-item.is-error')).toHaveLength(1)
    expect(wrapper.findAll('.el-form-item__error').map(node => node.text())).toEqual([
      '提醒标题不能为空'
    ])

    // 4. ERR-05：输入保留、弹窗仍开。
    expect(vm.form.title).toBe('每天八点喝水')
    expect(vm.form.reminderType).toBe('water')
    expect(vm.form.cronExpression).toBe('0 0 8 * * *')
    expect(vm.createVisible).toBe(true)

    wrapper.unmount()
  })

  it('切换失败只发一次请求：失败分支不再重取列表', async () => {
    const wrapper = mountReminders()
    await flushPromises()

    // 挂载时 list 已被调用一次（真实条目入表）。
    const listCallsBeforeToggle = listMock.mock.calls.length

    toggleMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const vm = wrapper.vm as any
    await vm.handleToggle(9)
    await flushPromises()

    // 1. 一次失败一条提示。
    expect(errorToastCount()).toBe(1)

    // 2. 失败处理期间**没有**第二个 list 请求 —— 旧实现的最大缺陷。
    expect(listMock.mock.calls.length).toBe(listCallsBeforeToggle)

    wrapper.unmount()
  })

  it('切换已被删除的提醒（RESOURCE_NOT_FOUND 信封）：恰好一条提示，文案是后端消息（B1 回归）', async () => {
    toggleMock.mockRejectedValueOnce({
      response: {
        status: 200,
        data: { code: 500, errorCode: 'resource.not_found', message: '提醒不存在' }
      }
    })

    const wrapper = mountReminders()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.handleToggle(9)
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    // 断言文案而不只是条数：证明 presentStates 真的走到了 resolveMessage。
    const options = ElMessageMock.mock.calls[0][0] as { message: unknown }
    const textNode = findNodeByClass(options.message, 'error-toast__text')
    expect(textNode?.children).toBe('提醒不存在')

    wrapper.unmount()
  })

  it('确认框以 AbortError 取消：零提示（手写 cancel 判断已删除，取消路径是裸 return）', async () => {
    // 用 AbortError 而不是字符串 'cancel'：旧的手写判断 `e !== 'cancel'` 对字符串
    // 恰好能用，因此字符串形态**证明不了**任何事 —— 它对 AbortError 与归一化后的
    // `ApiError('', 'cancel')` 都判成真失败并弹红灯。这里用 AbortError 才能把
    // 「删除守卫」与「守卫写错了」区分开。
    const abortError = Object.assign(new Error('canceled'), { name: 'AbortError' })
    confirmMock.mockRejectedValueOnce(abortError)

    const wrapper = mountReminders()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.handleDelete(9)
    await flushPromises()

    expect(toastCount()).toBe(0)
    // 取消必须发生在请求之前：绝不为一次被取消的删除发出 DELETE。
    expect(remindersApi.remove).not.toHaveBeenCalled()

    wrapper.unmount()
  })
})