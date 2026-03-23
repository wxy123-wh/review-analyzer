<template>
  <div class="panel">
    <header class="head">
      <h3>趋势图（{{ aspectLabel }}）</h3>
      <button v-if="canRetry" type="button" class="retry-btn" @click="emit('retry')">重试</button>
    </header>

    <p v-if="state === 'loading'" class="hint">正在加载趋势图，请稍候...</p>
    <div v-else-if="chartRenderError" class="state-block">
      <p class="hint error">{{ chartRenderError }}</p>
      <button type="button" class="retry-btn" @click="emit('retry')">重新加载</button>
    </div>
    <div v-else-if="state === 'error' || state === 'timeout'" class="state-block">
      <p class="hint error">{{ stateMessage }}</p>
      <button type="button" class="retry-btn" @click="emit('retry')">重新加载</button>
    </div>
    <div v-else-if="state === 'empty'" class="state-block">
      <p class="hint">{{ stateMessage }}</p>
      <button type="button" class="retry-btn" @click="emit('retry')">刷新数据</button>
    </div>
    <template v-else>
      <div v-if="renderChart" ref="chartContainer" class="chart" />
      <ul v-else class="point-list">
        <li v-for="point in points" :key="point.period">
          <strong>{{ point.period }}</strong>
          <span>负面率 {{ toPercent(point.negativeRate) }}</span>
          <span>提及量 {{ point.mentionVolume }}</span>
        </li>
      </ul>

      <article v-if="selectedPoint" class="point-detail">
        <h4>点位值</h4>
        <div class="detail-grid">
          <p>
            <span>周期</span>
            <strong>{{ selectedPoint.period }}</strong>
          </p>
          <p>
            <span>负面率</span>
            <strong>{{ toPercent(selectedPoint.negativeRate) }}</strong>
          </p>
          <p>
            <span>提及量</span>
            <strong>{{ selectedPoint.mentionVolume }}</strong>
          </p>
        </div>
      </article>

      <p class="touch-tip">触控提示：轻触折线点可查看对应周期的关键值。</p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Line, LineOptions } from '@antv/g2plot/esm/plots/line'

import type { ChartLoadState, TrendPoint } from '../types/domain'

const aspectAlias: Record<string, string> = {
  all: '全部',
  audio: '音质',
  battery: '续航',
  connectivity: '连接',
  comfort: '佩戴',
  call: '通话',
}

type TrendChartDatum = TrendPoint & {
  negativeRatePct: number
}

const isTestMode = import.meta.env.MODE === 'test'

const props = defineProps<{
  aspect: string
  points: TrendPoint[]
  state: ChartLoadState
  message?: string
}>()

const emit = defineEmits<{
  (event: 'retry'): void
}>()

const chartContainer = ref<HTMLDivElement | null>(null)
const selectedPoint = ref<TrendPoint | null>(null)
const chartHeight = ref(320)
const chartRenderError = ref('')

const renderChart = computed(
  () => !isTestMode && props.state === 'success' && props.points.length > 0,
)
const canRetry = computed(
  () => Boolean(chartRenderError.value) || ['empty', 'error', 'timeout'].includes(props.state),
)
const aspectLabel = computed(() => aspectAlias[props.aspect] ?? props.aspect)

const stateMessage = computed(() => {
  if (props.state === 'empty') {
    return props.message || '暂无趋势数据，建议初始化演示评论数据后重试。'
  }
  if (props.state === 'timeout') {
    return props.message || '趋势接口请求超时，请检查网络后重试。'
  }
  if (props.state === 'error') {
    return props.message || '趋势接口请求失败，请稍后重试。'
  }
  return ''
})

const chartData = computed<TrendChartDatum[]>(() =>
  props.points.map((point) => ({
    ...point,
    negativeRatePct: Number((point.negativeRate * 100).toFixed(2)),
  })),
)

let trendChart: Line | null = null
let renderVersion = 0

function syncChartHeight(): void {
  chartHeight.value = window.innerWidth <= 768 ? 240 : 320
}

function toPercent(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}

function resetSelectedPoint(): void {
  selectedPoint.value = props.points.at(-1) ?? null
}

function destroyTrendChart(): void {
  trendChart?.destroy()
  trendChart = null
}

