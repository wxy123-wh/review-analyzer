<template>
  <div class="panel">
    <h3>趋势追踪（{{ aspect }}）</h3>
    <ul v-if="points.length > 0">
      <li v-for="point in points" :key="point.period">
        <strong>{{ point.period }}</strong>
        <span>负面率 {{ (point.negativeRate * 100).toFixed(1) }}%</span>
        <span>提及量 {{ point.mentionVolume }}</span>
      </li>
    </ul>
    <p v-else class="empty">暂无趋势数据</p>
  </div>
</template>

<script setup lang="ts">
import type { TrendPoint } from '../types/domain'

defineProps<{
  aspect: string
  points: TrendPoint[]
}>()
</script>

<style scoped>
.panel {
  border: 1px solid #cfe1d9;
  border-radius: 14px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.9);
}

h3 {
  margin: 0 0 12px;
}

ul {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 10px;
}

li {
  display: grid;
  grid-template-columns: 120px 1fr 1fr;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #e5ede8;
  border-radius: 10px;
}

.empty {
  margin: 0;
  color: #677c72;
}
</style>
