<template>
  <section class="panel">
    <header class="hero" data-motion-reveal data-motion-spotlight="soft" style="--motion-delay: 40ms">
      <div class="hero-copy">
        <div class="hero-topline">
          <span class="eyebrow">Resilience drill</span>
          <span class="status-badge">{{ statusLabel }}</span>
        </div>
        <h3>混沌演练（评论链路韧性）</h3>
        <p class="note">{{ data?.note ?? '加载中...' }}</p>
      </div>

      <div class="hero-metrics">
        <article class="metric-card metric-card--accent" data-motion-hover="lift">
          <span class="metric-label">演练剧本</span>
          <strong>{{ totalDrills }}</strong>
          <p>当前可见的链路韧性演示场景。</p>
        </article>
        <article class="metric-card" data-motion-hover="lift">
          <span class="metric-label">待触发</span>
          <strong>{{ pendingDrills }}</strong>
          <p>{{ nextScenario }}</p>
        </article>
        <article class="metric-card" data-motion-hover="lift">
          <span class="metric-label">映射节点</span>
          <strong>{{ mappingItems.length }}</strong>
          <p>固定展示当前评论链路受影响位置。</p>
        </article>
      </div>
    </header>

    <section class="detail-grid" data-motion-reveal style="--motion-delay: 120ms">
      <article class="mapping-shell">
        <div class="section-head">
          <span class="section-kicker">Impact map</span>
          <h4>链路映射</h4>
        </div>
        <ul class="mapping-list">
          <li v-for="(item, index) in mappingItems" :key="item" class="mapping-item" data-motion-hover="lift">
            <span class="mapping-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span>{{ item }}</span>
          </li>
        </ul>
      </article>

      <article class="drill-shell">
        <div class="section-head section-head--split">
          <div>
            <span class="section-kicker">Drill detail</span>
            <h4>剧本状态</h4>
          </div>
          <span class="support-pill">{{ drillSupport }}</span>
        </div>

        <ul v-if="data?.drills?.length" class="drill-list">
          <li
            v-for="(drill, index) in data.drills"
            :key="drill.scenario"
            class="drill-card"
            :style="{ '--motion-delay': `${160 + index * 40}ms` }"
            data-motion-reveal
            data-motion-hover="lift"
          >
            <div class="drill-headline">
              <strong>{{ drill.scenario }}</strong>
              <span class="state-pill" :class="stateTone(drill.state)">{{ drill.state }}</span>
            </div>
            <p>{{ drill.detail }}</p>
          </li>
        </ul>
        <p v-else class="empty">暂无混沌演练数据</p>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { ShowcaseChaosData } from '../types/domain'
import { formatShowcaseStatus } from '../utils/showcaseCopy'

const props = defineProps<{
  data: ShowcaseChaosData | null
}>()

const mappingItems = [
  '评论同步延迟上升 -> 趋势与词云刷新滞后',
  '接口限流波动 -> 问题识别召回下降',
  '任务重试积压 -> 看板更新时间延后',
]

const drills = computed(() => props.data?.drills ?? [])
const statusLabel = computed(() => formatShowcaseStatus(props.data?.status))

const totalDrills = computed(() => drills.value.length)
const pendingDrills = computed(() => drills.value.filter((drill) => isPendingState(drill.state)).length)
const nextScenario = computed(() => {
  const next = drills.value.find((drill) => isPendingState(drill.state))
  return next ? next.scenario : '当前没有待触发剧本'
})
const drillSupport = computed(() => {
  if (!drills.value.length) {
    return '暂无演练信号'
  }
  return `当前展示 ${drills.value.length} 条韧性剧本`
})

function normalizeState(state: string): string {
  return state.trim().toUpperCase()
}

function isPendingState(state: string): boolean {
  return ['PENDING', 'QUEUED', 'IDLE'].includes(normalizeState(state))
}

function isRunningState(state: string): boolean {
  return ['RUNNING', 'IN_PROGRESS', 'PROCESSING'].includes(normalizeState(state))
}

function isCompletedState(state: string): boolean {
  return ['DONE', 'SUCCESS', 'COMPLETED'].includes(normalizeState(state))
}

