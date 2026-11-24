import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'

/**
 * ERR-03 的推送设置页锚点（`views/user/PushSettings.vue`）。
 *
 * 本页的难点不是「有没有内联错误」，而是**通道之间不许串**：页面在一个 `v-for` 里
 * 给每个通道渲染一个 `el-form`，每个都绑定 `{ configValue: … }`，而
 * `PushConfigRequest` 的字段名恒为 `configValue`。一个按裸字段名索引的投影表会把
 * 邮件通道的错误标到企业微信通道的输入框上 —— 那种实现在第一半断言（有错的那条通道
 * 确实标了）通过、第二半断言（兄弟通道必须干净）失败。第二半才是本 spec 的意义。
 *
 * 两个用例：
 *  1. 一个通道保存被 400 拒（`configValue` 超长）→ **只有该通道**有内联 + 汇总，兄弟通道
 *     干净，零 toast（`validation` 不弹 toast），且被拒的值仍在输入框里（ERR-05）。
 *  2. 加载失败块与字段投影共存：保存失败不会清掉 `loadError`，加载失败也不会清掉投影 ——
 *     两套机制各自独立，谁也不替代谁。
 *
 * 后端依据：`PushConfigController.java:102` 的 `@RequestBody @Valid PushConfigRequest`
 * 与 `PushConfigRequest` 上的 `@Size(max = 500, message = "配置内容不能超过 500 个字符")`。
 * 一个超长粘贴会真的产生 HTTP 400 + `fieldErrors`，所以这条通路是可达的，不是防御性接线。
 */

vi.mock('@/api/push', () => ({
  getPushConfigs: vi.fn(),
  updatePushConfig: vi.fn(),
  testPushChannel: vi.fn(),
  getPushHistory: vi.fn(),
  getPushStats: vi.fn()
}))

vi.mock('@/composables/useGsap', () => ({
  staggerReveal: vi.fn(),
  cleanupScrollTriggers: vi.fn(),
  gsap: { to: vi.fn(), timeline: () => ({ fromTo: vi.fn().mockReturnThis() }) },
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
    })
  }
})

import PushSettings from '@/views/user/PushSettings.vue'
import { getPushConfigs, updatePushConfig } from '@/api/push'
import { ElMessage } from 'element-plus'

const getConfigsMock = getPushConfigs as unknown as Mock
const updateConfigMock = updatePushConfig as unknown as Mock
const ElMessageMock = ElMessage as unknown as Mock &
  Record<'error' | 'success' | 'warning' | 'info', Mock>

/** showError 的 VNode 分支有模块级 2000ms 闩锁；让每次 Date.now() 前进 60s。 */
let nowCursor = 1_700_000_000_000
vi.spyOn(Date, 'now').mockImplementation(() => (nowCursor += 60_000))

/** 四通道统计：对象式调用 + 函数式旧 API。 */
function toastCount(): number {
  return (
    ElMessageMock.mock.calls.length +
    ElMessageMock.error.mock.calls.length +
    ElMessageMock.success.mock.calls.length +
    ElMessageMock.warning.mock.calls.length +
    ElMessageMock.info.mock.calls.length
  )
}

function errorToastCount(): number {
  const objectCalls = ElMessageMock.mock.calls.filter(
    ([options]) => (options as { type?: string } | undefined)?.type === 'error'
  ).length
  return objectCalls + ElMessageMock.error.mock.calls.length
}

const globalStubs = {
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': { template: '<div class="el-col"><slot /></div>', props: ['xs', 'lg'] },
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-collapse-transition': { template: '<div><slot /></div>' },
  // disabled 必须真的落到 DOM 上：S3 的「不会把用户锁死在开关里」就是靠它观察的。
  'el-switch': {
    template: '<input type="checkbox" class="el-switch" :disabled="disabled" />',
    props: ['modelValue', 'disabled']
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
  'el-empty': { template: '<div class="el-empty">{{ description }}</div>', props: ['description'] },
  'el-timeline': { template: '<div class="el-timeline"><slot /></div>' },
  'el-timeline-item': { template: '<div class="el-timeline-item"><slot /></div>' },
  'el-icon': { template: '<span class="el-icon"><slot /></span>', props: ['size'] },
  'el-button': {
    template: '<button class="el-button"><slot /></button>',
    props: ['type', 'text', 'size', 'loading']
  },
  'el-form': { template: '<form class="el-form"><slot /></form>' },
  // 内联错误必须真的渲染到 DOM（含 is-error 类），才能证明 :error 绑到了本通道的复合键。
  'el-form-item': {
    template:
      '<div class="el-form-item" :class="{ \'is-error\': !!error }"><label>{{ label }}</label><slot /><span v-if="error" class="el-form-item__error">{{ error }}</span></div>',
    props: ['label', 'prop', 'error']
  },
  'el-input': {
    template: '<input class="el-input" :value="modelValue" />',
    props: ['modelValue', 'placeholder', 'clearable']
  },
  // FormErrorSummary 内部用 el-alert 承载标题。
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /></div>',
    props: ['type', 'closable', 'showIcon']
  }
}

