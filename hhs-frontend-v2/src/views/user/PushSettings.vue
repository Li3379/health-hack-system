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

          <!-- 加载失败：渲染错误态，不再让空列表冒充「加载中」（WR-09） -->
          <ErrorState
            v-if="pushStore.loadError"
            variant="server"
            title="无法加载推送配置"
            :description="pushStore.loadError"
            :retry="() => pushStore.loadConfigs()"
            retry-label="重新加载推送配置"
            :retrying="pushStore.loading"
          />

          <div v-else class="channel-list">
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
                    <!-- 每个通道自己的汇总块：只列本通道的键，不串到兄弟通道。
                         位置仍是 <el-form> 的第一个子节点（Contract 4.3）。 -->
                    <FormErrorSummary
                      :summary-items="channelSummaryItems(config.channelType)"
                      @focus-field="handleSummaryClick"
                    />
                    <el-form-item
                      :label="getConfigLabel(config.channelType)"
                      prop="configValue"
                      :data-field="channelField(config.channelType)"
                      :error="errors.fieldErrors[channelField(config.channelType)]"
                    >
                      <el-input
                        v-model="config.configValue"
                        :placeholder="getConfigPlaceholder(config.channelType)"
                        clearable
                        @blur="handleSaveConfig(config)"
                        @update:model-value="errors.clearField(channelField(config.channelType))"
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
import { onMounted, onUnmounted, reactive, watch } from 'vue'
import { staggerReveal, cleanupScrollTriggers } from '@/composables/useGsap'
import { usePushStore } from '@/stores/push'
import { useRealtimeStore } from '@/stores/realtime'
import { formatDateTime } from '@/utils/format'
import type { PushConfig } from '@/api/push'
import ErrorState from '@/components/common/ErrorState.vue'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import { useFormServerErrors } from '@/composables/useFormServerErrors'
import { ElMessage } from 'element-plus'
import { Bell, Message, ChatDotRound, Connection, Refresh } from '@element-plus/icons-vue'

const pushStore = usePushStore()
const realtimeStore = useRealtimeStore()

/** 上次成功保存的配置值，用于跳过「失焦但值未变」的冗余 PUT（IN-12）。 */
const lastSavedValues = new Map<string, string>()

/**
 * 通道键与字段名的分隔符。
 *
 * 本页每个通道各渲染一个 `el-form`，而 `PushConfigRequest` 的字段名恒为 `configValue` ——
 * 一个按裸字段名索引的投影表会把 A 通道的 `configValue` 错误标到 B 通道的输入框上。
 * 复合键 `{通道}::{字段}` 让两个通道的同名字段成为两条互不相干的条目。
 *
 * 分隔符是编译期常量，不来自任何后端字符串（T-14-44）：复合键因此不可能被外部内容污染。
 */
const CHANNEL_FIELD_SEPARATOR = '::'

/** 通道 → 该通道下某个字段的投影键。模板与 handler 共用，避免两处各拼一次。 */
const channelField = (channelType: string, field = 'configValue'): string =>
  `${channelType}${CHANNEL_FIELD_SEPARATOR}${field}`

/**
 * 复合键 → 汇总块里的可读标签。
 *
 * `fieldLabel` 不认识复合键（它会返回空串），若不覆盖，汇总条目会退化成只有后端消息。
 * 这里把每个通道映射回 `getConfigLabel` 自己的措辞，于是摘要读作
 * 「Webhook URL：配置内容不能超过 500 个字符」而不是「WECOM::configValue：…」。
 * 用 `reactive` 而非普通对象，是因为标签依赖服务端下发的 `channelLabel`，通道加载后才会齐。
 */
const channelLabels = reactive<Record<string, string>>({})

const syncChannelLabels = (): void => {
  for (const config of pushStore.configs) {
    channelLabels[channelField(config.channelType)] =
      `${config.channelLabel || config.channelType} ${getConfigLabel(config.channelType)}`
  }
}

/**
 * 一个投影实例，错误表的键是**复合键**（不是裸 `configValue`）。
 *
 * 不能改成「每个通道一个实例」：composable 必须在 setup 期同步创建，而通道列表是
 * `v-for` 出来的运行时数据，循环里创建 composable 会破坏 setup 期约束。
 * 一个实例 + 复合键是唯一同时满足两条约束的形状。
 */
const errors = reactive(
  useFormServerErrors({
    // 每个表单只有一个字段，顺序天然成立；列出四个枚举值只为让汇总顺序确定。
    fieldOrder: [
      channelField('EMAIL'),
      channelField('WECOM'),
      channelField('FEISHU'),
      channelField('WEBSOCKET')
    ],
    labels: channelLabels
  })
)

