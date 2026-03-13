<template>
  <div class="panel">
    <div class="head">
      <h3>改进行动登记</h3>
      <button type="button" class="create-btn" @click="$emit('create-demo')">登记示例动作</button>
    </div>
    <ul v-if="items.length > 0">
      <li v-for="item in items" :key="item.actionId">
        <strong>{{ item.actionName }}</strong>
        <span>{{ item.issueId }}</span>
        <span>{{ item.status }}</span>
      </li>
    </ul>
    <p v-else class="empty">暂无动作，点击“登记示例动作”快速创建。</p>
  </div>
</template>

<script setup lang="ts">
import type { ActionItem } from '../types/domain'

defineEmits<{
  (event: 'create-demo'): void
}>()

defineProps<{
  items: ActionItem[]
}>()
</script>

<style scoped>
.panel {
  border: 1px solid #cfe1d9;
  border-radius: 14px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.9);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

h3 {
  margin: 0;
}

.create-btn {
  border: 1px solid #1f6f53;
  border-radius: 999px;
  background: #1f6f53;
  color: #fff;
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
}

ul {
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 10px;
}

li {
  display: grid;
  grid-template-columns: 1.5fr 1fr 80px;
  gap: 8px;
  border: 1px solid #e5ede8;
  border-radius: 10px;
  padding: 10px 12px;
  align-items: center;
}

.empty {
  margin: 14px 0 0;
  color: #677c72;
}
</style>