const mountOptions = {
  attachTo: document.body,
  global: {
    stubs: globalStubs,
    directives: { loading: () => {} }
  }
}

function pushConfig(overrides: Record<string, unknown>) {
  return {
    id: 1,
    channelType: 'EMAIL',
    channelLabel: '邮件',
    configKey: 'address',
    configValue: '',
    enabled: true,
    offlineSupported: true,
    isConfigured: true,
    availability: true,
    createdAt: '',
    updatedAt: '',
    ...overrides
  }
}

/** 两个离线通道：串通道的缺陷只有在有兄弟通道时才能被观察到。 */
function twoChannels() {
  return [
    pushConfig({ id: 1, channelType: 'EMAIL', channelLabel: '邮件', configValue: 'a@b.com' }),
    pushConfig({
      id: 2,
      channelType: 'WECOM',
      channelLabel: '企业微信',
      configKey: 'webhook',
      configValue: 'https://qyapi.weixin.qq.com/hook/ok'
    })
  ]
}

/** 真实形态：`@Size(max = 500)` 被违反 → HTTP 400 + fieldErrors。 */
function configValueTooLong() {
  return {
    response: {
      status: 400,
      data: {
        code: 400,
        message: '参数校验失败',
        errorCode: 'validation.invalid_parameter',
        fieldErrors: [{ field: 'configValue', message: '配置内容不能超过 500 个字符' }]
      }
    }
  }
}

