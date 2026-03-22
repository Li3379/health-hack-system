import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import PlatformStatusTag from '@/components/device/PlatformStatusTag.vue'

describe('PlatformStatusTag.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('props rendering', () => {
    it('should render AVAILABLE status correctly', () => {
      const wrapper = mount(PlatformStatusTag, {
        props: {
          status: 'AVAILABLE',
          platform: 'huawei'
        }
      })

      expect(wrapper.find('.status-tag').exists()).toBe(true)
    })

    it('should render REQUIRES_MINI_PROGRAM status with warning', () => {
      const wrapper = mount(PlatformStatusTag, {
        props: {
          status: 'REQUIRES_MINI_PROGRAM',
          platform: 'wechat',
          reason: '需要小程序授权'
        }
      })

      expect(wrapper.find('.status-tag').exists()).toBe(true)
    })

    it('should render REQUIRES_APP status correctly', () => {
      const wrapper = mount(PlatformStatusTag, {
        props: {
          status: 'REQUIRES_APP',
          platform: 'apple',
          reason: '需要 iOS 应用'
        }
      })

      expect(wrapper.find('.status-tag').exists()).toBe(true)
    })

    it('should render COMING_SOON status', () => {
      const wrapper = mount(PlatformStatusTag, {
        props: {
          status: 'COMING_SOON',
          platform: 'xiaomi',
          reason: '服务开发中'
        }
      })

      expect(wrapper.find('.status-tag').exists()).toBe(true)
    })
  })

  describe('dialog interaction', () => {
    it('should show dialog when clicked on disabled tag', async () => {
      const wrapper = mount(PlatformStatusTag, {
        props: {
          status: 'REQUIRES_MINI_PROGRAM',
          platform: 'wechat'
        }
      })

      await wrapper.find('.status-badge').trigger('click')

      // Dialog should be visible (check for dialog element in DOM)
      expect(wrapper.find('.el-dialog').exists()).toBe(true)
    })
  })
})
