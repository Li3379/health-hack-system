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
        创建前加载态：点击后真实请求（档案 + 完整度）在跑，给用户明确的过程反馈，
        而不是点击后毫无反应地等待，或瞬间弹出一个空表单。
      -->
      <section v-if="preparing" class="risk-preparing" role="status" aria-live="polite">
        <el-skeleton :rows="3" animated />
        <p class="risk-preparing__text">正在读取你的健康档案与指标数据…</p>
      </section>

      <!--
        评估依据面板（Contract 5 修订）：不再二次填表。
        评估直接读取健康档案与指标数据，本面板只做两件事 ——
        ① 只读展示「本次将使用的数据」及每项来源，让用户知道分数基于什么；
        ② 明确指出缺失项并给出去补录的入口，而不是在这里重复录入。
        al-alert 只承载标题：EP 的默认插槽渲染在 <p> 内，列表必须是它的兄弟节点。
      -->
      <section v-if="panelOpen" class="risk-estimate" aria-labelledby="risk-estimate-title">
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            <span id="risk-estimate-title">本次评估将使用以下数据</span>
          </template>
        </el-alert>

        <ul class="risk-estimate__sources">
          <li
            v-for="item in dataSources"
            :key="item.key"
            class="risk-estimate__source"
            :class="`risk-estimate__source--${item.tone}`"
          >
            <span class="risk-estimate__source-label">{{ item.label }}</span>
            <span class="risk-estimate__source-value">{{ item.value }}</span>
            <span class="risk-estimate__source-origin">{{ item.origin }}</span>
          </li>
        </ul>

        <p v-if="missingFields.length" class="risk-estimate__hint">
          有 {{ missingFields.length }} 项数据缺失，相关风险按默认值或不参与估算。
          补齐后评分会更贴近你的真实情况。
        </p>

        <div class="risk-estimate__actions">
          <el-button type="primary" :loading="saving" @click="createAnyway">
            按现有数据创建评估
          </el-button>
          <el-button @click="goProfileWithFocus">
            去完善档案
          </el-button>
          <el-button text @click="cancel">取消</el-button>
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
        评分构成：逐项列出每个输入、取值、参考值与贡献分，各项之和等于总分。
        这是「让用户理解并信任」的核心 —— 没有它，用户只看到一个孤立的数字。
      -->
      <div v-if="currentAssessment?.contributors?.length" class="risk-dialog-breakdown">
        <p class="risk-dialog-breakdown__title">
          评分构成
          <span class="risk-dialog-breakdown__sum">
            各项合计 {{ contributorSum }} 分 = 风险评分
          </span>
        </p>
        <ul class="risk-dialog-breakdown__list">
          <li
            v-for="(item, idx) in currentAssessment.contributors"
            :key="`${item.key}-${idx}`"
            class="risk-dialog-breakdown__item"
            :class="`risk-dialog-breakdown__item--${item.state.toLowerCase()}`"
          >
            <span class="risk-dialog-breakdown__label">{{ item.label }}</span>
            <span class="risk-dialog-breakdown__state">{{ stateLabel(item.state) }}</span>
            <span class="risk-dialog-breakdown__points">{{ item.points > 0 ? `+${item.points}` : '0' }}</span>
            <span class="risk-dialog-breakdown__explain">{{ item.explanation }}</span>
          </li>
        </ul>
        <p
          v-if="missingContributorCount > 0"
          class="risk-dialog-breakdown__warn"
        >
          有 {{ missingContributorCount }} 项缺少数据，本结果未包含这些因素 ——
          补齐后评分可能上升，请勿把「未计入」当作「正常」。
        </p>
      </div>

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
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { preventionApi } from '@/api/prevention'
import { formatDateTime } from '@/utils/format'
import EstimateBadge from '@/components/common/EstimateBadge.vue'
import { useProfileCompletenessStore } from '@/stores/profileCompleteness'
import { showError } from '@/utils/showError'
import type { HealthProfileVO, RiskAssessmentVO } from '@/types/api'

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

// 面板不再渲染 assumptions 列表：它现在按「将使用的数据」逐项展示来源，
// 缺失项本身即说明（见 dataSources）。assumptions 仍在详情弹窗里原样呈现，
// 那份来自 RiskAssessmentVO，与 ProfileFieldCatalog.assumptionsFor 同源。

