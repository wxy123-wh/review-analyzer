import axios from 'axios'

import type {
  ActionCreatePayload,
  ActionItem,
  AnalysisJobResponse,
  ActionResponse,
  ChartLoadState,
  CompareItem,
  CompareResponse,
  CompareState,
  ContractState,
  CrawlJobResponse,
  CrawlJobStatus,
  CrawlStartPayload,
  IssueItem,
  IssueResponse,
  PositiveInsightItem,
  PositiveInsightResponse,
  ProductTaxonomyPayload,
  ProductTaxonomyResponse,
  ServiceStatus,
  ShowcaseAgentArenaData,
  ShowcaseChaosData,
  ShowcaseExplainabilityData,
  ShowcasePipelineData,
  ShowcaseReportPreviewData,
  TrendResponse,
  UxLabelOption,
  ValidationItem,
  ValidationResponse,
  WordCloudItem,
  WordCloudResponse,
} from '../types/domain'
import { normalizeShowcaseStatus } from '../utils/showcaseCopy'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const isTestMode = import.meta.env.MODE === 'test'
export const DEFAULT_PRODUCT_CODE = 'jd-100127936932'
export const DEFAULT_COMPARE_PRODUCT_CODE = 'jd-competitor'

const CANONICAL_COMPARE_ASPECTS = [
  'battery',
  'bluetooth',
  'noise-canceling',
  'comfort',
  'microphone',
] as const

const CANONICAL_ASPECT_ALIASES: Record<string, string> = {
  battery: 'battery',
  bluetooth: 'bluetooth',
  connectivity: 'bluetooth',
  'noise-canceling': 'noise-canceling',
  noise_canceling: 'noise-canceling',
  noise_cancel: 'noise-canceling',
  comfort: 'comfort',
  microphone: 'microphone',
  call_quality: 'microphone',
  'call-quality': 'microphone',
}

const DEFAULT_UX_LABELS: UxLabelOption[] = [
  {
    id: 'quality-performance',
    uxPrimaryLabel: '产品体验',
    uxSecondaryLabel: '质量与性能',
    description: '性能、稳定性、耐用性等商品核心体验。',
    enabled: true,
  },
  {
    id: 'usability-operation',
    uxPrimaryLabel: '产品体验',
    uxSecondaryLabel: '易用性与操作',
    description: '安装、设置、操作、学习成本等使用体验。',
    enabled: true,
  },
  {
    id: 'service-delivery',
    uxPrimaryLabel: '交易体验',
    uxSecondaryLabel: '物流与售后',
    description: '配送、包装、客服、退换货等交易过程体验。',
    enabled: true,
  },
]

export const apiClient = axios.create({
  baseURL,
  timeout: 10000,
})

// AbortController for request cancellation
let abortController: AbortController | null = null

export function cancelPendingRequests(): void {
  if (abortController) {
    abortController.abort()
  }
  abortController = new AbortController()
  return abortController
}

apiClient.interceptors.request.use((config) => {
  config.signal = abortController?.signal
  return config
})

type ShowcasePayloadBase = {
  status?: string
  note?: string
}

function normalizeShowcaseNote(note: unknown, fallback: string): string {
  if (typeof note !== 'string') {
    return fallback
  }
  const trimmed = note.trim()
  if (!trimmed) {
    return fallback
  }
  return trimmed
}

function normalizeShowcaseData<T extends ShowcasePayloadBase>(payload: T, fallbackNote: string): T {
  return {
    ...payload,
    status: normalizeShowcaseStatus(payload.status),
    note: normalizeShowcaseNote(payload.note, fallbackNote),
  }
}

function resolveRequestState(error: unknown): Extract<ChartLoadState, 'timeout' | 'error'> {
  if (!axios.isAxiosError(error)) {
    return 'error'
  }
  if (error.code === 'ECONNABORTED') {
    return 'timeout'
  }
  if (error.response?.status === 408 || error.response?.status === 504) {
    return 'timeout'
  }
  return 'error'
}

function normalizeChartState(
  state: unknown,
): Extract<ChartLoadState, 'success' | 'empty' | 'degraded' | 'error' | 'disabled' | 'runtime-unavailable'> | null {
  if (typeof state !== 'string') {
    return null
  }
  switch (state.trim()) {
    case 'success':
    case 'empty':
    case 'degraded':
    case 'error':
    case 'disabled':
    case 'runtime-unavailable':
      return state.trim() as Extract<ChartLoadState, 'success' | 'empty' | 'degraded' | 'error' | 'disabled' | 'runtime-unavailable'>
    default:
      return null
  }
}

function normalizeAspectCode(
  aspect: unknown,
  fallback: 'battery' | 'bluetooth' | 'noise-canceling' | 'comfort' | 'microphone' | 'all' | 'general',
): string {
  if (typeof aspect !== 'string') {
    return fallback
  }
  const normalized = aspect.trim().toLowerCase()
  if (!normalized) {
    return fallback
  }
  return CANONICAL_ASPECT_ALIASES[normalized] ?? fallback
}

