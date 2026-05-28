<template>
  <div class="reminders-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>提醒管理</span>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            添加提醒
          </el-button>
        </div>
      </template>
      <el-skeleton :loading="loading" animated>
        <template #template>
          <el-row :gutter="20">
            <el-col v-for="n in 4" :key="n" :xs="24" :sm="12" :md="8" :lg="6">
              <el-card shadow="hover" class="reminder-card">
                <el-skeleton-item variant="text" style="width: 60%; height: 20px;" />
                <el-skeleton-item variant="text" style="width: 100%; height: 14px; margin-top: 12px;" />
                <el-skeleton-item variant="text" style="width: 40%; height: 14px; margin-top: 8px;" />
              </el-card>
            </el-col>
          </el-row>
        </template>
        <template #default>
          <el-empty v-if="!loading && reminders.length === 0" description="暂无提醒" />
          <el-row v-else :gutter="20">
            <el-col v-for="reminder in reminders" :key="reminder.id" :xs="24" :sm="12" :md="8" :lg="6">
              <el-card shadow="hover" class="reminder-card">
                <div class="reminder-header">
                  <span class="reminder-title">{{ reminder.title }}</span>
                  <el-tag size="small" :type="getTypeTagType(reminder.reminderType)">
                    {{ getTypeLabel(reminder.reminderType) }}
                  </el-tag>
                </div>
                <div v-if="reminder.description" class="reminder-desc">
                  {{ reminder.description }}
                </div>
                <div class="reminder-meta">
                  <span class="cron-expression">
                    <el-icon><Clock /></el-icon>
                    {{ reminder.cronExpression }}
                  </span>
                </div>
                <div class="reminder-footer">
                  <el-switch v-model="reminder.isActive" @change="handleToggle(reminder.id)"
                    active-text="启用" inactive-text="停用" inline-prompt />
                  <el-button link type="danger" @click="handleDelete(reminder.id)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </template>
      </el-skeleton>
    </el-card>
    <el-dialog v-model="createVisible" title="添加提醒" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="提醒标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入提醒标题" />
        </el-form-item>
        <el-form-item label="提醒描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选描述" />
        </el-form-item>
        <el-form-item label="提醒类型" prop="reminderType">
          <el-select v-model="form.reminderType" placeholder="请选择类型" style="width: 100%">
            <el-option label="用药提醒" value="medication" />
            <el-option label="运动提醒" value="exercise" />
            <el-option label="饮水提醒" value="water" />
            <el-option label="睡眠提醒" value="sleep" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron表达式" prop="cronExpression">
          <el-input v-model="form.cronExpression" placeholder="例: 0 8 * * * (每天8点)" />
          <div class="form-tip">格式: 秒 分 时 日 月 星期</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Clock, Delete } from '@element-plus/icons-vue'
import { remindersApi, type UserReminder } from '@/api/reminders'

const loading = ref(false)
const submitting = ref(false)
const createVisible = ref(false)
const reminders = ref<UserReminder[]>([])
const formRef = ref<FormInstance>()

const form = reactive({
  title: '',
  description: '',
  reminderType: '',
  cronExpression: ''
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入提醒标题', trigger: 'blur' }],
  reminderType: [{ required: true, message: '请选择提醒类型', trigger: 'change' }],
  cronExpression: [{ required: true, message: '请输入Cron表达式', trigger: 'blur' }]
}

const typeLabels: Record<string, string> = {
  medication: '用药',
  exercise: '运动',
  water: '饮水',
  sleep: '睡眠',
  custom: '自定义'
}

const typeTagTypes: Record<string, string> = {
  medication: 'danger',
  exercise: 'success',
  water: 'primary',
  sleep: 'warning',
  custom: 'info'
}

const getTypeLabel = (type: string): string => typeLabels[type] || type
const getTypeTagType = (type: string): string => typeTagTypes[type] || 'info'

const fetchReminders = async () => {
  loading.value = true
  try {
    const res = await remindersApi.list()
    reminders.value = res.data || []
  } catch (error) {
    ElMessage.error('获取提醒列表失败')
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => {
  form.title = ''
  form.description = ''
  form.reminderType = ''
  form.cronExpression = ''
  createVisible.value = true
}

const handleCreate = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await remindersApi.create({
        title: form.title,
        description: form.description || undefined,
        reminderType: form.reminderType,
        cronExpression: form.cronExpression
      })
      ElMessage.success('提醒创建成功')
      createVisible.value = false
      fetchReminders()
    } catch (error) {
      ElMessage.error('创建提醒失败')
    } finally {
      submitting.value = false
    }
  })
}

const handleToggle = async (id: number) => {
  try {
    await remindersApi.toggle(id)
    ElMessage.success('状态已更新')
  } catch (error) {
    ElMessage.error('更新状态失败')
    fetchReminders()
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这条提醒吗？', '提示', { type: 'warning' })
    await remindersApi.remove(id)
    ElMessage.success('删除成功')
    fetchReminders()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchReminders()
})
</script>

<style scoped>
.reminders-page { padding: 20px; }
.header-actions { display: flex; justify-content: space-between; align-items: center; }
.reminder-card { margin-bottom: 20px; transition: all 0.3s; }
.reminder-card:hover { transform: translateY(-2px); }
.reminder-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.reminder-title { font-size: 16px; font-weight: 500; color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; margin-right: 8px; }
.reminder-desc { font-size: 13px; color: var(--el-text-color-secondary); margin-bottom: 12px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.reminder-meta { margin-bottom: 16px; }
.cron-expression { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: var(--el-text-color-placeholder); background: var(--el-fill-color-light); padding: 2px 8px; border-radius: 4px; }
.reminder-footer { display: flex; justify-content: space-between; align-items: center; padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.form-tip { font-size: 12px; color: var(--el-text-color-placeholder); margin-top: 4px; }
</style>