function stateTone(state: string): string {
  if (isCompletedState(state)) {
    return 'up'
  }
  if (isRunningState(state)) {
    return 'accent'
  }
  if (isPendingState(state)) {
    return 'unknown'
  }
  return 'default'
}
</script>

<style scoped>
.panel {
  position: relative;
  overflow: hidden;
  display: grid;
  gap: var(--space-5);
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
  background: linear-gradient(135deg, var(--color-semantic-unknown-soft), transparent 38%);
  opacity: 0.62;
}

.hero,
.detail-grid,
.mapping-shell,
.drill-shell,
.empty {
  position: relative;
  z-index: var(--z-raised);
}

.hero,
.hero-copy,
.hero-metrics,
.mapping-shell,
.drill-shell {
  display: grid;
  gap: var(--space-3);
}

.hero-topline,
.section-head,
.drill-headline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.hero-metrics {
  grid-template-columns: repeat(3, minmax(12rem, 1fr));
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(18rem, 0.9fr) minmax(0, 1.1fr);
  gap: var(--space-4);
}

.eyebrow,
.status-badge,
.metric-label,
.section-kicker,
.support-pill,
.state-pill,
.mapping-index {
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
.section-kicker {
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.eyebrow,
.section-kicker,
.metric-card--accent .metric-label {
  color: var(--color-semantic-unknown);
}

.status-badge,
.support-pill,
.metric-label,
.state-pill.default,
.mapping-index {
  color: var(--color-text-secondary);
}

h3,
h4,
.metric-card strong,
.drill-card strong {
  margin: 0;
  color: var(--color-text-primary);
}

h3 {
  font-size: clamp(1.75rem, 3vw, 2.35rem);
  line-height: var(--line-height-tight);
  letter-spacing: -0.03em;
}

h4 {
  font-size: var(--font-size-lg);
  line-height: var(--line-height-snug);
}

.note,
.metric-card p,
.drill-card p,
.mapping-item,
.empty {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.mapping-shell,
.drill-shell {
  padding: var(--space-4);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 16, 29, 0.52);
  box-shadow: var(--shadow-inset-soft);
}

.metric-card {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 16, 29, 0.58);
  box-shadow: var(--shadow-inset-soft);
}

.metric-card--accent {
  border-color: var(--color-semantic-unknown-soft);
  background: linear-gradient(135deg, var(--color-semantic-unknown-soft), rgba(8, 16, 29, 0.22));
}

.metric-card strong {
  font-size: clamp(1.75rem, 4vw, 2.4rem);
  line-height: 1;
  letter-spacing: -0.04em;
}

.metric-card p {
  color: var(--color-text-muted);
}

.mapping-list,
.drill-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--space-3);
}

.mapping-item,
.drill-card {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(11, 21, 38, 0.8);
}

@media (hover: hover) {
  .mapping-item:hover,
  .drill-card:hover,
  .metric-card:hover {
    border-color: var(--color-border-default);
  }
}

.mapping-item {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
}

.mapping-index {
  justify-content: center;
}

.drill-card strong {
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
}

.state-pill.up {
  color: var(--color-semantic-up);
  border-color: var(--color-semantic-up-soft);
  background: var(--color-semantic-up-soft);
}

.state-pill.accent {
  color: var(--color-accent-primary);
  border-color: var(--color-accent-soft);
  background: var(--color-accent-soft);
}

.state-pill.unknown {
  color: var(--color-semantic-unknown);
  border-color: var(--color-semantic-unknown-soft);
  background: var(--color-semantic-unknown-soft);
}

.empty {
  padding: var(--space-3);
  border: 1px dashed var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 15, 27, 0.42);
}

@media (max-width: 920px) {
  .hero-metrics,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .panel,
  .mapping-shell,
  .drill-shell {
    padding: var(--space-3);
  }

  .metric-card {
    padding: var(--space-3);
  }

  .hero-topline,
  .section-head,
  .drill-headline {
    flex-direction: column;
    align-items: flex-start;
  }

  .mapping-item {
    grid-template-columns: 1fr;
  }
}

html[data-motion='reduce'] .panel::after,
html[data-motion='none'] .panel::after {
  opacity: 0.36;
}
</style>
