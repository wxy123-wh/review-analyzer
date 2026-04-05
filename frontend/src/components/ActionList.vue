<template>
  <section class="panel" data-motion-spotlight="soft">
    <div class="head">
      <div class="title-block">
        <span class="eyebrow">动作队列</span>
        <h3>改进行动登记</h3>
      </div>
      <button type="button" class="create-btn" data-motion-hover="lift" @click="$emit('create-demo')">登记演示数据动作</button>
    </div>
    <ul v-if="items.length > 0" class="action-list">
      <li v-for="item in items" :key="item.actionId" class="action-item" data-motion-hover="lift">
        <div class="action-main">
          <strong>{{ item.actionName }}</strong>
          <span class="meta-label">{{ item.issueId }}</span>
        </div>
        <span class="status-badge">{{ item.status }}</span>
      </li>
    </ul>
    <p v-else class="empty">暂无动作，点击“登记演示数据动作”快速创建。</p>
  </section>
</template>

<script setup lang="ts">
import type { ActionItem } from '../types/domain'

defineEmits<{
  (event: 'create-demo'): void
}>()

defineProps<{
  items: ActionItem[]
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
  background: linear-gradient(135deg, rgba(122, 184, 255, 0.06), transparent 32%);
}

.head {
  position: relative;
  z-index: var(--z-raised);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

h3 {
  margin: 0;
  font-size: var(--font-size-xl);
  line-height: var(--line-height-tight);
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.title-block {
  display: grid;
  gap: var(--space-2);
}

.eyebrow,
.status-badge,
.meta-label {
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

.create-btn {
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  background: linear-gradient(135deg, rgba(122, 184, 255, 0.16), rgba(102, 224, 194, 0.2));
  color: var(--color-text-primary);
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-sm);
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-inset-soft);
  transition:
    border-color var(--motion-medium) var(--easing-standard),
    background-color var(--motion-medium) var(--easing-standard),
    box-shadow var(--motion-medium) var(--easing-standard),
    transform var(--motion-fast) var(--easing-standard);
}

.create-btn:hover {
  border-color: var(--color-accent-primary);
  box-shadow: var(--shadow-glow);
}

.create-btn:focus-visible {
  outline: none;
  box-shadow: var(--shadow-focus);
}

.action-list {
  position: relative;
  z-index: var(--z-raised);
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--space-2);
}

.action-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  background: rgba(8, 16, 29, 0.48);
  box-shadow: var(--shadow-inset-soft);
  transition:
    border-color var(--motion-medium) var(--easing-standard),
    background-color var(--motion-medium) var(--easing-standard),
    transform var(--motion-fast) var(--easing-standard);
}

.action-item:hover {
  border-color: var(--color-border-default);
  background: rgba(12, 22, 38, 0.7);
}

.action-main {
  min-width: 0;
  display: grid;
  gap: var(--space-2);
}

.action-main strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
}

.meta-label {
  color: var(--color-text-secondary);
}

.status-badge {
  flex-shrink: 0;
  color: var(--color-accent-secondary);
  border-color: rgba(102, 224, 194, 0.24);
  background: rgba(102, 224, 194, 0.12);
}

.empty {
  position: relative;
  z-index: var(--z-raised);
  margin: 0;
  padding: var(--space-3);
  border: 1px dashed var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 15, 27, 0.42);
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
  align-items: center;
}

@media (max-width: 640px) {
  .head,
  .action-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .create-btn,
  .status-badge {
    width: 100%;
    justify-content: center;
  }
}
</style>
