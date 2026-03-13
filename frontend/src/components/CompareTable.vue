<template>
  <div class="panel">
    <h3>竞品对比概览</h3>
    <table v-if="items.length > 0">
      <thead>
        <tr>
          <th>方面</th>
          <th>我方分数</th>
          <th>竞品分数</th>
          <th>差距</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.aspect">
          <td>{{ item.aspect }}</td>
          <td>{{ item.ourScore.toFixed(2) }}</td>
          <td>{{ item.competitorScore.toFixed(2) }}</td>
          <td :class="{ up: item.gap >= 0, down: item.gap < 0 }">
            {{ item.gap.toFixed(2) }}
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="empty">暂无竞品对比数据</p>
  </div>
</template>

<script setup lang="ts">
import type { CompareItem } from '../types/domain'

defineProps<{
  items: CompareItem[]
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
}

.up {
  color: #16794f;
  font-weight: 700;
}

.down {
  color: #b5473a;
  font-weight: 700;
}

.empty {
  margin: 0;
  color: #677c72;
}
</style>
