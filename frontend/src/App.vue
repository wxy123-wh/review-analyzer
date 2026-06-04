<template>
  <LoginGate
    v-if="!isAuthenticated"
    :expected-username="internalAccessUsername"
    :expected-password="internalAccessPassword"
    :display-name="internalAccessDisplayName"
    @enter="handleLogin"
  />

  <AppShellFrame v-else>
    <template #sidebar>
      <AppShellSidebar :items="modules" :active-module="activeModule" @select="activateModule($event as ModuleId)" />
    </template>

    <div class="content-stack">
      <section class="current-product-bar" data-testid="current-product-bar" data-motion-reveal style="--motion-delay: 80ms">
        <div>
          <span>当前展示商品</span>
          <strong>{{ currentProductDisplayName }}</strong>
        </div>
        <small>{{ currentProductMeta }}</small>
      </section>

      <section class="module-card" data-motion-reveal style="--motion-delay: 120ms">
        <ProductSetupPanel
          v-if="activeModule === 'product-setup'"
          @analysis-ready="handleProductAnalysisReady"
          @product-selected="handleProductSelected"
        />
        <TaxonomyManagerPanel v-else-if="activeModule === 'taxonomy'" />
        <IssueTable v-else-if="activeModule === 'issues'" :items="issues" :state="issueState" :message="issueMessage" />
        <PositiveInsightPanel
          v-else-if="activeModule === 'positive-insights'"
          :items="positiveInsights"
          :state="positiveInsightState"
          :message="positiveInsightMessage"
        />
        <CompareTable
          v-else-if="activeModule === 'compare'"
          :items="compareItems"
          :state="compareState"
          :message="compareMessage"
          :product-code="compareProductCode"
          :product-name="compareProductName"
          :comparison-product-code="compareComparisonProductCode"
          :comparison-product-name="compareComparisonProductName"
          @compare="runCompare"
        />
        <TrendList
          v-else-if="activeModule === 'trends'"
          :series="trendSeries"
          :state="trendState"
          :message="trendMessage"
          @retry="reloadTrendData"
        />
        <WordCloudPanel
          v-else-if="activeModule === 'wordcloud'"
          :aspect="wordCloudAspect"
          :ux-secondary-label="wordCloudUxSecondaryLabel"
          :items="wordCloudItems"
          :state="wordCloudState"
          :message="wordCloudMessage"
          :notice="wordCloudNotice"
          @retry="reloadWordCloudData"
        />
        <UxChangeComparisonPanel
          v-else-if="activeModule === 'ux-change-comparisons'"
          :product-code="uxChangeProductCode"
          :product-name="uxChangeProductName"
          :history-items="uxChangeHistory"
          :active-record="uxChangeActiveRecord"
          :state="uxChangeState"
          :message="uxChangeMessage"
          :submitting="uxChangeSubmitting"
          @create="createUxChangeRecord"
          @refresh="loadUxChangeHistory"
          @select="loadUxChangeDetail"
        />
      </section>
    </div>
  </AppShellFrame>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import AppShellFrame from './components/AppShellFrame.vue'
import AppShellSidebar from './components/AppShellSidebar.vue'
import CompareTable from './components/CompareTable.vue'
import IssueTable from './components/IssueTable.vue'
import LoginGate from './components/LoginGate.vue'
import PositiveInsightPanel from './components/PositiveInsightPanel.vue'
import ProductSetupPanel from './components/ProductSetupPanel.vue'
import TaxonomyManagerPanel from './components/TaxonomyManagerPanel.vue'
import TrendList from './components/TrendList.vue'
import UxChangeComparisonPanel from './components/UxChangeComparisonPanel.vue'
import WordCloudPanel from './components/WordCloudPanel.vue'
import {
  DEFAULT_PRODUCT_CODE,
  createUxChangeComparison,
  fetchCompare,
  fetchIssues,
  fetchPositiveInsights,
  fetchProductTaxonomy,
  fetchTrends,
  fetchUxChangeComparisonDetail,
  fetchUxChangeComparisons,
  fetchWordCloud,
} from './api/client'
import { useMotionPreferences } from './motion/preferences'
import type {
  ChartLoadState,
  CompareItem,
  CompareResponse,
  CompareState,
  ContractState,
  IssueItem,
  IssueResponse,
  ProductAnalysisReadyPayload,
  ProductHistoryItem,
  ProductTaxonomyResponse,
  PositiveInsightItem,
  PositiveInsightResponse,
  TrendResponse,
  TrendSeries,
  UxLabelOption,
  UxChangeComparisonCreatePayload,
  UxChangeComparisonListResponse,
  UxChangeComparisonRecord,
  WordCloudItem,
  WordCloudResponse,
} from './types/domain'

