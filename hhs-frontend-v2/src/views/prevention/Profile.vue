<template>
  <div class="profile-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>健康档案</span>
          <el-button v-if="!editing" type="primary" @click="editing = true">编辑</el-button>
          <div v-else>
            <el-button @click="cancelEdit">放弃编辑</el-button>
            <el-button type="primary" @click="handleSubmit">保存</el-button>
          </div>
        </div>
      </template>

      <!-- 完整度头部：数据未到达（null）时整块不渲染，也不画骨架屏 ——
           加载态由 el-form 的 v-loading 掩码承担，一种状态一种指示（Contract 8）。 -->
      <ProfileCompleteness v-if="completeness" :completeness="completeness" variant="full" />

      <!-- 不填承诺：全页只出现一次，紧贴完整度块之后、错误汇总之前（Contract 2）。 -->
      <p class="profile-promise">推荐与可选字段不填也可保存，不影响核心功能使用。</p>

      <el-form
        ref="formRef"
        v-loading="loading"
        :model="form"
        :rules="rules"
        label-width="120px"
        :label-position="isNarrow ? 'top' : 'right'"
        :hide-required-asterisk="true"
        :disabled="!editing"
        @focusout="onFocusOut"
      >
        <FormErrorSummary
          ref="summaryRef"
          :summary-items="summaryItems"
          @focus-field="focusField"
        />

        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item
              label="性别"
              prop="gender"
              data-field="gender"
              :class="{ 'is-focus-target': focusTarget === 'gender' }"
              :error="fieldErrors['gender']"
            >
              <el-radio-group v-model="form.gender" @change="clearField('gender')">
                <el-radio value="male">男</el-radio>
                <el-radio value="female">女</el-radio>
              </el-radio-group>
              <!-- 说明行独占控件下方一行：文案来自后端 fields[].reason，绝不在此硬编码。 -->
              <p v-if="fieldMeta('gender')" class="field-hint">
                <span :class="importanceClass('gender')">{{ importanceLabel('gender') }}</span>
                <span class="field-hint__text">{{ hintFor('gender') }}</span>
              </p>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item
              label="出生日期"
              prop="birthDate"
              data-field="birthDate"
              :class="{ 'is-focus-target': focusTarget === 'birthDate' }"
              :error="fieldErrors['birthDate']"
            >
              <el-date-picker
                v-model="form.birthDate"
                type="date"
                placeholder="选择日期"
                value-format="YYYY-MM-DD"
                :disabled-date="disabledDate"
                style="width: 100%"
                @change="clearField('birthDate')"
              />
              <p v-if="fieldMeta('birthDate')" class="field-hint">
                <span :class="importanceClass('birthDate')">{{
                  importanceLabel('birthDate')
                }}</span>
                <span class="field-hint__text">{{ hintFor('birthDate') }}</span>
              </p>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item
              label="身高(cm)"
              prop="heightCm"
              data-field="heightCm"
              :class="{ 'is-focus-target': focusTarget === 'heightCm' }"
              :error="fieldErrors['heightCm']"
            >
              <el-input-number
                v-model="form.heightCm"
                :min="50"
                :max="300"
                :disabled="!editing"
                style="width: 100%"
                @change="clearField('heightCm')"
              />
              <p v-if="fieldMeta('heightCm')" class="field-hint">
                <span :class="importanceClass('heightCm')">{{
                  importanceLabel('heightCm')
                }}</span>
                <span class="field-hint__text">{{ hintFor('heightCm') }}</span>
              </p>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item
              label="体重(kg)"
              prop="weightKg"
              data-field="weightKg"
              :class="{ 'is-focus-target': focusTarget === 'weightKg' }"
              :error="fieldErrors['weightKg']"
            >
              <el-input-number
                v-model="form.weightKg"
                :min="20"
                :max="500"
                :precision="1"
                :disabled="!editing"
                style="width: 100%"
                @change="clearField('weightKg')"
              />
              <p v-if="fieldMeta('weightKg')" class="field-hint">
                <span :class="importanceClass('weightKg')">{{
                  importanceLabel('weightKg')
                }}</span>
                <span class="field-hint__text">{{ hintFor('weightKg') }}</span>
              </p>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <!-- BMI 始终渲染，说明行才有稳定的落点；血型列同样始终可见/可选，
               否则 bloodType 的服务端错误无处显示（WR-11）。不可计算时只显示占位值，
               分类 tag 用 v-if 挡住，避免把「—」送进 getBmiType。 -->
          <el-col :xs="24" :md="12">
            <el-form-item
              label="BMI"
              data-field="bmi"
              :class="{ 'is-focus-target': focusTarget === 'bmi' }"
            >
              <el-input :value="bmi ?? '—'" readonly>
                <template #append>
                  <el-tag v-if="bmi" :type="getBmiType(bmi)">{{ getBmiLabel(bmi) }}</el-tag>
                </template>
              </el-input>
              <p v-if="fieldMeta('bmi')" class="field-hint">
                <span :class="importanceClass('bmi')">{{ importanceLabel('bmi') }}</span>
                <span class="field-hint__text">{{ hintFor('bmi') }}</span>
              </p>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item
              label="血型"
              prop="bloodType"
              data-field="bloodType"
              :class="{ 'is-focus-target': focusTarget === 'bloodType' }"
              :error="fieldErrors['bloodType']"
            >
              <el-select
                v-model="form.bloodType"
                placeholder="请选择"
                style="width: 100%"
                @change="clearField('bloodType')"
              >
                <el-option label="A型" value="A" />
                <el-option label="B型" value="B" />
                <el-option label="AB型" value="AB" />
                <el-option label="O型" value="O" />
              </el-select>
              <p v-if="fieldMeta('bloodType')" class="field-hint">
                <span :class="importanceClass('bloodType')">{{
                  importanceLabel('bloodType')
                }}</span>
                <span class="field-hint__text">{{ hintFor('bloodType') }}</span>
              </p>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item
          label="过敏史"
          prop="allergyHistory"
          data-field="allergyHistory"
          :class="{ 'is-focus-target': focusTarget === 'allergyHistory' }"
          :error="fieldErrors['allergyHistory']"
        >
          <el-input
            v-model="form.allergyHistory"
            type="textarea"
            :rows="3"
            placeholder="请输入过敏史，如：青霉素过敏、花粉过敏等"
            @input="clearField('allergyHistory')"
          />
          <p v-if="fieldMeta('allergyHistory')" class="field-hint">
            <span :class="importanceClass('allergyHistory')">{{
              importanceLabel('allergyHistory')
            }}</span>
            <span class="field-hint__text">{{ hintFor('allergyHistory') }}</span>
          </p>
        </el-form-item>

        <el-form-item
          label="家族病史"
          prop="familyHistory"
          data-field="familyHistory"
          :class="{ 'is-focus-target': focusTarget === 'familyHistory' }"
          :error="fieldErrors['familyHistory']"
        >
          <el-input
            v-model="form.familyHistory"
            type="textarea"
            :rows="3"
            placeholder="请输入家族病史，如：父亲有高血压、母亲有糖尿病等"
            @input="clearField('familyHistory')"
          />
          <p v-if="fieldMeta('familyHistory')" class="field-hint">
            <span :class="importanceClass('familyHistory')">{{
              importanceLabel('familyHistory')
            }}</span>
            <span class="field-hint__text">{{ hintFor('familyHistory') }}</span>
          </p>
        </el-form-item>

        <el-form-item
          label="生活习惯"
          prop="lifestyleHabits"
          data-field="lifestyleHabits"
          :class="{ 'is-focus-target': focusTarget === 'lifestyleHabits' }"
          :error="fieldErrors['lifestyleHabits']"
        >
          <el-input
            v-model="form.lifestyleHabits"
            type="textarea"
            :rows="3"
            placeholder="请输入生活习惯，如：吸烟、饮酒、运动频率等"
            @input="clearField('lifestyleHabits')"
          />
          <p v-if="fieldMeta('lifestyleHabits')" class="field-hint">
            <span :class="importanceClass('lifestyleHabits')">{{
              importanceLabel('lifestyleHabits')
            }}</span>
            <span class="field-hint__text">{{ hintFor('lifestyleHabits') }}</span>
          </p>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { gsap, cleanupScrollTriggers, DUR, EASE } from '@/composables/useGsap'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { preventionApi } from '@/api/prevention'
