import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

// ElMessage 是全站唯一 toast 出口所依赖的 EP 原语；用对象式调用，因此 mock 返回 handler。
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(() => ({ close: vi.fn() })), {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  })
}))

import type { MessageHandler } from 'element-plus'

let showError: typeof import('@/utils/showError').showError
let ERROR_CODE_ACTIONS: typeof import('@/utils/showError').ERROR_CODE_ACTIONS
let ApiError: typeof import('@/utils/appError').ApiError
let ElMessage: ReturnType<typeof vi.fn> & Record<string, ReturnType<typeof vi.fn>>

/**
 * 每个用例重置模块注册表，避免 showError 的模块级闩锁 / 可见列表在用例间串味。
 * 同时拿到与 showError 同一份 mock 的 ElMessage 实例。
 */
beforeEach(async () => {
  // resetModules 让 showError 的模块级闩锁 / 可见列表回到初始状态；
  // element-plus 的 mock 工厂只会求值一次，因此调用历史需要手动清空。
  vi.resetModules()
  const elementPlus = await import('element-plus')
  ElMessage = elementPlus.ElMessage as unknown as typeof ElMessage
  ElMessage.mockClear()
  ElMessage.error.mockClear()

  const appErrorModule = await import('@/utils/appError')
  ApiError = appErrorModule.ApiError

  const showErrorModule = await import('@/utils/showError')
  showError = showErrorModule.showError
  ERROR_CODE_ACTIONS = showErrorModule.ERROR_CODE_ACTIONS
})

afterEach(() => {
  vi.useRealTimers()
  // 统一走对象式调用：函数式旧 API 在全程必须零调用，便于后续 grep 收敛。
  expect(ElMessage.error).toHaveBeenCalledTimes(0)
})

interface VNodeLike {
  type?: unknown
  props?: Record<string, unknown>
  children?: unknown
}

/** 深度遍历 VNode 子节点，找到第一个 button。 */
function findButton(node: string | VNodeLike | undefined): VNodeLike | undefined {
  if (!node || typeof node === 'string' || !Array.isArray(node.children)) return undefined
  for (const child of node.children as Array<VNodeLike | string>) {
    if (!child || typeof child !== 'object') continue
    if (child.type === 'button') return child
    const nested = findButton(child)
    if (nested) return nested
  }
  return undefined
}

interface ToastOptions {
  message: string | VNodeLike
  grouping: boolean
  duration: number
  showClose: boolean
  type: string
}

function firstCallOptions(): ToastOptions {
  return ElMessage.mock.calls[0][0]
}

describe('showError — zero-toast channels', () => {
  it('returns a validation ApiError without presenting anything', () => {
    const err = showError({ response: { status: 400, data: { fieldErrors: [{ field: 'value', message: '不能为空' }] } } }, 'profile.saveFailed')

    expect(err.kind).toBe('validation')
    expect(ElMessage).toHaveBeenCalledTimes(0)
  })

  // 锁死 11-03 的幂等直通：拦截器抛出的就是 ApiError，若直通缺失会被判成 network 并弹提示。
  it('passes an interceptor-shaped validation ApiError straight through, preserving fieldErrors', () => {
    const original = new ApiError('参数校验失败', 'validation', 400, 'validation.invalid_parameter', {
      metricKey: '指标类型不能为空'
    })
    const err = showError(original, 'profile.saveFailed')

    expect(ElMessage).toHaveBeenCalledTimes(0)
    expect(err.kind).toBe('validation')
    expect(err.fieldErrors.metricKey).toBe('指标类型不能为空')
  })

  it('passes a cancel ApiError through with zero toasts (D-12)', () => {
    const err = showError(new ApiError('', 'cancel'), 'threshold.saveFailed')

    expect(ElMessage).toHaveBeenCalledTimes(0)
    expect(err.kind).toBe('cancel')
  })

  it('ignores the "cancel" sentinel string', () => {
    const err = showError('cancel', 'threshold.saveFailed')

    expect(ElMessage).toHaveBeenCalledTimes(0)
    expect(err.kind).toBe('cancel')
  })

  it('presents nothing when silent is set but still returns a classified ApiError', () => {
    const err = showError({ response: { status: 500, data: {} } }, 'server.unavailable', { silent: true })

    expect(ElMessage).toHaveBeenCalledTimes(0)
    expect(err).toBeInstanceOf(ApiError)
  })

  it('does not present a second message for auth (401 is owned by authStore.logout)', () => {
    const err = showError(new ApiError('登录已过期，请重新登录', 'auth', 401), 'auth.expired')

    expect(ElMessage).toHaveBeenCalledTimes(0)
    expect(err.kind).toBe('auth')
  })
})