useMotionPreferences()

type ModuleId =
  | 'product-setup'
  | 'taxonomy'
  | 'issues'
  | 'positive-insights'
  | 'compare'
  | 'trends'
  | 'wordcloud'
  | 'ux-change-comparisons'

type ModuleContract = {
  id: ModuleId
  label: string
  icon: string
}

const internalAccessUsername = `${import.meta.env.VITE_INTERNAL_ACCESS_USERNAME ?? 'wxy'}`.trim() || 'wxy'
const internalAccessPassword = `${import.meta.env.VITE_INTERNAL_ACCESS_PASSWORD ?? '123456'}`.trim() || '123456'
const internalAccessDisplayName = `${import.meta.env.VITE_INTERNAL_ACCESS_DISPLAY_NAME ?? '内部分析员'}`.trim() || '内部分析员'

const moduleContracts: ModuleContract[] = [
  { id: 'product-setup', label: '数据接入', icon: '接' },
  { id: 'taxonomy', label: 'taxonomy', icon: '签' },
  { id: 'issues', label: '问题', icon: '题' },
  { id: 'positive-insights', label: '卖点', icon: '卖' },
  { id: 'compare', label: '竞品对比', icon: '比' },
  { id: 'trends', label: '趋势图', icon: '势' },
  { id: 'wordcloud', label: '词云', icon: '云' },
  { id: 'ux-change-comparisons', label: '前后对比', icon: '改' },
]

const trendColors = ['#2563eb', '#059669', '#dc2626', '#7c3aed', '#ea580c', '#0891b2', '#c026d3', '#65a30d', '#be123c', '#0f766e']

const fallbackTrendLabels: UxLabelOption[] = [
  { id: 'trend-battery', uxPrimaryLabel: '产品硬件', uxSecondaryLabel: '电池与续航', enabled: true },
  { id: 'trend-connection', uxPrimaryLabel: '产品硬件', uxSecondaryLabel: '连接与稳定性', enabled: true },
  { id: 'trend-comfort', uxPrimaryLabel: '产品体验', uxSecondaryLabel: '佩戴与人体工学', enabled: true },
  { id: 'trend-noise', uxPrimaryLabel: '声音表现', uxSecondaryLabel: '降噪与通透', enabled: true },
  { id: 'trend-microphone', uxPrimaryLabel: '声音表现', uxSecondaryLabel: '麦克风与通话', enabled: true },
]

const modules = computed(() => moduleContracts)
const currentProductDisplayName = computed(() => activeProductName.value.trim() || activeProductCode.value)
const currentProductMeta = computed(() => {
  const name = activeProductName.value.trim()
  if (name && name !== activeProductCode.value) {
    return `商品编号 ${activeProductCode.value}`
  }
  return '商品名称未识别，可在数据接入页补充后重新导入'
})

const isAuthenticated = ref(false)
const activeModule = ref<ModuleId>('product-setup')
const activeProductCode = ref(DEFAULT_PRODUCT_CODE)
const activeProductName = ref('')

const issues = ref<IssueItem[]>([])
const positiveInsights = ref<PositiveInsightItem[]>([])
const compareItems = ref<CompareItem[]>([])
const trendSeries = ref<TrendSeries[]>([])
const wordCloudItems = ref<WordCloudItem[]>([])
const uxChangeHistory = ref<UxChangeComparisonRecord[]>([])
const uxChangeActiveRecord = ref<UxChangeComparisonRecord | null>(null)

const issueState = ref<ContractState>('idle')
const issueMessage = ref('')
const positiveInsightState = ref<ContractState>('idle')
const positiveInsightMessage = ref('')
const compareProductCode = ref(DEFAULT_PRODUCT_CODE)
const compareProductName = ref('')
const compareComparisonProductCode = ref('')
const compareComparisonProductName = ref('')
const compareState = ref<CompareState>('idle')
const compareMessage = ref('')
const trendState = ref<ChartLoadState>('idle')
const trendMessage = ref('')
const wordCloudAspect = ref('all')
const wordCloudUxSecondaryLabel = ref('')
const wordCloudState = ref<ChartLoadState>('idle')
const wordCloudMessage = ref('')
const wordCloudNotice = ref('')
const uxChangeProductCode = ref(DEFAULT_PRODUCT_CODE)
const uxChangeProductName = ref('')
const uxChangeState = ref<ContractState>('idle')
const uxChangeMessage = ref('')
const uxChangeSubmitting = ref(false)