/**
 * 本通道自己的汇总条目。
 *
 * 必须过滤：`errors.summaryItems` 是全部通道共享的一张表，不过滤的话保存邮件通道失败
 * 会在企业微信通道里也弹出一份汇总 —— 那正是本 plan 要钉住的「不串通道」。
 */
const channelSummaryItems = (channelType: string) => {
  const prefix = `${channelType}${CHANNEL_FIELD_SEPARATOR}`
  return errors.summaryItems.filter(item => item.field.startsWith(prefix))
}

/** 汇总条目点击 → 聚焦对应控件；复合键由 composable 内部 CSS.escape 转义。 */
const handleSummaryClick = (field: string): void => {
  errors.focusField(field)
}

/**
 * 把 store 记录下来的保存失败投影到**本通道**的字段上。
 *
 * 呈现权仍归 store（它已经调用过 `showError`），因此这里**不**再调 `showError` ——
 * 否则同一次失败会有两条提示（D-02）。这里只做字段级投影，且只处理 `validation`：
 * 其余 kind 由 toast 承担，投影不该抢它们的呈现。
 */
const projectSaveError = (channelType: string): void => {
  const record = pushStore.saveError
  if (!record || record.channelType !== channelType) return
  const err = record.error
  if (err.kind !== 'validation') return

  // 后端的键是裸字段名，落到本通道的复合键上；未被绑定的键（如 channelType）
  // 同样进表，于是汇总仍能显示它们，只是没有内联落点。
  const next = { ...errors.fieldErrors }
  for (const [field, message] of Object.entries(err.fieldErrors)) {
    next[channelField(channelType, field)] = message
  }
  errors.setErrors(next)
}

onMounted(async () => {
  await pushStore.loadConfigs()
  // 加载成功后记住服务端值，避免用户仅聚焦/失焦就触发一次冗余保存。
  for (const config of pushStore.configs) {
    if (config.configValue && !config.configValue.includes('****')) {
      lastSavedValues.set(config.channelType, config.configValue)
    }
  }
  // 通道标签依赖服务端下发的 channelLabel，必须在这里补一次（刷新按钮走的也是这条路）。
  syncChannelLabels()
  pushStore.loadStats()
  pushStore.loadHistory()
  staggerReveal('.el-card', { stagger: 0.08, y: 16 })
})
// 「刷新」按钮直接调 pushStore.loadConfigs()，不经过 onMounted，因此标签同步要挂在数据上。
watch(() => pushStore.configs, syncChannelLabels, { deep: true })
onUnmounted(() => { cleanupScrollTriggers() })

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

  // store 不 throw，只返回布尔值；失败时的提示已由 store 的 showError 承担（T-11-21）。
  const ok = await pushStore.saveConfig(config.channelType, {
    enabled: config.enabled,
    configValue: config.configValue?.includes('****') ? undefined : config.configValue
  })
  if (!ok) {
    // 回滚开关，不追加第二条提示。
    config.enabled = !config.enabled
    return
  }
  if (config.enabled) {
    ElMessage.success(`${config.channelLabel}已启用`)
  } else {
    ElMessage.info(`${config.channelLabel}已禁用`)
  }
}

async function handleSaveConfig(config: PushConfig) {
  // Don't save if value contains mask pattern
  if (config.configValue && config.configValue.includes('****')) {
    ElMessage.warning('配置值已被遮蔽，请重新输入完整的配置值')
    return
  }
  if (config.configValue) {
    // 失焦即触发；值未变化时跳过，避免每次点击输入框都产生一次 PUT（IN-12）。
    if (config.configValue === lastSavedValues.get(config.channelType)) return
    // 上一轮的投影不进入本次尝试；只清错误文本，不碰输入值（ERR-05）。
    errors.clearField(channelField(config.channelType))
    const ok = await pushStore.saveConfig(config.channelType, {
      configValue: config.configValue
    })
    if (!ok) {
      // 提示已由 store 的 showError 承担（D-02 单一呈现）；这里只补字段级投影，
      // 绝不第二次调用 showError —— 那会让一次失败出现两条提示。
      projectSaveError(config.channelType)
      return
    }
    lastSavedValues.set(config.channelType, config.configValue)
    // 此处是唯一保存成功提示；store 不再重复弹（WR-07）。
    ElMessage.success('配置已保存')
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
