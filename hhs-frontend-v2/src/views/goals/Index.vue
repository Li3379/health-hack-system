<template>
  <div class="goals-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>健康目标</span>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            创建目标
          </el-button>
        </div>
      </template>
      <el-skeleton :loading="loading" animated>
        <template #template>
          <el-row :gutter="20">
            <el-col v-for="n in 3" :key="n" :xs="24" :sm="12" :md="8">
              <el-card shadow="hover" class="goal-card">
                <el-skeleton-item variant="text" style="width: 70%; height: 20px;" />
                <el-skeleton-item variant="text" style="width: 100%; height: 14px; margin-top: 12px;" />
                <el-skeleton-item variant="rect" style="width: 100%; height: 8px; margin-top: 16px;" />
              </el-card>
            </el-col>
          </el-row>
        </template>
        <template #default>
          <el-empty v-if="!loading && goals.length === 0" description="暂无健康目标" />
          <el-row v-else :gutter="20">
            <el-col v-for="goal in goals" :key="goal.id" :xs="24" :sm="12" :md="8">
              <el-card shadow="hover" class="goal-card" :class="{ 'goal-completed': goal.status === 'COMPLETED' }">
                <div class="goal-header">
                  <span class="goal-title">{{ goal.title }}</span>
                  <el-tag :type="getStatusTagType(goal.status)" size="small">
                    {{ getStatusLabel(goal.status) }}
                  </el-tag>
                </div>
                <div v-if="goal.description" class="goal-desc">{{ goal.description }}</div>
                <div class="goal-progress">
                  <div class="progress-info">
                    <span class="progress-label">{{ goal.metricKey }}</span>
                    <span class="progress-value">{{ goal.currentValue }} / {{ goal.targetValue }} {{ goal.unit }}</span>
                  </div>
                  <el-progress :percentage="getProgressPercent(goal)" :color="getProgressColor(goal)" :stroke-width="8" />
                </div>
                <div class="goal-date">{{ goal.startDate }} ~ {{ goal.endDate }}</div>
                <div class="goal-actions">
                  <el-button v-if="goal.status !== 'COMPLETED'" type="primary" size="small" @click="showProgressDialog(goal)">记录进度</el-button>
                  <!-- 删除按钮暂时移除：后端 DELETE /api/goals/{id} 端点已回滚，留着只会点出 405。 -->
                </div>
              </el-card>
            </el-col>
          </el-row>
        </template>
      </el-skeleton>
    </el-card>
    <el-dialog v-model="createVisible" title="创建健康目标" width="500px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <!-- 汇总块必须是 <el-form> 的第一个子节点（Contract 4.3），弹窗与全页表单同一位置。 -->
        <FormErrorSummary
          ref="createSummaryRef"
          :summary-items="createErrors.summaryItems"
          @focus-field="createErrors.focusField"
        />
        <el-form-item
          label="目标标题"
          prop="title"
          data-field="title"
          :error="createErrors.fieldErrors['title']"
        >
          <el-input
            v-model="createForm.title"
            placeholder="例: 降低体重至70kg"
            @update:model-value="createErrors.clearField('title')"
          />
        </el-form-item>
        <el-form-item label="目标描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" placeholder="可选描述" />
        </el-form-item>
        <el-form-item
          label="指标类型"
          prop="metricKey"
          data-field="metricKey"
          :error="createErrors.fieldErrors['metricKey']"
        >
          <el-select
            v-model="createForm.metricKey"
            placeholder="请选择指标"
            style="width: 100%"
            @change="createErrors.clearField('metricKey')"
          >
            <el-option label="体重" value="weight" />
            <el-option label="BMI" value="bmi" />
            <el-option label="心率" value="heartRate" />
            <el-option label="血糖" value="glucose" />
            <el-option label="收缩压" value="systolicBP" />
            <el-option label="舒张压" value="diastolicBP" />
            <el-option label="步数" value="steps" />
            <el-option label="运动时长" value="exerciseMinutes" />
            <el-option label="睡眠时长" value="sleepDuration" />
          </el-select>
        </el-form-item>
        <el-form-item
          label="目标值"
          prop="targetValue"
          data-field="targetValue"
          :error="createErrors.fieldErrors['targetValue']"
        >
          <el-input-number
            v-model="createForm.targetValue"
            :precision="1"
            :min="0"
            style="width: 100%"
            @update:model-value="createErrors.clearField('targetValue')"
          />
        </el-form-item>
        <el-form-item
          label="单位"
          prop="unit"
          data-field="unit"
          :error="createErrors.fieldErrors['unit']"
        >
          <el-input
            v-model="createForm.unit"
            placeholder="例: kg, 次/分, 步"
            @update:model-value="createErrors.clearField('unit')"
          />
        </el-form-item>
        <!-- 范围选择器的根节点自身就是 .el-input__wrapper，:error 绑在 el-form-item 上，
             不给内部两个 input 各绑一次（Contract 4.1）。 -->
        <el-form-item
          label="起止日期"
          prop="dateRange"
          data-field="dateRange"
          :error="createErrors.fieldErrors['dateRange']"
        >
          <el-date-picker
            v-model="createForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            @change="createErrors.clearField('dateRange')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">确认创建</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="progressVisible" title="记录进度" width="400px">
      <el-form ref="progressFormRef" :model="progressForm" :rules="progressRules" label-width="80px">
        <FormErrorSummary
          ref="progressSummaryRef"
          :summary-items="progressErrors.summaryItems"
          @focus-field="progressErrors.focusField"
        />
        <el-form-item
          label="当前值"
          prop="value"
          data-field="value"
          :error="progressErrors.fieldErrors['value']"
        >
          <el-input-number
            v-model="progressForm.value"
            :precision="1"
            :min="0"
            style="width: 100%"
            @update:model-value="progressErrors.clearField('value')"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="progressForm.note" type="textarea" :rows="2" placeholder="可选备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingProgress" @click="handleAddProgress">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { goalsApi, type HealthGoal } from '@/api/goals'
