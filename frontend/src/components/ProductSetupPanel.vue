<template>
  <section class="panel">
    <header class="head">
      <div class="title-block">
        <span class="eyebrow">Product intake</span>
        <h3>商品采集与标签配置</h3>
        <p class="support">输入商品链接，绑定当前商品的 UX 标签组合，再启动评论采集和分析。</p>
      </div>
    </header>

    <div class="form-grid">
      <label class="field">
        <span>商品链接</span>
        <input v-model.trim="productUrl" data-testid="setup-product-url" type="url" placeholder="https://item.jd.com/..." />
      </label>
      <label class="field">
        <span>productCode</span>
        <input v-model.trim="productCode" data-testid="setup-product-code" type="text" placeholder="jd-100127936932" />
      </label>
      <label class="field">
        <span>商品品类</span>
        <input v-model.trim="category" data-testid="setup-category" type="text" placeholder="general-product" />
      </label>
    </div>

    <div class="toolbar">
      <button type="button" class="secondary-btn" :disabled="busy" @click="loadTaxonomy">读取标签</button>
      <button type="button" class="primary-btn" :disabled="busy || labels.length === 0" @click="saveTaxonomy">绑定标签</button>
      <button type="button" class="primary-btn" :disabled="busy || !canStartCrawl" @click="startProductCrawl">启动采集</button>
      <button type="button" class="secondary-btn" :disabled="busy || !crawlJob?.jobId" @click="refreshCrawlJob">刷新任务</button>
      <button type="button" class="secondary-btn" :disabled="busy || !canStartAnalysis" @click="startProductAnalysis">启动分析</button>
    </div>

    <p v-if="message" class="notice" :class="{ 'notice--error': messageTone === 'error' }">{{ message }}</p>

    <div class="content-grid">
      <article class="label-editor">
        <div class="section-head">
          <h4>UX 标签组合</h4>
          <button type="button" class="secondary-btn compact" @click="addLabel">新增标签</button>
        </div>

        <ul class="label-list">
          <li v-for="(label, index) in labels" :key="label.id" class="label-row">
            <label class="toggle">
              <input v-model="label.enabled" type="checkbox" />
              <span>{{ label.enabled ? '启用' : '停用' }}</span>
            </label>
            <input v-model.trim="label.uxPrimaryLabel" :data-testid="`setup-primary-${index}`" type="text" aria-label="UX 一级标签" />
            <input v-model.trim="label.uxSecondaryLabel" :data-testid="`setup-secondary-${index}`" type="text" aria-label="UX 二级标签" />
            <button type="button" class="icon-btn" :aria-label="`删除 ${label.uxSecondaryLabel || '标签'}`" @click="removeLabel(index)">×</button>
          </li>
        </ul>
      </article>

      <aside class="status-panel">
        <h4>任务状态</h4>
        <dl>
          <div>
            <dt>绑定商品</dt>
            <dd>{{ taxonomy?.productCode || productCode || '未填写' }}</dd>
          </div>
          <div>
            <dt>标签数量</dt>
            <dd>{{ enabledLabelCount }} / {{ labels.length }}</dd>
          </div>
          <div>
            <dt>taxonomyId</dt>
            <dd>{{ taxonomy?.taxonomyId ?? '待绑定' }}</dd>
          </div>
          <div>
            <dt>采集任务</dt>
            <dd>{{ crawlJob?.status || '未启动' }}</dd>
          </div>
          <div>
            <dt>已采集评论</dt>
            <dd>{{ crawlJob?.fetchedCount ?? 0 }}</dd>
          </div>
          <div>
            <dt>分析任务</dt>
            <dd>{{ analysisJob?.status || crawlJob?.analysisHandoffStatus || '待采集完成' }}</dd>
          </div>
        </dl>
        <p v-if="crawlJob?.analysisHandoffNote" class="handoff">{{ crawlJob.analysisHandoffNote }}</p>
        <p v-if="analysisJob?.errorMessage" class="handoff handoff--error">{{ analysisJob.errorMessage }}</p>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  DEFAULT_PRODUCT_CODE,
  fetchCrawlJob,
  fetchProductTaxonomy,
  saveProductTaxonomy,
  startAnalysis,
  startCrawl,
} from '../api/client'
import type { AnalysisJobResponse, CrawlJobResponse, ProductTaxonomyResponse, UxLabelOption } from '../types/domain'

