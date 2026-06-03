<template>
  <section class="panel" data-motion-spotlight="soft">
    <header class="hero" data-motion-reveal style="--motion-delay: 40ms">
      <div class="hero-copy">
        <div class="hero-topline">
          <span class="eyebrow">Pipeline stage</span>
          <span class="status-badge">{{ statusLabel }}</span>
        </div>
        <h3>流水线编排</h3>
        <p class="note">{{ data?.note ?? '加载中...' }}</p>
      </div>

      <div class="hero-metrics">
        <article class="metric-card metric-card--accent" data-motion-hover="lift">
          <span class="metric-label">阶段总数</span>
          <strong>{{ totalStages }}</strong>
          <p>当前可从真实运行态推断出的链路节点。</p>
        </article>
        <article class="metric-card" data-motion-hover="lift">
          <span class="metric-label">进行中</span>
          <strong>{{ runningStages }}</strong>
          <p>{{ currentStageLabel }}</p>
        </article>
        <article class="metric-card" data-motion-hover="lift">
          <span class="metric-label">已完成</span>
          <strong>{{ completedStages }}</strong>
          <p>已进入稳定或完成状态的阶段数量。</p>
        </article>
      </div>
    </header>

    <section class="detail-shell" data-motion-reveal style="--motion-delay: 120ms">
      <div class="section-head">
        <div>
          <span class="section-kicker">Stage detail</span>
          <h4>分阶段回放</h4>
        </div>
        <span class="support-pill">{{ timelineSupport }}</span>
      </div>

      <ul v-if="data?.stages?.length" class="stage-list">
        <li
          v-for="(stage, index) in data.stages"
          :key="stage.name"
          class="stage-card"
          :style="{ '--motion-delay': `${160 + index * 40}ms` }"
          data-motion-reveal
          data-motion-hover="lift"
        >
          <div class="stage-index">{{ String(index + 1).padStart(2, '0') }}</div>
          <div class="stage-body">
            <div class="stage-headline">
              <strong>{{ stage.name }}</strong>
              <span class="state-pill" :class="stateTone(stage.state)">{{ stage.state }}</span>
            </div>
            <p>{{ stage.detail }}</p>
          </div>
        </li>
      </ul>
      <p v-else class="empty">暂无流水线数据</p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { ShowcasePipelineData } from '../types/domain'
import { formatShowcaseStatus } from '../utils/showcaseCopy'

const props = defineProps<{
  data: ShowcasePipelineData | null
}>()

const stages = computed(() => props.data?.stages ?? [])
const statusLabel = computed(() => formatShowcaseStatus(props.data?.status))

const totalStages = computed(() => stages.value.length)
const runningStages = computed(() => stages.value.filter((stage) => isRunningState(stage.state)).length)
const completedStages = computed(() => stages.value.filter((stage) => isCompletedState(stage.state)).length)

const currentStageLabel = computed(() => {
  const activeStage = stages.value.find((stage) => isRunningState(stage.state) || isQueuedState(stage.state))
  if (activeStage) {
    return `${activeStage.name} · ${activeStage.state}`
  }
  const lastStage = stages.value.at(-1)
  return lastStage ? `${lastStage.name} · ${lastStage.state}` : '等待流水线数据'
})

const timelineSupport = computed(() => {
  if (!stages.value.length) {
    return '暂无阶段信号'
  }
  if (runningStages.value > 0) {
    return `有 ${runningStages.value} 个阶段处于推进中`
  }
  return '当前展示最近一次真实运行态摘要'
})

function normalizeState(state: string): string {
  return state.trim().toUpperCase()
}

function isCompletedState(state: string): boolean {
  return ['DONE', 'SUCCESS', 'COMPLETED', 'SUCCEEDED', 'STABLE', 'LIVE'].includes(normalizeState(state))
}

function isRunningState(state: string): boolean {
  return ['RUNNING', 'IN_PROGRESS', 'PROCESSING'].includes(normalizeState(state))
}

function isQueuedState(state: string): boolean {
  return ['QUEUED', 'PENDING', 'IDLE'].includes(normalizeState(state))
}

function isErrorState(state: string): boolean {
  return ['FAILED', 'DEGRADED', 'UNAVAILABLE', 'RUNTIME_UNAVAILABLE'].includes(normalizeState(state))
}

function stateTone(state: string): string {
  if (isCompletedState(state)) {
    return 'up'
  }
  if (isRunningState(state)) {
    return 'accent'
  }
  if (isQueuedState(state)) {
    return 'unknown'
  }
  if (isErrorState(state)) {
    return 'down'
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
  background: linear-gradient(135deg, var(--color-accent-soft), transparent 36%);
}

.hero,
.detail-shell,
.empty {
  position: relative;
  z-index: var(--z-raised);
}

.hero,
.hero-copy,
.hero-metrics,
.section-head,
.stage-body {
  display: grid;
  gap: var(--space-3);
}

.hero {
  gap: var(--space-4);
}

.hero-topline,
.section-head,
.stage-headline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.hero-metrics {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.eyebrow,
.status-badge,
.metric-label,
.section-kicker,
.support-pill,
.state-pill {
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
.metric-card--accent .metric-label,
.section-kicker {
  color: var(--color-accent-primary);
}

.status-badge,
.support-pill,
.metric-label,
.state-pill.default {
  color: var(--color-text-secondary);
}

h3,
h4,
.metric-card strong,
.stage-body strong,
.stage-index {
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
.stage-body p,
.empty {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.detail-shell {
  display: grid;
  gap: var(--space-4);
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
  border-color: var(--color-border-strong);
  background: linear-gradient(135deg, var(--color-accent-soft), rgba(8, 16, 29, 0.22));
}

.metric-card strong {
  font-size: clamp(1.75rem, 4vw, 2.4rem);
  line-height: 1;
  letter-spacing: -0.04em;
}

.metric-card p {
  color: var(--color-text-muted);
}

.stage-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--space-3);
}

.stage-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(11, 21, 38, 0.8);
}

.stage-index {
  min-width: 2.5rem;
  min-height: 2.5rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border-default);
  background: var(--color-surface-overlay);
  font-size: var(--font-size-sm);
  letter-spacing: 0.08em;
}

.stage-body strong {
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

.state-pill.down {
  color: var(--color-semantic-down);
  border-color: rgba(255, 123, 133, 0.28);
  background: rgba(255, 123, 133, 0.12);
}

.empty {
  padding: var(--space-3);
  border: 1px dashed var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 15, 27, 0.42);
}

@media (max-width: 820px) {
  .hero-metrics {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .panel,
  .detail-shell {
    padding: var(--space-3);
  }

  .hero-topline,
  .section-head,
  .stage-headline {
    flex-direction: column;
    align-items: flex-start;
  }

  .stage-card {
    grid-template-columns: 1fr;
  }
}
</style>
