<template>
  <section class="panel">
    <header class="head">
      <div class="title-block">
        <span class="eyebrow">Compare snapshot</span>
        <h3>竞品对比概览</h3>
        <p v-if="productCode || comparisonProductCode" class="meta">
          主产品：{{ productCode || '未选择' }}
          <span class="meta-divider">vs</span>
          对比产品：{{ comparisonProductCode || '未选择' }}
        </p>
      </div>
    </header>

    <div v-if="state === 'success' && items.length > 0" class="table-shell">
      <table>
        <thead>
          <tr>
            <th class="aspect-column">方面</th>
            <th class="score-column">我方分数</th>
            <th class="score-column">竞品分数</th>
            <th class="gap-column">差距</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.aspect">
            <td class="aspect-column aspect-cell">
              <strong>{{ item.aspect }}</strong>
            </td>
            <td class="score-column score-cell">
              <span class="score-pill our-score">{{ item.ourScore.toFixed(2) }}</span>
            </td>
            <td class="score-column score-cell">
              <span class="score-pill competitor-score">{{ item.competitorScore.toFixed(2) }}</span>
            </td>
            <td class="gap-column">
              <span class="gap-pill" :class="{ up: item.gap >= 0, down: item.gap < 0 }">
                {{ item.gap.toFixed(2) }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <p v-else class="empty">{{ message || '暂无竞品对比数据' }}</p>
  </section>
</template>

<script setup lang="ts">
import type { CompareItem, CompareState } from '../types/domain'

defineProps<{
  items: CompareItem[]
  state: CompareState
  message?: string
  productCode?: string
  comparisonProductCode?: string
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
  background: linear-gradient(135deg, rgba(102, 224, 194, 0.06), transparent 34%);
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

.meta {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-snug);
}

.meta-divider {
  display: inline-block;
  margin: 0 var(--space-2);
  color: var(--color-text-muted);
}

.eyebrow,
.score-pill,
.gap-pill {
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
  color: var(--color-accent-secondary);
}

h3 {
  margin: 0;
  font-size: var(--font-size-xl);
  line-height: var(--line-height-tight);
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.table-shell {
  overflow: hidden;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 16, 29, 0.56);
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

tbody tr:hover td {
  background: rgba(13, 25, 45, 0.86);
  border-bottom-color: var(--color-border-default);
}

.aspect-column {
  width: 34%;
}

.score-column,
.gap-column {
  width: 22%;
  white-space: nowrap;
}

.aspect-cell strong {
  display: block;
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
}

.score-cell {
  color: var(--color-text-primary);
}

.score-pill {
  font-weight: 700;
}

.our-score {
  border-color: rgba(122, 184, 255, 0.24);
  background: var(--color-accent-soft);
  color: var(--color-accent-primary);
}

.competitor-score {
  border-color: rgba(127, 144, 168, 0.26);
  color: var(--color-text-secondary);
}

.gap-pill {
  position: relative;
  gap: var(--space-2);
  padding-left: var(--space-2);
  font-weight: 700;
}

.gap-pill::before {
  content: '';
  width: 0.5rem;
  height: 0.5rem;
  border-radius: var(--radius-pill);
  background: currentColor;
  opacity: 0.82;
}

.up {
  color: var(--color-semantic-up);
  border-color: rgba(79, 208, 139, 0.26);
  background: var(--color-semantic-up-soft);
}

.down {
  color: var(--color-semantic-down);
  border-color: rgba(255, 123, 133, 0.26);
  background: var(--color-semantic-down-soft);
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

  th,
  td {
    padding: var(--space-2) var(--space-3);
  }

  .aspect-cell strong {
    min-width: 8rem;
  }
}
</style>