import { useFormServerErrors, collectInvalidFieldMessages } from '@/composables/useFormServerErrors'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import { showError } from '@/utils/showError'
import {
  staggerReveal, progressAnimate, cleanupScrollTriggers,
  tilt3D, buttonPress, hoverLift, rippleClick, DUR, EASE,
} from '@/composables/useGsap'

const loading = ref(false)
const submitting = ref(false)
const submittingProgress = ref(false)
const createVisible = ref(false)
const progressVisible = ref(false)
const goals = ref<HealthGoal[]>([])
const currentGoalId = ref<number | null>(null)
const createFormRef = ref<FormInstance>()
const progressFormRef = ref<FormInstance>()
const createSummaryRef = ref<InstanceType<typeof FormErrorSummary> | null>(null)
const progressSummaryRef = ref<InstanceType<typeof FormErrorSummary> | null>(null)

const createForm = reactive({
  title: '', description: '', metricKey: '', targetValue: 0, unit: '',
  dateRange: null as [string, string] | null
})

const progressForm = reactive({ value: 0, note: '' })

/**
 * 两个表单各一个投影实例：字段顺序与标签都不同。
 *
 * `title` 在本页是「目标标题」而非全局表的「标题」，`value` 是「当前值」/「进度值」
 * 而非「指标数值」—— 按 Contract 4.5 规则 4 用 per-call `labels` 覆盖，绝不改全局表
 * （改全局表会把档案页、阈值页的 title 一起误标）。
 *
 * 用 `reactive()` 持有返回值，让 `fieldErrors` / `summaryItems` 在模板里按普通对象读取
 * （否则 `xxx.fieldErrors['title']` 拿到的是 Ref 而不是字符串）。
 */
const createErrors = reactive(useFormServerErrors({
  fieldOrder: ['title', 'metricKey', 'targetValue', 'unit', 'dateRange'],
  labels: { title: '目标标题', description: '目标描述', value: '当前值' }
}))