describe('PushSettings.vue — 每通道独立的内联投影（ERR-03 / ERR-05）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getConfigsMock.mockReset()
    updateConfigMock.mockReset()
    getConfigsMock.mockResolvedValue({ code: 200, message: 'success', data: twoChannels() })
  })

  afterEach(() => {
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('一个通道保存被 400 拒：只有该通道有内联与汇总，兄弟通道干净，零 toast，值保留', async () => {
    const wrapper = mount(PushSettings, mountOptions)
    await flushPromises()

    // 两个通道各渲染一个 configValue 输入框，data-field 是复合键而非裸字段名。
    const emailField = wrapper.find('[data-field="EMAIL::configValue"]')
    const wecomField = wrapper.find('[data-field="WECOM::configValue"]')
    expect(emailField.exists()).toBe(true)
    expect(wecomField.exists()).toBe(true)

    const vm = wrapper.vm as any
    const emailConfig = vm.pushStore.configs.find((c: any) => c.channelType === 'EMAIL')
    const longValue = 'https://example.com/' + 'x'.repeat(600)
    emailConfig.configValue = longValue
    await flushPromises()

    updateConfigMock.mockRejectedValueOnce(configValueTooLong())

    await vm.handleSaveConfig(emailConfig)
    await flushPromises()

    // 1. 零 toast —— validation 由汇总 + 内联承担（D-01）。
    expect(toastCount()).toBe(0)
    expect(errorToastCount()).toBe(0)

    // 2. 出错通道：一条内联 + 一份汇总，标签是通道自己的措辞而非复合键。
    const emailItem = wrapper.find('[data-field="EMAIL::configValue"]')
    expect(emailItem.classes()).toContain('is-error')
    expect(emailItem.find('.el-form-item__error').text()).toBe('配置内容不能超过 500 个字符')

    const summaries = wrapper.findAll('.form-error-summary')
    expect(summaries).toHaveLength(1)
    expect(summaries[0].text()).toContain('请修正以下 1 项填写问题')
    // 标签来自 labels 覆盖：通道名 + 该通道的配置标签，绝不出现 WECOM::configValue 这类键。
    expect(summaries[0].text()).toContain('邮件 邮箱地址：配置内容不能超过 500 个字符')
    expect(summaries[0].text()).not.toContain('::')

    // 3. **本用例的重点**：兄弟通道一个错误都没有，也没有自己的汇总。
    const wecomItem = wrapper.find('[data-field="WECOM::configValue"]')
    expect(wecomItem.classes()).not.toContain('is-error')
    expect(wecomItem.find('.el-form-item__error').exists()).toBe(false)
    // 整个页面只有一个 is-error —— 串通道的实现会在这里读到 2。
    expect(wrapper.findAll('.el-form-item.is-error')).toHaveLength(1)

    // 4. ERR-05：被拒的值仍在输入框里，没被清空。
    const emailInput = emailItem.find('input')
    expect((emailInput.element as HTMLInputElement).value).toBe(longValue)

    // 5. 汇总条目可点击跳焦：复合键必须能穿过 focusField 的 `[data-field="…"]` 选择器。
    //    这条断言证明 `::` 分隔符没有让选择器失效（转义后仍能命中本通道的输入框）。
    const summaryLink = summaries[0].find('.form-error-summary__link')
    expect(summaryLink.exists()).toBe(true)
    await summaryLink.trigger('click')
    await flushPromises()
    expect(document.activeElement).toBe(emailInput.element)

    wrapper.unmount()
  })

  it('加载失败块与字段投影共存：保存失败不清 loadError，加载失败不清投影', async () => {
    const wrapper = mount(PushSettings, mountOptions)
    await flushPromises()

    const vm = wrapper.vm as any
    const emailConfig = vm.pushStore.configs.find((c: any) => c.channelType === 'EMAIL')
    emailConfig.configValue = 'https://example.com/' + 'y'.repeat(600)
    await flushPromises()

    updateConfigMock.mockRejectedValueOnce(configValueTooLong())
    await vm.handleSaveConfig(emailConfig)
    await flushPromises()

    // 前提：字段投影已存在，页面级块尚未渲染（加载是成功的）。
    expect(wrapper.findAll('.form-error-summary')).toHaveLength(1)
    expect(wrapper.find('.error-state').exists()).toBe(false)
    expect(vm.pushStore.loadError).toBeNull()

    // 现在让加载失败：页面级块出现。
    getConfigsMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })
    await vm.pushStore.loadConfigs()
    await flushPromises()

    // 1. 页面级块渲染，且它的重试入口在（既有行为未被本 plan 改动）。
    const errorState = wrapper.find('.error-state')
    expect(errorState.exists()).toBe(true)
    expect(errorState.text()).toContain('无法加载推送配置')
    expect(errorState.find('button').exists()).toBe(true)

    // 2. 加载失败**不**清掉字段投影：投影表里仍留着 EMAIL 的条目。
    //    这是「两套机制各自独立」的可观察形式 —— 保存失败没有把 loadError 置空，
    //    加载失败也没有把投影表清空。
    expect(vm.pushStore.saveError).not.toBeNull()
    expect(vm.pushStore.saveError.channelType).toBe('EMAIL')

    wrapper.unmount()
  })

  it('服务端未开通：未启用的开关禁用且拒绝开启；已启用的仍可关闭（不锁死）', async () => {
    getConfigsMock.mockResolvedValue({
      code: 200,
      message: 'success',
      data: [
        // 未启用 + 服务端未开通 → 开关禁用，用户点不了（后端也会 400）。
        pushConfig({ id: 1, channelType: 'EMAIL', enabled: false, availability: false }),
        // 已启用 + 服务端未开通 → 必须仍可关闭，否则用户被永久锁在开启状态。
        pushConfig({ id: 2, channelType: 'WECOM', channelLabel: '企业微信', enabled: true, availability: false })
      ]
    })

    const wrapper = mount(PushSettings, mountOptions)
    await flushPromises()

    const switches = wrapper.findAll('.el-switch')
    expect(switches).toHaveLength(2)
    // EMAIL：未启用 → 禁用
    expect((switches[0].element as HTMLInputElement).disabled).toBe(true)
    // WECOM：已启用 → 不禁用（否则无法关闭）
    expect((switches[1].element as HTMLInputElement).disabled).toBe(false)

    // handleToggle 直接调用（绕过禁用态）也必须拒绝开启，且不产生 PUT。
    const vm = wrapper.vm as any
    const emailConfig = vm.pushStore.configs.find((c: any) => c.channelType === 'EMAIL')
    emailConfig.enabled = true
    await vm.handleToggle(emailConfig)
    await flushPromises()

    expect(emailConfig.enabled).toBe(false)
    expect(updateConfigMock).not.toHaveBeenCalled()
    expect(ElMessageMock.warning).toHaveBeenCalled()

    wrapper.unmount()
  })
})