function normalizeCompareItems(rawItems: unknown): CompareItem[] {
  if (!Array.isArray(rawItems)) {
    return []
  }
  return rawItems
    .map((item) => {
      if (typeof item !== 'object' || item === null) {
        return null
      }
      const record = item as Record<string, unknown>
      const ourScore = typeof record.ourScore === 'number' ? record.ourScore : 0
      const competitorScore = typeof record.competitorScore === 'number' ? record.competitorScore : 0
      const gap = typeof record.gap === 'number' ? record.gap : ourScore - competitorScore
      return {
        aspect: normalizeAspectCode(record.aspect, 'battery'),
        uxPrimaryLabel: normalizeNotice(record.uxPrimaryLabel),
        uxSecondaryLabel: normalizeUxSecondaryLabel(record.uxSecondaryLabel, record.aspect),
        ourScore,
        competitorScore,
        gap,
      }
    })
    .filter((item): item is CompareItem => item !== null)
    .sort((left, right) => {
      const leftIndex = CANONICAL_COMPARE_ASPECTS.indexOf(left.aspect as (typeof CANONICAL_COMPARE_ASPECTS)[number])
      const rightIndex = CANONICAL_COMPARE_ASPECTS.indexOf(right.aspect as (typeof CANONICAL_COMPARE_ASPECTS)[number])
      return (leftIndex === -1 ? Number.MAX_SAFE_INTEGER : leftIndex) - (rightIndex === -1 ? Number.MAX_SAFE_INTEGER : rightIndex)
    })
}

function normalizeCompareState(state: unknown): CompareState {
  if (typeof state !== 'string') {
    return 'error'
  }
  switch (state.trim()) {
    case 'success':
    case 'missing-target':
    case 'comparison-unavailable':
    case 'primary-unavailable':
    case 'error':
      return state.trim() as CompareState
    default:
      return 'error'
  }
}

function normalizeNotice(notice: unknown): string | undefined {
  if (typeof notice !== 'string') {
    return undefined
  }
  const trimmed = notice.trim()
  return trimmed || undefined
}

function normalizeText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

export function normalizeUxSecondaryLabel(value: unknown, fallback: unknown): string {
  const label = normalizeText(value)
  if (label) {
    return label
  }
  return normalizeText(fallback)
}

function normalizeUxLabelOption(rawLabel: unknown, index: number): UxLabelOption | null {
  if (typeof rawLabel !== 'object' || rawLabel === null) {
    return null
  }
  const record = rawLabel as Record<string, unknown>
  const uxPrimaryLabel = normalizeText(record.uxPrimaryLabel)
  const uxSecondaryLabel = normalizeText(record.uxSecondaryLabel)
  if (!uxPrimaryLabel || !uxSecondaryLabel) {
    return null
  }
  return {
    id: normalizeText(record.id) || `${uxPrimaryLabel}-${uxSecondaryLabel}-${index}`,
    uxPrimaryLabel,
    uxSecondaryLabel,
    description: normalizeNotice(record.description),
    enabled: typeof record.enabled === 'boolean' ? record.enabled : true,
  }
}

export function normalizeUxLabelOptions(rawLabels: unknown): UxLabelOption[] {
  if (!Array.isArray(rawLabels)) {
    return DEFAULT_UX_LABELS
  }
  const labels = rawLabels
    .map((label, index) => normalizeUxLabelOption(label, index))
    .filter((label): label is UxLabelOption => label !== null)
  return labels.length > 0 ? labels : DEFAULT_UX_LABELS
}

function normalizeCollectionState(
  state: unknown,
): Extract<ContractState, 'success' | 'empty' | 'degraded' | 'error' | 'disabled' | 'runtime-unavailable'> | null {
  if (typeof state !== 'string') {
    return null
  }
  switch (state.trim()) {
    case 'success':
    case 'empty':
    case 'degraded':
    case 'error':
    case 'disabled':
    case 'runtime-unavailable':
      return state.trim() as Extract<ContractState, 'success' | 'empty' | 'degraded' | 'error' | 'disabled' | 'runtime-unavailable'>
    default:
      return null
  }
}

function resolveCollectionState(
  items: unknown[],
  explicitState: unknown,
  notice?: string,
): Extract<ContractState, 'success' | 'empty' | 'degraded' | 'error' | 'disabled' | 'runtime-unavailable'> {
  const normalizedState = normalizeCollectionState(explicitState)
  if (normalizedState) {
    return normalizedState
  }
  if (notice && items.length > 0) {
    return 'degraded'
  }
  return items.length > 0 ? 'success' : 'empty'
}

function resolveChartState(
  items: unknown[],
  explicitState: unknown,
  notice?: string,
): Extract<ChartLoadState, 'success' | 'empty' | 'degraded' | 'error' | 'disabled' | 'runtime-unavailable'> {
  const normalizedState = normalizeChartState(explicitState)
  if (normalizedState) {
    return normalizedState
  }
  if (notice && items.length > 0) {
    return 'degraded'
  }
  return items.length > 0 ? 'success' : 'empty'
}

function normalizeIssueItems(rawItems: unknown): IssueItem[] {
  if (!Array.isArray(rawItems)) {
    return []
  }
  return rawItems
    .map((item) => {
      if (typeof item !== 'object' || item === null) {
        return null
      }
      const record = item as Record<string, unknown>
      const issueId = typeof record.issueId === 'string' ? record.issueId : ''
      const title = typeof record.title === 'string' ? record.title : ''
      const evidenceSummary = typeof record.evidenceSummary === 'string' ? record.evidenceSummary : ''
      if (!issueId || !title || !evidenceSummary) {
        return null
      }
      return {
        issueId,
        title,
        aspect: normalizeAspectCode(record.aspect, 'general'),
        uxPrimaryLabel: normalizeNotice(record.uxPrimaryLabel),
        uxSecondaryLabel: normalizeUxSecondaryLabel(record.uxSecondaryLabel, record.aspect),
        priorityScore: typeof record.priorityScore === 'number' ? record.priorityScore : 0,
        evidenceSummary,
      }
    })
    .filter((item): item is IssueItem => item !== null)
}

