import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import type { Mock } from 'vitest'

/**
 * Phase 14 / plan 14-10 的端到端锚点（`data-input/components/DeviceSync.vue`）。
 *
 * 三条不变量，缺一条这个 plan 就没有证据：
 *
 * 1. **删除是保行为的。** `@/utils/request` 的响应拦截器在信封 code !== 200 时
 *    直接 reject（`request.ts:45-52`），所以 `if (res.code === 200) { … } else { … }`
 *    里的 `else` 永远执行不到。这里用源码断言把「那个形状不再存在」钉住，
 *    再用一个真实的失败用例证明「可达路径仍恰好一条提示」。
 * 2. **面板与 toast 并存。** 同步失败既是内容区状态（`lastSyncError`，带按设备重试）
 *    也是本次动作的失败（一条 toast）。一次失败两条呈现是 Contract 3 §3.4 明确允许的
 *    例外组合，因为它服务两种不同的生命周期 —— 所以两条断言写在同一个用例里。
 * 3. **页面自己的断网判断已删除。** 把 `isOnline` 置为 false 后再触发一次无 response 的
 *    AxiosError，提示条数必须仍然是 1。若旧的 `if (!isOnline.value || …)` 分支还在，
 *    这条会变成 2 —— 这正是「删掉了而不是重排了」的证据。
 */

vi.mock('@/api/device', () => ({
  deviceApi: {
    getConnections: vi.fn(),
    getConfig: vi.fn(),
    getPlatforms: vi.fn(),
    getPlatformMetadata: vi.fn(),
    connect: vi.fn(),
    disconnect: vi.fn(),
    sync: vi.fn(),
    syncAll: vi.fn(),
    getSyncHistory: vi.fn()
  }
}))

vi.mock('@/composables/useGsap', () => ({
  gsap: {
    timeline: () => {
      const timeline: Record<string, unknown> = {}
      timeline.fromTo = () => timeline
      return timeline
    }
  },
  staggerReveal: vi.fn(),
  cleanupScrollTriggers: vi.fn(),
  DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
  EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
}))

vi.mock('vue-router', async importOriginal => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: vi.fn(() => ({ query: {}, params: {}, path: '/data-input' })),
    useRouter: vi.fn(() => ({ push: vi.fn(), replace: vi.fn() }))
  }
})

// 与 ErrorLayerSample.spec.ts:53-68 同形：保住组件导出，只替换 ElMessage / ElMessageBox。
// showError 也 import 同一个 ElMessage，因此计数覆盖「单一出口」的所有调用。
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

import DeviceSync from '@/views/data-input/components/DeviceSync.vue'
import { deviceApi } from '@/api/device'
import { ElMessage, ElMessageBox } from 'element-plus'

const getConnectionsMock = deviceApi.getConnections as unknown as Mock
const getPlatformMetadataMock = deviceApi.getPlatformMetadata as unknown as Mock
const connectMock = deviceApi.connect as unknown as Mock
const syncMock = deviceApi.sync as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy
const ElMessageBoxMock = ElMessageBox as unknown as { confirm: Mock }

/** showError 的 VNode 闩锁是模块级、跨用例存活的。让 Date.now 单调前进 60s。 */
let nowCursor = 1_700_000_000_000
vi.spyOn(Date, 'now').mockImplementation(() => (nowCursor += 60_000))

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

function lastErrorToastText(): string {
  const calls = ElMessageMock.mock.calls.filter(
    ([options]) => (options as { type?: string } | undefined)?.type === 'error'
  )
  expect(calls.length).toBeGreaterThan(0)
  const message = (calls[calls.length - 1][0] as { message: unknown }).message
  if (typeof message === 'string') return message
  const textNode = findNodeByClass(message, 'error-toast__text')
  return String(textNode?.children ?? '')
}

