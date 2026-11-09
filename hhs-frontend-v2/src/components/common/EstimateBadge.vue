<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** null / undefined 合法：旧后端或缓存 VO 缺少该字段时整枚徽标不渲染。 */
  completeness: number | null | undefined
}>()

const emit = defineEmits<{
  (e: 'complete'): void
}>()

/**
 * 条件收紧（UI-SPEC Contract 4）：`null` 与 `undefined` 都不渲染 ——
 * 宁可不说，也不显示一个假的「完整度 0%」。而 `0` 是合法值，不能当 falsy 处理。
 */
const show = computed(() => props.completeness != null && props.completeness < 100)

function handleClick(): void {
  emit('complete')
}
</script>

<template>
  <button v-if="show" type="button" class="hhs-estimate-badge" @click="handleClick">
    <!-- 自绘 button，不用 EP 的 Tag 组件：theme.css 把它的样式锁死为 500 11px mono +
         accent 文字色（浅色 3.74:1 不达 AA），且本控件需要 12px 正文、明确的焦点环
         与下划线动作线索。 -->
    <span>基于不完整档案估算</span>
    <span aria-hidden="true">·</span>
    <span class="hhs-estimate-badge__action">完善档案</span>
  </button>
</template>