// 面板状态
const panelOpen = ref(false)
const saving = ref(false)
/** 创建前的加载态：真实请求在跑，用户看到的是「正在读取你的数据」而不是空白等待。 */
const preparing = ref(false)

// 档案本身（只读展示数据来源用）。
const profileData = ref<HealthProfileVO | null>(null)

/**
 * 「本次评估将使用的数据」——只读，逐项标注来源。
 *
 * 这里刻意不做任何输入：评估直接读健康档案与指标，用户不需要也不应该在风险页重复录入。
 * 需要补的数据一律引导到档案页（`goProfileWithFocus`），保持单一录入入口。
 */
const dataSources = computed<DataSourceRow[]>(() => {
  const p = profileData.value
  const rows: DataSourceRow[] = []

  // 年龄
  if (p?.birthDate) {
    const age = calcAge(p.birthDate)
    rows.push({ key: 'age', label: '年龄', value: `${age} 岁`, origin: '健康档案', tone: 'ok' })
  } else {
    rows.push({ key: 'age', label: '年龄', value: '未填，按 35 岁估算', origin: '档案缺失', tone: 'assumed' })
  }

  // BMI
  if (p?.bmi != null) {
    rows.push({ key: 'bmi', label: 'BMI', value: String(p.bmi), origin: '健康档案', tone: 'ok' })
  } else {
    rows.push({ key: 'bmi', label: 'BMI', value: '未填，按 22.0 估算', origin: '档案缺失', tone: 'assumed' })
  }

  // 家族史
  if (p?.familyHistory) {
    rows.push({ key: 'family', label: '家族病史', value: p.familyHistory, origin: '健康档案', tone: 'ok' })
  } else {
    rows.push({
      key: 'family',
      label: '家族病史',
      value: '未填，按「无家族史」计算',
      origin: '档案缺失',
      tone: 'assumed'
    })
  }

  // 血压 / 血糖来自指标录入，不来自档案 —— 这是用户最容易误解的一处，必须点名来源。
  for (const item of metricSources.value) {
    rows.push(item)
  }

  return rows
})

/**
 * 指标来源行：跨**全部**评估汇总，不能只看第一条。
 *
 * 血压只出现在高血压的归因里、血糖只出现在糖尿病的归因里，若只读 `assessments[0]`
 * 就会把「有血压数据」误报成「未录入」，与下方表格的分数自相矛盾。
 */
const metricSources = computed<DataSourceRow[]>(() => {
  const wanted: Array<{ key: string; label: string }> = [
    { key: 'systolicBP', label: '收缩压' },
    { key: 'diastolicBP', label: '舒张压' },
    { key: 'bloodGlucose', label: '血糖' }
  ]

  // 汇总所有评估的归因，取每个 key 第一次出现的非缺失项。
  const found = new Map<string, { value: string; state: string }>()
  for (const assessment of assessments.value) {
    for (const c of assessment.contributors ?? []) {
      if (!found.has(c.key) && c.state !== 'MISSING' && c.value) {
        found.set(c.key, { value: c.value, state: c.state })
      }
    }
  }

  return wanted.map(w => {
    const hit = found.get(w.key)
    if (!hit) {
      return {
        key: w.key,
        label: w.label,
        value: '暂无数据，此项未计入',
        origin: '指标未录入',
        tone: 'missing' as const
      }
    }
    return {
      key: w.key,
      label: w.label,
      value: hit.value,
      origin: '指标录入',
      tone: 'ok' as const
    }
  })
})

/** 缺失的数据项（档案 + 指标），驱动提示语与「去完善档案」的聚焦字段。 */
const missingFields = computed(() => dataSources.value.filter(r => r.tone !== 'ok'))

/** 周岁计算，与后端 Period.between 语义一致（生日未到不算一岁）。 */
function calcAge(birthDate: string): number {
  const b = new Date(birthDate)
  const now = new Date()
  let age = now.getFullYear() - b.getFullYear()
  const m = now.getMonth() - b.getMonth()
  if (m < 0 || (m === 0 && now.getDate() < b.getDate())) age--
  return age
}

