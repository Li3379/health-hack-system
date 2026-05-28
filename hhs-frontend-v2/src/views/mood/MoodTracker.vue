<template>
  <div class="mood-page">
    <el-row :gutter="20">
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header><span>记录心情</span></template>
          <div class="mood-input-section">
            <div class="mood-label">今天心情如何？</div>
            <div class="mood-score-selector">
              <button v-for="n in 10" :key="n" class="mood-btn" :class="{ active: form.moodScore === n }" @click="form.moodScore = n">
                {{ n }}
              </button>
            </div>
            <div class="mood-hint">当前选择: {{ form.moodScore }} / 10 — {{ getMoodText(form.moodScore) }}</div>
            <el-divider />
            <el-form label-width="80px">
              <el-form-item label="精力值">
                <el-slider v-model="form.energyLevel" :min="1" :max="10" :marks="sliderMarks" show-stops />
              </el-form-item>
              <el-form-item label="压力值">
                <el-slider v-model="form.stressLevel" :min="1" :max="10" :marks="sliderMarks" show-stops />
              </el-form-item>
              <el-form-item label="睡眠质量">
                <el-slider v-model="form.sleepQuality" :min="1" :max="10" :marks="sliderMarks" show-stops />
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="form.notes" type="textarea" :rows="3" placeholder="记录今天的感受..." />
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
              <div v-if="insights" class="insights-grid">
                <div class="insight-item">
                  <div class="insight-value">{{ insights.avgMoodScore.toFixed(1) }}</div>
                  <div class="insight-label">平均心情</div>
                </div>
                <div class="insight-item">
                  <div class="insight-value">{{ insights.avgEnergyLevel.toFixed(1) }}</div>
                  <div class="insight-label">平均精力</div>
                </div>
                <div class="insight-item">
                  <div class="insight-value">{{ insights.avgStressLevel.toFixed(1) }}</div>
                  <div class="insight-label">平均压力</div>
                </div>
                <div class="insight-item">
                  <div class="insight-value">{{ insights.avgSleepQuality.toFixed(1) }}</div>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { moodApi, type MoodEntry, type MoodInsights } from '@/api/mood'
import { formatDate } from '@/utils/format'

const submitting = ref(false)
const insightsLoading = ref(false)
const entries = ref<MoodEntry[]>([])
const insights = ref<MoodInsights | null>(null)

const form = reactive({ moodScore: 5, energyLevel: 5, stressLevel: 5, sleepQuality: 5, notes: '' })
const sliderMarks: Record<number, string> = { 1: '1', 5: '5', 10: '10' }

const moodTexts: string[] = ['', '很差', '很差', '较差', '较差', '一般', '一般', '较好', '较好', '很好', '很好']
const getMoodText = (score: number): string => moodTexts[score] || ''

const getMoodColor = (score: number): string => {
  if (score >= 8) return '#67c23a'
  if (score >= 6) return '#409eff'
  if (score >= 4) return '#e6a23c'
  return '#f56c6c'
}

const fetchData = async () => {
  insightsLoading.value = true
  try {
    const [entriesRes, insightsRes] = await Promise.all([moodApi.list(), moodApi.getInsights(30)])
    entries.value = entriesRes.data || []
    insights.value = insightsRes.data || null
  } catch (error) {
    ElMessage.error('获取数据失败')
  } finally {
    insightsLoading.value = false
  }
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    await moodApi.create({
      moodScore: form.moodScore,
      energyLevel: form.energyLevel,
      stressLevel: form.stressLevel,
      sleepQuality: form.sleepQuality,
      notes: form.notes || undefined
    })
    ElMessage.success('记录成功')
    form.notes = ''
    fetchData()
  } catch (error) {
    ElMessage.error('记录失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (id: number) => { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await moodApi.remove(id); ElMessage.success('删除成功'); fetchData() } catch (e) { if (e !== 'cancel') ElMessage.error('删除失败') } }

onMounted(() => { fetchData() })
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