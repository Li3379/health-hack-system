import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'

/**
 * 发送失败的体验钉子（14-RESEARCH 开放问题 2 的 RESOLVED 结论）。
 *
 * <p>契约：一次发送失败必须
 *   1. 把用户输入**原样回填**输入框（气泡消失但文本不丢），
 *   2. 在输入区就地显示一行常驻说明（`发送未完成，请重试`），发送按钮就在旁边，
 *   3. **零 toast** —— 这条断言是防止有人「贴心地」给发送失败补一条 toast 的防线：
 *      那会让一次正在被注视的气泡消失变成一条转瞬即逝的横幅，比静默更糟。
 *
 * <p>同一文件顺带钉住 chat 页的另外两条通道（同一挂载面、同一组 mock）：
 * 删除会话的 confirm/api 拆分（取消静默、真实失败一条 toast），以及历史加载失败
 * 不再把空对话框当成「没有历史」。
 */

vi.mock('@/api/ai', () => ({
  aiApi: {
    chat: vi.fn(),
    getHistory: vi.fn(),
    clearHistory: vi.fn(),
    getSessions: vi.fn(),
    getRemainingCount: vi.fn(),
    classify: vi.fn()
  }
}))

vi.mock('@/api/health', () => ({
  healthApi: {
    getMetrics: vi.fn().mockResolvedValue({ code: 200, message: 'success', data: { records: [] } })
  }
}))

// Chat.vue 读 authStore.user?.avatar / nickname 渲染头像。
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: { id: 1, nickname: 'tester', avatar: '' } })
}))

// vi.mock 的工厂会被提升到文件顶部，因此共享 spy 必须同样提升，否则在工厂
// 求值时 `confirmMock` 还在 TDZ（Cannot access before initialization）。
const { confirmMock } = vi.hoisted(() => ({ confirmMock: vi.fn() }))

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
    ElMessageBox: { confirm: confirmMock }
  }
})

import Chat from '@/views/ai/Chat.vue'
import { useAiStore } from '@/stores/ai'
import { aiApi } from '@/api/ai'
import { ElMessage } from 'element-plus'

const chatMock = aiApi.chat as unknown as Mock
const getHistoryMock = aiApi.getHistory as unknown as Mock
const getSessionsMock = aiApi.getSessions as unknown as Mock
const getRemainingCountMock = aiApi.getRemainingCount as unknown as Mock
const clearHistoryMock = aiApi.clearHistory as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/**
 * showError 的 2000ms VNode 去重闩锁是模块级、跨用例存活的：相邻两个用例若弹出
 * 文案相同的 VNode toast，后一个会被前一个闩掉，计数变成 0。
 * 让 Date.now 单调前进 5 秒/次，用例之间就不会互相干扰（同 ErrorLayerSample.spec.ts:306-308）。
 */
let clock = 1_700_000_000_000
beforeEach(() => {
  vi.spyOn(Date, 'now').mockImplementation(() => (clock += 5000))
})

/** 错误类 toast 计数：对象式且 type='error'，或函数式 ElMessage.error。 */
function errorToastCount(): number {
  const objectCalls = ElMessageMock.mock.calls.filter(
    ([options]) => (options as { type?: string })?.type === 'error'
  ).length
  return objectCalls + ElMessageMock.error.mock.calls.length
}

/**
 * el-input 的桩：把 v-model 变成可写 textarea。
 * 真实的 el-input 在 happy-dom 里不会把 input 事件翻译成 update:modelValue，
 * 因此不桩就永远无法「在输入框里打字」。
 */const globalStubs = {
  'el-input': {
    template:
      '<textarea class="el-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'rows', 'maxlength', 'resize', 'placeholder']
  },
  // 不绑 disabled：VTU 的 trigger 直接派发事件，绑定 disabled 会让桩与真实按钮
  // 在 happy-dom 下的行为不一致，而本 spec 关心的是点击后的处理逻辑。
  // `$event` 必须透传：父组件的 `@click.stop` 编译成 withModifiers(handler, ['stop'])，
  // 收到 undefined 会在 stopPropagation 上抛错。
  // `emits` 必须声明：否则 onClick 同时被当作 fallthrough attr 挂到根元素上，
  // 一次点击会走「原生监听 + $emit」两条路径，处理器执行两次（toast 计数翻倍）。
  'el-button': {
    emits: ['click'],
    template: '<button class="el-button" @click="$emit(\'click\', $event)"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'text', 'circle', 'icon']
  },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-avatar': { template: '<span class="el-avatar"><slot /></span>', props: ['size', 'src'] },
  'el-scrollbar': { template: '<div class="el-scrollbar"><slot /></div>' },
  'el-tooltip': { template: '<span class="el-tooltip"><slot /></span>', props: ['content', 'placement'] },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /></div>',
    props: ['modelValue', 'title', 'width', 'closeOnClickModal']
  },
  'el-timeline': { template: '<div class="el-timeline"><slot /></div>' },
  'el-timeline-item': { template: '<div class="el-timeline-item"><slot /></div>', props: ['timestamp', 'placement'] },
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-empty': { template: '<div class="el-empty"></div>', props: ['description'] }
}

function mountChat() {
  return mount(Chat, {
    global: { stubs: globalStubs, directives: { loading: () => {} } }
  })
}

/** 在输入框里打字并点击发送。 */
async function typeAndSend(wrapper: ReturnType<typeof mountChat>, text: string) {
  await wrapper.find('textarea.el-input').setValue(text)
  await wrapper.find('button.send-btn').trigger('click')
  await flushPromises()
}