function normalizePositiveInsightItems(rawItems: unknown): PositiveInsightItem[] {
  if (!Array.isArray(rawItems)) {
    return []
  }
  return rawItems
    .map((item) => {
      if (typeof item !== 'object' || item === null) {
        return null
      }
      const record = item as Record<string, unknown>
      const sellingPointId = typeof record.sellingPointId === 'string' ? record.sellingPointId : ''
      const sellingPoint = typeof record.sellingPoint === 'string' ? record.sellingPoint : ''
      const uxSecondaryLabel = normalizeUxSecondaryLabel(record.uxSecondaryLabel, record.aspect)
      if (!sellingPointId || !sellingPoint || !uxSecondaryLabel) {
        return null
      }
      return {
        sellingPointId,
        aspect: normalizeAspectCode(record.aspect, 'general'),
        uxPrimaryLabel: typeof record.uxPrimaryLabel === 'string' ? record.uxPrimaryLabel : '',
        uxSecondaryLabel: normalizeUxSecondaryLabel(record.uxSecondaryLabel, record.aspect),
        sellingPoint,
        mentionCount: typeof record.mentionCount === 'number' ? record.mentionCount : 0,
        positiveRate: typeof record.positiveRate === 'number' ? record.positiveRate : 0,
        score: typeof record.score === 'number' ? record.score : 0,
        evidence: Array.isArray(record.evidence)
          ? record.evidence.filter((value): value is string => typeof value === 'string' && value.trim().length > 0)
          : [],
      }
    })
    .filter((item): item is PositiveInsightItem => item !== null)
}

function normalizeActionItems(rawItems: unknown): ActionItem[] {
  if (!Array.isArray(rawItems)) {
    return []
  }
  return rawItems
    .map((item) => {
      if (typeof item !== 'object' || item === null) {
        return null
      }
      const record = item as Record<string, unknown>
      const actionId = typeof record.actionId === 'string' ? record.actionId : ''
      const productCode = typeof record.productCode === 'string' ? record.productCode : ''
      const issueId = typeof record.issueId === 'string' ? record.issueId : ''
      const actionName = typeof record.actionName === 'string' ? record.actionName : ''
      const status = typeof record.status === 'string' ? record.status : ''
      const createdAt = typeof record.createdAt === 'string' ? record.createdAt : ''
      if (!actionId || !productCode || !issueId || !actionName || !status || !createdAt) {
        return null
      }
      return {
        actionId,
        productCode,
        issueId,
        actionName,
        actionDesc: typeof record.actionDesc === 'string' ? record.actionDesc : undefined,
        status,
        createdAt,
      }
    })
    .filter((item): item is ActionItem => item !== null)
}

function normalizeValidationItems(rawItems: unknown): ValidationItem[] {
  if (!Array.isArray(rawItems)) {
    return []
  }
  return rawItems
    .map((item) => {
      if (typeof item !== 'object' || item === null) {
        return null
      }
      const record = item as Record<string, unknown>
      const actionId = typeof record.actionId === 'string' ? record.actionId : ''
      const summary = typeof record.summary === 'string' ? record.summary : ''
      if (!actionId || !summary) {
        return null
      }
      return {
        actionId,
        beforeNegativeRate: typeof record.beforeNegativeRate === 'number' ? record.beforeNegativeRate : 0,
        afterNegativeRate: typeof record.afterNegativeRate === 'number' ? record.afterNegativeRate : 0,
        improvementRate: typeof record.improvementRate === 'number' ? record.improvementRate : 0,
        summary,
      }
    })
    .filter((item): item is ValidationItem => item !== null)
}

function normalizeTaxonomyRecord(rawTaxonomy: unknown, fallbackProductCode: string, fallbackCategory: string): ProductTaxonomyResponse {
  const taxonomy = typeof rawTaxonomy === 'object' && rawTaxonomy !== null ? (rawTaxonomy as Record<string, unknown>) : {}
  const primaryLabels = Array.isArray(taxonomy.primaryLabels) ? taxonomy.primaryLabels : []
  const labels = primaryLabels.flatMap((primary, primaryIndex) => {
    if (typeof primary !== 'object' || primary === null) {
      return []
    }
    const primaryRecord = primary as Record<string, unknown>
    const uxPrimaryLabel = normalizeText(primaryRecord.labelName)
    const secondaryLabels = Array.isArray(primaryRecord.secondaryLabels) ? primaryRecord.secondaryLabels : []
    return secondaryLabels
      .map((secondary, secondaryIndex) => {
        if (typeof secondary !== 'object' || secondary === null) {
          return null
        }
        const secondaryRecord = secondary as Record<string, unknown>
        const uxSecondaryLabel = normalizeText(secondaryRecord.labelName)
        if (!uxPrimaryLabel || !uxSecondaryLabel) {
          return null
        }
        return {
          id:
            normalizeText(secondaryRecord.id) ||
            `${normalizeText(primaryRecord.id) || primaryIndex}-${uxSecondaryLabel}-${secondaryIndex}`,
          uxPrimaryLabel,
          uxSecondaryLabel,
          description: normalizeNotice(secondaryRecord.description),
          enabled: typeof secondaryRecord.enabled === 'boolean' ? secondaryRecord.enabled : true,
        }
      })
      .filter((label): label is UxLabelOption => label !== null)
  })
  return {
    taxonomyId: typeof taxonomy.taxonomyId === 'number' ? taxonomy.taxonomyId : undefined,
    name: normalizeNotice(taxonomy.name),
    productCode: fallbackProductCode,
    category: normalizeText(taxonomy.productCategory) || fallbackCategory,
    labels: labels.length > 0 ? labels : DEFAULT_UX_LABELS,
    state: labels.length > 0 ? 'success' : 'degraded',
    notice: labels.length > 0 ? undefined : '当前 taxonomy 暂无有效标签，已回退到通用 UX 标签草稿。',
  }
}

