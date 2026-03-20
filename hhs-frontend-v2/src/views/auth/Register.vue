<template>
  <div class="register-container">
    <!-- Animated Background -->
    <div class="background">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
      <div class="grid-pattern"></div>
    </div>

    <!-- Register Card -->
    <div class="register-wrapper">
      <el-card class="register-card">
        <!-- Logo & Title -->
        <div class="card-header">
          <div class="logo-container">
            <svg
              viewBox="0 0 48 48"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              class="logo-svg"
            >
              <rect width="48" height="48" rx="12" fill="var(--color-primary)" fill-opacity="0.1" />
              <path
                d="M24 10C16.27 10 10 16.27 10 24s6.27 14 14 14 14-6.27 14-14S31.73 10 24 10zm0 24c-5.51 0-10-4.49-10-10s4.49-10 10-10 10 4.49 10 10-4.49 10-10 10z"
                fill="var(--color-primary)"
              />
              <path
                d="M24 16c-4.41 0-8 3.59-8 8s3.59 8 8 8 8-3.59 8-8-3.59-8-8-8zm0 12c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4z"
                fill="var(--color-primary)"
                fill-opacity="0.6"
              />
              <circle cx="24" cy="24" r="2.5" fill="var(--color-primary)" />
            </svg>
          </div>
          <h1 class="title">创建新账户</h1>
          <p class="subtitle">加入 HHS，开启您的健康之旅</p>
        </div>

        <!-- Register Form -->
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="0"
          size="large"
          class="register-form"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              class="register-input"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码 (至少6位)"
              :prefix-icon="Lock"
              show-password
              class="register-input"
            />
          </el-form-item>

          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              :prefix-icon="Lock"
              show-password
              class="register-input"
            />
          </el-form-item>

          <el-form-item prop="email">
            <el-input
              v-model="form.email"
              placeholder="请输入邮箱 (可选)"
              :prefix-icon="Message"
              class="register-input"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              :loading="loading"
              class="register-button"
              @click="handleRegister"
            >
              <span v-if="!loading">立即注册</span>
              <span v-else>注册中...</span>
            </el-button>
          </el-form-item>

          <div class="footer-links">
            <router-link to="/login" class="login-link">
              已有账户？
              <span>立即登录</span>
            </router-link>
          </div>
        </el-form>

        <!-- Features -->
        <div class="features">
          <div class="feature-item">
            <el-icon><Shield /></el-icon>
            <span>数据安全</span>
          </div>
          <div class="feature-item">
            <el-icon><TrendCharts /></el-icon>
            <span>健康追踪</span>
          </div>
          <div class="feature-item">
            <el-icon><ChatDotRound /></el-icon>
            <span>AI 顾问</span>
          </div>
        </div>
      </el-card>

      <!-- Copyright -->
      <p class="copyright">HHS Health Management System v1.0</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { User, Lock, Message } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: ''
})

const validateConfirmPassword = (_rule: any, value: any, callback: any) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 32, message: '用户名长度需在4-32之间', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需在6-64之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const handleRegister = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (valid) {
      loading.value = true
      try {
        await authStore.register({
          username: form.username,
          password: form.password,
          email: form.email || undefined
        })
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: var(--color-bg-primary);
}

/* Animated Background */
.background {
  position: absolute;
  inset: 0;
  overflow: hidden;
  z-index: 0;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.6;
  animation: float 20s ease-in-out infinite;
}

.orb-1 {
  width: 600px;
  height: 600px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  top: -200px;
  right: -100px;
  animation-delay: 0s;
}

.orb-2 {
  width: 500px;
  height: 500px;
  background: linear-gradient(135deg, var(--color-success) 0%, #059669 100%);
  bottom: -150px;
  left: -100px;
  animation-delay: -7s;
}

.orb-3 {
  width: 400px;
  height: 400px;
  background: linear-gradient(135deg, var(--color-warning) 0%, #d97706 100%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation-delay: -14s;
}

@keyframes float {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  25% {
    transform: translate(30px, -30px) scale(1.05);
  }
  50% {
    transform: translate(-20px, 20px) scale(0.95);
  }
  75% {
    transform: translate(-30px, -20px) scale(1.02);
  }
}

.grid-pattern {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(37, 99, 235, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(37, 99, 235, 0.03) 1px, transparent 1px);
  background-size: 50px 50px;
}

/* Register Wrapper */
.register-wrapper {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 440px;
  padding: 20px;
  animation: fadeInUp 0.6s ease-out;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Register Card */
.register-card {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-2xl);
  box-shadow:
    0 25px 50px -12px rgba(0, 0, 0, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.1) inset;
  overflow: hidden;
}

[data-theme='dark'] .register-card {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

/* Card Header */
.card-header {
  text-align: center;
  padding: 40px 40px 24px;
}

.logo-container {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
  animation: pulse-glow 3s ease-in-out infinite;
}

@keyframes pulse-glow {
  0%,
  100% {
    filter: drop-shadow(0 0 0 transparent);
  }
  50% {
    filter: drop-shadow(0 0 20px rgba(37, 99, 235, 0.3));
  }
}

.logo-svg {
  width: 100%;
  height: 100%;
}

.title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  margin: 0 0 8px;
  letter-spacing: -0.02em;
}

.subtitle {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  margin: 0;
}

/* Register Form */
.register-form {
  padding: 0 40px 24px;
}

.register-input :deep(.el-input__wrapper) {
  background: var(--color-bg-tertiary);
  border: 1px solid transparent;
  border-radius: var(--radius-lg);
  box-shadow: none;
  padding: 4px 16px;
  transition: var(--transition-colors), var(--transition-shadow);
}

.register-input :deep(.el-input__wrapper:hover) {
  border-color: var(--color-border);
}

.register-input :deep(.el-input__wrapper.is-focus) {
  background: var(--color-bg-primary);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-focus);
}

.register-input :deep(.el-input__inner) {
  font-size: var(--font-size-base);
}

.register-button {
  width: 100%;
  height: 48px;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  border-radius: var(--radius-lg);
  background: var(--color-primary);
  border: none;
  transition: var(--transition-transform), var(--transition-shadow);
}

.register-button:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.4);
}

.register-button:active {
  transform: translateY(0);
}

/* Footer Links */
.footer-links {
  text-align: center;
  margin-top: 16px;
}

.login-link {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  text-decoration: none;
  transition: var(--transition-colors);
}

.login-link:hover {
  color: var(--color-primary);
}

.login-link span {
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

/* Features */
.features {
  display: flex;
  justify-content: center;
  gap: 32px;
  padding: 20px 40px 32px;
  border-top: 1px solid var(--color-border-light);
  background: var(--color-bg-tertiary);
}

.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.feature-item .el-icon {
  font-size: 20px;
  color: var(--color-primary);
}

/* Copyright */
.copyright {
  text-align: center;
  margin-top: 24px;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

/* Responsive */
@media (max-width: 480px) {
  .register-wrapper {
    padding: 16px;
  }

  .card-header {
    padding: 32px 24px 20px;
  }

  .register-form {
    padding: 0 24px 20px;
  }

  .features {
    padding: 16px 24px 24px;
    gap: 20px;
  }

  .gradient-orb {
    opacity: 0.4;
  }
}
</style>
