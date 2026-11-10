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
  /**
   * 几何档位（UI-SPEC Contract 2 §2.1）。
   * - 'content'（默认）：内容区卡片块，Phase 11 行为不变
   * - 'page'：403 全页解释态 —— 无卡片面、无虚线边，垂直占满内容区
   */
  scope?: 'content' | 'page'
  /**
   * 有序动作列表（UI-SPEC Contract 2/3）。
   * 非空时**不渲染** `retry` 按钮（二者互斥；需要重试就显式写一条 action）。
   * `label` 同时是可见文本与可访问名 —— 必须是「动词 + 宾语」。
   */
  actions?: Array<{
    label: string
    onClick: () => void | Promise<void>
    variant?: 'primary' | 'default'
  }>
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

// 页级（403）与内容区级（404 等）两档几何 —— 见 UI-SPEC Contract 2 §2.3。
const isPage = computed(() => props.scope === 'page')

// actions 与 retry 互斥：actions 非空时只渲染 actions。
const hasActions = computed(() => (props.actions?.length ?? 0) > 0)

// 组件自身不维护 loading 状态，也不自动重试第二次 —— 重试节奏由父组件的 retrying 控制。
async function handleRetry(): Promise<void> {
  // Vue 不会 await 事件处理器返回的 Promise，未捕获的拒绝会变成未处理拒绝且无反馈。
  // 错误呈现由父组件拥有（与 showError 的 `Promise.resolve(...).catch()` 一致）。
  try {
    await props.retry?.()
  } catch {
    /* parent owns the error surface */
  }
}

// 动作点击同理：拒绝由父组件负责呈现，组件只保证不产生未处理拒绝。
async function handleAction(action: { onClick: () => void | Promise<void> }): Promise<void> {
  try {
    await action.onClick()
  } catch {
    /* parent owns the error surface */
  }
}
</script>

<template>
  <div
    class="error-state"
    :class="{ 'error-state--page': isPage }"
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

    <!-- actions 与 retry 互斥：actions 非空时不渲染 retry 按钮。 -->
    <div v-if="hasActions" class="error-state__actions">
      <el-button
        v-for="action in actions"
        :key="action.label"
        :type="action.variant === 'primary' ? 'primary' : undefined"
        size="large"
        :aria-label="action.label"
        @click="handleAction(action)"
      >
        {{ action.label }}
      </el-button>
    </div>
    <div v-else-if="retry" class="error-state__actions">
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
  /* Phase 11 用 --text-3（Light 3.97:1 不达 AA），本阶段收紧为 --text-2（8.38:1）。 */
  color: var(--text-2);
}

/* 说明 → 按钮是 lg（24px），比上面的 8px 大：父级 gap 8px + 此处 16px = 24px。
   不用单一 gap 表达，否则 24px 会被压成 8px；也不写 24px，否则总间距会变成 32px。
   字符串 toast 与状态块共用同一套渲染节奏。 */
.error-state__actions {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

/* theme.css 用 !important 把主按钮锁成 500 14px/1（行高 1 会裁中文字形），
   次按钮是 EP 的 500 字重；两处都归一到 400/600 两档。 */
.error-state :deep(.el-button--primary) {
  font: 600 14px/1.5 var(--font-ui) !important;
}
.error-state :deep(.el-button:not(.el-button--primary)) {
  font-weight: 400 !important;
  line-height: 1.5 !important;
}
/* EP 给相邻按钮加 margin-left: 12px，与 gap 叠加会变 20px */
.error-state :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 页级（403 全页解释态）：不画卡片面、不画虚线边，坐在 --bg 上；垂直填满内容区。
   UI-SPEC Contract 2 §2.3 的五个区分通道之一：表面与高度都与内容区级不同。 */
.error-state--page {
  /* 页级高度 = 视口 − 页头 64px − 宿主页自身的垂直内边距。
     宿主通过 --hhs-page-pad-y 声明自己的上下内边距合计；未声明时为 0px
     （padding:0 的宿主如 health/Metrics.vue 无需任何改动）。
     不得把 104 硬编码进来：104 = 64 + 40，而 40 只对 DevicePlatformConfig 成立。
     不得改成 min-height:100%：宿主页根类高度是 auto，百分比会解析成 auto 而归零。
     见 UI-SPEC Spacing 的宿主相关说明。 */
  min-height: calc(100vh - 64px - var(--hhs-page-pad-y, 0px));
  max-width: 720px; /* 页级说明句一行可达 40 字，480px 会折 3 行 */
  padding: 48px 24px;
  background: transparent;
  border: 0;
  border-radius: 0;
}

/* 页级标题升级为 Display（24px/600/1.2）—— 与内容区级 18px 形成第一处「一眼可分」。 */
.error-state--page .error-state__title {
  font: 600 24px/1.2 var(--font-ui);
}

@media (max-width: 639px) {
  .error-state__actions {
    flex-direction: column;
    align-items: stretch;
  }
  .error-state__actions :deep(.el-button) {
    width: 100%;
  }
  /* 窄屏放弃满高：手机上强制 100vh 会把唯一动作推到折叠线以下，
     用户必须滚动才能看到出口 —— 宁可让块贴内容高。 */
  .error-state--page {
    min-height: 0;
    padding: 48px 16px;
  }
}
</style>