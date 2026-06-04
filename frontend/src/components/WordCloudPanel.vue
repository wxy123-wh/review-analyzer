<template>
  <section class="panel">
    <header class="head">
      <div class="title-block">
        <h3>词云</h3>
        <p class="support">{{ uxLabel }} · {{ items.length }} 个关键词</p>
      </div>
      <button v-if="canRetry" type="button" class="retry-btn" @click="emit('retry')">重试</button>
    </header>

    <div v-if="state === 'loading'" class="state-shell">
      <span class="state-label">加载中</span>
      <p class="hint">正在加载词云，请稍候...</p>
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
      <div class="wordcloud-layout">
        <div class="wordcloud-stage" aria-label="词云图">
          <button
            v-for="word in cloudWords"
            :key="word.keyword"
            type="button"
            class="word-node"
            :class="sentimentClass(word.sentimentTag)"
            :style="wordStyle(word)"
            @click="selectedWord = word"
          >
            {{ word.keyword }}
          </button>
        </div>

        <aside class="word-detail">
          <template v-if="selectedWord">
            <span class="detail-kicker">关键词</span>
            <h4>{{ selectedWord.keyword }}</h4>
            <dl>
              <div>
                <dt>词频</dt>
                <dd>{{ selectedWord.frequency }}</dd>
              </div>
              <div>
                <dt>情绪</dt>
                <dd :class="sentimentClass(selectedWord.sentimentTag)">{{ sentimentLabel(selectedWord.sentimentTag) }}</dd>
              </div>
              <div>
                <dt>词性</dt>
                <dd>{{ selectedWord.partOfSpeech || '未标注' }}</dd>
              </div>
              <div>
                <dt>类型</dt>
                <dd>{{ selectedWord.wordType || '商品属性' }}</dd>
              </div>
            </dl>
          </template>
        </aside>
      </div>

      <ul class="rank-list" aria-label="关键词排行">
        <li v-for="item in topItems" :key="item.keyword" :class="{ active: selectedWord?.keyword === item.keyword }">
          <button type="button" @click="selectedWord = item">
            <strong>{{ item.keyword }}</strong>
            <span>{{ item.frequency }}</span>
            <small :class="sentimentClass(item.sentimentTag)">{{ sentimentLabel(item.sentimentTag) }}</small>
          </button>
        </li>
      </ul>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

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

const cloudSlots = [
  { x: 48, y: 45 },
  { x: 28, y: 35 },
  { x: 67, y: 35 },
  { x: 36, y: 62 },
  { x: 60, y: 64 },
  { x: 18, y: 55 },
  { x: 78, y: 55 },
  { x: 50, y: 24 },
  { x: 24, y: 72 },
  { x: 74, y: 73 },
  { x: 13, y: 26 },
  { x: 86, y: 27 },
  { x: 43, y: 80 },
  { x: 61, y: 16 },
]

type CloudWord = WordCloudItem & {
  x: number
  y: number
  size: number
}

const props = defineProps<{
  aspect: string
  uxSecondaryLabel?: string
  items: WordCloudItem[]
  state: ChartLoadState
  message?: string
  notice?: string
}>()

const emit = defineEmits<{
  (event: 'retry'): void
}>()

const selectedWord = ref<WordCloudItem | null>(null)

const canRetry = computed(() => ['empty', 'degraded', 'error', 'timeout', 'runtime-unavailable'].includes(props.state))
const aspectLabel = computed(() => aspectAlias[props.aspect] ?? props.aspect)
const uxLabel = computed(() => props.uxSecondaryLabel?.trim() || aspectLabel.value)
const topItems = computed(() => props.items.slice(0, 8))

const stateMessage = computed(() => {
  if (props.state === 'empty') {
    return props.message || '暂无词云数据，请先完成评论分析。'
  }
  if (props.state === 'degraded') {
    return props.message || '词云数据暂时只返回部分结果。'
  }
  if (props.state === 'timeout') {
    return props.message || '词云接口请求超时，请检查网络后重试。'
  }
  if (props.state === 'runtime-unavailable') {
    return props.message || '词云运行态暂不可用，请稍后重试。'
  }
  if (props.state === 'disabled') {
    return props.message || '词云模块当前已停用。'
  }
  if (props.state === 'error') {
    return props.message || '词云接口请求失败，请稍后重试。'
  }
  return ''
})

