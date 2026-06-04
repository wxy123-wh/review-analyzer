<template>
  <section class="ux-tree" aria-label="UX 标签树状图">
    <header class="ux-tree__head">
      <div>
        <h4>UX 标签树</h4>
        <p>中心节点按一级标签展开，二级标签作为可编辑叶子节点。</p>
      </div>
      <span class="ux-tree__count">{{ enabledCount }} / {{ labels.length }} 启用</span>
    </header>

    <div v-if="groups.length === 0" class="ux-tree__empty" data-testid="ux-tree-empty">
      <strong>暂无 UX 标签</strong>
      <p>读取或新增标签后，这里会按一级/二级层级展示。</p>
      <div v-if="!readonly" class="ux-tree__new-root">
        <input v-model.trim="newPrimaryLabel" data-testid="ux-tree-new-primary" type="text" placeholder="一级标签" />
        <input v-model.trim="newSecondaryLabel" data-testid="ux-tree-new-secondary" type="text" placeholder="二级标签" />
        <button type="button" :disabled="!canAddNewRoot" @click="addRootLabel">新增</button>
      </div>
    </div>

    <div v-else class="ux-tree__scroll">
      <div class="ux-tree__map">
        <div class="ux-tree__center">
          <span>UX 标签</span>
          <strong>{{ groups.length }} 个一级</strong>
        </div>

        <div class="ux-tree__groups">
          <article v-for="group in groups" :key="group.key" class="ux-tree__group" data-testid="ux-tree-primary-group">
            <div class="ux-tree__branch" aria-hidden="true"></div>
            <div class="ux-tree__primary">
              <span>{{ group.primaryLabel }}</span>
              <strong>{{ group.enabledCount }} / {{ group.labels.length }}</strong>
            </div>

            <div class="ux-tree__secondary-list">
              <div
                v-for="label in group.labels"
                :key="label.id"
                class="ux-tree__secondary"
                :class="{ 'ux-tree__secondary--disabled': !label.enabled }"
                data-testid="ux-tree-secondary-node"
              >
                <label v-if="!readonly" class="ux-tree__toggle">
                  <input
                    :checked="label.enabled"
                    data-testid="ux-tree-enabled-toggle"
                    type="checkbox"
                    @change="toggleLabel(label.id)"
                  />
                  <span>{{ label.enabled ? '启用' : '停用' }}</span>
                </label>
                <span v-else class="ux-tree__status">{{ label.enabled ? '启用' : '停用' }}</span>
                <div class="ux-tree__label-text">
                  <strong>{{ label.uxSecondaryLabel || '未命名二级标签' }}</strong>
                  <small v-if="label.description">{{ label.description }}</small>
                </div>
                <button
                  v-if="!readonly"
                  type="button"
                  class="ux-tree__delete"
                  :aria-label="`删除 ${label.uxSecondaryLabel || '二级标签'}`"
                  data-testid="ux-tree-delete"
                  @click="deleteLabel(label.id)"
                >
                  删除
                </button>
              </div>

              <form v-if="!readonly" class="ux-tree__add" @submit.prevent="addSecondaryLabel(group.primaryLabel)">
                <input
                  v-model.trim="drafts[group.primaryLabel]"
                  :data-testid="`ux-tree-add-input-${group.key}`"
                  type="text"
                  :placeholder="`新增${group.primaryLabel}下的二级标签`"
                />
                <button type="submit" :disabled="!drafts[group.primaryLabel]?.trim()">新增</button>
              </form>
            </div>
          </article>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'

import type { UxLabelOption } from '../types/domain'

type LabelGroup = {
  key: string
  primaryLabel: string
  labels: UxLabelOption[]
  enabledCount: number
}

const props = defineProps<{
  labels: UxLabelOption[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:labels', labels: UxLabelOption[]): void
}>()

const drafts = reactive<Record<string, string>>({})
const newPrimaryLabel = ref('')
const newSecondaryLabel = ref('')

const groups = computed<LabelGroup[]>(() => {
  const grouped = new Map<string, UxLabelOption[]>()
  for (const label of props.labels) {
    const primaryLabel = normalizePrimaryLabel(label.uxPrimaryLabel)
    grouped.set(primaryLabel, [...(grouped.get(primaryLabel) ?? []), label])
  }
  return Array.from(grouped.entries()).map(([primaryLabel, labels]) => ({
    key: toDomKey(primaryLabel),
    primaryLabel,
    labels,
    enabledCount: labels.filter((label) => label.enabled).length,
  }))
})

const readonly = computed(() => props.readonly === true)
const enabledCount = computed(() => props.labels.filter((label) => label.enabled).length)
const canAddNewRoot = computed(() => newPrimaryLabel.value.length > 0 && newSecondaryLabel.value.length > 0)

function normalizePrimaryLabel(primaryLabel: string): string {
  return primaryLabel.trim() || '未命名一级标签'
}

function toDomKey(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5-]/g, '') || 'unnamed'
}

