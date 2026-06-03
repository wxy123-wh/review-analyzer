<template>
  <section class="panel">
    <header class="head">
      <div class="title-block">
        <span class="eyebrow">Positive VOC</span>
        <h3>正面卖点</h3>
      </div>
    </header>

    <p v-if="state === 'degraded' && stateMessage" class="notice">{{ stateMessage }}</p>

    <div v-if="items.length > 0" class="insight-grid">
      <article v-for="item in items" :key="item.sellingPointId" class="insight-card">
        <div class="card-head">
          <span class="tag">{{ item.uxSecondaryLabel }}</span>
          <span class="score">{{ item.score.toFixed(4) }}</span>
        </div>
        <h4>{{ item.sellingPoint }}</h4>
        <div class="metrics">
          <span>提及 {{ item.mentionCount }}</span>
          <span>正面率 {{ formatPercent(item.positiveRate) }}</span>
          <span>{{ displayUxLabel(item) }}</span>
        </div>
        <ul v-if="item.evidence.length > 0" class="evidence-list">
          <li v-for="evidence in item.evidence" :key="evidence">{{ evidence }}</li>
        </ul>
      </article>
    </div>

    <p v-else class="empty" :class="{ 'empty--error': state === 'error' }">{{ stateMessage }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { ContractState, PositiveInsightItem } from '../types/domain'

const props = defineProps<{
  items: PositiveInsightItem[]
  state: ContractState
  message?: string
}>()

const stateMessage = computed(() => {
  if (props.state === 'error') {
    return props.message || '卖点接口请求失败，请稍后重试。'
  }
  if (props.state === 'degraded') {
    return props.message || '卖点结果暂时回退为部分数据，请稍后刷新。'
  }
  return props.message || '暂无正面卖点数据'
})

function formatPercent(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}

function displayUxLabel(item: PositiveInsightItem): string {
  return item.uxSecondaryLabel?.trim() || item.aspect
}
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

.head,
.notice,
.insight-grid,
.empty {
  position: relative;
  z-index: var(--z-raised);
}

.title-block {
  display: grid;
  gap: var(--space-2);
}

.eyebrow,
.tag,
.score {
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

.eyebrow {
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-accent-primary);
}

h3,
h4 {
  margin: 0;
  color: var(--color-text-primary);
  line-height: var(--line-height-tight);
}

h3 {
  font-size: var(--font-size-xl);
}

h4 {
  font-size: var(--font-size-lg);
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-4);
}

.insight-card {
  display: grid;
  gap: var(--space-3);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  background: rgba(8, 16, 29, 0.56);
  box-shadow: var(--shadow-inset-soft);
}

.card-head,
.metrics {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.score {
  border-color: rgba(102, 224, 194, 0.24);
  background: rgba(102, 224, 194, 0.1);
  color: var(--color-accent-secondary);
  font-weight: 700;
}

.metrics span {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.evidence-list {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding-left: var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.notice,
.empty {
  margin: 0;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.notice {
  border: 1px dashed rgba(255, 123, 133, 0.28);
  background: rgba(39, 13, 20, 0.24);
  color: var(--color-semantic-down);
}

.empty {
  border: 1px dashed var(--color-border-subtle);
  background: rgba(8, 15, 27, 0.42);
  color: var(--color-text-secondary);
}

.empty--error {
  border-color: rgba(255, 123, 133, 0.28);
  color: var(--color-semantic-down);
}

@media (max-width: 720px) {
  .panel {
    padding: var(--space-3);
  }

  .insight-grid {
    grid-template-columns: 1fr;
  }
}
</style>
