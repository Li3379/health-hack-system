<script lang="ts">
// 模块级计数器：一个 bundle 内自增，保证同页多个完整度块的标题 id 唯一。
// 刻意不用 useId() —— package.json 声明 vue ^3.4.15，useId 属 3.5+。
let completenessIdSeq = 0
</script>

<script setup lang="ts">
import { computed } from 'vue'
import type { ProfileCompletenessVO } from '@/types/api'

const props = withDefaults(
  defineProps<{
    /** null 合法：旧后端或缓存 VO 缺少该字段时整块不渲染，绝不画成 0%。 */
    completeness: ProfileCompletenessVO | null
    variant?: 'full' | 'compact'
  }>(),
  { variant: 'full' }
)

const labelId = `completeness-label-${++completenessIdSeq}`

/** 三档等级 pill 的修饰类。颜色只是辅助，pill 内文字才是信息载体。 */
function tierClass(level: string): string {
  if (level === '基础档案') return 'hhs-tier-pill--basic'
  if (level === '完整档案') return 'hhs-tier-pill--full'
  return ''
}

/**
 * 影响句。**hasProfile 必须是第一条分支** —— 同一个「缺必需字段」的状态在
 * 「完全没有档案行」与「有档案但稀疏」下含义不同：
 * - 无档案行时 RiskAssessmentServiceImpl 走固定 30/25/25 分支，不使用 35 岁；
 * - 35 岁路径只对 birth_date IS NULL 的历史行可达（HealthProfileRequest.birthDate 是 @NotNull）。
 *
 * 之后按 required → 缺出生日期 / 仅缺性别 拆开：gender 的真实消费方是健康报告与
 * AI 提示词，不是风险引擎，因此不能折进「35 岁」那句。
 */
const impactLine = computed(() => {
  if (!props.completeness) return ''
  const { hasProfile, assumptions, fields } = props.completeness

  if (!hasProfile) {
    return '尚未填写健康档案，评分与风险结果将按通用参考值估算。'
  }

  const missing = fields.filter(field => !field.filled)
  if (missing.length === 0) {
    return '档案信息完整，评分与风险结果均基于真实数据。'
  }

  const missingRequired = missing.filter(field => field.importance === 'required')
  if (missingRequired.some(field => field.key === 'birthDate')) {
    return '缺少出生日期，风险评估将按 35 岁与通用模型估算。'
  }
  if (missingRequired.some(field => field.key === 'gender')) {
    return '缺少性别，健康报告与 AI 建议将按通用表述生成。'
  }

  const missingRecommended = missing.filter(field => field.importance === 'recommended')
  if (missingRecommended.length > 0) {
    // 由 payload 自己的 assumptions 数组判定，绝不在此重算 bmi 规则：
    // 该数组由后端 assumptionsFor 产出，与风险引擎的分支同源。
    // bmi.filled 要求身高+体重+bmi 三者齐备（粘性豁免），因此 filled 无法区分
    // 「从未记录 bmi」与「身高体重被清空但 bmi 列仍在」—— assumptions 才是正确的信号。
    const bmiAssumption = fields.find(field => field.key === 'bmi')?.assumption
    if (bmiAssumption != null && assumptions.includes(bmiAssumption)) {
      return '缺少身高、体重或 BMI，风险评估将按 BMI 参考值 22.0 估算。'
    }
    // 引擎在 bmi 列非空时沿用已记录值（RiskAssessmentServiceImpl:120），此时承诺 22.0 是假的
    return '缺少身高或体重，BMI 无法随身体变化重算，风险评估沿用已记录的 BMI。'
  }

  return '缺少血型、过敏史、家族病史或生活习惯，健康报告与 AI 建议的个性化程度会降低。'
})
</script>

