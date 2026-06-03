<script setup>
import echarts from '../utils/echarts'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchWordCloud } from '../api/analytics'
import { useWordCloudFilters } from '../composables/useInsightsFilters'

const props = defineProps({
  productId: { type: [String, Number], default: null },
  dateRange: { type: Array, default: null }, // [start, end] with YYYY-MM-DD
  aspectId: { type: [String, Number], default: null },
  sentiment: { type: String, default: 'ALL' }, // POS/NEG/ALL
  aspectName: { type: String, default: '' },
})

const router = useRouter()

const start = computed(() => (Array.isArray(props.dateRange) ? props.dateRange?.[0] : null))
const end = computed(() => (Array.isArray(props.dateRange) ? props.dateRange?.[1] : null))

const { localSentiment, topN, normalizedSentimentOrNull } = useWordCloudFilters(props)

const loading = ref(false)
const items = ref([])
const meta = ref(null)

const isEmpty = computed(() => !items.value || items.value.length === 0)

const chartEl = ref(null)
let chartInstance = null
let resizeObserver = null
let wordCloudImported = false
let disposed = false
let windowResizeHandler = null

function stableColor(str) {
  const s = String(str || '')
  let hash = 0
  for (let i = 0; i < s.length; i += 1) {
    hash = (hash << 5) - hash + s.charCodeAt(i)
    hash |= 0
  }
  const hue = Math.abs(hash) % 360
  return `hsl(${hue}, 65%, 40%)`
}

async function ensureWordCloudPlugin() {
  if (wordCloudImported) return
  await import('echarts-wordcloud')
  wordCloudImported = true
}

function resize() {
  if (!chartInstance) return
  chartInstance.resize()
}

async function ensureChart() {
  if (chartInstance || disposed) return
  await ensureWordCloudPlugin()
  if (!chartEl.value || disposed) return

  chartInstance = echarts.init(chartEl.value)
  chartInstance.on('click', (params) => {
    const word = String(params?.name || '').trim()
    if (!word) return
    const query = { keyword: word }
    if (props.aspectId != null && props.aspectId !== '') query.aspectId = String(props.aspectId)
    if (normalizedSentimentOrNull.value) query.sentiment = normalizedSentimentOrNull.value
    router.push({ path: '/reviews', query })
  })

  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => resize())
    resizeObserver.observe(chartEl.value)
  }

  windowResizeHandler = () => resize()
  window.addEventListener('resize', windowResizeHandler)
}

function clearChart() {
  if (!chartInstance) return
  chartInstance.clear()
}

async function renderChart() {
  await ensureChart()
  if (!chartInstance) return

  if (isEmpty.value) {
    clearChart()
    return
  }

  const data = (items.value || [])
    .map((x) => ({ name: x?.word, value: Number(x?.value || 0) }))
    .filter((x) => x.name && Number.isFinite(x.value) && x.value > 0)

  if (data.length === 0) {
    clearChart()
    return
  }

  chartInstance.setOption(
    {
      tooltip: { formatter: (p) => `${p?.name ?? ''}：${p?.value ?? 0}` },
      series: [
        {
          type: 'wordCloud',
          shape: 'circle',
          left: 'center',
          top: 'center',
          width: '100%',
          height: '100%',
          gridSize: 6,
          sizeRange: [12, 52],
          rotationRange: [-45, 45],
          rotationStep: 45,
          drawOutOfBound: false,
          textStyle: { color: (p) => stableColor(p?.name) },
          emphasis: { focus: 'self', textStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.25)' } },
          data,
        },
      ],
    },
    true,
  )
}

let reqSeq = 0
async function load() {
  const productId = props.productId
  if (!productId) {
    items.value = []
    meta.value = null
    clearChart()
    return
  }

  const seq = (reqSeq += 1)
  loading.value = true
  try {
    const res = await fetchWordCloud({
      productId,
      start: start.value,
      end: end.value,
      aspectId: props.aspectId,
      sentiment: normalizedSentimentOrNull.value,
      topN: topN.value,
    })
    if (seq !== reqSeq) return
    items.value = res?.items || []
    meta.value = res?.meta || null
    await renderChart()
  } catch (e) {
    if (seq !== reqSeq) return
    ElMessage.error(e?.message || '加载词云失败')
    items.value = []
    meta.value = null
    clearChart()
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

watch(() => [props.productId, start.value, end.value, props.aspectId, normalizedSentimentOrNull.value], load, { immediate: true })

onMounted(() => {
  ensureChart().then(() => renderChart())
})

onBeforeUnmount(() => {
  disposed = true
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (windowResizeHandler) {
    window.removeEventListener('resize', windowResizeHandler)
    windowResizeHandler = null
  }
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<template>
  <el-card shadow="never" v-loading="loading" class="card">
    <template #header>
      <div class="card__header">
        <div class="card__title">词云<span v-if="aspectName"> · {{ aspectName }}</span></div>
        <div class="card__controls">
          <el-select v-model="localSentiment" size="small" style="width: 110px">
            <el-option label="全部" value="ALL" />
            <el-option label="正向" value="POS" />
            <el-option label="负向" value="NEG" />
          </el-select>
          <div class="topn">
            <span class="topn__label">数量</span>
            <el-slider
              v-model="topN"
              size="small"
              :min="10"
              :max="200"
              :step="5"
              show-input
              :show-input-controls="false"
              class="topn__slider"
              @change="load"
            />
          </div>
        </div>
      </div>
    </template>

    <div class="chart-wrap">
      <div ref="chartEl" class="chart" />
      <div v-if="!productId" class="overlay">
        <el-empty description="请先选择产品" />
      </div>
      <div v-else-if="!loading && isEmpty" class="overlay">
        <el-empty description="暂无词云数据" />
      </div>
    </div>

    <div v-if="meta && productId" class="meta">
      <span>评论总数：{{ meta.totalReviews ?? 0 }}</span>
      <span>数量：{{ meta.topN ?? topN }}</span>
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
.topn {
  display: flex;
  align-items: center;
  gap: 8px;
}
.topn__label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}
.topn__slider {
  width: 260px;
  min-width: 200px;
}
.chart-wrap {
  position: relative;
  width: 100%;
  height: 340px;
}
.chart {
  width: 100%;
  height: 100%;
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
