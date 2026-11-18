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

      <!--
        指标覆盖面板：逐项回答「我的哪项参数被看了、它说了什么」。
        只给一个风险总分时，「测了且正常」与「根本没测」在界面上无法区分，
        这张表把两者分开，并标出哪些指标真正参与评估。
      -->
      <section class="risk-coverage" aria-labelledby="risk-coverage-title">
        <header class="risk-coverage__head">
          <h4 id="risk-coverage-title" class="risk-coverage__title">我的指标覆盖情况</h4>
          <span class="risk-coverage__summary">
            已测 {{ coverageSummary.measured }} / {{ coverageSummary.total }} 项
            <!--
              措辞随 16-05 的分类来源同批变更：classify 改为按风险阈值分级后，
              这里的「超出参考范围」会与面板「偏高」pill 自相矛盾（收缩压 130–139
              判偏高却不计入参考范围）。本计划不动它，故留此注记并非漏改。
            -->
            <template v-if="coverageSummary.abnormal > 0">
              · <span class="risk-coverage__abnormal">{{ coverageSummary.abnormal }} 项超出参考范围</span>
            </template>
            <!--
              第三段描述「评估范围」而不是「使用」：8 项纳入但只有 3 项有值时，
              「8 项参与」读起来像「用了 8 项」。括号段用 S1 自己的词（与同一批行
              状态列渲染的那个词一致），不用 S2 归因态的措辞。
            -->
            <template v-if="coverageSummary.used > 0">
              · {{ coverageSummary.used }} 项纳入评估范围<template v-if="usedMissing > 0">（其中 {{ usedMissing }} 项未测）</template>
            </template>
          </span>
        </header>

        <div v-if="coverageLoading" class="risk-coverage__loading">
          <el-skeleton :rows="2" animated />
        </div>

        <template v-else>
          <!-- 参与评估的指标单独置顶：这是用户最该看清的一组。 -->
          <div v-if="usedMetrics.length" class="risk-coverage__group">
            <p class="risk-coverage__group-title">参与风险评估的指标</p>
            <ul class="risk-coverage__list">
              <li
                v-for="m in usedMetrics"
                :key="m.key"
                class="risk-coverage__item"
                :class="`risk-coverage__item--${m.state.toLowerCase()}`"
              >
                <span class="risk-coverage__label">{{ m.label }}</span>
                <span class="risk-coverage__value">{{ m.value ?? '未测' }}</span>
                <span class="risk-coverage__range">{{ m.normalRange ?? '—' }}</span>
                <span class="risk-coverage__state">{{ coverageStateLabel(m.state) }}</span>
                <span class="risk-coverage__note">{{ m.riskNote }}</span>
              </li>
            </ul>
          </div>

          <div class="risk-coverage__group">
            <p class="risk-coverage__group-title">
              其他已测指标
              <span class="risk-coverage__hint">（有数据，但不参与本次风险评估）</span>
            </p>
            <ul v-if="otherMeasured.length" class="risk-coverage__list">
              <li
                v-for="m in otherMeasured"
                :key="m.key"
                class="risk-coverage__item"
                :class="`risk-coverage__item--${m.state.toLowerCase()}`"
              >
                <span class="risk-coverage__label">{{ m.label }}</span>
                <span class="risk-coverage__value">{{ m.value }}</span>
                <span class="risk-coverage__range">{{ m.normalRange ?? '—' }}</span>
                <span class="risk-coverage__state">{{ coverageStateLabel(m.state) }}</span>
              </li>
            </ul>
            <p v-else class="risk-coverage__empty">暂无其他已测指标。录入指标后，未被风险评估使用的项目会显示在这里。</p>
          </div>

          <div v-if="missingMetrics.length" class="risk-coverage__group">
            <p class="risk-coverage__group-title">
              尚未采集
              <span class="risk-coverage__hint">
                （{{ missingMetrics.length }} 项；这些不是「正常」，是「不知道」）
              </span>
            </p>
            <ul class="risk-coverage__list risk-coverage__list--missing">
              <li v-for="m in missingMetrics" :key="m.key" class="risk-coverage__item risk-coverage__item--missing">
                <span class="risk-coverage__label">{{ m.label }}</span>
                <span class="risk-coverage__state">未测</span>
                <span v-if="m.riskNote" class="risk-coverage__note">{{ m.riskNote }}</span>
              </li>
            </ul>
          </div>
        </template>
      </section>

      <!--
        历史按「一次评估」折叠：`batchId` 是后端在同一次评估里写给三条疾病的
        同一个批次号。平铺表格让用户分不清哪三行属于同一次评估，也看不出这次
        比上次是好是坏 —— 这两件事正是本区域存在的理由。

        用 el-collapse（数组模型，非 accordion）让多个批次可同时展开；最近一个
        批次默认展开，其余折叠。只有 1 个批次时也显示「首次评估」，不造出「↑0」。
      -->
      <div v-loading="loading" class="risk-batches">
        <el-collapse v-if="pagedBatches.length" v-model="activeBatchNames">
          <el-collapse-item
            v-for="batch in pagedBatches"
            :key="batch.batchId"
            :name="batch.batchId"
            class="risk-batch"
          >
            <template #title>
              <div class="risk-batch__header">
                <span class="risk-batch__time">{{ formatDateTime(batch.createTime) }}</span>
                <el-tag :type="getRiskLevelType(batch.worstLevel)" size="small">
                  最高风险：{{ getRiskLevelLabel(batch.worstLevel) }}
                </el-tag>
                <span v-if="batch.isLegacy" class="risk-batch__legacy-badge">存量记录</span>
              </div>
            </template>

            <div class="risk-batch__rows">
              <p v-if="!batch.hasDetail" class="risk-batch__legacy">
                该记录创建时未保存评分明细
              </p>

              <div
                v-for="row in batch.rows"
                :key="row.assessment.id"
                class="risk-batch__row"
              >
                <span class="risk-batch__disease">{{ row.assessment.diseaseName }}</span>

                <el-tag :type="getRiskLevelType(row.assessment.riskLevel)" size="small">
                  {{ getRiskLevelLabel(row.assessment.riskLevel) }}
                </el-tag>

                <span class="risk-batch__score">
                  {{ row.assessment.riskScore || '-' }}
                  <!--
                    分差用绝对分 + 方向，不用百分比：风险评分不是比率，40→44 是
                    4 分而不是 +10%。持平单独说「持平」，避免出现「↑0」噪音。
                  -->
                  <span
                    v-if="row.isFirst"
                    class="risk-batch__delta risk-batch__delta--first"
                  >首次评估</span>
                  <span
                    v-else-if="row.delta === 0"
                    class="risk-batch__delta risk-batch__delta--flat"
                  >持平</span>
                  <span
                    v-else
                    class="risk-batch__delta"
                    :class="row.delta! > 0 ? 'risk-batch__delta--up' : 'risk-batch__delta--down'"
                  >{{ row.delta! > 0 ? `↑${row.delta}` : `↓${Math.abs(row.delta!)}` }}</span>
                </span>

                <!--
                  等级变化与分数箭头分开呈现：44 → 46 只涨 2 分，但跨过阈值就是
                  低→中，这才是用户真正需要知道的那件事。
                -->
                <span
                  v-if="row.levelChanged"
                  class="risk-batch__level-change"
                >{{ getRiskLevelLabel(row.previousLevel!) }} → {{ getRiskLevelLabel(row.assessment.riskLevel) }}</span>

                <span class="risk-batch__suggestion">{{ row.assessment.suggestion || '暂无建议' }}</span>

                <el-button link type="primary" @click="viewDetail(row.assessment)">详情</el-button>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>

        <p v-else-if="!loading" class="risk-batches__empty">暂无风险评估记录</p>
      </div>

      <!--
        分页是纯客户端的：后端一次返回全部记录，不吃 page/size。所以翻页不重新
        请求，只重算 pagedBatches 并让展开态跟随新页面的最新批次。
      -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="handlePageChange"
          @current-change="handlePageChange"
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
import type { HealthProfileVO, MetricCoverageVO, RiskAssessmentVO } from '@/types/api'

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