function normalizeProductTaxonomyBinding(payload: unknown, fallbackProductCode: string, fallbackCategory: string): ProductTaxonomyResponse {
  const record = typeof payload === 'object' && payload !== null ? (payload as Record<string, unknown>) : {}
  return normalizeTaxonomyRecord(record.taxonomy, normalizeText(record.productCode) || fallbackProductCode, fallbackCategory)
}

function buildTaxonomyRequest(payload: ProductTaxonomyPayload): Record<string, unknown> {
  const grouped = new Map<string, UxLabelOption[]>()
  payload.labels.forEach((label) => {
    const primary = label.uxPrimaryLabel.trim()
    const secondary = label.uxSecondaryLabel.trim()
    if (!primary || !secondary) {
      return
    }
    grouped.set(primary, [...(grouped.get(primary) ?? []), { ...label, uxPrimaryLabel: primary, uxSecondaryLabel: secondary }])
  })

  return {
    name: payload.name?.trim() || `${payload.productCode} UX 标签`,
    productCategory: payload.category,
    primaryLabels: Array.from(grouped.entries()).map(([labelName, secondaryLabels]) => ({
      labelName,
      secondaryLabels: secondaryLabels.map((label) => ({
        labelName: label.uxSecondaryLabel,
        synonyms: [],
        description: label.description,
        enabled: label.enabled,
      })),
    })),
  }
}

function normalizeCrawlJobStatus(status: unknown): CrawlJobStatus {
  const normalized = normalizeText(status).toUpperCase()
  switch (normalized) {
    case 'QUEUED':
    case 'RUNNING':
    case 'WAITING_FOR_MANUAL_ACTION':
    case 'SUCCEEDED':
    case 'FAILED':
    case 'CANCELLED':
      return normalized
    default:
      return 'QUEUED'
  }
}

function normalizeCrawlJob(payload: unknown, fallbackProductCode: string): CrawlJobResponse {
  const record = typeof payload === 'object' && payload !== null ? (payload as Record<string, unknown>) : {}
  return {
    jobId: normalizeText(record.jobId) || normalizeText(record.crawlJobId) || '',
    productCode: normalizeText(record.productCode) || fallbackProductCode,
    productUrl: normalizeNotice(record.productUrl),
    platform: normalizeNotice(record.platform),
    taxonomyId: typeof record.taxonomyId === 'number' ? record.taxonomyId : undefined,
    status: normalizeCrawlJobStatus(record.status),
    fetchedCount: typeof record.fetchedCount === 'number' ? record.fetchedCount : 0,
    errorMessage: normalizeNotice(record.errorMessage),
    analysisHandoffStatus: normalizeNotice(record.analysisHandoffStatus),
    analysisHandoffNote: normalizeNotice(record.analysisHandoffNote),
    startedAt: normalizeNotice(record.startedAt),
    finishedAt: normalizeNotice(record.finishedAt),
  }
}

function normalizeAnalysisJob(payload: unknown, fallbackProductCode: string): AnalysisJobResponse {
  const record = typeof payload === 'object' && payload !== null ? (payload as Record<string, unknown>) : {}
  return {
    jobId: normalizeText(record.jobId),
    productCode: normalizeText(record.productCode) || fallbackProductCode,
    status: normalizeText(record.status) || 'FAILED',
    startedAt: normalizeNotice(record.startedAt),
    finishedAt: normalizeNotice(record.finishedAt),
    errorMessage: normalizeNotice(record.errorMessage),
  }
}

export async function fetchProductTaxonomy(
  productCode = DEFAULT_PRODUCT_CODE,
  category = 'general-product',
): Promise<ProductTaxonomyResponse> {
  if (isTestMode) {
    return {
      productCode,
      category,
      labels: DEFAULT_UX_LABELS,
      state: 'success',
      notice: '测试模式下使用通用电商 UX 标签组合。',
    }
  }

  try {
    const response = await apiClient.get(`/api/v1/products/${encodeURIComponent(productCode)}/taxonomy`)
    return normalizeProductTaxonomyBinding(response.data, productCode, category)
  } catch {
    try {
      const response = await apiClient.get('/api/v1/taxonomies')
      const taxonomies = Array.isArray(response.data) ? response.data : []
      const matched = taxonomies.find((item) => {
        if (typeof item !== 'object' || item === null) {
          return false
        }
        const record = item as Record<string, unknown>
        return normalizeText(record.productCategory) === category && record.active !== false
      })
      return normalizeTaxonomyRecord(matched ?? taxonomies[0], productCode, category)
    } catch {
      return {
        productCode,
        category,
        labels: DEFAULT_UX_LABELS,
        state: 'degraded',
        notice: '标签配置接口暂不可用，当前使用前端通用 UX 标签草稿。',
      }
    }
  }
}

