<template>
  <section class="panel" data-motion-spotlight="soft">
    <header class="hero" data-motion-reveal style="--motion-delay: 40ms">
      <div class="hero-copy">
        <div class="hero-topline">
          <span class="eyebrow">Report preview</span>
          <span class="status-badge">{{ statusLabel }}</span>
        </div>
        <h3>报告中心</h3>
        <p class="note">{{ data?.note ?? '支持基于真实查询结果生成报告段落预览。' }}</p>
      </div>

      <div class="hero-metrics">
        <article class="metric-card metric-card--accent" data-motion-hover="lift">
          <span class="metric-label">目标模块</span>
          <strong>{{ selectedModuleLabel }}</strong>
          <p>当前预览会沿用既有模块入口与真实查询结果。</p>
        </article>
        <article class="metric-card" data-motion-hover="lift">
          <span class="metric-label">预览段落</span>
          <strong>{{ previewCount }}</strong>
          <p>当前已拼装出的预览章节数量。</p>
        </article>
        <article class="metric-card" data-motion-hover="lift">
          <span class="metric-label">当前状态</span>
          <strong>{{ statusLabel }}</strong>
          <p>{{ previewSupport }}</p>
        </article>
      </div>
    </header>

    <section class="detail-grid" data-motion-reveal style="--motion-delay: 120ms">
      <article class="control-shell">
        <div class="section-head">
          <span class="section-kicker">Preview control</span>
          <h4>预览触发器</h4>
        </div>

        <div class="control-card" data-motion-hover="lift">
          <label class="field-label">
            <span>目标模块</span>
            <select v-model="selectedModule">
              <option value="overview">总览</option>
              <option value="issues">问题</option>
              <option value="compare">对比</option>
              <option value="trends">趋势</option>
              <option value="showcase">演示模块</option>
            </select>
          </label>

          <button type="button" class="preview-button" data-motion-hover="lift" @click="$emit('preview', selectedModule)">
            生成报告预览
          </button>
        </div>
      </article>

      <article class="preview-shell">
        <div class="section-head section-head--split">
          <div>
            <span class="section-kicker">Preview section</span>
            <h4>报告段落预览</h4>
          </div>
          <span class="support-pill">{{ selectedModuleLabel }}</span>
        </div>

        <ol v-if="data?.previewSections?.length" class="preview-list">
          <li
            v-for="(section, index) in data.previewSections"
            :key="index"
            class="preview-card"
            :style="{ '--motion-delay': `${160 + index * 40}ms` }"
            data-motion-reveal
            data-motion-hover="lift"
          >
            <span class="preview-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <p>{{ section }}</p>
          </li>
        </ol>
        <p v-else class="empty">暂未生成报告预览</p>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import type { ShowcaseReportPreviewData } from '../types/domain'
import { formatShowcaseStatus } from '../utils/showcaseCopy'

defineEmits<{
  (event: 'preview', module: string): void
}>()

const props = defineProps<{
  data: ShowcaseReportPreviewData | null
}>()

const selectedModule = ref('overview')

const moduleLabels: Record<string, string> = {
  overview: '总览',
  issues: '问题',
  compare: '对比',
  trends: '趋势',
  showcase: '演示模块',
}

const statusLabel = computed(() => (props.data?.status ? formatShowcaseStatus(props.data.status) : '就绪'))
const previewCount = computed(() => props.data?.previewSections?.length ?? 0)
const selectedModuleLabel = computed(() => moduleLabels[selectedModule.value] ?? selectedModule.value)
const previewSupport = computed(() => {
  if (previewCount.value > 0) {
    return `已生成 ${previewCount.value} 个预览段落`
  }
  return '等待触发预览动作'
})
</script>

<style scoped>
.panel {
  position: relative;
  overflow: hidden;
  display: grid;
  gap: var(--space-5);
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 58%),
    var(--gradient-surface);
  box-shadow: var(--shadow-raised);
}

.panel::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(135deg, var(--color-accent-soft), transparent 34%);
}

