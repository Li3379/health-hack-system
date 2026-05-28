<template>
  <div class="compare-page">
    <el-page-header title="返回" @back="goBack">
      <template #content><span class="page-title">筛查报告对比</span></template>
    </el-page-header>
    <el-card v-loading="loading" class="compare-card">
      <div v-if="data" class="compare-content">
        <div class="compare-headers">
          <div class="header-a">
            <h3>{{ data.screeningA.reportName }}</h3>
            <p>{{ data.screeningA.reportDate }} · {{ data.screeningA.institution }}</p>
          </div>
          <div class="header-b">
            <h3>{{ data.screeningB.reportName }}</h3>
            <p>{{ data.screeningB.reportDate }} · {{ data.screeningB.institution }}</p>
          </div>
        </div>
        <el-row :gutter="20" class="summary-row">
          <el-col :span="8">
            <div class="summary-item improved">
              <div class="summary-value">{{ data.summary.improvedCount }}</div>
              <div class="summary-label">改善</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="summary-item worsened">
              <div class="summary-value">{{ data.summary.worsenedCount }}</div>
              <div class="summary-label">恶化</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="summary-item stable">
              <div class="summary-value">{{ data.summary.stableCount }}</div>
              <div class="summary-label">稳定</div>
            </div>
          </el-col>
        </el-row>
        <el-table :data="data.comparisons" stripe>
          <el-table-column prop="metricDisplayName" label="指标名称" min-width="180" />
          <el-table-column label="A值" width="120">
            <template #default="{ row }">{{ row.valueA ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="B值" width="120">
            <template #default="{ row }">{{ row.valueB ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="变化" width="120">
            <template #default="{ row }">
              <span v-if="row.changePercent !== null">{{ row.changePercent > 0 ? '+' : '' }}{{ row.changePercent }}%</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'improved'" type="success" size="small">改善</el-tag>
              <el-tag v-else-if="row.status === 'worsened'" type="danger" size="small">恶化</el-tag>
              <el-tag v-else type="info" size="small">稳定</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-empty v-else-if="!loading" description="无法加载对比数据" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { screeningCompareApi, type ScreeningComparisonVO } from '@/api/screening-compare'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const data = ref<ScreeningComparisonVO | null>(null)

const idA = Number(route.params.idA)
const idB = Number(route.params.idB)

const fetchComparison = async () => {
  if (!idA || !idB) { ElMessage.error('参数错误'); return }
  loading.value = true
  try {
    const res = await screeningCompareApi.compare(idA, idB)
    data.value = res.data
  } catch (error) {
    ElMessage.error('获取对比数据失败')
  } finally {
    loading.value = false
  }
}

const goBack = () => { router.back() }

onMounted(() => { fetchComparison() })
</script>

<style scoped>
.compare-page { padding: 20px; }
.page-title { font-size: 18px; font-weight: 500; }
.compare-card { margin-top: 20px; }
.compare-headers { display: flex; justify-content: space-around; margin-bottom: 24px; text-align: center; }
.compare-headers h3 { font-size: 16px; color: var(--el-text-color-primary); margin-bottom: 4px; }
.compare-headers p { font-size: 13px; color: var(--el-text-color-secondary); }
.summary-row { margin-bottom: 24px; }
.summary-item { text-align: center; padding: 16px; border-radius: 8px; }
.summary-item.improved { background: #f0f9eb; }
.summary-item.improved .summary-value { color: #67c23a; }
.summary-item.worsened { background: #fef0f0; }
.summary-item.worsened .summary-value { color: #f56c6c; }
.summary-item.stable { background: #f4f4f5; }
.summary-item.stable .summary-value { color: #909399; }
.summary-value { font-size: 32px; font-weight: 700; }
.summary-label { font-size: 13px; color: var(--el-text-color-secondary); margin-top: 4px; }
</style>