<template>
  <!-- full：档案页。视觉分段条对读屏隐藏，数字与图例承载同一信息。 -->
  <section
    v-if="props.completeness && props.variant === 'full'"
    class="completeness"
    :aria-labelledby="labelId"
  >
    <div class="completeness__head">
      <span :id="labelId" class="completeness__label">档案完整度</span>
      <span class="completeness__score">{{ props.completeness.score }}%</span>
      <span class="hhs-tier-pill" :class="tierClass(props.completeness.level)">
        {{ props.completeness.level }}
      </span>
      <span class="completeness__count">
        已填 {{ props.completeness.filledCount }} / {{ props.completeness.totalCount }}
      </span>
    </div>

    <div class="hhs-completeness-meter completeness__meter" aria-hidden="true">
      <span
        v-for="field in props.completeness.fields"
        :key="field.key"
        class="hhs-completeness-meter__seg"
        :class="{ 'is-filled': field.filled }"
      ></span>
    </div>

    <ul class="completeness__legend">
      <li
        v-for="field in props.completeness.fields"
        :key="field.key"
        class="completeness__legend-item"
      >
        <span
          class="completeness__state"
          :class="
            field.filled ? 'completeness__state--filled' : 'completeness__state--missing'
          "
          aria-hidden="true"
          >{{ field.filled ? '✓' : '○' }}</span
        >
        <!-- 符号本身无独立语义：读屏必须听到「已填 / 未填」而不是一个孤立字符 -->
        <span class="visually-hidden">{{ field.filled ? '已填' : '未填' }}</span>
        <span>{{ field.label }}</span>
      </li>
    </ul>

    <p class="completeness__impact">{{ impactLine }}</p>
  </section>

  <!-- compact：仪表盘健康评分卡。无图例、无影响句；链接自成一行动作。 -->
  <div v-else-if="props.completeness" class="completeness completeness--compact">
    <div class="completeness__head">
      <span class="completeness__label">档案完整度</span>
      <span class="completeness__score">{{ props.completeness.score }}%</span>
      <span class="hhs-tier-pill" :class="tierClass(props.completeness.level)">
        {{ props.completeness.level }}
      </span>
    </div>
    <div
      class="hhs-completeness-meter hhs-completeness-meter--compact completeness__meter"
      aria-hidden="true"
    >
      <span
        v-for="field in props.completeness.fields"
        :key="field.key"
        class="hhs-completeness-meter__seg"
        :class="{ 'is-filled': field.filled }"
      ></span>
    </div>
    <router-link class="completeness__link" to="/prevention/profile">完善档案</router-link>
  </div>
</template>

<style scoped>
.completeness {
  display: block;
}

/* flex-wrap 让「62%」与 pill 永远不折半截；窄屏时整行换成多行而不是溢出 */
.completeness__head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
}

.completeness__label {
  font: 400 14px/1.5 var(--font-ui);
  color: var(--text-2);
}

.completeness__score {
  font: 600 24px/1.2 var(--font-ui);
  color: var(--text-1);
  /* 数值变化时不抖动 */
  font-variant-numeric: tabular-nums;
}

.completeness__count {
  font: 400 12px/1.4 var(--font-ui);
  color: var(--text-2);
}

.completeness__meter {
  margin-top: 12px;
}

/* 换行而不是截断：九项图例在窄屏分成多行，每项本身不折（见 __legend-item） */
.completeness__legend {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.completeness__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font: 400 12px/1.4 var(--font-ui);
  color: var(--text-2);
  /* 「✓ 家族病史」不被拆到两行 */
  white-space: nowrap;
}

.completeness__state {
  width: 12px;
  text-align: center;
  line-height: 1.4;
}

/* 图形色：Light #0d9488 on #ffffff = 3.74:1（≥3:1 通过）；Dark 12.64:1 */
.completeness__state--filled {
  color: var(--accent-cool);
}

/* Light 8.38:1 / Dark 11.30:1，说明性小字一律不用 --text-3（浅色 3.97:1） */
.completeness__state--missing {
  color: var(--text-2);
}

.completeness__impact {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
  font: 400 14px/1.5 var(--font-ui);
  color: var(--text-2);
}

.completeness__link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  font: 400 12px/1.4 var(--font-ui);
  color: var(--text-1);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.completeness__link:focus-visible {
  outline: 2px solid var(--accent-cool);
  outline-offset: 2px;
}

/* 仓库内此前没有该工具类（已 grep 确认）。放在 scoped 块内是因为只有本组件使用。 */
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}
</style>