<template>
  <div class="mood-page">
    <el-row :gutter="20">
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header><span>记录心情</span></template>
          <div class="mood-input-section">
            <!--
              这个 el-form 原先没有 :model 也没有任何 prop —— 它是「装饰性」的，
              validate() 无法运行，:error 也无处可落。本次改造补上绑定模型与 prop，
              让它成为真正可接服务端字段错误的表单（Contract 4.1 的五件套）。
            -->
            <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
              <!-- 汇总块必须是 <el-form> 的第一个子节点（Contract 4.3）。 -->
              <FormErrorSummary
                ref="summaryRef"
                :summary-items="moodErrors.summaryItems"
                @focus-field="moodErrors.focusField"
              />
              <!--
                心情评分本身是一个裸按钮组，既没有 input 也没有 select wrapper。
                focusField('moodScore') 因此找不到可聚焦控件并静默返回 —— 这是刻意的：
                契约把「首个无效字段的可见性」交给 is-error 描边，汇总条目本身仍是
                可点击的锚点。不为它写自定义选择器（台账 理由 列已记录）。
              -->
              <el-form-item
                label="心情评分"
                prop="moodScore"
                data-field="moodScore"
                :error="moodErrors.fieldErrors['moodScore']"
              >
                <div class="mood-label">今天心情如何？</div>
                <div class="mood-score-selector">
                  <button v-for="n in 10" :key="n" class="mood-btn" :class="{ active: form.moodScore === n }" @click="form.moodScore = n; moodErrors.clearField('moodScore')">
                    {{ n }}
                  </button>
                </div>
                <div class="mood-hint">当前选择: {{ form.moodScore }} / 10 — {{ getMoodText(form.moodScore) }}</div>
              </el-form-item>
              <el-divider />
              <el-form-item
                label="精力值"
                prop="energyLevel"
                data-field="energyLevel"
                :error="moodErrors.fieldErrors['energyLevel']"
              >
                <el-slider
                  v-model="form.energyLevel"
                  :min="1"
                  :max="10"
                  :marks="sliderMarks"
                  show-stops
                  @change="moodErrors.clearField('energyLevel')"
                />
              </el-form-item>
              <el-form-item
                label="压力值"
                prop="stressLevel"
                data-field="stressLevel"
                :error="moodErrors.fieldErrors['stressLevel']"
              >
                <el-slider
                  v-model="form.stressLevel"
                  :min="1"
                  :max="10"
                  :marks="sliderMarks"
                  show-stops
                  @change="moodErrors.clearField('stressLevel')"
                />
              </el-form-item>
              <el-form-item
                label="睡眠质量"
                prop="sleepQuality"
                data-field="sleepQuality"
                :error="moodErrors.fieldErrors['sleepQuality']"
              >
                <el-slider
                  v-model="form.sleepQuality"
                  :min="1"
                  :max="10"
                  :marks="sliderMarks"
                  show-stops
                  @change="moodErrors.clearField('sleepQuality')"
                />
              </el-form-item>
              <el-form-item
                label="备注"
                prop="notes"
                data-field="notes"
                :error="moodErrors.fieldErrors['notes']"
              >
                <el-input
                  v-model="form.notes"
                  type="textarea"
                  :rows="3"
                  placeholder="记录今天的感受..."
                  @update:model-value="moodErrors.clearField('notes')"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="submitting" @click="handleSubmit">提交记录</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="insights-card">
          <template #header><span>心情洞察</span></template>
          <el-skeleton :loading="insightsLoading" animated>
            <template #template>
              <el-row :gutter="16">
                <el-col v-for="n in 4" :key="n" :span="12">
                  <div style="text-align:center;padding:16px;">
                    <el-skeleton-item variant="text" style="width:60%;height:32px;margin:0 auto;" />
                    <el-skeleton-item variant="text" style="width:40%;height:14px;margin:8px auto 0;" />
                  </div>
                </el-col>
              </el-row>
            </template>
            <template #default>
              <div v-if="insights && insights.entryCount > 0" class="insights-grid">
                <div class="insight-item">
                  <div class="insight-value">{{ insights.avgMood?.toFixed(1) ?? '-' }}</div>
                  <div class="insight-label">平均心情</div>
                </div>
                <div class="insight-item">
                  <div class="insight-value">{{ insights.avgEnergy?.toFixed(1) ?? '-' }}</div>
                  <div class="insight-label">平均精力</div>
                </div>
                <div class="insight-item">
                  <div class="insight-value">{{ insights.avgStress?.toFixed(1) ?? '-' }}</div>
                  <div class="insight-label">平均压力</div>
                </div>
                <div class="insight-item">
                  <div class="insight-value">{{ insights.avgSleep?.toFixed(1) ?? '-' }}</div>
                  <div class="insight-label">平均睡眠</div>
                </div>
              </div>
              <el-empty v-else description="暂无数据" />
            </template>
          </el-skeleton>
        </el-card>
        <el-card class="history-card">
          <template #header><span>近期记录</span></template>
          <el-empty v-if="entries.length === 0" description="暂无记录" />
          <div v-else class="entry-list">
            <div v-for="entry in entries" :key="entry.id" class="entry-item">
              <div class="entry-score" :style="{ background: getMoodColor(entry.moodScore) }">{{ entry.moodScore }}</div>
              <div class="entry-info">
                <div class="entry-date">{{ formatDate(entry.recordedAt) }}</div>
                <div v-if="entry.notes" class="entry-notes">{{ entry.notes }}</div>
              </div>
              <el-button link type="danger" size="small" @click="handleDelete(entry.id)">删除</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { moodApi, type MoodEntry, type MoodInsights } from '@/api/mood'
