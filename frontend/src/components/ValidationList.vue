<template>
  <div class="panel">
    <h3>改进效果验证</h3>
    <ul v-if="items.length > 0">
      <li v-for="item in items" :key="item.actionId">
        <div class="row">
          <strong>{{ item.actionId }}</strong>
          <span class="delta">改善 {{ (item.improvementRate * 100).toFixed(2) }}%</span>
        </div>
        <p>{{ item.summary }}</p>
      </li>
    </ul>
    <p v-else class="empty">暂无验证结果</p>
  </div>
</template>

<script setup lang="ts">
import type { ValidationItem } from '../types/domain'

defineProps<{
  items: ValidationItem[]
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
  border: 1px solid #e5ede8;
  border-radius: 10px;
  padding: 12px;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.delta {
  color: #176b48;
  font-weight: 700;
}

p {
  margin: 8px 0 0;
  color: #364840;
}

.empty {
  margin: 0;
  color: #677c72;
}
</style>
