<template>
  <el-dialog
    v-model="visible"
    title="检测到严重生理危急值"
    width="480px"
    :close-on-click-modal="false"
    append-to-body
    class="critical-confirm-dialog"
  >
    <div class="critical-dialog-body">
      <div class="warning-banner">
        <el-icon class="warning-icon"><BellFilled /></el-icon>
        <div class="warning-text">
          <p class="main-warning">
            您录入的 <strong>{{ metricName }}</strong> 为
            <span class="value-highlight">{{ value }} {{ unit }}</span>，已达到临床重度危急预警阈值。
          </p>
          <p class="sub-warning">
            如属误触请输入正确数值；若测量属实，请保持平静并立即遵医嘱就医或呼叫急救。
          </p>
        </div>
      </div>

      <div class="emergency-guide-box">
        <div class="guide-title">
          <el-icon><FirstAidKit /></el-icon>
          <span>紧急健康处置建议：</span>
        </div>
        <ul class="guide-list">
          <li>保持镇定，立即坐下或平卧休息，切勿进行剧烈体力活动</li>
          <li>静坐 10 ~ 15 分钟后使用经过校验的仪器重新复测一次</li>
          <li>若伴有胸闷胸痛、头晕冷汗、呼吸急促或视物模糊，请立即联系家属就医或拨打 120 急救电话</li>
        </ul>
      </div>
    </div>

    <template #footer>
      <div class="dialog-actions">
        <el-button type="warning" plain @click="handleConfirm">
          确认录入无误并保存
        </el-button>
        <el-button type="primary" @click="handleCancel">
          返回核对修改
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BellFilled, FirstAidKit } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: boolean
  metricName: string
  value: number | string
  unit: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val)
})

const handleConfirm = () => {
  emit('confirm')
  visible.value = false
}

const handleCancel = () => {
  emit('cancel')
  visible.value = false
}
</script>

<style scoped>
.critical-dialog-body {
  padding: 8px 0;
}

.warning-banner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: var(--el-color-danger-light-9, #fef0f0);
  border: 1px solid var(--el-color-danger-light-5, #fde2e2);
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
}

.warning-icon {
  font-size: 24px;
  color: var(--el-color-danger, #f56c6c);
  margin-top: 2px;
  flex-shrink: 0;
}

.warning-text {
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-primary, #303133);
}

.main-warning {
  margin: 0 0 6px 0;
}

.sub-warning {
  margin: 0;
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
}

.value-highlight {
  font-weight: 700;
  color: var(--el-color-danger, #f56c6c);
  font-size: 14px;
}

.emergency-guide-box {
  background: var(--el-fill-color-light, #fafafa);
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 6px;
  padding: 12px 16px;
}

.guide-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular, #606266);
  margin-bottom: 8px;
}

.guide-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
  line-height: 1.6;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
