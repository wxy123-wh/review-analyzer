<template>
  <section class="panel">
    <header class="head">
      <div class="title-block">
        <h3>趋势图</h3>
        <p class="support">{{ supportText }}</p>
      </div>
      <button v-if="canRetry" type="button" class="retry-btn" @click="emit('retry')">重试</button>
    </header>

    <div v-if="state === 'loading'" class="state-shell">
      <span class="state-label">加载中</span>
      <p class="hint">正在加载趋势图，请稍候...</p>
    </div>
    <div v-else-if="state === 'degraded'" class="state-shell">
      <span class="state-label">部分数据</span>
      <p class="hint">{{ stateMessage }}</p>
      <button type="button" class="retry-btn" @click="emit('retry')">重新加载</button>
    </div>
    <div v-else-if="state === 'error' || state === 'timeout' || state === 'runtime-unavailable'" class="state-shell state-shell--error">
      <span class="state-label">接口状态</span>
      <p class="hint error">{{ stateMessage }}</p>
      <button type="button" class="retry-btn" @click="emit('retry')">重新加载</button>
    </div>
    <div v-else-if="state === 'empty' || state === 'disabled'" class="state-shell">
      <span class="state-label">{{ state === 'disabled' ? '模块已停用' : '暂无数据' }}</span>
      <p class="hint">{{ stateMessage }}</p>
      <button type="button" class="retry-btn" @click="emit('retry')">刷新数据</button>
    </div>
    <template v-else>
      <div class="series-legend" aria-label="趋势维度">
        <button
          v-for="item in series"
          :key="item.id"
          type="button"
          class="legend-item"
          :class="{ active: activeSeries?.id === item.id, muted: item.points.length === 0 }"
          :aria-pressed="activeSeries?.id === item.id"
          :style="{ '--series-color': item.color }"
          @click="selectSeries(item.id)"
        >
          <span class="legend-swatch" aria-hidden="true"></span>
          <span class="legend-copy">
            <strong>{{ item.uxSecondaryLabel }}</strong>
            <small>{{ latestSummary(item) }}</small>
          </span>
        </button>
      </div>

      <div class="trend-layout">
        <div class="chart-card" aria-label="多维负面率趋势图">
          <svg class="trend-svg" viewBox="0 0 720 300" role="img">
            <title>多维负面率趋势</title>
            <g class="grid-lines">
              <line
                v-for="tick in yTicks"
                :key="tick"
                :x1="chartPadding.left"
                :x2="chartWidth - chartPadding.right"
                :y1="yFor(tick / 100)"
                :y2="yFor(tick / 100)"
              />
              <text
                v-for="tick in yTicks"
                :key="`label-${tick}`"
                :x="chartPadding.left - 12"
                :y="yFor(tick / 100) + 4"
                text-anchor="end"
              >
                {{ tick }}%
              </text>
            </g>
            <g class="x-axis">
              <g v-for="(period, index) in allPeriods" :key="period">
                <line
                  class="x-guide"
                  :x1="xForPeriod(period)"
                  :x2="xForPeriod(period)"
                  :y1="chartHeight - chartPadding.bottom"
                  :y2="chartHeight - chartPadding.bottom + 6"
                />
                <text
                  v-if="index === 0 || index === allPeriods.length - 1 || allPeriods.length <= 5"
                  class="x-label"
                  :x="xForPeriod(period)"
                  :y="chartHeight - 14"
                  text-anchor="middle"
                >
                  {{ period }}
                </text>
              </g>
            </g>
            <path
              v-for="line in chartLines"
              :key="`line-${line.series.id}`"
              class="line-path"
              :class="{ active: activeSeries?.id === line.series.id, muted: activeSeries?.id !== line.series.id }"
              :d="line.path"
              :stroke="line.series.color"
              @click="selectSeries(line.series.id)"
            />
            <g v-for="line in chartLines" :key="`points-${line.series.id}`">
              <g v-for="point in line.points" :key="`${line.series.id}-${point.period}`">
                <circle
                  class="point-hit"
                  :cx="point.x"
                  :cy="point.y"
                  r="16"
                  @click="selectPoint(line.series.id, point.raw)"
                />
                <circle
                  class="point-dot"
                  :class="{ active: activeSeries?.id === line.series.id && selectedPoint?.period === point.period }"
                  :cx="point.x"
                  :cy="point.y"
                  r="5"
                  :stroke="line.series.color"
                  :fill="activeSeries?.id === line.series.id && selectedPoint?.period === point.period ? line.series.color : '#ffffff'"
                  @click="selectPoint(line.series.id, point.raw)"
                />
              </g>
            </g>
          </svg>
        </div>

        <article v-if="activeSeries && selectedPoint" class="point-detail">
          <span class="detail-kicker">当前维度</span>
          <h4>{{ activeSeries.uxSecondaryLabel }}</h4>
          <div class="detail-grid">
            <p>
              <span>当前周期</span>
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
            <p>
              <span>较上一期</span>
              <strong :class="deltaClass">{{ deltaText }}</strong>
            </p>
          </div>
        </article>
      </div>

      <ul class="point-list" aria-label="当前维度趋势数据">
        <li v-for="point in compactPoints" :key="point.period" :class="{ active: selectedPoint?.period === point.period }">
          <button type="button" @click="selectPoint(activeSeries?.id ?? '', point)">
            <strong>{{ point.period }}</strong>
            <span>{{ toPercent(point.negativeRate) }}</span>
            <small>{{ point.mentionVolume }} 条</small>
          </button>
        </li>
      </ul>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { ChartLoadState, TrendPoint, TrendSeries } from '../types/domain'

