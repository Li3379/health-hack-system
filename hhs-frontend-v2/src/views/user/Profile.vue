<template>
  <div class="user-profile-page">
    <el-row :gutter="20">
      <!-- 个人信息 -->
      <el-col :xs="24" :md="16">
        <el-card>
          <template #header>
            <div class="header-actions">
              <span>个人信息</span>
              <el-button v-if="!editing" type="primary" @click="editing = true">编辑</el-button>
              <div v-else>
                <el-button @click="cancelEdit">取消</el-button>
                <el-button type="primary" @click="handleSubmit">保存</el-button>
              </div>
            </div>
          </template>

          <el-form
            ref="formRef"
            v-loading="loading"
            :model="form"
            :rules="rules"
            label-width="100px"
            :disabled="!editing"
          >
            <!-- 汇总块必须是 <el-form> 的第一个子节点（Contract 4.3）。本页有两个表单，
                 因此有两个汇总块、两个实例 —— 一个共享实例会让密码表单的错误出现在
                 个人信息卡片里，且汇总条目混着两边的字段（Contract 4.5 规则 4）。 -->
            <FormErrorSummary
              ref="profileSummaryRef"
              :summary-items="profileErrors.summaryItems"
              @focus-field="profileErrors.focusField"
            />
            <el-form-item label="用户名">
              <el-input v-model="authStore.user!.username" disabled />
            </el-form-item>
            <el-form-item
              label="昵称"
              prop="nickname"
              data-field="nickname"
              :error="profileErrors.fieldErrors['nickname']"
            >
              <el-input
                v-model="form.nickname"
                placeholder="请输入昵称"
                @update:model-value="profileErrors.clearField('nickname')"
              />
            </el-form-item>
            <el-form-item
              label="邮箱"
              prop="email"
              data-field="email"
              :error="profileErrors.fieldErrors['email']"
            >
              <el-input
                v-model="form.email"
                placeholder="请输入邮箱"
                @update:model-value="profileErrors.clearField('email')"
              />
            </el-form-item>
            <el-form-item
              label="手机号"
              prop="phone"
              data-field="phone"
              :error="profileErrors.fieldErrors['phone']"
            >
              <el-input
                v-model="form.phone"
                placeholder="请输入手机号"
                @update:model-value="profileErrors.clearField('phone')"
              />
            </el-form-item>
            <!-- 只读项不参与服务端校验：没有 prop、没有 :error（它们不可能被后端拒绝）。 -->
            <el-form-item label="注册时间">
              <el-input :value="formatDateTime(authStore.user!.createdAt)" disabled />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 修改密码 -->
        <el-card class="password-card">
          <template #header>
            <span>修改密码</span>
          </template>
          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-width="100px"
          >
            <!-- 密码表单自己的汇总：与个人信息卡片的实例完全独立（字段顺序也不同）。 -->
            <FormErrorSummary
              ref="passwordSummaryRef"
              :summary-items="passwordErrors.summaryItems"
              @focus-field="passwordErrors.focusField"
            />
            <el-form-item
              label="原密码"
              prop="oldPassword"
              data-field="oldPassword"
              :error="passwordErrors.fieldErrors['oldPassword']"
            >
              <el-input
                v-model="passwordForm.oldPassword"
                type="password"
                placeholder="请输入原密码"
                show-password
                @update:model-value="passwordErrors.clearField('oldPassword')"
              />
            </el-form-item>
            <el-form-item
              label="新密码"
              prop="newPassword"
              data-field="newPassword"
              :error="passwordErrors.fieldErrors['newPassword']"
            >
              <el-input
                v-model="passwordForm.newPassword"
                type="password"
                placeholder="请输入新密码"
                show-password
                @update:model-value="passwordErrors.clearField('newPassword')"
              />
            </el-form-item>
            <el-form-item
              label="确认密码"
              prop="confirmPassword"
              data-field="confirmPassword"
              :error="passwordErrors.fieldErrors['confirmPassword']"
            >
              <el-input
                v-model="passwordForm.confirmPassword"
                type="password"
                placeholder="请再次输入新密码"
                show-password
                @update:model-value="passwordErrors.clearField('confirmPassword')"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="passwordLoading" @click="handleChangePassword">
                修改密码
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 头像 -->
      <el-col :xs="24" :md="8">
        <el-card>
          <template #header>
            <span>头像</span>
          </template>
          <div class="avatar-section">
            <el-avatar :size="120" :src="authStore.user?.avatar">
              {{ authStore.user?.nickname?.[0] || authStore.user?.username?.[0] || 'U' }}
            </el-avatar>
            <el-upload
              class="avatar-uploader"
              :show-file-list="false"
              :before-upload="beforeAvatarUpload"
              :http-request="handleAvatarUpload"
              accept="image/*"
            >
              <el-button type="primary" style="margin-top: 20px">
                <el-icon><Upload /></el-icon>
                更换头像
              </el-button>
            </el-upload>
            <div class="avatar-tip">支持 JPG、PNG 格式，文件小于 2MB</div>
          </div>
        </el-card>

        <!-- 账户统计 -->
        <el-card class="stats-card">
          <template #header>
            <span>账户统计</span>
          </template>
          <div class="stat-item">
            <span class="stat-label">健康指标数</span>
            <span class="stat-value">{{ stats.metricCount }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">预警数</span>
            <span class="stat-value">{{ stats.alertCount }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">体检报告数</span>
            <span class="stat-value">{{ stats.reportCount }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">AI 咨询次数</span>
            <span class="stat-value">{{ stats.aiChatCount }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { gsap, DUR, EASE } from '@/composables/useGsap'
import {
  ElMessage,
  type FormInstance,
  type FormRules,
  type UploadRequestOptions
} from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api/user'
import { healthApi } from '@/api/health'
import { alertApi } from '@/api/alert'
import { screeningApi } from '@/api/screening'
import { useFormServerErrors, collectInvalidFieldMessages } from '@/composables/useFormServerErrors'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import { showError } from '@/utils/showError'
import { formatDateTime } from '@/utils/format'
import type { UpdateProfileRequest, ChangePasswordRequest } from '@/types/api'

const authStore = useAuthStore()
const loading = ref(false)
const passwordLoading = ref(false)
const editing = ref(false)
const formRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()

const form = reactive<UpdateProfileRequest>({
  nickname: '',
  email: '',
  phone: ''
})

const passwordForm = reactive<ChangePasswordRequest & { confirmPassword: string }>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const stats = reactive({
  metricCount: 0,
  alertCount: 0,
  reportCount: 0,
  aiChatCount: 0
})

/**
 * 两个表单各一个投影实例。
 *
 * 本页是「同一页两个表单」的唯一案例：一个共享实例会让密码表单的错误落进个人信息卡片，
 * 且两个汇总块都列出同一批字段 —— 每份汇总必须各自回答「这里错了几项」。
 * 字段名在两处都无歧义（全局 `FIELD_LABELS` 已含 nickname/email/phone/oldPassword/
 * newPassword/confirmPassword），因此不需要 `labels` 覆盖。
 *
 * 用 `reactive()` 持有返回值，让模板里 `profileErrors.fieldErrors['email']` 读到字符串而不是 Ref。
 */
const profileErrors = reactive(useFormServerErrors({
  fieldOrder: ['nickname', 'email', 'phone']
}))

const passwordErrors = reactive(useFormServerErrors({
  fieldOrder: ['oldPassword', 'newPassword', 'confirmPassword']
}))

const profileSummaryRef = ref<InstanceType<typeof FormErrorSummary> | null>(null)
const passwordSummaryRef = ref<InstanceType<typeof FormErrorSummary> | null>(null)

/**
 * 客户端校验失败 → 与服务端校验共用同一份汇总通道（GOV.UK 双呈现，D-01），零 toast。
 * 两个表单各调一次，收集器本身是 14-03 交付的无状态纯函数，不是本地重新实现。
 */
const projectClientErrors = async (
  target: typeof profileErrors,
  invalidFields: Record<string, Array<{ message?: string }>> | undefined,
  summary: InstanceType<typeof FormErrorSummary> | null | undefined
): Promise<void> => {
  target.setErrors(collectInvalidFieldMessages(invalidFields))
  await nextTick()
  summary?.focusSummary()
}

const rules: FormRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }]
}

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const fetchProfile = async () => {
  loading.value = true
  try {
    const res = await userApi.getProfile()
    const user = res.data.profile
    Object.assign(form, {
      nickname: user.nickname || '',
      email: user.email || '',
      phone: user.phone || ''
    })
  } catch (error) {
    // 本页不渲染任何错误状态块，`forbidden` / `notFound` 没有落点，
    // 因此显式 opt-in 一条常驻 toast —— 静默是唯一不可接受的结果（Contract 1.3）。
    showError(error, 'user.loadFailed', { presentStates: true })
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  // 上一轮的残留投影不进入本次尝试；只清投影，不碰用户输入（ERR-05）。
  profileErrors.reset()

  await formRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      // 客户端校验失败也走同一份汇总通道（GOV.UK 双呈现），零 toast。
      await projectClientErrors(profileErrors, invalidFields, profileSummaryRef.value)
      return
    }

    loading.value = true
    try {
      await userApi.updateProfile(form)
      ElMessage.success('更新成功')
      // 成功路径顺序不变：退回只读态只在保存解析成功之后。
      editing.value = false
      await authStore.fetchUserInfo()
    } catch (error) {
      // validation → 0 toast，汇总 + 内联承担呈现；其余 kind 由 showError 呈现。
      const err = showError(error, 'user.saveFailed', { presentStates: true })
      if (err.kind === 'validation') {
        profileErrors.apply(err)
        await nextTick()
        profileSummaryRef.value?.focusSummary()
      }
    } finally {
      loading.value = false
    }
  })
}