import { useFormServerErrors, collectInvalidFieldMessages } from '@/composables/useFormServerErrors'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import ProfileCompleteness from '@/components/common/ProfileCompleteness.vue'
import { useProfileCompletenessStore } from '@/stores/profileCompleteness'
import { showError } from '@/utils/showError'
import { normalizeApiError } from '@/utils/appError'
import type { HealthProfileRequest, HealthProfileVO } from '@/types/api'

// Phase 14: 其余 toast 调用点待迁移（ERR-03/05/07/08）

const loading = ref(false)
const editing = ref(false)
const formRef = ref<FormInstance>()
const summaryRef = ref<InstanceType<typeof FormErrorSummary> | null>(null)
const profileData = ref<HealthProfileVO | null>(null)

const completenessStore = useProfileCompletenessStore()
const completeness = computed(() => completenessStore.data)

const route = useRoute()
const router = useRouter()
/** 当前被 ?focus= 点亮的字段 key；失焦即清空，避免常驻高亮。 */
const focusTarget = ref<string | null>(null)

const { fieldErrors, summaryItems, apply, setErrors, clearField, reset, focusField } =
  useFormServerErrors()

/**
 * 窄屏判定：用 matchMedia 而不是 resize 轮询，与 <style scoped> 的 639px 断点一致。
 * 窄屏时标签改为上方堆叠（EP 原生 label-position），不改 el-form-item 的 display ——
 * 它是 flex 容器，改成 block 会连带破坏说明行的 flex-basis: 100%。
 */
