import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

// 拦截器内部会调用 authStore.logout —— 用 spy 隔离，同时作为"恰好一次提示"的等价断言。
const { logoutSpy, MOCK_TOKEN } = vi.hoisted(() => ({
  logoutSpy: vi.fn(),
  MOCK_TOKEN: 'fake-token-for-test'
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ token: MOCK_TOKEN, logout: logoutSpy })
}))

vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    error: vi.fn(),
    success: vi.fn(),
    info: vi.fn()
  })
}))

import service from '@/utils/request'
import { ApiError } from '@/utils/appError'
import { resetAuthLatch, isHandling401 } from '@/utils/authSession'
import { ElMessage } from 'element-plus'

interface ResponseInterceptor {
  fulfilled: (response: { data: unknown }) => unknown
  rejected: (error: unknown) => Promise<unknown>
}

/** axios 1.16 的 InterceptorManager.handlers 是稳定的内部结构，直接取已注册的 handler。 */
function getResponseHandler(): ResponseInterceptor {
  const handlers = (service.interceptors.response as unknown as { handlers: ResponseInterceptor[] })
    .handlers
  const handler = handlers[0]
  if (!handler) throw new Error('response interceptor not registered')
  return handler
}

/** 断言 promise 被拒绝，并返回拒绝原因，避免在每个用例里重复 try/catch 样板。 */
async function rejectionOf(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise
  } catch (err) {
    expect(err).toBeInstanceOf(ApiError)
    return err as ApiError
  }
  throw new Error('expected promise to reject, but it resolved')
}

describe('request interceptors', () => {
  beforeEach(() => {
    resetAuthLatch()
    logoutSpy.mockClear()
  })

  afterEach(() => {
    // 拦截器只翻译不展示：任何用例都不应产生 UI 副作用
    expect(vi.mocked(ElMessage.error)).toHaveBeenCalledTimes(0)
  })

  describe('request interceptor', () => {
    it('always sends Accept-Language: zh-CN and injects the bearer token', () => {
      const requestHandlers = (
        service.interceptors.request as unknown as {
          handlers: Array<{ fulfilled: (config: any) => any }>
        }
      ).handlers
      const config = { headers: {} as Record<string, string>, data: undefined }
      const result = requestHandlers[0].fulfilled(config)

      expect(result.headers['Accept-Language']).toBe('zh-CN')
      expect(result.headers.Authorization).toBe(`Bearer ${MOCK_TOKEN}`)
    })
  })

  describe('fulfilled branch (HTTP 200)', () => {
    it('passes a code===200 envelope through unchanged', () => {
      const envelope = { code: 200, message: 'success', data: { ok: 1 } }
      expect(getResponseHandler().fulfilled({ data: envelope })).toBe(envelope)
    })

    it('rejects a code!==200 envelope as a business ApiError with the backend message', async () => {
      const err = await rejectionOf(
        getResponseHandler().fulfilled({
          data: { code: 404, message: '健康档案不存在', errorCode: 'health.profile_not_found' }
        }) as Promise<unknown>
      )

      expect(err.kind).toBe('business')
      expect(err.message).toBe('健康档案不存在')
      expect(err.code).toBe('health.profile_not_found')
    })

    // 反回归锚点：假 response 的 status 固定为 200，
    // 因此信封 code=500 不会被 5xx 分支截胡，仍是 business 并保留后端中文。
    it('keeps an envelope code=500 as business rather than server', async () => {
      const err = await rejectionOf(
        getResponseHandler().fulfilled({
          data: { code: 500, message: '该指标已存在阈值，请直接编辑', errorCode: 'resource.exists' }
        }) as Promise<unknown>
      )

      expect(err.kind).toBe('business')
      expect(err.kind).not.toBe('server')
      expect(err.message).toBe('该指标已存在阈值，请直接编辑')
      expect(err.code).toBe('resource.exists')
    })
  })

  describe('rejected branch', () => {
    it('still rejects a silent request (silent suppresses display only, never resolves)', async () => {
      const err = await rejectionOf(
        getResponseHandler().rejected({
          config: { silent: true },
          response: { status: 500, data: {} }
        })
      )

      expect(err.kind).toBe('server')
      expect(err.retryable).toBe(true)
    })

    it('classifies a cancel sentinel without touching the auth latch', async () => {
      const err = await rejectionOf(getResponseHandler().rejected('cancel'))

      expect(err.kind).toBe('cancel')
      expect(err.message).toBe('')
      expect(logoutSpy).not.toHaveBeenCalled()
      expect(isHandling401()).toBe(false)
    })

    it('triggers logout exactly once for concurrent 401s and rejects every one', async () => {
      const first = await rejectionOf(
        getResponseHandler().rejected({ response: { status: 401, data: {} } })
      )
      const second = await rejectionOf(
        getResponseHandler().rejected({ response: { status: 401, data: {} } })
      )

      expect(first.kind).toBe('auth')
      expect(second.kind).toBe('auth')
      expect(logoutSpy).toHaveBeenCalledTimes(1)
      expect(isHandling401()).toBe(true)
    })

    it('triggers a second logout after the latch is reset (next session still notified)', async () => {
      await rejectionOf(getResponseHandler().rejected({ response: { status: 401, data: {} } }))
      expect(logoutSpy).toHaveBeenCalledTimes(1)

      resetAuthLatch()

      await rejectionOf(getResponseHandler().rejected({ response: { status: 401, data: {} } }))
      expect(logoutSpy).toHaveBeenCalledTimes(2)
    })
  })
})