type DashboardLoadOptions = {
  includeWordCloud?: boolean
}

type TrendDimension = {
  id: string
  aspect: string
  uxPrimaryLabel?: string
  uxSecondaryLabel: string
}

function aspectForTrendLabel(label: string): string {
  if (label.includes('电池') || label.includes('续航') || label.includes('充电')) {
    return 'battery'
  }
  if (label.includes('连接') || label.includes('蓝牙') || label.includes('稳定')) {
    return 'bluetooth'
  }
  if (label.includes('降噪') || label.includes('通透') || label.includes('噪音') || label.includes('杂音')) {
    return 'noise-canceling'
  }
  if (label.includes('佩戴') || label.includes('人体工学') || label.includes('舒适')) {
    return 'comfort'
  }
  if (label.includes('麦克风') || label.includes('通话') || label.includes('收音')) {
    return 'microphone'
  }
  return 'general'
}

function trendDimensionsFromTaxonomy(taxonomy: ProductTaxonomyResponse | null): TrendDimension[] {
  const labels = taxonomy?.labels?.length ? taxonomy.labels : fallbackTrendLabels
  const seen = new Set<string>()

  const dimensions = labels
    .filter((label) => label.enabled !== false && label.uxSecondaryLabel.trim() && label.uxSecondaryLabel.trim() !== '无明显问题')
    .map((label, index) => ({
      id: label.id?.trim() || `${label.uxPrimaryLabel}-${label.uxSecondaryLabel}-${index}`,
      aspect: aspectForTrendLabel(label.uxSecondaryLabel),
      uxPrimaryLabel: label.uxPrimaryLabel,
      uxSecondaryLabel: label.uxSecondaryLabel.trim(),
    }))
    .filter((dimension) => {
      if (seen.has(dimension.uxSecondaryLabel)) {
        return false
      }
      seen.add(dimension.uxSecondaryLabel)
      return true
    })

  if (dimensions.length > 0 || labels === fallbackTrendLabels) {
    return dimensions
  }
  return trendDimensionsFromTaxonomy(null)
}

function resolveContractMessage(
  state: ContractState | ChartLoadState,
  notice: string | undefined,
  fallbackEmpty: string,
  fallbackDegraded: string,
  fallbackError: string,
  fallbackRuntimeUnavailable = fallbackError,
  fallbackDisabled = fallbackEmpty,
  fallbackTimeout = fallbackError,
): string {
  if (state === 'empty') {
    return notice?.trim() || fallbackEmpty
  }
  if (state === 'degraded') {
    return notice?.trim() || fallbackDegraded
  }
  if (state === 'runtime-unavailable') {
    return notice?.trim() || fallbackRuntimeUnavailable
  }
  if (state === 'disabled') {
    return notice?.trim() || fallbackDisabled
  }
  if (state === 'timeout') {
    return notice?.trim() || fallbackTimeout
  }
  if (state === 'error') {
    return notice?.trim() || fallbackError
  }
  return ''
}

function applyIssueResponse(response: IssueResponse): void {
  issues.value = response.items
  issueState.value = response.state
  issueMessage.value = resolveContractMessage(
    response.state,
    response.notice,
    '暂无问题数据',
    '问题列表暂时回退为受限结果，请稍后刷新。',
    '问题接口请求失败，请稍后重试。',
  )
}

function applyPositiveInsightResponse(response: PositiveInsightResponse): void {
  positiveInsights.value = response.items
  positiveInsightState.value = response.state
  positiveInsightMessage.value = resolveContractMessage(
    response.state,
    response.notice,
    '暂无可提炼的正面卖点',
    '卖点结果暂时回退为部分数据，请稍后刷新。',
    '卖点接口请求失败，请稍后重试。',
  )
}

