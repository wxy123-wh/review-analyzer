<template>
  <section class="panel">
    <header class="head">
      <div class="title-block">
        <span class="eyebrow">Issue matrix</span>
        <h3>问题优先级清单</h3>
      </div>
    </header>

    <div v-if="items.length > 0" class="table-shell">
      <table>
        <thead>
          <tr>
            <th class="issue-column">问题</th>
            <th class="aspect-column">方面</th>
            <th class="score-column">优先级</th>
            <th class="evidence-column">证据</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.issueId">
            <td class="issue-column issue-cell">
              <strong>{{ item.title }}</strong>
            </td>
            <td class="aspect-column">
              <span class="meta-pill">{{ item.aspect }}</span>
            </td>
            <td class="score-column score-cell">
              <span class="score-pill">{{ item.priorityScore.toFixed(4) }}</span>
            </td>
            <td class="evidence-column evidence-cell">{{ item.evidenceSummary }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <p v-else class="empty">暂无问题数据</p>
  </section>
</template>

<script setup lang="ts">
import type { IssueItem } from '../types/domain'

defineProps<{
  items: IssueItem[]
}>()
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
  background: linear-gradient(135deg, rgba(122, 184, 255, 0.06), transparent 34%);
  opacity: 0.7;
}

.head,
.table-shell,
.empty {
  position: relative;
  z-index: var(--z-raised);
}

.title-block {
  display: grid;
  gap: var(--space-2);
}

.eyebrow,
.meta-pill,
.score-pill {
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

h3 {
  margin: 0;
  font-size: var(--font-size-xl);
  line-height: var(--line-height-tight);
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.table-shell {
  overflow-x: auto;
  overflow-y: hidden;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 16, 29, 0.56);
  box-shadow: var(--shadow-inset-soft);
  scrollbar-width: thin;
}

table {
  width: 100%;
  min-width: 42rem;
  border-collapse: separate;
  border-spacing: 0;
}

th,
td {
  text-align: left;
  padding: var(--space-3);
  vertical-align: top;
}

th {
  padding-top: calc(var(--space-3) + var(--space-1));
  padding-bottom: calc(var(--space-2) + var(--space-1));
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
  line-height: var(--line-height-snug);
  color: var(--color-text-secondary);
  transition:
    background-color var(--motion-fast) var(--easing-standard),
    border-color var(--motion-fast) var(--easing-standard);
}

tbody tr:last-child td {
  border-bottom: none;
}

@media (hover: hover) {
  tbody tr:hover td {
    background: rgba(13, 25, 45, 0.86);
    border-bottom-color: var(--color-border-default);
  }
}

.issue-column {
  width: 26%;
}

.aspect-column {
  width: 16%;
}

.score-column {
  width: 16%;
  white-space: nowrap;
}

.evidence-column {
  width: 42%;
}

.issue-cell strong {
  display: block;
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
}

.meta-pill {
  color: var(--color-text-secondary);
}

.score-cell {
  color: var(--color-text-primary);
}

.score-pill {
  border-color: rgba(122, 184, 255, 0.24);
  background: var(--color-accent-soft);
  color: var(--color-accent-primary);
  font-weight: 700;
}

.evidence-cell {
  color: var(--color-text-secondary);
}

.empty {
  margin: 0;
  padding: var(--space-3);
  border: 1px dashed var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 15, 27, 0.42);
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

@media (max-width: 720px) {
  .panel {
    padding: var(--space-3);
  }

  .table-shell {
    margin-inline: calc(var(--space-3) * -1);
    padding-inline: var(--space-3);
    border-left: 0;
    border-right: 0;
    border-radius: 0;
  }

  th,
  td {
    padding: var(--space-2) var(--space-3);
  }

  .issue-cell strong,
  .evidence-cell {
    min-width: 9rem;
  }
}
</style>