export async function saveProductTaxonomy(payload: ProductTaxonomyPayload): Promise<ProductTaxonomyResponse> {
  if (isTestMode) {
    return {
      taxonomyId: payload.taxonomyId ?? 1,
      name: payload.name ?? `${payload.productCode} UX 标签`,
      productCode: payload.productCode,
      category: payload.category,
      labels: normalizeUxLabelOptions(payload.labels),
      state: 'success',
      notice: '标签组合已在测试模式中保存。',
      updatedAt: '2026-06-03T00:00:00Z',
    }
  }

  const taxonomyRequest = buildTaxonomyRequest(payload)
  const taxonomyResponse = payload.taxonomyId
    ? await apiClient.put(`/api/v1/taxonomies/${encodeURIComponent(`${payload.taxonomyId}`)}`, taxonomyRequest)
    : await apiClient.post('/api/v1/taxonomies', taxonomyRequest)
  const taxonomy = normalizeTaxonomyRecord(taxonomyResponse.data, payload.productCode, payload.category)
  if (taxonomy.taxonomyId) {
    const bindingResponse = await apiClient.put(`/api/v1/products/${encodeURIComponent(payload.productCode)}/taxonomy`, {
      taxonomyId: taxonomy.taxonomyId,
    })
    return normalizeProductTaxonomyBinding(bindingResponse.data, payload.productCode, payload.category)
  }
  return taxonomy
}

export async function startCrawl(payload: CrawlStartPayload): Promise<CrawlJobResponse> {
  if (isTestMode) {
    return {
      jobId: 'crawl-test-1',
      productCode: payload.productCode,
      productUrl: payload.productUrl,
      platform: payload.productUrl.includes('taobao') ? 'taobao' : 'jd',
      taxonomyId: payload.taxonomyId,
      status: 'RUNNING',
      fetchedCount: 0,
      analysisHandoffStatus: 'CRAWL_RUNNING',
      analysisHandoffNote: '采集任务已创建，完成后可启动分析。',
      startedAt: '2026-06-03T00:00:00Z',
    }
  }

  const response = await apiClient.post('/api/v1/crawl/start', payload)
  return normalizeCrawlJob(response.data, payload.productCode ?? DEFAULT_PRODUCT_CODE)
}

export async function fetchCrawlJob(jobId: string, productCode = DEFAULT_PRODUCT_CODE): Promise<CrawlJobResponse> {
  if (isTestMode) {
    return {
      jobId,
      productCode,
      status: 'SUCCEEDED',
      fetchedCount: 128,
      analysisHandoffStatus: 'READY_FOR_ANALYSIS',
      analysisHandoffNote: '采集完成，可以启动分析。',
      finishedAt: '2026-06-03T00:05:00Z',
    }
  }

  const response = await apiClient.get(`/api/v1/crawl/jobs/${encodeURIComponent(jobId)}`)
  return normalizeCrawlJob(response.data, productCode)
}

export async function startAnalysis(productCode = DEFAULT_PRODUCT_CODE): Promise<AnalysisJobResponse> {
  if (isTestMode) {
    return {
      jobId: 'analysis-test-1',
      productCode,
      status: 'SUCCEEDED',
      startedAt: '2026-06-03T00:06:00Z',
      finishedAt: '2026-06-03T00:06:12Z',
    }
  }

  const response = await apiClient.post('/api/v1/analysis/start', { productCode })
  return normalizeAnalysisJob(response.data, productCode)
}

export async function fetchBackendHealth(): Promise<ServiceStatus> {
  if (isTestMode) {
    return { name: 'Backend API', status: 'UP' }
  }

  try {
    const response = await apiClient.get('/api/v1/health')
    return {
      name: 'Backend API',
      status: response.data.status === 'UP' ? 'UP' : 'DOWN',
    }
  } catch {
    return { name: 'Backend API', status: 'DOWN' }
  }
}

export function nlpServiceStatus(): ServiceStatus {
  return { name: 'NLP Service', status: isTestMode ? 'UP' : 'UNKNOWN' }
}

