import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import type { Mock } from 'vitest'

/**
 * D-02 的结构性钉子：Pinia store 不是 UI 层。
 *
 * <p>store 内的错误只允许两种去向 —— **上抛**（void 返回的加载器）或**返回 falsy**
 * （调用方按布尔分支的动作）。绝不自己弹错误 toast：呈现权归调用点，否则同一个
 * 失败会在 store 和页面各呈现一次，且 store 的失败对调用方变成不可观测。
 *
 * <p>注意本文件的断言是**逐文件**的，不写成「stores 目录里恰好 N 条 ElMessage」：
 * 各文件的合法存活数不同（ai 1 条 success、push 1 条 success、floatingAi 0 条），
 * 而 auth.ts 的 4 条是认证流程提示、不在本阶段范围内，必须单独断言，
 * 否则一次「顺手清理 auth」会被误认为 store 检查通过。
 *
 * <p>源码级断言沿用 `ErrorLayerSample.spec.ts:214-219` 的做法：先剔掉注释行再匹配，
 * 这样解释「为什么不再用 ElMessage.error」的注释不会把检查弄红。
 */

// element-plus 用 importOriginal 部分 mock：保住组件导出，只替换 ElMessage，
// 这样 store 的源码级 import 检查与运行时 toast 计数可以同时成立。
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
    })
  }
})

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

import { useAiStore } from '@/stores/ai'
import { useFloatingAiStore } from '@/stores/floatingAi'
import { aiApi } from '@/api/ai'
import { ElMessage } from 'element-plus'

const getSessionsMock = aiApi.getSessions as unknown as Mock
const getHistoryMock = aiApi.getHistory as unknown as Mock
const clearHistoryMock = aiApi.clearHistory as unknown as Mock
const chatMock = aiApi.chat as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/** 全部 toast 调用：对象式 + 函数式旧 API。 */
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
    ([options]) => (options as { type?: string })?.type === 'error'
  ).length
  return objectCalls + ElMessageMock.error.mock.calls.length
}

/** 剔掉整行注释与块注释续行，只留可执行代码 —— 同 ErrorLayerSample.spec.ts:216-219。 */
function codeOnly(relativePath: string): string {
  const source = readFileSync(resolve(process.cwd(), relativePath), 'utf8')
  return source
    .split('\n')
    .filter(line => {
      const trimmed = line.trimStart()
      return !trimmed.startsWith('//') && !trimmed.startsWith('*') && !trimmed.startsWith('/*')
    })
    .join('\n')
}

/** element-plus 的具名导入里是否含 ElMessage（用于判断 store 是否还持有 message API）。 */
function importsElMessage(relativePath: string): boolean {
  const code = codeOnly(relativePath)
  const importLines = code
    .split('\n')
    .filter(line => line.includes("from 'element-plus'") || line.includes('from "element-plus"'))
  return importLines.some(line => /\bElMessage\b/.test(line))
}