describe('showError — business string toast', () => {
  it('emits exactly one grouped, timed toast for a plain business failure', () => {
    const err = showError({ response: { status: 200, data: { code: 500, message: '该指标已存在阈值，请直接编辑' } } }, 'threshold.duplicate')

    expect(err.kind).toBe('business')
    expect(ElMessage).toHaveBeenCalledTimes(1)
    const opts = firstCallOptions()
    expect(opts.grouping).toBe(true)
    expect(opts.duration).toBe(4000)
    expect(opts.showClose).toBe(true)
    expect(typeof opts.message).toBe('string')
    expect(opts.message).toBe('该指标已存在阈值，请直接编辑')
  })

  it('falls back to the scenario dictionary when the backend message is empty', () => {
    showError(new ApiError('', 'business', 200), 'threshold.saveFailed')

    expect(firstCallOptions().message).toBe('无法保存阈值，请检查高低值顺序后重试')
  })

  it('never truncates a long backend message (long-text backstop)', () => {
    const longMessage = '异'.repeat(60)
    showError(new ApiError(longMessage, 'business', 200), 'metric.saveFailed')

    const shown = firstCallOptions().message as string
    expect(shown).toBe(longMessage)
    expect(shown.length).toBe(60)
  })

  it('keeps every dictionary fallback at or below 30 characters', async () => {
    const { ERROR_MESSAGES, resolveMessage } = await import('@/utils/errorMessages')
    for (const key of Object.keys(ERROR_MESSAGES)) {
      expect(resolveMessage(key, '').length).toBeLessThanOrEqual(30)
    }
  })
})

describe('showError — VNode sticky toast', () => {
  it('emits a sticky VNode toast for a real 5xx with grouping disabled', () => {
    showError({ response: { status: 500, data: {} } }, 'server.unavailable')

    expect(ElMessage).toHaveBeenCalledTimes(1)
    const opts = firstCallOptions()
    expect(opts.grouping).toBe(false)
    expect(opts.duration).toBe(0)
    expect(opts.showClose).toBe(true)
    expect(typeof opts.message).toBe('object')
  })

  it('renders a sticky text-only VNode for network failures without a retry callback', () => {
    // 只有原生 Axios 错误形态（isAxiosError=true）才是 network（WR-17）。
    showError({ isAxiosError: true, message: 'Network Error' }, 'network.offline')

    const opts = firstCallOptions()
    expect(opts.grouping).toBe(false)
    expect(opts.duration).toBe(0)
    expect(findButton(opts.message)).toBeUndefined()
  })

  it('renders an accessible retry button when a retry callback is supplied', () => {
    showError({ response: { status: 500, data: {} } }, 'server.unavailable', { retry: () => undefined })

    const opts = firstCallOptions()
    const button = findButton(opts.message)
    expect(button).toBeDefined()
    expect(button?.children).toBe('重试')
    expect(button?.props?.['aria-label']).toBe('重试：服务暂时不可用，请稍后重试')
  })

  it('closes the toast and invokes the retry callback exactly once on click (D-03)', async () => {
    const retry = vi.fn()
    showError({ response: { status: 500, data: {} } }, 'server.unavailable', { retry })

    const handler = ElMessage.mock.results[0].value as MessageHandler
    const button = findButton(firstCallOptions().message)
    const onClick = button?.props?.onClick as (() => void) | undefined
    onClick?.()

    await Promise.resolve()
    expect(handler.close).toHaveBeenCalledTimes(1)
    expect(retry).toHaveBeenCalledTimes(1)
  })

  it('auto-renders 重试 from ApiError.retryRequest when the caller supplies no retry (D-03)', () => {
    const retryRequest = vi.fn().mockResolvedValue(undefined)
    const err = new ApiError(
      '网络连接异常，请检查网络后重试',
      'network',
      undefined,
      undefined,
      {},
      undefined,
      true
    )
    err.retryRequest = retryRequest

    showError(err, 'network.offline')

    const button = findButton(firstCallOptions().message)
    expect(button?.children).toBe('重试')
  })

  it('re-presents a failed auto-retry instead of swallowing it', async () => {
    const err = new ApiError('网络连接异常，请检查网络后重试', 'network', undefined, undefined, {}, undefined, true)
    err.retryRequest = vi.fn().mockRejectedValue(
      new ApiError('服务暂时不可用，请稍后重试', 'server', 503, undefined, {}, undefined, true)
    )

    showError(err, 'network.offline')

    const button = findButton(firstCallOptions().message)
    ;(button?.props?.onClick as (() => void) | undefined)?.()
    await Promise.resolve()
    await Promise.resolve()

    // 第一次 toast + 重试失败后的第二条 toast
    expect(ElMessage).toHaveBeenCalledTimes(2)
  })

  it('never renders 重试 for a non-retryable business failure even when a callback is supplied', () => {
    showError(
      new ApiError('该指标已存在阈值，请直接编辑', 'business', 200, 'resource.exists'),
      'threshold.duplicate',
      { retry: () => undefined }
    )

    const opts = firstCallOptions()
    expect(typeof opts.message).toBe('string')
    expect(findButton(opts.message)).toBeUndefined()
  })

  it('auto-attaches the 打开编辑 label from ERROR_CODE_ACTIONS when only onClick is given (D-08)', () => {
    showError(
      new ApiError('该指标已存在阈值，请直接编辑', 'business', 200, 'resource.exists'),
      'threshold.duplicate',
      { action: { onClick: vi.fn() } }
    )

    expect(ElMessage).toHaveBeenCalledTimes(1)
    const button = findButton(firstCallOptions().message)
    expect(button?.children).toBe(ERROR_CODE_ACTIONS['resource.exists'])
    expect(button?.children).toBe('打开编辑')
    expect(button?.props?.class).toBe('error-toast__action')
  })

  it('lets a caller-supplied label override the auto-attached one', () => {
    showError(
      new ApiError('该指标已存在阈值，请直接编辑', 'business', 200, 'resource.exists'),
      'threshold.duplicate',
      { action: { label: '去编辑', onClick: vi.fn() } }
    )

    expect(findButton(firstCallOptions().message)?.children).toBe('去编辑')
  })

  it('degrades to a plain string toast when resource.exists has no action at all', () => {
    showError(
      new ApiError('该指标已存在阈值，请直接编辑', 'business', 200, 'resource.exists'),
      'threshold.duplicate'
    )

    const opts = firstCallOptions()
    expect(typeof opts.message).toBe('string')
    expect(opts.grouping).toBe(true)
  })
})