/** 只读数据来源行：评估依据的可视化，不承载任何输入。 */
interface DataSourceRow {
  key: string
  label: string
  value: string
  origin: string
  tone: 'ok' | 'assumed' | 'missing'
}

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
    // 表格保留原有行（UI Considerations · error E7）：失败时不清空 assessments。
    // 键复用 profile.*：这是健康档案的「风险」页，两者共享同一个后端聚合（见台账理由）。
    showError(error, 'profile.loadFailed', { presentStates: true })
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
    showError(error, 'profile.saveFailed', { presentStates: true })
  }
}

/**
 * 三序判定（Contract 5，先假设后分数，命中即停）。
 *
 * 步骤 1 是正确性前提：首次访问时 store 里还没有 payload，若直接判定，空假设列表会
 * 让面板永不展开、徽标永不出现。store 的 fetchCompleteness 单飞且可等待（13-05），
 * 因此这里 await 会真的等到挂载中的那个请求落定，而不是读到尚未落定的 null。
 */
/**
 * 点击「创建评估」。
 *
 * 与旧实现的关键差别：**不再二次填表**。评估直接读健康档案与指标数据，
 * 所以这里只做「加载数据 → 展示依据 → 用户确认」三步。
 *
 * `preparing` 覆盖真实请求（档案 + 完整度 + 最近评估），让用户看到「正在读取你的数据」，
 * 而不是点击后毫无反馈地等待或瞬间弹出一个空表单。
 */
const handleCreateClick = async () => {
  preparing.value = true
  try {
    // 档案与完整度并行拉取：两者都只读，互不依赖。
    await Promise.all([
      loadProfile(),
      completeness.value === null && !completenessStore.loadFailed
        ? completenessStore.fetchCompleteness()
        : Promise.resolve()
    ])
  } finally {
    // 即使某个请求失败也让面板出现：用户需要看到「哪些数据取不到」而不是卡在加载态。
    preparing.value = false
  }

  // 数据齐备且无缺失 → 无需展示依据，直接创建。
  if (missingFields.value.length === 0) {
    await createRiskAssessment()
    return
  }

  panelOpen.value = true
  await nextTick()
  document.querySelector<HTMLElement>('.risk-estimate')?.scrollIntoView({ block: 'center' })
}

/** 用户确认按现有数据创建（缺失项按默认值或不计入）。 */
const createAnyway = async () => {
  panelOpen.value = false
  await createRiskAssessment()
}

/** 用户主动放弃不是失败（Phase 11 D-12）：零 toast、零 API 调用。 */
const cancel = () => {
  panelOpen.value = false
}
const goProfileWithFocus = () => {
  const key = completenessStore.firstMissingKey()
  router.push({ path: '/prevention/profile', query: key ? { focus: key } : {} })
}

/** 归因各项之和。后端保证等于 riskScore；这里单独算出来是为了让用户能自己核对。 */
const contributorSum = computed(
  () => (currentAssessment.value?.contributors ?? []).reduce((a, c) => a + c.points, 0)
)

/** 缺失项数量：驱动「请勿把未计入当作正常」的警示。 */
const missingContributorCount = computed(
  () => (currentAssessment.value?.contributors ?? []).filter(c => c.state === 'MISSING').length
)

