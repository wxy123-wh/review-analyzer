<template>
  <div class="panel">
    <header>
      <h3>Pipeline Orchestration</h3>
      <span class="badge">{{ data?.status ?? 'LOADING' }}</span>
    </header>
    <p class="note">{{ data?.note ?? '加载中...' }}</p>
    <ul v-if="data?.stages?.length">
      <li v-for="stage in data.stages" :key="stage.name">
        <strong>{{ stage.name }}</strong>
        <span class="state">{{ stage.state }}</span>
        <p>{{ stage.detail }}</p>
      </li>
    </ul>
    <p v-else class="empty">暂无流水线数据</p>
  </div>
</template>

<script setup lang="ts">
import type { ShowcasePipelineData } from '../types/domain'

defineProps<{
  data: ShowcasePipelineData | null
}>()
</script>

<style scoped>
.panel {
  border: 1px solid #bcdeec;
  border-radius: 14px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.9);
}

header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

h3 {
  margin: 0;
}

.badge {
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid #89c3de;
  color: #0f4d74;
}

.note {
  margin: 8px 0 0;
  color: #355e77;
}

ul {
  list-style: none;
  margin: 14px 0 0;
  padding: 0;
  display: grid;
  gap: 10px;
}

li {
  border: 1px solid #dbeaf2;
  border-radius: 10px;
  padding: 10px;
}

strong {
  margin-right: 8px;
}

.state {
  color: #126d96;
  font-size: 12px;
  font-weight: 700;
}

p {
  margin: 6px 0 0;
}

.empty {
  margin: 14px 0 0;
  color: #5a7584;
}
</style>