const isNarrow = ref(false)
const narrowQuery =
  typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(max-width: 639px)')
    : null
if (narrowQuery) {
  isNarrow.value = narrowQuery.matches
  narrowQuery.addEventListener('change', onNarrowChange)
}

function onNarrowChange(event: MediaQueryListEvent): void {
  isNarrow.value = event.matches
}

/**
 * 字段元信息全部取自后端 payload（`fields[].reason` / `.importance`）。
 * 模板里不写任何一条说明文案：一旦前后端各存一份，风险引擎改了替代规则时页面会继续
 * 承诺旧行为（Contract 2 / T-13-26）。
 */
const IMPORTANCE_LABELS: Record<string, string> = {
  required: '必需',
  recommended: '推荐',
  optional: '可选'
}

const fieldMeta = (key: string) => completeness.value?.fields.find(field => field.key === key)
const hintFor = (key: string): string => fieldMeta(key)?.reason ?? ''
const importanceFor = (key: string): string => fieldMeta(key)?.importance ?? ''
const importanceClass = (key: string): string =>
  'hhs-imp-badge hhs-imp-badge--' + (importanceFor(key) || 'optional')
const importanceLabel = (key: string): string => {
  const importance = importanceFor(key)
  return importance ? (IMPORTANCE_LABELS[importance] ?? '') : ''
}

const form = reactive<HealthProfileRequest>({
  gender: '',
  birthDate: '',
  heightCm: undefined,
  weightKg: undefined,
  bloodType: '',
  allergyHistory: '',
  familyHistory: '',
  lifestyleHabits: ''
})

const rules: FormRules = {
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  birthDate: [{ required: true, message: '请选择出生日期', trigger: 'change' }]
}

const bmi = computed(() => {
  if (form.heightCm && form.weightKg) {
    const heightM = form.heightCm / 100
    return (form.weightKg / (heightM * heightM)).toFixed(1)
  }
  return null
})

const getBmiType = (bmi: string): string => {
  const value = parseFloat(bmi)
  if (value < 18.5) return 'warning'
  if (value < 24) return 'success'
  if (value < 28) return 'warning'
  return 'danger'
}

const getBmiLabel = (bmi: string): string => {
  const value = parseFloat(bmi)
  if (value < 18.5) return '偏瘦'
  if (value < 24) return '正常'
  if (value < 28) return '偏胖'
  return '肥胖'
}

const disabledDate = (time: Date) => {
  return time.getTime() >= Date.now()
}

