<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEditMode ? '编辑阈值' : '新增阈值'"
    width="760px"
    class="threshold-wizard"
    :close-on-click-modal="false"
    destroy-on-close
    :before-close="handleBeforeClose"
    @update:model-value="handleVisibleChange"
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
          style="width: 100%"
          @change="handleMetricSelect"
        >
          <el-option-group label="健康指标">
            <el-option
              v-for="m in store.alertableHealthMetrics"
              :key="m.canonicalKey"
              :label="`${m.displayName} (${m.unit})`"
              :value="m.canonicalKey"
            />
          </el-option-group>
          <el-option-group v-if="store.alertableWellnessMetrics.length" label="保健指标">
            <el-option
              v-for="m in store.alertableWellnessMetrics"
              :key="m.canonicalKey"
              :label="`${m.displayName} (${m.unit})`"
              :value="m.canonicalKey"
            />
          </el-option-group>
        </el-select>
        <p v-if="!hasAlertableMetrics" class="wizard-empty-hint">暂无可配置的告警指标</p>
      </div>

      <!-- 第 2 步：四项阈值输入 + 实时四区预览条（说明文案回答「不知道怎么填」） -->
      <div v-else-if="currentStep === 1" ref="fieldsFocusRef" class="wizard-panel">
        <div class="threshold-field">
          <div class="threshold-field__header">
            <span class="threshold-field__label">严重高值</span>
            <span v-if="isRecommended('criticalHigh')" class="recommend-badge">推荐</span>
          </div>
          <el-form-item>
            <el-input-number
              v-model="draft.criticalHigh"
              :min="meta?.hardMin"
              :max="meta?.hardMax"
              :precision="meta?.precision"
              :step="meta?.step"
              controls-position="right"
              :disabled="!meta"
              style="width: 100%"
            />
          </el-form-item>
          <p class="threshold-field__hint">
            高于此值发出红色紧急提醒<template v-if="meta">（{{ meta.unit }}）</template>
          </p>
        </div>

        <div class="threshold-field">
          <div class="threshold-field__header">
            <span class="threshold-field__label">警告高值</span>
            <span v-if="isRecommended('warningHigh')" class="recommend-badge">推荐</span>
          </div>
          <el-form-item>
            <el-input-number
              v-model="draft.warningHigh"
              :min="meta?.hardMin"
              :max="meta?.hardMax"
              :precision="meta?.precision"
              :step="meta?.step"
              controls-position="right"
              :disabled="!meta"
              style="width: 100%"
            />
          </el-form-item>
          <p class="threshold-field__hint">
            高于此值发出黄色提醒<template v-if="meta">（{{ meta.unit }}）</template>
          </p>
        </div>

        <div class="threshold-field">
          <div class="threshold-field__header">
            <span class="threshold-field__label">警告低值</span>
            <span v-if="isRecommended('warningLow')" class="recommend-badge">推荐</span>
          </div>
          <el-form-item>
            <el-input-number
              v-model="draft.warningLow"
              :min="meta?.hardMin"
              :max="meta?.hardMax"
              :precision="meta?.precision"
              :step="meta?.step"
              controls-position="right"
              :disabled="!meta"
              style="width: 100%"
            />
          </el-form-item>
          <p class="threshold-field__hint">
            低于此值发出黄色提醒<template v-if="meta">（{{ meta.unit }}）</template>
          </p>
        </div>

        <div class="threshold-field">
          <div class="threshold-field__header">
            <span class="threshold-field__label">严重低值</span>
            <span v-if="isRecommended('criticalLow')" class="recommend-badge">推荐</span>
          </div>
          <el-form-item>
            <el-input-number
              v-model="draft.criticalLow"
              :min="meta?.hardMin"
              :max="meta?.hardMax"
              :precision="meta?.precision"
              :step="meta?.step"
              controls-position="right"
              :disabled="!meta"
              style="width: 100%"
            />
          </el-form-item>
          <p class="threshold-field__hint">
            低于此值发出红色紧急提醒<template v-if="meta">（{{ meta.unit }}）</template>
          </p>
        </div>

        <ThresholdZoneBar :draft="draft" :unit="meta?.unit" />
      </div>

      <!-- 第 3 步：确认保存（自然语言确认语句） -->
      <div v-else class="wizard-panel">
        <div class="confirm-block">
          <h4 class="confirm-block__title">请确认以下告警行为</h4>
          <p ref="confirmRef" tabindex="-1" role="status" class="confirm-sentence">
            <template v-if="meta">
              当 <strong>{{ meta.displayName }}</strong>
              <template v-if="draft.warningHigh != null">
                ≥ <strong>{{ draft.warningHigh }}</strong> 时发出黄色提醒；
              </template>
              <template v-if="draft.criticalHigh != null">
                ≥ <strong>{{ draft.criticalHigh }}</strong> 时发出红色紧急提醒；
              </template>
              <template v-if="draft.warningLow != null">
                ≤ <strong>{{ draft.warningLow }}</strong> 时发出黄色提醒；
              </template>
              <template v-if="draft.criticalLow != null">
                ≤ <strong>{{ draft.criticalLow }}</strong> 时发出红色紧急提醒。
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
        <el-button type="primary" :disabled="primaryDisabled" @click="handlePrimary">
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
import { ERROR_MESSAGES } from '@/utils/errorMessages'
import { hasAnyBoundary, type ThresholdDraft } from '@/utils/thresholdValidation'
import type { UserThreshold } from '@/types/api'
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
}>()

const store = useMetricMetaStore()

type DraftField = keyof ThresholdDraft

const FIELD_KEYS: DraftField[] = ['criticalLow', 'warningLow', 'warningHigh', 'criticalHigh']

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

// 打开时的草稿快照：草稿偏离快照才拦截关闭（T-12-25 防误关丢数据）
let compareDraft: ThresholdDraft = {}
let confirmedLeave = false

const isEditMode = computed(() => props.editing !== null)
const meta = computed(() => store.getDefinition(selectedKey.value))
const hasAlertableMetrics = computed(
  () => store.alertableHealthMetrics.length + store.alertableWellnessMetrics.length > 0
)

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

function handleVisibleChange(visible: boolean): void {
  emit('update:modelValue', visible)
}

function goToStep(step: number): void {
  currentStep.value = Math.min(2, Math.max(0, step))
  void focusCurrentStep()
}

const primaryDisabled = computed(() => {
  if (currentStep.value === 0) return !selectedKey.value || !hasAlertableMetrics.value
  if (currentStep.value === 1) return !hasAnyBoundary(draft)
  // 确认保存步：提交与校验接线完成前保持禁用
  return true
})

function handlePrimary(): void {
  if (primaryDisabled.value) return
  goToStep(currentStep.value + 1)
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
  handleBeforeClose
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
