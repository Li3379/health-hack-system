<template>
  <div v-if="definition" class="reference-guide-card">
    <el-card shadow="never" class="guide-box">
      <div class="guide-header">
        <el-icon class="header-icon"><InfoFilled /></el-icon>
        <span class="header-title">临床生理参考指南</span>
        <el-tag size="small" type="info" effect="plain" class="header-tag">标准解读</el-tag>
      </div>

      <div class="guide-content">
        <!-- 血压指标高低压双参考区间并列展示 -->
        <div v-if="diastolicDef" class="bp-reference-grid">
          <div class="ref-item">
            <span class="ref-label">收缩压 (高压)</span>
            <span class="ref-value">{{ definition.normalMin }} ~ {{ definition.normalMax }} {{ definition.unit }}</span>
          </div>
          <div class="ref-item">
            <span class="ref-label">舒张压 (低压)</span>
            <span class="ref-value">{{ diastolicDef.normalMin }} ~ {{ diastolicDef.normalMax }} {{ diastolicDef.unit }}</span>
          </div>
        </div>

        <!-- 单项常规指标参考区间展示 -->
        <div v-else class="single-reference">
          <span class="ref-label">{{ definition.displayName }}常规参考区间</span>
          <span class="ref-value">{{ definition.normalMin }} ~ {{ definition.normalMax }} {{ definition.unit }}</span>
        </div>

        <!-- 临床建议与注意事项 -->
        <div v-if="definition.guideHint" class="guide-hint-text">
          <el-icon class="hint-icon"><Clock /></el-icon>
          <span>{{ definition.guideHint }}</span>
        </div>

        <!-- 针对高频特殊指标的临床补充提醒 -->
        <div v-if="isGlucose" class="clinical-note">
          <span>注：正常成人空腹血糖为 3.9 ~ 6.1 mmol/L，餐后2小时血糖应 &lt; 7.8 mmol/L。</span>
        </div>
        <div v-else-if="diastolicDef" class="clinical-note">
          <span>注：测量前请静坐休息 5 分钟，避免剧烈运动、情绪激动及饮用浓茶或咖啡。</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { InfoFilled, Clock } from '@element-plus/icons-vue'
import type { MetricDefinitionVO } from '@/types/api'

const props = defineProps<{
  definition?: MetricDefinitionVO
  diastolicDef?: MetricDefinitionVO
}>()

const isGlucose = computed(() => {
  const key = props.definition?.canonicalKey
  return key === 'bloodGlucose' || key === 'fastingGlucose' || key === 'postprandialGlucose'
})
</script>

<style scoped>
.reference-guide-card {
  margin-top: 12px;
}

.guide-box {
  background: var(--el-fill-color-light, #fafafa);
  border: 1px dashed var(--el-border-color, #e4e7ed);
  border-radius: 8px;
}

.guide-box :deep(.el-card__body) {
  padding: 12px 16px;
}

.guide-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.header-icon {
  color: var(--el-color-primary, #409eff);
  font-size: 16px;
}

.header-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.header-tag {
  margin-left: auto;
  font-size: 11px;
}

.guide-content {
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
}

.bp-reference-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 8px;
}

.ref-item,
.single-reference {
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color, #ffffff);
  padding: 6px 10px;
  border-radius: 4px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.ref-label {
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
}

.ref-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-success, #67c23a);
  margin-top: 2px;
}

.guide-hint-text {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  color: var(--el-text-color-regular, #606266);
  line-height: 1.4;
}

.hint-icon {
  font-size: 13px;
  color: var(--el-color-info, #909399);
}

.clinical-note {
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
  line-height: 1.4;
}
</style>
