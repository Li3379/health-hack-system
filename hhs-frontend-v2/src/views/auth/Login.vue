<template>
  <div class="login-page">
    <!-- Background -->
    <div class="bg-layer">
      <div class="bg-gradient"></div>
      <div class="bg-glow"></div>
    </div>

    <!-- Login Card -->
    <div class="login-wrapper">
      <div class="login-card">
        <!-- Logo -->
        <div class="card-header">
          <div class="logo-container">
            <svg
              viewBox="0 0 48 48"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              class="logo-svg"
            >
              <rect width="48" height="48" rx="12" fill="var(--accent-cool)" fill-opacity="0.1" />
              <path
                d="M24 10C16.27 10 10 16.27 10 24s6.27 14 14 14 14-6.27 14-14S31.73 10 24 10zm0 24c-5.51 0-10-4.49-10-10s4.49-10 10-10 10 4.49 10 10-4.49 10-10 10z"
                fill="var(--accent-cool)"
              />
              <path
                d="M24 16c-4.41 0-8 3.59-8 8s3.59 8 8 8 8-3.59 8-8-3.59-8-8-8zm0 12c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4z"
                fill="var(--accent-cool)"
                fill-opacity="0.6"
              />
              <circle cx="24" cy="24" r="2.5" fill="var(--accent-cool)" />
            </svg>
          </div>
          <h1 class="card-title">HHS 健康管理系统</h1>
          <p class="card-subtitle">您的智能健康管家</p>
        </div>

        <!-- Form -->
        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large" class="login-form">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
          </el-form-item>

          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">
              <span v-if="!loading">登 录</span>
              <span v-else>登录中...</span>
            </el-button>
          </el-form-item>

          <div class="form-footer">
            <router-link to="/register" class="register-link">
              还没有账户？<span>立即注册</span>
            </router-link>
          </div>
        </el-form>

        <!-- Features -->
        <div class="features">
          <div class="feature-chip">
            <el-icon><Monitor /></el-icon>
            <span>实时监控</span>
          </div>
          <div class="feature-chip">
            <el-icon><ChatDotRound /></el-icon>
            <span>AI 顾问</span>
          </div>
          <div class="feature-chip">
            <el-icon><TrendCharts /></el-icon>
            <span>健康评分</span>
          </div>
        </div>
      </div>

      <p class="copyright">HHS Health Management System v1.0</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { User, Lock, Monitor, ChatDotRound, TrendCharts } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (valid) {
      loading.value = true
      try {
        await authStore.login(form)
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: var(--bg);
}

/* Background */
.bg-layer {
  position: absolute;
  inset: 0;
  z-index: 0;
}
.bg-gradient {
  position: absolute;
  inset: 0;
  background: var(--bg);
}
.bg-glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 30% 30%, rgba(var(--accent-cool-rgb), 0.08) 0%, transparent 50%),
    radial-gradient(ellipse at 70% 70%, rgba(var(--accent-warm-rgb), 0.06) 0%, transparent 50%);
}

/* Login Wrapper */
.login-wrapper {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  padding: 20px;
  animation: fadeInUp 0.8s var(--ease-cinema);
}
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(24px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Login Card */
.login-card {
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  padding: 48px 36px;
  transition: border-color var(--dur-mid) var(--ease);
}
.login-card:hover {
  border-color: var(--border-strong);
}

/* Header */
.card-header {
  text-align: center;
  margin-bottom: 36px;
}
.logo-container {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
}
.logo-svg {
  width: 100%;
  height: 100%;
}
.card-title {
  font: 700 28px/1 var(--font-ui);
  color: var(--text-1);
  letter-spacing: -0.02em;
  margin-bottom: 8px;
}
.card-subtitle {
  font: 400 15px/1.5 var(--font-ui);
  color: var(--text-3);
}

/* Form */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.login-btn {
  width: 100%;
  height: 48px !important;
  font-size: 15px !important;
  font-weight: 600 !important;
  margin-top: 8px;
}

/* Footer */
.form-footer {
  text-align: center;
  margin-top: 8px;
}
.register-link {
  font: 400 14px/1.5 var(--font-ui);
  color: var(--text-3);
  transition: color var(--dur-fast) var(--ease);
}
.register-link:hover {
  color: var(--text-1);
}
.register-link span {
  color: var(--accent-cool);
  font-weight: 500;
}

/* Features */
.features {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border);
}
.feature-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-full);
  font: 400 12px/1 var(--font-ui);
  color: var(--text-3);
  transition: all var(--dur-fast) var(--ease);
}
.feature-chip:hover {
  border-color: var(--border-strong);
  color: var(--text-1);
}
.feature-chip .el-icon {
  font-size: 14px;
  color: var(--accent-cool);
}

/* Copyright */
.copyright {
  text-align: center;
  margin-top: 24px;
  font: 400 12px/1 var(--font-mono);
  color: var(--text-4);
  letter-spacing: 0.04em;
}

/* Responsive */
@media (max-width: 480px) {
  .login-card {
    padding: 36px 24px;
  }
  .features {
    flex-direction: column;
    align-items: center;
  }
}
</style>
