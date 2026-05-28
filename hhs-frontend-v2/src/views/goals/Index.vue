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
                  <el-button type="danger" size="small" link @click="handleDelete(goal.id)">删除</el-button>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </template>
      </el-skeleton>
    </el-card>
    <el-dialog v-model="createVisible" title="创建健康目标" width="500px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="目标标题" prop="title">
          <el-input v-model="createForm.title" placeholder="例: 降低体重至70kg" />
        </el-form-item>
        <el-form-item label="目标描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" placeholder="可选描述" />
        </el-form-item>
        <el-form-item label="指标类型" prop="metricKey">
          <el-select v-model="createForm.metricKey" placeholder="请选择指标" style="width: 100%">
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
        <el-form-item label="目标值" prop="targetValue">
          <el-input-number v-model="createForm.targetValue" :precision="1" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="createForm.unit" placeholder="例: kg, 次/分, 步" />
        </el-form-item>
        <el-form-item label="起止日期" prop="dateRange">
          <el-date-picker v-model="createForm.dateRange" type="daterange" range-separator="至"
            start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">确认创建</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="progressVisible" title="记录进度" width="400px">
      <el-form ref="progressFormRef" :model="progressForm" :rules="progressRules" label-width="80px">
        <el-form-item label="当前值" prop="value">
          <el-input-number v-model="progressForm.value" :precision="1" :min="0" style="width: 100%" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { goalsApi, type HealthGoal } from '@/api/goals'

const loading = ref(false)
const submitting = ref(false)
const submittingProgress = ref(false)
const createVisible = ref(false)
const progressVisible = ref(false)
const goals = ref<HealthGoal[]>([])
const currentGoalId = ref<number | null>(null)
const createFormRef = ref<FormInstance>()
const progressFormRef = ref<FormInstance>()

const createForm = reactive({
  title: '', description: '', metricKey: '', targetValue: 0, unit: '',
  dateRange: null as [string, string] | null
})

const progressForm = reactive({ value: 0, note: '' })

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

const fetchGoals = async () => { loading.value = true; try { const r = await goalsApi.list(); goals.value = r.data || [] } catch (e) { ElMessage.error('获取目标列表失败') } finally { loading.value = false } }
const showCreateDialog = () => { createForm.title = ''; createForm.description = ''; createForm.metricKey = ''; createForm.targetValue = 0; createForm.unit = ''; createForm.dateRange = null; createVisible.value = true }
const handleCreate = async () => { if (!createFormRef.value) return; await createFormRef.value.validate(async (v) => { if (!v) return; submitting.value = true; try { await goalsApi.create({ title: createForm.title, description: createForm.description || undefined, metricKey: createForm.metricKey, targetValue: createForm.targetValue, unit: createForm.unit, startDate: createForm.dateRange![0], endDate: createForm.dateRange![1] }); ElMessage.success('目标创建成功'); createVisible.value = false; fetchGoals() } catch (e) { ElMessage.error('创建目标失败') } finally { submitting.value = false } }) }
const showProgressDialog = (g: HealthGoal) => { currentGoalId.value = g.id; progressForm.value = g.currentValue; progressForm.note = ''; progressVisible.value = true }
const handleAddProgress = async () => { if (!progressFormRef.value || !currentGoalId.value) return; await progressFormRef.value.validate(async (v) => { if (!v) return; submittingProgress.value = true; try { await goalsApi.addProgress(currentGoalId.value!, { value: progressForm.value, note: progressForm.note || undefined }); ElMessage.success('进度更新成功'); progressVisible.value = false; fetchGoals() } catch (e) { ElMessage.error('更新进度失败') } finally { submittingProgress.value = false } }) }
const handleDelete = async (id: number) => { try { await ElMessageBox.confirm('确定要删除这个目标吗？', '提示', { type: 'warning' }); await goalsApi.remove(id); ElMessage.success('删除成功'); fetchGoals() } catch (e) { if (e !== 'cancel') ElMessage.error('删除失败') } }
onMounted(() => { fetchGoals() })
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