import { formatDate } from '@/utils/format'
import { useFormServerErrors, collectInvalidFieldMessages } from '@/composables/useFormServerErrors'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import { showError } from '@/utils/showError'
import {
  springPop, staggerReveal, countUp, cleanupScrollTriggers,
  tilt3D, magneticHover, buttonPress, hoverLift, rippleClick, DUR, EASE,
} from '@/composables/useGsap'

const submitting = ref(false)
const insightsLoading = ref(false)
const entries = ref<MoodEntry[]>([])
const insights = ref<MoodInsights | null>(null)
const formRef = ref<FormInstance>()
const summaryRef = ref<InstanceType<typeof FormErrorSummary> | null>(null)

const form = reactive({ moodScore: 5, energyLevel: 5, stressLevel: 5, sleepQuality: 5, notes: '' })
const sliderMarks: Record<number, string> = { 1: '1', 5: '5', 10: '10' }

/**
 * 一个投影实例，字段顺序与页面的视觉顺序一致（GOV.UK：汇总顺序必须匹配页面）。
 *
 * 五个 prop 名与 `FIELD_LABELS` 的键逐一对应（`moodScore` / `energyLevel` /
 * `stressLevel` / `sleepQuality` / `notes` 均由 14-03 保证），因此不需要 `labels`
 * 覆盖 —— 汇总与内联直接读出中文标签，绝不回退成驼峰键（Contract 4.5 规则 2）。
 * `reactive()` 让模板里 `moodErrors.fieldErrors['moodScore']` 读到字符串而非 Ref。
 */
const moodErrors = reactive(useFormServerErrors({
  fieldOrder: ['moodScore', 'energyLevel', 'stressLevel', 'sleepQuality', 'notes']
}))

/**
 * 客户端规则镜像后端 `RecordMoodRequest` 的约束（`moodScore` @NotNull、四个评分
 * 字段 @Min(1)/@Max(10)）。`notes` 后端只限 @Size(500)，这里不加必填。
 */
const rules: FormRules = {
  moodScore: [{ required: true, message: '请选择心情评分', trigger: 'change' }],
  energyLevel: [{ required: true, message: '请设置精力值', trigger: 'change' }],
  stressLevel: [{ required: true, message: '请设置压力值', trigger: 'change' }],
  sleepQuality: [{ required: true, message: '请设置睡眠质量', trigger: 'change' }]
}

const moodTexts: string[] = ['', '很差', '很差', '较差', '较差', '一般', '一般', '较好', '较好', '很好', '很好']
const getMoodText = (score: number): string => moodTexts[score] || ''

const getMoodColor = (score: number): string => {
  if (score >= 8) return '#67c23a'
  if (score >= 6) return '#409eff'
  if (score >= 4) return '#e6a23c'
  return '#f56c6c'
}

/**
 * 列表 + 洞察是 `Promise.all` 的一对：任一失败只呈现一次。
 * 本页不渲染任何错误状态块，`forbidden` / `notFound` 没有落点，
 * 因此显式 opt-in 一条常驻 toast —— 静默是唯一不可接受的结果（Contract 1.3）。
 * 刻意**不**把这一对拆成两条独立错误路径：那会让一次失败弹出两条提示。
 */
const fetchData = async () => {
  insightsLoading.value = true
  try {
    const [entriesRes, insightsRes] = await Promise.all([moodApi.list(), moodApi.getInsights(30)])
    entries.value = entriesRes.data || []
    insights.value = insightsRes.data || null
  } catch (error) {
    showError(error, 'mood.loadFailed', { presentStates: true })
  } finally {
    insightsLoading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  // 上一轮的残留投影不进入本次尝试；只清投影，绝不碰用户输入（ERR-05）。
  moodErrors.reset()

  await formRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      // 客户端校验失败也走同一份汇总通道（GOV.UK 双呈现），零 toast。
      moodErrors.setErrors(collectInvalidFieldMessages(invalidFields))
      await nextTick()
      summaryRef.value?.focusSummary()
      return
    }

    submitting.value = true
    try {
      const today = new Date().toISOString().split('T')[0]
      await moodApi.create({
        moodScore: form.moodScore,
        energyLevel: form.energyLevel,
        stressLevel: form.stressLevel,
        sleepQuality: form.sleepQuality,
        notes: form.notes || undefined,
        entryDate: today
      })
      // 成功路径保持原样：只清 `notes`，四个评分字段刻意不清 —— 用户可能想连着
      // 记两条，改掉这个行为属于另一个改动。
      ElMessage.success('记录成功')
      form.notes = ''
      fetchData()
    } catch (error) {
      // validation → 0 toast，汇总 + 内联承担呈现；其余 kind 由 showError 呈现。
      // `WellnessServiceImpl:118` 在同类写路径上抛 AUTH_FORBIDDEN：本页无状态块，
      // 缺 `presentStates` 会把一次真实的权限拒绝变成静默。
      const err = showError(error, 'mood.saveFailed', { presentStates: true })
      if (err.kind === 'validation') {
        moodErrors.apply(err)
        await nextTick()
        summaryRef.value?.focusSummary()
      }
    } finally {
      submitting.value = false
    }
  })
}

