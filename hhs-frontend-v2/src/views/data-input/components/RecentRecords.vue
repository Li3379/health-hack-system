<template>
  <el-card shadow="hover" class="recent-records-card">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <el-icon><Clock /></el-icon>
          <span>最近录入记录</span>
        </div>
        <el-button type="primary" text size="small" @click="refresh">刷新</el-button>
      </div>
    </template>

    <el-table v-loading="loading" :data="records" stripe>
      <!-- 记录日期是用户填的那个日子；录入时间是行被写入库的时刻。补录历史数据时两者不同，
           只显示后者会让人以为记录被改成了今天，所以两列都在，标题也各自说清口径。 -->
      <el-table-column label="记录日期" width="110">
        <template #default="{ row }">
          {{ formatRecordDate(row.recordDate) }}
        </template>
      </el-table-column>
      <el-table-column label="录入时间" width="140">
        <template #default="{ row }">
          {{ formatCreateTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="指标" width="120">
        <template #default="{ row }">
          {{ row.metricName || row.metricKey }}
        </template>
      </el-table-column>
      <el-table-column label="数值">
        <template #default="{ row }">{{ row.value }} {{ row.unit }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Clock } from '@element-plus/icons-vue'
import { healthApi } from '@/api/health'

const loading = ref(false)
const records = ref<any[]>([])

/** 展示条数。后端 /api/metrics/recent 会把它夹到 1..50。 */
const RECENT_LIMIT = 10

/**
 * 加载最近录入记录（健康指标与保健指标混排）。
 *
 * 这里曾经并行请求 /api/metrics 与 /api/wellness 各 10 条，浏览器合并后 slice(0, 10)。
 * 两个接口各自锁死一个 category、各自截断，排序字段还不同（create_time vs record_date），
 * 所以保健类近 10 条一旦占满，刚录入的健康指标会被整段挤掉 —— 用户以为没保存成功。
 * 现在由后端一条查询按 create_time 跨 category 排序，名额不再被单类吃光。
 */
const loadRecords = async () => {
  loading.value = true
  try {
    const res = await healthApi.getRecentMetrics(RECENT_LIMIT)
    records.value = res.code === 200 ? res.data || [] : []
  } catch (error) {
    // records load failed
  } finally {
    loading.value = false
  }
}

const refresh = () => {
  loadRecords()
}

/**
 * 用户填写的记录日期（后端 recordDate，DATE 类型，可能早于今天）。
 *
 * 这里曾经只渲染 createTime 的 HH:mm，补录历史数据时看到的就是「今天 15:03」，而健康指标页
 * 显示的是 recordDate —— 同一条记录两处日期不同，用户会以为记录被改成了今天。
 * recordTime（精确时刻）在 schema 里有列但全仓无人写入、恒为 NULL，所以这里只有日期可用，
 * 不拿一个空值去拼「日期 时刻」。
 */
const formatRecordDate = (recordDate: string) => {
  if (!recordDate) return ''
  // 后端返回 yyyy-MM-dd；直接用字符串，避免 new Date('2026-11-19') 被解析成 UTC 午夜后跨日
  return String(recordDate).slice(0, 10)
}

/** 数据写入库的时刻（createTime），与记录日期是两件事：补录时它永远是「今天」。 */
const formatCreateTime = (time: string) => {
  if (!time) return ''
  // 后端 Jackson 输出的是 "yyyy-MM-dd HH:mm:ss"（空格分隔、无时区）。Safari 不认这种写法，
  // 换成 ISO 的 T 分隔后各引擎都按本地时间解析；避免同一列在不同浏览器一半空白一半有值。
  const date = new Date(String(time).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return ''
  // 手工拼固定分隔符：toLocaleString('zh-CN') 给出的是 2026/11/23 15:03，与「记录日期」列的
  // yyyy-MM-dd 不一致，同一张表里出现两种日期写法。
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// 这里曾经有一列「来源」（设备同步/AI识别/OCR识别/手动录入），但 health_metric 表没有
// source 列，HealthMetricVO（vo/HealthMetricVO.java:9-23）与 types/api.ts 的 HealthMetricVO
// 也都没有该字段 —— 每一行都恒为 undefined，于是全都渲染成「手动录入」。保留一列恒错的
// 值比不显示更糟：用户会把 AI/OCR 录入的记录记成手动。拿到真实来源（落库 source 或按
// ai_parse_history / ocr_health_record 的 metric_ids 反查）之前，这一列不再渲染。

onMounted(() => {
  loadRecords()
})

defineExpose({ refresh })
</script>

<style scoped>
.recent-records-card {
  margin-top: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
</style>
