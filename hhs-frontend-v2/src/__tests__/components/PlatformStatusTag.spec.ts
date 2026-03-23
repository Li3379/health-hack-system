import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import PlatformStatusTag from '@/components/device/PlatformStatusTag.vue'

// Stub Element Plus components for unit testing
const globalStubs = {
  'el-tooltip': {
    template: '<div class="el-tooltip"><slot /></div>'
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose']
  },
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /><slot /></div>',
    props: ['type', 'closable', 'showIcon']
  },
  'el-divider': {
    template: '<hr class="el-divider" />'
  },
  'el-link': {
    template: '<a class="el-link"><slot /></a>',
    props: ['href', 'target', 'type']
  },
  'el-button': {
    template: '<button class="el-button"><slot /></button>'
  },
  Warning: {
    template: '<span class="icon-warning">!</span>'
  },
  Link: {
    template: '<span class="icon-link">🔗</span>'
  }
}

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
        },
        global: {
          stubs: globalStubs
        }
      })

      expect(wrapper.find('.platform-status-tag').exists()).toBe(true)
      expect(wrapper.find('.status-badge').exists()).toBe(true)
      expect(wrapper.find('.status-badge.available').exists()).toBe(true)
    })

    it('should render REQUIRES_MINI_PROGRAM status with warning', () => {
      const wrapper = mount(PlatformStatusTag, {
        props: {
          status: 'REQUIRES_MINI_PROGRAM',
          platform: 'wechat',
          reason: '需要小程序授权'
        },
        global: {
          stubs: globalStubs
        }
      })

      expect(wrapper.find('.platform-status-tag').exists()).toBe(true)
      expect(wrapper.find('.status-badge').exists()).toBe(true)
    })

    it('should render REQUIRES_APP status correctly', () => {
      const wrapper = mount(PlatformStatusTag, {
        props: {
          status: 'REQUIRES_APP',
          platform: 'apple',
          reason: '需要 iOS 应用'
        },
        global: {
          stubs: globalStubs
        }
      })

      expect(wrapper.find('.platform-status-tag').exists()).toBe(true)
      expect(wrapper.find('.status-badge.requires-app').exists()).toBe(true)
    })

    it('should render COMING_SOON status', () => {
      const wrapper = mount(PlatformStatusTag, {
        props: {
          status: 'COMING_SOON',
          platform: 'xiaomi',
          reason: '服务开发中'
        },
        global: {
          stubs: globalStubs
        }
      })

      expect(wrapper.find('.platform-status-tag').exists()).toBe(true)
      expect(wrapper.find('.status-badge.coming-soon').exists()).toBe(true)
    })
  })

  describe('dialog interaction', () => {
    it('should show dialog when clicked on disabled tag', async () => {
      const wrapper = mount(PlatformStatusTag, {
        props: {
          status: 'REQUIRES_MINI_PROGRAM',
          platform: 'wechat'
        },
        global: {
          stubs: globalStubs
        }
      })

      // Initially dialog should not be visible
      expect(wrapper.find('.el-dialog').exists()).toBe(false)

      // Click on the status badge
      await wrapper.find('.status-badge').trigger('click')

      // Dialog should be visible after click
      expect(wrapper.find('.el-dialog').exists()).toBe(true)
    })
  })
})