import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { Mock } from 'vitest'

/**
 * ERR-03 + ERR-07 + D-03 的端到端锚点（settings/DevicePlatformConfig.vue 的动作通道）。
 *
 * 本页同时拥有两种呈现通道，本 spec 正是钉住二者**不互相塌陷**：
 *  - `loadConfigs` 的 403 → 页级 ErrorState 块、零 toast（由 DevicePlatformConfigForbidden.spec.ts 覆盖）。
 *  - save / test / delete / init 的 403 → 恰好一条常驻 toast，**页级块必须缺席**。
 *
 * 若有人删掉 save catch 上的 `presentStates: true`，403 会静默（0 条）；
 * 若有人把它换成「渲染页级块」，页面连同用户的弹窗会被顶掉 —— 两个方向各有一条断言。
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

// gsap 时间线只在 onMounted 里跑一次动画；形状照抄 DevicePlatformConfigForbidden.spec.ts:37-48。
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

// 保住组件导出，只替换 ElMessage / ElMessageBox，才能既有页面、又能对每次 toast（含对象式）计数。
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

import DevicePlatformConfig from '@/views/settings/DevicePlatformConfig.vue'
import { deviceApi } from '@/api/device'
import { ElMessage, ElMessageBox } from 'element-plus'

const adminGetAllStatusMock = deviceApi.adminGetAllStatus as unknown as Mock
const checkConfigReadyMock = deviceApi.checkConfigReady as unknown as Mock
const adminSaveConfigMock = deviceApi.adminSaveConfig as unknown as Mock
const adminDeleteConfigMock = deviceApi.adminDeleteConfig as unknown as Mock
const confirmMock = ElMessageBox.confirm as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/**
 * showError 的 VNode 分支有模块级 2000ms 去重闩锁：同文案的用例会互相吞掉对方的 toast，
 * 让断言读成 0（那是闩锁生效，不是呈现缺失）。让每次 Date.now() 前进 60s。
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

const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  // 表格内容与本次用例无关；不渲染列插槽，避免 getPlatformIcon 收到占位 row。
  'el-table': {
    template: '<div class="el-table" />',
    props: ['data', 'stripe']
  },
  'el-table-column': {
    template: '<div class="el-table-column" />',
    props: ['prop', 'label', 'width', 'fixed', 'align', 'minWidth']
  },
  // el-dialog 只在 modelValue 为真时渲染插槽 —— configFormRef 依赖它存在。
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose']
  },
  // 真实 EP 的 validate 回调签名是 (valid, invalidFields)；stub 必须同形。
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
  'el-select': {
    template: '<select class="el-select"><slot /></select>',
    props: ['modelValue', 'placeholder', 'multiple', 'filterable', 'allowCreate']
  },
  'el-option': {
    template: '<option class="el-option" :value="value">{{ label }}</option>',
    props: ['label', 'value']
  },
  // 转发 v-model：保存用例要断言用户键入的 clientId 在失败后原样保留。
  'el-input': {
    template:
      '<input class="el-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
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
  // FormErrorSummary 内部用 el-alert 承载标题（默认插槽在 <p> 里，列表放在外面）。
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /><slot /></div>',
    props: ['title', 'type', 'showIcon', 'closable']
  },
  'el-avatar': {
    template: '<span class="el-avatar"><slot /></span>',
    props: ['size']
  },
  'el-button-group': {
    template: '<div class="el-button-group"><slot /></div>'
  },
  'el-button': {
    template: '<button class="el-button" :disabled="disabled"><slot /></button>',
    props: ['type', 'size', 'loading', 'disabled', 'plain', 'icon', 'ariaLabel']
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  Refresh: {
    template: '<span class="icon-refresh" />'
  },
  // ErrorState 不 stub —— 本用例要断言它的**缺席**（save 403 不得渲染页级块）。
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

/** 加载成功：页面有内容，所以「保存 403 不得顶掉页面」才有意义。 */
function seedLoadedPage() {
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
  checkConfigReadyMock.mockResolvedValueOnce({
    code: 200,
    message: 'success',
    data: { encryptionReady: true }
  })
}

/** 打开配置弹窗并填入凭据（保存用例的前置状态）。 */
async function openDialogAndFill(wrapper: ReturnType<typeof mountPage>, clientId: string) {
  const vm = wrapper.vm as unknown as {
    openConfigDialog: (p: unknown) => void
    configForm: { clientId: string; clientSecret: string }
  }
  vm.openConfigDialog({
    platform: 'huawei',
    platformName: '华为运动健康',
    configured: true
  })
  await flushPromises()
  vm.configForm.clientId = clientId
  vm.configForm.clientSecret = 'super-secret-value'
  await flushPromises()
  return vm
}

