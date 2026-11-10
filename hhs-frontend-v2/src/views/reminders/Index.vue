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
        <!-- 汇总块必须是 <el-form> 的第一个子节点（Contract 4.3）。 -->
        <FormErrorSummary
          ref="summaryRef"
          :summary-items="summaryItems"
          @focus-field="focusField"
        />
        <el-form-item
          label="提醒标题"
          prop="title"
          data-field="title"
          :error="fieldErrors['title']"
        >
          <el-input
            v-model="form.title"
            placeholder="请输入提醒标题"
            @update:model-value="clearField('title')"
          />
        </el-form-item>
        <el-form-item label="提醒描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选描述" />
        </el-form-item>
        <el-form-item
          label="提醒类型"
          prop="reminderType"
          data-field="reminderType"
          :error="fieldErrors['reminderType']"
        >
          <el-select
            v-model="form.reminderType"
            placeholder="请选择类型"
            style="width: 100%"
            @change="clearField('reminderType')"
          >
            <el-option label="用药提醒" value="medication" />
            <el-option label="运动提醒" value="exercise" />
            <el-option label="饮水提醒" value="water" />
            <el-option label="睡眠提醒" value="sleep" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item
          label="Cron表达式"
          prop="cronExpression"
          data-field="cronExpression"
          :error="fieldErrors['cronExpression']"
        >
          <el-input
            v-model="form.cronExpression"
            placeholder="例: 0 8 * * * (每天8点)"
            @update:model-value="clearField('cronExpression')"
          />
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
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { staggerReveal, cleanupScrollTriggers } from '@/composables/useGsap'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Clock, Delete } from '@element-plus/icons-vue'
import { remindersApi, type UserReminder } from '@/api/reminders'
import { useFormServerErrors, collectInvalidFieldMessages } from '@/composables/useFormServerErrors'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import { showError } from '@/utils/showError'

const loading = ref(false)
const submitting = ref(false)
const createVisible = ref(false)
const reminders = ref<UserReminder[]>([])
const formRef = ref<FormInstance>()
const summaryRef = ref<InstanceType<typeof FormErrorSummary> | null>(null)

const form = reactive({
  title: '',
  description: '',
  reminderType: '',
  cronExpression: ''
})

/**
 * `title` 在本页是「提醒标题」而非全局表的「标题」，`description` 同理 —— 按
 * Contract 4.5 规则 4 用 per-call `labels` 覆盖，绝不改全局表（改全局表会把
 * 档案页、阈值页、目标页的 title 一起误标）。`fieldOrder` 与页面视觉顺序一致。
 *
 * 本页只有一个表单，故直接解构（与 prevention/Profile.vue 同形，模板里靠 ref
 * 自动解包）；goals 页有两个表单需要用前缀区分，因此那边包了一层 `reactive()`。
 */
const { fieldErrors, summaryItems, apply, setErrors, clearField, reset, focusField } =
  useFormServerErrors({
    fieldOrder: ['title', 'reminderType', 'cronExpression'],
    labels: { title: '提醒标题', description: '提醒描述' }
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

/**
 * 列表加载失败：本页不渲染错误状态块，因此 `forbidden` / `notFound` 没有落点，
 * 一次加载失败必须换一条常驻 toast 而不是静默。
 */
const fetchReminders = async () => {
  loading.value = true
  try {
    const res = await remindersApi.list()
    reminders.value = res.data || []
  } catch (error) {
    showError(error, 'reminder.loadFailed', { presentStates: true })
  } finally {
    loading.value = false
  }
}

/** 弹窗打开时的初始化：不是失败路径，绝不搬进任何 catch（会破坏 ERR-05）。 */
const showCreateDialog = () => {
  form.title = ''
  form.description = ''
  form.reminderType = ''
  form.cronExpression = ''
  createVisible.value = true
}

const handleCreate = async () => {
  if (!formRef.value) return

  // 上一轮的残留投影不进入本次尝试；只清投影，不碰用户输入（ERR-05）。
  reset()

  await formRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      // 客户端校验失败也走同一份汇总通道（GOV.UK 双呈现），零 toast。
      setErrors(collectInvalidFieldMessages(invalidFields))
      await nextTick()
      summaryRef.value?.focusSummary()
      return
    }

    submitting.value = true
    try {
      await remindersApi.create({
        title: form.title,
        description: form.description || undefined,
        reminderType: form.reminderType,
        cronExpression: form.cronExpression
      })
      // 成功路径顺序保持不变：提示 → 关闭弹窗 → 重取列表。
      ElMessage.success('提醒创建成功')
      createVisible.value = false
      fetchReminders()
    } catch (error) {
      // validation → 0 toast，汇总 + 内联承担呈现；其余 kind 由 showError 呈现。
      const err = showError(error, 'reminder.saveFailed', { presentStates: true })
      if (err.kind === 'validation') {
        apply(err)
        await nextTick()
        summaryRef.value?.focusSummary()
      }
    } finally {
      submitting.value = false
    }
  })
}

