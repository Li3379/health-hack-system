<template>
  <div class="platform-status-tag">
    <el-tooltip
      :content="tooltipContent"
      placement="top"
      :disabled="!showTooltip"
      effect="dark"
      popper-class="device-tooltip"
    >
      <div
        class="status-badge"
        :class="[status.toLowerCase().replace('_', '-'), { clickable: isDisabled }]"
        @click="handleClick"
      >
        <span v-if="isDisabled" class="status-icon">
          <el-icon :size="12"><Warning /></el-icon>
        </span>
        <span class="status-text">{{ tagConfig.label }}</span>
      </div>
    </el-tooltip>

    <!-- Details Dialog -->
    <el-dialog
      v-model="showDialog"
      :title="platformName + ' 说明'"
      width="500px"
      destroy-on-close
      class="platform-status-dialog"
    >
      <div class="dialog-content">
        <el-alert
          v-if="status === 'REQUIRES_MINI_PROGRAM'"
          type="warning"
          :closable="false"
          show-icon
        >
          <template #title>微信运动需要小程序授权</template>
          微信运动目前不提供网页 OAuth 授权方式，需要通过微信小程序获取数据。
        </el-alert>

        <el-alert v-else-if="status === 'REQUIRES_APP'" type="warning" :closable="false" show-icon>
          <template #title>Apple Health 需要 iOS 应用</template>
          Apple Health 数据获取需要 iOS 应用集成 HealthKit，不支持网页授权。
        </el-alert>

        <el-alert v-else-if="status === 'COMING_SOON'" type="info" :closable="false" show-icon>
          <template #title>即将支持</template>
          该平台服务正在开发中，敬请期待。
        </el-alert>

        <el-alert v-else-if="status === 'NOT_CONFIGURED'" type="error" :closable="false" show-icon>
          <template #title>平台未配置</template>
          {{ reason || '该平台 OAuth 未配置，请联系管理员。' }}
        </el-alert>

        <el-alert v-else-if="status === 'UNAVAILABLE'" type="error" :closable="false" show-icon>
          <template #title>平台暂不可用</template>
          {{ reason || '该平台服务暂时不可用，请稍后再试。' }}
        </el-alert>

        <!-- Guide Link -->
        <div
          v-if="guideUrl && (status === 'REQUIRES_MINI_PROGRAM' || status === 'REQUIRES_APP')"
          class="guide-section"
        >
          <el-divider />
          <p class="guide-hint">了解更多：</p>
          <el-link :href="guideUrl" target="_blank" type="primary">
            <el-icon><Link /></el-icon>
            {{ status === 'REQUIRES_MINI_PROGRAM' ? '微信运动小程序' : 'Apple Health 开发指南' }}
          </el-link>
        </div>

        <!-- Supported Data Types -->
        <div v-if="(supportedDataTypes?.length ?? 0) > 0" class="data-types-section">
          <el-divider />
          <p class="data-types-hint">支持的数据类型：</p>
          <div class="data-types-tags">
            <span v-for="type in supportedDataTypes" :key="type" class="data-type-pill">
              {{ getTypeName(type) }}
            </span>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="showDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Warning, Link } from '@element-plus/icons-vue'
import {
  type PlatformStatus,
  STATUS_CONFIG,
  PLATFORM_NAMES,
  DATA_TYPE_NAMES
} from '@/types/platform'

const props = defineProps<{
  status: PlatformStatus
  platform?: string
  reason?: string
  guideUrl?: string
  supportedDataTypes?: string[]
}>()

const showDialog = ref(false)

// Computed
const tagConfig = computed(() => {
  return STATUS_CONFIG[props.status] || STATUS_CONFIG.UNAVAILABLE
})

const isDisabled = computed(() => {
  return props.status !== 'AVAILABLE'
})

const showTooltip = computed(() => {
  return props.status !== 'AVAILABLE' && props.reason
})

const tooltipContent = computed(() => {
  return props.reason || tagConfig.value.label
})

