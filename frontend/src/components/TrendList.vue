<template>
  <section class="panel">
    <header class="head">
      <div class="title-block">
        <span class="eyebrow">Trend signal</span>
        <div class="title-copy">
          <h3>趋势图（{{ aspectLabel }}）</h3>
          <p class="support">按周期查看负面率变化，点击点位后在下方锁定当前周期的关键值。</p>
        </div>
      </div>
      <button v-if="canRetry" type="button" class="retry-btn" @click="emit('retry')">重试</button>
    </header>

    <div v-if="state === 'loading'" class="state-shell">
      <span class="state-label">加载中</span>
      <p class="hint">正在加载趋势图，请稍候...</p>
    </div>
    <div v-else-if="chartRenderError" class="state-shell state-shell--error">
      <span class="state-label">渲染异常</span>
      <p class="hint error">{{ chartRenderError }}</p>
      <button type="button" class="retry-btn" @click="emit('retry')">重新加载</button>
    </div>
    <div v-else-if="state === 'error' || state === 'timeout'" class="state-shell state-shell--error">
      <span class="state-label">接口状态</span>
      <p class="hint error">{{ stateMessage }}</p>
      <button type="button" class="retry-btn" @click="emit('retry')">重新加载</button>
    </div>
    <div v-else-if="state === 'empty'" class="state-shell">
      <span class="state-label">暂无数据</span>
      <p class="hint">{{ stateMessage }}</p>
      <button type="button" class="retry-btn" @click="emit('retry')">刷新数据</button>
    </div>
    <template v-else>
      <div class="chart-meta">
        <p class="support support--chart">折线区域保持轻量，说明信息与交互提示集中放在图表外侧，避免与数据争抢视觉重心。</p>
        <div class="legend">
          <span class="legend-item">
            <i class="legend-dot"></i>
            负面率走势
          </span>
          <span class="legend-item">
            <i class="legend-dot legend-dot--muted"></i>
            默认显示最新周期详情
          </span>
        </div>
      </div>

      <div v-if="renderChart" ref="chartContainer" class="chart" />
      <ul v-else class="point-list">
        <li v-for="point in points" :key="point.period">
          <strong>{{ point.period }}</strong>
          <span>负面率 {{ toPercent(point.negativeRate) }}</span>
          <span>提及量 {{ point.mentionVolume }}</span>
        </li>
      </ul>

      <article v-if="selectedPoint" class="point-detail">
        <div class="detail-head">
          <span class="detail-kicker">Selected point</span>
          <h4>点位值</h4>
        </div>
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
  </section>
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
    color: '#7ab8ff',
    padding: [20, 18, 42, 42],
    point: {
      size: 4,
      shape: 'circle',
          style: {
            fill: '#091323',
            stroke: '#7ab8ff',
            lineWidth: 2,
          },
    },
    xAxis: {
      label: {
        autoHide: true,
        autoRotate: false,
        style: { fill: '#b6c4d9', fontSize: 11 },
      },
      tickLine: null,
    },
    yAxis: {
      min: 0,
      max: 100,
      label: {
        formatter: (value: string) => `${value}%`,
        style: { fill: '#b6c4d9', fontSize: 11 },
      },
      grid: { line: { style: { stroke: 'rgba(128, 160, 196, 0.16)', lineDash: [4, 4] } } },
    },
    label: {
      formatter: (datum: TrendChartDatum) => `${datum.negativeRatePct.toFixed(1)}%`,
      style: {
        fill: '#eef4ff',
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
  position: relative;
  overflow: hidden;
  display: grid;
  gap: var(--space-4);
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 58%),
    var(--gradient-surface);
  box-shadow: var(--shadow-raised);
}

.panel::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(135deg, rgba(122, 184, 255, 0.08), transparent 34%);
  opacity: 0.7;
}