const progressErrors = reactive(useFormServerErrors({
  fieldOrder: ['value'],
  labels: { value: '进度值' }
}))

/**
 * 客户端校验失败 → 与服端校验共用同一份汇总通道（GOV.UK 双呈现，D-01），零 toast。
 * 抽成一个函数只因为本页有两个表单：共享收集器的调用点仍然只有一处
 * （它是 14-03 交付的无状态纯函数），不是本地重新实现。
 */
type ProjectableErrors = typeof createErrors

const projectClientErrors = async (
  target: ProjectableErrors,
  invalidFields: Record<string, Array<{ message?: string }>> | undefined,
  summary: InstanceType<typeof FormErrorSummary> | null | undefined
): Promise<void> => {
  target.setErrors(collectInvalidFieldMessages(invalidFields))
  await nextTick()
  summary?.focusSummary()
}

const createRules: FormRules = {
  title: [{ required: true, message: '请输入目标标题', trigger: 'blur' }],
  metricKey: [{ required: true, message: '请选择指标类型', trigger: 'change' }],
  targetValue: [{ required: true, message: '请输入目标值', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
  dateRange: [{ required: true, message: '请选择起止日期', trigger: 'change' }]
}
const progressRules: FormRules = { value: [{ required: true, message: '请输入当前值', trigger: 'blur' }] }

const statusLabels: Record<string, string> = { ACTIVE: '进行中', COMPLETED: '已完成', PAUSED: '已暂停', EXPIRED: '已过期' }
const statusTagTypes: Record<string, string> = { ACTIVE: 'primary', COMPLETED: 'success', PAUSED: 'warning', EXPIRED: 'info' }
const getStatusLabel = (s: string): string => statusLabels[s] || s
const getStatusTagType = (s: string): string => statusTagTypes[s] || 'info'
const getProgressPercent = (g: HealthGoal): number => g.targetValue === 0 ? 0 : Math.min(100, Math.round((g.currentValue / g.targetValue) * 100))
const getProgressColor = (g: HealthGoal): string => { const p = getProgressPercent(g); return p >= 100 ? '#67c23a' : p >= 70 ? '#409eff' : p >= 40 ? '#e6a23c' : '#f56c6c' }

/**
 * 列表加载失败：本页不渲染错误状态块，因此 `forbidden` / `notFound` 没有落点，
 * 必须显式 opt-in 一条常驻 toast —— 静默是唯一不可接受的结果。
 */
const fetchGoals = async () => {
  loading.value = true
  try {
    const r = await goalsApi.list()
    goals.value = r.data || []
  } catch (error) {
    showError(error, 'goal.loadFailed', { presentStates: true })
  } finally {
    loading.value = false
  }
}

/** 弹窗打开时的初始化：不是失败路径，绝不搬进任何 catch（会破坏 ERR-05）。 */
const showCreateDialog = () => { createForm.title = ''; createForm.description = ''; createForm.metricKey = ''; createForm.targetValue = 0; createForm.unit = ''; createForm.dateRange = null; createVisible.value = true }

const handleCreate = async () => {
  if (!createFormRef.value) return

  // 上一轮的残留投影不进入本次尝试；只清投影，不碰用户输入（ERR-05）。
  createErrors.reset()

  await createFormRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      // 客户端校验失败也走同一份汇总通道（GOV.UK 双呈现），零 toast。
      await projectClientErrors(createErrors, invalidFields, createSummaryRef.value)
      return
    }

    submitting.value = true
    try {
      await goalsApi.create({
        title: createForm.title,
        description: createForm.description || undefined,
        metricKey: createForm.metricKey,
        targetValue: createForm.targetValue,
        unit: createForm.unit,
        startDate: createForm.dateRange![0],
        endDate: createForm.dateRange![1]
      })
      // 成功路径顺序保持不变：关闭弹窗只在创建解析成功之后。
      ElMessage.success('目标创建成功')
      createVisible.value = false
      fetchGoals()
    } catch (error) {
      // validation → 0 toast，汇总 + 内联承担呈现；其余 kind 由 showError 呈现。
      const err = showError(error, 'goal.saveFailed', { presentStates: true })
      if (err.kind === 'validation') {
        createErrors.apply(err)
        await nextTick()
        createSummaryRef.value?.focusSummary()
      }
    } finally {
      submitting.value = false
    }
  })
}

