<template>
  <div class="device-platform-config">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>设备平台配置</span>
          <div class="header-actions">
            <el-button type="primary" :loading="initializing" @click="handleInitDefaults">
              初始化默认配置
            </el-button>
            <el-button :loading="loading" @click="loadConfigs">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- Encryption Key Status -->
      <el-alert
        v-if="!encryptionKeyConfigured"
        title="加密密钥未配置"
        type="error"
        show-icon
        class="encryption-alert"
      >
        <template #default>
          系统需要配置加密密钥才能安全存储 OAuth 凭据。 请设置环境变量
          <code>DEVICE_ENCRYPTION_KEY</code>
          (32字节)。
          <br />
          生成命令:
          <code>openssl rand -base64 32</code>
        </template>
      </el-alert>

      <!-- Platform Config Table -->
      <el-table v-loading="loading" :data="platforms" stripe>
        <el-table-column label="平台" width="200">
          <template #default="{ row }">
            <div class="platform-cell">
              <el-avatar :size="32" :class="['platform-avatar', row.platform]">
                {{ getPlatformIcon(row.platform) }}
              </el-avatar>
              <span>{{ row.platformName }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="配置状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.configured ? 'success' : 'danger'" size="small">
              {{ row.configured ? '已配置' : '未配置' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="OAuth就绪" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.oauthReady ? 'success' : 'info'" size="small">
              {{ row.oauthReady ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="启用状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :disabled="!row.configured"
              @change="handleToggleEnabled(row)"
            />
          </template>
        </el-table-column>

        <el-table-column label="测试结果" min-width="150">
          <template #default="{ row }">
            <div v-if="row.lastTestTime" class="test-result">
              <el-tag
                :type="
                  row.testResult === 'success'
                    ? 'success'
                    : row.testResult === 'failed'
                      ? 'danger'
                      : 'info'
                "
                size="small"
              >
                {{ getTestResultLabel(row.testResult) }}
              </el-tag>
              <span class="test-time">{{ formatTime(row.lastTestTime) }}</span>
            </div>
            <span v-else class="no-test">未测试</span>
          </template>
        </el-table-column>

        <el-table-column label="支持的数据类型" min-width="200">
          <template #default="{ row }">
            <div class="data-types">
              <el-tag
                v-for="type in (row.supportedDataTypes || []).slice(0, 3)"
                :key="type"
                size="small"
                type="info"
                class="type-tag"
              >
                {{ getDataTypeName(type) }}
              </el-tag>
              <el-tag
                v-if="(row.supportedDataTypes || []).length > 3"
                size="small"
                class="type-tag"
              >
                +{{ row.supportedDataTypes.length - 3 }}
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button-group>
              <el-button size="small" @click="openConfigDialog(row)">
                {{ row.configured ? '编辑' : '配置' }}
              </el-button>
              <el-button
                size="small"
                type="primary"
                :disabled="!row.configured"
                :loading="testingPlatform === row.platform"
                @click="handleTestConfig(row.platform)"
              >
                测试
              </el-button>
              <el-button
                size="small"
                type="danger"
                :disabled="!row.configured"
                @click="handleDeleteConfig(row.platform)"
              >
                删除
              </el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Config Dialog -->
    <el-dialog
      v-model="showConfigDialog"
      :title="editingPlatform ? `配置 ${editingPlatform.platformName}` : '配置平台'"
      width="600px"
      destroy-on-close
    >
      <el-form
        ref="configFormRef"
        :model="configForm"
        :rules="configRules"
        label-width="120px"
        label-position="right"
      >
        <el-form-item v-if="!editingPlatform" label="平台">
          <el-select v-model="configForm.platform" placeholder="选择平台">
            <el-option
              v-for="p in unconfiguredPlatforms"
              :key="p.platform"
              :label="p.platformName"
              :value="p.platform"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Client ID" prop="clientId">
          <el-input
            v-model="configForm.clientId"
            placeholder="输入 OAuth Client ID"
            show-word-limit
            maxlength="200"
          />
        </el-form-item>

        <el-form-item label="Client Secret" prop="clientSecret">
          <el-input
            v-model="configForm.clientSecret"
            type="password"
            placeholder="输入 OAuth Client Secret"
            show-password
            show-word-limit
            maxlength="200"
          />
        </el-form-item>

        <el-form-item label="授权 URL">
          <el-input v-model="configForm.authUrl" placeholder="OAuth 授权端点 URL" />
        </el-form-item>

        <el-form-item label="Token URL">
          <el-input v-model="configForm.tokenUrl" placeholder="Token 交换端点 URL" />
        </el-form-item>

        <el-form-item label="回调 URL">
          <el-input v-model="configForm.redirectUri" placeholder="OAuth 回调地址" />
        </el-form-item>

        <el-form-item label="权限范围">
          <el-select
            v-model="configForm.scopes"
            multiple
            filterable
            allow-create
            placeholder="选择或输入权限范围"
          >
            <el-option v-for="scope in defaultScopes" :key="scope" :label="scope" :value="scope" />
          </el-select>
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="configForm.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showConfigDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { deviceApi, type PlatformConfigStatus, type AdminConfigRequest } from '@/api/device'

// State
const loading = ref(false)
const saving = ref(false)
const testingPlatform = ref<string | null>(null)
const initializing = ref(false)
const platforms = ref<PlatformConfigStatus[]>([])
const encryptionKeyConfigured = ref(true)
const showConfigDialog = ref(false)
const editingPlatform = ref<PlatformConfigStatus | null>(null)
const configFormRef = ref<FormInstance>()

const configForm = reactive<AdminConfigRequest & { scopes: string[] }>({
  platform: '',
  clientId: '',
  clientSecret: '',
  authUrl: '',
  tokenUrl: '',
  redirectUri: '',
  scopes: [],
  enabled: true
})

const configRules: FormRules = {
  clientId: [{ required: true, message: '请输入 Client ID', trigger: 'blur' }],
  clientSecret: [{ required: true, message: '请输入 Client Secret', trigger: 'blur' }]
}

// Default scopes per platform
const defaultScopesMap: Record<string, string[]> = {
  huawei: [
    'https://www.huawei.com/healthkit/healthdata.read',
    'https://www.huawei.com/healthkit/healthdata.write'
  ],
  xiaomi: ['read', 'write'],
  wechat: ['werun'],
  apple: []
}

const defaultScopes = computed(() => {
  return defaultScopesMap[configForm.platform] || []
})

const unconfiguredPlatforms = computed(() => {
  return platforms.value.filter(p => !p.configured)
})

// Methods
const loadConfigs = async () => {
  loading.value = true
  try {
    const res = await deviceApi.adminGetAllStatus()
    if (res.code === 200) {
      platforms.value = res.data
    }

    // Also check encryption key status
    const readyRes = await deviceApi.checkConfigReady()
    if (readyRes.code === 200) {
      encryptionKeyConfigured.value = readyRes.data.encryptionReady
    }
  } catch (error) {
    console.error('Failed to load configs', error)
    ElMessage.error('加载配置失败')
  } finally {
    loading.value = false
  }
}

const openConfigDialog = (platform: PlatformConfigStatus) => {
  editingPlatform.value = platform
  configForm.platform = platform.platform
  configForm.clientId = ''
  configForm.clientSecret = ''
  configForm.authUrl = getDefaultAuthUrl(platform.platform)
  configForm.tokenUrl = getDefaultTokenUrl(platform.platform)
  configForm.redirectUri = getDefaultRedirectUri(platform.platform)
  configForm.scopes = defaultScopesMap[platform.platform] || []
  configForm.enabled = true
  showConfigDialog.value = true
}

const handleSaveConfig = async () => {
  if (!configFormRef.value) return

  await configFormRef.value.validate(async valid => {
    if (!valid) return

    saving.value = true
    try {
      const res = await deviceApi.adminSaveConfig(configForm)
      if (res.code === 200) {
        ElMessage.success('配置保存成功')
        showConfigDialog.value = false
        loadConfigs()
      } else {
        ElMessage.error(res.message || '保存失败')
      }
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    } finally {
      saving.value = false
    }
  })
}

const handleTestConfig = async (platform: string) => {
  testingPlatform.value = platform
  try {
    const res = await deviceApi.adminTestConfig(platform)
    if (res.code === 200) {
      if (res.data.testResult === 'success') {
        ElMessage.success('配置测试成功')
      } else {
        ElMessage.warning('配置测试失败，请检查凭据是否正确')
      }
      loadConfigs()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '测试失败')
  } finally {
    testingPlatform.value = null
  }
}

const handleDeleteConfig = async (platform: string) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除此平台的配置吗？删除后用户将无法连接此设备。',
      '确认删除',
      { type: 'warning' }
    )

    const res = await deviceApi.adminDeleteConfig(platform)
    if (res.code === 200) {
      ElMessage.success('配置已删除')
      loadConfigs()
    }
  } catch {
    // User cancelled
  }
}

const handleToggleEnabled = async (row: PlatformConfigStatus) => {
  // This would require an API endpoint to toggle enabled status
  // For now, just show a message
  ElMessage.info(`平台 ${row.platformName} ${row.enabled ? '已启用' : '已禁用'}`)
}

const handleInitDefaults = async () => {
  try {
    await ElMessageBox.confirm('将从 YAML 配置初始化数据库配置，是否继续？', '初始化配置', {
      type: 'info'
    })

    initializing.value = true
    const res = await deviceApi.adminInitDefaults()
    if (res.code === 200) {
      ElMessage.success('默认配置已初始化')
      loadConfigs()
    }
  } catch {
    // User cancelled
  } finally {
    initializing.value = false
  }
}

// Helper functions
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

const getTestResultLabel = (result?: string) => {
  const labels: Record<string, string> = {
    success: '成功',
    failed: '失败',
    pending: '待测试'
  }
  return labels[result || ''] || result || '未知'
}

const formatTime = (time?: string) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getDefaultAuthUrl = (platform: string) => {
  const urls: Record<string, string> = {
    huawei: 'https://oauth-login.cloud.huawei.com/oauth2/v2/authorize',
    xiaomi: 'https://account.xiaomi.com/oauth2/authorize',
    wechat: '',
    apple: ''
  }
  return urls[platform] || ''
}

const getDefaultTokenUrl = (platform: string) => {
  const urls: Record<string, string> = {
    huawei: 'https://oauth-login.cloud.huawei.com/oauth2/v2/token',
    xiaomi: 'https://account.xiaomi.com/oauth2/token',
    wechat: '',
    apple: ''
  }
  return urls[platform] || ''
}

const getDefaultRedirectUri = (platform: string) => {
  const baseUrl = window.location.origin
  return `${baseUrl}/api/device/callback/${platform}`
}

onMounted(() => {
  loadConfigs()
})
</script>

<style scoped>
.device-platform-config {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.encryption-alert {
  margin-bottom: 16px;
}

.encryption-alert code {
  background: rgba(0, 0, 0, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

.platform-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.platform-avatar {
  background: var(--color-primary);
  color: white;
  font-weight: 600;
  font-size: 14px;
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

.test-result {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.test-time {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.no-test {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.data-types {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.type-tag {
  margin: 2px;
}
</style>
