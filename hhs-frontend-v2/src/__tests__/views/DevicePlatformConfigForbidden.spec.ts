import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import type { Mock } from 'vitest'

/**
 * 本阶段的端到端锚点（Phase 14 / ERR-07 / D-03）：
 * `/settings/device-platform` 的首个主查询返回 403 时，整页内容降级为**页级解释块**，
 * 零 toast，且不再发出第二个请求（checkConfigReady）。
 *
 * 为什么要在页面级测：403 的两个性质（「替换整块页面内容」与「不弹提示」）只有把
 * 分类层、呈现层、页面状态三者串起来才能验证。单独测 normalizeApiError 或
 * 单独测 ErrorState 都无法证明它们被接在一起。
 */

vi.mock('@/api/device', () => ({
  deviceApi: {
    getConnections: vi.fn(),
    getConfig: vi.fn(),
    getPlatforms: vi.fn(),
    connect: vi.fn(),
    disconnect: vi.fn(),
    sync: vi.fn(),
    syncAll: vi.fn(),
    getSyncHistory: vi.fn(),
    checkConfigReady: vi.fn(),
    adminGetAllStatus: vi.fn(),
    adminSaveConfig: vi.fn(),
    adminTestConfig: vi.fn(),
    adminDeleteConfig: vi.fn(),
    adminInitDefaults: vi.fn()
  }
}))

// gsap 时间线只在 onMounted 里跑一次动画；这里给一个形状完整的替身。
vi.mock('@/composables/useGsap', () => ({
  gsap: {
    timeline: () => {
      const timeline: Record<string, unknown> = {}
      timeline.fromTo = () => timeline
      return timeline
    }
  },
  cleanupScrollTriggers: vi.fn(),
  DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
  EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
}))

vi.mock('vue-router', async importOriginal => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: vi.fn(() => ({ query: {}, params: {}, path: '/settings/device-platform' })),
    useRouter: vi.fn(() => ({ push: vi.fn(), replace: vi.fn() }))
  }
})

// 与 ErrorLayerSample.spec.ts:53-68 同形：保住组件导出，只替换 ElMessage，
// 这样才能既挂载页面组件、又对每次 toast 调用（含对象式）计数。
vi.mock('element-plus', async importOriginal => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: Object.assign(
      vi.fn(() => ({ close: vi.fn() })),
      {
        error: vi.fn(),
        success: vi.fn(),
        warning: vi.fn(),
        info: vi.fn(),
        closeAll: vi.fn()
      }
    ),
    ElMessageBox: { confirm: vi.fn() }
  }
})

import DevicePlatformConfig from '@/views/settings/DevicePlatformConfig.vue'
import { deviceApi } from '@/api/device'
import { ElMessage } from 'element-plus'

const adminGetAllStatusMock = deviceApi.adminGetAllStatus as unknown as Mock
const checkConfigReadyMock = deviceApi.checkConfigReady as unknown as Mock

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
    ([options]) => (options as { type?: string } | undefined)?.type === 'error'
  ).length
  return objectCalls + ElMessageMock.error.mock.calls.length
}

const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  // 表格内容与 403 无关；不渲染列插槽，避免 getPlatformIcon 收到占位 row。
  'el-table': {
    template: '<div class="el-table"><slot /></div>',
    props: ['data', 'stripe']
  },
  'el-table-column': {
    template: '<div class="el-table-column" />',
    props: ['prop', 'label', 'width', 'fixed', 'align', 'minWidth']
  },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose']
  },
  'el-form': {
    template: '<form class="el-form"><slot /></form>',
    methods: {
      validate: (cb: (valid: boolean) => void) => Promise.resolve(cb(true)),
      validateField: vi.fn(),
      clearValidate: vi.fn()
    }
  },
  'el-form-item': {
    template: '<div class="el-form-item"><slot /></div>',
    props: ['label', 'prop']
  },
  'el-select': {
    template: '<select class="el-select"><slot /></select>',
    props: ['modelValue', 'placeholder', 'multiple', 'filterable', 'allowCreate']
  },
  'el-option': {
    template: '<option class="el-option" :value="value">{{ label }}</option>',
    props: ['label', 'value']
  },
  'el-input': {
    template: '<input class="el-input" />',
    props: ['modelValue', 'type', 'placeholder', 'showPassword', 'showWordLimit', 'maxlength']
  },
  'el-switch': {
    template: '<span class="el-switch" />',
    props: ['modelValue', 'disabled']
  },
  'el-tag': {
    template: '<span class="el-tag"><slot /></span>',
    props: ['type', 'size']
  },
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /><slot /></div>',
    props: ['title', 'type', 'showIcon']
  },
  'el-avatar': {
    template: '<span class="el-avatar"><slot /></span>',
    props: ['size']
  },
  'el-button-group': {
    template: '<div class="el-button-group"><slot /></div>'
  },
  'el-button': {
    template:
      '<button class="el-button" :type="type" :disabled="loading" :aria-label="ariaLabel"><slot /></button>',
    props: ['type', 'size', 'loading', 'disabled', 'ariaLabel']
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  Refresh: {
    template: '<span class="icon-refresh" />'
  },
  // ErrorState 不 stub —— 本用例要断言它真的渲染出页级块。
  Lock: {
    template: '<span class="icon-lock">L</span>'
  },
  DocumentDelete: {
    template: '<span class="icon-document-delete">D</span>'
  },
  WarningFilled: {
    template: '<span class="icon-warning-filled">!</span>'
  }
}