const cloudWords = computed<CloudWord[]>(() => {
  const values = props.items.slice(0, cloudSlots.length)
  const maxWeight = Math.max(...values.map((item) => Number(item.weight) || item.frequency || 1), 1)
  const minWeight = Math.min(...values.map((item) => Number(item.weight) || item.frequency || 1), maxWeight)
  const range = Math.max(maxWeight - minWeight, 1)
  return values.map((item, index) => {
    const slot = cloudSlots[index % cloudSlots.length]
    const weight = Number(item.weight) || item.frequency || 1
    return {
      ...item,
      x: slot.x,
      y: slot.y,
      size: 16 + ((weight - minWeight) / range) * 28,
    }
  })
})

function normalizeSentiment(sentimentTag: string): string {
  const normalized = sentimentTag.trim().toUpperCase()
  if (normalized === 'POSITIVE' || normalized === '正向') {
    return 'POSITIVE'
  }
  if (normalized === 'NEGATIVE' || normalized === '负向') {
    return 'NEGATIVE'
  }
  if (normalized === 'NEUTRAL' || normalized === '中性') {
    return 'NEUTRAL'
  }
  return 'NEUTRAL'
}

function sentimentLabel(sentimentTag: string): string {
  return sentimentText[normalizeSentiment(sentimentTag)] ?? '中性'
}

function sentimentClass(sentimentTag: string): string {
  return normalizeSentiment(sentimentTag).toLowerCase()
}

function wordStyle(word: CloudWord): Record<string, string> {
  return {
    left: `${word.x}%`,
    top: `${word.y}%`,
    fontSize: `${word.size}px`,
  }
}

watch(
  () => [props.items, props.state],
  () => {
    selectedWord.value = props.items[0] ?? null
  },
  { deep: true, immediate: true },
)
</script>

<style scoped>
.panel {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: var(--space-3);
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-lg);
  padding: var(--space-3);
  background: var(--gradient-surface);
  box-shadow: var(--shadow-raised);
}

.head,
.state-shell,
.wordcloud-layout,
.rank-list {
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

h3,
.word-detail h4 {
  margin: 0;
  color: var(--color-text-primary);
}

h3 {
  font-size: var(--font-size-xl);
  line-height: var(--line-height-tight);
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
}

.state-shell,
.wordcloud-stage,
.word-detail {
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
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.hint.error {
  color: var(--color-semantic-down);
}

.wordcloud-layout {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(13rem, 0.32fr);
  gap: var(--space-3);
}

.wordcloud-stage {
  position: relative;
  min-height: 260px;
  height: min(38vh, 330px);
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 48%, color-mix(in srgb, var(--color-accent-soft) 80%, transparent), transparent 58%),
    var(--color-surface-1);
}

.word-node {
  position: absolute;
  transform: translate(-50%, -50%);
  max-width: 9.5rem;
  border: 0;
  background: transparent;
  color: var(--color-text-primary);
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  text-wrap: nowrap;
}

.word-node.positive {
  color: var(--color-semantic-up);
}

.word-node.negative {
  color: var(--color-semantic-down);
}

.word-node.neutral {
  color: var(--color-accent-primary);
}

.word-node:focus-visible {
  outline: var(--outline-focus);
  outline-offset: 4px;
}

.word-detail {
  display: grid;
  align-content: start;
  gap: var(--space-3);
  padding: var(--space-3);
}

.word-detail h4 {
  font-size: var(--font-size-2xl);
  line-height: var(--line-height-tight);
}

.word-detail dl {
  display: grid;
  gap: var(--space-2);
  margin: 0;
}

.word-detail div {
  display: grid;
  gap: var(--space-1);
}

.word-detail dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.word-detail dd {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.positive {
  color: var(--color-semantic-up);
}

.negative {
  color: var(--color-semantic-down);
}

.neutral {
  color: var(--color-accent-primary);
}

.rank-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
}

.rank-list button {
  width: 100%;
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 2px var(--space-2);
  align-items: center;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-sm);
  padding: var(--space-2);
  background: var(--color-surface-1);
  text-align: left;
  cursor: pointer;
}

.rank-list li.active button {
  border-color: var(--color-accent-primary);
  background: var(--color-accent-soft);
}

.rank-list strong {
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-list span {
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  font-weight: 800;
}

.rank-list small {
  grid-column: 1 / -1;
  font-size: var(--font-size-xs);
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
  }

  .wordcloud-layout {
    grid-template-columns: 1fr;
  }

  .rank-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
