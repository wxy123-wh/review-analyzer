<template>
  <div class="panel">
    <header>
      <h3>报告中心</h3>
      <span class="badge">{{ data?.status ? formatShowcaseStatus(data.status) : '就绪' }}</span>
    </header>
    <p class="note">{{ data?.note ?? '支持预览报告段落，导出链路当前为演示数据流程。' }}</p>

    <div class="actions">
      <label>
        目标模块
        <select v-model="selectedModule">
          <option value="overview">总览</option>
          <option value="issues">问题</option>
          <option value="compare">对比</option>
          <option value="trends">趋势</option>
          <option value="showcase">演示模块</option>
        </select>
      </label>
      <button type="button" @click="$emit('preview', selectedModule)">生成演示数据报告预览</button>
    </div>

    <ul v-if="data?.previewSections?.length">
      <li v-for="(section, idx) in data.previewSections" :key="idx">{{ section }}</li>
    </ul>
    <p v-else class="empty">暂未生成报告预览</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import type { ShowcaseReportPreviewData } from '../types/domain'
import { formatShowcaseStatus } from '../utils/showcaseCopy'

defineEmits<{
  (event: 'preview', module: string): void
}>()

defineProps<{
  data: ShowcaseReportPreviewData | null
}>()

const selectedModule = ref('overview')
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

.actions {
  margin-top: 14px;
  display: flex;
  align-items: end;
  flex-wrap: wrap;
  gap: 10px;
}

label {
  display: grid;
  gap: 6px;
  font-size: 12px;
}

select {
  height: 36px;
  border-radius: 10px;
  border: 1px solid #b9d8e7;
  padding: 0 10px;
}

button {
  height: 36px;
  border-radius: 999px;
  border: 1px solid #1f84af;
  background: #1f84af;
  color: #fff;
  padding: 0 14px;
  cursor: pointer;
}

ul {
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}

li {
  border: 1px solid #e2edf3;
  border-radius: 10px;
  padding: 10px;
}

.empty {
  margin: 14px 0 0;
  color: #5a7584;
}
</style>
