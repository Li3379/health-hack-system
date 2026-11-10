<template>
  <div class="risk-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>风险评估</span>
          <el-button type="primary" @click="handleCreateClick">
            <el-icon><Plus /></el-icon>
            创建评估
          </el-button>
        </div>
      </template>

      <!--
        就地补填面板（Contract 5）。al-alert 只承载标题：EP 的默认插槽渲染在 <p> 内，
        列表与表单必须是它的兄弟节点，否则会被浏览器提升出去、破坏整块底色。
      -->
      <section v-if="panelOpen" class="risk-estimate" aria-labelledby="risk-estimate-title">
        <el-alert type="warning" :closable="false" show-icon>
          <template #title>
            <span id="risk-estimate-title">系统将按以下假设估算本次风险评估</span>
          </template>
        </el-alert>

        <ul class="risk-estimate__list">
          <li v-for="item in pendingAssumptions" :key="item">{{ item }}</li>
        </ul>

        <div class="risk-estimate__form">
          <el-form
            ref="backfillRef"
            :model="backfill"
            :rules="backfillRules"
            label-width="120px"
            :hide-required-asterisk="true"
          >
            <FormErrorSummary
              ref="summaryRef"
              :summary-items="summaryItems"
              @focus-field="focusField"
            />
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12">
                <el-form-item
                  label="性别"
                  prop="gender"
                  data-field="gender"
                  :error="fieldErrors['gender']"
                >
                  <el-radio-group v-model="backfill.gender" @change="clearField('gender')">
                    <el-radio value="male">男</el-radio>
                    <el-radio value="female">女</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item
                  label="出生日期"
                  prop="birthDate"
                  data-field="birthDate"
                  :error="fieldErrors['birthDate']"
                >
                  <el-date-picker
                    v-model="backfill.birthDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="选择日期"
                    :disabled-date="disabledDate"
                    style="width: 100%"
                    @change="clearField('birthDate')"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <!-- 第 2 行：可选补（D-06a）。没有 :rules 条目，留空是合法提交。 -->
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12">
                <el-form-item
                  label="身高（可选）"
                  prop="heightCm"
                  data-field="heightCm"
                  :error="fieldErrors['heightCm']"
                >
                  <el-input-number
                    v-model="backfill.heightCm"
                    :min="50"
                    :max="300"
                    placeholder="可不填"
                    style="width: 100%"
                    @change="clearField('heightCm')"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item
                  label="体重（可选）"
                  prop="weightKg"
                  data-field="weightKg"
                  :error="fieldErrors['weightKg']"
                >
                  <el-input-number
                    v-model="backfill.weightKg"
                    :min="20"
                    :max="500"
                    :precision="1"
                    placeholder="可不填"
                    style="width: 100%"
                    @change="clearField('weightKg')"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>

        <div class="risk-estimate__actions">
          <el-button type="primary" :loading="saving" @click="backfillAndCreate">
            保存档案并创建
          </el-button>
          <el-button @click="createAnyway">直接创建（按估算）</el-button>
          <el-button text @click="cancel">放弃创建</el-button>
        </div>
      </section>

      <!-- 徽标落在表格之外（Contract 4 隔离规则 1）：永不进入 .el-table。 -->
      <div class="risk-disclosure">
        <EstimateBadge
          v-if="!panelOpen"
          :completeness="completeness?.score ?? null"
          @complete="goProfileWithFocus"
        />
      </div>

      <el-table v-loading="loading" :data="assessments" stripe>
        <el-table-column prop="diseaseName" label="疾病名称" width="150" />
        <el-table-column prop="riskLevel" label="风险等级" width="120">
          <template #default="{ row }">
            <el-tag :type="getRiskLevelType(row.riskLevel)">
              {{ getRiskLevelLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskScore" label="风险评分" width="120">
          <template #default="{ row }">
            {{ row.riskScore || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="suggestion" label="建议措施" min-width="300" show-overflow-tooltip />
        <el-table-column prop="createTime" label="评估时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchAssessments"
          @current-change="fetchAssessments"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="评估详情" width="600px">
      <el-descriptions v-if="currentAssessment" :column="1" border>
        <el-descriptions-item label="疾病名称">
          {{ currentAssessment.diseaseName }}
        </el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="getRiskLevelType(currentAssessment.riskLevel)">
            {{ getRiskLevelLabel(currentAssessment.riskLevel) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="风险评分">
          {{ currentAssessment.riskScore || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="建议措施">
          {{ currentAssessment.suggestion || '暂无建议' }}
        </el-descriptions-item>
        <el-descriptions-item label="评估时间">
          {{ formatDateTime(currentAssessment.createTime) }}
        </el-descriptions-item>
      </el-descriptions>

      <!--
        「本次评估采用的假设」：读时按当前档案重算，因此标题刻意不声称是创建时冻结的记录
        （RESEARCH open question 2 的 RESOLVED 决定）。列表原样渲染后端句子。
      -->
      <div v-if="currentAssessment?.assumptions?.length" class="risk-dialog-assumptions">
        <p class="risk-dialog-assumptions__title">本次评估采用的假设</p>
        <ul class="risk-dialog-assumptions__list">
          <li v-for="item in currentAssessment.assumptions" :key="item">{{ item }}</li>
        </ul>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { gsap, cleanupScrollTriggers, DUR, EASE } from '@/composables/useGsap'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { preventionApi } from '@/api/prevention'
import { formatDateTime } from '@/utils/format'
import { useFormServerErrors, collectInvalidFieldMessages } from '@/composables/useFormServerErrors'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import EstimateBadge from '@/components/common/EstimateBadge.vue'
import { useProfileCompletenessStore } from '@/stores/profileCompleteness'
import { showError } from '@/utils/showError'
import type { HealthProfileRequest, HealthProfileVO, RiskAssessmentVO } from '@/types/api'

const loading = ref(false)
const detailVisible = ref(false)
const assessments = ref<RiskAssessmentVO[]>([])
const currentAssessment = ref<RiskAssessmentVO | null>(null)

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const router = useRouter()

const completenessStore = useProfileCompletenessStore()
const completeness = computed(() => completenessStore.data)

/**
 * 面板要披露的假设 = 后端聚合数组原样。
 *
 * 绝不在这里 filter(fields).map(field => field.assumption) 重建：逐字段拼接对
 * 「完全没有档案」会产出 3 条引擎不会执行的代入，而风险引擎在该分支只代入 1 条通用句。
 * `completeness.assumptions` 与 `RiskAssessmentVO.assumptions` 由后端同一个
 * ProfileFieldCatalog.assumptionsFor 填充，因此面板承诺与结果披露逐字相同。
 */
const pendingAssumptions = computed(() => completeness.value?.assumptions ?? [])

// 面板状态
const panelOpen = ref(false)
const saving = ref(false)
const backfillRef = ref<FormInstance>()
const summaryRef = ref<InstanceType<typeof FormErrorSummary> | null>(null)
const backfill = reactive<{
  gender: string
  birthDate: string
  heightCm: number | null
  weightKg: number | null
}>({ gender: '', birthDate: '', heightCm: null, weightKg: null })

// 档案本身（只用于两个用途：可选身高体重的预填、updateProfile 的 existing 载荷）。
const profileData = ref<HealthProfileVO | null>(null)

const { fieldErrors, summaryItems, apply, setErrors, clearField, reset, focusField } =
  useFormServerErrors()

const backfillRules: FormRules = {
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  birthDate: [{ required: true, message: '请选择出生日期', trigger: 'change' }]
  // heightCm / weightKg 刻意没有规则：D-06 称它们为「可选补」，留空必须能提交。
}

/** 与 prevention/Profile.vue 的禁用规则一致：不允许未来日期。 */
const disabledDate = (time: Date) => time.getTime() >= Date.now()

const getRiskLevelType = (level: string): string => {
  const types: Record<string, string> = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger'
  }
  return types[level] || 'info'
}

const getRiskLevelLabel = (level: string): string => {
  const labels: Record<string, string> = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险'
  }
  return labels[level] || level
}

const fetchAssessments = async () => {
  loading.value = true
  try {
    const res = await preventionApi.getRiskAssessments({
      page: pagination.page,
      size: pagination.size
    })
    // 后端返回数组，不是分页对象
    const data = Array.isArray(res.data) ? res.data : []
    assessments.value = data
    pagination.total = data.length
  } catch (error) {
    ElMessage.error('获取评估列表失败')
  } finally {
    loading.value = false
  }
}

/**
 * 加载档案行。`{ silent: true }`：404（档案不存在）是正常的引导状态，不得弹红色提示（D-09）。
 * 唯一失败处理是把 profileData 置空 —— 可选字段随即退化为空，不影响任何创建路径。
 */
const loadProfile = async () => {
  try {
    const res = await preventionApi.getProfile({ silent: true })
    profileData.value = res.data
  } catch {
    profileData.value = null
  }
}

const createRiskAssessment = async () => {
  try {
    await preventionApi.createRiskAssessment()
    ElMessage.success('评估创建成功')
    fetchAssessments()
  } catch (error) {
    ElMessage.error('创建评估失败')
  }
}

/**
 * 三序判定（Contract 5，先假设后分数，命中即停）。
 *
 * 步骤 1 是正确性前提：首次访问时 store 里还没有 payload，若直接判定，空假设列表会
 * 让面板永不展开、徽标永不出现。store 的 fetchCompleteness 单飞且可等待（13-05），
 * 因此这里 await 会真的等到挂载中的那个请求落定，而不是读到尚未落定的 null。
 */
const handleCreateClick = async () => {
  if (completeness.value === null && !completenessStore.loadFailed) {
    await completenessStore.fetchCompleteness()
  }

  // 1. 没有要披露的假设（含加载失败）→ 直接创建，面板不展开。
  if (pendingAssumptions.value.length === 0) {
    await createRiskAssessment()
    return
  }

  // 2. 有假设且完整度未满 → 展开面板，绝不抢先调用创建接口。
  if ((completeness.value?.score ?? 0) < 100) {
    panelOpen.value = true
    if (profileData.value) {
      backfill.heightCm = profileData.value.heightCm ?? null
      backfill.weightKg = profileData.value.weightKg ?? null
    }
    await nextTick()
    document.querySelector<HTMLElement>('.risk-estimate')?.scrollIntoView({ block: 'center' })
    focusField('gender')
    return
  }

  // 3. 防御分支：假设非空却 100%（数据自相矛盾）→ 直接创建，不打扰用户。
  await createRiskAssessment()
}

const createAnyway = async () => {
  panelOpen.value = false
  await createRiskAssessment()
}

/** 用户主动放弃不是失败（Phase 11 D-12）：零 toast、零 API 调用。 */
const cancel = () => {
  panelOpen.value = false
  reset()
  Object.assign(backfill, { gender: '', birthDate: '', heightCm: null, weightKg: null })
}

/**
 * 保存档案并创建：校验 → 写入 → 重取完整度（刷新假设列表）→ 关闭面板 → 创建评估。
 *
 * 失败时绝不清空 backfill（D-12）：用户已输入的值必须留给他们重试。
 * bmi 不由前端计算 —— 后端 toEntity 从身高体重派生（D-01 禁止前端算派生值）。
 */
const backfillAndCreate = async () => {
  if (!backfillRef.value) return

  reset()

  await backfillRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      setErrors(collectInvalidFieldMessages(invalidFields))
      await nextTick()
      summaryRef.value?.focusSummary()
      return
    }

    saving.value = true
    try {
      // 在提交那一刻取快照：用户可能在面板打开后、点击之前补填了档案。
      const existing = profileData.value
      const payload: HealthProfileRequest = {
        gender: backfill.gender,
        birthDate: backfill.birthDate,
        // 可选补（D-06a）：用户填的优先，未动的回落到已存值，两者都为空则不下发。
        // 用 undefined 而不是 null 收口：HealthProfileRequest 的可选属性是 number | undefined，
        // 而两种写法在服务端等价 —— toEntity 直接 request.getHeightCm()，缺键即 null。
        heightCm: backfill.heightCm ?? existing?.heightCm ?? undefined,
        weightKg: backfill.weightKg ?? existing?.weightKg ?? undefined,
        bloodType: existing?.bloodType,
        allergyHistory: existing?.allergyHistory,
        familyHistory: existing?.familyHistory,
        lifestyleHabits: existing?.lifestyleHabits
      }

      if (existing) {
        await preventionApi.updateProfile(existing.id, payload)
      } else {
        await preventionApi.createProfile(payload)
      }

      // 先重取假设再关闭面板：用户看到的列表必须是保存后服务端算出来的那一份。
      await completenessStore.fetchCompleteness()
      panelOpen.value = false
      await createRiskAssessment()
    } catch (error) {
      // validation → 0 toast，内联 + 汇总承担呈现；其余由 showError 单条呈现。
      const err = showError(error, 'profile.saveFailed')
      if (err.kind === 'validation') {
        apply(err)
        await nextTick()
        summaryRef.value?.focusSummary()
      }
      // 面板保持打开，backfill 保持用户输入。
    } finally {
      saving.value = false
    }
  })
}

/** 徽标的跳转（Contract 7）：带 ?focus= 指向优先级最高的未填字段。 */
const goProfileWithFocus = () => {
  const key = completenessStore.firstMissingKey()
  router.push({ path: '/prevention/profile', query: key ? { focus: key } : {} })
}

const viewDetail = (row: RiskAssessmentVO) => {
  currentAssessment.value = row
  detailVisible.value = true
}

onMounted(async () => {
  // 档案行只服务可选字段预填与 update 载荷，无人能在点击前消费它，因此不 await。
  loadProfile()

  // 三序判定与徽标都读它，必须最先落定；单飞且可等待，不会重复请求（13-05）。
  await completenessStore.fetchCompleteness()

  fetchAssessments()

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
  })
})
onUnmounted(() => { cleanupScrollTriggers() })
</script>

<style scoped>
.risk-page {
  padding: 20px;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* ---------- Contract 5：就地补填面板 ---------- */
.risk-estimate {
  margin-bottom: 16px;                            /* md */
  padding: 16px;                                  /* md */
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}

/* EP 的 alert 变量：warning 类型默认用 #e6a23c 作标题色，在 tint 上仅 2.04:1（AA 严重不达）。
   本契约把标题与正文一律压到 --text-1，图标保留暖橙（图形，3:1 达标）。 */
.risk-estimate :deep(.el-alert) {
  --el-alert-padding: 12px 16px;                  /* sm-3 / md */
  --el-alert-bg-color: var(--hhs-color-warning-tint);
  border-radius: var(--radius-md);
  color: var(--text-1);                           /* Light 15.86:1 / Dark 13.22:1 */
}
.risk-estimate :deep(.el-alert__title) {
  font: 600 14px/1.4 var(--font-ui);              /* 覆盖 EP 的 line-height: 24px */
  color: var(--text-1);
}
.risk-estimate :deep(.el-alert__icon) {
  color: var(--accent-warm);                      /* Light 3.32:1 / Dark 6.90:1 */
}

/* theme.css 全局 `ul { list-style: none; margin: 0; padding: 0 }`，
   假设列表必须重新声明为带悬挂缩进的真实列表。 */
.risk-estimate__list {
  margin: 12px 0 0;                               /* sm-3 */
  padding-left: 24px;                             /* lg */
  list-style: disc;
  display: flex;
  flex-direction: column;
  gap: 4px;                                       /* xs */
  font: 400 14px/1.5 var(--font-ui);              /* Body */
  color: var(--text-2);
}

.risk-estimate__form { margin-top: 16px; }        /* md */
.risk-estimate__form :deep(.el-form-item__label) {
  font: 600 14px/1.5 var(--font-ui) !important;
}
.risk-estimate__form :deep(.el-form-item) { margin-bottom: 16px; }
.risk-estimate__form :deep(.el-input__inner) {
  font: 400 14px/1.5 var(--font-ui) !important;
}

.risk-estimate__actions {
  margin-top: 16px;                               /* md */
  display: flex;
  align-items: center;
  gap: 8px;                                       /* sm */
  flex-wrap: wrap;
}
.risk-estimate__actions :deep(.el-button + .el-button) { margin-left: 0; }

@media (max-width: 639px) {
  .risk-estimate__actions { flex-direction: column; align-items: stretch; }
  .risk-estimate__actions :deep(.el-button) { width: 100%; }
}

/* ---------- Contract 4：徽标落点 ---------- */
.risk-disclosure {
  margin-bottom: 16px;                            /* md：与表格、与徽标上文各留 md */
}

/* ---------- 详情对话框：本次评估采用的假设 ---------- */
.risk-dialog-assumptions {
  margin-top: 16px;
}
.risk-dialog-assumptions__title {
  margin: 0 0 8px;
  font: 600 14px/1.4 var(--font-ui);
  color: var(--text-1);
}
.risk-dialog-assumptions__list {
  margin: 0;
  padding-left: 24px;
  list-style: disc;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font: 400 14px/1.5 var(--font-ui);
  color: var(--text-2);
}

/* ---------- Contract 6：按钮/标签归一化 ---------- */
.risk-page :deep(.el-button--primary) {
  font: 600 14px/1.5 var(--font-ui) !important;
}
.risk-page :deep(.el-button:not(.el-button--primary)) {
  font-weight: 400 !important;
  line-height: 1.5 !important;
}
.risk-page :deep(.el-form-item__label) {
  font: 600 14px/1.5 var(--font-ui) !important;
}
</style>
