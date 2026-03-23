<template>
  <div class="panel">
    <header>
      <h3>Chaos Drill Theater</h3>
      <span class="badge">{{ data?.status ?? 'LOADING' }}</span>
    </header>
    <p class="note">{{ data?.note ?? '加载中...' }}</p>
    <ul v-if="data?.drills?.length">
      <li v-for="drill in data.drills" :key="drill.scenario">
        <div class="row">
          <strong>{{ drill.scenario }}</strong>
          <span>{{ drill.state }}</span>
        </div>
        <p>{{ drill.detail }}</p>
      </li>
    </ul>
    <p v-else class="empty">暂无混沌演练数据</p>
  </div>
</template>

<script setup lang="ts">
import type { ShowcaseChaosData } from '../types/domain'

defineProps<{
  data: ShowcaseChaosData | null
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
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 10px;
}

li {
  border: 1px solid #e2edf3;
  border-radius: 10px;
  padding: 10px;
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

p {
  margin: 8px 0 0;
}

.empty {
  margin: 14px 0 0;
  color: #5a7584;
}
</style>
