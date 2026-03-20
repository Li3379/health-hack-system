<template>
  <div class="config-wizard">
    <!-- 步骤指示器 -->
    <el-steps :active="currentStep" finish-status="success" align-center>
      <el-step title="检查配置" />
      <el-step title="选择平台" />
      <el-step title="配置指南" />
      <el-step title="完成连接" />
    </el-steps>

    <!-- 步骤内容 -->
    <div class="step-content">
      <!-- Step 0: 检查配置状态 -->
      <div v-if="currentStep === 0" class="step-panel">
        <div class="check-status">
          <el-icon v-if="loading" class="is-loading" :size="48"><Loading /></el-icon>
          <template v-else>
            <el-icon v-if="configReady.encryptionReady" :size="48" color="#67c23a">
              <CircleCheckFilled />
            </el-icon>
            <el-icon v-else :size="48" color="#f56c6c"><WarningFilled /></el-icon>
          </template>

          <h3>
            {{
              loading ? '正在检查配置...' : configReady.encryptionReady ? '系统已就绪' : '需要配置'
            }}
          </h3>

          <div v-if="!loading" class="status-details">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="加密密钥">
                <el-tag :type="configReady.encryptionReady ? 'success' : 'danger'">
                  {{ configReady.encryptionReady ? '已配置' : '未配置' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="可用平台">
                <el-tag :type="configReady.anyPlatformReady ? 'success' : 'warning'">
                  {{ configReady.anyPlatformReady ? '有平台可用' : '无平台配置' }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>

            <div v-if="configReady.unconfiguredPlatforms.length > 0" class="unconfigured-list">
              <p>以下平台尚未配置：</p>
              <el-tag
                v-for="platform in configReady.unconfiguredPlatforms"
                :key="platform"
                type="info"
                class="platform-tag"
              >
                {{ getPlatformName(platform) }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- Step 1: 选择平台 -->
      <div v-if="currentStep === 1" class="step-panel">
        <h3>选择要配置的平台</h3>
        <div class="platform-grid">
          <div
            v-for="platform in platforms"
            :key="platform.platform"
            class="platform-card"
            :class="{
              selected: selectedPlatform === platform.platform,
              configured: platform.configured
            }"
            @click="selectPlatform(platform.platform)"
          >
            <el-avatar :size="48" :class="['platform-avatar', platform.platform]">
              {{ getPlatformIcon(platform.platform) }}
            </el-avatar>
            <div class="platform-info">
              <span class="platform-name">{{ platform.platformName }}</span>
              <el-tag :type="platform.configured ? 'success' : 'info'" size="small">
                {{ platform.configured ? '已配置' : '未配置' }}
              </el-tag>
            </div>
            <div v-if="platform.supportedDataTypes" class="supported-types">
              <span
                v-for="type in platform.supportedDataTypes.slice(0, 3)"
                :key="type"
                class="type-tag"
              >
                {{ getDataTypeName(type) }}
              </span>
              <span v-if="platform.supportedDataTypes.length > 3" class="type-tag more">
                +{{ platform.supportedDataTypes.length - 3 }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Step 2: 配置指南 -->
      <div v-if="currentStep === 2" class="step-panel">
        <template v-if="selectedPlatformInfo">
          <div class="guide-header">
            <el-avatar :size="40" :class="['platform-avatar', selectedPlatform || '']">
              {{ selectedPlatform ? getPlatformIcon(selectedPlatform) : '' }}
            </el-avatar>
            <h3>{{ selectedPlatformInfo.platformName }} 配置指南</h3>
          </div>

          <el-alert
            v-if="!configReady.encryptionReady"
            title="加密密钥未配置"
            type="warning"
            show-icon
            class="config-alert"
          >
            系统需要配置加密密钥才能安全存储 OAuth 凭据。请联系系统管理员配置 DEVICE_ENCRYPTION_KEY
            环境变量。
          </el-alert>

          <el-collapse v-model="activeGuide">
            <el-collapse-item name="how-to">
              <template #title>
                <strong>如何获取 {{ selectedPlatformInfo.platformName }} 的 API 凭据？</strong>
              </template>
              <div class="guide-content">
                <template v-if="selectedPlatform === 'huawei'">
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
                    <li>配置 OAuth 回调地址</li>
                    <li>在「项目设置」获取应用ID和密钥</li>
                  </ol>
                </template>
                <template v-else-if="selectedPlatform === 'xiaomi'">
                  <ol>
                    <li>
                      访问
                      <a href="https://dev.mi.com" target="_blank">小米开放平台</a>
                      并登录
                    </li>
                    <li>进入控制台创建应用</li>
                    <li>申请开通「小米运动健康 API」</li>
                    <li>配置 OAuth 回调地址</li>
                    <li>获取 App ID 和 App Secret</li>
                  </ol>
                </template>
                <template v-else-if="selectedPlatform === 'wechat'">
                  <el-alert type="warning" :closable="false">
                    微信运动需要通过微信小程序获取数据，不支持纯 Web OAuth 流程。
                  </el-alert>
                  <ol>
                    <li>
                      注册
                      <a href="https://mp.weixin.qq.com" target="_blank">微信小程序</a>
                    </li>
                    <li>在小程序后台开通「微信运动」数据权限</li>
                    <li>使用 wx.getWeRunData() API 获取数据</li>
                  </ol>
                </template>
                <template v-else-if="selectedPlatform === 'apple'">
                  <el-alert type="warning" :closable="false">
                    Apple Health 数据获取需要 iOS 应用，不支持纯 Web OAuth 流程。
                  </el-alert>
                  <ol>
                    <li>
                      加入
                      <a href="https://developer.apple.com/programs" target="_blank">
                        Apple Developer Program
                      </a>
                    </li>
                    <li>在 iOS 应用中集成 HealthKit 框架</li>
                    <li>通过应用将数据同步到服务器</li>
                  </ol>
                </template>
              </div>
            </el-collapse-item>

            <el-collapse-item name="requirements">
              <template #title>
                <strong>配置要求</strong>
              </template>
              <div class="requirements-list">
                <el-descriptions :column="1" border>
                  <el-descriptions-item label="Client ID">
                    <el-tag
                      :type="
                        selectedPlatformInfo.missingConfig?.includes('client_id')
                          ? 'danger'
                          : 'success'
                      "
                    >
                      {{
                        selectedPlatformInfo.missingConfig?.includes('client_id')
                          ? '需要配置'
                          : '已配置'
                      }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="Client Secret">
                    <el-tag
                      :type="
                        selectedPlatformInfo.missingConfig?.includes('client_secret')
                          ? 'danger'
                          : 'success'
                      "
                    >
                      {{
                        selectedPlatformInfo.missingConfig?.includes('client_secret')
                          ? '需要配置'
                          : '已配置'
                      }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="加密密钥">
                    <el-tag :type="configReady.encryptionReady ? 'success' : 'danger'">
                      {{ configReady.encryptionReady ? '已配置' : '需要配置' }}
                    </el-tag>
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </el-collapse-item>
          </el-collapse>
        </template>
      </div>

      <!-- Step 3: 完成连接 -->
      <div v-if="currentStep === 3" class="step-panel">
        <template v-if="selectedPlatformInfo?.configured">
          <div class="completion-success">
            <el-icon :size="64" color="#67c23a"><CircleCheckFilled /></el-icon>
            <h3>{{ selectedPlatformInfo.platformName }} 已配置完成</h3>
            <p>您现在可以连接您的设备并同步健康数据了！</p>

            <el-button type="primary" size="large" @click="handleConnect">
              <el-icon><Link /></el-icon>
              立即连接
            </el-button>
          </div>
        </template>
        <template v-else>
          <div class="completion-pending">
            <el-icon :size="64" color="#e6a23c"><Clock /></el-icon>
            <h3>等待管理员配置</h3>
            <p>请联系系统管理员完成 {{ selectedPlatformInfo?.platformName }} 的 API 凭据配置。</p>
            <p>配置完成后，您将能够连接设备并同步数据。</p>

            <el-button type="primary" size="large" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
              刷新状态
            </el-button>
          </div>
        </template>
      </div>
    </div>

    <!-- 底部按钮 -->
    <div class="wizard-footer">
      <el-button v-if="currentStep > 0" @click="prevStep">上一步</el-button>
      <el-button v-if="currentStep < 3" type="primary" :disabled="!canNextStep" @click="nextStep">
        下一步
      </el-button>
      <el-button v-if="currentStep === 3" @click="handleClose">关闭</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Loading,
  CircleCheckFilled,
  WarningFilled,
  Link,
  Clock,
  Refresh
} from '@element-plus/icons-vue'
import { deviceApi, type PlatformConfigStatus, type ConfigReadyResponse } from '@/api/device'

const emit = defineEmits<{
  (e: 'connect', platform: string): void
  (e: 'close'): void
}>()

// State
const currentStep = ref(0)
const loading = ref(true)
const platforms = ref<PlatformConfigStatus[]>([])
const configReady = ref<ConfigReadyResponse>({
  ready: false,
  encryptionReady: false,
  anyPlatformReady: false,
  unconfiguredPlatforms: []
})
const selectedPlatform = ref<string | null>(null)
const activeGuide = ref(['how-to'])

// Computed
const selectedPlatformInfo = computed(() => {
  if (!selectedPlatform.value) return null
  return platforms.value.find(p => p.platform === selectedPlatform.value)
})

const canNextStep = computed(() => {
  if (currentStep.value === 1) {
    return selectedPlatform.value !== null
  }
  return true
})

// Methods
const loadConfigStatus = async () => {
  loading.value = true
  try {
    const [statusRes, readyRes] = await Promise.all([
      deviceApi.getConfigStatus(),
      deviceApi.checkConfigReady()
    ])

    if (statusRes.code === 200) {
      platforms.value = statusRes.data.platforms
    }
    if (readyRes.code === 200) {
      configReady.value = readyRes.data
    }
  } catch (error) {
    console.error('Failed to load config status', error)
    ElMessage.error('加载配置状态失败')
  } finally {
    loading.value = false
  }
}

const selectPlatform = (platform: string) => {
  selectedPlatform.value = platform
}

const nextStep = () => {
  if (currentStep.value < 3) {
    currentStep.value++
  }
}

const prevStep = () => {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

const handleConnect = () => {
  if (selectedPlatform.value) {
    emit('connect', selectedPlatform.value)
  }
}

const handleRefresh = async () => {
  await loadConfigStatus()
  if (selectedPlatformInfo.value?.configured) {
    ElMessage.success('配置已更新，平台已就绪！')
  }
}

const handleClose = () => {
  emit('close')
}

const getPlatformName = (platform: string) => {
  const names: Record<string, string> = {
    huawei: '华为运动健康',
    xiaomi: '小米运动',
    wechat: '微信运动',
    apple: 'Apple Health'
  }
  return names[platform] || platform
}

const getPlatformIcon = (platform: string) => {
  const icons: Record<string, string> = {
    huawei: 'H',
    xiaomi: 'M',
    wechat: 'W',
    apple: 'A'
  }
  return icons[platform] || platform[0].toUpperCase()
}

const getDataTypeName = (type: string) => {
  const names: Record<string, string> = {
    heart_rate: '心率',
    step_count: '步数',
    sleep: '睡眠',
    blood_pressure: '血压',
    blood_glucose: '血糖',
    spo2: '血氧'
  }
  return names[type] || type
}

onMounted(() => {
  loadConfigStatus()
})
</script>

<style scoped>
.config-wizard {
  padding: 20px;
}

.step-content {
  margin: 24px 0;
  min-height: 300px;
}

.step-panel {
  padding: 20px;
}

.check-status {
  text-align: center;
  padding: 40px 0;
}

.check-status h3 {
  margin-top: 16px;
  color: var(--color-text-primary);
}

.status-details {
  max-width: 400px;
  margin: 20px auto 0;
}

.unconfigured-list {
  margin-top: 16px;
  text-align: left;
}

.platform-tag {
  margin: 4px;
}

.platform-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.platform-card {
  padding: 16px;
  border: 2px solid var(--color-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.platform-card:hover {
  border-color: var(--color-primary);
  background: var(--color-bg-secondary);
}

.platform-card.selected {
  border-color: var(--color-primary);
  background: rgba(64, 158, 255, 0.1);
}

.platform-card.configured {
  border-color: var(--color-success);
}

.platform-avatar {
  background: var(--color-primary);
  color: white;
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

.platform-info {
  text-align: center;
}

.platform-name {
  display: block;
  font-weight: 500;
  margin-bottom: 8px;
}

.supported-types {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}

.type-tag {
  font-size: 12px;
  padding: 2px 6px;
  background: var(--color-bg-secondary);
  border-radius: 4px;
  color: var(--color-text-secondary);
}

.type-tag.more {
  background: var(--color-primary-light-9);
  color: var(--color-primary);
}

.guide-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.config-alert {
  margin-bottom: 16px;
}

.config-alert :deep(.el-alert__title) {
  word-break: break-word;
  line-height: 1.5;
}

.config-alert :deep(.el-alert__description) {
  word-break: break-word;
  line-height: 1.6;
}

.config-alert :deep(.el-alert__content) {
  overflow: hidden;
}

.guide-content ol {
  padding-left: 20px;
  line-height: 2;
}

.guide-content a {
  color: var(--color-primary);
}

.requirements-list {
  max-width: 400px;
}

.completion-success,
.completion-pending {
  text-align: center;
  padding: 40px 0;
}

.completion-success h3,
.completion-pending h3 {
  margin-top: 16px;
  color: var(--color-text-primary);
}

.completion-success p,
.completion-pending p {
  color: var(--color-text-secondary);
  margin: 8px 0;
}

.wizard-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
}
</style>
