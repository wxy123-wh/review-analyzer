<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ackAlert, fetchAlerts } from '../api/alerts'
import { fetchDashboardOverview } from '../api/dashboard'
import { fetchEvents } from '../api/events'
import { fetchBeforeAfter } from '../api/evaluate'
import EChart from '../components/EChart.vue'
import { useGlobalFilters } from '../stores/globalFilters'
import { useMobileDetect } from '../composables/useMobileDetect'
import { mergeMobileConfig, getLineMobileConfig } from '../utils/chartMobileConfig'

const router = useRouter()
const { productId, start, end } = useGlobalFilters()
const { isMobile } = useMobileDetect()

const loading = ref(false)
const overview = ref(null)

const alertsLoading = ref(false)
const alerts = ref([])

const events = ref([])
const eventId = ref(null)
const beforeAfter = ref(null)
const loadingBeforeAfter = ref(false)

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

async function loadOverview() {
  if (!productId.value) {
    overview.value = null
    return
  }
  loading.value = true
  try {
    overview.value = await fetchDashboardOverview({ productId: productId.value, start: start.value, end: end.value })
  } catch (e) {
    ElMessage.error(e?.message || '加载趋势失败')
    overview.value = null
  } finally {
    loading.value = false
  }
}

async function loadAlerts() {
  if (!productId.value) {
    alerts.value = []
    return
  }
  alertsLoading.value = true
  try {
    const res = await fetchAlerts({ productId: productId.value, status: 'new' })
    alerts.value = res?.items || []
  } catch (e) {
    alerts.value = []
  } finally {
    alertsLoading.value = false
  }
}

async function onAck(row) {
  if (!row?.id) return
  alertsLoading.value = true
  try {
    await ackAlert(row.id)
    await loadAlerts()
  } catch (e) {
    ElMessage.error(e?.message || '确认失败')
  } finally {
    alertsLoading.value = false
  }
}

async function loadEventsList() {
  if (!productId.value) {
    events.value = []
    eventId.value = null
    return
  }
  try {
    events.value = (await fetchEvents({ productId: productId.value })) || []
    if (!eventId.value && events.value.length > 0) {
      eventId.value = events.value[0].id
    }
  } catch (e) {
    // ignore
  }
}

async function loadBeforeAfter() {
  if (!eventId.value) {
    beforeAfter.value = null
    return
  }
  loadingBeforeAfter.value = true
  try {
    beforeAfter.value = await fetchBeforeAfter({ eventId: eventId.value })
  } catch (e) {
    beforeAfter.value = null
  } finally {
    loadingBeforeAfter.value = false
  }
}

watch([productId, start, end], loadOverview, { immediate: true })
watch(productId, loadAlerts, { immediate: true })
watch(productId, loadEventsList, { immediate: true })
watch(eventId, loadBeforeAfter, { immediate: true })

const trendOption = computed(() => {
  const trend = overview.value?.trend || []
  const baseOption = {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: trend.map((t) => t.date) },
    yAxis: {
      type: 'value',
      min: 0,
      max: 1,
      axisLabel: { formatter: (v) => `${Math.round(v * 100)}%` },
    },
    series: [
      {
        name: '负向率',
        type: 'line',
        smooth: true,
        data: trend.map((t) => t.negRate),
        symbolSize: 6,
        lineStyle: { width: 3 },
      },
    ],
  }

  if (isMobile.value) {
    return mergeMobileConfig(baseOption, getLineMobileConfig())
  }
  return baseOption
})

function openLink(path) {
  router.push(path)
}

function fmtMetric(v) {
  const m = String(v || '').trim()
  if (m === 'negRate') return '负向占比'
  return m || '-'
}
</script>