const fetchProfile = async () => {
  loading.value = true
  try {
    // silent：档案不存在是正常状态，拦截器与 showError 都不打扰用户（D-09/D-10）。
    const res = await preventionApi.getProfile({ silent: true })
    profileData.value = res.data
    Object.assign(form, {
      gender: res.data.gender || '',
      birthDate: res.data.birthDate || '',
      heightCm: res.data.heightCm,
      weightKg: res.data.weightKg,
      bloodType: res.data.bloodType || '',
      allergyHistory: res.data.allergyHistory || '',
      familyHistory: res.data.familyHistory || '',
      lifestyleHabits: res.data.lifestyleHabits || ''
    })
  } catch (error) {
    // 只有「档案不存在」才进入创建模式；网络故障或服务端错误进入创建模式会让人以为
    // 要新建，提交时才报「已存在」，或在部分故障下创建出重复档案（WR-12）。
    //
    // 判定只读 `kind`，不要再拼 status/code：本接口的 404 走 HTTP 200 信封，
    // `err.status` 因此恒为 200；而 PreventionController 用的是
    // `Result.failure(404, msg)`（不带 errorCode），`err.code` 是空串。
    // 旧写法两个条件都不成立，创建模式永远进不去 —— 之前靠用户手动点「编辑」掩盖了。
    // `normalizeApiError` 已经把这三种证据统一收敛成 `kind === 'notFound'`。
    const err = normalizeApiError(error)
    if (err.kind === 'notFound') {
      editing.value = true
    } else {
      // 非「不存在」失败不属于「正常状态」：本页没有状态块，必须让用户知道加载失败。
      showError(err, 'profile.loadFailed', { presentStates: true })
    }
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  // 上一轮的残留服务端错误不进入本次尝试（不碰用户输入）。
  reset()

  await formRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      // 客户端 el-form 校验失败：Element Plus 只会内联提示，这里补上顶部常驻汇总，
      // 让客户端与服务端校验共用同一套 GOV.UK 双呈现（D-01），且不弹 toast。
      setErrors(collectInvalidFieldMessages(invalidFields))
      await nextTick()
      summaryRef.value?.focusSummary()
      return
    }

    loading.value = true
    try {
      if (profileData.value) {
        await preventionApi.updateProfile(profileData.value.id, form)
        ElMessage.success('更新成功')
      } else {
        await preventionApi.createProfile(form)
        ElMessage.success('创建成功')
      }
      // 保存成功后的完整度重算必须留在成功路径内：写库成功 → 重读档案 → 重算完整度，
      // 三步都完成后才回到只读态，用户看到的新分数才是服务端算出来的（T-13-29）。
      // 失败路径完全不发第二个 getCompleteness —— 一次失败的保存不得显示「未挣得的提升」。
      await fetchProfile()
      await completenessStore.fetchCompleteness()
      editing.value = false
    } catch (error) {
      // validation → 0 toast，内联 + 汇总承担呈现（GOV.UK 双呈现）。
      // presentStates：本页没有状态块，保存是用户主动动作 —— 若档案在别处被删除
      // （update() 匹配不到行 → 404）或无权修改（403），默认的零 toast 会让点击
      // 「保存」换不来任何反馈。prevention/Risk.vue 的同类调用已如此处理。
      const err = showError(error, 'profile.saveFailed', { presentStates: true })
      if (err.kind === 'validation') {
        apply(err)
        await nextTick()
        summaryRef.value?.focusSummary()
      }
    } finally {
      loading.value = false
    }
  })
}

const cancelEdit = () => {
  editing.value = false
  if (profileData.value) {
    Object.assign(form, {
      gender: profileData.value.gender || '',
      birthDate: profileData.value.birthDate || '',
      heightCm: profileData.value.heightCm,
      weightKg: profileData.value.weightKg,
      bloodType: profileData.value.bloodType || '',
      allergyHistory: profileData.value.allergyHistory || '',
      familyHistory: profileData.value.familyHistory || '',
      lifestyleHabits: profileData.value.lifestyleHabits || ''
    })
  }
}

/**
 * 消费 `?focus=`（Contract 7）。
 *
 * 只认「存在于 fields[] 且尚未填写」的 key —— 这是 T-13-25 的缓解：攻击者能控制的
 * 只是这个字符串，任何不在目录里的值都退化为空操作。真正的聚焦复用
 * useFormServerErrors.focusField（内部已做 CSS.escape 与 scrollIntoView），
 * 因此与错误汇总的定位行为逐字一致。
 *
 * 成功消费后立刻清空 query：刷新页面不会重复滚动。
 *
 * 只读态下 `el-form :disabled="!editing"` 会把控件置为 disabled，`focus()` 是空操作。
 * 而「完善档案」链接的语义就是「来补填这一项」，因此命中目标时先进入编辑态再聚焦 ——
 * 否则最常见的情况（已有档案行、只读打开）会只画出一圈 outline 却无法输入。
 */
async function applyFocusTarget(): Promise<void> {
  const raw = route.query.focus
  if (typeof raw !== 'string' || raw.length === 0) return
  if (!completeness.value) return
  const entry = completeness.value.fields.find(field => field.key === raw)
  if (!entry) return
  if (entry.filled) return

  focusTarget.value = raw
  if (!editing.value) {
    editing.value = true
    await nextTick()
  }
  focusField(raw)
  void router.replace({ query: {} })
}