describe('D-02 — ai store 不再呈现错误', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('1. 源码里没有 ElMessage.error，且 element-plus 的导入不再取 ElMessage', () => {
    const code = codeOnly('src/stores/ai.ts')
    expect(code).not.toContain('ElMessage.error')
    // 唯一允许的 ElMessage 使用是 deleteSession 的成功确认（UI-SPEC §5.1）。
    expect(code).toContain("ElMessage.success('会话已删除')")
    expect(code.match(/ElMessage\./g)?.length).toBe(1)
  })

  it('2. 会话列表加载失败：loadSessions 上抛而不是静默吞掉', async () => {
    getSessionsMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const store = useAiStore()
    await expect(store.loadSessions()).rejects.toBeTruthy()
    // 静默的旧实现会让这里 resolve，并把「加载失败」呈现成「你还没有会话」。
    expect(store.sessions).toEqual([])
    expect(store.sessionsLoading).toBe(false)
    expect(toastCount()).toBe(0)
  })

  it('3. 历史加载失败：getHistory 上抛而不是返回空数组', async () => {
    getHistoryMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const store = useAiStore()
    await expect(store.getHistory('session_1')).rejects.toBeTruthy()
    // 旧实现返回 [] —— 「请求失败」与「没有历史」因此不可区分。
    expect(toastCount()).toBe(0)
  })

  it('4. 发送失败：sendMessage 解析为 false、不抛、并回滚乐观气泡', async () => {
    chatMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const store = useAiStore()
    await store.createNewSession()
    // 不应抛：调用方在事件处理器里读布尔值，抛错会变成未处理的拒绝。
    await expect(store.sendMessage('我头疼')).resolves.toBe(false)
    expect(store.messages).toHaveLength(0)
    // 零 toast：失败的就地面由调用点承担。
    expect(errorToastCount()).toBe(0)
  })

  it('5. 删除成功：仍解析为 true（成功契约与新抛错契约并存）', async () => {
    clearHistoryMock.mockResolvedValueOnce({ code: 200, message: 'success', data: undefined })

    const store = useAiStore()
    await expect(store.deleteSession('session_1')).resolves.toBe(true)
    // 成功提示保留：它是动作确认，不在错误 toast 的禁令内。
    expect(ElMessageMock.success).toHaveBeenCalledTimes(1)
    expect(errorToastCount()).toBe(0)
  })

  it('6. 删除失败：deleteSession 上抛，且不把会话从列表里移除', async () => {
    clearHistoryMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const store = useAiStore()
    store.sessions = [
      { sessionId: 'session_1', summary: '会话', lastMessageAt: '2026-11-10 10:00:00' } as never
    ]

    await expect(store.deleteSession('session_1')).rejects.toBeTruthy()
    // 删除失败却把行移走，会让用户看到「列表变了」而服务端没变 —— 不一致。
    expect(store.sessions).toHaveLength(1)
    expect(errorToastCount()).toBe(0)
  })
})

describe('D-02 — floatingAi store 不再呈现错误', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('7. 源码里没有 ElMessage.error，也不再从 element-plus 导入 ElMessage', () => {
    const code = codeOnly('src/stores/floatingAi.ts')
    expect(code).not.toContain('ElMessage.error')
    expect(code.match(/ElMessage/g)).toBeNull()
    expect(importsElMessage('src/stores/floatingAi.ts')).toBe(false)
  })

  it('8. 发送失败：解析为 false、不抛、回滚乐观气泡、零 toast', async () => {
    chatMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const store = useFloatingAiStore()
    await expect(store.sendMessage('我头疼')).resolves.toBe(false)
    expect(store.messages).toHaveLength(0)
    expect(errorToastCount()).toBe(0)
  })
})

describe('D-02 — 参照形态：push store 保持「分类而不呈现」', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('9. push store 的 ElMessage 只有那一条推送测试成功提示', () => {
    const code = codeOnly('src/stores/push.ts')
    expect(code).not.toContain('ElMessage.error')
    expect(code.match(/ElMessage\./g)?.length).toBe(1)
    expect(code).toContain('推送测试成功')
  })
})

describe('D-02 — 允许存活的 success 提示与 auth 的既有提示（逐文件断言）', () => {
  it('10. ai 1 条 success、push 1 条 success、floatingAi 0 条 —— 三者各自断言，不求和', () => {
    const aiSuccess = codeOnly('src/stores/ai.ts').match(/ElMessage\.success/g)?.length ?? 0
    const pushSuccess = codeOnly('src/stores/push.ts').match(/ElMessage\.success/g)?.length ?? 0
    const floatingAny = codeOnly('src/stores/floatingAi.ts').match(/ElMessage/g)?.length ?? 0

    expect(aiSuccess).toBe(1)
    expect(pushSuccess).toBe(1)
    expect(floatingAny).toBe(0)
  })

  it('11. auth.ts 的 4 条认证流程提示单独断言：它们是本阶段范围之外、未被触碰的', () => {
    const code = codeOnly('src/stores/auth.ts')
    // 2 success（登录成功 / 注册成功）+ 1 warning（登录已过期）+ 1 info（已退出登录）。
    // 与上面三个文件的计数分开断言：把 auth 的 4 条并进「恰好 3」的求和里必然失败，
    // 也会掩盖「auth 被顺手清理」这种越界改动。
    expect(code.match(/ElMessage\./g)?.length).toBe(4)
    expect(code).toContain("ElMessage.success('登录成功')")
    expect(code).toContain("ElMessage.success('注册成功，请登录')")
    expect(code).toContain('ElMessage.warning(')
    expect(code).toContain("ElMessage.info('已退出登录')")
  })
})

afterEach(() => {
  vi.clearAllMocks()
})
