<script lang="ts">
// 模块级计数器：一个 bundle 内自增，保证同页多个汇总块的标题 id 唯一。
// 刻意不用 useId() —— package.json 声明 vue ^3.4.15，useId 属 3.5+（UI-SPEC 的 floor 约束）。
let summaryIdSeq = 0
</script>

<script setup lang="ts">
import { ref } from 'vue'
import type { SummaryItem } from '@/composables/useFormServerErrors'

const props = defineProps<{
  summaryItems: SummaryItem[]
}>()

const emit = defineEmits<{
  (e: 'focus-field', field: string): void
}>()

const titleId = `form-error-summary-title-${++summaryIdSeq}`
const summaryRef = ref<HTMLElement | null>(null)

/**
 * 供调用方在 apply() 后的 nextTick 里聚焦汇总块。
 * 组件内部不 watch props 自动抢焦点 —— 否则每敲一个字都会跳焦。
 */
function focusSummary(): void {
  summaryRef.value?.focus()
}

defineExpose({ summaryRef, focusSummary })

// 显式 emit 而非模板内 $emit：让 emits 契约对调用方可枚举。
function handleEntryClick(field: string): void {
  emit('focus-field', field)
}
</script>

<template>
  <div
    v-if="props.summaryItems.length"
    ref="summaryRef"
    class="form-error-summary"
    tabindex="-1"
    role="group"
    :aria-labelledby="titleId"
  >
    <!-- ElAlert 只承载标题与图标：它的默认插槽渲染在 <p> 内，
         列表放进去会被浏览器提升出去，破坏整块底色。 -->
    <el-alert type="error" :closable="false" show-icon>
      <template #title>
        <span :id="titleId">请修正以下 {{ props.summaryItems.length }} 项填写问题</span>
      </template>
    </el-alert>

    <ol class="form-error-summary__list">
      <li v-for="item in props.summaryItems" :key="item.field">
        <button
          type="button"
          class="form-error-summary__link"
          @click="handleEntryClick(item.field)"
        >
          {{ item.label ? `${item.label}：` : '' }}{{ item.message }}
        </button>
      </li>
    </ol>
  </div>
</template>

<style scoped>
.form-error-summary {
  margin-bottom: 16px;
  border-radius: var(--radius-md);
  background: var(--hhs-color-error-tint);
  overflow: hidden;
}

.form-error-summary :deep(.el-alert) {
  --el-alert-bg-color: transparent;
  --el-alert-title-font-size: 14px;
  padding: 12px 16px 8px;
}

/* UI-SPEC Typography：汇总标题 14px / 600 / 1.4。
   上面的变量只给字号，字重必须显式声明，否则按 EP 默认 400 渲染。 */
.form-error-summary :deep(.el-alert__title) {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  /* ElAlert 的 error 类型把标题色写死成 --el-color-error（#f56c6c），
     在亮色 tint 上只有 2.61:1。标题与条目必须同色，统一走错误 token。 */
  color: var(--hhs-color-error-text);
}

.form-error-summary__list {
  margin: 0;
  padding: 0 16px 12px 40px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
  line-height: 1.5;
  max-height: 144px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.form-error-summary__link {
  display: inline;
  text-align: left;
  background: none;
  border: 0;
  padding: 0;
  color: var(--hhs-color-error-text);
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
  font: inherit;
}

.form-error-summary__link:focus-visible {
  outline: 2px solid var(--hhs-color-error-text);
  outline-offset: 2px;
}
</style>