const cancelEdit = () => {
  editing.value = false
  if (authStore.user) {
    Object.assign(form, {
      nickname: authStore.user.nickname || '',
      email: authStore.user.email || '',
      phone: authStore.user.phone || ''
    })
  }
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value) return

  // 只清投影，绝不碰三个输入框的值（ERR-05 —— 本页最严重的回归形态就是
  // 一次「原密码不正确」把用户被迫重打的密码清空）。
  passwordErrors.reset()

  await passwordFormRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      await projectClientErrors(passwordErrors, invalidFields, passwordSummaryRef.value)
      return
    }

    passwordLoading.value = true
    try {
      await userApi.changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      // ↓ 清空只发生在 await 解析成功之后、且位于 try 块内。失败路径完全不触达这三行，
      //   因此被服务端拒绝的那三个值会原样留在输入框里（Contract 7.1）。
      Object.assign(passwordForm, {
        oldPassword: '',
        newPassword: '',
        confirmPassword: ''
      })
      ElMessage.success('密码修改成功，请重新登录')
      setTimeout(() => {
        authStore.logout()
      }, 1500)
    } catch (error) {
      // validation → 0 toast，密码表单自己的汇总 + 内联承担呈现。
      const err = showError(error, 'user.saveFailed', { presentStates: true })
      if (err.kind === 'validation') {
        passwordErrors.apply(err)
        await nextTick()
        passwordSummaryRef.value?.focusSummary()
      }
    } finally {
      passwordLoading.value = false
    }
  })
}

