<template>
  <section class="panel">
    <header class="head">
      <div class="title-block">
        <h3>竞品对比</h3>
        <p v-if="productCode || comparisonProductCode" class="meta">
          主产品：{{ primaryDisplayName }}
          <span v-if="primaryCodeHint" class="meta-code">{{ primaryCodeHint }}</span>
          <span class="meta-divider">vs</span>
          对比产品：{{ comparisonDisplayName }}
          <span v-if="comparisonCodeHint" class="meta-code">{{ comparisonCodeHint }}</span>
        </p>
      </div>
    </header>

    <form class="compare-form" data-testid="compare-form" @submit.prevent="submitCompare">
      <label class="field">
        <span>主商品编号</span>
        <input
          v-model="formProductCode"
          data-testid="compare-product-code"
          name="productCode"
          autocomplete="off"
          placeholder="jd-100127936932"
        />
      </label>
      <label class="field">
        <span>竞品编号</span>
        <input
          v-model="formComparisonProductCode"
          data-testid="compare-comparison-product-code"
          name="comparisonProductCode"
          autocomplete="off"
          placeholder="jd-100127936933"
        />
      </label>
      <button class="submit-button" data-testid="compare-submit" type="submit" :disabled="state === 'loading'">
        {{ state === 'loading' ? '查询中...' : '查询对比' }}
      </button>
    </form>

    <div v-if="state === 'success' && items.length > 0" class="table-shell">
      <table>
        <thead>
          <tr>
            <th class="aspect-column">UX 标签</th>
            <th class="score-column">我方分数</th>
            <th class="score-column">竞品分数</th>
            <th class="score-column">提及量</th>
            <th class="score-column">负面率</th>
            <th class="gap-column">差距</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="`${item.aspect}-${displayUxLabel(item)}`">
            <td class="aspect-column aspect-cell">
              <strong>{{ displayUxLabel(item) }}</strong>
            </td>
            <td class="score-column score-cell">
              <span class="score-pill our-score">{{ item.ourScore.toFixed(2) }}</span>
            </td>
            <td class="score-column score-cell">
              <span class="score-pill competitor-score">{{ item.competitorScore.toFixed(2) }}</span>
            </td>
            <td class="score-column score-cell">
              <span>{{ formatMentionPair(item) }}</span>
            </td>
            <td class="score-column score-cell">
              <span>{{ formatNegativeRatePair(item) }}</span>
            </td>
            <td class="gap-column">
              <span class="gap-pill" :class="{ up: item.gap >= 0, down: item.gap < 0 }">
                {{ item.gap.toFixed(2) }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <p v-else class="empty" :class="{ 'empty--error': state === 'error' || state === 'taxonomy-mismatch' }">
      {{ stateMessage }}
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { CompareItem, CompareState } from '../types/domain'

const props = defineProps<{
  items: CompareItem[]
  state: CompareState
  message?: string
  productCode?: string
  productName?: string
  comparisonProductCode?: string
  comparisonProductName?: string
}>()

const emit = defineEmits<{
  (event: 'compare', payload: { productCode: string; comparisonProductCode: string }): void
}>()

const formProductCode = ref(props.productCode ?? '')
const formComparisonProductCode = ref(props.comparisonProductCode ?? '')

const primaryDisplayName = computed(() => displayProductName(props.productName, props.productCode))
const comparisonDisplayName = computed(() => displayProductName(props.comparisonProductName, props.comparisonProductCode))
const primaryCodeHint = computed(() => displayCodeHint(props.productName, props.productCode))
const comparisonCodeHint = computed(() => displayCodeHint(props.comparisonProductName, props.comparisonProductCode))

watch(
  () => props.productCode,
  (value) => {
    formProductCode.value = value ?? ''
  },
)

watch(
  () => props.comparisonProductCode,
  (value) => {
    formComparisonProductCode.value = value ?? ''
  },
)

const stateMessage = computed(() => {
  if (props.message?.trim()) {
    return props.message
  }
  if (props.state === 'taxonomy-mismatch') {
    return '两个商品绑定的 UX 标签体系不一致，请先统一 taxonomy 后再对比。'
  }
  if (props.state === 'missing-target') {
    return '请输入主商品和竞品编号。'
  }
  if (props.state === 'primary-unavailable') {
    return '主商品暂无可用分析结果。'
  }
  if (props.state === 'comparison-unavailable') {
    return '竞品暂无可用分析结果。'
  }
  if (props.state === 'error') {
    return '竞品对比接口请求失败，请稍后重试。'
  }
  if (props.state === 'loading') {
    return '正在查询对比结果...'
  }
  return '输入两个商品编号后查看对比结果。'
})

function displayProductName(productName?: string, productCode?: string): string {
  const normalizedName = productName?.trim()
  if (normalizedName) {
    return normalizedName
  }
  return productCode?.trim() || '未选择'
}

function displayCodeHint(productName?: string, productCode?: string): string {
  const normalizedName = productName?.trim()
  const normalizedCode = productCode?.trim()
  if (!normalizedName || !normalizedCode || normalizedName === normalizedCode) {
    return ''
  }
  return `（${normalizedCode}）`
}

function displayUxLabel(item: CompareItem): string {
  return item.uxSecondaryLabel?.trim() || item.aspect
}

function formatMentionPair(item: CompareItem): string {
  if (item.ourMentionCount === undefined && item.competitorMentionCount === undefined) {
    return '-'
  }
  return `${item.ourMentionCount ?? 0} / ${item.competitorMentionCount ?? 0}`
}

function formatNegativeRatePair(item: CompareItem): string {
  if (item.ourNegativeRate === undefined && item.competitorNegativeRate === undefined) {
    return '-'
  }
  return `${formatPercent(item.ourNegativeRate ?? 0)} / ${formatPercent(item.competitorNegativeRate ?? 0)}`
}

function formatPercent(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}

function submitCompare(): void {
  emit('compare', {
    productCode: formProductCode.value.trim(),
    comparisonProductCode: formComparisonProductCode.value.trim(),
  })
}
</script>

<style scoped>
.panel {
  position: relative;
  overflow: hidden;
  display: grid;
  gap: var(--space-4);
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
  background: linear-gradient(135deg, rgba(102, 224, 194, 0.06), transparent 34%);
}

.head,
.compare-form,
.table-shell,
.empty {
  position: relative;
  z-index: var(--z-raised);
}

.title-block {
  display: grid;
  gap: var(--space-2);
}

.meta {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-snug);
}

