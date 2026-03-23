<template>
  <div class="panel">
    <header>
      <h3>Agent Arena</h3>
      <span class="badge">{{ data?.status ?? 'LOADING' }}</span>
    </header>
    <p class="note">{{ data?.note ?? '加载中...' }}</p>
    <table v-if="data?.agents?.length">
      <thead>
        <tr>
          <th>智能体</th>
          <th>职责</th>
          <th>状态</th>
          <th>置信度</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="agent in data.agents" :key="agent.agentName">
          <td>{{ agent.agentName }}</td>
          <td>{{ agent.role }}</td>
          <td>{{ agent.state }}</td>
          <td>{{ (agent.confidence * 100).toFixed(0) }}%</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="empty">暂无智能体数据</p>
  </div>
</template>

<script setup lang="ts">
import type { ShowcaseAgentArenaData } from '../types/domain'

defineProps<{
  data: ShowcaseAgentArenaData | null
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

table {
  margin-top: 14px;
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 10px;
  border-bottom: 1px solid #e3edf3;
}

.empty {
  margin: 14px 0 0;
  color: #5a7584;
}
</style>