function applyCompareResponse(response: CompareResponse): void {
  compareProductCode.value = response.productCode
  compareProductName.value =
    response.productName?.trim() ||
    (response.productCode === activeProductCode.value ? activeProductName.value : compareProductName.value)
  compareComparisonProductCode.value = response.comparisonProductCode ?? ''
  compareComparisonProductName.value = response.comparisonProductName?.trim() || ''
  compareItems.value = response.items
  compareState.value = response.state

  if (response.state === 'taxonomy-mismatch') {
    compareMessage.value = response.notice?.trim() || '两个商品绑定的 UX 标签体系不一致，请先统一 taxonomy 后再对比。'
  } else if (response.state === 'missing-target') {
    compareMessage.value = response.notice?.trim() || '请输入主商品和竞品编号。'
  } else if (response.state === 'comparison-unavailable') {
    compareMessage.value = response.notice?.trim() || '竞品暂无可用分析结果，请先导入竞品评论并等待自动分析完成。'
  } else if (response.state === 'primary-unavailable') {
    compareMessage.value = response.notice?.trim() || '主商品暂无可用分析结果，请先导入评论并等待自动分析完成。'
  } else if (response.state === 'error') {
    compareMessage.value = response.notice?.trim() || '竞品对比接口请求失败，请稍后重试。'
  } else {
    compareMessage.value = ''
  }
}

function trendStateFromSeries(series: TrendSeries[]): ChartLoadState {
  if (series.some((item) => item.points.length > 0)) {
    return 'success'
  }
  if (series.some((item) => item.state === 'timeout')) {
    return 'timeout'
  }
  if (series.some((item) => item.state === 'runtime-unavailable')) {
    return 'runtime-unavailable'
  }
  if (series.some((item) => item.state === 'error')) {
    return 'error'
  }
  if (series.some((item) => item.state === 'degraded')) {
    return 'degraded'
  }
  if (series.some((item) => item.state === 'disabled')) {
    return 'disabled'
  }
  return 'empty'
}

function applyTrendSeries(series: TrendSeries[]): void {
  trendSeries.value = series
  trendState.value = trendStateFromSeries(series)
  trendMessage.value = resolveContractMessage(
    trendState.value,
    series.find((item) => item.notice?.trim())?.notice,
    '暂无趋势数据，请先导入真实评论并等待自动分析完成。',
    '趋势数据暂时只保留最近一次可用时间窗，请稍后重试。',
    '趋势接口请求失败，请稍后重试。',
    '趋势运行态暂不可用，请稍后重试。',
    '趋势模块当前已禁用。',
    '趋势接口请求超时，请检查网络后重试。',
  )
}

async function loadTrendSeries(productCode: string): Promise<TrendSeries[]> {
  const taxonomy = await fetchProductTaxonomy(productCode, 'general-product')
  const dimensions = trendDimensionsFromTaxonomy(taxonomy)
  const responses = await Promise.all(
    dimensions.map((dimension) => fetchTrends(productCode, dimension.aspect, dimension.uxSecondaryLabel)),
  )

  return responses.map((response: TrendResponse, index) => {
    const dimension = dimensions[index]
    return {
      id: dimension.id,
      aspect: dimension.aspect,
      uxPrimaryLabel: response.uxPrimaryLabel?.trim() || dimension.uxPrimaryLabel,
      uxSecondaryLabel: dimension.uxSecondaryLabel,
      color: trendColors[index % trendColors.length],
      points: response.points,
      state: response.state,
      notice: response.notice,
    }
  })
}

function applyWordCloudResponse(response: WordCloudResponse): void {
  wordCloudAspect.value = response.aspect
  wordCloudUxSecondaryLabel.value = response.uxSecondaryLabel?.trim() || response.aspect
  wordCloudItems.value = response.items
  wordCloudState.value = response.state
  wordCloudMessage.value = resolveContractMessage(
    response.state,
    response.notice,
    '暂无词云数据，请先导入真实评论并等待自动分析完成。',
    '词云数据暂时退化为受限结果，请稍后重试。',
    '词云接口请求失败，请稍后重试。',
    '词云运行态暂不可用，请稍后重试。',
    '词云模块当前已禁用。',
    '词云接口请求超时，请检查网络后重试。',
  )
  wordCloudNotice.value = response.state === 'success' ? response.notice?.trim() ?? '' : ''
}

function applyUxChangeHistory(response: UxChangeComparisonListResponse): void {
  uxChangeHistory.value = response.items
  uxChangeProductName.value = response.productName?.trim() || uxChangeProductName.value
  uxChangeState.value = response.state
  uxChangeMessage.value = resolveContractMessage(
    response.state,
    response.notice,
    '暂无前后对比结果。',
    '前后对比结果暂时只返回部分数据。',
    '前后对比历史请求失败，请稍后重试。',
  )
  uxChangeActiveRecord.value = response.items[0] ?? uxChangeActiveRecord.value
}

