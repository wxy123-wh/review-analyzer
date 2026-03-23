<template>
  <div class="panel">
    <header class="head">
      <h3>词云洞察（{{ aspectLabel }}）</h3>
      <button v-if="canRetry" type="button" class="retry-btn" @click="emit('retry')">重试</button>
    </header>

    <p v-if="state === 'loading'" class="hint">正在加载词云，请稍候...</p>
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

      <div v-if="renderChart" ref="chartContainer" class="chart" />
      <ul v-else class="chip-list">
        <li v-for="item in items" :key="item.keyword" :class="sentimentClass(item.sentimentTag)">
          <strong>{{ item.keyword }}</strong>
          <span>词频 {{ item.frequency }}</span>
        </li>
      </ul>

      <article v-if="topWord" class="summary">
        <h4>当前高频词</h4>
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
  </div>
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
  POSITIVE: '#2d9a62',
  NEGATIVE: '#d45252',
  NEUTRAL: '#4d75ad',
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

.legend {
  margin-top: 10px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #4e6879;
}

.swatch {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.positive {
  background: #2d9a62;
}

.neutral {
  background: #4d75ad;
}

.negative {
  background: #d45252;
}

.chart {
  margin-top: 12px;
  min-height: 220px;
  border-radius: 10px;
  background: linear-gradient(180deg, rgba(232, 246, 255, 0.5), rgba(255, 255, 255, 0.95));
}

.chip-list {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip-list li {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #dce9f2;
  border-radius: 999px;
  padding: 8px 12px;
  background: #f7fbff;
}

.chip-list strong {
  font-size: 14px;
}

.chip-list span {
  font-size: 12px;
  color: #537083;
}

.chip-list .positive {
  border-color: #b8e3cb;
  background: rgba(232, 251, 240, 0.9);
}

.chip-list .neutral {
  border-color: #bfd5ed;
  background: rgba(239, 246, 255, 0.9);
}

.chip-list .negative {
  border-color: #efc7c7;
  background: rgba(255, 242, 242, 0.9);
}

.summary {
  margin-top: 14px;
  border: 1px solid #d5e8f3;
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(243, 251, 255, 0.75);
}

.summary h4 {
  margin: 0;
  font-size: 13px;
  color: #396077;
}

.summary p {
  margin: 8px 0 0;
  display: inline-flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.summary strong {
  font-size: 18px;
  color: #223744;
}

.summary span {
  font-size: 12px;
  color: #567183;
}

.notice {
  margin: 10px 0 0;
  font-size: 12px;
  color: #4f6778;
}

.touch-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: #607785;
}

@media (max-width: 768px) {
  .panel {
    padding: 14px;
  }

  .chip-list strong {
    font-size: 13px;
  }

  .touch-tip {
    font-size: 11px;
  }
}
</style>
