import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// 这是复位契约的端到端证明：不是单测 resetAuthLatch 本身，
// 而是走 authStore.login() 的真实路径，断言闩锁被业务代码复位。
const { loginMock, pushMock, LOGIN_TOKEN } = vi.hoisted(() => ({
  loginMock: vi.fn(),
  pushMock: vi.fn(),
  LOGIN_TOKEN: 'fresh-token'
}))

vi.mock('@/api/auth', () => ({
  authApi: {
    login: loginMock,
    register: vi.fn()
  }
}))

vi.mock('@/api/user', () => ({
  userApi: {
    getProfile: vi.fn()
  }
}))

vi.mock('@/router', () => ({
  default: { push: pushMock }
}))

vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    error: vi.fn(),
    success: vi.fn(),
    info: vi.fn()
  })
}))

import { useAuthStore } from '@/stores/auth'
import { isHandling401, markHandling401, resetAuthLatch } from '@/utils/authSession'

describe('useAuthStore 401 latch reset', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    resetAuthLatch()
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('resets the latch after a successful login so the next session is still notified', async () => {
    markHandling401()
    expect(isHandling401()).toBe(true)

    loginMock.mockResolvedValue({ data: { token: LOGIN_TOKEN, user: { id: 1 } } })

    const store = useAuthStore()
    await store.login({ username: 'user', password: 'pass' })

    expect(store.token).toBe(LOGIN_TOKEN)
    expect(isHandling401()).toBe(false)
    expect(pushMock).toHaveBeenCalledWith('/dashboard')
  })

  it('keeps the latch set when login fails, because no valid session was established', async () => {
    markHandling401()
    expect(isHandling401()).toBe(true)

    loginMock.mockRejectedValue(new Error('bad credentials'))

    const store = useAuthStore()
    await expect(store.login({ username: 'user', password: 'wrong' })).rejects.toThrow(
      'bad credentials'
    )

    expect(isHandling401()).toBe(true)
    expect(pushMock).not.toHaveBeenCalled()
  })
})