function mountPage() {
  return mount(DevicePlatformConfig, {
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

/** 真实形态：GlobalExceptionHandler.handleAccessDenied 返回 HTTP 403 + 信封 code 403。 */
function forbiddenRejection() {
  return {
    response: {
      status: 403,
      data: { code: 403, errorCode: 'auth.forbidden', message: '没有访问权限' }
    }
  }
}

describe('settings/DevicePlatformConfig.vue — 403 页级解释态（D-03 端到端）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    adminGetAllStatusMock.mockReset()
    checkConfigReadyMock.mockReset()
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('首个查询返回 403：渲染页级块、零 toast、且不再发第二个请求', async () => {
    adminGetAllStatusMock.mockRejectedValueOnce(forbiddenRejection())

    const wrapper = mountPage()
    await flushPromises()

    // 1. 页级几何：错误块出现在页面根元素内，且带 page 修饰类。
    const block = wrapper.find('.error-state--page')
    expect(block.exists()).toBe(true)
    expect(wrapper.find('.error-state__title').text()).toBe('无权访问此页面')

    // 2. 零 toast —— 403 是状态，呈现由状态块独占，不得同时弹提示。
    expect(errorToastCount()).toBe(0)
    expect(toastCount()).toBe(0)

    // 3. 串行请求被打断：403 后不应再发 checkConfigReady（Pitfall 12）。
    expect(checkConfigReadyMock).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('页级块以 返回首页 作为唯一动作，且标签同时是可见文本与可访问名', async () => {
    adminGetAllStatusMock.mockRejectedValueOnce(forbiddenRejection())

    const wrapper = mountPage()
    await flushPromises()

    const buttons = wrapper.find('.error-state').findAll('.el-button')
    expect(buttons).toHaveLength(1)
    expect(buttons[0].text()).toBe('返回首页')
    expect(buttons[0].attributes('aria-label')).toBe('返回首页')

    wrapper.unmount()
  })

  it('后端消息点名被拒动作时用它，不用通用兜底句', async () => {
    adminGetAllStatusMock.mockRejectedValueOnce({
      response: {
        status: 403,
        data: { code: 403, errorCode: 'auth.forbidden', message: '无权修改其他用户的阈值数据' }
      }
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.error-state__desc').text()).toBe('无权修改其他用户的阈值数据')

    wrapper.unmount()
  })

  it('通用「没有访问权限」不覆盖兜底说明句', async () => {
    // 后端只说「没有访问权限」时，用户读不到任何下一步；兜底句给出「返回首页 / 联系管理员」。
    adminGetAllStatusMock.mockRejectedValueOnce(forbiddenRejection())

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.error-state__desc').text()).toBe(
      '当前账号没有访问该页面的权限，请返回首页，或联系管理员申请开通。'
    )

    wrapper.unmount()
  })

  it('非 403 失败仍走 toast 而不是页级块（页级块只属于权限语义）', async () => {
    adminGetAllStatusMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.error-state--page').exists()).toBe(false)
    expect(errorToastCount()).toBe(1)

    wrapper.unmount()
  })

  it('刷新后权限已开通：页级块被清除，页面内容恢复', async () => {
    adminGetAllStatusMock.mockRejectedValueOnce(forbiddenRejection())
    checkConfigReadyMock.mockResolvedValueOnce({
      code: 200,
      message: 'success',
      data: { encryptionReady: true }
    })
    adminGetAllStatusMock.mockResolvedValueOnce({
      code: 200,
      message: 'success',
      data: [
        {
          platform: 'huawei',
          platformName: '华为运动健康',
          configured: true,
          oauthReady: true,
          enabled: true,
          supportedDataTypes: ['heart_rate']
        }
      ]
    })

    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('.error-state--page').exists()).toBe(true)

    const vm = wrapper.vm as unknown as { loadConfigs: () => Promise<void> }
    await vm.loadConfigs()
    await flushPromises()

    expect(wrapper.find('.error-state--page').exists()).toBe(false)
    expect(wrapper.find('.el-card').exists()).toBe(true)

    wrapper.unmount()
  })
})

describe('settings/DevicePlatformConfig.vue — 源码级几何契约', () => {
  /** 源码断言：样式契约无法在 jsdom 里计算，只能对源文件做机械校验。 */
  function readSource(): string {
    return readFileSync(resolve(process.cwd(), 'src/components/common/ErrorState.vue'), 'utf8')
  }

  it('页级高度扣掉页头 64px 与宿主声明的内边距变量', () => {
    const source = readSource()
    expect(source).toContain('calc(100vh - 64px - var(--hhs-page-pad-y, 0px))')
  })

  it('组件内不出现硬编码的 104px 高度', () => {
    // 104 = 64 + 40，只对 padding:20px 的宿主成立；padding:0 与 padding:32px 的宿主
    // 会分别欠填 40px 与溢出 24px（UI-SPEC Spacing 实测表）。
    // 注释里解释「为什么不能写 104」是允许的，被禁的是真的把它当高度用。
    expect(readSource()).not.toContain('104px')
  })

  it('说明句用次级文字色而不是三级色', () => {
    const source = readSource()
    const descRule = source.slice(source.indexOf('.error-state__desc {'))
    const ruleBody = descRule.slice(0, descRule.indexOf('}'))
    expect(ruleBody).toContain('var(--text-2)')
    expect(ruleBody).not.toContain('var(--text-3)')
  })
})