describe('showError — duplicate suppression and visible cap', () => {
  it('suppresses an identical VNode toast within the 2000ms window', () => {
    showError({ response: { status: 500, data: {} } }, 'server.unavailable')
    showError({ response: { status: 500, data: {} } }, 'server.unavailable')

    expect(ElMessage).toHaveBeenCalledTimes(1)
  })

  it('emits again once the 2000ms latch expires', () => {
    vi.useFakeTimers()
    showError({ response: { status: 500, data: {} } }, 'server.unavailable')
    expect(ElMessage).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(2100)
    showError({ response: { status: 500, data: {} } }, 'server.unavailable')

    expect(ElMessage).toHaveBeenCalledTimes(2)
  })

  it('closes the oldest string toast when a 4th distinct business toast appears', () => {
    for (let i = 1; i <= 4; i += 1) {
      showError(new ApiError(`业务失败文案 ${i}`, 'business', 200), 'metric.saveFailed')
    }

    expect(ElMessage).toHaveBeenCalledTimes(4)
    const handlers = ElMessage.mock.results.map(r => r.value as MessageHandler)
    expect(handlers[0].close).toHaveBeenCalledTimes(1)
    expect(handlers[1].close).toHaveBeenCalledTimes(0)
    expect(handlers[2].close).toHaveBeenCalledTimes(0)
  })

  it('applies the same cap to VNode toasts', () => {
    for (let i = 1; i <= 4; i += 1) {
      showError(
        new ApiError(`动作失败文案 ${i}`, 'business', 200),
        'threshold.duplicate',
        { action: { label: '去编辑', onClick: vi.fn() } }
      )
    }

    expect(ElMessage).toHaveBeenCalledTimes(4)
    const handlers = ElMessage.mock.results.map(r => r.value as MessageHandler)
    expect(handlers[0].close).toHaveBeenCalledTimes(1)
    expect(handlers[3].close).toHaveBeenCalledTimes(0)
  })
})