describe('chat 发送失败 — 就地恢复而非 toast', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getSessionsMock.mockResolvedValue({ code: 200, message: 'success', data: [] })
    getRemainingCountMock.mockResolvedValue({ code: 200, message: 'success', data: 10 })
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('1. 发送失败：输入文本被回填、就地提示可见、零 toast', async () => {
    chatMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const wrapper = mountChat()
    await flushPromises()

    await typeAndSend(wrapper, '我最近血压有点高')

    // 1) 文本回填 —— 这是整个契约的核心：气泡消失不能连带吞掉用户写的东西。
    expect((wrapper.find('textarea.el-input').element as HTMLTextAreaElement).value).toBe(
      '我最近血压有点高'
    )
    // 2) 就地说明可见，且是模板字面量（不插值后端文本）。
    const marker = wrapper.find('.send-failed')
    expect(marker.exists()).toBe(true)
    expect(marker.text()).toContain('发送未完成，请重试')
    // 3) 零 toast —— 有人给发送失败补 toast 时这条会红。
    expect(errorToastCount()).toBe(0)

    wrapper.unmount()
  })

  it('2. 重试成功：提示消失，且不再是「发送未完成」的状态', async () => {
    chatMock
      .mockRejectedValueOnce({ response: { status: 500, data: {} } })
      .mockResolvedValueOnce({
        code: 200,
        message: 'success',
        data: { answer: '多休息', remainingCount: 9, sessionId: 'session_1' }
      })

    const wrapper = mountChat()
    await flushPromises()

    await typeAndSend(wrapper, '我最近血压有点高')
    expect(wrapper.find('.send-failed').exists()).toBe(true)

    // 输入框里已是原文，直接再点一次发送。
    await wrapper.find('button.send-btn').trigger('click')
    await flushPromises()

    expect(wrapper.find('.send-failed').exists()).toBe(false)
    expect(errorToastCount()).toBe(0)

    wrapper.unmount()
  })

  it('3. 用户重新编辑输入：提示被撤掉（它不是当前输入的注解）', async () => {
    chatMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const wrapper = mountChat()
    await flushPromises()

    await typeAndSend(wrapper, '我最近血压有点高')
    expect(wrapper.find('.send-failed').exists()).toBe(true)

    await wrapper.find('textarea.el-input').setValue('我最近血压有点高，另外睡眠也不好')
    expect(wrapper.find('.send-failed').exists()).toBe(false)

    wrapper.unmount()
  })
})

describe('chat 会话删除 — confirm 与 api 拆分', () => {
  const SESSION = { sessionId: 'session_1', summary: '旧会话', lastMessageAt: '2026-11-10 10:00:00' }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getSessionsMock.mockResolvedValue({ code: 200, message: 'success', data: [SESSION] })
    getRemainingCountMock.mockResolvedValue({ code: 200, message: 'success', data: 10 })
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('4. 取消确认：零提示，且没有发出删除请求', async () => {
    confirmMock.mockRejectedValueOnce('cancel')

    const wrapper = mountChat()
    await flushPromises()

    await wrapper.find('.session-delete').trigger('click')
    await flushPromises()

    expect(clearHistoryMock).not.toHaveBeenCalled()
    expect(errorToastCount()).toBe(0)
    expect(ElMessageMock.success).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('5. 真实删除失败：恰好一条 toast，会话仍留在列表里', async () => {
    confirmMock.mockResolvedValueOnce('confirm')
    clearHistoryMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const wrapper = mountChat()
    await flushPromises()

    await wrapper.find('.session-delete').trigger('click')
    await flushPromises()

    expect(clearHistoryMock).toHaveBeenCalledWith('session_1')
    // 旧代码用一个裸 catch 把这里吞成「用户取消」，真实失败完全不可见。
    expect(errorToastCount()).toBe(1)
    expect(useAiStore().sessions).toHaveLength(1)

    wrapper.unmount()
  })

  it('6. 删除成功：仍弹那一条 success 确认（UI-SPEC §5.1 允许）', async () => {
    confirmMock.mockResolvedValueOnce('confirm')
    clearHistoryMock.mockResolvedValueOnce({ code: 200, message: 'success', data: undefined })

    const wrapper = mountChat()
    await flushPromises()

    await wrapper.find('.session-delete').trigger('click')
    await flushPromises()

    expect(ElMessageMock.success).toHaveBeenCalledTimes(1)
    expect(errorToastCount()).toBe(0)
    expect(useAiStore().sessions).toHaveLength(0)

    wrapper.unmount()
  })
})

describe('chat 历史记录 — 失败不等于「没有历史」', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getSessionsMock.mockResolvedValue({ code: 200, message: 'success', data: [] })
    getRemainingCountMock.mockResolvedValue({ code: 200, message: 'success', data: 10 })
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('7. 历史加载失败：一条 toast，且不打开空对话框', async () => {
    getHistoryMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const wrapper = mountChat()
    await flushPromises()

    await wrapper.find('.history-btn').trigger('click')
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    // 旧实现先 `historyConversations = []` 再无条件 `historyVisible = true`：
    // 请求失败会被呈现成「暂无历史记录」——把故障伪装成空结果。
    expect(wrapper.find('.history-dialog').exists()).toBe(false)

    wrapper.unmount()
  })

  it('8. 历史加载成功：打开对话框、零 toast', async () => {
    getHistoryMock.mockResolvedValueOnce({
      code: 200,
      message: 'success',
      data: { history: [], remainingCount: 10 }
    })

    const wrapper = mountChat()
    await flushPromises()

    await wrapper.find('.history-btn').trigger('click')
    await flushPromises()

    expect(errorToastCount()).toBe(0)
    expect(wrapper.find('.history-dialog').exists()).toBe(true)

    wrapper.unmount()
  })
})