/** 一条疾病在某个批次里的呈现：原记录 + 与紧邻上一批次的比较结果。 */
interface BatchRow {
  assessment: RiskAssessmentVO
  /** 无对照组（最早一批 / 上一批没有同名疾病）时为 true，显示「首次评估」。 */
  isFirst: boolean
  /** 与上一批次同疾病的绝对分差；无对照组时为 null。 */
  delta: number | null
  /** 上一批次同疾病的等级，用于渲染「中风险 → 高风险」。 */
  previousLevel: string | null
  levelChanged: boolean
}

/** 一个批次 = 一次评估，内含该次产出的全部疾病。 */
interface BatchGroup {
  batchId: string
  createTime: string
  /** 本批次内最高的风险等级，用于头部摘要。 */
  worstLevel: string
  /** 归因快照是否可用；存量记录为 false，不渲染明细。 */
  hasDetail: boolean
  /** 无 batchId 的存量记录，归入 legacy-<createTime> 批次。 */
  isLegacy: boolean
  rows: BatchRow[]
}

/**
 * 等级强弱排序。刻意只做「谁更严重」的比较，不重复后端的 60/35 分数阈值：
 * 等级是后端算好的结论，前端只负责排序与展示。
 */
const LEVEL_RANK: Record<string, number> = { HIGH: 3, MEDIUM: 2, LOW: 1 }

