import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * UAT-4 反回归测试：OAuth 回调失败页只能显示固定中文。
 *
 * `error_description` 是回调 URL 上的查询参数，完全由外部控制且通常是英文；
 * 早期实现把它直接塞进 `el-alert :description`，导致页面上出现
 * `User denied access` 这类原始平台字符串（既是英文泄漏，也是内容欺骗面）。
 */

const routeState = vi.hoisted(() => ({
  params: { platform: 'huawei' } as Record<string, string>,
  query: {} as Record<string, string>
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('@/api/device', () => ({
  deviceApi: {
    checkConnectionStatus: vi.fn().mockResolvedValue({ data: { connected: false } }),
    getConnectUrl: vi.fn()
  }
}))

vi.mock('@/composables/useGsap', () => ({
  gsap: {
    fromTo: vi.fn(),
    to: vi.fn(),
    timeline: () => ({ fromTo: vi.fn().mockReturnThis(), to: vi.fn() })
  }
}))

import ElementPlus from 'element-plus'
import OAuthCallback from '@/views/oauth/OAuthCallback.vue'

async function render(): Promise<string> {
  const wrapper = mount(OAuthCallback, { global: { plugins: [ElementPlus] } })
  // onMounted 里才解析 query 并写入错误态，必须等一次微任务队列排空再取文本。
  await flushPromises()
  return wrapper.text()
}

describe('OAuthCallback 错误文案', () => {
  beforeEach(() => {
    sessionStorage.clear()
    routeState.params = { platform: 'huawei' }
    routeState.query = {}
  })

  it('renders fixed Chinese copy for access_denied and never echoes error_description', async () => {
    routeState.query = {
      error: 'access_denied',
      error_description: 'User denied access'
    }

    const text = await render()

    expect(text).toContain('您在授权页面取消了授权')
    expect(text).not.toContain('User denied access')
    expect(text).not.toContain('denied access')
  })

  it('falls back to fixed Chinese copy for an unknown provider error code', async () => {
    routeState.query = {
      error: 'weird_code',
      error_description: 'Something broke'
    }

    const text = await render()

    expect(text).toContain('授权未能完成，请重新发起连接')
    expect(text).not.toContain('Something broke')
    expect(text).not.toContain('weird_code')
  })

  it('rejects an unknown platform with fixed Chinese copy instead of falling through', async () => {
    routeState.params = { platform: 'weibo' }
    routeState.query = { code: 'abc', state: 'xyz' }

    const text = await render()

    expect(text).toContain('该设备平台暂不支持授权连接')
    expect(text).not.toContain('weibo')
  })
})