describe('settings/DevicePlatformConfig.vue — 动作通道：保存的 403 是一条 toast，绝不顶掉页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    adminGetAllStatusMock.mockReset()
    checkConfigReadyMock.mockReset()
    adminSaveConfigMock.mockReset()
    adminDeleteConfigMock.mockReset()
    confirmMock.mockReset()
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('保存时 403：恰好一条 toast、页级块缺席、弹窗仍开、clientId 原样保留', async () => {
    seedLoadedPage()
    const wrapper = mountPage()
    await flushPromises()

    // 前置：页面确实有内容（否则「块缺席」这条断言会因为页面本来就空而空过）。
    expect(wrapper.find('.el-card').exists()).toBe(true)
    expect(wrapper.find('.error-state--page').exists()).toBe(false)

    const vm = await openDialogAndFill(wrapper, 'my-client-id')
    expect(wrapper.find('.el-dialog').exists()).toBe(true)

    adminSaveConfigMock.mockRejectedValueOnce({
      response: {
        status: 403,
        data: { code: 403, errorCode: 'auth.forbidden', message: '没有访问权限' }
      }
    })

    ;(vm as unknown as { handleSaveConfig: () => Promise<void> }).handleSaveConfig()
    await flushPromises()

    // 1. 一条常驻 toast —— 缺 presentStates 时这里是 0（B1 的回归护栏）。
    expect(errorToastCount()).toBe(1)
    expect(toastCount()).toBe(1)

    // 2. 页级块缺席：用一次动作的 403 顶掉整页内容是本 spec 存在的理由。
    expect(wrapper.find('.error-state--page').exists()).toBe(false)
    expect(wrapper.find('.error-state').exists()).toBe(false)
    // 页面仍带着加载期的内容。
    expect(wrapper.find('.el-card').exists()).toBe(true)

    // 3. 弹窗仍然打开、用户输入不变（ERR-05）。
    expect(wrapper.find('.el-dialog').exists()).toBe(true)
    expect(vm.configForm.clientId).toBe('my-client-id')
    expect(vm.configForm.clientSecret).toBe('super-secret-value')

    wrapper.unmount()
  })

  it('保存时 400：汇总 + 内联一项，零 toast，弹窗仍开', async () => {
    seedLoadedPage()
    const wrapper = mountPage()
    await flushPromises()

    const vm = await openDialogAndFill(wrapper, '')

    adminSaveConfigMock.mockRejectedValueOnce({
      response: {
        status: 400,
        data: {
          code: 400,
          message: '请检查以下填写内容',
          errorCode: 'validation.invalid_parameter',
          fieldErrors: [{ field: 'clientId', message: '客户端 ID 不能为空' }]
        }
      }
    })

    ;(vm as unknown as { handleSaveConfig: () => Promise<void> }).handleSaveConfig()
    await flushPromises()

    // 1. 零 toast —— validation 由汇总 + 内联承担（D-01）。
    expect(toastCount()).toBe(0)

    // 2. 汇总块按 fieldOrder 落在 clientId；标签取全局表「客户端 ID」（本页不覆盖 labels）。
    const summary = wrapper.find('.form-error-summary')
    expect(summary.exists()).toBe(true)
    expect(summary.text()).toContain('请修正以下 1 项填写问题')
    const items = summary.findAll('li')
    expect(items).toHaveLength(1)
    expect(items[0].text()).toBe('客户端 ID：客户端 ID 不能为空')

    // 3. 恰好一项内联错误，且它就是 clientId。
    const errored = wrapper.findAll('.el-form-item.is-error')
    expect(errored).toHaveLength(1)
    expect(wrapper.findAll('.el-form-item__error').map(n => n.text())).toEqual([
      '客户端 ID 不能为空'
    ])

    // 4. 弹窗仍开、页级块缺席。
    expect(wrapper.find('.el-dialog').exists()).toBe(true)
    expect(wrapper.find('.error-state--page').exists()).toBe(false)

    wrapper.unmount()
  })

  it('删除确认框取消：零提示且不发 DELETE', async () => {
    seedLoadedPage()
    confirmMock.mockRejectedValueOnce(Object.assign(new Error('canceled'), { name: 'AbortError' }))

    const wrapper = mountPage()
    await flushPromises()

    const vm = wrapper.vm as unknown as { handleDeleteConfig: (p: string) => Promise<void> }
    await vm.handleDeleteConfig('huawei')
    await flushPromises()

    expect(toastCount()).toBe(0)
    expect(adminDeleteConfigMock).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('删除的 API 真失败：不再被当成「用户取消」吞掉，恰好一条提示', async () => {
    seedLoadedPage()
    confirmMock.mockResolvedValueOnce('confirm')
    adminDeleteConfigMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const wrapper = mountPage()
    await flushPromises()

    const vm = wrapper.vm as unknown as { handleDeleteConfig: (p: string) => Promise<void> }
    await vm.handleDeleteConfig('huawei')
    await flushPromises()

    // 旧实现把这里吞成 "User cancelled"：一次失败的删除什么都不显示。
    expect(errorToastCount()).toBe(1)

    wrapper.unmount()
  })
})