function buildLineOptions(): LineOptions {
  return {
    autoFit: true,
    height: chartHeight.value,
    data: chartData.value,
    xField: 'period',
    yField: 'negativeRatePct',
    smooth: true,
    color: '#1f84af',
    padding: [20, 18, 42, 42],
    point: {
      size: 4,
      shape: 'circle',
      style: {
        fill: '#ffffff',
        stroke: '#1f84af',
        lineWidth: 2,
      },
    },
    xAxis: {
      label: {
        autoHide: true,
        autoRotate: false,
        style: { fill: '#46606f', fontSize: 11 },
      },
      tickLine: null,
    },
    yAxis: {
      min: 0,
      max: 100,
      label: {
        formatter: (value: string) => `${value}%`,
        style: { fill: '#46606f', fontSize: 11 },
      },
      grid: { line: { style: { stroke: '#e2edf4', lineDash: [4, 4] } } },
    },
    label: {
      formatter: (datum: TrendChartDatum) => `${datum.negativeRatePct.toFixed(1)}%`,
      style: {
        fill: '#355263',
        fontSize: 11,
        fontWeight: 600,
      },
      offsetY: -10,
    },
    tooltip: {
      showMarkers: true,
      title: (value: string) => `周期 ${value}`,
      formatter: (datum: TrendChartDatum) => ({
        name: '趋势值',
        value: `负面率 ${datum.negativeRatePct.toFixed(1)}%，提及量 ${datum.mentionVolume}`,
      }),
    },
    interactions: [{ type: 'element-active' }],
    animation: false,
  }
}

async function renderTrendChart(): Promise<void> {
  const currentRenderVersion = ++renderVersion
  if (!renderChart.value || !chartContainer.value) {
    chartRenderError.value = ''
    destroyTrendChart()
    return
  }

  try {
    chartRenderError.value = ''
    const { Line } = await import('@antv/g2plot/esm/plots/line')
    if (currentRenderVersion !== renderVersion || !chartContainer.value || !renderChart.value) {
      return
    }

    destroyTrendChart()
    trendChart = new Line(chartContainer.value, buildLineOptions())
    trendChart.on('element:click', (event: { data?: { data?: TrendChartDatum } }) => {
      const period = event.data?.data?.period
      if (!period) {
        return
      }
      const matched = props.points.find((item) => item.period === period)
      if (matched) {
        selectedPoint.value = matched
      }
    })
    trendChart.render()
  } catch {
    destroyTrendChart()
    chartRenderError.value = '趋势图渲染失败，请刷新后重试。'
  }
}

watch(
  () => [props.points, props.state, chartHeight.value],
  () => {
    resetSelectedPoint()
    void renderTrendChart()
  },
  { deep: true },
)

watch(
  () => props.aspect,
  () => {
    void renderTrendChart()
  },
)

onMounted(() => {
  resetSelectedPoint()
  syncChartHeight()
  window.addEventListener('resize', syncChartHeight, { passive: true })
  void renderTrendChart()
})

onBeforeUnmount(() => {
  renderVersion += 1
  window.removeEventListener('resize', syncChartHeight)
  destroyTrendChart()
})
</script>

<style scoped>
.panel {
  border: 1px solid #c7deea;
  border-radius: 14px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.9);
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

h3 {
  margin: 0;
}

.retry-btn {
  border: 1px solid #81b6d3;
  border-radius: 999px;
  background: #f4faff;
  color: #1b5f81;
  font-size: 12px;
  line-height: 1;
  padding: 8px 12px;
  cursor: pointer;
}

.state-block {
  margin-top: 14px;
  display: grid;
  gap: 10px;
  justify-items: start;
}

.hint {
  margin: 14px 0 0;
  color: #4f6778;
}

.hint.error {
  color: #9b4343;
}

.chart {
  margin-top: 12px;
  min-height: 220px;
  border-radius: 10px;
  background: linear-gradient(180deg, rgba(232, 246, 255, 0.5), rgba(255, 255, 255, 0.95));
}

.point-list {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 10px;
}

li {
  display: grid;
  grid-template-columns: 112px 1fr 1fr;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #dfebf3;
  border-radius: 10px;
}

.point-detail {
  margin-top: 14px;
  border: 1px solid #d5e8f3;
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(243, 251, 255, 0.75);
}

.point-detail h4 {
  margin: 0;
  font-size: 13px;
  color: #396077;
}

.detail-grid {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.detail-grid p {
  margin: 0;
  display: grid;
  gap: 4px;
}

.detail-grid span {
  color: #607785;
  font-size: 12px;
}

.detail-grid strong {
  color: #233744;
  font-size: 14px;
}

.touch-tip {
  margin: 10px 0 0;
  font-size: 12px;
  color: #607785;
}

@media (max-width: 768px) {
  .panel {
    padding: 14px;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .touch-tip {
    font-size: 11px;
  }
}
</style>