const rankLevel = (level: string): number => LEVEL_RANK[level] ?? 0

const worstLevelOf = (rows: RiskAssessmentVO[]): string => {
  let worst = rows[0]?.riskLevel ?? 'LOW'
  for (const r of rows) {
    if (rankLevel(r.riskLevel) > rankLevel(worst)) worst = r.riskLevel
  }
  return worst
}

/**
 * 把平铺的评估记录折叠成批次。
 *
 * `assessments` 由后端按 createTime DESC 返回，因此 Map 的插入序天然就是
 * 批次的时间倒序 —— 这里刻意不排序，避免与后端顺序产生分歧。
 *
 * 分差只与**紧邻的上一个批次**比（D-02）：用户关心「这次比上次怎么样」，
 * 不是「这次比三个月前怎么样」。上一批缺少同名疾病时不伪造比较，按首次处理。
 *
 * 存量记录（无 `batchId`）不能按 `createTime` **精确相等**分组：该列是
 * `DATETIME DEFAULT CURRENT_TIMESTAMP`，秒精度。同一次旧评估的三条记录如果在
 * 秒边界两侧写入，会被精确相等切成两张卡片（2 条 + 1 条），把「一次评估」
 * 展成「两次评估」，比分差还更容易误导人。
 *
 * 因此存量记录按**时间邻近**聚类：与当前簇首条的间隔在 LEGACY_CLUSTER_MS 之内
 * 就并入同一簇。阈值取 2 秒 —— 三条记录的写入间隔在毫秒级，2 秒足以吸收秒边界
 * 与轻微时钟抖动，又远小于「两次评估」之间真实存在的间隔（用户手动触发）。
 */
const LEGACY_CLUSTER_MS = 2000

const parseTime = (value: string): number => {
  // `2026-10-01 08:00:00` 在 Safari/部分环境下 new Date() 解析不稳定，
  // 统一替换成 ISO 分隔符，失败则返回 NaN 由调用方退化为「不聚类」。
  const ms = Date.parse(value.replace(' ', 'T'))
  return Number.isFinite(ms) ? ms : NaN
}

const batches = computed<BatchGroup[]>(() => {
  const map = new Map<string, RiskAssessmentVO[]>()
  // 存量簇的时间锚点：clusterKey → 该簇首条记录的毫秒时间戳。
  const clusterAnchor = new Map<string, number>()
  let clusterSeq = 0

  for (const a of assessments.value) {
    let key: string
    if (a.batchId) {
      key = a.batchId
    } else {
      const t = parseTime(a.createTime)
      // 找最近的一个仍在时间邻近窗口内的存量簇。
      let joined: string | null = null
      if (Number.isFinite(t)) {
        for (const [candidate, anchor] of clusterAnchor) {
          if (Math.abs(anchor - t) <= LEGACY_CLUSTER_MS) {
            joined = candidate
            break
          }
        }
      }
      if (joined) {
        key = joined
      } else {
        key = `legacy-${a.createTime}${Number.isFinite(t) ? `-${clusterSeq++}` : ''}`
        if (Number.isFinite(t)) clusterAnchor.set(key, t)
      }
    }
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(a)
  }

  const groups = [...map.entries()].map(([batchId, rows]) => ({
    batchId,
    createTime: rows[0]?.createTime ?? '',
    worstLevel: worstLevelOf(rows),
    hasDetail: rows.some(r => (r.contributors?.length ?? 0) > 0),
    isLegacy: batchId.startsWith('legacy-'),
    rows
  }))

  // 第 N 个批次的对照组是第 N+1 个（时间上更早的那个）。
  return groups.map((group, index) => {
    const previous = groups[index + 1]
    const previousByDisease = new Map(
      (previous?.rows ?? []).map(r => [r.diseaseName, r])
    )

    const rows: BatchRow[] = group.rows.map(assessment => {
      const prev = previousByDisease.get(assessment.diseaseName)
      // 评分缺失或没有对照组时，不编造分差。
      if (!prev || assessment.riskScore == null || prev.riskScore == null) {
        return {
          assessment,
          isFirst: true,
          delta: null,
          previousLevel: null,
          levelChanged: false
        }
      }
      return {
        assessment,
        isFirst: false,
        delta: assessment.riskScore - prev.riskScore,
        previousLevel: prev.riskLevel,
        levelChanged: prev.riskLevel !== assessment.riskLevel
      }
    })

    return { ...group, rows }
  })
})

