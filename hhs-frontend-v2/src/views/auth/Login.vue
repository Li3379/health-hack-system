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
        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large" class="login-form" @submit.prevent="handleLogin">
          <!-- 汇总块必须是 <el-form> 的第一个子节点（Contract 4.3）。 -->
          <FormErrorSummary
            ref="summaryRef"
            :summary-items="loginErrors.summaryItems"
            @focus-field="loginErrors.focusField"
          />

          <el-form-item
            prop="username"
            data-field="username"
            :error="loginErrors.fieldErrors['username']"
          >
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              @change="syncFromNative"
              @update:model-value="loginErrors.clearField('username')"
            />
          </el-form-item>

          <el-form-item
            prop="password"
            data-field="password"
            :error="loginErrors.fieldErrors['password']"
          >
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              @change="syncFromNative"
              @update:model-value="loginErrors.clearField('password')"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" native-type="submit" :loading="loading" class="login-btn">
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
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { User, Lock, Monitor, ChatDotRound, TrendCharts } from '@element-plus/icons-vue'
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
  password: ''
})

/**
 * 服务端字段错误投影（Contract 4）。
 *
 * 不传 `labels`：`username`（用户名）与 `password`（密码）已在全局 `FIELD_LABELS` 中，
 * 本页没有语义歧义，不需要 per-call 覆盖。
 * 用 `reactive()` 持有返回值，让模板里 `loginErrors.fieldErrors['username']` 读到字符串
 * 而不是 Ref。
 */
const loginErrors = reactive(useFormServerErrors({ fieldOrder: ['username', 'password'] }))

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ]
}

// 同步浏览器 autofill 填充的原生值到 Vue reactive form
const syncFromNative = () => {
  const inputs = document.querySelectorAll<HTMLInputElement>('.login-form input')
  if (inputs[0]?.value) form.username = inputs[0].value
  if (inputs[1]?.value) form.password = inputs[1].value
}

onMounted(() => {
  // ── Dramatic page entrance ──
  const tl = gsap.timeline({ defaults: { ease: EASE.expo } })

  // Logo: scale-in with rotation
  tl.fromTo('.logo-container',
    { scale: 0, rotation: -180, autoAlpha: 0 },
    { scale: 1, rotation: 0, autoAlpha: 1, duration: DUR.dramatic, ease: EASE.back, clearProps: 'transform,autoAlpha,visibility,opacity' }
  )

  // Title: 3D flip reveal
  tl.fromTo('.card-title',
    { y: 50, rotationX: -90, autoAlpha: 0 },
    { y: 0, rotationX: 0, autoAlpha: 1, duration: DUR.slow, ease: EASE.back, clearProps: 'transform,autoAlpha,visibility,opacity' },
    '-=0.6'
  )

  // Subtitle: slide up
  tl.fromTo('.card-subtitle',
    { y: 30, autoAlpha: 0 },
    { y: 0, autoAlpha: 1, duration: DUR.mid, clearProps: 'transform,autoAlpha,visibility,opacity' },
    '-=0.3'
  )

  // Form fields: alternating left/right with stagger
  // 交互控件只能用 opacity 做入场：autoAlpha 会打 visibility:hidden，
  // 入场期间输入框与按钮既无法命中测试也无法获得焦点（见 LoginEntranceInteraction.spec.ts）。
  tl.fromTo('.login-form .el-form-item',
    { x: (i: number) => (i % 2 === 0 ? -60 : 60), opacity: 0 },
    { x: 0, opacity: 1, stagger: 0.08, duration: DUR.mid, ease: EASE.back, clearProps: 'transform,opacity' },
    '-=0.2'
  )

  // Button: spring pop
  tl.fromTo('.login-btn',
    { scale: 0.5, opacity: 0 },
    { scale: 1, opacity: 1, duration: DUR.mid, ease: EASE.back, clearProps: 'transform,opacity' },
    '-=0.1'
  )

  // Footer（含「立即注册」router-link，属于交互控件，同样不能用 autoAlpha）
  tl.fromTo('.form-footer',
    { opacity: 0, y: 15 },
    { opacity: 1, y: 0, duration: DUR.fast, clearProps: 'transform,opacity' },
    '-=0.1'
  )

  // Feature chips: spring pop from random directions
  tl.fromTo('.feature-chip',
    { scale: 0, autoAlpha: 0 },
    { scale: 1, autoAlpha: 1, stagger: 0.06, duration: DUR.mid, ease: EASE.back, clearProps: 'transform,autoAlpha,visibility,opacity' },
    '-=0.1'
  )

  // Magnetic hover on login button
  magneticHover('.login-btn', { strength: 0.3, radius: 120 })

  // ── Interaction: button press feedback ──
  buttonPress('.login-btn', { scale: 0.94 })

  // ── Interaction: shine sweep on card ──
  hoverShine('.login-card', { color: 'rgba(94, 234, 212, 0.12)' })

  // ── Interaction: ripple on button click ──
  rippleClick('.login-btn', { color: 'rgba(255,255,255,0.3)' })

  // ── Interaction: input focus glow ──
  document.querySelectorAll('.login-form .el-input__wrapper').forEach((input) => {
    input.addEventListener('focus', () => {
      gsap.to(input, { boxShadow: '0 0 0 3px rgba(94, 234, 212, 0.25)', duration: 0.3, ease: 'power2.out' })
    })
    input.addEventListener('blur', () => {
      gsap.to(input, { boxShadow: 'none', duration: 0.3, ease: 'power2.out' })
    })
  })

  // Floating background orbs animation
  gsap.to('.bg-glow', {
    backgroundPosition: '100% 100%',
    duration: 20,
    ease: EASE.none,
    repeat: -1,
    yoyo: true,
  })
})

const handleLogin = async () => {
  if (!formRef.value) return

  // 上一轮的残留投影不进入本次尝试；只清投影，不碰用户输入（ERR-05）。
  loginErrors.reset()

  syncFromNative()
  try {
    await formRef.value.validate()
  } catch (invalidFields) {
    // 客户端校验失败也走同一份汇总通道（GOV.UK 双呈现，Contract 4.3），零 toast。
    // EP 的 validate() 拒绝值就是 invalidFields，因此直接投影，不需要第二套提示。
    loginErrors.setErrors(
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
    await authStore.login(form)
  } catch (error) {
    // 拦截器只翻译不展示：登录失败（密码错误 / 限流 / 账号禁用）必须由本调用点呈现。
    // validation → 0 toast，汇总 + 内联承担呈现；其余 kind 由 showError 呈现。
    // 本页没有任何页级状态块，因此 forbidden / notFound 必须 opt-in 一条常驻 toast。
    const err = showError(error, 'auth.loginFailed', { presentStates: true })
    if (err.kind === 'validation') {
      loginErrors.apply(err)
      await nextTick()
      summaryRef.value?.focusSummary()
    }
  } finally {
    loading.value = false
  }
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
  pointer-events: none;
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