.hero,
.detail-grid,
.control-shell,
.preview-shell,
.empty {
  position: relative;
  z-index: var(--z-raised);
}

.hero,
.hero-copy,
.hero-metrics,
.control-shell,
.preview-shell,
.control-card {
  display: grid;
  gap: var(--space-3);
}

.hero-topline,
.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.hero-metrics,
.detail-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.detail-grid {
  gap: var(--space-4);
}

.control-shell {
  grid-column: span 1;
}

.preview-shell {
  grid-column: span 2;
}

.eyebrow,
.status-badge,
.metric-label,
.section-kicker,
.support-pill,
.preview-index {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 1.75rem;
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border-default);
  background: var(--color-surface-overlay);
  box-shadow: var(--shadow-inset-soft);
  font-size: var(--font-size-xs);
}

.eyebrow,
.section-kicker {
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.eyebrow,
.metric-card--accent .metric-label,
.section-kicker {
  color: var(--color-accent-primary);
}

.status-badge,
.support-pill,
.metric-label,
.preview-index,
.field-label span {
  color: var(--color-text-secondary);
}

h3,
h4,
.metric-card strong {
  margin: 0;
  color: var(--color-text-primary);
}

h3 {
  font-size: clamp(1.75rem, 3vw, 2.35rem);
  line-height: var(--line-height-tight);
  letter-spacing: -0.03em;
}

h4 {
  font-size: var(--font-size-lg);
  line-height: var(--line-height-snug);
}

.note,
.metric-card p,
.preview-card p,
.empty {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.control-shell,
.preview-shell {
  padding: var(--space-4);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 16, 29, 0.52);
  box-shadow: var(--shadow-inset-soft);
}

.metric-card {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 16, 29, 0.58);
  box-shadow: var(--shadow-inset-soft);
}

.metric-card--accent {
  border-color: var(--color-border-strong);
  background: linear-gradient(135deg, var(--color-accent-soft), rgba(8, 16, 29, 0.22));
}

.metric-card strong {
  font-size: clamp(1.45rem, 3.4vw, 2.1rem);
  line-height: 1.1;
  letter-spacing: -0.04em;
}

.metric-card p {
  color: var(--color-text-muted);
}

.control-card {
  padding: var(--space-4);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(11, 21, 38, 0.8);
  box-shadow: var(--shadow-inset-soft);
}

.field-label {
  display: grid;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
}

select {
  min-height: 2.75rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-default);
  background: var(--color-surface-input);
  color: var(--color-text-primary);
  padding: 0 var(--space-3);
  font-size: var(--font-size-md);
  box-shadow: var(--shadow-inset-soft);
}

select:focus-visible,
.preview-button:focus-visible {
  outline: none;
  box-shadow: var(--shadow-focus);
}

.preview-button {
  min-height: 2.75rem;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border-strong);
  background: linear-gradient(135deg, var(--color-accent-soft), var(--color-accent-strong));
  color: var(--color-text-primary);
  padding: 0 var(--space-4);
  font-size: var(--font-size-sm);
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--shadow-inset-soft);
  transition:
    border-color var(--motion-medium) var(--easing-standard),
    box-shadow var(--motion-medium) var(--easing-standard),
    transform var(--motion-fast) var(--easing-standard);
}

.preview-button:hover {
  border-color: var(--color-accent-primary);
  box-shadow: var(--shadow-glow);
}

.preview-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--space-3);
}

.preview-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(11, 21, 38, 0.8);
}

.preview-index {
  justify-content: center;
}

.empty {
  padding: var(--space-3);
  border: 1px dashed var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 15, 27, 0.42);
}

@media (max-width: 960px) {
  .hero-metrics,
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .preview-shell {
    grid-column: span 1;
  }
}

@media (max-width: 640px) {
  .panel,
  .control-shell,
  .preview-shell,
  .control-card {
    padding: var(--space-3);
  }

  .hero-topline,
  .section-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .preview-card {
    grid-template-columns: 1fr;
  }
}
</style>
