<template>
  <div class="alerts-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>健康预警</span>
          <el-button type="primary" :disabled="alertStore.unreadCount === 0" @click="markAllRead">
            全部标记已读
          </el-button>
        </div>
      </template>

      <!-- 统计信息 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ alertStore.unreadCount }}</div>
            <div class="stat-label">未读预警</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ criticalCount }}</div>
            <div class="stat-label">严重预警</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ warningCount }}</div>
            <div class="stat-label">警告预警</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ infoCount }}</div>
            <div class="stat-label">提示预警</div>
          </div>
        </el-col>
      </el-row>

      <!-- 筛选区 -->
      <div class="filter-bar">
        <el-form :inline="true" :model="filters">
          <el-form-item label="预警级别">
            <el-select v-model="filters.alertLevel" placeholder="全部" clearable>
              <el-option label="严重" value="CRITICAL" />
              <el-option label="警告" value="WARNING" />
              <el-option label="提示" value="INFO" />
            </el-select>
          </el-form-item>
          <el-form-item label="预警类型">
            <el-select v-model="filters.alertType" placeholder="全部" clearable>
              <el-option label="阈值预警" value="THRESHOLD" />
              <el-option label="趋势预警" value="TREND" />
              <el-option label="系统预警" value="SYSTEM" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filters.isRead" placeholder="全部" clearable>
              <el-option label="未读" :value="false" />
              <el-option label="已读" :value="true" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="fetchAlerts">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 预警列表 -->
      <el-table v-loading="loading" :data="alerts" stripe>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-badge :is-dot="!row.isRead" type="danger">
              <el-icon v-if="row.isRead" color="#67c23a"><CircleCheck /></el-icon>
              <el-icon v-else color="#f56c6c"><Warning /></el-icon>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column prop="alertLevel" label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="getAlertLevelColor(row.alertLevel)">
              {{ getAlertLevelLabel(row.alertLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="message" label="详情" min-width="300" show-overflow-tooltip />
        <el-table-column prop="metricKey" label="相关指标" width="120">
          <template #default="{ row }">
            {{ row.metricKey ? getMetricLabel(row.metricKey) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.isRead" link type="primary" @click="markRead(row.id)">
              标记已读
            </el-button>
            <el-button v-if="!row.isAcknowledged" link type="success" @click="acknowledge(row.id)">
              确认
            </el-button>
            <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchAlerts"
          @current-change="fetchAlerts"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAlertStore } from '@/stores/alert'
import { alertApi } from '@/api/alert'
import {
  formatDateTime,
  getMetricLabel,
  getAlertLevelColor,
  getAlertLevelLabel
} from '@/utils/format'
import type { AlertVO } from '@/types/api'

const alertStore = useAlertStore()
const loading = ref(false)
const alerts = ref<AlertVO[]>([])

const filters = reactive({
  alertLevel: '',
  alertType: '',
  isRead: undefined as boolean | undefined
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const criticalCount = computed(() => alerts.value.filter(a => a.alertLevel === 'CRITICAL').length)
const warningCount = computed(() => alerts.value.filter(a => a.alertLevel === 'WARNING').length)
const infoCount = computed(() => alerts.value.filter(a => a.alertLevel === 'INFO').length)

const fetchAlerts = async () => {
  loading.value = true
  try {
    const params: any = {
      page: pagination.page,
      size: pagination.size
    }
    if (filters.alertLevel) params.alertLevel = filters.alertLevel
    if (filters.alertType) params.alertType = filters.alertType
    if (filters.isRead !== undefined) params.isRead = filters.isRead

    const res = await alertApi.getAlerts(params)
    alerts.value = res.data.records
    pagination.total = res.data.total
  } catch (error) {
    ElMessage.error('获取预警失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.alertLevel = ''
  filters.alertType = ''
  filters.isRead = undefined
  pagination.page = 1
  fetchAlerts()
}

const markRead = async (id: number) => {
  try {
    await alertApi.markAsRead(id)
    ElMessage.success('已标记为已读')
    fetchAlerts()
    alertStore.fetchUnreadCount()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const acknowledge = async (id: number) => {
  try {
    await alertApi.acknowledgeAlert(id)
    ElMessage.success('已确认')
    fetchAlerts()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const markAllRead = async () => {
  try {
    await ElMessageBox.confirm('确定要将所有未读预警标记为已读吗？', '提示', {
      type: 'warning'
    })
    await alertApi.markAllAsRead()
    ElMessage.success('操作成功')
    fetchAlerts()
    alertStore.fetchUnreadCount()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这条预警吗？', '提示', {
      type: 'warning'
    })
    await alertApi.deleteAlert(id)
    ElMessage.success('删除成功')
    fetchAlerts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchAlerts()
  alertStore.fetchUnreadCount()
})
</script>

<style scoped>
.alerts-page {
  padding: 20px;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-row {
  margin-bottom: 20px;
  padding: 20px 0;
  border-bottom: 1px solid #ebeef5;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.filter-bar {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
