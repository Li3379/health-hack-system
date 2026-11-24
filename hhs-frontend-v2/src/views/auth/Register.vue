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
          @submit.prevent="handleRegister"
        >
          <!-- 汇总块必须是 <el-form> 的第一个子节点（Contract 4.3）。 -->
          <FormErrorSummary
            ref="summaryRef"
            :summary-items="registerErrors.summaryItems"
            @focus-field="registerErrors.focusField"
          />

          <el-form-item
            prop="username"
            data-field="username"
            :error="registerErrors.fieldErrors['username']"
          >
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              class="register-input"
              @update:model-value="registerErrors.clearField('username')"
            />
          </el-form-item>

          <el-form-item
            prop="password"
            data-field="password"
            :error="registerErrors.fieldErrors['password']"
          >
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码 (至少6位)"
              :prefix-icon="Lock"
              show-password
              class="register-input"
              @update:model-value="registerErrors.clearField('password')"
            />
          </el-form-item>

          <el-form-item
            prop="confirmPassword"
            data-field="confirmPassword"
            :error="registerErrors.fieldErrors['confirmPassword']"
          >
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              :prefix-icon="Lock"
              show-password
              class="register-input"
              @update:model-value="registerErrors.clearField('confirmPassword')"
            />
          </el-form-item>

          <el-form-item
            prop="email"
            data-field="email"
            :error="registerErrors.fieldErrors['email']"
          >
            <el-input
              v-model="form.email"
              placeholder="请输入邮箱 (可选)"
              :prefix-icon="Message"
              class="register-input"
              @update:model-value="registerErrors.clearField('email')"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              native-type="submit"
              :loading="loading"
              class="register-button"
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
            <el-icon><Key /></el-icon>
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
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { User, Lock, Message, Key, TrendCharts, ChatDotRound } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { showError } from '@/utils/showError'
import { useFormServerErrors, collectInvalidFieldMessages } from '@/composables/useFormServerErrors'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import { gsap, magneticHover, buttonPress, hoverShine, rippleClick, DUR, EASE } from '@/composables/useGsap'

const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const summaryRef = ref<InstanceType<typeof FormErrorSummary> | null>(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: ''
})

/**
 * 服务端字段错误投影（Contract 4）。
 *
 * 四个字段分三类（这是本页最容易说错的地方，勿混）：
 * - **三个服务端可达**：`username`（`@NotBlank` + `@Size`）、`password`（`@NotBlank` + `@Size`）、
 *   `email`（`@Email`）—— 它们是 `RegisterRequest` 上真实存在的约束，也是本页实际提交的三个字段。
 * - **一个纯客户端**：`confirmPassword` —— `RegisterRequest` **没有**这个字段，后端永远不会在
 *   `fieldErrors` 里下发它。它在投影表里只作为防御性绑定存在，真正生效的是下面那条
 *   自定义的「两次密码一致」客户端规则。
 * - **一个不提交**：`nickname` —— DTO 上声明但无约束，且本页提交时不发送，因此不绑定、
 *   也不加入 payload。
 * 不传 `labels`：四个标签都已在全局 `FIELD_LABELS` 中（用户名 / 密码 / 确认密码 / 邮箱）。
 */
const registerErrors = reactive(
  useFormServerErrors({ fieldOrder: ['username', 'password', 'confirmPassword', 'email'] })
)

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

