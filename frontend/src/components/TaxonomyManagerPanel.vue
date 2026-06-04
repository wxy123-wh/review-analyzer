<template>
  <section class="panel taxonomy-panel" data-testid="taxonomy-manager">
    <header class="head">
      <div class="title-block">
        <h3>taxonomy</h3>
        <p class="support">维护可复用的商品品类和 UX 标签体系。</p>
      </div>
      <button type="button" class="primary-btn" data-testid="taxonomy-new" @click="startNewTaxonomy">新增 taxonomy</button>
    </header>

    <div class="taxonomy-body">
      <aside class="taxonomy-list">
        <div class="section-head">
          <h4>已保存</h4>
          <button type="button" class="secondary-btn compact" :disabled="loading" @click="loadTaxonomies">
            {{ loading ? '读取中' : '刷新' }}
          </button>
        </div>
        <div class="taxonomy-list-scroll scroll-region">
          <button
            v-for="item in taxonomies"
            :key="taxonomyOptionKey(item)"
            type="button"
            class="taxonomy-option"
            :class="{ 'taxonomy-option--active': taxonomyOptionKey(item) === selectedTaxonomyKey }"
            data-testid="taxonomy-option"
            @click="selectTaxonomy(item)"
          >
            <strong>{{ item.name || item.category }}</strong>
            <span>{{ item.category }} · #{{ item.taxonomyId ?? '草稿' }}</span>
          </button>
          <p v-if="taxonomies.length === 0" class="empty-reviews">暂无 taxonomy。</p>
        </div>
      </aside>

      <section class="taxonomy-editor">
        <div class="editor-form">
          <label class="field">
            <span>taxonomy 名称</span>
            <input v-model.trim="formName" data-testid="taxonomy-name" type="text" placeholder="蓝牙耳机 UX 标签" />
          </label>
          <label class="field">
            <span>商品品类</span>
            <input v-model.trim="formCategory" data-testid="taxonomy-category" type="text" placeholder="bluetooth-headset" />
          </label>
          <button type="button" class="primary-btn" data-testid="taxonomy-save" :disabled="saving" @click="saveTaxonomy">
            {{ saving ? '保存中' : '保存 taxonomy' }}
          </button>
        </div>

        <div class="quick-add">
          <label class="field">
            <span>一级标签</span>
            <input v-model.trim="newPrimaryLabel" data-testid="taxonomy-new-primary" type="text" placeholder="产品硬件" />
          </label>
          <label class="field">
            <span>二级标签</span>
            <input v-model.trim="newSecondaryLabel" data-testid="taxonomy-new-secondary" type="text" placeholder="连接与稳定性" />
          </label>
          <button type="button" class="secondary-btn" data-testid="taxonomy-add-label" :disabled="!canAddLabel" @click="addLabel">
            新增标签
          </button>
        </div>

        <p v-if="message" class="notice" :class="{ 'notice--error': messageTone === 'error' }">{{ message }}</p>

        <UxTaxonomyTree v-model:labels="labels" />
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { DEFAULT_PRODUCT_CODE, fetchTaxonomies, saveTaxonomyDefinition } from '../api/client'
import type { ProductTaxonomyResponse, UxLabelOption } from '../types/domain'
import UxTaxonomyTree from './UxTaxonomyTree.vue'

const taxonomies = ref<ProductTaxonomyResponse[]>([])
const selectedTaxonomyKey = ref('')
const formTaxonomyId = ref<number | undefined>(undefined)
const formName = ref('')
const formCategory = ref('general-product')
const labels = ref<UxLabelOption[]>([])
const newPrimaryLabel = ref('')
const newSecondaryLabel = ref('')
const loading = ref(false)
const saving = ref(false)
const message = ref('')
const messageTone = ref<'info' | 'error'>('info')

const canAddLabel = computed(() => newPrimaryLabel.value.length > 0 && newSecondaryLabel.value.length > 0)

function showMessage(text: string, tone: 'info' | 'error' = 'info'): void {
  message.value = text
  messageTone.value = tone
}

function taxonomyOptionKey(option: ProductTaxonomyResponse): string {
  if (option.taxonomyId) {
    return `id:${option.taxonomyId}`
  }
  return `category:${option.category || 'general-product'}`
}

function buildLabelId(primaryLabel: string, secondaryLabel: string, index: number): string {
  const normalizedPrimary = primaryLabel.trim().toLowerCase().replace(/\s+/g, '-')
  const normalizedSecondary = secondaryLabel.trim().toLowerCase().replace(/\s+/g, '-')
  return `ux-${normalizedPrimary || 'primary'}-${normalizedSecondary || 'secondary'}-${Date.now()}-${index}`
}

function selectTaxonomy(taxonomy: ProductTaxonomyResponse): void {
  selectedTaxonomyKey.value = taxonomyOptionKey(taxonomy)
  formTaxonomyId.value = taxonomy.taxonomyId
  formName.value = taxonomy.name || ''
  formCategory.value = taxonomy.category || 'general-product'
  labels.value = taxonomy.labels.map((label) => ({ ...label }))
  showMessage(`正在编辑 ${taxonomy.name || taxonomy.category}。`)
}

function startNewTaxonomy(): void {
  selectedTaxonomyKey.value = 'new'
  formTaxonomyId.value = undefined
  formName.value = ''
  formCategory.value = 'general-product'
  labels.value = []
  newPrimaryLabel.value = ''
  newSecondaryLabel.value = ''
  showMessage('已切换到新 taxonomy 草稿。')
}

