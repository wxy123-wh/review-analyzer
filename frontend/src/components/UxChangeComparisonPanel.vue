<template>
  <section class="panel" data-motion-spotlight="soft">
    <header class="head">
      <div class="title-block">
        <span class="eyebrow">UX 变化</span>
        <h3>前后对比</h3>
      </div>
    </header>

    <form class="comparison-form" data-testid="ux-change-form" @submit.prevent="submitComparison">
      <label class="field">
        <span>商品编号</span>
        <input
          v-model="formProductCode"
          data-testid="ux-change-product-code"
          name="productCode"
          autocomplete="off"
          placeholder="jd-100127936932"
        />
      </label>
      <label class="field">
        <span>时间点</span>
        <input v-model="formChangeDate" data-testid="ux-change-date" name="changeDate" type="date" />
      </label>
      <label class="field">
        <span>时间间隔</span>
        <select v-model="formWindowPreset" data-testid="ux-change-window-preset" name="windowPreset">
          <option value="ONE_MONTH">前后一个月</option>
          <option value="TWO_WEEKS">前后两周</option>
          <option value="THREE_MONTHS">前三后三个月</option>
          <option value="CUSTOM">自定义</option>
        </select>
      </label>
      <label v-if="formWindowPreset === 'CUSTOM'" class="field compact">
        <span>前窗口天数</span>
        <input v-model.number="formCustomBeforeDays" data-testid="ux-change-before-days" min="1" type="number" />
      </label>
      <label v-if="formWindowPreset === 'CUSTOM'" class="field compact">
        <span>后窗口天数</span>
        <input v-model.number="formCustomAfterDays" data-testid="ux-change-after-days" min="1" type="number" />
      </label>
      <div class="form-actions">
        <button class="ghost-button" data-testid="ux-change-refresh" type="button" @click="refreshHistory">
          刷新历史
        </button>
        <button class="submit-button" data-testid="ux-change-submit" type="submit" :disabled="submitting">
          {{ submitting ? '计算中...' : '保存并计算' }}
        </button>
      </div>
    </form>

    <p v-if="stateMessage" class="notice" :class="{ 'notice--error': state === 'error' }">{{ stateMessage }}</p>

    <div class="content-grid">
      <aside class="history-panel">
        <h4>历史记录</h4>
        <ul v-if="historyItems.length > 0" class="history-list">
          <li v-for="item in historyItems" :key="item.id || item.changeDate">
            <button type="button" class="history-button" @click="emit('select', item.id)">
              <strong>{{ item.changeDate || '未记录时间点' }}</strong>
              <span>{{ windowPresetLabel(item.windowPreset) }}</span>
            </button>
          </li>
        </ul>
        <p v-else class="empty">暂无历史记录</p>
      </aside>

      <div class="detail-panel">
        <div v-if="activeRecord" class="summary-block">
          <div class="summary-row">
            <strong>
              {{ activeRecordProductName }}
              <span v-if="activeRecordCodeHint">{{ activeRecordCodeHint }}</span>
            </strong>
            <span>{{ activeRecord.changeDate }}</span>
          </div>
          <p v-if="activeRecord.summary">{{ activeRecord.summary }}</p>
          <p v-if="activeRecord.notice" class="subtle">{{ activeRecord.notice }}</p>
          <p v-if="windowRange" class="subtle">{{ windowRange }}</p>
        </div>

        <div v-if="activeRecord && activeRecord.items.length > 0" class="table-shell">
          <table>
            <thead>
              <tr>
                <th>UX 标签</th>
                <th>前提及量</th>
                <th>后提及量</th>
                <th>前负面率</th>
                <th>后负面率</th>
                <th>改善率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in activeRecord.items" :key="item.uxSecondaryLabel">
                <td>
                  <strong>{{ item.uxSecondaryLabel }}</strong>
                  <span v-if="item.uxPrimaryLabel">{{ item.uxPrimaryLabel }}</span>
                </td>
                <td>{{ item.beforeMentionCount }}</td>
                <td>{{ item.afterMentionCount }}</td>
                <td>{{ formatPercent(item.beforeNegativeRate) }}</td>
                <td>{{ formatPercent(item.afterNegativeRate) }}</td>
                <td>
                  <span class="delta-pill" :class="{ up: item.improvementRate >= 0, down: item.improvementRate < 0 }">
                    {{ formatSignedPercent(item.improvementRate) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="empty">暂无可展示的 UX 标签变化</p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type {
  ContractState,
  UxChangeComparisonCreatePayload,
  UxChangeComparisonRecord,
  UxChangeComparisonWindowPreset,
} from '../types/domain'

const props = defineProps<{
  productCode: string
  productName?: string
  historyItems: UxChangeComparisonRecord[]
  activeRecord: UxChangeComparisonRecord | null
  state: ContractState
  message?: string
  submitting?: boolean
}>()

const emit = defineEmits<{
  (event: 'create', payload: UxChangeComparisonCreatePayload): void
  (event: 'refresh', productCode: string): void
  (event: 'select', id: string): void
}>()

const formProductCode = ref(props.productCode)
const formChangeDate = ref(new Date().toISOString().slice(0, 10))
const formWindowPreset = ref<UxChangeComparisonWindowPreset>('ONE_MONTH')
const formCustomBeforeDays = ref(30)
const formCustomAfterDays = ref(30)

const activeRecordProductName = computed(() =>
  displayProductName(props.activeRecord?.productName || props.productName, props.activeRecord?.productCode || props.productCode),
)
const activeRecordCodeHint = computed(() =>
  displayCodeHint(props.activeRecord?.productName || props.productName, props.activeRecord?.productCode || props.productCode),
)

watch(
  () => props.productCode,
  (value) => {
    formProductCode.value = value
  },
)

const stateMessage = computed(() => {
  if (props.message?.trim()) {
    return props.message
  }
  if (props.state === 'loading') {
    return '正在加载前后对比。'
  }
  if (props.state === 'empty') {
    return '暂无前后对比结果。'
  }
  if (props.state === 'degraded') {
    return '前后对比结果暂时只返回部分数据。'
  }
  if (props.state === 'error') {
    return '前后对比接口请求失败，请稍后重试。'
  }
  return ''
})

const windowRange = computed(() => {
  const record = props.activeRecord
  if (!record?.beforeWindowStart && !record?.afterWindowStart) {
    return ''
  }
  return `前窗口：${record.beforeWindowStart ?? '-'} 至 ${record.beforeWindowEnd ?? '-'}；后窗口：${record.afterWindowStart ?? '-'} 至 ${record.afterWindowEnd ?? '-'}`
})

function windowPresetLabel(preset: UxChangeComparisonWindowPreset): string {
  if (preset === 'TWO_WEEKS') {
    return '前后两周'
  }
  if (preset === 'THREE_MONTHS') {
    return '前三后三个月'
  }
  if (preset === 'CUSTOM') {
    return '自定义'
  }
  return '前后一个月'
}

function displayProductName(productName?: string, productCode?: string): string {
  const normalizedName = productName?.trim()
  if (normalizedName) {
    return normalizedName
  }
  return productCode?.trim() || '未选择商品'
}

function displayCodeHint(productName?: string, productCode?: string): string {
  const normalizedName = productName?.trim()
  const normalizedCode = productCode?.trim()
  if (!normalizedName || !normalizedCode || normalizedName === normalizedCode) {
    return ''
  }
  return `（${normalizedCode}）`
}

function formatPercent(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}

function formatSignedPercent(value: number): string {
  const sign = value > 0 ? '+' : ''
  return `${sign}${(value * 100).toFixed(1)}%`
}

function submitComparison(): void {
  const payload: UxChangeComparisonCreatePayload = {
    productCode: formProductCode.value.trim(),
    changeDate: formChangeDate.value,
    windowPreset: formWindowPreset.value,
  }
  if (formWindowPreset.value === 'CUSTOM') {
    payload.customBeforeDays = Number(formCustomBeforeDays.value) || 30
    payload.customAfterDays = Number(formCustomAfterDays.value) || 30
  }
  emit('create', payload)
}

function refreshHistory(): void {
  emit('refresh', formProductCode.value.trim())
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
  background: linear-gradient(135deg, rgba(122, 184, 255, 0.07), transparent 34%);
}

.head,
.comparison-form,
.notice,
.content-grid {
  position: relative;
  z-index: var(--z-raised);
}

.title-block {
  display: grid;
  gap: var(--space-2);
}

.eyebrow,
.delta-pill {
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

.eyebrow {
  letter-spacing: 0.08em;
  color: var(--color-accent-secondary);
}

h3,
h4 {
  margin: 0;
  color: var(--color-text-primary);
  line-height: var(--line-height-tight);
}

h3 {
  font-size: var(--font-size-xl);
}

h4 {
  font-size: var(--font-size-md);
}

.comparison-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 11rem 12rem auto;
  gap: var(--space-3);
  align-items: end;
}

.field {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.field.compact {
  max-width: 10rem;
}

.field span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.field input,
.field select {
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

.field input:focus,
.field select:focus {
  outline: none;
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-focus), var(--shadow-inset-soft);
}

.form-actions {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.submit-button,
.ghost-button,
.history-button {
  border-radius: var(--radius-md);
  font: inherit;
  cursor: pointer;
}

.submit-button,
.ghost-button {
  min-height: 2.75rem;
  padding: 0 var(--space-4);
  white-space: nowrap;
}

.submit-button {
  border: 1px solid rgba(102, 224, 194, 0.28);
  background: rgba(102, 224, 194, 0.14);
  color: var(--color-accent-secondary);
  font-weight: 700;
}

.ghost-button {
  border: 1px solid var(--color-border-subtle);
  background: rgba(8, 16, 29, 0.42);
  color: var(--color-text-secondary);
}

.submit-button:disabled {
  cursor: wait;
  opacity: 0.72;
}

.notice,
.empty {
  margin: 0;
  padding: var(--space-3);
  border: 1px dashed var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 15, 27, 0.42);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.notice--error {
  border-color: rgba(255, 123, 133, 0.28);
  color: var(--color-semantic-down);
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(14rem, 0.28fr) minmax(0, 1fr);
  gap: var(--space-4);
}

.history-panel,
.detail-panel {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.history-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--space-2);
}

.history-button {
  width: 100%;
  display: grid;
  gap: var(--space-1);
  text-align: left;
  border: 1px solid var(--color-border-subtle);
  background: rgba(8, 16, 29, 0.5);
  color: var(--color-text-secondary);
  padding: var(--space-3);
}

.history-button strong {
  color: var(--color-text-primary);
}

.summary-block {
  display: grid;
  gap: var(--space-2);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  background: rgba(8, 16, 29, 0.48);
}

.summary-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  justify-content: space-between;
}

.summary-row strong span {
  color: var(--color-text-muted);
  font-weight: 500;
}

.summary-block p,
.subtle {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.subtle {
  color: var(--color-text-muted);
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
  min-width: 720px;
  border-collapse: separate;
  border-spacing: 0;
}

th,
td {
  text-align: left;
  padding: var(--space-3);
  border-bottom: 1px solid var(--color-border-subtle);
}

th {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-text-muted);
  background: rgba(11, 23, 41, 0.92);
}

tbody tr:last-child td {
  border-bottom: none;
}

td {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

td strong,
td span {
  display: block;
}

td strong {
  color: var(--color-text-primary);
}

.delta-pill {
  font-weight: 700;
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

@media (max-width: 960px) {
  .comparison-form,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .form-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .panel {
    padding: var(--space-3);
  }

  .form-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
