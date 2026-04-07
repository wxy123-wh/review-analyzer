<template>
  <section class="panel" data-motion-spotlight="soft">
    <div class="head">
      <div class="title-block">
        <span class="eyebrow">验证回看</span>
        <h3>改进效果验证</h3>
      </div>
    </div>
    <p v-if="state === 'degraded' && stateMessage" class="notice">{{ stateMessage }}</p>
    <ul v-if="items.length > 0" class="validation-list">
      <li v-for="item in items" :key="item.actionId" class="validation-item" data-motion-hover="lift">
        <div class="row">
          <strong>{{ item.actionId }}</strong>
          <span class="delta">改善 {{ (item.improvementRate * 100).toFixed(2) }}%</span>
        </div>
        <p>{{ item.summary }}</p>
      </li>
    </ul>
    <p v-else class="empty" :class="{ 'empty--error': state === 'error' }">{{ stateMessage }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { ContractState, ValidationItem } from '../types/domain'

const props = defineProps<{
  items: ValidationItem[]
  state: ContractState
  message?: string
}>()

const stateMessage = computed(() => {
  if (props.state === 'error') {
    return props.message || '验证接口请求失败，请稍后重试。'
  }
  if (props.state === 'degraded') {
    return props.message || '验证结果暂时回退为部分数据，请稍后刷新。'
  }
  return props.message || '暂无验证结果'
})
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
.notice,
.validation-list,
.empty {
  position: relative;
  z-index: var(--z-raised);
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
.delta {
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

.validation-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--space-2);
}

.notice {
  margin: 0;
  padding: var(--space-3);
  border: 1px dashed rgba(255, 123, 133, 0.28);
  border-radius: var(--radius-md);
  background: rgba(39, 13, 20, 0.24);
  color: var(--color-semantic-down);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.validation-item {
  display: grid;
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

.validation-item:hover {
  border-color: var(--color-border-default);
  background: rgba(12, 22, 38, 0.7);
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
}

.delta {
  color: var(--color-accent-secondary);
  border-color: rgba(102, 224, 194, 0.24);
  background: rgba(102, 224, 194, 0.12);
  font-weight: 700;
}

strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
}

p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.empty {
  margin: 0;
  padding: var(--space-3);
  border: 1px dashed var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 15, 27, 0.42);
  color: var(--color-text-secondary);
}

.empty--error {
  border-color: rgba(255, 123, 133, 0.28);
  color: var(--color-semantic-down);
}

@media (max-width: 640px) {
  .row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