const showProgressDialog = (g: HealthGoal) => { currentGoalId.value = g.id; progressForm.value = g.currentValue; progressForm.note = ''; progressVisible.value = true }

const handleAddProgress = async () => {
  if (!progressFormRef.value || !currentGoalId.value) return

  progressErrors.reset()

  await progressFormRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      await projectClientErrors(progressErrors, invalidFields, progressSummaryRef.value)
      return
    }

    submittingProgress.value = true
    try {
      await goalsApi.addProgress(currentGoalId.value!, {
        value: progressForm.value,
        note: progressForm.note || undefined
      })
      ElMessage.success('进度更新成功')
      progressVisible.value = false
      fetchGoals()
    } catch (error) {
      // `GoalServiceImpl:103` 在这个动作上抛 GOAL_NOT_FOUND：目标已被删掉时若不 opt-in，
      // 一次失败的「记录进度」会完全静默。
      const err = showError(error, 'goal.saveFailed', { presentStates: true })
      if (err.kind === 'validation') {
        progressErrors.apply(err)
        await nextTick()
        progressSummaryRef.value?.focusSummary()
      }
    } finally {
      submittingProgress.value = false
    }
  })
}

/**
 * 删除功能已整体移除：后端 DELETE /api/goals/{id} 端点被回滚，前端保留调用只会得到 405。
 * 将来若重新提供删除能力，需要后端端点与这里的确认/提示逻辑一起加回来。
 */

onMounted(async () => {
  await fetchGoals()
  await nextTick()
  // ── Goal cards: dramatic entrance with 3D ──
  staggerReveal('.goal-card', {
    y: 50,
    scale: 0.85,
    rotation: -3,
    stagger: 0.1,
    duration: DUR.slow,
    ease: EASE.back,
  })
  tilt3D('.goal-card', { maxTilt: 10, scale: 1.02 })

  // ── Interaction: hover lift on goal cards ──
  hoverLift('.goal-card', { y: -6, scale: 1.02 })

  // ── Interaction: press + ripple on action buttons ──
  buttonPress('.goal-actions .el-button--primary')
  rippleClick('.goal-card', { color: 'rgba(94, 234, 212, 0.15)' })

  // Animate progress bars
  setTimeout(() => {
    document.querySelectorAll('.el-progress-bar__inner').forEach((el) => {
      const style = (el as HTMLElement).style
      const target = style.width
      if (target && target !== '0%') {
        style.width = '0%'
        progressAnimate(el as HTMLElement, parseFloat(target))
      }
    })
  }, 300)
})

onUnmounted(() => { cleanupScrollTriggers() })
</script>

<style scoped>
.goals-page { padding: 20px; }
.header-actions { display: flex; justify-content: space-between; align-items: center; }
.goal-card { margin-bottom: 20px; transition: all 0.3s; }
.goal-card:hover { transform: translateY(-2px); }
.goal-card.goal-completed { border-color: var(--el-color-success); border-width: 2px; }
.goal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.goal-title { font-size: 16px; font-weight: 500; color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; margin-right: 8px; }
.goal-desc { font-size: 13px; color: var(--el-text-color-secondary); margin-bottom: 12px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.goal-progress { margin-bottom: 12px; }
.progress-info { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.progress-label { font-size: 13px; color: var(--el-text-color-secondary); }
.progress-value { font-size: 14px; font-weight: 500; color: var(--el-text-color-primary); }
.goal-date { font-size: 12px; color: var(--el-text-color-placeholder); margin-bottom: 12px; }
.goal-actions { display: flex; justify-content: space-between; align-items: center; padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
</style>