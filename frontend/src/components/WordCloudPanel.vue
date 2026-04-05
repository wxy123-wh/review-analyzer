<template>
  <section class="panel">
    <header class="head">
      <div class="title-block">
        <span class="eyebrow">Keyword pulse</span>
        <div class="title-copy">
          <h3>词云洞察（{{ aspectLabel }}）</h3>
          <p class="support">保留词云为主视图，说明、情绪图例与补充结论收拢到图外，提升信息分层。</p>
        </div>
      </div>
      <button v-if="canRetry" type="button" class="retry-btn" @click="emit('retry')">重试</button>
    </header>

    <div v-if="state === 'loading'" class="state-shell">
      <span class="state-label">加载中</span>
      <p class="hint">正在加载词云，请稍候...</p>
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
        <p class="support support--chart">颜色仅承担情绪分组，补充说明和高频词摘要保持紧凑，避免盖过词项本身。</p>
        <div class="legend">
          <span class="legend-item">
            <i class="swatch positive"></i>
            正向
          </span>
          <span class="legend-item">
            <i class="swatch neutral"></i>
            中性
          </span>
          <span class="legend-item">
            <i class="swatch negative"></i>
            负向
          </span>
        </div>
      </div>

      <div v-if="renderChart" ref="chartContainer" class="chart" />
      <ul v-else class="chip-list">
        <li v-for="item in items" :key="item.keyword" :class="sentimentClass(item.sentimentTag)">
          <strong>{{ item.keyword }}</strong>
          <span>词频 {{ item.frequency }}</span>
        </li>
      </ul>

      <article v-if="topWord" class="summary">
        <div class="summary-head">
          <span class="summary-kicker">Top term</span>
          <h4>当前高频词</h4>
        </div>
        <p>
          <strong>{{ topWord.keyword }}</strong>
          <span>
            词频 {{ topWord.frequency }} · {{ sentimentLabel(topWord.sentimentTag) }}
          </span>
        </p>
      </article>

      <p v-if="notice" class="notice">{{ notice }}</p>
      <p class="touch-tip">触控提示：轻触词项可查看关键词与词频详情。</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { WordCloud, WordCloudOptions } from '@antv/g2plot/esm/plots/word-cloud'

import type { ChartLoadState, WordCloudItem } from '../types/domain'

const aspectAlias: Record<string, string> = {
  all: '全部',
  audio: '音质',
  battery: '续航',
  connectivity: '连接',
  comfort: '佩戴',
  call: '通话',
}

const sentimentText: Record<string, string> = {
  POSITIVE: '正向',
  NEGATIVE: '负向',
  NEUTRAL: '中性',
}

const sentimentColor: Record<string, string> = {
  POSITIVE: '#4fd08b',
  NEGATIVE: '#ff7b85',
  NEUTRAL: '#7ab8ff',
}

const isTestMode = import.meta.env.MODE === 'test'

const props = defineProps<{
  aspect: string
  items: WordCloudItem[]
  state: ChartLoadState
  message?: string
  notice?: string
}>()

const emit = defineEmits<{
  (event: 'retry'): void
}>()

const chartContainer = ref<HTMLDivElement | null>(null)
const chartHeight = ref(320)
const chartRenderError = ref('')

const renderChart = computed(() => !isTestMode && props.state === 'success' && props.items.length > 0)
const canRetry = computed(
  () => Boolean(chartRenderError.value) || ['empty', 'error', 'timeout'].includes(props.state),
)
const aspectLabel = computed(() => aspectAlias[props.aspect] ?? props.aspect)
const topWord = computed(() => props.items[0] ?? null)
const notice = computed(() => props.notice?.trim() ?? '')

const stateMessage = computed(() => {
  if (props.state === 'empty') {
    return props.message || '暂无词云数据，建议初始化演示评论数据后重试。'
  }
  if (props.state === 'timeout') {
    return props.message || '词云接口请求超时，请检查网络后重试。'
  }
  if (props.state === 'error') {
    return props.message || '词云接口请求失败，请稍后重试。'
  }
  return ''
})

let wordCloudChart: WordCloud | null = null
let renderVersion = 0

function syncChartHeight(): void {
  chartHeight.value = window.innerWidth <= 768 ? 250 : 320
}

function normalizeSentiment(sentimentTag: string): string {
  const normalized = sentimentTag.trim().toUpperCase()
  if (normalized === 'POSITIVE' || normalized === 'NEGATIVE' || normalized === 'NEUTRAL') {
    return normalized
  }
  return 'NEUTRAL'
}

function sentimentLabel(sentimentTag: string): string {
  return sentimentText[normalizeSentiment(sentimentTag)] ?? '中性'
}

function sentimentClass(sentimentTag: string): string {
  return normalizeSentiment(sentimentTag).toLowerCase()
}

function destroyWordCloudChart(): void {
  wordCloudChart?.destroy()
  wordCloudChart = null
}

