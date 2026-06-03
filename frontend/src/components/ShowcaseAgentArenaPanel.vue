<template>
  <section class="panel" data-motion-spotlight="soft">
    <header class="hero" data-motion-reveal style="--motion-delay: 40ms">
      <div class="hero-copy">
        <div class="hero-topline">
          <span class="eyebrow">Agent arena</span>
          <span class="status-badge">{{ statusLabel }}</span>
        </div>
        <h3>智能体协同台</h3>
        <p class="note">{{ data?.note ?? '加载中...' }}</p>
      </div>

      <div class="hero-metrics">
        <article class="metric-card metric-card--accent" data-motion-hover="lift">
          <span class="metric-label">智能体数量</span>
          <strong>{{ totalAgents }}</strong>
          <p>由真实子系统状态合成的席位数量。</p>
        </article>
        <article class="metric-card" data-motion-hover="lift">
          <span class="metric-label">平均置信度</span>
          <strong>{{ averageConfidence }}</strong>
          <p>{{ confidenceSupport }}</p>
        </article>
        <article class="metric-card" data-motion-hover="lift">
          <span class="metric-label">活跃节点</span>
          <strong>{{ runningAgents }}</strong>
          <p>{{ runningSupport }}</p>
        </article>
      </div>
    </header>

    <section class="detail-shell" data-motion-reveal style="--motion-delay: 120ms">
      <div class="section-head">
        <div>
          <span class="section-kicker">Coordination detail</span>
          <h4>协同席位明细</h4>
        </div>
        <span class="support-pill">{{ roleSupport }}</span>
      </div>

      <div v-if="data?.agents?.length" class="table-shell">
        <table>
          <thead>
            <tr>
              <th>智能体</th>
              <th>职责</th>
              <th>状态</th>
              <th>置信度</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="agent in data.agents" :key="agent.agentName" data-motion-hover="lift">
              <td class="name-cell">
                <strong>{{ agent.agentName }}</strong>
              </td>
              <td>
                <span class="role-pill">{{ agent.role }}</span>
              </td>
              <td>
                <span class="state-pill" :class="stateTone(agent.state)">{{ agent.state }}</span>
              </td>
              <td>
                <span class="confidence-pill">{{ formatConfidence(agent.confidence) }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="empty">暂无智能体数据</p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { ShowcaseAgentArenaData } from '../types/domain'
import { formatShowcaseStatus } from '../utils/showcaseCopy'

const props = defineProps<{
  data: ShowcaseAgentArenaData | null
}>()

const agents = computed(() => props.data?.agents ?? [])
const statusLabel = computed(() => formatShowcaseStatus(props.data?.status))

const totalAgents = computed(() => agents.value.length)
const runningAgents = computed(() => agents.value.filter((agent) => isRunningState(agent.state)).length)
const averageConfidence = computed(() => {
  if (!agents.value.length) {
    return '0%'
  }
  const total = agents.value.reduce((sum, agent) => sum + agent.confidence, 0)
  return `${Math.round((total / agents.value.length) * 100)}%`
})

const confidenceSupport = computed(() => {
  const strongest = [...agents.value].sort((left, right) => right.confidence - left.confidence)[0]
  return strongest ? `最高 ${strongest.agentName} · ${formatConfidence(strongest.confidence)}` : '等待协同数据'
})

const runningSupport = computed(() => {
  const active = agents.value.find((agent) => isRunningState(agent.state))
  if (active) {
    return `${active.agentName} 正在推进 ${active.role}`
  }
  const attention = agents.value.find((agent) => isAttentionState(agent.state))
  return attention ? `${attention.agentName} 当前处于 ${attention.state}` : '当前没有运行中的节点'
})

const roleSupport = computed(() => {
  if (!agents.value.length) {
    return '暂无职责分布'
  }
  return `${new Set(agents.value.map((agent) => agent.role)).size} 类职责可见`
})

function normalizeState(state: string): string {
  return state.trim().toUpperCase()
}

function isRunningState(state: string): boolean {
  return ['RUNNING', 'IN_PROGRESS', 'PROCESSING'].includes(normalizeState(state))
}

function isIdleState(state: string): boolean {
  return ['IDLE', 'QUEUED', 'PENDING'].includes(normalizeState(state))
}

function isHealthyState(state: string): boolean {
  return ['SUCCEEDED', 'SUCCESS', 'STABLE', 'LIVE', 'READY'].includes(normalizeState(state))
}

function isAttentionState(state: string): boolean {
  return ['FAILED', 'DEGRADED', 'UNAVAILABLE', 'RUNTIME_UNAVAILABLE'].includes(normalizeState(state))
}

function stateTone(state: string): string {
  if (isRunningState(state)) {
    return 'accent'
  }
  if (isHealthyState(state)) {
    return 'up'
  }
  if (isIdleState(state)) {
    return 'unknown'
  }
  if (isAttentionState(state)) {
    return 'down'
  }
  return 'default'
}

function formatConfidence(value: number): string {
  return `${(value * 100).toFixed(0)}%`
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
  background: linear-gradient(135deg, var(--color-accent-strong), transparent 36%);
}

.hero,
.detail-shell,
.empty {
  position: relative;
  z-index: var(--z-raised);
}

.hero,
.hero-copy,
.hero-metrics {
  display: grid;
  gap: var(--space-3);
}

.hero-topline,
.section-head {
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
.role-pill,
.state-pill,
.confidence-pill {
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
.section-kicker,
.confidence-pill {
  color: var(--color-accent-secondary);
}

.status-badge,
.support-pill,
.metric-label,
.role-pill,
.state-pill.default {
  color: var(--color-text-secondary);
}

h3,
h4,
.metric-card strong,
.name-cell strong {
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
  background: linear-gradient(135deg, var(--color-accent-strong), rgba(8, 16, 29, 0.22));
}

.metric-card strong {
  font-size: clamp(1.75rem, 4vw, 2.4rem);
  line-height: 1;
  letter-spacing: -0.04em;
}

.metric-card p {
  color: var(--color-text-muted);
}

.table-shell {
  overflow: hidden;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 16, 29, 0.62);
  box-shadow: var(--shadow-inset-soft);
}

table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
}

th,
td {
  text-align: left;
  padding: var(--space-3);
}

th {
  border-bottom: 1px solid var(--color-border-default);
  font-size: var(--font-size-xs);
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  background: rgba(11, 23, 41, 0.92);
}

tbody td {
  border-bottom: 1px solid var(--color-border-subtle);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  transition:
    background-color var(--motion-fast) var(--easing-standard),
    border-color var(--motion-fast) var(--easing-standard);
}

tbody tr:last-child td {
  border-bottom: none;
}

tbody tr:hover td {
  background: rgba(13, 25, 45, 0.86);
  border-bottom-color: var(--color-border-default);
}

.name-cell strong {
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
}

.role-pill {
  color: var(--color-text-primary);
}

.confidence-pill {
  border-color: var(--color-accent-strong);
  background: var(--color-accent-strong);
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

.state-pill.up {
  color: var(--color-semantic-up);
  border-color: var(--color-semantic-up-soft);
  background: var(--color-semantic-up-soft);
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

@media (max-width: 860px) {
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
  .section-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .table-shell {
    overflow-x: auto;
  }

  th,
  td {
    min-width: 7rem;
  }

  .name-cell {
    min-width: 11rem;
  }
}
</style>
