<template>
  <div class="panel">
    <header>
      <h3>混沌演练（评论链路韧性）</h3>
      <span class="badge">{{ formatShowcaseStatus(data?.status) }}</span>
    </header>
    <p class="note">{{ data?.note ?? '加载中...' }}</p>
    <section class="mapping">
      <h4>链路映射</h4>
      <ul>
        <li>评论同步延迟上升 -> 趋势与词云刷新滞后</li>
        <li>接口限流波动 -> 问题识别召回下降</li>
        <li>任务重试积压 -> 看板更新时间延后</li>
      </ul>
    </section>
    <ul v-if="data?.drills?.length" class="drill-list">
      <li v-for="drill in data.drills" :key="drill.scenario" class="drill-item">
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
import { formatShowcaseStatus } from '../utils/showcaseCopy'

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

.mapping {
  margin-top: 12px;
  border: 1px solid #d7e8f2;
  border-radius: 10px;
  padding: 10px;
  background: rgba(244, 250, 255, 0.85);
}

.mapping h4 {
  margin: 0;
  font-size: 13px;
  color: #396077;
}

.mapping ul {
  margin: 8px 0 0;
  padding-left: 18px;
  display: grid;
  gap: 6px;
  color: #48687e;
}

.drill-list {
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 10px;
}

.drill-item {
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
