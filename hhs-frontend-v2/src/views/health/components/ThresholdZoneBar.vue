<template>
  <div class="zone-bar" role="img" :aria-label="zoneBarAriaLabel">
    <div class="zone-bar__band zone-bar__band--critical">
      <span class="zone-bar__name">严重低</span>
      <span class="zone-bar__value">{{ formatBoundary(draft.criticalLow) }}</span>
    </div>
    <div class="zone-bar__band zone-bar__band--warning">
      <span class="zone-bar__name">警告低</span>
      <span class="zone-bar__value">{{ formatBoundary(draft.warningLow) }}</span>
    </div>
    <div class="zone-bar__band zone-bar__band--normal">
      <span class="zone-bar__name">正常</span>
      <span class="zone-bar__value">{{ normalValue }}</span>
    </div>
    <div class="zone-bar__band zone-bar__band--warning">
      <span class="zone-bar__name">警告高</span>
      <span class="zone-bar__value">{{ formatBoundary(draft.warningHigh) }}</span>
    </div>
    <div class="zone-bar__band zone-bar__band--critical">
      <span class="zone-bar__name">严重高</span>
      <span class="zone-bar__value">{{ formatBoundary(draft.criticalHigh) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ThresholdDraft } from '@/utils/thresholdValidation'

/**
 * 四区预览条（THR-04 / UI-SPEC Component Contract 4）。
 *
 * 纯展示组件：只消费 draft + unit，零 store / composable / Element Plus 依赖，
 * 数值随 draft 变化纯 computed 更新，无任何请求。
 *
 * 可用性设计：色带对表面对比度仅 1.08-1.35:1，颜色只作装饰（T-12-21 / WCAG 1.4.1），
 * 带内「名称 + 数值」文字（--text-1）才是信息主通道；完整范围由根元素的
 * aria-label 朗读，未设置的边界读作「未设置」。
 * 所有值经 Vue 插值渲染（自动转义），不注入任何原始 HTML（T-12-20）。
 */
const props = defineProps<{
  draft: ThresholdDraft
  unit?: string
}>()

const UNSET_TEXT = '未设置'
const DASH = '—'

function isUnset(value: number | null | undefined): boolean {
  return value === null || value === undefined
}

/** 色带内数值：null/undefined 一律渲染破折号 —，绝不出现 undefined/null 字样。 */
function formatBoundary(value: number | null | undefined): string {
  return isUnset(value) ? DASH : String(value)
}

/** aria-label 内数值：null/undefined 读作「未设置」。 */
function describeBoundary(value: number | null | undefined): string {
  return isUnset(value) ? UNSET_TEXT : String(value)
}

/**
 * 区间短语：两侧都未设置时整体读作「未设置」而不是「未设置 到 未设置」，
 * 保证全 null 时五段恰好各出现一次「未设置」；仅一侧未设置时保留另一侧数值。
 */
function describeRange(low: number | null | undefined, high: number | null | undefined): string {
  if (isUnset(low) && isUnset(high)) return UNSET_TEXT
  return `${describeBoundary(low)} 到 ${describeBoundary(high)}`
}

/** 正常带数值：两侧都未设置时显示 —，与边界带保持一致的空态语义。 */
const normalValue = computed(() => {
  if (isUnset(props.draft.warningLow) && isUnset(props.draft.warningHigh)) return DASH
  return `${formatBoundary(props.draft.warningLow)} – ${formatBoundary(props.draft.warningHigh)}`
})

/**
 * 完整朗读文本，格式与 UI-SPEC 的示例逐字一致：
 * 严重低：低于 40；警告低：40 到 50；正常：50 到 100；警告高：100 到 120；严重高：高于 120（mmHg）
 * 未设置的边界读作「未设置」；unit 未提供时省略末尾的单位后缀。
 */
const zoneBarAriaLabel = computed(() => {
  const { criticalLow, warningLow, warningHigh, criticalHigh } = props.draft
  const parts = [
    `严重低：${isUnset(criticalLow) ? UNSET_TEXT : `低于 ${criticalLow}`}`,
    `警告低：${describeRange(criticalLow, warningLow)}`,
    `正常：${describeRange(warningLow, warningHigh)}`,
    `警告高：${describeRange(warningHigh, criticalHigh)}`,
    `严重高：${isUnset(criticalHigh) ? UNSET_TEXT : `高于 ${criticalHigh}`}`
  ]
  const label = parts.join('；')
  return props.unit ? `${label}（${props.unit}）` : label
})
</script>

<style scoped>
.zone-bar {
  display: flex;
  gap: 8px; /* sm — 色带间隙，露出 --bg 形成分隔 */
  margin: 12px 0; /* sm-3 上下 */
  width: 100%;
}

.zone-bar__band {
  flex: 1;
  height: 40px; /* 2xl */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  /* 色带内文字一律 --text-1，实测 Dark 12.31-13.62:1 / Light 15.34-15.86:1 */
  color: var(--text-1);
}

.zone-bar__band--critical {
  background: var(--hhs-color-error-tint);
}

.zone-bar__band--warning {
  background: var(--hhs-color-warning-tint);
}

.zone-bar__band--normal {
  background: var(--hhs-color-normal-tint);
}

.zone-bar__name {
  font-size: 12px; /* Label */
  line-height: 1.4;
}

.zone-bar__value {
  font-size: 14px; /* Body */
  line-height: 1.5;
  font-family: var(--font-mono);
}

/* 窄屏：五带换行两行堆叠（3 + 2）而不是压缩，每带保持可读宽度 */
@media (max-width: 640px) {
  .zone-bar {
    flex-wrap: wrap;
  }

  .zone-bar__band {
    flex: 1 1 30%;
  }
}
</style>