function addLabel(): void {
  const primaryLabel = newPrimaryLabel.value.trim()
  const secondaryLabel = newSecondaryLabel.value.trim()
  if (!primaryLabel || !secondaryLabel) {
    return
  }
  labels.value = [
    ...labels.value,
    {
      id: buildLabelId(primaryLabel, secondaryLabel, labels.value.length),
      uxPrimaryLabel: primaryLabel,
      uxSecondaryLabel: secondaryLabel,
      enabled: true,
    },
  ]
  newSecondaryLabel.value = ''
}

function normalizeLabels(): UxLabelOption[] {
  return labels.value
    .map((label, index) => ({
      ...label,
      id: label.id || buildLabelId(label.uxPrimaryLabel, label.uxSecondaryLabel, index),
      uxPrimaryLabel: label.uxPrimaryLabel.trim(),
      uxSecondaryLabel: label.uxSecondaryLabel.trim(),
    }))
    .filter((label) => label.uxPrimaryLabel.length > 0 && label.uxSecondaryLabel.length > 0)
}

async function loadTaxonomies(): Promise<void> {
  loading.value = true
  try {
    const response = await fetchTaxonomies(DEFAULT_PRODUCT_CODE)
    taxonomies.value = response
    if (response.length > 0 && !selectedTaxonomyKey.value) {
      selectTaxonomy(response[0])
    }
  } catch {
    showMessage('读取 taxonomy 失败，请检查后端服务。', 'error')
  } finally {
    loading.value = false
  }
}

async function saveTaxonomy(): Promise<void> {
  const normalizedName = formName.value.trim()
  const normalizedCategory = formCategory.value.trim()
  const normalizedLabels = normalizeLabels()
  labels.value = normalizedLabels
  if (!normalizedName || !normalizedCategory) {
    showMessage('请填写 taxonomy 名称和商品品类。', 'error')
    return
  }
  if (normalizedLabels.length === 0) {
    showMessage('请至少新增一个 UX 标签。', 'error')
    return
  }

  saving.value = true
  try {
    const saved = await saveTaxonomyDefinition({
      taxonomyId: formTaxonomyId.value,
      name: normalizedName,
      productCode: DEFAULT_PRODUCT_CODE,
      category: normalizedCategory,
      labels: normalizedLabels,
    })
    taxonomies.value = [
      saved,
      ...taxonomies.value.filter((item) => taxonomyOptionKey(item) !== taxonomyOptionKey(saved)),
    ]
    selectTaxonomy(saved)
    showMessage('taxonomy 已保存。回到数据接入页后，商品品类下拉框可以选择它。')
  } catch {
    showMessage('保存 taxonomy 失败，请检查后端接口。', 'error')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadTaxonomies()
})
</script>

<style scoped>
.taxonomy-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--space-3);
}

.head,
.section-head,
.editor-form,
.quick-add {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.head,
.section-head {
  justify-content: space-between;
}

.title-block {
  display: grid;
  gap: var(--space-1);
}

h3,
h4 {
  margin: 0;
  color: var(--color-text-primary);
}

.support {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.taxonomy-body {
  display: grid;
  grid-template-columns: minmax(14rem, 0.34fr) minmax(0, 1fr);
  gap: var(--space-3);
  min-height: 0;
}

.taxonomy-list,
.taxonomy-editor {
  display: grid;
  gap: var(--space-3);
  min-height: 0;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  background: var(--color-surface-1);
}

.taxonomy-list {
  grid-template-rows: auto minmax(0, 1fr);
}

.taxonomy-editor {
  grid-template-rows: auto auto auto minmax(0, 1fr);
}

.scroll-region,
.taxonomy-list-scroll {
  min-height: 0;
  overflow: auto;
}

.taxonomy-list-scroll {
  display: grid;
  align-content: start;
  gap: var(--space-2);
}

.taxonomy-option {
  display: grid;
  gap: var(--space-1);
  width: 100%;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  padding: var(--space-2);
  background: var(--color-surface-1);
  color: var(--color-text-secondary);
  text-align: left;
  cursor: pointer;
}

.taxonomy-option strong {
  color: var(--color-text-primary);
}

.taxonomy-option span {
  font-size: var(--font-size-xs);
}

.taxonomy-option--active {
  border-color: var(--color-border-strong);
  background: var(--color-accent-soft);
}

.field {
  display: grid;
  gap: var(--space-1);
  min-width: min(100%, 12rem);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.field input {
  width: 100%;
  min-width: 0;
  min-height: 2.25rem;
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-md);
  padding: var(--space-2) var(--space-3);
  background: var(--color-surface-input);
  color: var(--color-text-primary);
  font: inherit;
}

.primary-btn,
.secondary-btn {
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-sm);
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
}

.primary-btn {
  background: #111827;
  color: #ffffff;
}

.secondary-btn {
  background: var(--color-surface-1);
  color: var(--color-text-primary);
}

.compact {
  padding-inline: var(--space-2);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.notice {
  margin: 0;
  border: 1px dashed var(--color-border-subtle);
  border-radius: var(--radius-md);
  padding: var(--space-2) var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.notice--error {
  border-color: rgba(185, 28, 28, 0.28);
  color: var(--color-semantic-down);
}

.empty-reviews {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

@media (max-width: 960px) {
  .taxonomy-body,
  .editor-form,
  .quick-add {
    grid-template-columns: 1fr;
  }

  .taxonomy-body {
    display: grid;
  }

  .head,
  .section-head,
  .editor-form,
  .quick-add {
    align-items: stretch;
  }
}
</style>
