<template>
  <div class="panel">
    <h3>问题优先级清单</h3>
    <table v-if="items.length > 0">
      <thead>
        <tr>
          <th>问题</th>
          <th>方面</th>
          <th>优先级</th>
          <th>证据</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.issueId">
          <td>{{ item.title }}</td>
          <td>{{ item.aspect }}</td>
          <td>{{ item.priorityScore.toFixed(4) }}</td>
          <td>{{ item.evidenceSummary }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="empty">暂无问题数据</p>
  </div>
</template>

<script setup lang="ts">
import type { IssueItem } from '../types/domain'

defineProps<{
  items: IssueItem[]
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

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 10px;
  border-bottom: 1px solid #e4ece7;
  vertical-align: top;
}

th {
  color: #2d4a3d;
  font-size: 13px;
}

.empty {
  margin: 0;
  color: #677c72;
}
</style>