function emitLabels(labels: UxLabelOption[]): void {
  emit('update:labels', labels.map((label) => ({ ...label })))
}

function buildLabelId(primaryLabel: string, secondaryLabel: string): string {
  return `ux-${toDomKey(primaryLabel)}-${toDomKey(secondaryLabel)}-${Date.now()}-${props.labels.length}`
}

function toggleLabel(labelId: string): void {
  emitLabels(
    props.labels.map((label) =>
      label.id === labelId
        ? {
            ...label,
            enabled: !label.enabled,
          }
        : label,
    ),
  )
}

function addSecondaryLabel(primaryLabel: string): void {
  const secondaryLabel = drafts[primaryLabel]?.trim()
  if (!secondaryLabel) {
    return
  }
  drafts[primaryLabel] = ''
  emitLabels([
    ...props.labels,
    {
      id: buildLabelId(primaryLabel, secondaryLabel),
      uxPrimaryLabel: primaryLabel,
      uxSecondaryLabel: secondaryLabel,
      enabled: true,
    },
  ])
}

function addRootLabel(): void {
  const primaryLabel = newPrimaryLabel.value.trim()
  const secondaryLabel = newSecondaryLabel.value.trim()
  if (!primaryLabel || !secondaryLabel) {
    return
  }
  newPrimaryLabel.value = ''
  newSecondaryLabel.value = ''
  emitLabels([
    ...props.labels,
    {
      id: buildLabelId(primaryLabel, secondaryLabel),
      uxPrimaryLabel: primaryLabel,
      uxSecondaryLabel: secondaryLabel,
      enabled: true,
    },
  ])
}

function deleteLabel(labelId: string): void {
  emitLabels(props.labels.filter((label) => label.id !== labelId))
}
</script>

