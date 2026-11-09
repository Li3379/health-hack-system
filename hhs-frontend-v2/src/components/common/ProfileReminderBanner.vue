<script lang="ts">
// 模块级计数器：横幅全站单实例，但用计数器可避免同页测试/预览重复挂载时 id 冲突。
let bannerIdSeq = 0
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { firstMissingKeyOf, missingFieldsOf } from '@/utils/profileFields'
import type { ProfileCompletenessVO } from '@/types/api'

const props = defineProps<{
  completeness: ProfileCompletenessVO | null
  dismissed: boolean
}>()

const emit = defineEmits<{
  (e: 'complete', focusKey: string | null): void
  (e: 'dismiss'): void
}>()

const titleId = `profile-banner-title-${++bannerIdSeq}`

/**
 * 只有在「档案真的不完整」时才打扰：score < 60。
 * 60 是边界 —— 只填性别 + 出生日期的用户约 60%，按 ROADMAP 成功标准 5 不打扰。
 * null（旧后端 / 加载失败）绝不渲染成 0%。
 */
const show = computed(
  () => props.completeness != null && props.completeness.score < 60 && !props.dismissed
)

/** 未填字段，保持目录顺序（不需要在此重排优先级）。 */
const missing = computed(() => missingFieldsOf(props.completeness?.fields ?? []))

const missingNames = computed(() => missing.value.map(field => field.label).join('、'))

/**
 * 主 CTA 的跳转目标。优先级规则只在 utils/profileFields.ts 实现一次 ——
 * 这里若自己写一份，横幅指向的字段会与徽标和 `?focus=` 定位悄悄分叉。
 */
function goProfileWithFocus(): void {
  emit('complete', firstMissingKeyOf(props.completeness?.fields ?? []))
}

function dismiss(): void {
  emit('dismiss')
}
</script>

<template>
  <div
    v-if="show && props.completeness"
    class="profile-banner"
    role="status"
    :aria-labelledby="titleId"
  >
    <!-- 自绘 div，不用 EP 的 Alert 组件：它的默认插槽渲染进 <p>，两个按钮会被浏览器
         提升出去，且它只提供一个关闭 ×，无法承载「完善档案 / 稍后提醒」双动作。 -->
    <span class="profile-banner__icon" aria-hidden="true">
      <el-icon :size="18"><InfoFilled /></el-icon>
    </span>

    <div class="profile-banner__body">
      <!-- 标题含动态数值，因此区域名称走 aria-labelledby 而不是静态 aria-label -->
      <p :id="titleId" class="profile-banner__title">
        档案完整度 {{ props.completeness.score }}%，还缺 {{ missing.length }} 项
      </p>
      <p class="profile-banner__desc">
        缺少{{ missingNames }}，健康评分与风险结果会按参考值估算。
      </p>
    </div>

    <div class="profile-banner__actions">
      <el-button type="primary" @click="goProfileWithFocus">完善档案</el-button>
      <el-button @click="dismiss">稍后提醒</el-button>
      <button
        type="button"
        class="profile-banner__close"
        aria-label="关闭提醒，7 天内不再显示"
        @click="dismiss"
      >
        ×
      </button>
    </div>
  </div>
</template>

<style scoped>
.profile-banner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  margin-bottom: 24px;
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}

.profile-banner__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: var(--radius-full);
  /* 底纹与图标都是图形用途，Light 3.32:1 达标；强调色不用于文字 */
  background: var(--accent-warm-soft);
  color: var(--accent-warm);
}

.profile-banner__body {
  flex: 1;
  min-width: 0;
}

.profile-banner__title {
  font: 600 14px/1.5 var(--font-ui);
  color: var(--text-1);
}

.profile-banner__desc {
  margin-top: 4px;
  font: 400 14px/1.5 var(--font-ui);
  color: var(--text-2);
}

.profile-banner__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}

.profile-banner__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  /* 18px 用于符号而非文本；颜色走 --text-2（Light 8.38:1） */
  font: 400 18px/1 var(--font-ui);
  color: var(--text-2);
  background: none;
  border: 0;
  cursor: pointer;
}

.profile-banner__close:hover {
  background: var(--surface-2);
  color: var(--text-1);
}

.profile-banner__close:focus-visible {
  outline: 2px solid var(--accent-cool);
  outline-offset: 2px;
}

/* EP 给相邻按钮加 margin-left: 12px，与父级 gap: 8px 叠加会变成 20px 的 off-grid 值。 */
.profile-banner__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 窄屏：动作区整体下移到正文之下，缩进对齐正文左缘（32 图标 + 12 间距）。 */
@media (max-width: 768px) {
  .profile-banner {
    flex-wrap: wrap;
  }

  .profile-banner__actions {
    width: 100%;
    justify-content: flex-start;
    padding-left: 44px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .profile-banner {
    transition: none;
  }
}
</style>