<template>
  <article class="status-card" :class="statusClass">
    <div class="status-card__header">
      <span class="status-card__eyebrow">状态总览</span>
      <span class="status-card__badge">{{ statusLabel }}</span>
    </div>
    <div class="status-card__body">
      <h3>{{ name }}</h3>
      <p>状态：{{ statusLabel }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type Status = 'UP' | 'DOWN' | 'UNKNOWN'

const props = defineProps<{
  name: string
  status: Status
}>()

const statusClass = computed(() => {
  if (props.status === 'UP') return 'up'
  if (props.status === 'DOWN') return 'down'
  return 'unknown'
})

const statusLabel = computed(() => {
  if (props.status === 'UP') return '正常'
  if (props.status === 'DOWN') return '异常'
  return '未知'
})
</script>

<style scoped>
.status-card {
  position: relative;
  overflow: hidden;
  display: grid;
  gap: var(--space-4);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  border: 1px solid var(--color-border-default);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 58%),
    var(--gradient-surface);
  box-shadow: var(--shadow-raised);
  transition:
    border-color var(--motion-medium) var(--easing-standard),
    box-shadow var(--motion-medium) var(--easing-standard),
    transform var(--motion-fast) var(--easing-standard);
}

.status-card::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.05), transparent 35%);
  opacity: 0.7;
}

.status-card__header,
.status-card__body {
  position: relative;
  z-index: var(--z-raised);
}

.status-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.status-card__eyebrow,
.status-card__badge {
  display: inline-flex;
  align-items: center;
  min-height: 1.75rem;
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border-default);
  background: var(--color-surface-overlay);
  box-shadow: var(--shadow-inset-soft);
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.status-card__eyebrow {
  color: var(--color-text-muted);
}

.status-card__badge {
  color: var(--color-text-primary);
}

.status-card__body {
  display: grid;
  gap: var(--space-2);
}

.status-card h3 {
  margin: 0;
  font-size: var(--font-size-xl);
  line-height: var(--line-height-tight);
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.status-card p {
  margin: 0;
  font-size: var(--font-size-md);
  color: var(--color-text-secondary);
}

.status-card:hover {
  transform: translateY(-1px);
}

.up {
  border-color: var(--color-semantic-up);
  box-shadow: var(--shadow-status-up);
}

.up .status-card__badge {
  border-color: rgba(79, 208, 139, 0.3);
  background: var(--color-semantic-up-soft);
}

.down {
  border-color: var(--color-semantic-down);
  box-shadow: var(--shadow-status-down);
}

.down .status-card__badge {
  border-color: rgba(255, 123, 133, 0.3);
  background: var(--color-semantic-down-soft);
}

.unknown {
  border-color: var(--color-semantic-unknown);
  box-shadow: var(--shadow-status-unknown);
}

.unknown .status-card__badge {
  border-color: rgba(240, 189, 103, 0.3);
  background: var(--color-semantic-unknown-soft);
}

@media (max-width: 640px) {
  .status-card {
    gap: var(--space-3);
  }

  .status-card__header {
    flex-wrap: wrap;
  }
}
</style>