export async function fetchIssues(productCode = DEFAULT_PRODUCT_CODE): Promise<IssueResponse> {
  if (isTestMode) {
    return {
      state: 'success',
      items: [
        {
          issueId: 'iss-bluetooth-001',
          title: '连接稳定性偶发断连',
          aspect: 'bluetooth',
          uxPrimaryLabel: '产品体验',
          uxSecondaryLabel: '连接与稳定性',
          priorityScore: 0.554,
          evidenceSummary: '近30天断连反馈上升且竞品差距扩大。',
        },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/issues', { params: { productCode } })
    const items = normalizeIssueItems(response.data.items)
    const notice = normalizeNotice(response.data.notice)
    return {
      items,
      notice,
      state: resolveCollectionState(items, response.data.state, notice),
    }
  } catch {
    return {
      items: [],
      state: 'error',
      notice: '问题接口请求失败，请稍后重试。',
    }
  }
}

export async function fetchPositiveInsights(productCode = DEFAULT_PRODUCT_CODE): Promise<PositiveInsightResponse> {
  if (isTestMode) {
    return {
      state: 'success',
      items: [
        {
          sellingPointId: 'sp-comfort-test',
          aspect: 'comfort',
          uxPrimaryLabel: '产品体验',
          uxSecondaryLabel: '佩戴与人体工学',
          sellingPoint: '佩戴舒适',
          mentionCount: 12,
          positiveRate: 0.86,
          score: 0.78,
          evidence: ['戴了几个小时耳朵也不疼', '跑步不容易掉'],
        },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/positive-insights', { params: { productCode } })
    const items = normalizePositiveInsightItems(response.data.items)
    const notice = normalizeNotice(response.data.notice)
    return {
      items,
      notice,
      state: resolveCollectionState(items, response.data.state, notice),
    }
  } catch {
    return {
      items: [],
      state: 'error',
      notice: '卖点接口请求失败，请稍后重试。',
    }
  }
}

export async function fetchCompare(
  productCode = DEFAULT_PRODUCT_CODE,
  comparisonProductCode = DEFAULT_COMPARE_PRODUCT_CODE,
): Promise<CompareResponse> {
  if (isTestMode) {
    return {
      productCode,
      comparisonProductCode,
      state: 'success',
      items: [
        { aspect: 'battery', uxSecondaryLabel: '电池与续航', ourScore: 0.22, competitorScore: 0.78, gap: -0.56 },
        { aspect: 'bluetooth', uxSecondaryLabel: '连接与稳定性', ourScore: 0.78, competitorScore: 0.5, gap: 0.28 },
        { aspect: 'noise-canceling', uxSecondaryLabel: '环境降噪', ourScore: 0.5, competitorScore: 0.78, gap: -0.28 },
        { aspect: 'comfort', uxSecondaryLabel: '佩戴与人体工学', ourScore: 0.78, competitorScore: 0.5, gap: 0.28 },
        { aspect: 'microphone', uxSecondaryLabel: '通话与收音', ourScore: 0.5, competitorScore: 0.22, gap: 0.28 },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/compare', {
      params: { productCode, comparisonProductCode },
    })
    return {
      productCode: response.data.productCode ?? productCode,
      comparisonProductCode:
        typeof response.data.comparisonProductCode === 'string'
          ? response.data.comparisonProductCode
          : comparisonProductCode,
      items: normalizeCompareItems(response.data.items),
      state: normalizeCompareState(response.data.state),
      notice: typeof response.data.notice === 'string' ? response.data.notice : undefined,
    }
  } catch {
    return {
      productCode,
      comparisonProductCode,
      items: [],
      state: 'error',
      notice: '竞品对比接口请求失败，请稍后重试。',
    }
  }
}

export async function fetchTrends(
  productCode = DEFAULT_PRODUCT_CODE,
  aspect = 'battery',
): Promise<TrendResponse> {
  const fallbackAspect = normalizeAspectCode(aspect, 'battery')

  if (isTestMode) {
    return {
      aspect: fallbackAspect,
      uxSecondaryLabel: normalizeUxSecondaryLabel(aspect, fallbackAspect),
      points: [
        { period: '2026-W06', negativeRate: 0.31, mentionVolume: 75 },
        { period: '2026-W09', negativeRate: 0.4, mentionVolume: 105 },
      ],
      state: 'success',
    }
  }

  try {
    const response = await apiClient.get('/api/v1/trends', { params: { productCode, aspect } })
    const points = Array.isArray(response.data.points) ? response.data.points : []
    const notice = normalizeNotice(response.data.notice)
    return {
      aspect: normalizeAspectCode(response.data.aspect, 'battery') || fallbackAspect,
      uxPrimaryLabel: normalizeNotice(response.data.uxPrimaryLabel),
      uxSecondaryLabel: normalizeUxSecondaryLabel(response.data.uxSecondaryLabel, response.data.aspect),
      points,
      state: resolveChartState(points, response.data.state, notice),
      notice,
    }
  } catch (error) {
    return { aspect: fallbackAspect, uxSecondaryLabel: fallbackAspect, points: [], state: resolveRequestState(error) }
  }
}

function normalizeWordCloudItems(rawItems: unknown): WordCloudItem[] {
  if (!Array.isArray(rawItems)) {
    return []
  }
  return rawItems
    .map((item) => {
      if (typeof item !== 'object' || item === null) {
        return null
      }
      const record = item as Record<string, unknown>
      const keyword = typeof record.keyword === 'string' ? record.keyword.trim() : ''
      if (!keyword) {
        return null
      }
      const frequency = typeof record.frequency === 'number' ? record.frequency : 0
      const weight = typeof record.weight === 'number' ? record.weight : frequency
      const sentimentTag = typeof record.sentimentTag === 'string' ? record.sentimentTag : 'NEUTRAL'
      return {
        keyword,
        frequency,
        weight,
        sentimentTag,
      }
    })
    .filter((item): item is WordCloudItem => item !== null)
}

export async function fetchWordCloud(
  productCode = DEFAULT_PRODUCT_CODE,
  aspect = 'all',
): Promise<WordCloudResponse> {
  if (isTestMode) {
    return {
      productCode,
      aspect,
      uxSecondaryLabel: aspect,
      items: [
        { keyword: '续航', frequency: 42, weight: 0.92, sentimentTag: 'POSITIVE' },
        { keyword: '断连', frequency: 31, weight: 0.85, sentimentTag: 'NEGATIVE' },
        { keyword: '降噪', frequency: 27, weight: 0.78, sentimentTag: 'POSITIVE' },
        { keyword: '佩戴', frequency: 24, weight: 0.7, sentimentTag: 'NEUTRAL' },
      ],
      notice: '真实评论词云测试数据',
      state: 'success',
    }
  }

  try {
    const response = await apiClient.get('/api/v1/wordcloud', {
      params: { productCode, aspect },
    })
    const items = normalizeWordCloudItems(response.data.items)
    const notice = normalizeNotice(response.data.notice)
    return {
      productCode: response.data.productCode ?? productCode,
      aspect: normalizeAspectCode(response.data.aspect, aspect === 'all' ? 'all' : 'battery'),
      uxPrimaryLabel: normalizeNotice(response.data.uxPrimaryLabel),
      uxSecondaryLabel: normalizeUxSecondaryLabel(response.data.uxSecondaryLabel, response.data.aspect),
      items,
      notice,
      state: resolveChartState(items, response.data.state, notice),
    }
  } catch (error) {
    return {
      productCode,
      aspect: aspect === 'all' ? 'all' : normalizeAspectCode(aspect, 'battery'),
      uxSecondaryLabel: aspect,
      items: [],
      state: resolveRequestState(error),
    }
  }
}

export async function createAction(payload: ActionCreatePayload): Promise<ActionItem> {
  if (isTestMode) {
    return {
      actionId: 'action-test-1',
      productCode: payload.productCode,
      issueId: payload.issueId,
      actionName: payload.actionName,
      actionDesc: payload.actionDesc,
      status: 'PLANNED',
      createdAt: '2026-03-12T00:00:00Z',
    }
  }

  const response = await apiClient.post('/api/v1/actions', payload)
  return response.data
}

export async function fetchActions(): Promise<ActionResponse> {
  if (isTestMode) {
    return {
      state: 'success',
      items: [
        {
          actionId: 'action-test-1',
          productCode: DEFAULT_PRODUCT_CODE,
          issueId: 'iss-battery-7',
          actionName: '处理：续航体验波动',
          actionDesc: '基于动作关联评论窗口回看负向率变化。',
          status: 'PLANNED',
          createdAt: '2026-03-12T00:00:00Z',
        },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/actions')
    const payload = Array.isArray(response.data) ? { items: response.data } : response.data
    const items = normalizeActionItems(payload.items)
    const notice = normalizeNotice(payload.notice)
    return {
      items,
      notice,
      state: resolveCollectionState(items, payload.state, notice),
    }
  } catch {
    return {
      items: [],
      state: 'error',
      notice: '动作接口请求失败，请稍后重试。',
    }
  }
}

export async function fetchValidation(actionId?: string): Promise<ValidationResponse> {
  if (isTestMode) {
    return {
      state: 'success',
      items: [
        {
          actionId: actionId ?? 'action-test-1',
          beforeNegativeRate: 0.42,
          afterNegativeRate: 0.31,
          improvementRate: 0.11,
          summary: '上线后负面率下降 11.00%，问题热度趋稳。',
        },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/validation', {
      params: actionId ? { actionId } : {},
    })
    const items = normalizeValidationItems(response.data.items)
    const notice = normalizeNotice(response.data.notice)
    return {
      items,
      notice,
      state: resolveCollectionState(items, response.data.state, notice),
    }
  } catch {
    return {
      items: [],
      state: 'error',
      notice: '验证接口请求失败，请稍后重试。',
    }
  }
}

export async function fetchShowcasePipeline(): Promise<ShowcasePipelineData> {
  if (isTestMode) {
    return {
      status: 'LIVE',
      implemented: true,
      note: 'v1-state=live; strategy=keep; data-source=sync_jobs+analysis_jobs+materialized_outputs+actions+validation; stages are synthesized from persisted v1 pipeline state.',
      stages: [
        { name: 'SYNC', state: 'SUCCEEDED', detail: `provider=local-jsonl; productCode=${DEFAULT_PRODUCT_CODE}; fetchedCount=3` },
        { name: 'ANALYSIS', state: 'SUCCEEDED', detail: `productCode=${DEFAULT_PRODUCT_CODE}; jobId=analysis-test-1` },
        { name: 'MATERIALIZATION', state: 'SUCCEEDED', detail: `productCode=${DEFAULT_PRODUCT_CODE}; issueCount=3; outputs align with the latest persisted analysis window` },
        { name: 'ACTIONS', state: 'SUCCEEDED', detail: 'actions=1; planned=1; latestAction=处理：续航体验波动' },
        { name: 'VALIDATION', state: 'SUCCEEDED', detail: 'validationCount=1; latestImprovementRate=11.00%' },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/showcase/pipeline')
    return normalizeShowcaseData(
      response.data as ShowcasePipelineData,
      'v1-state=runtime-unavailable; strategy=keep; data-source=sync_jobs+analysis_jobs+materialized_outputs+actions+validation; 流水线运行态暂不可用。',
    )
  } catch {
    return {
      status: 'RUNTIME_UNAVAILABLE',
      implemented: true,
      note: 'v1-state=runtime-unavailable; strategy=keep; data-source=sync_jobs+analysis_jobs+materialized_outputs+actions+validation; 流水线接口暂不可用。',
      stages: [],
    }
  }
}

export async function fetchShowcaseAgentArena(): Promise<ShowcaseAgentArenaData> {
  if (isTestMode) {
    return {
      status: 'LIVE',
      implemented: true,
      note: 'v1-state=live; strategy=keep; data-source=sync_jobs+analysis_jobs+materialized_outputs+actions+validation; lane rows are synthesized from persisted subsystem state.',
      agents: [
        { agentName: 'sync-lane', role: 'SYNC', state: 'QUEUED', confidence: 0.6 },
        { agentName: 'analysis-lane', role: 'ANALYSIS', state: 'SUCCEEDED', confidence: 0.92 },
        { agentName: 'insight-lane', role: 'QUERY', state: 'SUCCEEDED', confidence: 0.92 },
        { agentName: 'action-validation-lane', role: 'ACTION_VALIDATION', state: 'SUCCEEDED', confidence: 0.92 },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/showcase/agent-arena')
    return normalizeShowcaseData(
      response.data as ShowcaseAgentArenaData,
      'v1-state=runtime-unavailable; strategy=keep; data-source=sync_jobs+analysis_jobs+materialized_outputs+actions+validation; 智能体席位运行态暂不可用。',
    )
  } catch {
    return {
      status: 'RUNTIME_UNAVAILABLE',
      implemented: true,
      note: 'v1-state=runtime-unavailable; strategy=keep; data-source=sync_jobs+analysis_jobs+materialized_outputs+actions+validation; 智能体接口暂不可用。',
      agents: [],
    }
  }
}

export async function fetchShowcaseExplainability(): Promise<ShowcaseExplainabilityData> {
  if (isTestMode) {
    return {
      status: 'LIVE',
      implemented: true,
      note: 'v1-state=live; strategy=keep; data-source=materialized_issue_scores+deterministic-score-weights; 当前解释的是真实评论物化后的固定权重问题得分拆解。',
      featureContributions: [
        { feature: 'negative_rate', weight: 0.41 },
        { feature: 'mention_volume', weight: 0.28 },
        { feature: 'trend_growth', weight: 0.19 },
        { feature: 'competitor_gap', weight: 0.12 },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/showcase/explainability')
    return normalizeShowcaseData(
      response.data as ShowcaseExplainabilityData,
      'v1-state=live; strategy=keep; data-source=deterministic-score-weights; 可解释性运行态暂不可用。',
    )
  } catch {
    return {
      status: 'RUNTIME_UNAVAILABLE',
      implemented: true,
      note: 'v1-state=runtime-unavailable; strategy=keep; data-source=deterministic-score-weights; 可解释性接口暂不可用。',
      featureContributions: [],
    }
  }
}

export async function fetchShowcaseChaos(): Promise<ShowcaseChaosData> {
  if (isTestMode) {
    return {
      status: 'DEGRADED',
      implemented: true,
      note: 'v1-state=runtime-state; strategy=keep; data-source=sync_jobs+analysis_jobs+materialized_outputs; 当前展示最近运行态告警与降级信号。',
      drills: [
        { scenario: 'sync-runtime', state: 'STABLE', detail: `provider=local-jsonl; productCode=${DEFAULT_PRODUCT_CODE}; imported reviews are ready` },
        { scenario: 'analysis-runtime', state: 'STABLE', detail: `productCode=${DEFAULT_PRODUCT_CODE}; latest analysis completed successfully` },
        { scenario: 'materialization-runtime', state: 'STABLE', detail: `productCode=${DEFAULT_PRODUCT_CODE}; materialized outputs are aligned with the latest analysis window` },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/showcase/chaos')
    return normalizeShowcaseData(
      response.data as ShowcaseChaosData,
      'v1-state=runtime-unavailable; strategy=keep; data-source=sync_jobs+analysis_jobs+materialized_outputs; 韧性运行态暂不可用。',
    )
  } catch {
    return {
      status: 'RUNTIME_UNAVAILABLE',
      implemented: true,
      note: 'v1-state=runtime-unavailable; strategy=keep; data-source=sync_jobs+analysis_jobs+materialized_outputs; 韧性演练接口暂不可用。',
      drills: [],
    }
  }
}

export async function previewShowcaseReport(module: string): Promise<ShowcaseReportPreviewData> {
  if (isTestMode) {
    return {
      status: 'LIVE',
      implemented: true,
      note: 'v1-state=live; strategy=keep; data-source=issues+compare+trends+actions+validation; 当前预览由真实查询结果拼装。',
      previewSections: [
        `执行摘要：模块 ${module} 当前预览使用真实查询结果。`,
        '问题摘要：连接稳定性偶发断连仍是最高优先级问题。',
        '动作与验证：已登记 1 个动作，最新验证显示负面率下降 11.00%。',
      ],
    }
  }

  try {
    const response = await apiClient.post('/api/v1/showcase/reports/preview', { module })
    return normalizeShowcaseData(
      response.data as ShowcaseReportPreviewData,
      'v1-state=runtime-unavailable; strategy=keep; data-source=issues+compare+trends+actions+validation; 报告预览运行态暂不可用。',
    )
  } catch {
    return {
      status: 'RUNTIME_UNAVAILABLE',
      implemented: true,
      note: 'v1-state=runtime-unavailable; strategy=keep; data-source=issues+compare+trends+actions+validation; 报告预览接口暂不可用。',
      previewSections: [],
    }
  }
}