function normalizeProductCode(productCode?: string): string {
  return productCode?.trim() || DEFAULT_PRODUCT_CODE
}

function handleLogin(payload: { username: string; displayName: string }): void {
  isAuthenticated.value = true
  void loadDashboard(activeProductCode.value)
}

function activateModule(moduleId: ModuleId): void {
  activeModule.value = moduleId
  void ensureModuleData(moduleId)
}

async function ensureModuleData(moduleId: ModuleId): Promise<void> {
  if (moduleId === 'issues' && shouldRefreshContractState(issueState.value)) {
    issueState.value = 'loading'
    const response = await fetchIssues(activeProductCode.value)
    applyIssueResponse(response)
  }
  if (moduleId === 'positive-insights' && shouldRefreshContractState(positiveInsightState.value)) {
    positiveInsightState.value = 'loading'
    const response = await fetchPositiveInsights(activeProductCode.value)
    applyPositiveInsightResponse(response)
  }
  if (moduleId === 'trends' && shouldRefreshChartState(trendState.value)) {
    await reloadTrendData()
  }
  if (moduleId === 'wordcloud' && shouldRefreshChartState(wordCloudState.value)) {
    await reloadWordCloudData()
  }
  if (moduleId === 'ux-change-comparisons' && uxChangeState.value === 'idle') {
    await loadUxChangeHistory(uxChangeProductCode.value)
  }
}

function shouldRefreshContractState(state: ContractState): boolean {
  return ['idle', 'empty', 'degraded', 'error', 'runtime-unavailable'].includes(state)
}

function shouldRefreshChartState(state: ChartLoadState): boolean {
  return ['idle', 'empty', 'degraded', 'error', 'timeout', 'runtime-unavailable'].includes(state)
}

async function loadDashboard(productCode = activeProductCode.value, options: DashboardLoadOptions = {}): Promise<void> {
  const normalizedProductCode = normalizeProductCode(productCode)
  activeProductCode.value = normalizedProductCode
  issueState.value = 'loading'
  positiveInsightState.value = 'loading'
  trendState.value = 'loading'
  if (options.includeWordCloud) {
    wordCloudState.value = 'loading'
  }
  const [issueResponse, positiveInsightResponse, trendSeriesResponse, wordCloudResponse] = await Promise.all([
    fetchIssues(normalizedProductCode),
    fetchPositiveInsights(normalizedProductCode),
    loadTrendSeries(normalizedProductCode),
    options.includeWordCloud ? fetchWordCloud(normalizedProductCode, wordCloudAspect.value) : Promise.resolve(null),
  ])
  applyIssueResponse(issueResponse)
  applyPositiveInsightResponse(positiveInsightResponse)
  applyTrendSeries(trendSeriesResponse)
  if (wordCloudResponse) {
    applyWordCloudResponse(wordCloudResponse)
  }
}

async function handleProductAnalysisReady(payload: ProductAnalysisReadyPayload): Promise<void> {
  const productCode = normalizeProductCode(payload.productCode || payload.analysisJob.productCode || payload.importResult.productCode)
  const productName = payload.productName?.trim() || payload.importResult.productName?.trim() || ''
  await switchActiveProduct(productCode, productName)
}

async function handleProductSelected(payload: ProductHistoryItem): Promise<void> {
  await switchActiveProduct(payload.productCode, payload.productName?.trim() || '')
}

async function switchActiveProduct(productCode: string, productName: string): Promise<void> {
  const normalizedProductCode = normalizeProductCode(productCode)
  activeProductCode.value = normalizedProductCode
  compareProductCode.value = normalizedProductCode
  compareProductName.value = productName
  uxChangeProductCode.value = normalizedProductCode
  uxChangeProductName.value = productName
  activeProductName.value = productName
  compareItems.value = []
  compareState.value = 'idle'
  compareMessage.value = ''
  uxChangeHistory.value = []
  uxChangeActiveRecord.value = null
  uxChangeState.value = 'idle'
  uxChangeMessage.value = ''

  await loadDashboard(normalizedProductCode, { includeWordCloud: true })
  if (activeModule.value === 'ux-change-comparisons') {
    await loadUxChangeHistory(normalizedProductCode)
  }
}

