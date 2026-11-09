<template>
  <el-dialog
    v-model="visible"
    :title="isEditMode ? '编辑阈值' : '新增阈值'"
    width="760px"
    class="threshold-wizard"
    :close-on-click-modal="false"
    destroy-on-close
    :before-close="handleBeforeClose"
  >
    <div class="wizard-steps">
      <el-steps :active="currentStep" finish-status="finish" align-center>
        <el-step
          :title="isEditMode ? '指标已锁定' : '选择指标'"
          :class="{ 'is-skipped': isEditMode }"
        />
        <el-step title="设置阈值" />
        <el-step title="确认保存" />
      </el-steps>
    </div>

    <div class="wizard-body">
      <!-- 第 1 步：元数据驱动的指标选择器（D-04/D-05：仅 alertable、空组隐藏、显示名 (单位)） -->
      <div v-if="currentStep === 0" ref="pickerFocusRef" class="wizard-panel">
        <el-select
          v-model="selectedKey"
          placeholder="请选择指标"
          filterable
          :disabled="isEditMode"
          popper-class="threshold-wizard-options"
          style="width: 100%"
          @change="handleMetricSelect"
        >
          <el-option-group label="健康指标">
            <el-option
              v-for="m in store.alertableHealthMetrics"
              :key="m.canonicalKey"
              :label="`${m.displayName} (${m.unit})`"
              :value="m.canonicalKey"
              :title="`${m.displayName} (${m.unit})`"
            />
          </el-option-group>
          <el-option-group v-if="store.alertableWellnessMetrics.length" label="保健指标">
            <el-option
              v-for="m in store.alertableWellnessMetrics"
              :key="m.canonicalKey"
              :label="`${m.displayName} (${m.unit})`"
              :value="m.canonicalKey"
              :title="`${m.displayName} (${m.unit})`"
            />
          </el-option-group>
        </el-select>
        <p v-if="!hasAlertableMetrics" class="wizard-empty-hint">暂无可配置的告警指标</p>
      </div>

      <!-- 第 2 步：四项阈值输入 + 实时四区预览条（说明文案回答「不知道怎么填」） -->
      <div v-else-if="currentStep === 1" ref="fieldsFocusRef" class="wizard-panel">
        <FormErrorSummary :summary-items="summaryItems" @focus-field="focusField" />
        <div class="threshold-field" :class="{ 'is-invalid': !!mergedErrors.criticalHigh }">
          <div class="threshold-field__header">
            <span class="threshold-field__label">严重高值</span>
            <span v-if="isRecommended('criticalHigh')" class="recommend-badge">推荐</span>
          </div>
          <el-form-item
            prop="criticalHigh"
            data-field="criticalHigh"
            :error="mergedErrors.criticalHigh"
          >
            <el-input-number
              v-model="draft.criticalHigh"
              :min="meta?.hardMin"
              :max="meta?.hardMax"
              :precision="meta?.precision"
              :step="meta?.step"
              controls-position="right"
              :disabled="!meta"
              style="width: 100%"
              @update:model-value="clearField('criticalHigh')"
            />
          </el-form-item>
          <p class="threshold-field__hint">
            高于此值发出红色紧急提醒<template v-if="meta">（{{ meta.unit }}）</template>
          </p>
        </div>

        <div class="threshold-field" :class="{ 'is-invalid': !!mergedErrors.warningHigh }">
          <div class="threshold-field__header">
            <span class="threshold-field__label">警告高值</span>
            <span v-if="isRecommended('warningHigh')" class="recommend-badge">推荐</span>
          </div>
          <el-form-item
            prop="warningHigh"
            data-field="warningHigh"
            :error="mergedErrors.warningHigh"
          >
            <el-input-number
              v-model="draft.warningHigh"
              :min="meta?.hardMin"
              :max="meta?.hardMax"
              :precision="meta?.precision"
              :step="meta?.step"
              controls-position="right"
              :disabled="!meta"
              style="width: 100%"
              @update:model-value="clearField('warningHigh')"
            />
          </el-form-item>
          <p class="threshold-field__hint">
            高于此值发出黄色提醒<template v-if="meta">（{{ meta.unit }}）</template>
          </p>
        </div>

        <div class="threshold-field" :class="{ 'is-invalid': !!mergedErrors.warningLow }">
          <div class="threshold-field__header">
            <span class="threshold-field__label">警告低值</span>
            <span v-if="isRecommended('warningLow')" class="recommend-badge">推荐</span>
          </div>
          <el-form-item prop="warningLow" data-field="warningLow" :error="mergedErrors.warningLow">
            <el-input-number
              v-model="draft.warningLow"
              :min="meta?.hardMin"
              :max="meta?.hardMax"
              :precision="meta?.precision"
              :step="meta?.step"
              controls-position="right"
              :disabled="!meta"
              style="width: 100%"
              @update:model-value="clearField('warningLow')"
            />
          </el-form-item>
          <p class="threshold-field__hint">
            低于此值发出黄色提醒<template v-if="meta">（{{ meta.unit }}）</template>
          </p>
        </div>

        <div class="threshold-field" :class="{ 'is-invalid': !!mergedErrors.criticalLow }">
          <div class="threshold-field__header">
            <span class="threshold-field__label">严重低值</span>
            <span v-if="isRecommended('criticalLow')" class="recommend-badge">推荐</span>
          </div>
          <el-form-item
            prop="criticalLow"
            data-field="criticalLow"
            :error="mergedErrors.criticalLow"
          >
            <el-input-number
              v-model="draft.criticalLow"
              :min="meta?.hardMin"
              :max="meta?.hardMax"
              :precision="meta?.precision"
              :step="meta?.step"
              controls-position="right"
              :disabled="!meta"
              style="width: 100%"
              @update:model-value="clearField('criticalLow')"
            />
          </el-form-item>
          <p class="threshold-field__hint">
            低于此值发出红色紧急提醒<template v-if="meta">（{{ meta.unit }}）</template>
          </p>
        </div>

        <ThresholdZoneBar :draft="draft" :unit="meta?.unit" />
      </div>

      <!-- 第 3 步：确认保存（自然语言确认语句，只陈述已填边界；全空降级为固定文案） -->
      <div v-else class="wizard-panel">
        <div class="confirm-block">
          <h4 class="confirm-block__title">请确认以下告警行为</h4>
          <p ref="confirmRef" tabindex="-1" role="status" class="confirm-sentence">
            <template v-if="confirmationSentence.length === 0">尚未设置任何有效阈值</template>
            <template v-else>
              <template v-for="(seg, index) in confirmationSentence" :key="index">
                <strong v-if="seg.strong">{{ seg.text }}</strong>
                <template v-else>{{ seg.text }}</template>
              </template>
            </template>
          </p>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="wizard-footer">
        <el-button v-if="currentStep > 0" @click="goToStep(currentStep - 1)">上一步</el-button>
        <el-button v-if="currentStep === 1" link type="primary" @click="restoreRecommended">
          恢复推荐值
        </el-button>
        <el-button @click="handleCancel">放弃设置</el-button>
        <el-button
          type="primary"
          :disabled="primaryDisabled"
          :loading="saving"
          @click="handlePrimary"
        >
          {{ currentStep === 2 ? '保存阈值' : '下一步' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useMetricMetaStore } from '@/stores/metricMeta'
import { useAuthStore } from '@/stores/auth'
import { ERROR_MESSAGES, fieldLabel } from '@/utils/errorMessages'
import {
  hasAnyBoundary,
  validateThresholdOrder,
  type ThresholdDraft
} from '@/utils/thresholdValidation'
import { useFormServerErrors, type SummaryItem } from '@/composables/useFormServerErrors'
import { normalizeApiError } from '@/utils/appError'
import { thresholdApi } from '@/api/threshold'
import type { UserThreshold, UserThresholdRequest } from '@/types/api'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import ThresholdZoneBar from './ThresholdZoneBar.vue'

/**
 * 阈值配置三步向导（UI-SPEC Contract 1/2/3/5 的承载组件）。
 *
 * 元数据驱动（D-03/D-06）：alertable 列表、推荐四值、precision/step/hardMin/hardMax
 * 全部来自 metricMeta store（离线 fallback 同步可用，零加载态），组件不做独立计算、
 * 不维护指标白名单。
 *
 * 错误呈现遵循 Phase 11 契约：向导自身零 toast（D-12/T-12-24）；「推荐」徽标自绘
 * span 而非 EP 标签组件（theme.css 会用 !important 把该组件锁成 11px/500 字级）。
 */
const props = defineProps<{
  modelValue: boolean
  editing: UserThreshold | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved'): void
  (e: 'error', error: unknown): void
}>()

const store = useMetricMetaStore()
const authStore = useAuthStore()

type DraftField = keyof ThresholdDraft

const FIELD_KEYS: DraftField[] = ['criticalLow', 'warningLow', 'warningHigh', 'criticalHigh']

// 汇总块排序必须与页面字段顺序一致（GOV.UK 原则，Phase 11 D-01）
const SUMMARY_FIELD_ORDER = ['criticalLow', 'warningLow', 'warningHigh', 'criticalHigh']

// D-01：ElSteps 三步 —— 0 选择指标 / 1 设置阈值 / 2 确认保存
const currentStep = ref(0)
const selectedKey = ref('')
const pickerFocusRef = ref<HTMLElement | null>(null)
const fieldsFocusRef = ref<HTMLElement | null>(null)
const confirmRef = ref<HTMLElement | null>(null)

const draft = reactive<ThresholdDraft>({
  criticalLow: null,
  warningLow: null,
  warningHigh: null,
  criticalHigh: null
})

// Phase 11 服务端 fieldErrors 投影：apply() 只吃 validation kind，绝不清空输入、绝不 toast
const { fieldErrors, apply, clearField, reset, focusField } = useFormServerErrors({
  fieldOrder: SUMMARY_FIELD_ORDER
})

// 保存进行中的提交闩锁：主按钮 loading 并阻止重复提交
const saving = ref(false)

// 打开时的草稿快照：草稿偏离快照才拦截关闭（T-12-25 防误关丢数据）
let compareDraft: ThresholdDraft = {}
let confirmedLeave = false

// el-dialog 双向绑定（仓库标准模式，同 UniversalMetricDialog.vue）：EP 内部所有关闭路径
// （ESC/X/守卫）经同一可写 computed 出口回写父组件，避免单向绑定在 destroy-on-close
// 下与 EP 内部可见性脱同步导致遮罩滞留（UAT gap 1 / THR-06）
const visible = computed({
  get: () => props.modelValue,
  set: val => emit('update:modelValue', val)
})

const isEditMode = computed(() => props.editing !== null)
const meta = computed(() => store.getDefinition(selectedKey.value))
const hasAlertableMetrics = computed(
  () => store.alertableHealthMetrics.length + store.alertableWellnessMetrics.length > 0
)

/**
 * 客户端校验（D-09：即时反馈、非权威；权威拦截在后端 @ValidThresholdRange）。
 * 次序违规命名涉及的两个字段，同一句挂到两侧；null = 无界永不违规（D-10）。
 */
const clientViolations = computed(() => validateThresholdOrder(draft, meta.value))

/** 服务端 fieldErrors 与客户端违规合并：同一字段以客户端结果优先呈现（实时）。 */
const mergedErrors = computed<Record<string, string>>(() => {
  const merged: Record<string, string> = { ...fieldErrors.value }
  for (const violation of clientViolations.value) {
    for (const field of violation.fields) {
      merged[field] = violation.message
    }
  }
  return merged
})

/** GOV.UK 汇总块数据：顺序与字段声明顺序一致，标签来自 fieldLabel（D-15）。 */
const summaryItems = computed<SummaryItem[]>(() => {
  const items: SummaryItem[] = []
  for (const field of SUMMARY_FIELD_ORDER) {
    const message = mergedErrors.value[field]
    if (message) items.push({ field, label: fieldLabel(field), message })
  }
  return items
})

/** 确认语句分段：strong 段加粗（指标名/数值，同字号只加字重），纯文本段承载连接语。 */
interface SentenceSegment {
  text: string
  strong: boolean
}

/**
 * 第 3 步确认语句（THR-06 / Contract 5）：只为已填写边界生成分句并按
 * 高值对 → 低值对顺序拼接；全部为 null 时返回空数组，由模板降级为固定文案。
 * 纯展示元素（role="status"），唯一的「状态处理」就是省略 null 分句与全空降级。
 */
const confirmationSentence = computed<SentenceSegment[]>(() => {
  if (!meta.value || !hasAnyBoundary(draft)) return []
  const segments: SentenceSegment[] = [
    { text: '当 ', strong: false },
    { text: meta.value.displayName, strong: true }
  ]
  if (draft.warningHigh != null) {
    segments.push(
      { text: ' ≥ ', strong: false },
      { text: String(draft.warningHigh), strong: true },
      { text: ' 时发出黄色提醒；', strong: false }
    )
  }
  if (draft.criticalHigh != null) {
    segments.push(
      { text: ' ≥ ', strong: false },
      { text: String(draft.criticalHigh), strong: true },
      { text: ' 时发出红色紧急提醒；', strong: false }
    )
  }
  if (draft.warningLow != null) {
    segments.push(
      { text: ' ≤ ', strong: false },
      { text: String(draft.warningLow), strong: true },
      { text: ' 时发出黄色提醒；', strong: false }
    )
  }
  if (draft.criticalLow != null) {
    segments.push(
      { text: ' ≤ ', strong: false },
      { text: String(draft.criticalLow), strong: true },
      { text: ' 时发出红色紧急提醒。', strong: false }
    )
  }
  return segments
})

// 每次打开重置状态（destroy-on-close 语义的显式初始化）
watch(
  () => props.modelValue,
  visible => {
    if (!visible) return
    resetForOpen()
    void focusCurrentStep()
  },
  { immediate: true }
)

function resetForOpen(): void {
  confirmedLeave = false
  reset()
  saving.value = false
  if (props.editing) {
    // D-02：编辑模式跳过第 1 步直接进入第 2 步，指标锁定不可改
    selectedKey.value = props.editing.metricKey
    draft.criticalLow = props.editing.criticalLow ?? null
    draft.warningLow = props.editing.warningLow ?? null
    draft.warningHigh = props.editing.warningHigh ?? null
    draft.criticalHigh = props.editing.criticalHigh ?? null
    currentStep.value = 1
  } else {
    selectedKey.value = ''
    draft.criticalLow = null
    draft.warningLow = null
    draft.warningHigh = null
    draft.criticalHigh = null
    currentStep.value = 0
  }
  compareDraft = {
    criticalLow: draft.criticalLow,
    warningLow: draft.warningLow,
    warningHigh: draft.warningHigh,
    criticalHigh: draft.criticalHigh
  }
}

/** D-08：选定指标立即把后端推荐四值写入草稿（缺失项落 null = 无界）。 */
function handleMetricSelect(key: string): void {
  selectedKey.value = key
  const recommended = store.getDefinition(key)?.recommended
  draft.criticalLow = recommended?.criticalLow ?? null
  draft.warningLow = recommended?.warningLow ?? null
  draft.warningHigh = recommended?.warningHigh ?? null
  draft.criticalHigh = recommended?.criticalHigh ?? null
}

/** D-08：恢复推荐值 —— 四项回到 meta.recommended。 */
function restoreRecommended(): void {
  if (selectedKey.value) handleMetricSelect(selectedKey.value)
}

/** 徽标只挂在「仍等于推荐值且非空」的字段上；用户改过即消失。 */
function isRecommended(field: DraftField): boolean {
  const value = draft[field]
  const recommended = meta.value?.recommended?.[field]
  return value !== null && value !== undefined && recommended != null && value === recommended
}

function isDirty(): boolean {
  return FIELD_KEYS.some(key => (draft[key] ?? null) !== (compareDraft[key] ?? null))
}

/**
 * 关闭拦截（T-12-25）：头部 X / Esc / 底部「放弃设置」共用。
 * 草稿与打开快照一致 → 直接关闭；否则确认离开，取消走 catch 零提示（D-12）。
 */
function handleBeforeClose(done: () => void): void {
  if (!isDirty() || confirmedLeave) {
    done()
    return
  }
  ElMessageBox.confirm(ERROR_MESSAGES['dialog.unsavedConfirm'], '提示', {
    type: 'warning',
    confirmButtonText: '放弃设置',
    cancelButtonText: '继续编辑'
  })
    .then(() => {
      confirmedLeave = true
      done()
    })
    .catch(() => {
      // 用户选择继续编辑：零提示、不关闭（D-12）
    })
}

function handleCancel(): void {
  handleBeforeClose(() => emit('update:modelValue', false))
}

function goToStep(step: number): void {
  currentStep.value = Math.min(2, Math.max(0, step))
  void focusCurrentStep()
}

const primaryDisabled = computed(() => {
  if (currentStep.value === 0) return !selectedKey.value || !hasAlertableMetrics.value
  // D-10/D-12：仅次序违规或四项全空时禁用；部分填写合法，违规清零输入框内容
  return clientViolations.value.length > 0 || !hasAnyBoundary(draft)
})

function handlePrimary(): void {
  if (primaryDisabled.value) return
  if (currentStep.value < 2) {
    goToStep(currentStep.value + 1)
    return
  }
  void handleSubmit()
}

/**
 * 保存（T-12-26）：userId 来自认证 store 而非用户输入，后端会按令牌重新判定归属。
 * 校验失败 → apply() 内联 + 汇总、零 toast、弹窗保持且输入原样（D-12）；
 * 其他失败 → emit 给父级，由页面唯一的错误出口呈现单条 toast（T-12-24）。
 */
async function handleSubmit(): Promise<void> {
  if (saving.value || primaryDisabled.value) return
  reset()
  const payload: UserThresholdRequest = {
    userId: authStore.user?.id ?? 0,
    metricKey: selectedKey.value,
    warningHigh: draft.warningHigh ?? undefined,
    criticalHigh: draft.criticalHigh ?? undefined,
    warningLow: draft.warningLow ?? undefined,
    criticalLow: draft.criticalLow ?? undefined
  }
  saving.value = true
  try {
    if (props.editing) {
      await thresholdApi.updateThreshold(props.editing.id, payload)
    } else {
      await thresholdApi.createThreshold(payload)
    }
    // 关闭必须先于父级刷新（UAT gap 1 根因修复）：若 emit('saved') 与关闭在同一 tick
    // 触发，父级 handleSaved 的列表刷新会与 dialog 的 leave 过渡抢同一帧，Vue transition
    // 的 enter-from 类残留、leave 动画永不结束，EP after-leave 不派发 → 遮罩滞留。
    // 先翻 modelValue 让关闭过渡独占这一帧，再在下一 tick 通知父级刷新列表。
    emit('update:modelValue', false)
    await nextTick()
    emit('saved')
  } catch (raw) {
    const apiError = normalizeApiError(raw)
    if (apiError.kind === 'validation') {
      apply(apiError)
      // 校验失败发生在第 3 步提交时：退回第 2 步让汇总块与内联错误可见（GOV.UK 双呈现），
      // 焦点随步骤规则落到第一个输入框；草稿与弹窗保持原样（D-12）。
      goToStep(1)
    } else {
      emit('error', raw)
    }
  } finally {
    saving.value = false
  }
}

/** 步骤切换焦点规则（Contract 1）：新步第一个控件获得焦点，不自动滚动。 */
async function focusCurrentStep(): Promise<void> {
  await nextTick()
  if (currentStep.value === 0) {
    pickerFocusRef.value?.querySelector('input')?.focus()
  } else if (currentStep.value === 1) {
    fieldsFocusRef.value?.querySelector('input')?.focus()
  } else {
    confirmRef.value?.focus()
  }
}

defineExpose({
  currentStep,
  selectedKey,
  draft,
  handleMetricSelect,
  restoreRecommended,
  goToStep,
  handleBeforeClose,
  handleSubmit
})
</script>

<style scoped>
/* 对话框高度：内容超出时 body 纵向滚动，头部与底部按钮保持固定 */
.threshold-wizard {
  display: flex;
  flex-direction: column;
  max-height: calc(85vh - 64px);
}

.threshold-wizard :deep(.el-dialog__body) {
  flex: 1;
  overflow-y: auto;
}

.wizard-steps {
  margin-bottom: 8px; /* sm */
}

.wizard-steps :deep(.el-steps) {
  padding: 24px 0 0; /* lg — 覆盖 EP 无上下留白的默认 */
}

.wizard-steps :deep(.el-step__title) {
  font-size: 14px; /* 覆盖 EP 的 16px → Body */
  line-height: 1.4; /* 覆盖 EP 的固定 38px 行高 */
}

.wizard-steps :deep(.el-step__title.is-process) {
  font-weight: 600; /* 覆盖 EP 的 700 → 本契约只有 400/600 两档 */
}

/* finish-status="finish" 输出 is-finish；同时覆盖 is-success 防改回 success。
   文字用 --text-2（浅色 8.38:1），accent-cool 浅色仅 3.74:1 不可作文字 */
.wizard-steps :deep(.el-step__title.is-finish),
.wizard-steps :deep(.el-step__title.is-success) {
  color: var(--text-2);
}

.wizard-steps :deep(.el-step__head.is-process) {
  color: var(--accent-cool); /* 当前步序号数字（图形，≥3:1 通过） */
  border-color: var(--accent-cool);
}

.wizard-steps :deep(.el-step__icon-inner) {
  font-weight: 600; /* 覆盖 EP 的 700 */
}

/* 编辑模式跳过的第 1 步：视觉置灰（D-02） */
.wizard-steps :deep(.el-step.is-skipped) {
  opacity: 0.45;
}

.wizard-body {
  padding: 0 16px 32px; /* md 左右 / xl 底部 */
  min-height: 360px; /* 防步骤切换时对话框高度跳动 */
}

.wizard-footer {
  margin-top: 16px; /* md */
  display: flex;
  justify-content: flex-end;
  gap: 8px; /* sm */
}

.wizard-empty-hint {
  margin-top: 8px; /* sm */
  font-size: 12px; /* Label */
  line-height: 1.4;
  color: var(--text-2);
}

/* 四项阈值输入（Contract 3） */
.threshold-field + .threshold-field {
  margin-top: 12px; /* sm-3 */
}

/* EP 的 .el-form-item 默认 margin-bottom: 18px（off-grid），垂直节奏交给 .threshold-field */
.threshold-field :deep(.el-form-item) {
  margin-bottom: 0;
}

/* EP 的加减按钮字形是 13px（第 5 种字号），归一到 14px Body */
.threshold-field :deep(.el-input-number__decrease),
.threshold-field :deep(.el-input-number__increase) {
  font-size: 14px;
}

.threshold-field__header {
  display: flex;
  align-items: center;
  gap: 4px; /* xs */
  margin-bottom: 4px; /* xs */
}

.threshold-field__label {
  font-size: 14px; /* Body */
  font-weight: 600; /* theme.css 的 500 只作用于 el-form-item__label，此处自绘 span 不受影响 */
  line-height: 1.4;
  color: var(--text-1);
}

/* 违反层级的两个字段同时标红（Contract 3 §层级校验的呈现），复用 Phase 11 错误 token */
.threshold-field.is-invalid .threshold-field__label {
  color: var(--hhs-color-error-text);
}

/* 确认语句（Contract 5） */
.confirm-block__title {
  font-size: 18px; /* Heading */
  font-weight: 600;
  line-height: 1.3;
  color: var(--text-1);
  margin-bottom: 12px; /* sm-3 */
}

.confirm-sentence {
  padding: 12px 16px; /* sm-3 / md */
  font-size: 14px; /* Body */
  line-height: 1.5;
  color: var(--text-1);
  background: var(--surface-2);
  border-radius: var(--radius-md);
}

.confirm-sentence:focus {
  outline: 2px solid var(--accent-cool); /* 焦点环为非文字图形，≥3:1 通过 */
  outline-offset: 2px;
}

/* theme.css 用 !important 把 .el-input__inner 锁成 15px，必须同等优先级覆盖回 14px */
.threshold-field :deep(.el-input__inner) {
  font: 400 14px/1.5 var(--font-ui) !important;
}

.recommend-badge {
  padding: 0 8px; /* sm 水平 */
  font-size: 12px; /* Label */
  line-height: 1.4;
  color: var(--text-1);
  background: var(--accent-cool-soft);
  border-radius: var(--radius-sm);
}

.threshold-field__hint {
  margin-top: 4px; /* xs */
  font-size: 12px; /* Label */
  line-height: 1.4;
  /* --text-3 浅色实测仅 3.97:1 不达 AA，说明文字一律 --text-2（浅色 8.38:1） */
  color: var(--text-2);
}
</style>

<!-- 指标下拉面板经 popper teleport 到 body，scoped 无法触达；用专用 popper-class 约束（UI-SPEC 长文本与溢出） -->
<style>
.threshold-wizard-options .el-select-dropdown__item {
  height: 34px;
  line-height: 34px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.threshold-wizard-options .el-select-dropdown__wrap {
  max-height: 264px; /* 超出即面板内滚动，六项以上也不撑破视口 */
}
</style>