/** 四种状态的用户可读标签。 */
const stateLabel = (state: string): string => {
  const labels: Record<string, string> = {
    CONTRIBUTED: '已计入',
    NORMAL: '正常',
    ASSUMED: '按默认值',
    MISSING: '缺数据'
  }
  return labels[state] ?? state
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

// 供测试驱动详情弹窗：表格列由 Element Plus 渲染，组件测试的 stub 拿不到行内按钮。
defineExpose({ viewDetail, handleCreateClick })
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

/* 只读数据来源列表：每行「字段 — 取值 — 来源」，来源用色区分可信度。 */
.risk-estimate__sources {
  margin: 16px 0 0;                               /* md */
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: var(--border-1);
  border: 1px solid var(--border-1);
  border-radius: 6px;
  overflow: hidden;
}

.risk-estimate__source {
  display: grid;
  grid-template-columns: 96px 1fr auto;
  gap: 12px;
  align-items: baseline;
  padding: 10px 12px;
  background: var(--surface-1);
  font: 400 14px/1.5 var(--font-ui);
}

.risk-estimate__source-label {
  font-weight: 600;
  color: var(--text-1);
}

.risk-estimate__source-value {
  color: var(--text-1);
  overflow-wrap: anywhere;
}

.risk-estimate__source-origin {
  font: 500 11px/1.4 var(--font-mono);
  padding: 2px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

.risk-estimate__source--ok .risk-estimate__source-origin {
  color: var(--success-700, #15803d);
  background: var(--success-50, #f0fdf4);
}

.risk-estimate__source--assumed .risk-estimate__source-origin {
  color: var(--warning-700, #b45309);
  background: var(--warning-50, #fffbeb);
}

.risk-estimate__source--missing .risk-estimate__source-origin {
  color: var(--hhs-color-error-text);
  background: var(--hhs-color-error-tint);
}

.risk-estimate__source--missing .risk-estimate__source-value {
  color: var(--hhs-color-error-text);
}

.risk-estimate__hint {
  margin: 12px 0 0;
  font: 400 13px/1.6 var(--font-ui);
  color: var(--text-2);
}

/* 创建前加载态。 */
.risk-preparing {
  margin-bottom: 16px;
  padding: 20px;
  border: 1px solid var(--border-1);
  border-radius: 8px;
  background: var(--surface-1);
}

.risk-preparing__text {
  margin: 12px 0 0;
  font: 400 13px/1.5 var(--font-ui);
  color: var(--text-2);
  text-align: center;
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

/* ---------- 详情对话框：评分构成 ---------- */
.risk-dialog-breakdown {
  margin-top: 16px;
}

.risk-dialog-breakdown__title {
  margin: 0 0 8px;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font: 600 14px/1.4 var(--font-ui);
  color: var(--text-1);
}

.risk-dialog-breakdown__sum {
  font: 400 12px/1.4 var(--font-mono);
  color: var(--text-2);
}

.risk-dialog-breakdown__list {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--border-1);
  border-radius: 6px;
  overflow: hidden;
}

.risk-dialog-breakdown__item {
  display: grid;
  grid-template-columns: 80px 72px 48px 1fr;
  gap: 10px;
  align-items: baseline;
  padding: 8px 12px;
  background: var(--surface-1);
  border-bottom: 1px solid var(--border-1);
  font: 400 13px/1.5 var(--font-ui);
}

.risk-dialog-breakdown__item:last-child {
  border-bottom: none;
}

.risk-dialog-breakdown__label {
  font-weight: 600;
  color: var(--text-1);
}

.risk-dialog-breakdown__state {
  font: 500 11px/1.4 var(--font-mono);
  padding: 1px 6px;
  border-radius: 4px;
  text-align: center;
  white-space: nowrap;
}

.risk-dialog-breakdown__points {
  font: 600 13px/1.5 var(--font-mono);
  text-align: right;
  color: var(--text-1);
}

.risk-dialog-breakdown__explain {
  color: var(--text-2);
}

/* 四种状态：已计入=红（贡献风险）、正常=绿、默认值=橙、缺数据=灰红 */
.risk-dialog-breakdown__item--contributed .risk-dialog-breakdown__state {
  color: var(--hhs-color-error-text);
  background: var(--hhs-color-error-tint);
}

.risk-dialog-breakdown__item--contributed .risk-dialog-breakdown__points {
  color: var(--hhs-color-error-text);
}

.risk-dialog-breakdown__item--normal .risk-dialog-breakdown__state {
  color: var(--success-700, #15803d);
  background: var(--success-50, #f0fdf4);
}

.risk-dialog-breakdown__item--assumed .risk-dialog-breakdown__state {
  color: var(--warning-700, #b45309);
  background: var(--warning-50, #fffbeb);
}

.risk-dialog-breakdown__item--missing .risk-dialog-breakdown__state {
  color: var(--text-2);
  background: var(--surface-2);
}

.risk-dialog-breakdown__item--missing .risk-dialog-breakdown__explain {
  font-style: italic;
}

.risk-dialog-breakdown__warn {
  margin: 10px 0 0;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--hhs-color-error-tint);
  color: var(--hhs-color-error-text);
  font: 400 13px/1.5 var(--font-ui);
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
