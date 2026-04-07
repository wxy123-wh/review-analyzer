<template>
  <section class="panel" data-motion-spotlight="soft">
    <header class="hero" data-motion-reveal style="--motion-delay: 40ms">
      <div class="hero-copy">
        <div class="hero-topline">
          <span class="eyebrow">Explainability signal</span>
          <span class="status-badge">{{ statusLabel }}</span>
        </div>
        <h3>可解释性分析</h3>
        <p class="note">{{ data?.note ?? '加载中...' }}</p>
      </div>

      <div class="hero-metrics">
        <article class="metric-card metric-card--accent" data-motion-hover="lift">
          <span class="metric-label">特征数量</span>
          <strong>{{ totalFeatures }}</strong>
          <p>当前问题得分拆解中可见的贡献维度。</p>
        </article>
        <article class="metric-card" data-motion-hover="lift">
          <span class="metric-label">主导特征</span>
          <strong>{{ topFeatureLabel }}</strong>
          <p>{{ topFeatureSupport }}</p>
        </article>
        <article class="metric-card" data-motion-hover="lift">
          <span class="metric-label">权重覆盖</span>
          <strong>{{ weightCoverage }}</strong>
          <p>基于固定权重与当前问题分数拆解生成。</p>
        </article>
      </div>
    </header>

    <section class="detail-shell" data-motion-reveal style="--motion-delay: 120ms">
      <div class="section-head">
        <div>
          <span class="section-kicker">Contribution detail</span>
          <h4>特征权重明细</h4>
        </div>
        <span class="support-pill">{{ detailSupport }}</span>
      </div>

      <ol v-if="features.length" class="contribution-list">
        <li
          v-for="(item, index) in features"
          :key="item.feature"
          class="contribution-card"
          :style="{ '--motion-delay': `${160 + index * 40}ms` }"
          data-motion-reveal
          data-motion-hover="lift"
        >
          <div class="contribution-headline">
            <span class="contribution-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <strong>{{ item.feature }}</strong>
            <span class="weight-pill">{{ formatWeight(item.weight) }}</span>
          </div>
          <div class="bar" aria-hidden="true">
            <div class="fill" :style="{ width: `${Math.round(item.weight * 100)}%` }"></div>
          </div>
        </li>
      </ol>
      <p v-else class="empty">暂无可解释性数据</p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { ShowcaseExplainabilityData } from '../types/domain'
import { formatShowcaseStatus } from '../utils/showcaseCopy'

const props = defineProps<{
  data: ShowcaseExplainabilityData | null
}>()

const features = computed(() => props.data?.featureContributions ?? [])
const statusLabel = computed(() => formatShowcaseStatus(props.data?.status))
const totalFeatures = computed(() => features.value.length)
const topFeature = computed(() => [...features.value].sort((left, right) => right.weight - left.weight)[0])
const topFeatureLabel = computed(() => topFeature.value?.feature ?? '等待数据')
const topFeatureSupport = computed(() =>
  topFeature.value ? `当前最高权重 ${formatWeight(topFeature.value.weight)}` : '暂无主导特征信号',
)
const weightCoverage = computed(() => `${Math.round(features.value.reduce((sum, item) => sum + item.weight, 0) * 100)}%`)
const detailSupport = computed(() =>
  features.value.length ? `展示 ${features.value.length} 条得分贡献` : '暂无解释权重',
)

function formatWeight(value: number): string {
  return `${Math.round(value * 100)}%`
}
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
  background: linear-gradient(135deg, rgba(122, 184, 255, 0.08), transparent 34%);
}

.hero,
.detail-shell,
.empty {
  position: relative;
  z-index: var(--z-raised);
}

.hero,
.hero-copy,
.hero-metrics {
  display: grid;
  gap: var(--space-3);
}

.hero-topline,
.section-head,
.contribution-headline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.hero-metrics {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.eyebrow,
.status-badge,
.metric-label,
.section-kicker,
.support-pill,
.contribution-index,
.weight-pill {
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
.section-kicker,
.weight-pill {
  color: var(--color-accent-primary);
}

.status-badge,
.support-pill,
.metric-label,
.contribution-index {
  color: var(--color-text-secondary);
}

h3,
h4,
.metric-card strong,
.contribution-card strong {
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
.empty {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.detail-shell {
  display: grid;
  gap: var(--space-4);
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

.contribution-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--space-3);
}

.contribution-card {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(11, 21, 38, 0.8);
}

.contribution-headline {
  align-items: center;
}

.contribution-headline strong {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
}

.bar {
  overflow: hidden;
  height: 0.625rem;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.06);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.04);
}

.fill {
  height: 100%;
  border-radius: inherit;
  background: var(--gradient-accent);
}

.empty {
  padding: var(--space-3);
  border: 1px dashed var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 15, 27, 0.42);
}

@media (max-width: 920px) {
  .hero-metrics {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .panel,
  .detail-shell,
  .metric-card {
    padding: var(--space-3);
  }

  .hero-topline,
  .section-head,
  .contribution-headline {
    flex-direction: column;
    align-items: flex-start;
  }

  .contribution-headline {
    gap: var(--space-2);
  }
}
</style>
