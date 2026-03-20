<template>
  <div class="push-settings-page">
    <el-row :gutter="20">
      <el-col :xs="24" :lg="16">
        <!-- 推送通道配置 -->
        <el-card v-loading="pushStore.loading">
          <template #header>
            <div class="card-header">
              <span>推送通道配置</span>
              <el-button type="primary" text @click="pushStore.loadConfigs()">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>

          <div class="channel-list">
            <div v-for="config in pushStore.configs" :key="config.channelType" class="channel-item">
              <div class="channel-header">
                <div class="channel-info">
                  <el-icon :size="24" class="channel-icon">
                    <component :is="getChannelIcon(config.channelType)" />
                  </el-icon>
                  <div>
                    <div class="channel-name">{{ config.channelLabel }}</div>
                    <div class="channel-desc">
                      {{ getChannelDesc(config.channelType) }}
                    </div>
                  </div>
                </div>
                <el-switch v-model="config.enabled" @change="handleToggle(config)" />
              </div>

              <el-collapse-transition>
                <div v-if="config.offlineSupported" class="channel-config">
                  <el-form
                    :model="{ configValue: config.configValue }"
                    label-width="100px"
                    class="config-form"
                  >
                    <el-form-item :label="getConfigLabel(config.channelType)">
                      <el-input
                        v-model="config.configValue"
                        :placeholder="getConfigPlaceholder(config.channelType)"
                        clearable
                        @blur="handleSaveConfig(config)"
                      />
                    </el-form-item>
                    <el-form-item v-if="config.enabled">
                      <el-button
                        type="primary"
                        size="small"
                        :loading="pushStore.testing === config.channelType"
                        @click="handleTest(config.channelType)"
                      >
                        测试推送
                      </el-button>
                    </el-form-item>
                  </el-form>
                </div>
              </el-collapse-transition>

              <div class="channel-status">
                <!-- WebSocket: 显示启用状态和连接状态 -->
                <template v-if="config.channelType === 'WEBSOCKET'">
                  <el-tag :type="config.enabled ? 'success' : 'info'" size="small">
                    {{ config.enabled ? '推送已启用' : '推送已禁用' }}
                  </el-tag>
                  <el-tag :type="realtimeStore.connected ? 'success' : 'warning'" size="small">
                    {{ realtimeStore.connected ? '已连接' : '未连接' }}
                  </el-tag>
                </template>
                <!-- 其他通道: 显示配置状态 -->
                <template v-else>
                  <el-tag :type="config.isConfigured ? 'success' : 'info'" size="small">
                    {{ config.isConfigured ? '已配置' : '未配置' }}
                  </el-tag>
                  <el-tag v-if="config.offlineSupported" type="warning" size="small">
                    支持离线推送
                  </el-tag>
                </template>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <!-- 推送统计 -->
        <el-card>
          <template #header>
            <span>推送统计 (24小时)</span>
          </template>

          <div v-if="pushStore.stats" class="stats-content">
            <div v-for="(stat, index) in pushStore.stats.channels" :key="index" class="stat-row">
              <span class="stat-channel">{{ stat.channelType }}</span>
              <span class="stat-status">{{ stat.status }}</span>
              <span class="stat-count">{{ stat.count }}</span>
            </div>
            <el-empty v-if="!pushStore.stats.channels?.length" description="暂无数据" />
          </div>
          <el-empty v-else description="加载中..." />
        </el-card>

        <!-- 推送历史 -->
        <el-card class="history-card">
          <template #header>
            <div class="card-header">
              <span>最近推送</span>
              <el-button type="primary" text size="small" @click="pushStore.loadHistory()">
                刷新
              </el-button>
            </div>
          </template>

          <el-timeline v-if="pushStore.history.length">
            <el-timeline-item
              v-for="item in pushStore.history.slice(0, 10)"
              :key="item.id"
              :timestamp="formatDateTime(item.pushedAt)"
              placement="top"
              :type="getTimelineType(item.status)"
            >
              <div class="history-item">
                <div class="history-header">
                  <span class="history-channel">{{ item.channelLabel }}</span>
                  <el-tag :type="getStatusType(item.status)" size="small">
                    {{ getStatusLabel(item.status) }}
                  </el-tag>
                </div>
                <div class="history-message">{{ item.message || '无消息' }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无推送记录" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { usePushStore } from '@/stores/push'
import { useRealtimeStore } from '@/stores/realtime'
import { formatDateTime } from '@/utils/format'
import type { PushConfig } from '@/api/push'
import { ElMessage } from 'element-plus'
import { Bell, Message, ChatDotRound, Connection, Refresh } from '@element-plus/icons-vue'

const pushStore = usePushStore()
const realtimeStore = useRealtimeStore()

onMounted(() => {
  pushStore.loadConfigs()
  pushStore.loadStats()
  pushStore.loadHistory()
})

function getChannelIcon(type: string) {
  const icons: Record<string, typeof Bell> = {
    WEBSOCKET: Connection,
    EMAIL: Message,
    WECOM: ChatDotRound,
    FEISHU: ChatDotRound
  }
  return icons[type] || Bell
}

function getChannelDesc(type: string) {
  const descs: Record<string, string> = {
    WEBSOCKET: '实时在线推送。开关控制是否接收告警推送，连接状态在"实时监控"页面管理',
    EMAIL: '邮件通知，支持离线推送',
    WECOM: '企业微信机器人，支持离线推送',
    FEISHU: '飞书机器人，支持离线推送'
  }
  return descs[type] || ''
}

function getConfigLabel(type: string) {
  const labels: Record<string, string> = {
    EMAIL: '邮箱地址',
    WECOM: 'Webhook URL',
    FEISHU: 'Webhook URL'
  }
  return labels[type] || '配置值'
}

function getConfigPlaceholder(type: string) {
  const placeholders: Record<string, string> = {
    EMAIL: '请输入接收通知的邮箱地址',
    WECOM: '请输入企业微信机器人 Webhook 地址',
    FEISHU: '请输入飞书机器人 Webhook 地址'
  }
  return placeholders[type] || '请输入配置值'
}

async function handleToggle(config: PushConfig) {
  // 离线通道启用时必须有配置值（WebSocket 是实时通道，不需要配置值）
  if (config.enabled && config.offlineSupported && !config.configValue) {
    ElMessage.warning(`请先输入${getConfigLabel(config.channelType)}`)
    config.enabled = false
    return
  }

  try {
    await pushStore.saveConfig(config.channelType, {
      enabled: config.enabled,
      configValue: config.configValue
    })
    if (config.enabled) {
      ElMessage.success(`${config.channelLabel}已启用`)
    } else {
      ElMessage.info(`${config.channelLabel}已禁用`)
    }
  } catch (error: unknown) {
    config.enabled = !config.enabled
    const errorMessage = error instanceof Error ? error.message : '保存失败'
    ElMessage.error(errorMessage)
  }
}

async function handleSaveConfig(config: PushConfig) {
  if (config.configValue) {
    try {
      await pushStore.saveConfig(config.channelType, {
        configValue: config.configValue
      })
      ElMessage.success('配置已保存')
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : '保存失败'
      ElMessage.error(errorMessage)
    }
  }
}

async function handleTest(channelType: string) {
  await pushStore.testChannel(channelType)
}

function getTimelineType(status: string) {
  const types: Record<string, string> = {
    SUCCESS: 'success',
    FAILED: 'danger',
    SKIPPED: 'info'
  }
  return types[status] || 'info'
}

function getStatusType(status: string) {
  const types: Record<string, 'success' | 'danger' | 'info'> = {
    SUCCESS: 'success',
    FAILED: 'danger',
    SKIPPED: 'info'
  }
  return types[status] || 'info'
}

function getStatusLabel(status: string) {
  const labels: Record<string, string> = {
    SUCCESS: '成功',
    FAILED: '失败',
    SKIPPED: '跳过'
  }
  return labels[status] || status
}
</script>

<style scoped>
.push-settings-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.channel-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.channel-item {
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafafa;
}

.channel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.channel-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.channel-icon {
  color: #409eff;
}

.channel-name {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.channel-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.channel-config {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed #e4e7ed;
}

.config-form {
  margin-bottom: 0;
}

.channel-status {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.stats-content {
  max-height: 200px;
  overflow-y: auto;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
}

.stat-row:last-child {
  border-bottom: none;
}

.stat-channel {
  color: #606266;
}

.stat-status {
  color: #909399;
}

.stat-count {
  font-weight: 500;
  color: #409eff;
}

.history-card {
  margin-top: 20px;
}

.history-item {
  padding: 4px 0;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.history-channel {
  font-weight: 500;
  color: #303133;
}

.history-message {
  font-size: 12px;
  color: #909399;
}
</style>
