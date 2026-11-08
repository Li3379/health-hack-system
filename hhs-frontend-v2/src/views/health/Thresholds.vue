<template>
  <div class="thresholds-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>阈值设置</span>
          <el-button type="primary" @click="showAddDialog">
            <el-icon><Plus /></el-icon>
            添加阈值
          </el-button>
        </div>
      </template>

      <!-- v-loading 挂在内容区而非 el-table：请求在途时表格分支可能未渲染（首载 0 行），
           教学空状态此时绝不出现（加载、空、错误三态互斥），只有遮罩可见。 -->
      <div v-loading="loading" class="thresholds-body">
        <!-- 加载失败：ErrorState 独占（D-02：一个失败只呈现一次，catch 不再叠加 toast） -->
        <ErrorState
          v-if="loadFailed"
          variant="server"
          title="无法加载阈值列表"
          description="可能是网络波动或服务暂时不可用，请重试。"
          :retry="fetchThresholds"
          retry-label="重新加载阈值列表"
          :retrying="loading"
        />

        <template v-else>
          <!-- 有数据：渲染全部存量行。D-20：不可告警指标（如血氧）不因缺席向导选择器而被隐藏/过滤 -->
          <template v-if="thresholds.length">
            <el-table :data="thresholds" stripe>
              <el-table-column
                prop="metricKey"
                label="指标类型"
                width="150"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ getMetricLabel(row.metricKey) }}
                </template>
              </el-table-column>
              <el-table-column label="严重高值" width="120" align="right">
                <template #default="{ row }">
                  <span class="threshold-value critical">{{ row.criticalHigh || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="警告高值" width="120" align="right">
                <template #default="{ row }">
                  <span class="threshold-value warning">{{ row.warningHigh || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="警告低值" width="120" align="right">
                <template #default="{ row }">
                  <span class="threshold-value warning">{{ row.warningLow || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="严重低值" width="120" align="right">
                <template #default="{ row }">
                  <span class="threshold-value critical">{{ row.criticalLow || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
                  <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination
              v-model:current-page="page"
              class="thresholds-pagination"
              :page-size="PAGE_SIZE"
              :total="total"
              layout="total, prev, pager, next"
              @current-change="handlePageChange"
            />
          </template>

          <!-- 教学型空状态（THR-07 / UI-SPEC Contract 6）：成功请求且 0 行时独占渲染。
               明确不用 EP 的通用空态组件 —— 这里解释默认规则并给出行动入口。 -->
          <div v-else-if="!loading" class="threshold-empty">
            <el-icon :size="48" class="threshold-empty__icon"><InfoFilled /></el-icon>
            <h3 class="threshold-empty__title">尚未设置个性化阈值</h3>
            <p class="threshold-empty__body">
              系统正在使用默认告警规则。设置个性化阈值后，将达到您设定的标准时才发出提醒。
            </p>
            <el-descriptions :column="1" border class="threshold-empty__defaults">
              <el-descriptions-item
                v-for="rule in defaultRuleRows"
                :key="rule.key"
                :label="rule.label"
              >
                {{ rule.description }}
              </el-descriptions-item>
            </el-descriptions>
            <el-button type="primary" @click="openWizard">使用推荐阈值</el-button>
          </div>
        </template>
      </div>
    </el-card>

    <!-- 三步向导（12-07）：指标选择 / 推荐预填 / 确认保存全部由其承载（D-03：页面不再维护指标白名单） -->
    <ThresholdWizard
      ref="wizardRef"
      v-model="wizardVisible"
      :editing="editingRow"
      @saved="handleSaved"
      @error="handleWizardError"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { gsap, cleanupScrollTriggers, DUR, EASE } from '@/composables/useGsap'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, InfoFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useMetricMetaStore } from '@/stores/metricMeta'
import { thresholdApi } from '@/api/threshold'
import { showError } from '@/utils/showError'
import { toCanonicalKey } from '@/utils/metricNormalizer'
import { formatDateTime, getMetricLabel } from '@/utils/format'
import ErrorState from '@/components/common/ErrorState.vue'
import ThresholdWizard from './components/ThresholdWizard.vue'
import type { UserThreshold, UserThresholdRequest } from '@/types/api'

const authStore = useAuthStore()
const store = useMetricMetaStore()

const PAGE_SIZE = 100
const loading = ref(false)
const thresholds = ref<UserThreshold[]>([])
const loadFailed = ref(false)
const total = ref(0)
const page = ref(1)

// —— 向导承载状态（12-07 契约面：props modelValue/editing，emits saved/error）——
const wizardVisible = ref(false)
const editingRow = ref<UserThreshold | null>(null)
const wizardRef = ref<InstanceType<typeof ThresholdWizard> | null>(null)
/** 提交失败的指标 key：向导路径取其暴露的 selectedKey，兼容路径取 form.metricKey。 */
const pendingMetricKey = ref('')

/**
 * 教学空状态的默认规则表（THR-07）：与向导同读一份 alertable 元数据，
 * 不维护第二份硬编码表（T-12-29：种子变更不会让页面教出过时数字）。
 * 推荐值缺失时回退到元数据正常范围上界，句子始终完整。
 */
const defaultRuleRows = computed(() =>
  store.alertableHealthMetrics.map(m => {
    const warningHigh = m.recommended?.warningHigh ?? m.normalMax
    const criticalHigh = m.recommended?.criticalHigh ?? m.normalMax
    return {
      key: m.canonicalKey,
      label: m.displayName,
      description: `正常 ${m.normalMin}–${m.normalMax} ${m.unit}，高于 ${warningHigh} 黄色提醒，高于 ${criticalHigh} 红色提醒`
    }
  })
)

// —— Phase 11 契约面 ——
// ErrorLayerSample.spec.ts 用例 6/7/8（RESEARCH §Pitfall 8）经组件实例驱动
// showAddDialog / form / handleSubmit / editingId / dialogVisible，且不可改动。
// 真实提交已移交向导（D-03）；form + handleSubmit 仅作为该契约的提交入口保留，
// 与向导路径共用 presentDuplicateError，保证重复指标恢复行为完全一致。
const form = reactive<UserThresholdRequest>({
  userId: authStore.user?.id || 0,
  metricKey: '',
  warningHigh: undefined,
  criticalHigh: undefined,
  warningLow: undefined,
  criticalLow: undefined
})
const dialogVisible = computed(() => wizardVisible.value)
const editingId = computed(() => editingRow.value?.id ?? null)

const resetCompatForm = (source?: UserThreshold) => {
  Object.assign(form, {
    userId: source?.userId ?? authStore.user?.id ?? 0,
    metricKey: source?.metricKey ?? '',
    warningHigh: source?.warningHigh,
    criticalHigh: source?.criticalHigh,
    warningLow: source?.warningLow,
    criticalLow: source?.criticalLow
  })
}

const fetchThresholds = async () => {
  loading.value = true
  try {
    const res = await thresholdApi.getThresholds({ page: page.value, size: PAGE_SIZE })
    thresholds.value = res.data.records || []
    total.value = res.data.total ?? thresholds.value.length
    loadFailed.value = false
  } catch {
    // D-02 分工（T-12-28）：页面级加载失败由 ErrorState 承担呈现与重试，
    // 此处只置状态即返回，绝不再走 showError —— 一个失败只呈现一次。
    loadFailed.value = true
    return
  } finally {
    loading.value = false
  }
}

/** 打开向导（新增模式）：空状态 CTA「使用推荐阈值」入口。 */
const openWizard = () => {
  editingRow.value = null
  wizardVisible.value = true
}

/** 列表头「添加阈值」入口（Phase 11 契约面）：等价 openWizard 并复位兼容表单。 */
const showAddDialog = () => {
  resetCompatForm()
  openWizard()
}

/** 行内「编辑」：整行交给向导（D-20：不可告警指标同样可编辑，弹窗内指标锁定）。 */
const handleEdit = (row: UserThreshold) => {
  editingRow.value = row
  wizardVisible.value = true
}

/**
 * 恢复动作（THR-08 / Contract 7）：先关闭再重开。向导只在 modelValue 变真时
 * 重置状态，true→true 不会触发其内部 watch，必须经一帧 false 让编辑模式生效。
 */
const openExistingThreshold = async (row: UserThreshold) => {
  if (wizardVisible.value) {
    wizardVisible.value = false
    await nextTick()
  }
  editingRow.value = row
  resetCompatForm(row)
  wizardVisible.value = true
}

/** 向导保存成功（新增与编辑共用）：成功提示归页面所有，随后刷新列表。 */
const handleSaved = () => {
  ElMessage.success(editingRow.value ? '更新成功' : '添加成功')
  fetchThresholds()
}

/**
 * 重复指标恢复（THR-08 / UI-SPEC Contract 7）：canonical 比较 —— 存量行可能
 * 是 'glucose' 这类历史别名，规范化后比较才能命中（T-12-27 / Pitfall 8）。
 * 标签由 ERROR_CODE_ACTIONS['resource.exists'] 自动补齐，本页不传 label；
 * 列表里找不到该指标时不传 action，绝不渲染死按钮（T-11-24）。
 */
const presentDuplicateError = (error: unknown) => {
  const existing = thresholds.value.find(
    t => toCanonicalKey(t.metricKey) === toCanonicalKey(pendingMetricKey.value)
  )
  showError(error, 'threshold.duplicate', {
    presentValidation: true,
    ...(existing ? { action: { onClick: () => openExistingThreshold(existing) } } : {})
  })
}

/** 向导业务失败（T-12-24：页面唯一错误 toast 出口）：失败指标 = 向导当前选定/锁定指标。 */
const handleWizardError = (error: unknown) => {
  pendingMetricKey.value = wizardRef.value?.selectedKey ?? pendingMetricKey.value
  presentDuplicateError(error)
}

/**
 * Phase 11 契约入口：ErrorLayerSample.spec.ts 用例 6/7/8 经组件实例驱动本入口
 * 复现「新增撞重复」流程（Pitfall 8）。校验失败显式放行到单一 toast 出口：
 * 本页未接入 useFormServerErrors、没有内联绑定，静默会让后端 400 完全不可见（WR-13）。
 */
const handleSubmit = async () => {
  pendingMetricKey.value = form.metricKey
  try {
    await thresholdApi.createThreshold({ ...form })
    ElMessage.success('添加成功')
    wizardVisible.value = false
    fetchThresholds()
  } catch (error) {
    presentDuplicateError(error)
  }
}

const handlePageChange = (next: number) => {
  page.value = next
  fetchThresholds()
}

const handleDelete = async (row: UserThreshold) => {
  try {
    await ElMessageBox.confirm('确定要删除这个阈值设置吗？', '删除阈值', {
      type: 'warning'
    })
  } catch {
    // 用户取消：零提示（D-12），不进入错误出口。
    return
  }
  await deleteThresholdConfirmed(row)
}

/**
 * 真正执行删除。
 *
 * 失败时的重试回调重新执行**整个删除动作**（含成功后的列表刷新与成功提示），
 * 而不是只重发 HTTP 请求 —— 否则重试成功后列表仍显示已删除的行。
 * showError 只在网络/传输层 5xx 时渲染「重试」，业务失败不会出现死按钮（D-03）。
 */
const deleteThresholdConfirmed = async (row: UserThreshold) => {
  try {
    await thresholdApi.deleteThreshold(row.id)
    ElMessage.success('删除成功')
    fetchThresholds()
  } catch (error) {
    showError(error, 'threshold.deleteFailed', {
      retry: () => deleteThresholdConfirmed(row)
    })
  }
}

// Phase 11 契约面：ErrorLayerSample.spec.ts 经组件实例访问以下成员（不可移除）。
defineExpose({
  showAddDialog,
  openWizard,
  handleEdit,
  openExistingThreshold,
  handleSubmit,
  form,
  dialogVisible,
  editingId,
  editingRow,
  wizardVisible
})

onMounted(() => {
  fetchThresholds()

  nextTick(() => {
    const tl = gsap.timeline({ defaults: { ease: EASE.out } })
    tl.fromTo(
      '.el-card',
      { y: 20, autoAlpha: 0 },
      {
        y: 0,
        autoAlpha: 1,
        duration: DUR.mid,
        clearProps: 'transform,autoAlpha,visibility,opacity'
      }
    )
    tl.fromTo(
      '.header-actions',
      { y: -10, autoAlpha: 0 },
      {
        y: 0,
        autoAlpha: 1,
        duration: DUR.mid,
        clearProps: 'transform,autoAlpha,visibility,opacity'
      },
      '-=0.2'
    )
  })
})
onUnmounted(() => {
  cleanupScrollTriggers()
})
</script>

<style scoped>
.thresholds-page {
  padding: 20px;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 请求在途时遮罩必须有可覆盖的区域（首载 0 行时各分支均不渲染） */
.thresholds-body {
  min-height: 200px;
}

/* UI-SPEC zero-one-many：列表同表 + 分页；中文量词「条」，无单复数变形 */
.thresholds-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

/* 列表行高固定 48px（UI-SPEC populated 行） */
.thresholds-page :deep(.el-table td.el-table__cell) {
  height: 48px;
}

.threshold-value {
  font-weight: 500;
  white-space: nowrap;
}

.threshold-value.critical {
  color: #f56c6c;
}

.threshold-value.warning {
  color: #e6a23c;
}

/* —— 教学型空状态（UI-SPEC Contract 6）—— */
.threshold-empty {
  padding: 48px 24px; /* 3xl 上下 / lg 左右 */
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.threshold-empty__icon {
  margin-bottom: 32px; /* xl */
  color: var(--text-3); /* 纯装饰图标，不承载信息，允许 3.97:1 */
}

.threshold-empty__title {
  font-size: 18px; /* Heading */
  font-weight: 600;
  line-height: 1.3;
  color: var(--text-1);
  margin-bottom: 8px; /* sm */
}

.threshold-empty__body {
  font-size: 14px; /* Body */
  line-height: 1.5;
  color: var(--text-2);
  margin-bottom: 24px; /* lg */
  max-width: 480px;
}

.threshold-empty__defaults {
  margin-bottom: 24px; /* lg */
  text-align: left;
  width: 100%;
  max-width: 560px;
}

/* EP 的 bordered 单元格 padding 是 8px 11px（off-grid），两处都归一化到契约刻度 */
.threshold-empty__defaults :deep(.el-descriptions__table.is-bordered .el-descriptions__cell) {
  padding: 8px 12px; /* sm / sm-3 */
}

.threshold-empty__defaults :deep(.el-descriptions__label.is-bordered-label) {
  font-size: 12px; /* Label */
  font-weight: 400;
  line-height: 1.4;
  color: var(--text-2);
}

.threshold-empty__defaults :deep(.el-descriptions__content) {
  font-size: 14px; /* Body */
  font-weight: 400;
  line-height: 1.5;
  color: var(--text-1);
  font-family: var(--font-mono);
  /* 内容列自然换行、绝不截断（UI-SPEC long-text E7） */
  word-break: break-word;
}

/* —— 按钮字重归一化（UI-SPEC Contract 9，页面级）——
   theme.css 用 !important 把主按钮锁成 500 14px/1（会裁切中文下缘），
   EP 默认按钮又是 500 字重；页面以同等优先级归一到 600/400 两档。
   向导对话框 teleport 到 body，其按钮归一化属全局 theme.css 规则，
   超出本计划 files_modified 范围（12-07 遗留，见 SUMMARY）。 */
.thresholds-page :deep(.el-button--primary) {
  font: 600 14px/1.5 var(--font-ui) !important;
}

.thresholds-page :deep(.el-button:not(.el-button--primary)) {
  font-weight: 400 !important;
  line-height: 1.5 !important;
}
</style>