function buildWordCloudOptions(): WordCloudOptions {
  return {
    autoFit: true,
    height: chartHeight.value,
    data: props.items.map((item) => ({
      ...item,
      sentimentTag: normalizeSentiment(item.sentimentTag),
    })),
    wordField: 'keyword',
    weightField: 'weight',
    colorField: 'sentimentTag',
    color: (datum: { sentimentTag?: string }) => {
      const sentiment = normalizeSentiment(datum.sentimentTag ?? 'NEUTRAL')
      return sentimentColor[sentiment] ?? sentimentColor.NEUTRAL
    },
    wordStyle: {
      fontFamily: 'Segoe UI, PingFang SC, sans-serif',
      fontWeight: 600,
      rotation: [0, 0],
      fontSize: [14, 42],
      padding: 2,
    },
    tooltip: {
      formatter: (datum: WordCloudItem) => ({
        name: datum.keyword,
        value: `词频 ${datum.frequency} · ${sentimentLabel(datum.sentimentTag)}`,
      }),
    },
    legend: false,
    interactions: [{ type: 'element-active' }],
    animation: false,
  }
}

async function renderWordCloudChart(): Promise<void> {
  const currentRenderVersion = ++renderVersion
  if (!renderChart.value || !chartContainer.value) {
    chartRenderError.value = ''
    destroyWordCloudChart()
    return
  }

  try {
    chartRenderError.value = ''
    const { WordCloud } = await import('@antv/g2plot/esm/plots/word-cloud')
    if (currentRenderVersion !== renderVersion || !chartContainer.value || !renderChart.value) {
      return
    }

    destroyWordCloudChart()
    wordCloudChart = new WordCloud(chartContainer.value, buildWordCloudOptions())
    wordCloudChart.render()
  } catch {
    destroyWordCloudChart()
    chartRenderError.value = '词云渲染失败，请刷新后重试。'
  }
}

watch(
  () => [props.items, props.state, chartHeight.value],
  () => {
    void renderWordCloudChart()
  },
  { deep: true },
)

watch(
  () => props.aspect,
  () => {
    void renderWordCloudChart()
  },
)

onMounted(() => {
  syncChartHeight()
  window.addEventListener('resize', syncChartHeight, { passive: true })
  void renderWordCloudChart()
})

onBeforeUnmount(() => {
  renderVersion += 1
  window.removeEventListener('resize', syncChartHeight)
  destroyWordCloudChart()
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
  background: linear-gradient(135deg, rgba(102, 224, 194, 0.08), transparent 34%);
}

.head,
.state-shell,
.chart-meta,
.chart,
.chip-list,
.summary,
.notice,
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
.title-copy,
.summary-head {
  display: grid;
  gap: var(--space-2);
}

.eyebrow,
.state-label,
.legend-item,
.summary-kicker {
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
.summary-kicker {
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.eyebrow {
  color: var(--color-accent-secondary);
}

h3,
.summary h4 {
  margin: 0;
  color: var(--color-text-primary);
}

h3 {
  font-size: var(--font-size-xl);
  line-height: var(--line-height-tight);
  letter-spacing: -0.02em;
}

.summary h4 {
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
}

.support,
.hint,
.notice,
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

.retry-btn:hover {
  border-color: var(--color-accent-primary);
  box-shadow: var(--shadow-glow);
}

.retry-btn:focus-visible {
  outline: none;
  box-shadow: var(--shadow-focus);
}

.state-shell,
.chart,
.summary,
.notice {
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
  gap: var(--space-2);
  flex-wrap: wrap;
}

.legend-item {
  gap: var(--space-2);
  color: var(--color-text-secondary);
}

.swatch {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: var(--radius-pill);
}

.legend .positive,
.chip-list .positive {
  background: rgba(79, 208, 139, 0.14);
}

.legend .neutral,
.chip-list .neutral {
  background: rgba(122, 184, 255, 0.14);
}

.legend .negative,
.chip-list .negative {
  background: rgba(255, 123, 133, 0.14);
}

.legend .positive {
  box-shadow: 0 0 0 0.2rem rgba(79, 208, 139, 0.14);
}

.legend .neutral {
  box-shadow: 0 0 0 0.2rem rgba(122, 184, 255, 0.14);
}

.legend .negative {
  box-shadow: 0 0 0 0.2rem rgba(255, 123, 133, 0.14);
}

.chart {
  min-height: 220px;
  padding: var(--space-2);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 65%),
    rgba(8, 16, 29, 0.72);
}

.chip-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.chip-list li {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-pill);
  padding: var(--space-2) var(--space-3);
  background: rgba(8, 16, 29, 0.56);
  box-shadow: var(--shadow-inset-soft);
}

.chip-list strong {
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
}

.chip-list span {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.chip-list .positive {
  border-color: rgba(79, 208, 139, 0.24);
  color: var(--color-semantic-up);
}

.chip-list .neutral {
  border-color: rgba(122, 184, 255, 0.24);
  color: var(--color-accent-primary);
}

.chip-list .negative {
  border-color: rgba(255, 123, 133, 0.24);
  color: var(--color-semantic-down);
}

.summary {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
}

.summary-kicker {
  color: var(--color-accent-secondary);
}

.summary p {
  margin: 0;
  display: inline-flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
}

.summary strong {
  font-size: var(--font-size-2xl);
  line-height: var(--line-height-tight);
  color: var(--color-text-primary);
}

.summary span {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.notice {
  padding: var(--space-3);
  border-style: dashed;
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

  .retry-btn {
    width: 100%;
    justify-content: center;
  }

  .chip-list strong {
    font-size: var(--font-size-sm);
  }
}

@media (prefers-reduced-motion: reduce) {
  .retry-btn {
    transition: none;
  }
}
</style>
