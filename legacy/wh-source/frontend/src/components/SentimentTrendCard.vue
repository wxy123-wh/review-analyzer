<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'

import { fetchSentimentTrend } from '../api/analytics'
import { useSentimentTrendFilters } from '../composables/useInsightsFilters'
import EChart from './EChart.vue'

const props = defineProps({
  productId: { type: [String, Number], default: null },
  dateRange: { type: Array, default: null }, // [start, end] with YYYY-MM-DD
  granularity: { type: String, default: 'day' }, // day/week
})

const start = computed(() => (Array.isArray(props.dateRange) ? props.dateRange?.[0] : null))
const end = computed(() => (Array.isArray(props.dateRange) ? props.dateRange?.[1] : null))

const { localGranularity, mode } = useSentimentTrendFilters(props)

const loading = ref(false)
const points = ref([])
const meta = ref(null)

const isEmpty = computed(() => !points.value || points.value.length === 0)

function fmtPercent(rate) {
  const n = Number(rate || 0)
  if (!Number.isFinite(n)) return '0%'
  return `${(n * 100).toFixed(1)}%`
}

function fmtGranularity(v) {
  const g = String(v || '').toLowerCase()
  if (g === 'day') return '天'
  if (g === 'week') return '周'
  return '-'
}

let reqSeq = 0
async function load() {
  const productId = props.productId
  if (!productId) {
    points.value = []
    meta.value = null
    return
  }

  const seq = (reqSeq += 1)
  loading.value = true
  try {
    const res = await fetchSentimentTrend({
      productId,
      start: start.value,
      end: end.value,
      granularity: localGranularity.value,
    })
    if (seq !== reqSeq) return
    points.value = res?.points || []
    meta.value = res?.meta || null
  } catch (e) {
    if (seq !== reqSeq) return
    ElMessage.error(e?.message || '加载情感趋势失败')
    points.value = []
    meta.value = null
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

watch(() => [props.productId, start.value, end.value, localGranularity.value], load, { immediate: true })

const option = computed(() => {
  const rows = points.value || []
  const dates = rows.map((p) => p.date)
  const isRate = mode.value === 'rate'

  const pointByDate = new Map(rows.map((p) => [p.date, p]))
  const posData = rows.map((p) => (isRate ? Number(p.posRate || 0) : Number(p.pos || 0)))
  const negData = rows.map((p) => (isRate ? Number(p.negRate || 0) : Number(p.neg || 0)))
  const neuData = rows.map((p) => {
    if (!isRate) return Number(p.neu || 0)
    const total = Number(p.total || 0)
    const neu = Number(p.neu || 0)
    return total > 0 ? neu / total : 0
  })

  const valueLabel = (value) => {
    const n = Number(value || 0)
    if (isRate) return fmtPercent(n)
    if (!Number.isFinite(n)) return '0'
    return String(Math.round(n))
  }

  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const items = Array.isArray(params) ? params : [params]
        const date = items?.[0]?.axisValue ?? ''
        const p = pointByDate.get(date)
        const lines = [String(date)]
        items.forEach((it) => {
          lines.push(`${it.marker}${it.seriesName}: ${valueLabel(it.data)}`)
        })
        if (p && Number.isFinite(Number(p.total))) {
          lines.push(`<span style="color:#999">总计：${p.total}</span>`)
        }
        return lines.join('<br/>')
      },
    },
    legend: { data: ['正向', '负向', '中性'], selected: { '中性': false } },
    grid: { left: 40, right: 20, top: 50, bottom: 40 },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: isRate
      ? {
          type: 'value',
          name: '占比',
          min: 0,
          max: 1,
          axisLabel: { formatter: (v) => `${Math.round(Number(v) * 100)}%` },
        }
      : { type: 'value', name: '数量', minInterval: 1 },
    series: [
      {
        name: '正向',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: posData,
        lineStyle: { width: 2, color: '#67C23A' },
        itemStyle: { color: '#67C23A' },
      },
      {
        name: '负向',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: negData,
        lineStyle: { width: 2, color: '#F56C6C' },
        itemStyle: { color: '#F56C6C' },
      },
      {
        name: '中性',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: neuData,
        lineStyle: { width: 2, color: '#909399' },
        itemStyle: { color: '#909399' },
      },
    ],
  }
})
</script>

<template>
  <el-card shadow="never" v-loading="loading" class="card">
    <template #header>
      <div class="card__header">
        <div class="card__title">情感趋势</div>
        <div class="card__controls">
          <el-radio-group v-model="mode" size="small">
            <el-radio-button label="count">数量</el-radio-button>
            <el-radio-button label="rate">占比</el-radio-button>
          </el-radio-group>

          <el-radio-group v-model="localGranularity" size="small">
            <el-radio-button label="day">按天</el-radio-button>
            <el-radio-button label="week">按周</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </template>

    <div class="chart-wrap">
      <EChart v-if="productId && !isEmpty" :option="option" height="100%" />
      <div v-else class="overlay">
        <el-empty v-if="!productId" description="请先选择产品" />
        <el-empty v-else description="暂无趋势数据" />
      </div>
    </div>

    <div v-if="meta && productId" class="meta">
      <span>评论总数：{{ meta.totalReviews ?? 0 }}</span>
      <span>时间粒度：{{ fmtGranularity(meta.granularity ?? localGranularity) }}</span>
    </div>
  </el-card>
</template>

<style scoped>
.card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.card__title {
  font-weight: 600;
}
.card__controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.chart-wrap {
  position: relative;
  width: 100%;
  height: 340px;
}
.overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-bg-color);
}
.meta {
  margin-top: 10px;
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
