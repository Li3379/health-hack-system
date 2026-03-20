<template>
  <div class="oauth-callback-page">
    <el-card shadow="hover" class="callback-card">
      <!-- Step Progress -->
      <el-steps :active="currentStep" finish-status="success" align-center class="progress-steps">
        <el-step title="发起连接" description="正在获取授权" />
        <el-step title="用户授权" description="请在平台完成授权" />
        <el-step title="连接中" description="正在处理授权" />
        <el-step title="完成" :description="stepDescription" />
      </el-steps>

      <!-- Content -->
      <div class="callback-content">
        <!-- Loading State -->
        <template v-if="isLoading">
          <el-icon class="is-loading" :size="48"><Loading /></el-icon>
          <p class="status-text">{{ statusText }}</p>
        </template>

        <!-- Success State -->
        <template v-else-if="isSuccess">
          <el-icon :size="64" color="#67c23a"><CircleCheckFilled /></el-icon>
          <h3>连接成功</h3>
          <p>{{ platformName }} 已成功连接</p>
          <p class="redirect-hint">即将返回...</p>
        </template>

        <!-- Error State -->
        <template v-else-if="isError">
          <el-icon :size="64" color="#f56c6c"><CircleCloseFilled /></el-icon>
          <h3>连接失败</h3>
          <el-alert
            :title="errorTitle"
            type="error"
            :description="errorMessage"
            show-icon
            class="error-alert"
          />
          <div class="solution-guide">
            <p><strong>解决方法：</strong></p>
            <ol>
              <li v-if="errorCode === 'access_denied'">您取消了授权，请重试并同意授权</li>
              <li v-else-if="errorCode === 'invalid_state'">授权已过期，请重新发起连接</li>
              <li v-else-if="errorCode === 'token_exchange_failed'">
                授权服务暂时不可用，请稍后重试
              </li>
              <li v-else>请检查网络连接后重试，或联系技术支持</li>
            </ol>
          </div>
        </template>

        <!-- Timeout State -->
        <template v-else-if="isTimeout">
          <el-icon :size="64" color="#e6a23c"><WarningFilled /></el-icon>
          <h3>等待超时</h3>
          <p>授权处理时间过长</p>
        </template>
      </div>

      <!-- Actions -->
      <div class="callback-actions">
        <el-button v-if="isError" type="primary" @click="handleRetry">
          <el-icon><Refresh /></el-icon>
          重新连接
        </el-button>
        <el-button v-if="isError || isTimeout" @click="handleBack">
          <el-icon><Back /></el-icon>
          返回
        </el-button>
      </div>
    </el-card>

    <!-- Timeout Dialog -->
    <el-dialog
      v-model="showTimeoutDialog"
      title="等待超时"
      width="400px"
      :close-on-click-modal="false"
    >
      <p>授权处理时间较长，是否继续等待？</p>
      <template #footer>
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" @click="handleContinueWait">继续等待</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Loading,
  CircleCheckFilled,
  CircleCloseFilled,
  WarningFilled,
  Refresh,
  Back
} from '@element-plus/icons-vue'
import { deviceApi } from '@/api/device'
import { PLATFORM_NAMES } from '@/types/platform'

const route = useRoute()
const router = useRouter()

// State
const currentStep = ref(0)
const isLoading = ref(true)
const isSuccess = ref(false)
const isError = ref(false)
const isTimeout = ref(false)
const showTimeoutDialog = ref(false)

const errorCode = ref<string | null>(null)
const errorTitle = ref('')
const errorMessage = ref('')
const statusText = ref('正在处理授权...')
const stepDescription = ref('')

// OAuth state from session storage
const oauthPlatform = ref(sessionStorage.getItem('oauth_platform') || '')
const oauthTimestamp = ref(parseInt(sessionStorage.getItem('oauth_timestamp') || '0'))

// Timeout tracking
const TIMEOUT_MS = 60000 // 60 seconds
const startTime = ref(Date.now())
let timeoutCheck: ReturnType<typeof setInterval> | null = null

// Computed
const platformName = computed(() => {
  return PLATFORM_NAMES[oauthPlatform.value] || oauthPlatform.value
})

// Step progression
const updateStep = (step: number, text?: string) => {
  currentStep.value = step
  if (text) {
    statusText.value = text
  }
}

// Handle OAuth callback
const handleCallback = async () => {
  const platform = route.params.platform as string
  const code = route.query.code as string
  const state = route.query.state as string
  const error = route.query.error as string
  const errorDescription = route.query.error_description as string

  // Validate platform
  if (!platform) {
    handleError('invalid_platform', '无效的平台', '未指定设备平台')
    return
  }

  // Update OAuth platform from route if not in session
  if (!oauthPlatform.value) {
    oauthPlatform.value = platform
  }

  // Check for OAuth error from provider
  if (error) {
    handleError(error, '授权被拒绝', errorDescription || '用户取消了授权')
    return
  }

  // Validate required parameters
  if (!code || !state) {
    handleError('missing_params', '参数缺失', '授权回调缺少必要参数')
    return
  }

  // Check timestamp for timeout
  const elapsed = Date.now() - oauthTimestamp.value
  if (elapsed > TIMEOUT_MS) {
    handleTimeout()
    return
  }

  updateStep(2, '正在交换授权令牌...')

  try {
    // The callback is handled by the backend via redirect
    // Here we just need to verify the connection was successful
    // The backend has already processed the callback

    // Check connection status
    await checkConnectionStatus(platform)
  } catch (err: any) {
    handleError('connection_failed', '连接失败', err.message || '无法完成设备连接')
  }
}