const productUrl = ref('https://item.jd.com/100127936932.html')
const productCode = ref(DEFAULT_PRODUCT_CODE)
const category = ref('general-product')
const labels = ref<UxLabelOption[]>([])
const taxonomy = ref<ProductTaxonomyResponse | null>(null)
const crawlJob = ref<CrawlJobResponse | null>(null)
const analysisJob = ref<AnalysisJobResponse | null>(null)
const busy = ref(false)
const message = ref('')
const messageTone = ref<'info' | 'error'>('info')

const enabledLabelCount = computed(() => labels.value.filter((label) => label.enabled).length)
const canStartCrawl = computed(() => productUrl.value.length > 0 && productCode.value.length > 0 && enabledLabelCount.value > 0)
const canStartAnalysis = computed(() => {
  const handoff = crawlJob.value?.analysisHandoffStatus?.toUpperCase()
  return productCode.value.length > 0 && (!crawlJob.value || crawlJob.value.status === 'SUCCEEDED' || handoff === 'READY_FOR_ANALYSIS')
})

function showMessage(text: string, tone: 'info' | 'error' = 'info'): void {
  message.value = text
  messageTone.value = tone
}

function buildLabelId(index: number): string {
  return `ux-label-${Date.now()}-${index}`
}

function addLabel(): void {
  labels.value = [
    ...labels.value,
    {
      id: buildLabelId(labels.value.length),
      uxPrimaryLabel: '产品体验',
      uxSecondaryLabel: '新的体验标签',
      enabled: true,
    },
  ]
}

function removeLabel(index: number): void {
  labels.value = labels.value.filter((_, currentIndex) => currentIndex !== index)
}

async function runTask(task: () => Promise<void>): Promise<void> {
  busy.value = true
  try {
    await task()
  } catch {
    showMessage('请求失败，请检查后端接口是否已经启动。', 'error')
  } finally {
    busy.value = false
  }
}

async function loadTaxonomy(): Promise<void> {
  await runTask(async () => {
    const response = await fetchProductTaxonomy(productCode.value, category.value || 'general-product')
    taxonomy.value = response
    labels.value = response.labels.map((label) => ({ ...label }))
    showMessage(response.notice || '已读取当前商品的 UX 标签组合。')
  })
}

async function saveTaxonomyConfig(): Promise<void> {
  const normalizedLabels = labels.value
    .map((label, index) => ({
      ...label,
      id: label.id || buildLabelId(index),
      uxPrimaryLabel: label.uxPrimaryLabel.trim(),
      uxSecondaryLabel: label.uxSecondaryLabel.trim(),
    }))
    .filter((label) => label.uxPrimaryLabel.length > 0 && label.uxSecondaryLabel.length > 0)
  labels.value = normalizedLabels
  if (normalizedLabels.length === 0) {
    showMessage('请至少保留一个有效 UX 标签。', 'error')
    return
  }
  const response = await saveProductTaxonomy({
    taxonomyId: taxonomy.value?.taxonomyId,
    name: taxonomy.value?.name,
    productCode: productCode.value,
    category: category.value || 'general-product',
    labels: normalizedLabels,
  })
  taxonomy.value = response
  labels.value = response.labels.map((label) => ({ ...label }))
  showMessage(response.notice || 'UX 标签已经绑定到当前商品。')
}

async function saveTaxonomy(): Promise<void> {
  await runTask(saveTaxonomyConfig)
}

