<script setup lang="ts">
import { computed } from 'vue'
import { WarningFilled, Lock, DocumentDelete } from '@element-plus/icons-vue'

const props = defineProps<{
  /** 变体只决定图标与配色，文案一律由 props 传入（UI-SPEC Component Contract 1） */
  variant?: 'network' | 'server' | 'forbidden' | 'notFound' | 'load'
  /** 失败对象，≤16 字 */
  title: string
  /** 原因与下一步，≤40 字；为空时不渲染该行 */
  description?: string
  /** 重试回调；缺省时不渲染按钮（不出现灰色死按钮） */
  retry?: () => void | Promise<void>
  /** 重试按钮的可访问名（动词 + 对象）；缺省时组合为 `重试：{title}` */
  retryLabel?: string
  /** 重试进行中：按钮进入 loading 并禁用，整块 aria-busy=true */
  retrying?: boolean
}>()

// load / server / network 共用同一警告图形，靠文案区分 —— 29 个页面不能出现图标大杂烩。
const ICONS = {
  load: WarningFilled,
  server: WarningFilled,
  network: WarningFilled,
  forbidden: Lock,
  notFound: DocumentDelete
}

const icon = computed(() => ICONS[props.variant ?? 'load'])

// 只有"加载/服务/网络"这三类走错误色；403/404 是中性色，不能当作故障吓唬用户。
const tinted = computed(() => ['load', 'server', 'network'].includes(props.variant ?? 'load'))

// 可访问名必须带对象：屏幕阅读器不会把周围的可见文案一起读给按钮。
const accessibleRetryName = computed(() => props.retryLabel || `重试：${props.title}`)

// 组件自身不维护 loading 状态，也不自动重试第二次 —— 重试节奏由父组件的 retrying 控制。
async function handleRetry(): Promise<void> {
  await props.retry?.()
}
</script>

<template>
  <div
    class="error-state"
    role="status"
    aria-live="polite"
    :aria-busy="retrying ? 'true' : 'false'"
  >
    <div
      class="error-state__icon-circle"
      :class="{ 'error-state__icon-circle--muted': !tinted }"
    >
      <el-icon :size="28">
        <component :is="icon" />
      </el-icon>
    </div>

    <h3 class="error-state__title">{{ title }}</h3>

    <p v-if="description" class="error-state__desc">{{ description }}</p>

    <div v-if="retry" class="error-state__actions">
      <el-button
        type="primary"
        size="large"
        :loading="retrying"
        :aria-label="accessibleRetryName"
        @click="handleRetry"
      >
        重试
      </el-button>
    </div>
  </div>
</template>

<style scoped>
/* 页面级加载失败块：占据内容区，与 ElEmpty 的空结果语义严格分离。 */
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 8px; /* 图标圆 → 标题 → 说明，两段各 8px（sm token） */
  min-height: 320px;
  max-width: 480px;
  margin: 0 auto;
  padding: 48px 24px;
  background: var(--surface-1);
  border: 1px dashed var(--border);
  border-radius: var(--radius-lg);
}

.error-state__icon-circle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: var(--radius-full);
  background: var(--hhs-color-error-tint);
  color: var(--hhs-color-error-text);
}

/* forbidden / notFound：中性灰，不把"无权访问 / 不存在"渲染成故障。 */
.error-state__icon-circle--muted {
  background: rgba(141, 144, 156, 0.12);
  color: var(--text-3);
}

.error-state__title {
  margin: 0;
  font: 600 18px/1.3 var(--font-ui);
  color: var(--text-1);
}

.error-state__desc {
  margin: 0;
  font: 400 14px/1.5 var(--font-ui);
  color: var(--text-3);
}

/* 说明 → 按钮是 lg（24px），比上面的 8px 大：父级 gap 8px + 此处 16px = 24px。
   不用单一 gap 表达，否则 24px 会被压成 8px；也不写 24px，否则总间距会变成 32px。 */
.error-state__actions {
  margin-top: 16px;
}
</style>