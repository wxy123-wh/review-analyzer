<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchDashboardOverview } from '../api/dashboard'
import EChart from '../components/EChart.vue'
import WordCloudCard from '../components/WordCloudCard.vue'
import { useGlobalFilters } from '../stores/globalFilters'
import { useMobileDetect } from '../composables/useMobileDetect'
import { mergeMobileConfig } from '../utils/chartMobileConfig'

const { isMobile } = useMobileDetect()

const router = useRouter()
const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const overview = ref(null)

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function fmtNum(v) {
  return Number(v || 0).toLocaleString()
}

function fmtPriority(v) {
  const n = Number(v || 0)
  return n.toFixed(4)
}

function fmtLevel(v) {
  const s = String(v || '').toUpperCase()
  if (s === 'ASPECT') return '维度'
  if (s === 'KEYWORD') return '关键词'
  return s || '-'
}

async function load() {
  if (!productId.value) {
    overview.value = null
    return
  }
  loading.value = true
  try {
    overview.value = await fetchDashboardOverview({
      productId: productId.value,
      start: start.value,
      end: end.value,
    })
  } catch (e) {
    ElMessage.error(e?.message || '加载总览失败')
  } finally {
    loading.value = false
  }
}

watch([productId, start, end], load, { immediate: true })

const trendOption = computed(() => {
  const trend = overview.value?.trend || []
  const baseOption = {
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v) => fmtRate(v),
    },
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
  return mergeMobileConfig(baseOption, isMobile.value)
})

const topPriorities = computed(() => overview.value?.topPriorities || [])

function onPriorityRowClick(row) {
  if (!row) return
  if (row.level === 'ASPECT') {
    router.push({ path: '/analysis', query: { aspectId: String(row.aspectId) } })
    return
  }
  if (row.level === 'KEYWORD') {
    router.push({ path: '/reviews', query: { aspectId: String(row.aspectId), keyword: row.name } })
  }
}
</script>

<template>
  <div class="overview-container">
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-row :gutter="12">
        <el-col :xs="24" :sm="12" :md="6">
          <el-card class="kpi" shadow="never" v-loading="loading">
            <div class="kpi__label">评论量</div>
            <div class="kpi__value">{{ fmtNum(overview?.reviewCount) }}</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-card class="kpi" shadow="never" v-loading="loading">
            <div class="kpi__label">负向率</div>
            <div class="kpi__value">{{ fmtRate(overview?.negRate) }}</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-card class="kpi" shadow="never" v-loading="loading">
            <div class="kpi__label">正向率</div>
            <div class="kpi__value">{{ fmtRate(overview?.posRate) }}</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-card class="kpi" shadow="never" v-loading="loading">
            <div class="kpi__label">中性率</div>
            <div class="kpi__value">{{ fmtRate(overview?.neuRate) }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="card" shadow="never" v-loading="loading">
        <template #header>
          <div class="card__title">负向趋势</div>
        </template>
        <EChart :option="trendOption" height="320px" />
      </el-card>

      <el-card class="card" shadow="never" v-loading="loading">
        <template #header>
          <div class="card__title">优先级问题</div>
        </template>
        <div v-if="isMobile" class="mobile-list">
          <div 
            v-for="row in topPriorities" 
            :key="row.name + row.level" 
            class="mobile-card"
            @click="onPriorityRowClick(row)"
          >
            <div class="mobile-card__header">
              <div class="header-left">
                <el-tag size="small" :type="row.level === 'ASPECT' ? 'primary' : 'warning'" effect="plain" class="level-tag">
                  {{ fmtLevel(row.level) }}
                </el-tag>
                <span class="priority-name">{{ row.name }}</span>
              </div>
              <span class="score negative">优先级: {{ fmtPriority(row.priority) }}</span>
            </div>
            
            <div class="mobile-card__body">
              <div class="stat-row">
                <div class="stat-item">
                  <div class="label">负向占比</div>
                  <div class="value neg">{{ fmtRate(row.negRate) }}</div>
                </div>
                <div class="stat-item">
                  <div class="label">增长</div>
                  <div class="value">{{ Number(row.growth || 0).toFixed(3) }}</div>
                </div>
                <div class="stat-item">
                  <div class="label">评论量</div>
                  <div class="value">{{ fmtNum(row.volume) }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <el-table 
          v-else 
          :data="topPriorities" 
          @row-click="onPriorityRowClick" 
          style="width: 100%" 
          :row-class-name="() => 'clickable'"
        >
          <el-table-column prop="level" label="层级" width="110">
            <template #default="{ row }">{{ fmtLevel(row.level) }}</template>
          </el-table-column>
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="priority" label="优先级" width="110">
            <template #default="{ row }">{{ fmtPriority(row.priority) }}</template>
          </el-table-column>
          <el-table-column prop="negRate" label="负向占比" width="110">
            <template #default="{ row }">{{ fmtRate(row.negRate) }}</template>
          </el-table-column>
          <el-table-column prop="growth" label="增长" width="110">
            <template #default="{ row }">{{ Number(row.growth || 0).toFixed(3) }}</template>
          </el-table-column>
          <el-table-column prop="volume" label="评论量" width="110">
            <template #default="{ row }">{{ fmtNum(row.volume) }}</template>
          </el-table-column>
        </el-table>
        <div class="tip">点击行：维度会跳转到「维度分析」，关键词会跳转到「评论」并自动带筛选。</div>
      </el-card>

      <!-- Word Cloud Section -->
      <div class="card">
        <WordCloudCard
          :product-id="productId"
          :date-range="[start, end]"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.kpi {
  margin-bottom: 12px;
}
.kpi__label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.kpi__value {
  font-size: 26px;
  font-weight: 700;
  margin-top: 6px;
}
.card {
  margin-top: 12px;
}
.card__title {
  font-weight: 600;
}
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
:deep(.clickable) {
  cursor: pointer;
}

/* 移动端列表样式 */
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
.mobile-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 6px;
}
.level-tag {
  font-size: 12px;
}
.priority-name {
  font-weight: 600;
  font-size: 14px;
}
.score {
  font-size: 12px;
  font-weight: 600;
}
.score.negative {
  color: var(--el-color-danger);
}
.stat-row {
  display: flex;
  justify-content: space-between;
  background: rgba(0,0,0,0.2);
  padding: 8px 12px;
  border-radius: 6px;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.stat-item .label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.stat-item .value {
  font-size: 14px;
  font-weight: 600;
}
.stat-item .value.neg {
  color: var(--el-color-danger);
}

/* 移动端底部间距 - 防止被底部导航栏遮挡 */
@media (max-width: 767px) {
  .overview-container {
    padding-bottom: 80px; /* 60px导航栏 + 20px额外间距 */
  }
}
</style>
