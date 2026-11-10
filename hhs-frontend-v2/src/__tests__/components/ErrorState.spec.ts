import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import ErrorState from '@/components/common/ErrorState.vue'

// el-button stub 写法 A（UI-SPEC 之外的关键细节）：模板里出现的 aria-label 会被 Vue
// 归一化成 ariaLabel prop。若声明了 prop 却不绑回模板，该值会被吞掉、永远落不到 DOM。
// 因此这里显式 `:aria-label="ariaLabel"`，让下面的 attributes('aria-label') 断言有意义。
const globalStubs = {
  'el-button': {
    template:
      '<button class="el-button" :disabled="loading" :aria-label="ariaLabel"><slot /></button>',
    props: ['type', 'size', 'loading', 'ariaLabel']
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  WarningFilled: {
    template: '<span class="icon-warning-filled">!</span>'
  },
  Lock: {
    template: '<span class="icon-lock">L</span>'
  },
  DocumentDelete: {
    template: '<span class="icon-document-delete">D</span>'
  }
}

function mountErrorState(
  props: Record<string, unknown> = {},
  attachToBody = false
): VueWrapper {
  return mount(ErrorState, {
    props: {
      title: '无法加载阈值列表',
      description: '可能是网络波动或服务暂时不可用，请重试。',
      ...props
    },
    global: { stubs: globalStubs },
    ...(attachToBody ? { attachTo: document.body } : {})
  })
}

describe('ErrorState.vue', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders the root block with role=status and polite live region by default', () => {
    const wrapper = mountErrorState()

    const root = wrapper.find('.error-state')
    expect(root.exists()).toBe(true)
    expect(root.attributes('role')).toBe('status')
    expect(root.attributes('aria-live')).toBe('polite')
    wrapper.unmount()
  })

  it('never uses role=alert on the root', () => {
    const wrapper = mountErrorState()

    expect(wrapper.attributes('role')).not.toBe('alert')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renders no retry button when retry is absent', () => {
    const wrapper = mountErrorState()

    expect(wrapper.find('.error-state__actions').exists()).toBe(false)
    expect(wrapper.find('.el-button').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renders the retry button with the fixed visible label when retry exists', () => {
    const wrapper = mountErrorState({ retry: vi.fn() })

    expect(wrapper.find('.error-state__actions').exists()).toBe(true)
    expect(wrapper.find('.el-button').text()).toBe('重试')
    wrapper.unmount()
  })

  it('uses retryLabel as the accessible name when provided', () => {
    const wrapper = mountErrorState({
      retry: vi.fn(),
      retryLabel: '重新加载阈值列表'
    })

    expect(wrapper.find('.el-button').attributes('aria-label')).toBe('重新加载阈值列表')
    // 可见文本仍固定为「重试」，可访问名才带对象。
    expect(wrapper.find('.el-button').text()).toBe('重试')
    wrapper.unmount()
  })

  it('composes 重试：{title} as the accessible name when retryLabel is omitted', () => {
    const wrapper = mountErrorState({ retry: vi.fn() })

    expect(wrapper.find('.el-button').attributes('aria-label')).toBe('重试：无法加载阈值列表')
    wrapper.unmount()
  })

  it('invokes the retry callback exactly once on click', async () => {
    const retry = vi.fn()
    const wrapper = mountErrorState({ retry })

    await wrapper.find('.el-button').trigger('click')

    expect(retry).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('sets aria-busy=true and disables the button while retrying', () => {
    const wrapper = mountErrorState({ retry: vi.fn(), retrying: true })

    expect(wrapper.find('.error-state').attributes('aria-busy')).toBe('true')
    expect(wrapper.find('.el-button').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('marks aria-busy=false when not retrying', () => {
    const wrapper = mountErrorState()

    expect(wrapper.find('.error-state').attributes('aria-busy')).toBe('false')
    wrapper.unmount()
  })

  it('applies the muted modifier for the forbidden variant', () => {
    const wrapper = mountErrorState({ variant: 'forbidden', title: '无权访问该页面' })

    expect(wrapper.find('.error-state__icon-circle').classes()).toContain(
      'error-state__icon-circle--muted'
    )
    wrapper.unmount()
  })

  it('does not apply the muted modifier for the load variant', () => {
    const wrapper = mountErrorState({ variant: 'load' })

    expect(wrapper.find('.error-state__icon-circle').classes()).not.toContain(
      'error-state__icon-circle--muted'
    )
    wrapper.unmount()
  })

  it('renders the warning glyph for load/server/network and neutral glyphs otherwise', () => {
    const load = mountErrorState({ variant: 'load' })
    expect(load.find('.icon-warning-filled').exists()).toBe(true)
    load.unmount()

    const server = mountErrorState({ variant: 'server' })
    expect(server.find('.icon-warning-filled').exists()).toBe(true)
    server.unmount()

    const network = mountErrorState({ variant: 'network' })
    expect(network.find('.icon-warning-filled').exists()).toBe(true)
    network.unmount()

    const forbidden = mountErrorState({ variant: 'forbidden' })
    expect(forbidden.find('.icon-lock').exists()).toBe(true)
    forbidden.unmount()

    const notFound = mountErrorState({ variant: 'notFound' })
    expect(notFound.find('.icon-document-delete').exists()).toBe(true)
    notFound.unmount()
  })

  it('omits the description element when description is empty', () => {
    const wrapper = mountErrorState({ description: '' })

    expect(wrapper.find('.error-state__desc').exists()).toBe(false)
    wrapper.unmount()
  })

  it('does not steal focus on mount', () => {
    const wrapper = mountErrorState({ retry: vi.fn() }, true)

    const active = document.activeElement
    expect(active === null || active === document.body).toBe(true)
    expect(wrapper.element.contains(active as Node | null)).toBe(false)
    wrapper.unmount()
  })

  // === Phase 14 Contract 2/3：scope 与 actions（既有 14 条用例保持不变）===

  it('adds the error-state--page class when scope is page', () => {
    const wrapper = mountErrorState({ scope: 'page' })

    expect(wrapper.find('.error-state').classes()).toContain('error-state--page')
    wrapper.unmount()
  })

  it('renders no retry button when actions is non-empty (the two are mutually exclusive)', () => {
    // actions 与 retry 互斥：需要重试就显式写一条 action，避免出现两个并列的
    // 「重试」入口（Contract 2 §2.1）。
    const wrapper = mountErrorState({
      retry: vi.fn(),
      actions: [{ label: '返回首页', onClick: vi.fn(), variant: 'primary' }]
    })

    const buttons = wrapper.findAll('.el-button')
    expect(buttons).toHaveLength(1)
    expect(buttons[0].text()).toBe('返回首页')
    // 按钮集合里没有第二个「重试」入口（说明文本里的「请重试」不算）。
    expect(buttons.map(button => button.text())).not.toContain('重试')
    wrapper.unmount()
  })

  it('renders actions in declaration order', () => {
    const wrapper = mountErrorState({
      variant: 'notFound',
      title: '资源不存在',
      actions: [
        { label: '返回列表', onClick: vi.fn(), variant: 'primary' },
        { label: '重新加载该记录', onClick: vi.fn(), variant: 'default' }
      ]
    })

    const labels = wrapper.findAll('.el-button').map(button => button.text())
    expect(labels).toEqual(['返回列表', '重新加载该记录'])
    wrapper.unmount()
  })

  it('uses each action-label as both the visible text and the aria-label', () => {
    // Contract 3 §3.2：actions[].label 双重身份 —— 标签必须自带对象，
    // 不能既写「重试」又把可访问名补成别的句子。
    const wrapper = mountErrorState({
      actions: [
        { label: '返回列表', onClick: vi.fn(), variant: 'primary' },
        { label: '重新加载该记录', onClick: vi.fn(), variant: 'default' }
      ]
    })

    const buttons = wrapper.findAll('.el-button')
    expect(buttons[0].text()).toBe('返回列表')
    expect(buttons[0].attributes('aria-label')).toBe('返回列表')
    expect(buttons[1].text()).toBe('重新加载该记录')
    expect(buttons[1].attributes('aria-label')).toBe('重新加载该记录')
    wrapper.unmount()
  })

  it('keeps the muted icon circle for the forbidden variant in page scope', () => {
    // 403/404 是中性状态，不能渲染成红色故障；页级块只是去掉了卡片面，不改配色语义。
    const wrapper = mountErrorState({ scope: 'page', variant: 'forbidden' })

    expect(wrapper.find('.error-state--page').exists()).toBe(true)
    expect(wrapper.find('.error-state__icon-circle').classes()).toContain(
      'error-state__icon-circle--muted'
    )
    expect(wrapper.find('.icon-lock').exists()).toBe(true)
    wrapper.unmount()
  })
});