/**
 * 焦点离开「被点亮的那个字段容器」时撤销高亮。
 * relatedTarget 为 null（点了页面空白、或浏览器不提供）时保守地保留高亮。
 */
function onFocusOut(event: FocusEvent): void {
  if (!focusTarget.value) return
  const related = event.relatedTarget
  if (!(related instanceof HTMLElement)) return
  const container = document.querySelector(`[data-field="${CSS.escape(focusTarget.value)}"]`)
  if (container && container.contains(related)) return
  focusTarget.value = null
}

onMounted(async () => {
  // 先等完整度落定，再渲染/取档案：单飞且可等待（13-05），并发消费者不会重复请求。
  await completenessStore.fetchCompleteness()
  fetchProfile()

  nextTick(() => {
    const tl = gsap.timeline({ defaults: { ease: EASE.out } })
    tl.fromTo('.el-card',
      { y: 20, autoAlpha: 0 },
      { y: 0, autoAlpha: 1, duration: DUR.mid, clearProps: 'transform,autoAlpha,visibility,opacity' }
    )
    tl.fromTo('.header-actions',
      { y: -10, autoAlpha: 0 },
      { y: 0, autoAlpha: 1, duration: DUR.mid, clearProps: 'transform,autoAlpha,visibility,opacity' }
    , '-=0.2')
    // Stagger form rows
    tl.fromTo('.el-form .el-row',
      { y: 12, autoAlpha: 0 },
      { y: 0, autoAlpha: 1, duration: DUR.mid, stagger: 0.06, clearProps: 'transform,autoAlpha,visibility,opacity' }
    , '-=0.15')
  })

  // 最后消费 ?focus=：payload 已落定、DOM 已刷出，focusField 才能命中真实元素。
  await nextTick()
  await applyFocusTarget()
})
onUnmounted(() => {
  if (narrowQuery) narrowQuery.removeEventListener('change', onNarrowChange)
  cleanupScrollTriggers()
})
</script>

<style scoped>
.profile-page {
  padding: 20px;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* theme.css 全局把标签锁成 500 字重（第 3 种字重）。本契约只有 400/600。 */
.profile-page :deep(.el-form-item__label) {
  font: 600 14px/1.5 var(--font-ui) !important;
}

/* EP 默认 18px 是 off-grid 值。 */
.profile-page :deep(.el-form-item) {
  margin-bottom: 16px;
}

/* theme.css 把输入框正文锁成 15px，归一到 14px Body。 */
.profile-page :deep(.el-input__inner),
.profile-page :deep(.el-textarea__inner) {
  font: 400 14px/1.5 var(--font-ui) !important;
}

/* EP 的 .el-form-item__content 是 flex + wrap + align-items:center；
   说明行必须以 100% 基准独占下一行，否则会和输入框挤在一行。 */
.field-hint {
  flex-basis: 100%;
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin-top: 4px;
  font: 400 12px/1.4 var(--font-ui);
  /* 说明文字是本阶段的核心教育内容，必须达 AA。
     --text-3 浅色仅 3.97:1 不达 4.5:1，因此用 --text-2（Light 8.38:1 / Dark 11.30:1）。 */
  color: var(--text-2);
}
.field-hint__text {
  flex: 1;
  min-width: 0;
}

/* 自动聚焦目标（Contract 7）：程序化 focus 不触发 :focus-visible，
   因此用显式类给出可见环，失焦即移除。 */
.profile-page :deep(.el-form-item.is-focus-target) {
  outline: 2px solid var(--accent-cool);
  outline-offset: 2px;
  border-radius: var(--radius-sm);
}

.profile-promise {
  margin-bottom: 16px;
  font: 400 14px/1.5 var(--font-ui);
  color: var(--text-1);
}

/* 窄屏：标签从右对齐改为上方堆叠，否则 120px 标签列挤掉输入宽度。
   用 EP 原生的 label-position，不改 el-form-item 的 display。 */
@media (max-width: 639px) {
  .profile-page :deep(.el-form-item__label) {
    justify-content: flex-start;
    padding: 0 0 4px 0;
  }
}

/* 主按钮：theme.css 锁成 500 14px/1 → 600 14px/1.5（行高 1 会裁切中文字形下缘）。 */
.profile-page :deep(.el-button--primary) {
  font: 600 14px/1.5 var(--font-ui) !important;
}
/* 次按钮：EP 默认 --el-button-font-weight: 500 → 400。 */
.profile-page :deep(.el-button:not(.el-button--primary)) {
  font-weight: 400 !important;
  line-height: 1.5 !important;
}
</style>