const stubs = {
  'el-card': { template: '<div class="el-card"><slot name="header" /><slot /></div>' },
  'el-alert': {
    template: '<div class="el-alert">{{ title }}{{ description }}<slot /></div>',
    props: ['title', 'description', 'type', 'showIcon', 'closable']
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size', 'effect'] },
  'el-avatar': { template: '<span class="el-avatar"><slot /></span>', props: ['size', 'style'] },
  'el-button': {
    template: '<button class="el-button" type="button" :disabled="disabled"><slot /></button>',
    props: ['type', 'size', 'text', 'plain', 'loading', 'disabled']
  },
  'el-tooltip': { template: '<span class="el-tooltip"><slot /></span>', props: ['content', 'placement', 'effect'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose']
  },
  'el-table': { template: '<div class="el-table"><slot /></div>', props: ['data', 'stripe', 'emptyText'] },
  'el-table-column': { template: '<div class="el-table-column" />', props: ['prop', 'label', 'width', 'align', 'minWidth', 'fixed'] },
  'el-pagination': { template: '<div class="el-pagination" />', props: ['currentPage', 'pageSize', 'total', 'layout'] },
  'el-collapse': { template: '<div class="el-collapse"><slot /></div>', props: ['modelValue', 'accordion'] },
  'el-collapse-item': { template: '<div class="el-collapse-item"><slot /></div>', props: ['name', 'title'] },
  Connection: { template: '<span class="icon-connection" />' },
  QuestionFilled: { template: '<span class="icon-question" />' },
  PlatformStatusTag: { template: '<span class="platform-status-tag" />' },
  DeviceConfigWizard: { template: '<div class="device-config-wizard" />' }
}

type DeviceSyncVm = {
  isOnline: boolean
  lastSyncError: { platform: string; title: string; message: string } | null
  handleConnect: (platform: string) => Promise<void>
  handleDisconnect: (platform: string) => Promise<void>
  handleSync: (platform: string) => Promise<void>
}

function mountPage() {
  return mount(DeviceSync, {
    global: { stubs, directives: { loading: () => {} } }
  })
}

function ok<T>(data: T) {
  return { code: 200, message: 'success', data }
}

describe('data-input/DeviceSync.vue — 删除三个不可达 else 后行为不变（ERR-05/ERR-07）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getConnectionsMock.mockResolvedValue(
      ok([
        {
          platform: 'huawei',
          platformName: '华为运动健康',
          status: 'connected',
          statusName: '已连接'
        }
      ])
    )
    getPlatformMetadataMock.mockResolvedValue(ok([]))
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('同步失败：恰好一条提示，且按设备面板同时被填充（面板与 toast 并存）', async () => {
    syncMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const wrapper = mountPage()
    await flushPromises()

    const vm = wrapper.vm as unknown as DeviceSyncVm
    await vm.handleSync('huawei')
    await flushPromises()

    // 一次失败 → 一条提示。旧代码在 catch 里分支成两条 ElMessage.error 之一，
    // 迁移后只有一个出口；若有人把 offline 分支加回来，这里会变成 2。
    expect(errorToastCount()).toBe(1)

    // 面板仍在：内容是「这台设备上次同步结果」，带按设备重试，生命周期比 toast 长。
    expect(vm.lastSyncError).not.toBeNull()
    expect(vm.lastSyncError?.platform).toBe('huawei')
    expect(vm.lastSyncError?.title).toBe('华为运动健康 同步失败')
    expect(vm.lastSyncError?.message).toBe('服务暂时不可用，请稍后重试')

    wrapper.unmount()
  })

  it('连接受阻且无 response：即使页面 isOnline 为 false，仍只有一条提示（旧的页面级分支已删除）', async () => {
    // 形状是真实 Axios 断网错误：无 response、isAxiosError=true → normalizeApiError 归为 network。
    connectMock.mockRejectedValueOnce({
      isAxiosError: true,
      message: 'Network Error',
      code: 'ERR_NETWORK'
    })

    const wrapper = mountPage()
    await flushPromises()

    const vm = wrapper.vm as unknown as DeviceSyncVm
    // 先把页面自己的在线标记置为 false，再调用 —— 这正是「证明分支被删掉」的前提。
    vm.isOnline = false
    expect(vm.isOnline).toBe(false)

    await vm.handleConnect('huawei')
    await flushPromises()

    // 旧实现：isOnline===false 会先 ElMessage.error(offlineMessage) 再走另一条分支。
    // 现在只有一个出口。这里断言**总条数**而不只是错误条数 —— 页面级分支即使改用
    // warning 而非 error 重新引入，总条数也会变成 2，所以总条数是更严的断言。
    expect(toastCount()).toBe(1)
    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('网络连接异常，请检查网络后重试')

    wrapper.unmount()
  })

  it('断开连接的确认框被取消：零提示，且没有发出请求', async () => {
    ElMessageBoxMock.confirm.mockRejectedValueOnce('cancel')

    const wrapper = mountPage()
    await flushPromises()

    const vm = wrapper.vm as unknown as DeviceSyncVm
    const disconnectSpy = deviceApi.disconnect as unknown as Mock
    await vm.handleDisconnect('huawei')
    await flushPromises()

    expect(toastCount()).toBe(0)
    expect(disconnectSpy).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('断开连接的真实失败不再被当成「用户取消」：恰好一条提示', async () => {
    ElMessageBoxMock.confirm.mockResolvedValueOnce('confirm')
    const disconnectSpy = deviceApi.disconnect as unknown as Mock
    disconnectSpy.mockRejectedValueOnce({
      response: { status: 200, data: { code: 500, errorCode: 'auth.forbidden', message: '无权操作该设备' } }
    })

    const wrapper = mountPage()
    await flushPromises()

    const vm = wrapper.vm as unknown as DeviceSyncVm
    await vm.handleDisconnect('huawei')
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('无权操作该设备')

    wrapper.unmount()
  })
})

describe('data-input/DeviceSync.vue — 源码级删除契约', () => {
  function readSource(): string {
    return readFileSync(
      resolve(process.cwd(), 'src/views/data-input/components/DeviceSync.vue'),
      'utf8'
    )
  }

  it('不再有「非 200」兜底呈现：那个 else 在拦截器 reject 之后不可达', () => {
    // 只断言完整的 `res.code !== 200` 表达式 —— 注释里解释「信封 code !== 200 时 reject」
    // 是允许的，被禁的是把它当成一条呈现分支写回来。
    expect(readSource()).not.toContain('res.code !== 200')
  })

  it('不再有裸的错误 toast', () => {
    expect(readSource()).not.toContain('ElMessage.error')
  })

  it('不再有吞掉真实失败的「User cancelled」catch', () => {
    expect(readSource()).not.toContain('User cancelled')
  })
})
