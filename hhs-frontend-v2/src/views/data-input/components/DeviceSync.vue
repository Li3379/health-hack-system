<template>
  <el-card shadow="hover" class="device-sync-card">
    <template #header>
      <div class="card-header">
        <el-icon><Connection /></el-icon>
        <span>穿戴设备同步</span>
        <el-button text type="primary" size="small" class="history-btn" @click="openSyncHistory">
          查看同步历史
        </el-button>
        <el-tooltip content="配置帮助" placement="top" effect="dark" popper-class="device-tooltip">
          <el-button text type="info" size="small" @click="showConfigHelp = true">
            <el-icon><QuestionFilled /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </template>

    <!-- Offline Alert -->
    <el-alert
      v-if="!isOnline"
      title="网络已断开"
      type="warning"
      description="当前网络连接已断开，设备同步功能暂时不可用。请检查网络连接后重试。"
      show-icon
      closable
      class="offline-alert"
    />

    <div class="device-list">
      <div v-for="device in enrichedDevices" :key="device.platform" class="device-item">
        <div class="device-info">
          <el-avatar
            :size="40"
            class="device-avatar"
            :style="{ background: getDeviceColor(device.platform) }"
          >
            {{ getDeviceIcon(device.platform) }}
          </el-avatar>
          <div class="device-details">
            <div class="device-name-row">
              <span class="device-name">{{ device.platformName }}</span>
              <PlatformStatusTag
                v-if="device.platformMetadata && device.platformMetadata.status !== 'AVAILABLE'"
                :status="device.platformMetadata.status"
                :platform="device.platform"
                :reason="device.platformMetadata.unavailableReason"
                :guide-url="device.platformMetadata.guideUrl"
                :supported-data-types="device.platformMetadata.supportedDataTypes"
              />
            </div>
            <div class="device-meta">
              <span class="device-status" :class="device.status">
                {{ device.statusName }}
              </span>
              <span v-if="device.lastSyncAt" class="sync-time">
                上次同步: {{ formatSyncTime(device.lastSyncAt) }}
              </span>
            </div>
          </div>
        </div>

        <div class="device-actions">
          <template v-if="device.status === 'connected'">
            <el-button
              type="primary"
              size="small"
              :loading="syncingPlatform === device.platform"
              :disabled="syncingPlatform !== null && syncingPlatform !== device.platform"
              @click="handleSync(device.platform)"
            >
              同步
            </el-button>
            <el-button
              type="danger"
              size="small"
              text
              :disabled="syncingPlatform === device.platform"
              @click="handleDisconnect(device.platform)"
            >
              断开
            </el-button>
          </template>
          <el-tooltip
            v-else-if="!isPlatformConnectable(device.platform)"
            :content="getConnectDisabledReason(device.platform)"
            placement="top"
            effect="dark"
            popper-class="device-tooltip"
          >
            <el-button type="primary" size="small" plain disabled>连接</el-button>
          </el-tooltip>
          <el-button
            v-else
            type="primary"
            size="small"
            plain
            :loading="connectingPlatform === device.platform"
            @click="handleConnect(device.platform)"
          >
            连接
          </el-button>
        </div>
      </div>
    </div>

    <div class="sync-actions">
      <el-button
        type="primary"
        :loading="syncingAll"
        :disabled="connectedCount === 0 || syncingPlatform !== null"
        @click="handleSyncAll"
      >
        同步全部 ({{ connectedCount }})
      </el-button>
    </div>

    <!-- Sync Error Display -->
    <el-alert
      v-if="lastSyncError"
      :title="lastSyncError.title"
      type="error"
      :description="lastSyncError.message"
      show-icon
      closable
      class="sync-error-alert"
      @close="lastSyncError = null"
    >
      <template #default>
        <div class="error-content">
          <span>{{ lastSyncError.message }}</span>
          <el-button size="small" type="danger" text @click="retrySync(lastSyncError.platform)">
            重试
          </el-button>
        </div>
      </template>
    </el-alert>

    <!-- Sync History Dialog -->
    <el-dialog v-model="showSyncHistory" title="同步历史" width="600px" destroy-on-close>
      <el-table v-loading="loadingHistory" :data="syncHistory" stripe>
        <el-table-column prop="platformName" label="平台" width="120" />
        <el-table-column prop="metricsCount" label="数据条数" width="100" align="center">
          <template #default="{ row }">
            {{ row.metricsCount ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="statusName" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="syncTime" label="同步时间">
          <template #default="{ row }">
            <div class="sync-time-cell">
              <span>{{ formatDateTime(row.syncTime) }}</span>
              <span v-if="row.durationMs" class="duration">({{ row.durationMs }}ms)</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" width="150">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.errorMessage"
              :content="row.errorMessage"
              placement="top"
              effect="dark"
              popper-class="device-tooltip"
            >
              <span class="error-text">{{ row.errorMessage }}</span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="historyPage"
          :page-size="10"
          :total="historyTotal"
          layout="total, prev, pager, next"
          @current-change="loadSyncHistory"
        />
      </div>
    </el-dialog>

    <!-- Configuration Help Dialog -->
    <el-dialog v-model="showConfigHelp" title="设备同步配置帮助" width="700px" destroy-on-close>
      <div class="config-help-content">
        <el-alert type="info" :closable="false" class="config-intro">
          <template #title>
            <strong>如何获取设备平台的 API 凭据？</strong>
          </template>
          点击下方平台名称查看详细配置步骤。
        </el-alert>

        <el-collapse v-model="activeConfigPanel" accordion>
          <el-collapse-item name="huawei">
            <template #title>
              <div class="config-item-title">
                <el-avatar :size="24" class="platform-avatar huawei">H</el-avatar>
                <span>华为运动健康</span>
                <el-tag type="success" size="small">推荐</el-tag>
              </div>
            </template>
            <div class="config-steps">
              <h4>配置步骤：</h4>
              <ol>
                <li>
                  访问
                  <a href="https://developer.huawei.com" target="_blank">华为开发者联盟</a>
                  并登录
                </li>
                <li>
                  进入
                  <a href="https://console.huawei.com/consumer" target="_blank">
                    AppGallery Connect
                  </a>
                  控制台
                </li>
                <li>创建项目和应用，选择「健康运动」类型</li>
                <li>
                  在「API管理」中开启
                  <strong>Health Kit</strong>
                  服务
                </li>
                <li>
                  配置 OAuth 回调地址：
                  <code>https://your-domain.com/api/device/callback/huawei</code>
                </li>
                <li>
                  在「项目设置」获取
                  <strong>应用ID</strong>
                  和
                  <strong>应用密钥</strong>
                </li>
              </ol>
            </div>
          </el-collapse-item>

          <el-collapse-item name="xiaomi">
            <template #title>
              <div class="config-item-title">
                <el-avatar :size="24" class="platform-avatar xiaomi">M</el-avatar>
                <span>小米运动</span>
              </div>
            </template>
            <div class="config-steps">
              <h4>配置步骤：</h4>
              <ol>
                <li>
                  访问
                  <a href="https://dev.mi.com" target="_blank">小米开放平台</a>
                  并登录
                </li>
                <li>进入控制台创建应用，选择「健康运动类」</li>
                <li>申请开通「小米运动健康 API」</li>
                <li>
                  配置 OAuth 回调地址：
                  <code>https://your-domain.com/api/device/callback/xiaomi</code>
                </li>
                <li>
                  获取
                  <strong>App ID</strong>
                  和
                  <strong>App Secret</strong>
                </li>
              </ol>
            </div>
          </el-collapse-item>

          <el-collapse-item name="wechat">
            <template #title>
              <div class="config-item-title">
                <el-avatar :size="24" class="platform-avatar wechat">W</el-avatar>
                <span>微信运动</span>
                <el-tag type="warning" size="small">需小程序</el-tag>
              </div>
            </template>
            <div class="config-steps">
              <el-alert type="warning" :closable="false">
                微信运动目前不提供公开的 Web OAuth API，需要通过微信小程序获取数据。
              </el-alert>
            </div>
          </el-collapse-item>

          <el-collapse-item name="apple">
            <template #title>
              <div class="config-item-title">
                <el-avatar :size="24" class="platform-avatar apple">A</el-avatar>
                <span>Apple Health</span>
                <el-tag type="warning" size="small">需 iOS 应用</el-tag>
              </div>
            </template>
            <div class="config-steps">
              <el-alert type="warning" :closable="false">
                Apple Health 数据获取需要 iOS 应用，不支持纯 Web OAuth 流程。
              </el-alert>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <template #footer>
        <el-button type="primary" @click="showConfigHelp = false">我知道了</el-button>
      </template>
    </el-dialog>

    <!-- Configuration Wizard Dialog -->
    <el-dialog v-model="showConfigWizard" title="设备同步配置向导" width="650px" destroy-on-close>
      <DeviceConfigWizard @connect="handleWizardConnect" @close="showConfigWizard = false" />
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, QuestionFilled } from '@element-plus/icons-vue'
import { deviceApi } from '@/api/device'
import type { DeviceConnectionVO, SyncHistoryVO } from '@/types/api'
import type { PlatformMetadata } from '@/types/platform'
import { PLATFORM_ICONS, PLATFORM_COLORS, PLATFORM_NAMES } from '@/types/platform'
import DeviceConfigWizard from './DeviceConfigWizard.vue'
import PlatformStatusTag from '@/components/device/PlatformStatusTag.vue'

const emit = defineEmits<{
  (e: 'refresh'): void
}>()

// Network status
const isOnline = ref(navigator.onLine)
const offlineMessage = '网络连接已断开，请检查网络后重试'

// Device state
const devices = ref<DeviceConnectionVO[]>([])
const platformMetadata = ref<Map<string, PlatformMetadata>>(new Map())
const syncingPlatform = ref<string | null>(null)
const syncingAll = ref(false)
const connectingPlatform = ref<string | null>(null)

// Sync error state
const lastSyncError = ref<{ platform: string; title: string; message: string } | null>(null)

// Sync history state
const showSyncHistory = ref(false)
const syncHistory = ref<SyncHistoryVO[]>([])
const loadingHistory = ref(false)
const historyPage = ref(1)
const historyTotal = ref(0)

// Configuration help state
const showConfigHelp = ref(false)
const activeConfigPanel = ref('huawei')

// Configuration wizard state
const showConfigWizard = ref(false)

// Computed
const connectedCount = computed(() => {
  return devices.value.filter(d => d.status === 'connected').length
})

// Enrich devices with platform metadata
const enrichedDevices = computed(() => {
  return devices.value.map(device => ({
    ...device,
    platformMetadata: platformMetadata.value.get(device.platform)
  }))
})

// Network status handlers
const handleOnline = () => {
  isOnline.value = true
  ElMessage.success('网络已恢复')
}

const handleOffline = () => {
  isOnline.value = false
  ElMessage.warning('网络连接已断开')
}

// Check network before action
const checkNetwork = (): boolean => {
  if (!isOnline.value) {
    ElMessage.warning(offlineMessage)
    return false
  }
  return true
}

// Load devices
const loadDevices = async () => {
  try {
    const res = await deviceApi.getConnections()
    if (res.code === 200) {
      devices.value = res.data
    }
  } catch (error) {
    console.error('加载设备列表失败', error)
  }
}

// Load platform metadata
const loadPlatformMetadata = async () => {
  try {
    const res = await deviceApi.getPlatformMetadata()
    if (res.code === 200 && res.data) {
      const map = new Map<string, PlatformMetadata>()
      res.data.forEach(meta => {
        map.set(meta.platform, meta)
      })
      platformMetadata.value = map
    }
  } catch (error) {
    console.error('加载平台元数据失败', error)
  }
}

// Format sync time
const formatSyncTime = (time: string) => {
  if (!time) return ''

  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`

  return date.toLocaleDateString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// Format date time
const formatDateTime = (time: string) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// Check if platform is connectable
const isPlatformConnectable = (platform: string): boolean => {
  const meta = platformMetadata.value.get(platform)
  return meta?.isConnectable ?? false
}

// Get reason why platform is not connectable
const getConnectDisabledReason = (platform: string): string => {
  const meta = platformMetadata.value.get(platform)
  return meta?.unavailableReason || '该平台暂不可用'
}

// Handle connect - uses in-page navigation instead of popup
const handleConnect = async (platform: string) => {
  if (!checkNetwork()) return

  connectingPlatform.value = platform
  try {
    const res = await deviceApi.connect(platform)
    if (res.code === 200 && res.data) {
      // Store OAuth state in sessionStorage
      sessionStorage.setItem('oauth_platform', platform)
      sessionStorage.setItem('oauth_timestamp', Date.now().toString())

      // Use in-page navigation instead of popup
      window.location.href = res.data
    } else if (res.code === 400 && res.message?.includes('未配置')) {
      // Platform not configured, show configuration wizard
      showConfigWizard.value = true
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } catch (error: any) {
    // Check if it's a network error
    if (!isOnline.value || error.message?.includes('Network Error')) {
      ElMessage.error(offlineMessage)
    } else {
      ElMessage.error(error.message || '连接失败')
    }
  } finally {
    connectingPlatform.value = null
  }
}

// Handle connect from wizard
const handleWizardConnect = (platform: string) => {
  showConfigWizard.value = false
  handleConnect(platform)
}

// Handle disconnect
const handleDisconnect = async (platform: string) => {
  try {
    await ElMessageBox.confirm('确定要断开此设备的连接吗？', '提示', {
      type: 'warning'
    })

    const res = await deviceApi.disconnect(platform)
    if (res.code === 200) {
      ElMessage.success('已断开连接')
      loadDevices()
    } else {
      ElMessage.error(res.message || '断开失败')
    }
  } catch {
    // User cancelled
  }
}

// Handle sync
const handleSync = async (platform: string) => {
  if (!checkNetwork()) return

  syncingPlatform.value = platform
  lastSyncError.value = null

  try {
    const res = await deviceApi.sync(platform)
    if (res.code === 200) {
      const data = res.data
      if (data.status === 'success') {
        ElMessage.success(`同步成功，共 ${data.metricsCount || 0} 条数据`)
        emit('refresh')
        loadDevices() // Refresh to update lastSyncAt
      } else {
        lastSyncError.value = {
          platform,
          title: `${data.platformName} 同步失败`,
          message: data.errorMessage || '同步失败，请重试'
        }
        ElMessage.error(data.errorMessage || '同步失败')
      }
    } else {
      const device = devices.value.find(d => d.platform === platform)
      lastSyncError.value = {
        platform,
        title: `${device?.platformName || platform} 同步失败`,
        message: res.message || '同步失败'
      }
      ElMessage.error(res.message || '同步失败')
    }
  } catch (error: any) {
    const device = devices.value.find(d => d.platform === platform)
    // Check if it's a network error
    if (!isOnline.value || error.message?.includes('Network Error')) {
      lastSyncError.value = {
        platform,
        title: `${device?.platformName || platform} 网络错误`,
        message: offlineMessage
      }
      ElMessage.error(offlineMessage)
    } else {
      lastSyncError.value = {
        platform,
        title: `${device?.platformName || platform} 同步失败`,
        message: error.message || '同步失败'
      }
      ElMessage.error(error.message || '同步失败')
    }
  } finally {
    syncingPlatform.value = null
  }
}

// Retry sync
const retrySync = (platform: string) => {
  handleSync(platform)
}

// Handle sync all
const handleSyncAll = async () => {
  if (!checkNetwork()) return

  syncingAll.value = true
  lastSyncError.value = null

  try {
    const res = await deviceApi.syncAll()
    if (res.code === 200) {
      const results = res.data || []
      const successCount = results.filter(r => r.status === 'success').length
      const failedCount = results.length - successCount

      if (failedCount > 0) {
        const failed = results.find(r => r.status !== 'success')
        if (failed) {
          lastSyncError.value = {
            platform: failed.platform,
            title: `${failed.platformName} 同步失败`,
            message: failed.errorMessage || '同步失败'
          }
        }
        ElMessage.warning(`同步完成，成功 ${successCount} 个，失败 ${failedCount} 个`)
      } else {
        ElMessage.success(`同步完成，成功 ${successCount} 个设备`)
      }

      emit('refresh')
      loadDevices()
    }
  } catch (error: any) {
    // Check if it's a network error
    if (!isOnline.value || error.message?.includes('Network Error')) {
      ElMessage.error(offlineMessage)
    } else {
      ElMessage.error(error.message || '同步失败')
    }
  } finally {
    syncingAll.value = false
  }
}

// Open sync history dialog
const openSyncHistory = () => {
  showSyncHistory.value = true
  historyPage.value = 1
  loadSyncHistory()
}

// Load sync history
const loadSyncHistory = async (page = 1) => {
  loadingHistory.value = true
  try {
    const res = await deviceApi.getSyncHistory({ page, size: 10 })
    if (res.code === 200) {
      syncHistory.value = res.data.records || []
      historyTotal.value = res.data.total || 0
    }
  } catch (error) {
    console.error('加载同步历史失败', error)
    ElMessage.error('加载同步历史失败')
  } finally {
    loadingHistory.value = false
  }
}

// Get device icon
const getDeviceIcon = (platform: string) => {
  return PLATFORM_ICONS[platform] || platform[0].toUpperCase()
}

// Get device color
const getDeviceColor = (platform: string) => {
  return PLATFORM_COLORS[platform] || 'var(--color-primary)'
}

onMounted(() => {
  // Add network status listeners
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)

  // Load devices and platform metadata
  loadDevices()
  loadPlatformMetadata()
})

onUnmounted(() => {
  // Clean up network status listeners
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
})
</script>

<style scoped>
.device-sync-card {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.history-btn {
  margin-left: auto;
}

.offline-alert {
  margin-bottom: 16px;
}

.device-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.device-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: var(--color-bg-secondary);
  border-radius: 8px;
}

.device-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.device-avatar {
  color: white;
  font-weight: 600;
}

.device-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.device-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.device-name {
  font-weight: 500;
}

.device-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.device-status {
  color: var(--color-text-secondary);
}

.device-status.connected {
  color: var(--color-success);
}

.device-status.disconnected {
  color: var(--color-text-tertiary);
}

.sync-time {
  color: var(--color-text-tertiary);
}

.device-actions {
  display: flex;
  gap: 8px;
}

.sync-actions {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}

.sync-error-alert {
  margin-top: 16px;
}

.error-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sync-time-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.duration {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.error-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
  max-width: 150px;
  color: var(--color-danger);
  font-size: 12px;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

/* Configuration Help Styles */
.config-help-content {
  max-height: 60vh;
  overflow-y: auto;
}

.config-intro {
  margin-bottom: 16px;
}

.config-intro :deep(.el-alert__title) {
  word-break: break-word;
  line-height: 1.5;
}

.config-intro :deep(.el-alert__content) {
  overflow: hidden;
}

.config-steps :deep(.el-alert) {
  word-break: break-word;
}

.config-steps :deep(.el-alert__content) {
  overflow: hidden;
}

.config-item-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.platform-avatar {
  font-size: 12px;
  font-weight: 600;
}

.platform-avatar.huawei {
  background: #cf0a2c;
}

.platform-avatar.xiaomi {
  background: #ff6700;
}

.platform-avatar.wechat {
  background: #07c160;
}

.platform-avatar.apple {
  background: #000;
}

.config-steps {
  padding: 8px 0;
}

.config-steps h4 {
  margin: 8px 0;
  color: var(--color-text-primary);
}

.config-steps ol {
  padding-left: 20px;
  line-height: 2;
}

.config-steps li {
  margin: 4px 0;
}

.config-steps a {
  color: var(--color-primary);
  text-decoration: none;
}

.config-steps a:hover {
  text-decoration: underline;
}
</style>