<style scoped>
.ux-tree {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 0.875rem;
  min-height: 18rem;
  max-height: 100%;
  overflow: hidden;
  border: 1px solid var(--color-border-default, #d8e0ea);
  border-radius: var(--radius-md, 8px);
  background: var(--color-surface-1, #ffffff);
  color: var(--color-text-primary, #172033);
}

.ux-tree__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1rem 0;
}

.ux-tree__head h4,
.ux-tree__head p {
  margin: 0;
}

.ux-tree__head h4 {
  font-size: 1rem;
  line-height: 1.3;
}

.ux-tree__head p {
  margin-top: 0.25rem;
  color: var(--color-text-secondary, #5d6b82);
  font-size: 0.8125rem;
  line-height: 1.45;
}

.ux-tree__count {
  flex: 0 0 auto;
  border: 1px solid var(--color-border-default, #d8e0ea);
  border-radius: 999px;
  padding: 0.35rem 0.625rem;
  color: var(--color-accent-primary, #246bfe);
  font-size: 0.75rem;
  font-weight: 700;
}

.ux-tree__scroll {
  min-height: 0;
  overflow: auto;
  padding: 0 1rem 1rem;
}

.ux-tree__map {
  position: relative;
  display: grid;
  grid-template-columns: minmax(8rem, 10rem) minmax(22rem, 1fr);
  gap: 1.25rem;
  align-items: start;
  min-width: 40rem;
}

.ux-tree__center {
  position: sticky;
  top: 0.25rem;
  display: grid;
  gap: 0.25rem;
  justify-items: center;
  border: 1px solid rgba(36, 107, 254, 0.24);
  border-radius: 8px;
  padding: 1rem 0.75rem;
  background: linear-gradient(180deg, rgba(36, 107, 254, 0.08), rgba(19, 184, 138, 0.06));
  box-shadow: 0 12px 32px rgba(23, 32, 51, 0.08);
}

.ux-tree__center span,
.ux-tree__primary span {
  color: var(--color-text-primary, #172033);
  font-weight: 800;
}

.ux-tree__center strong,
.ux-tree__primary strong {
  color: var(--color-text-secondary, #5d6b82);
  font-size: 0.75rem;
}

.ux-tree__groups {
  display: grid;
  gap: 1rem;
}

.ux-tree__group {
  position: relative;
  display: grid;
  grid-template-columns: minmax(8rem, 10rem) minmax(0, 1fr);
  gap: 0.875rem;
  align-items: start;
}

.ux-tree__branch {
  position: absolute;
  top: 1.25rem;
  left: -1.25rem;
  width: 1.25rem;
  border-top: 1px solid var(--color-border-default, #d8e0ea);
}

.ux-tree__primary {
  display: grid;
  gap: 0.25rem;
  border: 1px solid var(--color-border-default, #d8e0ea);
  border-radius: 8px;
  padding: 0.75rem;
  background: #f7f9fc;
}

.ux-tree__secondary-list {
  display: grid;
  gap: 0.625rem;
}

.ux-tree__secondary {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 0.75rem;
  align-items: center;
  border: 1px solid var(--color-border-default, #d8e0ea);
  border-radius: 8px;
  padding: 0.625rem;
  background: #ffffff;
}

.ux-tree__secondary::before {
  position: absolute;
  top: 50%;
  left: -0.875rem;
  width: 0.875rem;
  border-top: 1px solid var(--color-border-default, #d8e0ea);
  content: '';
}

.ux-tree__secondary--disabled {
  background: #f8fafc;
  color: var(--color-text-muted, #7b8798);
  opacity: 0.72;
}

.ux-tree__toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  color: var(--color-text-secondary, #5d6b82);
  font-size: 0.75rem;
  white-space: nowrap;
}

.ux-tree__status {
  border: 1px solid var(--color-border-default, #d8e0ea);
  border-radius: 999px;
  padding: 0.25rem 0.5rem;
  color: var(--color-text-secondary, #5d6b82);
  font-size: 0.75rem;
  white-space: nowrap;
}

.ux-tree__toggle input {
  width: auto;
  margin: 0;
}

.ux-tree__label-text {
  display: grid;
  gap: 0.125rem;
  min-width: 0;
}

.ux-tree__label-text strong {
  overflow-wrap: anywhere;
  font-size: 0.875rem;
  line-height: 1.35;
}

.ux-tree__label-text small {
  color: var(--color-text-muted, #7b8798);
  font-size: 0.75rem;
  line-height: 1.35;
}

.ux-tree__delete,
.ux-tree__add button,
.ux-tree__new-root button {
  border: 1px solid var(--color-border-default, #d8e0ea);
  border-radius: 999px;
  background: #ffffff;
  color: var(--color-text-primary, #172033);
  cursor: pointer;
  font: inherit;
  font-size: 0.75rem;
  font-weight: 700;
  line-height: 1;
  padding: 0.5rem 0.625rem;
}

.ux-tree__delete {
  color: #b42318;
}

.ux-tree__add,
.ux-tree__new-root {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.5rem;
  align-items: center;
}

.ux-tree__new-root {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  width: min(100%, 42rem);
}

.ux-tree__add input,
.ux-tree__new-root input {
  min-width: 0;
  border: 1px solid var(--color-border-default, #d8e0ea);
  border-radius: 8px;
  padding: 0.625rem 0.75rem;
  background: #ffffff;
  color: inherit;
  font: inherit;
  font-size: 0.8125rem;
}

.ux-tree__add input:focus-visible,
.ux-tree__new-root input:focus-visible,
.ux-tree__toggle input:focus-visible,
.ux-tree__delete:focus-visible,
.ux-tree__add button:focus-visible,
.ux-tree__new-root button:focus-visible {
  outline: 2px solid rgba(36, 107, 254, 0.32);
  outline-offset: 2px;
}

.ux-tree__add button:disabled,
.ux-tree__new-root button:disabled {
  cursor: not-allowed;
  opacity: 0.52;
}

.ux-tree__empty {
  display: grid;
  gap: 0.75rem;
  place-items: center;
  min-height: 14rem;
  margin: 0 1rem 1rem;
  border: 1px dashed var(--color-border-default, #d8e0ea);
  border-radius: 8px;
  padding: 1.25rem;
  background: #f8fafc;
  text-align: center;
}

.ux-tree__empty strong {
  font-size: 1rem;
}

.ux-tree__empty p {
  margin: 0;
  color: var(--color-text-secondary, #5d6b82);
  font-size: 0.8125rem;
}

@media (max-width: 720px) {
  .ux-tree__head,
  .ux-tree__new-root {
    grid-template-columns: 1fr;
  }

  .ux-tree__head {
    display: grid;
  }

  .ux-tree__map {
    min-width: 0;
    grid-template-columns: 1fr;
  }

  .ux-tree__center {
    position: static;
  }

  .ux-tree__group,
  .ux-tree__secondary,
  .ux-tree__add {
    grid-template-columns: 1fr;
  }

  .ux-tree__branch,
  .ux-tree__secondary::before {
    display: none;
  }
}
</style>