type TrendChartDatum = TrendPoint & {
  raw: TrendPoint
  x: number
  y: number
}

type TrendChartLine = {
  series: TrendSeries
  path: string
  points: TrendChartDatum[]
}

const props = defineProps<{
  series: TrendSeries[]
  state: ChartLoadState
  message?: string
}>()

const emit = defineEmits<{
  (event: 'retry'): void
}>()

const activeSeriesId = ref('')
const selectedPoint = ref<TrendPoint | null>(null)
const chartWidth = 720
const chartHeight = 300
const chartPadding = {
  top: 24,
  right: 24,
  bottom: 44,
  left: 52,
}
const yTicks = [0, 25, 50, 75, 100]

const canRetry = computed(() => ['empty', 'degraded', 'error', 'timeout', 'runtime-unavailable'].includes(props.state))
const seriesWithData = computed(() => props.series.filter((item) => item.points.length > 0))
const allPeriods = computed(() =>
  Array.from(new Set(seriesWithData.value.flatMap((item) => item.points.map((point) => point.period)))).sort(),
)
const activeSeries = computed(
  () =>
    props.series.find((item) => item.id === activeSeriesId.value) ??
    seriesWithData.value[0] ??
    props.series[0] ??
    null,
)
const activePoints = computed(() => sortPoints(activeSeries.value?.points ?? []))
const compactPoints = computed(() => activePoints.value.slice(-6))
const supportText = computed(() => `${seriesWithData.value.length}/${props.series.length} 个维度 · ${allPeriods.value.length} 个周期`)

const stateMessage = computed(() => {
  if (props.state === 'empty') {
    return props.message || '暂无趋势数据，请先完成评论分析。'
  }
  if (props.state === 'degraded') {
    return props.message || '趋势数据暂时只返回部分时间窗。'
  }
  if (props.state === 'timeout') {
    return props.message || '趋势接口请求超时，请检查网络后重试。'
  }
  if (props.state === 'runtime-unavailable') {
    return props.message || '趋势运行态暂不可用，请稍后重试。'
  }
  if (props.state === 'disabled') {
    return props.message || '趋势模块当前已停用。'
  }
  if (props.state === 'error') {
    return props.message || '趋势接口请求失败，请稍后重试。'
  }
  return ''
})

const chartInnerWidth = computed(() => chartWidth - chartPadding.left - chartPadding.right)
const chartInnerHeight = computed(() => chartHeight - chartPadding.top - chartPadding.bottom)

const chartLines = computed<TrendChartLine[]>(() =>
  seriesWithData.value
    .map((item) => {
      const points = sortPoints(item.points).map((point) => ({
        ...point,
        raw: point,
        x: xForPeriod(point.period),
        y: yFor(point.negativeRate),
      }))
      return {
        series: item,
        points,
        path: points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' '),
      }
    })
    .filter((line) => line.path),
)