/**
 * 分页按**批次**而不是按行。
 *
 * 后端 `GET /api/prevention/risk-assessment` 一次返回该用户全部记录，不吃
 * page/size（见 PreventionController#getRiskAssessment）。因此这里是客户端切分。
 *
 * 必须按批次切：一次评估产出三条记录，若按行分页，一个批次会被劈到两页之间，
 * 卡片只剩一到两项疾病，且「与上一批次对比」的对照组落到另一页上。按行分页
 * 还会让分页器显示 12 条而屏幕上只有 4 张卡片 —— 用户被告知有第二页，点进去
 * 却什么都没有。
 */
const pagedBatches = computed<BatchGroup[]>(() => {
  const start = (pagination.page - 1) * pagination.size
  return batches.value.slice(start, start + pagination.size)
})

/** 分页器的总数单位与展示单位一致：都是「批次」。 */
const syncPaginationTotal = () => {
  pagination.total = batches.value.length
}

/**
 * 当前页默认展开哪个批次：**本页最新**的那一个。
 *
 * 不能锚定全局最新批次：翻到第 2 页时全局最新批次不在屏幕上，`activeBatchNames`
 * 指向一个不可见的 name，本页唯一那张卡片就渲染成折叠状态 —— 用户翻页后看到
 * 一张点开才有内容的空壳。展开态必须跟着可见页面走。
 */
const defaultActiveBatch = computed<string[]>(() => {
  const newest = pagedBatches.value[0]
  return newest ? [newest.batchId] : []
})

/**
 * 用户手动开合过的批次名。null 表示「用户还没动过」，此时跟随默认值。
 *
 * 用 watch 把用户操作与默认值分开：否则每次翻页都会覆盖用户刚打开的批次。
 */
const manualActiveBatch = ref<string[] | null>(null)

const activeBatchNames = computed<string[]>({
  get: () => manualActiveBatch.value ?? defaultActiveBatch.value,
  set: value => {
    manualActiveBatch.value = value
  }
})

/** 翻页 / 重新加载后丢弃用户的手动展开态，重新跟随「本页最新」。 */
const resetActiveBatches = () => {
  manualActiveBatch.value = null
}

/**
 * 翻页：数据早已在客户端，不需要重新请求 —— 重新请求只会拿到同一份全量列表，
 * 白白多一次往返。只需丢弃手动展开态，让展开跟随新页面。
 *
 * 改 page-size 可能让当前页越界（例如从 50 条/页改到 10 条/页且正停在第 3 页），
 * 越界会渲染出空页，因此夹回最后一页。
 */
const handlePageChange = () => {
  const lastPage = Math.max(1, Math.ceil(pagination.total / pagination.size))
  if (pagination.page > lastPage) pagination.page = lastPage
  resetActiveBatches()
}

