/// <reference types="vitest/globals" />
import { config } from '@vue/test-utils'

// Mock Element Plus globally
config.global.mocks = {
  $message: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn()
  }
}