const selectedIndex = computed(() => activePoints.value.findIndex((point) => point.period === selectedPoint.value?.period))
const previousPoint = computed(() => {
  if (selectedIndex.value <= 0) {
    return null
  }
  return activePoints.value[selectedIndex.value - 1]
})

const deltaValue = computed(() => {
  if (!selectedPoint.value || !previousPoint.value) {
    return 0
  }
  return selectedPoint.value.negativeRate - previousPoint.value.negativeRate
})

const deltaText = computed(() => {
  if (!previousPoint.value) {
    return '-'
  }
  const sign = deltaValue.value > 0 ? '+' : ''
  return `${sign}${(deltaValue.value * 100).toFixed(1)}%`
})

const deltaClass = computed(() => {
  if (!previousPoint.value || deltaValue.value === 0) {
    return 'neutral'
  }
  return deltaValue.value > 0 ? 'up' : 'down'
})

function sortPoints(points: TrendPoint[]): TrendPoint[] {
  return [...points].sort((left, right) => left.period.localeCompare(right.period))
}

function latestSummary(item: TrendSeries): string {
  const latest = sortPoints(item.points).at(-1)
  return latest ? `${latest.period} · ${toPercent(latest.negativeRate)}` : '暂无数据'
}

function toPercent(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}

function xForPeriod(period: string): number {
  const periods = allPeriods.value
  if (periods.length <= 1) {
    return chartPadding.left + chartInnerWidth.value / 2
  }
  const index = Math.max(0, periods.indexOf(period))
  return chartPadding.left + (chartInnerWidth.value * index) / (periods.length - 1)
}

function yFor(value: number): number {
  const normalized = Math.max(0, Math.min(1, value))
  return chartPadding.top + (1 - normalized) * chartInnerHeight.value
}

function selectSeries(seriesId: string): void {
  const target = props.series.find((item) => item.id === seriesId)
  if (!target) {
    return
  }
  activeSeriesId.value = target.id
  selectedPoint.value = sortPoints(target.points).at(-1) ?? null
}

function selectPoint(seriesId: string, point: TrendPoint): void {
  if (seriesId) {
    activeSeriesId.value = seriesId
  }
  selectedPoint.value = point
}

function resetSelectedPoint(): void {
  const target = seriesWithData.value[0] ?? props.series[0]
  activeSeriesId.value = target?.id ?? ''
  selectedPoint.value = target ? sortPoints(target.points).at(-1) ?? null : null
}

watch(
  () => [props.series, props.state],
  () => {
    resetSelectedPoint()
  },
  { deep: true, immediate: true },
)
</script>

<style scoped>
.panel {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  gap: var(--space-3);
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-lg);
  padding: var(--space-3);
  background: var(--gradient-surface);
  box-shadow: var(--shadow-raised);
}

.head,
.state-shell,
.series-legend,
.trend-layout,
.point-list,
.point-detail {
  position: relative;
  z-index: var(--z-raised);
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.title-block {
  display: grid;
  gap: var(--space-1);
}

.state-label,
.detail-kicker {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 1.5rem;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border-default);
  background: var(--color-surface-overlay);
  box-shadow: var(--shadow-inset-soft);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

h3,
.point-detail h4 {
  margin: 0;
  color: var(--color-text-primary);
}

h3 {
  font-size: var(--font-size-xl);
  line-height: var(--line-height-tight);
}

.point-detail h4 {
  font-size: var(--font-size-lg);
  line-height: var(--line-height-snug);
}

.support,
.hint {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.retry-btn {
  flex-shrink: 0;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  background: var(--color-surface-1);
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

.retry-btn:focus-visible,
.legend-item:focus-visible,
.point-list button:focus-visible {
  outline: none;
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-focus), var(--shadow-glow);
}

.state-shell,
.chart-card,
.point-detail {
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: var(--color-surface-1);
  box-shadow: var(--shadow-inset-soft);
}

.state-shell {
  display: grid;
  gap: var(--space-3);
  justify-items: start;
  padding: var(--space-4);
}

.state-shell--error {
  border-color: #fecaca;
  background: var(--color-semantic-down-soft);
}

.hint.error {
  color: var(--color-semantic-down);
}

.series-legend {
  display: flex;
  gap: var(--space-2);
  overflow-x: auto;
  padding-bottom: var(--space-1);
  scrollbar-width: thin;
}

.legend-item {
  --series-color: var(--color-accent-primary);
  flex: 0 0 12rem;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: var(--color-surface-1);
  color: var(--color-text-secondary);
  padding: var(--space-2);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--motion-medium) var(--easing-standard),
    background-color var(--motion-medium) var(--easing-standard),
    opacity var(--motion-medium) var(--easing-standard);
}

.legend-item.active {
  border-color: var(--series-color);
  background: color-mix(in srgb, var(--series-color) 10%, var(--color-surface-1));
  color: var(--color-text-primary);
}

.legend-item.muted {
  opacity: 0.62;
}

.legend-swatch {
  width: 0.75rem;
  height: 0.75rem;
  flex: 0 0 auto;
  border-radius: var(--radius-pill);
  background: var(--series-color);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--series-color) 18%, transparent);
}