<template>
  <div class="dashboard-ops-container">
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <div class="headerLinks">
        <el-button @click="openLink('/overview')">总览</el-button>
        <el-button type="primary" @click="openLink('/alerts')">预警</el-button>
        <el-button @click="openLink('/events')">创建活动</el-button>
        <el-button @click="openLink('/before-after')">前后对比</el-button>
      </div>

      <el-row :gutter="12">
        <el-col :xs="24" :lg="14">
          <el-card shadow="never" v-loading="loading">
            <template #header><div class="card__title">负向趋势</div></template>
            <EChart :option="trendOption" height="320px" />
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="10">
          <el-card shadow="never" v-loading="alertsLoading">
            <template #header><div class="card__title">预警（未确认）</div></template>
            
            <!-- 移动端视图 -->
            <div v-if="isMobile" class="mobile-list">
              <el-empty v-if="alerts.length === 0" description="暂无预警" />
              <div v-for="row in alerts" :key="row.id" class="mobile-card">
                <div class="mobile-card__header">
                  <span class="alert-id">#{{ row.id }}</span>
                  <span class="mobile-card__metric neg">{{ fmtMetric(row.metric) }}</span>
                </div>
                <div class="mobile-card__body">
                  <div class="mobile-card__row">
                    <span class="label">当前值:</span>
                    <span class="value link">{{ fmtRate(row.currentValue) }}</span>
                  </div>
                  <div class="mobile-card__row">
                    <span class="label">时间窗口:</span>
                    <span class="value">{{ row.windowStart }} ~ {{ row.windowEnd }}</span>
                  </div>
                </div>
                <div class="mobile-card__footer">
                   <el-button size="small" type="primary" @click="onAck(row)" style="width: 100%">确认预警</el-button>
                </div>
              </div>
            </div>

            <!-- 桌面端视图 -->
            <el-table v-else :data="alerts" style="width: 100%">
              <el-table-column prop="id" label="编号" width="90" />
              <el-table-column prop="metric" label="指标" width="110">
                <template #default="{ row }">{{ fmtMetric(row.metric) }}</template>
              </el-table-column>
              <el-table-column prop="windowStart" label="窗口开始" width="120" />
              <el-table-column prop="windowEnd" label="窗口结束" width="120" />
              <el-table-column label="当前值" width="110">
                <template #default="{ row }">{{ fmtRate(row.currentValue) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="110" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" @click="onAck(row)">确认</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="card--mt" v-loading="loadingBeforeAfter">
            <template #header><div class="card__title">前后对比（摘要）</div></template>
            <div class="toolbar">
              <span class="label">事件</span>
              <el-select v-model="eventId" filterable clearable placeholder="请选择事件" style="width: 240px">
                <el-option
                  v-for="e in events"
                  :key="e.id"
                  :label="`${e.name} · ${e.startDate}~${e.endDate}`"
                  :value="e.id"
                />
              </el-select>
              <el-button type="primary" :disabled="!eventId" @click="openLink(`/before-after?eventId=${eventId}`)">详情</el-button>
            </div>
            <el-empty v-if="!eventId" description="请先创建并选择一个事件" />
            <div v-else class="ba">
              <div class="ba__row">变更前负向占比：<b>{{ fmtRate(beforeAfter?.before?.negRate) }}</b></div>
              <div class="ba__row">变更后负向占比：<b>{{ fmtRate(beforeAfter?.after?.negRate) }}</b></div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.headerLinks {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.card__title {
  font-weight: 600;
}
.card--mt {
  margin-top: 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.ba__row {
  line-height: 1.9;
}

/* 移动端卡片样式 */
.mobile-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mobile-card {
  background: var(--neu-bg-light);
  border-radius: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
}
.mobile-card:active {
  background: var(--neu-bg-base);
}
.mobile-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.alert-id {
  font-weight: 500;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.mobile-card__metric {
  font-weight: 600;
  font-size: 14px;
}
.mobile-card__metric.neg {
  color: var(--el-color-danger);
}
.mobile-card__body {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 12px;
}
.mobile-card__row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}
.mobile-card__footer {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 12px;
}
.value.link {
  color: var(--el-color-primary);
  font-weight: 600;
}

@media (max-width: 768px) {
  /* 为整个页面添加顶部间距，避免内容被sticky header遮挡 */
  .dashboard-ops-container {
    padding-top: 12px;
  }
  
  /* 增强按钮行的视觉效果，提高可见性 */
  .headerLinks {
    background: var(--neu-bg-card);
    padding: 10px;
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    margin-bottom: 16px;
    flex-wrap: nowrap;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
  .headerLinks .el-button {
    flex-shrink: 0;
  }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .toolbar .el-select {
    width: 100% !important;
  }
  .toolbar .el-button {
    width: 100%;
  }
  .label {
    margin-bottom: 4px;
  }
}
</style>