const platformName = computed(() => {
  return props.platform ? PLATFORM_NAMES[props.platform] || props.platform : ''
})

// Methods
const handleClick = () => {
  if (isDisabled.value) {
    showDialog.value = true
  }
}

const getTypeName = (type: string) => {
  return DATA_TYPE_NAMES[type] || type
}
</script>

<style scoped>
/* ============================================
   Platform Status Badge - Medical System Design
   ============================================ */
.platform-status-tag {
  display: inline-flex;
  align-items: center;
}

/* Status Badge Base */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  white-space: nowrap;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}

.status-badge.clickable {
  cursor: pointer;
}

.status-badge.clickable:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

/* Status Icon */
.status-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
}

/* Status Variants - Health System Color Palette */

/* Available - Success Green */
.status-badge.available {
  background: rgba(16, 185, 129, 0.1);
  color: #059669;
  border-color: rgba(16, 185, 129, 0.2);
}

/* Not Configured - Alert Red */
.status-badge.not-configured {
  background: rgba(239, 68, 68, 0.08);
  color: #dc2626;
  border-color: rgba(239, 68, 68, 0.2);
}

.status-badge.not-configured .status-icon {
  background: rgba(239, 68, 68, 0.15);
  color: #dc2626;
}

/* Requires Mini Program - Warning Amber */
.status-badge.requires-mini-program {
  background: rgba(245, 158, 11, 0.08);
  color: #d97706;
  border-color: rgba(245, 158, 11, 0.2);
}

.status-badge.requires-mini-program .status-icon {
  background: rgba(245, 158, 11, 0.15);
  color: #d97706;
}

/* Requires App - Warning Amber */
.status-badge.requires-app {
  background: rgba(245, 158, 11, 0.08);
  color: #d97706;
  border-color: rgba(245, 158, 11, 0.2);
}

.status-badge.requires-app .status-icon {
  background: rgba(245, 158, 11, 0.15);
  color: #d97706;
}

/* Coming Soon - Info Blue */
.status-badge.coming-soon {
  background: rgba(59, 130, 246, 0.08);
  color: #2563eb;
  border-color: rgba(59, 130, 246, 0.2);
}

.status-badge.coming-soon .status-icon {
  background: rgba(59, 130, 246, 0.15);
  color: #2563eb;
}

/* Unavailable - Neutral Gray */
.status-badge.unavailable {
  background: rgba(107, 114, 128, 0.08);
  color: #4b5563;
  border-color: rgba(107, 114, 128, 0.2);
}

.status-badge.unavailable .status-icon {
  background: rgba(107, 114, 128, 0.15);
  color: #4b5563;
}

/* Dark Mode Support */
[data-theme='dark'] .status-badge.not-configured {
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border-color: rgba(239, 68, 68, 0.3);
}

[data-theme='dark'] .status-badge.requires-mini-program,
[data-theme='dark'] .status-badge.requires-app {
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
  border-color: rgba(245, 158, 11, 0.3);
}

[data-theme='dark'] .status-badge.coming-soon {
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
  border-color: rgba(59, 130, 246, 0.3);
}

[data-theme='dark'] .status-badge.available {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
  border-color: rgba(16, 185, 129, 0.3);
}

/* ============================================
   Dialog Styles
   ============================================ */
.dialog-content {
  padding: 0 8px;
  overflow: hidden;
}

.dialog-content :deep(.el-alert) {
  word-break: break-word;
}

.dialog-content :deep(.el-alert__title) {
  word-break: break-word;
  line-height: 1.5;
  font-weight: 600;
}

.dialog-content :deep(.el-alert__description) {
  word-break: break-word;
  line-height: 1.6;
  margin-top: 8px;
  color: var(--color-text-secondary);
}

.dialog-content :deep(.el-alert__content) {
  overflow: hidden;
}

.guide-section,
.data-types-section {
  margin-top: 16px;
}

.guide-hint,
.data-types-hint {
  margin-bottom: 8px;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.data-types-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.data-type-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  background: var(--color-bg-tertiary);
  border-radius: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
}
</style>