.legend-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.legend-copy strong,
.legend-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.legend-copy strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
}

.legend-copy small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.trend-layout {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(13rem, 0.32fr);
  gap: var(--space-3);
}

.chart-card {
  min-height: 0;
  padding: var(--space-2);
  overflow: hidden;
}

.trend-svg {
  display: block;
  width: 100%;
  height: min(38vh, 320px);
  min-height: 220px;
}

.grid-lines line {
  stroke: var(--color-border-default);
  stroke-width: 1;
}

.grid-lines text,
.x-label {
  fill: var(--color-text-muted);
  font-size: 12px;
}

.x-guide {
  stroke: var(--color-border-default);
  stroke-width: 1;
}

.line-path {
  fill: none;
  stroke-width: 2.4;
  stroke-linecap: round;
  stroke-linejoin: round;
  cursor: pointer;
  opacity: 0.68;
  transition:
    opacity var(--motion-medium) var(--easing-standard),
    stroke-width var(--motion-medium) var(--easing-standard);
}

.line-path.active {
  stroke-width: 4;
  opacity: 1;
}

.line-path.muted {
  opacity: 0.24;
}

.point-hit {
  fill: transparent;
  cursor: pointer;
}

.point-dot {
  stroke-width: 3;
  cursor: pointer;
  opacity: 0.7;
}

.point-dot.active {
  stroke-width: 4;
  opacity: 1;
}

.point-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  gap: var(--space-2);
  min-width: 0;
}

.point-list li {
  flex: 1;
  min-width: 0;
}

.point-list button {
  width: 100%;
  display: grid;
  gap: 2px;
  padding: var(--space-2);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-sm);
  background: var(--color-surface-1);
  color: var(--color-text-secondary);
  text-align: left;
  cursor: pointer;
}

.point-list li.active button {
  border-color: var(--color-accent-primary);
  background: var(--color-accent-soft);
}

.point-list strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.point-list span {
  color: var(--color-accent-primary);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.point-list small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.point-detail {
  display: grid;
  gap: var(--space-3);
  align-content: start;
  padding: var(--space-3);
}

.detail-grid {
  display: grid;
  gap: var(--space-2);
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
  font-size: var(--font-size-xl);
}

.detail-grid strong.up {
  color: var(--color-semantic-down);
}

.detail-grid strong.down {
  color: var(--color-semantic-up);
}

.detail-grid strong.neutral {
  color: var(--color-text-muted);
}

@media (max-width: 768px) {
  .panel {
    padding: var(--space-3);
  }

  .head {
    flex-direction: column;
  }

  .retry-btn {
    width: 100%;
    justify-content: center;
  }

  .legend-item {
    flex-basis: 10.5rem;
  }

  .trend-layout {
    grid-template-columns: 1fr;
  }

  .trend-svg {
    height: 260px;
  }

  .point-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (hover: hover) {
  .retry-btn:hover,
  .legend-item:hover,
  .point-list button:hover {
    border-color: var(--color-accent-primary);
  }
}

@media (prefers-reduced-motion: reduce) {
  .retry-btn,
  .legend-item,
  .line-path {
    transition: none;
  }
}
</style>