async function startProductCrawl(): Promise<void> {
  await runTask(async () => {
    await saveTaxonomyConfig()
    if (messageTone.value === 'error') {
      return
    }
    crawlJob.value = await startCrawl({
      productUrl: productUrl.value,
      productCode: productCode.value,
      taxonomyId: taxonomy.value?.taxonomyId,
    })
    showMessage('采集任务已启动；如页面出现验证，请人工处理后刷新任务状态。')
  })
}

async function refreshCrawlJob(): Promise<void> {
  if (!crawlJob.value?.jobId) {
    return
  }
  await runTask(async () => {
    crawlJob.value = await fetchCrawlJob(crawlJob.value?.jobId ?? '', productCode.value)
    showMessage('采集任务状态已刷新。')
  })
}

async function startProductAnalysis(): Promise<void> {
  await runTask(async () => {
    analysisJob.value = await startAnalysis(productCode.value)
    showMessage(analysisJob.value.errorMessage || '分析任务已启动，完成后可查看问题、趋势、词云和卖点。')
  })
}

onMounted(() => {
  void loadTaxonomy()
})
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
.form-grid,
.toolbar,
.notice,
.content-grid {
  position: relative;
  z-index: var(--z-raised);
}

.title-block,
.label-editor,
.status-panel {
  display: grid;
  gap: var(--space-3);
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 1.75rem;
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-pill);
  background: var(--color-surface-overlay);
  color: var(--color-accent-primary);
  box-shadow: var(--shadow-inset-soft);
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
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
  font-size: var(--font-size-lg);
}

.support,
.notice,
.handoff {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(16rem, 2fr) minmax(12rem, 1fr) minmax(12rem, 1fr);
  gap: var(--space-3);
}

.field {
  display: grid;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

input {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-md);
  background: rgba(8, 16, 29, 0.72);
  color: var(--color-text-primary);
  padding: var(--space-2) var(--space-3);
  font: inherit;
  box-shadow: var(--shadow-inset-soft);
}

input:focus-visible {
  outline: none;
  box-shadow: var(--shadow-focus);
}

.toolbar,
.section-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.section-head {
  justify-content: space-between;
}

.primary-btn,
.secondary-btn,
.icon-btn {
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  color: var(--color-text-primary);
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-sm);
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  box-shadow: var(--shadow-inset-soft);
}

.primary-btn {
  background: linear-gradient(135deg, rgba(122, 184, 255, 0.2), rgba(102, 224, 194, 0.18));
}

.secondary-btn,
.icon-btn {
  background: rgba(8, 16, 29, 0.72);
}

.compact {
  padding-inline: var(--space-2);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.notice {
  padding: var(--space-3);
  border: 1px dashed var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 15, 27, 0.42);
}

.notice--error,
.handoff--error {
  border-color: rgba(255, 123, 133, 0.28);
  color: var(--color-semantic-down);
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(16rem, 0.9fr);
  gap: var(--space-4);
  align-items: start;
}

.label-editor,
.status-panel {
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  background: rgba(8, 16, 29, 0.56);
  box-shadow: var(--shadow-inset-soft);
}

.label-list {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.label-row {
  display: grid;
  grid-template-columns: auto minmax(8rem, 1fr) minmax(10rem, 1.2fr) auto;
  gap: var(--space-2);
  align-items: center;
}

.toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.toggle input {
  width: auto;
}

.icon-btn {
  width: 2rem;
  height: 2rem;
  padding: 0;
  color: var(--color-text-secondary);
}

dl {
  display: grid;
  gap: var(--space-3);
  margin: 0;
}

dl div {
  display: grid;
  gap: var(--space-1);
}

dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

dd {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

@media (max-width: 960px) {
  .form-grid,
  .content-grid,
  .label-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .panel,
  .label-editor,
  .status-panel {
    padding: var(--space-3);
  }
}
</style>