.meta-divider {
  display: inline-block;
  margin: 0 var(--space-2);
  color: var(--color-text-muted);
}

.meta-code {
  color: var(--color-text-muted);
}

.compare-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: var(--space-3);
  align-items: end;
}

.field {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.field span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-snug);
}

.field input {
  width: 100%;
  min-height: 2.75rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-subtle);
  background: rgba(8, 16, 29, 0.64);
  color: var(--color-text-primary);
  padding: 0 var(--space-3);
  font: inherit;
  box-shadow: var(--shadow-inset-soft);
}

.field input:focus {
  outline: none;
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-focus), var(--shadow-inset-soft);
}

.submit-button {
  min-height: 2.75rem;
  border: 1px solid rgba(102, 224, 194, 0.28);
  border-radius: var(--radius-md);
  padding: 0 var(--space-4);
  background: rgba(102, 224, 194, 0.14);
  color: var(--color-accent-secondary);
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}

.submit-button:disabled {
  cursor: wait;
  opacity: 0.72;
}

.score-pill,
.gap-pill {
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

h3 {
  margin: 0;
  font-size: var(--font-size-xl);
  line-height: var(--line-height-tight);
  letter-spacing: 0;
  color: var(--color-text-primary);
}

.table-shell {
  overflow: auto;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 16, 29, 0.56);
  box-shadow: var(--shadow-inset-soft);
}

table {
  width: 100%;
  min-width: 760px;
  border-collapse: separate;
  border-spacing: 0;
}

th,
td {
  text-align: left;
  padding: var(--space-3);
}

th {
  padding-top: calc(var(--space-3) + var(--space-1));
  padding-bottom: calc(var(--space-2) + var(--space-1));
  border-bottom: 1px solid var(--color-border-default);
  font-size: var(--font-size-xs);
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  background: rgba(11, 23, 41, 0.92);
}

tbody td {
  border-bottom: 1px solid var(--color-border-subtle);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-snug);
  color: var(--color-text-secondary);
  transition:
    background-color var(--motion-fast) var(--easing-standard),
    border-color var(--motion-fast) var(--easing-standard);
}

tbody tr:last-child td {
  border-bottom: none;
}

tbody tr:hover td {
  background: rgba(13, 25, 45, 0.86);
  border-bottom-color: var(--color-border-default);
}

.aspect-column {
  width: 28%;
}

.score-column,
.gap-column {
  width: 14%;
  white-space: nowrap;
}

.aspect-cell strong {
  display: block;
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
}

.score-cell {
  color: var(--color-text-primary);
}

.score-pill {
  font-weight: 700;
}

.our-score {
  border-color: rgba(122, 184, 255, 0.24);
  background: var(--color-accent-soft);
  color: var(--color-accent-primary);
}

.competitor-score {
  border-color: rgba(127, 144, 168, 0.26);
  color: var(--color-text-secondary);
}

.gap-pill {
  position: relative;
  gap: var(--space-2);
  padding-left: var(--space-2);
  font-weight: 700;
}

.gap-pill::before {
  content: '';
  width: 0.5rem;
  height: 0.5rem;
  border-radius: var(--radius-pill);
  background: currentColor;
  opacity: 0.82;
}

.up {
  color: var(--color-semantic-up);
  border-color: rgba(79, 208, 139, 0.26);
  background: var(--color-semantic-up-soft);
}

.down {
  color: var(--color-semantic-down);
  border-color: rgba(255, 123, 133, 0.26);
  background: var(--color-semantic-down-soft);
}

.empty {
  margin: 0;
  padding: var(--space-3);
  border: 1px dashed var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 15, 27, 0.42);
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.empty--error {
  border-color: rgba(255, 123, 133, 0.28);
  color: var(--color-semantic-down);
}

@media (max-width: 720px) {
  .panel {
    padding: var(--space-3);
  }

  .compare-form {
    grid-template-columns: 1fr;
  }

  th,
  td {
    padding: var(--space-2) var(--space-3);
  }

  .aspect-cell strong {
    min-width: 8rem;
  }
}
</style>
