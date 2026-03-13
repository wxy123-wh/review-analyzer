<template>
  <article class="status-card" :class="statusClass">
    <h3>{{ name }}</h3>
    <p>Status: {{ status }}</p>
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
</script>

<style scoped>
.status-card {
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #d7dee8;
  background: #ffffff;
}

.status-card h3 {
  margin: 0 0 8px;
  font-size: 18px;
}

.status-card p {
  margin: 0;
}

.up {
  border-color: #3fa76f;
}

.down {
  border-color: #e15f5f;
}

.unknown {
  border-color: #de9f35;
}
</style>