/**
 * 删除：确认与请求彻底分离。
 *
 * 取消路径是一个裸 `return` —— 用户按了「取消」不是失败，零提示（D-04 / ERR-08）。
 * 旧实现把确认框与请求塞进同一个 try、再用「拒绝值是否等于字符串 cancel」来判断
 * 是否提示；那个手写比较对 `AbortError`（`e.name === 'AbortError'`）与归一化后的
 * `ApiError('', 'cancel')` 都会判成真失败并弹红灯，因此整段判断删除。
 */
const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
  } catch {
    // 取消确认框：刻意静默，不弹任何提示。
    return
  }

  try {
    await moodApi.remove(id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    showError(error, 'mood.deleteFailed', { presentStates: true })
  }
}

onMounted(() => {
  fetchData()

  // ── Mood buttons: dramatic spring pop ──
  springPop('.mood-btn', { stagger: 0.06, scale: 0 })
  magneticHover('.mood-btn', { strength: 0.4, radius: 60 })

  // ── Insight cards: stagger with 3D ──
  staggerReveal('.insight-item', {
    y: 30,
    scale: 0.9,
    stagger: 0.1,
    duration: DUR.slow,
    ease: EASE.back,
  })
  tilt3D('.insight-item', { maxTilt: 8, scale: 1.02 })

  // ── Entry items: slide from right ──
  staggerReveal('.entry-item', {
    x: 40,
    y: 0,
    stagger: 0.06,
    duration: DUR.mid,
    ease: EASE.back,
  })

  // ── Count up insight values ──
  setTimeout(() => {
    document.querySelectorAll('.insight-value').forEach((el) => {
      const num = parseFloat(el.textContent?.trim() ?? '')
      if (!isNaN(num) && num > 0) countUp(el as HTMLElement, num, { decimals: 1, duration: DUR.dramatic })
    })
  }, 700)

  // ── Interaction: mood button press feedback ──
  buttonPress('.mood-btn', { scale: 0.88 })

  // ── Interaction: hover lift on insight cards ──
  hoverLift('.insight-item', { y: -4, scale: 1.01 })

  // ── Interaction: ripple on mood buttons ──
  rippleClick('.mood-btn', { color: 'rgba(94, 234, 212, 0.3)' })
})

onUnmounted(() => {
  cleanupScrollTriggers()
})
</script>

<style scoped>
.mood-page { padding: 20px; }
.mood-input-section { padding: 0 8px; }
.mood-label { font-size: 18px; font-weight: 500; color: var(--el-text-color-primary); margin-bottom: 16px; }
.mood-score-selector { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.mood-btn { width: 44px; height: 44px; border-radius: 50%; border: 2px solid var(--el-border-color); background: var(--el-fill-color-blank); font-size: 16px; font-weight: 500; cursor: pointer; transition: all 0.2s; color: var(--el-text-color-primary); }
.mood-btn:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); }
.mood-btn.active { background: var(--el-color-primary); border-color: var(--el-color-primary); color: #fff; }
.mood-hint { font-size: 14px; color: var(--el-text-color-secondary); margin-bottom: 16px; }
.insights-card { margin-bottom: 20px; }
.insights-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.insight-item { text-align: center; padding: 16px; background: var(--el-fill-color-light); border-radius: 8px; }
.insight-value { font-size: 32px; font-weight: 700; color: var(--el-color-primary); }
.insight-label { font-size: 13px; color: var(--el-text-color-secondary); margin-top: 4px; }
.history-card { margin-bottom: 20px; }
.entry-list { display: flex; flex-direction: column; gap: 12px; }
.entry-item { display: flex; align-items: center; gap: 12px; padding: 12px; background: var(--el-fill-color-light); border-radius: 8px; }
.entry-score { width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 700; font-size: 16px; flex-shrink: 0; }
.entry-info { flex: 1; min-width: 0; }
.entry-date { font-size: 13px; color: var(--el-text-color-secondary); }
.entry-notes { font-size: 13px; color: var(--el-text-color-regular); margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>