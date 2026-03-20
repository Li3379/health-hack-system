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
            <el-form-item label="用户名">
              <el-input v-model="authStore.user!.username" disabled />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" placeholder="请输入昵称" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入手机号" />
            </el-form-item>
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
            <el-form-item label="原密码" prop="oldPassword">
              <el-input
                v-model="passwordForm.oldPassword"
                type="password"
                placeholder="请输入原密码"
                show-password
              />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="passwordForm.newPassword"
                type="password"
                placeholder="请输入新密码"
                show-password
              />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="passwordForm.confirmPassword"
                type="password"
                placeholder="请再次输入新密码"
                show-password
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
import { ref, reactive, onMounted } from 'vue'
import {
  ElMessage,
  type FormInstance,
  type FormRules,
  type UploadRequestOptions
} from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api/user'
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
    ElMessage.error('获取个人信息失败')
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    loading.value = true
    try {
      await userApi.updateProfile(form)
      ElMessage.success('更新成功')
      editing.value = false
      await authStore.fetchUserInfo()
    } catch (error) {
      ElMessage.error('更新失败')
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

  await passwordFormRef.value.validate(async valid => {
    if (!valid) return

    passwordLoading.value = true
    try {
      await userApi.changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      ElMessage.success('密码修改成功，请重新登录')
      Object.assign(passwordForm, {
        oldPassword: '',
        newPassword: '',
        confirmPassword: ''
      })
      setTimeout(() => {
        authStore.logout()
      }, 1500)
    } catch (error) {
      ElMessage.error('密码修改失败')
    } finally {
      passwordLoading.value = false
    }
  })
}

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
      console.error('Failed to refresh user info:', e)
    }
  } catch (error) {
    ElMessage.error('头像上传失败')
  }
}

const fetchStats = async () => {
  // 这里可以调用统计接口获取数据
  // 暂时使用模拟数据
  stats.metricCount = 156
  stats.alertCount = 23
  stats.reportCount = 8
  stats.aiChatCount = 45
}

onMounted(() => {
  fetchProfile()
  fetchStats()
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