/** 跳到第一页并展开最新批次 —— 让用户**看见**刚创建的那次评估。 */
const goToNewestBatch = () => {
  pagination.page = 1
  resetActiveBatches()
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
    // 分页单位是批次，不是行：12 条记录可能是 4 个批次（见 pagedBatches）。
    syncPaginationTotal()
    // 重新加载后回到「本页最新批次展开」：创建评估后新批次才会自动展开。
    resetActiveBatches()
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
    // 新批次是最新的一个，必然排在第 1 页开头。若用户当时停在第 3 页，
    // 不跳回第 1 页就会出现「提示创建成功、却找不到新卡片」—— 用户会以为失败了。
    goToNewestBatch()
    await fetchAssessments()
    loadCoverage()
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

// ---------- 指标覆盖面板 ----------

const coverage = ref<MetricCoverageVO[]>([])
const coverageLoading = ref(false)

/** 参与风险评估的指标（有 riskNote 的），缺失项也要列出来 —— 那正是用户最需要知道的。 */
const usedMetrics = computed(() => coverage.value.filter(m => !!m.riskNote))

/** 有数据但不参与评估的指标。 */
const otherMeasured = computed(() =>
  coverage.value.filter(m => !m.riskNote && m.state !== 'MISSING')
)

/**
 * 尚未采集的指标 —— 只收「引擎不读、你也没有」的那批。
 *
 * 谓词必须带 `!m.riskNote`，否则「有 riskNote 且 MISSING」的指标会同时出现在
 * 上面两组里（血脂四项在心血管评估里确实参与，D-02）。把它从「参与」组去掉是
 * 说谎；它在「参与」组里已经以缺失状态诚实呈现，所以减掉的是这一组。
 */
const missingMetrics = computed(() =>
  coverage.value.filter(m => m.state === 'MISSING' && !m.riskNote)
)

/**
 * 已纳入评估范围但没有取值的项数。
 *
 * 必须从 `usedMetrics` 派生，不能从 `coverage` 全局筛 —— 全局筛会把不参与评估
 * 的缺失项也算进去，摘要括号段的数字就会大于实际纳入范围的缺失项数。
 */
const usedMissing = computed(
  () => usedMetrics.value.filter(m => m.state === 'MISSING').length
)

/** 顶部摘要：已测 / 异常 / 参与评估 三个数字。 */
const coverageSummary = computed(() => ({
  total: coverage.value.length,
  measured: coverage.value.filter(m => m.state !== 'MISSING').length,
  abnormal: coverage.value.filter(m => m.state === 'HIGH' || m.state === 'LOW').length,
  used: usedMetrics.value.length
}))

/** 覆盖面板的状态标签；与风险归因的四态刻意用不同措辞，避免混淆两个概念。 */
const coverageStateLabel = (state: string): string => {
  const labels: Record<string, string> = {
    HIGH: '偏高',
    LOW: '偏低',
    NORMAL: '正常',
    MISSING: '未测'
  }
  return labels[state] ?? state
}

/** 加载指标覆盖情况。失败不打扰用户：面板留空即可，风险结果本身仍然可用。 */
const loadCoverage = async () => {
  coverageLoading.value = true
  try {
    const res = await preventionApi.getMetricCoverage()
    coverage.value = Array.isArray(res.data) ? res.data : []
  } catch {
    coverage.value = []
  } finally {
    coverageLoading.value = false
  }
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
  loadCoverage()

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

/* ---------- 风险历史：按批次折叠 ---------- */
.risk-batches {
  min-height: 80px;
}

.risk-batch__header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.risk-batch__time {
  font-weight: 600;
  color: var(--text-1);
}

.risk-batch__legacy-badge {
  font-size: 12px;
  color: var(--text-3);
}

.risk-batch {
  margin-bottom: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  overflow: hidden;
}

.risk-batch__rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.risk-batch__row {
  display: grid;
  /*
    六列，与模板的子元素一一对应：疾病 / 等级标签 / 分数+分差 / 等级变化 / 建议 / 详情。
    等级变化是条件渲染的（只在跨等级时出现），所以它那列用 minmax(0, auto)：
    缺省时收成 0 宽，不会把「建议」和「详情」挤到第二行。
    早先这里只声明了 5 列，跨等级的行会把详情按钮折到下一行 —— 而那恰恰是最该看清的行。
  */
  grid-template-columns: 110px auto 150px minmax(0, auto) minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
}

.risk-batch__disease {
  font-weight: 600;
}

.risk-batch__score {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  font-variant-numeric: tabular-nums;
}

.risk-batch__delta {
  font-size: 12px;
  font-weight: 600;
}

.risk-batch__delta--up {
  color: var(--error);
}

.risk-batch__delta--down {
  color: var(--success);
}

.risk-batch__delta--flat,
.risk-batch__delta--first {
  color: var(--text-3);
  font-weight: 400;
}

/* 等级变化与分数箭头分开：跨阈值是用户真正需要知道的事。 */
.risk-batch__level-change {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: var(--hhs-color-warning-tint);
  color: var(--warning-700, #b45309);
  white-space: nowrap;
}

.risk-batch__suggestion {
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.risk-batch__legacy {
  margin: 0;
  color: var(--text-2);
  font-size: 13px;
}

.risk-batches__empty {
  padding: 32px 0;
  text-align: center;
  color: var(--text-2);
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
  background: var(--border);
  border: 1px solid var(--border);
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
  border: 1px solid var(--border);
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

/* ---------- 指标覆盖面板 ---------- */
.risk-coverage {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface-1);
}

.risk-coverage__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.risk-coverage__title {
  margin: 0;
  font: 600 14px/1.4 var(--font-ui);
  color: var(--text-1);
}

.risk-coverage__summary {
  font: 400 12px/1.4 var(--font-mono);
  color: var(--text-2);
}

.risk-coverage__abnormal {
  color: var(--hhs-color-error-text);
  font-weight: 600;
}

.risk-coverage__group + .risk-coverage__group {
  margin-top: 14px;
}

.risk-coverage__group-title {
  margin: 0 0 6px;
  font: 600 12px/1.4 var(--font-ui);
  color: var(--text-2);
}

.risk-coverage__hint {
  font-weight: 400;
  color: var(--text-2);
}

.risk-coverage__list {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: hidden;
}

.risk-coverage__item {
  display: grid;
  grid-template-columns: 96px 88px 96px 56px 1fr;
  gap: 10px;
  align-items: baseline;
  padding: 7px 12px;
  background: var(--surface-1);
  border-bottom: 1px solid var(--border);
  font: 400 13px/1.5 var(--font-ui);
}

.risk-coverage__item:last-child {
  border-bottom: none;
}

/* 未测分组没有取值/范围列，用更窄的栅格。 */
.risk-coverage__list--missing .risk-coverage__item {
  grid-template-columns: 96px 56px 1fr;
}

.risk-coverage__label {
  font-weight: 600;
  color: var(--text-1);
}

.risk-coverage__value {
  font-family: var(--font-mono);
  color: var(--text-1);
}

.risk-coverage__range {
  font: 400 12px/1.5 var(--font-mono);
  color: var(--text-2);
}

.risk-coverage__state {
  font: 500 11px/1.4 var(--font-mono);
  padding: 1px 6px;
  border-radius: 4px;
  text-align: center;
  white-space: nowrap;
}

.risk-coverage__note {
  font: 400 12px/1.5 var(--font-ui);
  color: var(--text-2);
}

.risk-coverage__item--high .risk-coverage__state {
  color: var(--hhs-color-error-text);
  background: var(--hhs-color-error-tint);
}

.risk-coverage__item--high .risk-coverage__value {
  color: var(--hhs-color-error-text);
  font-weight: 600;
}

.risk-coverage__item--low .risk-coverage__state {
  color: var(--warning-700, #b45309);
  background: var(--warning-50, #fffbeb);
}

.risk-coverage__item--low .risk-coverage__value {
  color: var(--warning-700, #b45309);
  font-weight: 600;
}

.risk-coverage__item--normal .risk-coverage__state {
  color: var(--success-700, #15803d);
  background: var(--success-50, #f0fdf4);
}

.risk-coverage__item--missing .risk-coverage__state {
  color: var(--text-2);
  background: var(--surface-2);
}

.risk-coverage__empty {
  margin: 0;
  padding: 8px 12px;
  font: 400 12px/1.5 var(--font-ui);
  color: var(--text-2);
}

.risk-coverage__loading {
  padding: 8px 0;
}

@media (max-width: 767px) {
  .risk-coverage__item {
    grid-template-columns: 1fr auto;
    row-gap: 2px;
  }
  .risk-coverage__range,
  .risk-coverage__note {
    grid-column: 1 / -1;
  }
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
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: hidden;
}

.risk-dialog-breakdown__item {
  display: grid;
  grid-template-columns: 88px 72px 48px 1fr;
  gap: 10px;
  align-items: baseline;
  padding: 8px 12px;
  background: var(--surface-1);
  border-bottom: 1px solid var(--border);
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

/*
  窄屏：六列横排会横向溢出（实测 375px 下 scrollWidth 444 > clientWidth 375）。
  折成两列 —— 疾病名与等级标签占第一行，分数、分差、等级变化、建议、详情各自落到
  后续整行，任何一项都不再与另一项争同一格。
*/
@media (max-width: 767px) {
  .risk-batch__row {
    grid-template-columns: minmax(0, 1fr) auto;
    row-gap: 6px;
  }
  .risk-batch__score,
  .risk-batch__level-change,
  .risk-batch__suggestion {
    grid-column: 1 / -1;
  }
  .risk-batch__suggestion {
    white-space: normal;
    overflow-wrap: anywhere;
  }
  .risk-batch__row > .el-button {
    grid-column: 2;
    justify-self: end;
  }
}
</style>
