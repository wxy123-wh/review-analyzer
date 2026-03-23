<template>
  <div class="panel">
    <header>
      <h3>Explainability Capsule</h3>
      <span class="badge">{{ data?.status ?? 'LOADING' }}</span>
    </header>
    <p class="note">{{ data?.note ?? '加载中...' }}</p>
    <ul v-if="data?.featureContributions?.length">
      <li v-for="item in data.featureContributions" :key="item.feature">
        <strong>{{ item.feature }}</strong>
        <span>{{ (item.weight * 100).toFixed(0) }}%</span>
        <div class="bar">
          <div class="fill" :style="{ width: `${Math.round(item.weight * 100)}%` }"></div>
        </div>
      </li>
    </ul>
    <p v-else class="empty">暂无可解释性数据</p>
  </div>
</template>

<script setup lang="ts">
import type { ShowcaseExplainabilityData } from '../types/domain'

defineProps<{
  data: ShowcaseExplainabilityData | null
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
  gap: 12px;
}

li {
  border: 1px solid #e2edf3;
  border-radius: 10px;
  padding: 10px;
}

strong {
  margin-right: 8px;
}

.bar {
  margin-top: 8px;
  height: 9px;
  border-radius: 999px;
  background: #e5f2f9;
}

.fill {
  height: 9px;
  border-radius: 999px;
  background: linear-gradient(90deg, #1f84af, #44d4b6);
}

.empty {
  margin: 14px 0 0;
  color: #5a7584;
}
</style>
