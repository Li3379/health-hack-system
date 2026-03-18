<template>
  <el-card shadow="hover" class="device-sync-card">
    <template #header>
      <div class="card-header">
        <el-icon><Connection /></el-icon>
        <span>穿戴设备同步</span>
        <el-button
          text
          type="primary"
          size="small"
          class="history-btn"
          @click="openSyncHistory"
        >
          查看同步历史
        </el-button>
      </div>
    </template>

    <div class="device-list">
      <div v-for="device in devices" :key="device.platform" class="device-item">
        <div class="device-info">
          <el-avatar :size="40" class="device-avatar">
            {{ getDeviceIcon(device.platform) }}
          </el-avatar>
          <div class="device-details">
            <span class="device-name">{{ device.platformName }}</span>
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
          <el-button
            size="small"
            type="danger"
            text
            @click="retrySync(lastSyncError.platform)"
          >
            重试
          </el-button>
        </div>
      </template>
    </el-alert>

    <!-- Sync History Dialog -->
    <el-dialog
      v-model="showSyncHistory"
      title="同步历史"
      width="600px"
      destroy-on-close
    >
      <el-table :data="syncHistory" v-loading="loadingHistory" stripe>
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
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection } from '@element-plus/icons-vue'
import { deviceApi } from '@/api/device'
import type { DeviceConnectionVO, SyncHistoryVO } from '@/types/api'

const emit = defineEmits<{
  (e: 'refresh'): void
}>()

// Device state
const devices = ref<DeviceConnectionVO[]>([])
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

// Computed
const connectedCount = computed(() => {
  return devices.value.filter(d => d.status === 'connected').length
})

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

// Handle connect
const handleConnect = async (platform: string) => {
  connectingPlatform.value = platform
  try {
    const res = await deviceApi.connect(platform)
    if (res.code === 200 && res.data) {
      // Open authorization page
      window.open(res.data, '_blank', 'width=600,height=500')
      ElMessage.info('请在弹出窗口中完成授权')

      // Poll for connection status
      pollConnectionStatus(platform)
    }
  } catch (error: any) {
    ElMessage.error(error.message || '连接失败')
  } finally {
    connectingPlatform.value = null
  }
}

// Poll for connection status after OAuth
const pollConnectionStatus = async (platform: string) => {
  let attempts = 0
  const maxAttempts = 30 // 30 seconds max

  const poll = async () => {
    attempts++
    const res = await deviceApi.getConnections()
    if (res.code === 200) {
      const device = res.data.find(d => d.platform === platform)
      if (device?.status === 'connected') {
        devices.value = res.data
        ElMessage.success('设备连接成功')
        return
      }
    }

    if (attempts < maxAttempts) {
      setTimeout(poll, 1000)
    }
  }

  setTimeout(poll, 2000) // Start polling after 2 seconds
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
    lastSyncError.value = {
      platform,
      title: `${device?.platformName || platform} 同步失败`,
      message: error.message || '同步失败'
    }
    ElMessage.error(error.message || '同步失败')
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
    ElMessage.error(error.message || '同步失败')
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
  const icons: Record<string, string> = {
    huawei: 'H',
    xiaomi: 'M',
    wechat: 'W',
    apple: 'A'
  }
  return icons[platform] || platform[0].toUpperCase()
}

onMounted(() => {
  loadDevices()
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
  background: var(--color-primary);
  color: white;
  font-weight: 600;
}

.device-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
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
</style>