/**
 * 客户端预检查，下面两条函数式 toast 是**刻意保留**的（本阶段的 2 行残留允许清单之一）。
 *
 * 它们校验的是用户刚选中、尚未发出任何请求的文件：没有 ApiError 可传、没有 kind 可分类。
 * 走 `showError` 需要凭空造一个 ApiError 才能满足签名，还会声称一次并未发生的服务端失败。
 * 台账记 `保持` 并附此理由（14-LEDGER-06.md）。
 */
const beforeAvatarUpload = (file: File) => {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2

  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

const handleAvatarUpload = async (options: UploadRequestOptions) => {
  try {
    const res = await userApi.uploadAvatar(options.file)
    // 更新用户头像字段
    await userApi.updateProfile({ avatar: res.data.url })
    ElMessage.success('头像上传成功')
    // 刷新用户信息以更新头像显示
    try {
      await authStore.fetchUserInfo()
    } catch (e) {
      // refresh user info failed silently
    }
  } catch (error) {
    // 保持「吞掉并呈现」的既有契约：Element Plus 的 uploader 通过 before-upload 的
    // 布尔返回与 http-request 的 promise 结算分别管理自身状态，此处不改变结算状态。
    showError(error, 'user.saveFailed', { presentStates: true })
  }
}

const fetchStats = async () => {
  try {
    const [metricsRes, alertsRes, reportsRes] = await Promise.allSettled([
      healthApi.getMetrics({ page: 1, size: 1 }),
      alertApi.getAlerts({ page: 1, size: 1 }),
      screeningApi.listReports({ page: 1, size: 1 })
    ])

    if (metricsRes.status === 'fulfilled') {
      stats.metricCount = metricsRes.value.data?.total ?? 0
    }
    if (alertsRes.status === 'fulfilled') {
      stats.alertCount = alertsRes.value.data?.total ?? 0
    }
    if (reportsRes.status === 'fulfilled') {
      stats.reportCount = reportsRes.value.data?.total ?? 0
    }
    stats.aiChatCount = 0
  } catch {
    // fallback: keep zeros
  }
}

onMounted(() => {
  fetchProfile()
  fetchStats()

  nextTick(() => {
    const tl = gsap.timeline({ defaults: { ease: EASE.out } })
    // Stagger cards entrance
    tl.fromTo('.el-card',
      { y: 20, autoAlpha: 0, scale: 0.98 },
      { y: 0, autoAlpha: 1, scale: 1, duration: DUR.mid, stagger: 0.1, clearProps: 'transform,autoAlpha,visibility,opacity' }
    )
    // Stat items in stats card
    tl.fromTo('.stat-item',
      { x: -12, autoAlpha: 0 },
      { x: 0, autoAlpha: 1, duration: DUR.fast, stagger: 0.06, clearProps: 'transform,autoAlpha,visibility,opacity' }
    , '-=0.15')
  })
})
</script>

<style scoped>
.user-profile-page {
  padding: 20px;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.password-card {
  margin-top: 20px;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
}

.avatar-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}

.stats-card {
  margin-top: 20px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #ebeef5;
}

.stat-item:last-child {
  border-bottom: none;
}

.stat-label {
  font-size: 14px;
  color: #606266;
}

.stat-value {
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
}
</style>