.head,
.state-shell,
.chart-meta,
.chart,
.point-list,
.point-detail,
.touch-tip {
  position: relative;
  z-index: var(--z-raised);
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.title-block,
.title-copy {
  display: grid;
  gap: var(--space-2);
}

.eyebrow,
.state-label,
.legend-item,
.detail-kicker {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 1.75rem;
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border-default);
  background: var(--color-surface-overlay);
  box-shadow: var(--shadow-inset-soft);
  font-size: var(--font-size-xs);
}

.eyebrow,
.detail-kicker {
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.eyebrow {
  color: var(--color-accent-primary);
}

h3,
.point-detail h4 {
  margin: 0;
  color: var(--color-text-primary);
}

h3 {
  font-size: var(--font-size-xl);
  line-height: var(--line-height-tight);
  letter-spacing: -0.02em;
}

.point-detail h4 {
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
}

.support,
.hint,
.touch-tip {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.support--chart {
  max-width: 38rem;
}

.retry-btn {
  flex-shrink: 0;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  background: linear-gradient(135deg, rgba(122, 184, 255, 0.16), rgba(102, 224, 194, 0.16));
  color: var(--color-text-primary);
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-sm);
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  box-shadow: var(--shadow-inset-soft);
  transition:
    border-color var(--motion-medium) var(--easing-standard),
    box-shadow var(--motion-medium) var(--easing-standard),
    transform var(--motion-fast) var(--easing-standard);
}

.retry-btn:focus-visible {
  outline: none;
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-focus), var(--shadow-glow);
}

.state-shell,
.chart,
.point-detail {
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 16, 29, 0.56);
  box-shadow: var(--shadow-inset-soft);
}

.state-shell {
  display: grid;
  gap: var(--space-3);
  justify-items: start;
  padding: var(--space-4);
}

.state-shell--error {
  border-color: rgba(255, 123, 133, 0.24);
  background: rgba(38, 12, 20, 0.36);
}

.state-label {
  color: var(--color-text-secondary);
}

.hint.error {
  color: var(--color-semantic-down);
}

.chart-meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.legend-item {
  gap: var(--space-2);
  color: var(--color-text-secondary);
}

.legend-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: var(--radius-pill);
  background: var(--color-accent-primary);
  box-shadow: 0 0 0 0.2rem rgba(122, 184, 255, 0.16);
}

.legend-dot--muted {
  background: var(--color-accent-secondary);
  box-shadow: 0 0 0 0.2rem rgba(102, 224, 194, 0.14);
}

.chart {
  min-height: 220px;
  padding: var(--space-2);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 65%),
    rgba(8, 16, 29, 0.72);
}

.point-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--space-2);
}

.point-list li {
  display: grid;
  grid-template-columns: minmax(6rem, 7.5rem) minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 16, 29, 0.48);
  color: var(--color-text-secondary);
  box-shadow: var(--shadow-inset-soft);
}

.point-list strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
}

.point-detail {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
}

.detail-head {
  display: grid;
  gap: var(--space-2);
}

.detail-kicker {
  color: var(--color-accent-secondary);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
}

.detail-grid p {
  margin: 0;
  display: grid;
  gap: var(--space-1);
}

.detail-grid span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.detail-grid strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.touch-tip {
  padding-left: var(--space-1);
  font-size: var(--font-size-sm);
}

@media (max-width: 768px) {
  .panel {
    padding: var(--space-3);
  }

  .head {
    flex-direction: column;
  }

  .chart-meta {
    gap: var(--space-3);
  }

  .retry-btn {
    width: 100%;
    justify-content: center;
  }

  .chart {
    min-height: 200px;
  }

  .point-list li,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (hover: hover) {
  .retry-btn:hover {
    border-color: var(--color-accent-primary);
    box-shadow: var(--shadow-glow);
  }
}

@media (prefers-reduced-motion: reduce) {
  .retry-btn {
    transition: none;
  }
}

html[data-motion='reduce'] .chart,
html[data-motion='none'] .chart {
  background: rgba(8, 16, 29, 0.72);
}
</style>