// Check if connection was successful
const checkConnectionStatus = async (platform: string) => {
  updateStep(2, '正在验证连接状态...')

  try {
    const res = await deviceApi.getConnections()
    if (res.code === 200) {
      const connection = res.data.find(d => d.platform === platform)
      if (connection && connection.status === 'connected') {
        handleSuccess()
      } else {
        // Connection not yet complete, poll for status
        await pollConnectionStatus(platform)
      }
    } else {
      handleError('check_failed', '检查失败', res.message || '无法检查连接状态')
    }
  } catch (err: any) {
    handleError('check_failed', '检查失败', err.message || '无法检查连接状态')
  }
}

// Poll for connection status
const pollConnectionStatus = async (platform: string) => {
  let attempts = 0
  const maxAttempts = 10 // 10 seconds

  const poll = async () => {
    attempts++
    try {
      const res = await deviceApi.getConnections()
      if (res.code === 200) {
        const connection = res.data.find(d => d.platform === platform)
        if (connection && connection.status === 'connected') {
          handleSuccess()
          return
        }
      }

      if (attempts < maxAttempts) {
        statusText.value = `正在等待连接确认... (${attempts}/${maxAttempts})`
        setTimeout(poll, 1000)
      } else {
        handleError('timeout', '连接超时', '设备连接确认超时，请稍后查看连接状态')
      }
    } catch (err) {
      if (attempts < maxAttempts) {
        setTimeout(poll, 1000)
      } else {
        handleError('poll_failed', '检查失败', '无法确认连接状态')
      }
    }
  }

  await poll()
}

// Handle success
const handleSuccess = () => {
  updateStep(3, '连接成功')
  isLoading.value = false
  isSuccess.value = true
  stepDescription.value = '设备已连接'

  // Clear session storage
  sessionStorage.removeItem('oauth_platform')
  sessionStorage.removeItem('oauth_timestamp')

  ElMessage.success(`${platformName.value} 连接成功`)

  // Redirect back to data-input page after delay
  setTimeout(() => {
    router.push('/data-input')
  }, 2000)
}

// Handle error
const handleError = (code: string, title: string, message: string) => {
  isLoading.value = false
  isError.value = true
  errorCode.value = code
  errorTitle.value = title
  errorMessage.value = message
  currentStep.value = 3
  stepDescription.value = '连接失败'

  // Clear session storage
  sessionStorage.removeItem('oauth_platform')
  sessionStorage.removeItem('oauth_timestamp')
}

// Handle timeout
const handleTimeout = () => {
  isLoading.value = false
  isTimeout.value = true
  showTimeoutDialog.value = true
}

// Handle retry
const handleRetry = () => {
  router.push('/data-input')
}

// Handle back
const handleBack = () => {
  router.push('/data-input')
}

// Handle continue waiting
const handleContinueWait = () => {
  showTimeoutDialog.value = false
  isLoading.value = true
  isTimeout.value = false
  startTime.value = Date.now()

  // Re-check connection status
  if (oauthPlatform.value) {
    checkConnectionStatus(oauthPlatform.value)
  }
}

// Handle cancel
const handleCancel = () => {
  showTimeoutDialog.value = false
  sessionStorage.removeItem('oauth_platform')
  sessionStorage.removeItem('oauth_timestamp')
  router.push('/data-input')
}

// Lifecycle
onMounted(() => {
  updateStep(1, '正在处理授权回调...')
  handleCallback()

  // Start timeout check
  timeoutCheck = setInterval(() => {
    const elapsed = Date.now() - startTime.value
    if (elapsed > TIMEOUT_MS && isLoading.value) {
      handleTimeout()
    }
  }, 5000)
})

// Cleanup
import { onUnmounted } from 'vue'
onUnmounted(() => {
  if (timeoutCheck) {
    clearInterval(timeoutCheck)
  }
})
</script>

<style scoped>
.oauth-callback-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--color-bg-secondary, #f5f7fa);
  padding: 20px;
}

.callback-card {
  width: 100%;
  max-width: 600px;
}

.progress-steps {
  margin-bottom: 40px;
}

.callback-content {
  text-align: center;
  padding: 40px 20px;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.status-text {
  margin-top: 16px;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.callback-content h3 {
  margin-top: 16px;
  margin-bottom: 8px;
  color: var(--color-text-primary);
}

.redirect-hint {
  color: var(--color-text-tertiary);
  font-size: 12px;
  margin-top: 8px;
}

.error-alert {
  margin-top: 16px;
  text-align: left;
}

.solution-guide {
  margin-top: 20px;
  text-align: left;
  padding: 16px;
  background: var(--color-bg-secondary, #f5f7fa);
  border-radius: 8px;
}

.solution-guide ol {
  padding-left: 20px;
  margin-top: 8px;
}

.solution-guide li {
  margin: 4px 0;
}

.callback-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
}
</style>