/**
 * 切换启用/停用。
 *
 * 旧实现在 catch 里回退成一条字符串错误提示**再**调一次 `fetchReminders()` ——
 * 一次失败触发第二个请求，且第二个请求失败会再弹一条。失败分支现在只呈现一次，
 * 绝不重取。
 *
 * `UserReminderServiceImpl:57` 抛 `RESOURCE_NOT_FOUND`、`:60` 抛 `AUTH_FORBIDDEN`：
 * 切换一条在别的标签页里被删掉的提醒、或一条属于别人的提醒，都是这一动作的常规
 * 分支，因此第三个参数必带，否则两者都会彻底静默。
 *
 * 复用 `alert.updateFailed`（「无法更新预警状态」）而不是新增 `reminder.updateFailed`：
 * 该 key 已在字典中，措辞对「状态更新失败」通用且贴切；为近似文案再加一个键会
 * 形成两套词汇表并需要同 commit 同步 `errorMessages.spec.ts` 的计数断言。
 */
const handleToggle = async (id: number) => {
  try {
    await remindersApi.toggle(id)
    ElMessage.success('状态已更新')
  } catch (error) {
    showError(error, 'alert.updateFailed', { presentStates: true })
  }
}

/**
 * 删除：确认与请求彻底分离。
 *
 * 取消路径是一个裸 `return` —— 用户按了「取消」不是失败，零提示（D-12 / ERR-08）。
 * 原先的手写判断「只要不是字符串 cancel 就弹错」对 `AbortError` 与归一化后的
 * `ApiError('', 'cancel')` 都会误判成真失败，因此整段删掉。
 * `UserReminderServiceImpl:71-76` 对「行已消失」静默返回，`:78` 则抛 `AUTH_FORBIDDEN`，
 * 后者必须仍然可见。
 */
const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这条提醒吗？', '提示', { type: 'warning' })
  } catch {
    // 取消确认框：刻意静默，不弹任何提示。
    return
  }

  try {
    await remindersApi.remove(id)
    ElMessage.success('删除成功')
    fetchReminders()
  } catch (error) {
    showError(error, 'reminder.deleteFailed', { presentStates: true })
  }
}

onMounted(async () => {
  await fetchReminders()
  staggerReveal('.reminder-card', { stagger: 0.06, y: 20 })
})
onUnmounted(() => { cleanupScrollTriggers() })
</script>

<style scoped>
.reminders-page { padding: 20px; }
.header-actions { display: flex; justify-content: space-between; align-items: center; }
.reminder-card { margin-bottom: 20px; transition: all 0.3s; }
.reminder-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.reminder-title { font-size: 16px; font-weight: 500; color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; margin-right: 8px; }
.reminder-desc { font-size: 13px; color: var(--el-text-color-secondary); margin-bottom: 12px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.reminder-meta { margin-bottom: 16px; }
.cron-expression { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: var(--el-text-color-placeholder); background: var(--el-fill-color-light); padding: 2px 8px; border-radius: 4px; }
.reminder-footer { display: flex; justify-content: space-between; align-items: center; padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.form-tip { font-size: 12px; color: var(--el-text-color-placeholder); margin-top: 4px; }
</style>