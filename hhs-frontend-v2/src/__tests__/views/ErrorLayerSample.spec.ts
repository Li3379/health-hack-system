import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import type { Mock } from 'vitest'

/**
 * 错误层语义样本 1/2：push store 的单一 toast 出口与布尔返回值契约（T-11-21）。
 *
 * element-plus 用 importOriginal 部分 mock：保住组件导出，只替换 ElMessage，
 * 对每次 toast 调用（含对象式）计数。
 */

vi.mock('@/api/push', () => ({
  getPushConfigs: vi.fn(),
  updatePushConfig: vi.fn(),
  testPushChannel: vi.fn(),
  getPushHistory: vi.fn(),
  getPushStats: vi.fn()
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
    })
  }
})

import { usePushStore } from '@/stores/push'
import { updatePushConfig } from '@/api/push'
import { ElMessage } from 'element-plus'

const updatePushConfigMock = updatePushConfig as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

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
    ([options]) => (options as { type?: string })?.type === 'error'
  ).length
  return objectCalls + ElMessageMock.error.mock.calls.length
}

describe('ErrorLayerSample — push store 单一出口与返回值契约', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('1. 保存失败：返回 false、恰好一条错误 toast、零成功提示', async () => {
    // 500 → kind='server' → VNode 常驻 toast（可在 message 里断言）。
    updatePushConfigMock.mockRejectedValueOnce({
      response: { status: 500, data: {} }
    })

    const store = usePushStore()
    const ok = await store.saveConfig('EMAIL', { configValue: 'a@b.com' })

    expect(ok).toBe(false)
    expect(errorToastCount()).toBe(1)
    expect(ElMessageMock.success).toHaveBeenCalledTimes(0)
    // 函数式旧 API 全面退场
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
  })

  it('2. 保存成功：返回 true、一条成功提示、零错误 toast', async () => {
    updatePushConfigMock.mockResolvedValueOnce({
      code: 200,
      message: 'success',
      data: {
        id: 1,
        channelType: 'EMAIL',
        channelLabel: '邮件',
        configKey: 'address',
        configValue: 'a@b.com',
        enabled: true,
        offlineSupported: true,
        isConfigured: true,
        createdAt: '',
        updatedAt: ''
      }
    })

    const store = usePushStore()
    const ok = await store.saveConfig('EMAIL', { configValue: 'a@b.com' })

    expect(ok).toBe(true)
    expect(ElMessageMock.success).toHaveBeenCalledTimes(1)
    expect(errorToastCount()).toBe(0)
  })

  it('3. 用户取消：返回 false 且任何 toast 都不出现（D-12）', async () => {
    updatePushConfigMock.mockRejectedValueOnce('cancel')

    const store = usePushStore()
    const ok = await store.saveConfig('EMAIL', { configValue: 'a@b.com' })

    expect(ok).toBe(false)
    expect(toastCount()).toBe(0)
  })

  it('4. 连续两次网络失败：VNode 闩锁把重复提示压到 1 条（<= 1）', async () => {
    // 网络错误走 showError 自身的 2000ms VNode 闩锁（EP grouping 不支持 VNode）。
    updatePushConfigMock.mockRejectedValue({
      request: {},
      message: 'Network Error'
    })

    const store = usePushStore()
    await store.saveConfig('EMAIL', { configValue: 'a@b.com' })
    await store.saveConfig('EMAIL', { configValue: 'a@b.com' })

    expect(errorToastCount()).toBeLessThanOrEqual(1)
  })

  it('5. push store 源码内不再有 ElMessage.error（单一出口）', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/stores/push.ts'), 'utf8')
    const codeLines = source
      .split('\n')
      .filter(line => !line.trimStart().startsWith('//') && !line.trimStart().startsWith('*'))
    expect(codeLines.join('\n')).not.toContain('ElMessage.error')
  })
})