async function runCompare(payload: { productCode: string; comparisonProductCode: string }): Promise<void> {
  const productCode = payload.productCode.trim()
  const comparisonProductCode = payload.comparisonProductCode.trim()
  if (!productCode || !comparisonProductCode) {
    applyCompareResponse({
      productCode,
      comparisonProductCode,
      items: [],
      state: 'missing-target',
      notice: '请输入主商品和竞品编号。',
    })
    return
  }
  compareState.value = 'loading'
  const response = await fetchCompare(productCode, comparisonProductCode)
  applyCompareResponse(response)
}

async function reloadTrendData(): Promise<void> {
  trendState.value = 'loading'
  const response = await loadTrendSeries(activeProductCode.value)
  applyTrendSeries(response)
}

async function reloadWordCloudData(): Promise<void> {
  wordCloudState.value = 'loading'
  const response = await fetchWordCloud(activeProductCode.value, wordCloudAspect.value)
  applyWordCloudResponse(response)
}

async function loadUxChangeHistory(productCode: string): Promise<void> {
  uxChangeProductCode.value = normalizeProductCode(productCode)
  if (uxChangeProductCode.value === activeProductCode.value) {
    uxChangeProductName.value = activeProductName.value
  }
  uxChangeState.value = 'loading'
  const response = await fetchUxChangeComparisons(uxChangeProductCode.value)
  applyUxChangeHistory(response)
  const firstRecord = response.items[0]
  if (response.state === 'success' && firstRecord?.id && firstRecord.items.length === 0) {
    await loadUxChangeDetail(firstRecord.id)
  }
}

async function createUxChangeRecord(payload: UxChangeComparisonCreatePayload): Promise<void> {
  if (!payload.productCode.trim() || !payload.changeDate) {
    uxChangeState.value = 'error'
    uxChangeMessage.value = '请输入商品编号和时间点。'
    return
  }
  uxChangeSubmitting.value = true
  uxChangeState.value = 'loading'
  try {
    const record = await createUxChangeComparison(payload)
    uxChangeProductCode.value = record.productCode
    uxChangeProductName.value = record.productName?.trim() || uxChangeProductName.value
    uxChangeActiveRecord.value = record
    uxChangeHistory.value = [record, ...uxChangeHistory.value.filter((item) => item.id !== record.id)]
    uxChangeState.value = record.state
    uxChangeMessage.value = record.notice ?? ''
  } catch {
    uxChangeState.value = 'error'
    uxChangeMessage.value = '前后对比接口请求失败，请稍后重试。'
  } finally {
    uxChangeSubmitting.value = false
  }
}

async function loadUxChangeDetail(id: string): Promise<void> {
  if (!id) {
    return
  }
  uxChangeState.value = 'loading'
  try {
    const record = await fetchUxChangeComparisonDetail(id, uxChangeProductCode.value)
    uxChangeActiveRecord.value = record
    uxChangeProductName.value = record.productName?.trim() || uxChangeProductName.value
    uxChangeState.value = record.state
    uxChangeMessage.value = record.notice ?? ''
  } catch {
    uxChangeState.value = 'error'
    uxChangeMessage.value = '前后对比详情请求失败，请稍后重试。'
  }
}
</script>

<style scoped>
.content-stack {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--space-2);
  height: 100%;
  min-height: 0;
}

.current-product-bar {
  position: relative;
  z-index: var(--z-raised);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-lg);
  background: var(--color-surface-1);
  box-shadow: var(--shadow-panel);
}

.current-product-bar div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.current-product-bar span,
.current-product-bar small {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-tight);
}

.current-product-bar strong {
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-tight);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current-product-bar small {
  flex: 0 1 auto;
  min-width: 0;
  text-align: right;
}

.module-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-xl);
  min-height: 0;
  height: 100%;
  padding: var(--space-3);
  background: var(--color-surface-1);
  border: 1px solid var(--color-border-default);
  box-shadow: var(--shadow-panel);
}

.module-card::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), transparent 12%),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04), transparent 20% 80%, rgba(255, 255, 255, 0.02));
}

.module-card > * {
  position: relative;
  z-index: var(--z-raised);
}

.module-card :deep(.panel) {
  height: 100%;
  min-height: 0;
}

@media (max-width: 720px) {
  .content-stack {
    height: auto;
  }

  .current-product-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .current-product-bar small {
    text-align: left;
  }

  .module-card {
    padding: var(--space-3);
    overflow: visible;
  }
}

html[data-motion='reduce'] .module-card,
html[data-motion='none'] .module-card {
  backdrop-filter: none;
}
</style>