onMounted(() => {
  // ── Floating orbs with parallax depth ──
  gsap.to('.orb-1', { x: 30, y: -30, scale: 1.05, duration: 10, yoyo: true, repeat: -1, ease: EASE.inOut })
  gsap.to('.orb-2', { x: -20, y: 20, scale: 0.95, duration: 12, yoyo: true, repeat: -1, ease: EASE.inOut, delay: 2 })
  gsap.to('.orb-3', { x: -30, y: -20, scale: 1.02, duration: 14, yoyo: true, repeat: -1, ease: EASE.inOut, delay: 4 })

  // ── Dramatic entrance sequence ──
  const tl = gsap.timeline({ defaults: { ease: EASE.expo } })

  // Logo: spin-in
  tl.fromTo('.logo-container',
    { scale: 0, rotation: -360, autoAlpha: 0 },
    { scale: 1, rotation: 0, autoAlpha: 1, duration: DUR.dramatic, ease: EASE.back, clearProps: 'transform,autoAlpha,visibility,opacity' }
  )

  // Title: 3D flip
  tl.fromTo('.title',
    { y: 60, rotationX: -90, autoAlpha: 0 },
    { y: 0, rotationX: 0, autoAlpha: 1, duration: DUR.slow, ease: EASE.back, clearProps: 'transform,autoAlpha,visibility,opacity' },
    '-=0.6'
  )

  // Subtitle
  tl.fromTo('.subtitle',
    { y: 30, autoAlpha: 0 },
    { y: 0, autoAlpha: 1, duration: DUR.mid, clearProps: 'transform,autoAlpha,visibility,opacity' },
    '-=0.3'
  )

  // Form fields: alternating slide with spring
  // 交互控件只能用 opacity 做入场：autoAlpha 会打 visibility:hidden，
  // 入场期间输入框与按钮既无法命中测试也无法获得焦点（同 Login.vue）。
  tl.fromTo('.register-form .el-form-item',
    { x: (i: number) => (i % 2 === 0 ? -50 : 50), opacity: 0 },
    { x: 0, opacity: 1, stagger: 0.08, duration: DUR.mid, ease: EASE.back, clearProps: 'transform,opacity' },
    '-=0.2'
  )

  // Button: spring pop
  tl.fromTo('.register-button',
    { scale: 0.5, opacity: 0 },
    { scale: 1, opacity: 1, duration: DUR.mid, ease: EASE.back, clearProps: 'transform,opacity' },
    '-=0.1'
  )

  // Footer（含「立即登录」router-link，属于交互控件，同样不能用 autoAlpha）
  tl.fromTo('.footer-links',
    { opacity: 0, y: 15 },
    { opacity: 1, y: 0, duration: DUR.fast, clearProps: 'transform,opacity' },
    '-=0.1'
  )

  // Features: spring pop from random
  tl.fromTo('.feature-item',
    { scale: 0, autoAlpha: 0 },
    { scale: 1, autoAlpha: 1, stagger: 0.06, duration: DUR.mid, ease: EASE.back, clearProps: 'transform,autoAlpha,visibility,opacity' },
    '-=0.1'
  )

  tl.fromTo('.copyright',
    { autoAlpha: 0 },
    { autoAlpha: 1, duration: DUR.fast, clearProps: 'autoAlpha,visibility,opacity' },
    '-=0.1'
  )

  // ── Logo glow pulse ──
  gsap.to('.logo-container', {
    filter: 'drop-shadow(0 0 25px rgba(37, 99, 235, 0.4))',
    duration: 1.5,
    yoyo: true,
    repeat: -1,
    ease: EASE.inOut,
  })

  // ── Magnetic hover on button ──
  magneticHover('.register-button', { strength: 0.3, radius: 120 })

  // ── Interaction: button press + ripple ──
  buttonPress('.register-button', { scale: 0.94 })
  rippleClick('.register-button', { color: 'rgba(255,255,255,0.3)' })

  // ── Interaction: shine on card ──
  hoverShine('.register-card', { color: 'rgba(94, 234, 212, 0.1)' })

  // ── Interaction: input focus glow ──
  document.querySelectorAll('.register-form .el-input__wrapper').forEach((input) => {
    input.addEventListener('focus', () => {
      gsap.to(input, { boxShadow: '0 0 0 3px rgba(94, 234, 212, 0.25)', duration: 0.3, ease: 'power2.out' })
    })
    input.addEventListener('blur', () => {
      gsap.to(input, { boxShadow: 'none', duration: 0.3, ease: 'power2.out' })
    })
  })
})

const handleRegister = async () => {
  if (!formRef.value) return

  // 上一轮的残留投影不进入本次尝试；只清投影，不碰用户输入（ERR-05）。
  registerErrors.reset()

  await formRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      // 客户端校验失败也走同一份汇总通道（GOV.UK 双呈现，Contract 4.3），零 toast。
      registerErrors.setErrors(
        collectInvalidFieldMessages(
          invalidFields as Record<string, Array<{ message?: string }>> | undefined
        )
      )
      await nextTick()
      summaryRef.value?.focusSummary()
      return
    }

    loading.value = true
    try {
      await authStore.register({
        username: form.username,
        password: form.password,
        email: form.email || undefined
      })
    } catch (error) {
      // 拦截器只翻译不展示：注册失败（用户名重复等）必须由本调用点呈现，
      // 否则会变成未处理的 Promise 拒绝且用户无任何反馈。
      // validation → 0 toast，汇总 + 内联承担呈现；其余 kind 由 showError 呈现。
      // 本页没有任何页级状态块，因此 forbidden / notFound 必须 opt-in 一条常驻 toast。
      const err = showError(error, 'auth.registerFailed', { presentStates: true })
      if (err.kind === 'validation') {
        registerErrors.apply(err)
        await nextTick()
        summaryRef.value?.focusSummary()
      }
    } finally {
      loading.value = false
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
}

.orb-1 {
  width: 600px;
  height: 600px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  top: -200px;
  right: -100px;
}

.orb-2 {
  width: 500px;
  height: 500px;
  background: linear-gradient(135deg, var(--color-success) 0%, #059669 100%);
  bottom: -150px;
  left: -100px;
}

.orb-3 {
  width: 400px;
  height: 400px;
  background: linear-gradient(135deg, var(--color-warning) 0%, #d97706 100%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
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
