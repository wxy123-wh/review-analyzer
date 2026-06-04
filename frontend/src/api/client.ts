import axios from 'axios'

import type {
  AnalysisJobResponse,
  ChartLoadState,
  CompareItem,
  CompareResponse,
  CompareState,
  ContractState,
  CrawlImportResponse,
  CrawlJobResponse,
  CrawlJobStatus,
  CrawlReviewSample,
  CrawlStartPayload,
  IssueItem,
  IssueResponse,
  JsonlFileCleanPayload,
  JsonlFileCandidate,
  JsonlFileImportPayload,
  PositiveInsightItem,
  PositiveInsightResponse,
  ProductHistoryItem,
  ReviewIntakeStatusResponse,
  ProductTaxonomyPayload,
  ProductTaxonomyResponse,
  ServiceStatus,
  TrendResponse,
  UxLabelOption,
  UxChangeComparisonCreatePayload,
  UxChangeComparisonItem,
  UxChangeComparisonListResponse,
  UxChangeComparisonRecord,
  UxChangeComparisonWindowPreset,
  WordCloudItem,
  WordCloudResponse,
} from '../types/domain'

type ApiBaseLocation = Pick<Location, 'protocol' | 'hostname'>

export function resolveApiBaseURL(
  explicitBaseURL = import.meta.env.VITE_API_BASE_URL,
  currentLocation: ApiBaseLocation | undefined = typeof window === 'undefined' ? undefined : window.location,
): string {
  const configuredBaseURL = typeof explicitBaseURL === 'string' ? explicitBaseURL.trim() : ''
  if (configuredBaseURL) {
    return configuredBaseURL
  }

  const protocol = currentLocation?.protocol === 'https:' ? 'https:' : 'http:'
  const rawHostname = currentLocation?.hostname?.trim() || 'localhost'
  const hostname = rawHostname.includes(':') && !rawHostname.startsWith('[') ? `[${rawHostname}]` : rawHostname
  return `${protocol}//${hostname}:8080`
}

const baseURL = resolveApiBaseURL()
const isTestMode = import.meta.env.MODE === 'test'
export const DEFAULT_PRODUCT_CODE = 'jd-100127936932'

const CANONICAL_ASPECT_ALIASES: Record<string, string> = {
  all: 'all',
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
  general: 'general',
}

const ASPECT_DISPLAY_LABELS: Record<string, string> = {
  all: '全部',
  battery: '电池与续航',
  bluetooth: '连接与稳定性',
  'noise-canceling': '降噪与环境声',
  comfort: '佩戴与人体工学',
  microphone: '通话与麦克风',
  general: '综合体验',
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

function buildRecentReviewTestSamples(productName?: string): CrawlReviewSample[] {
  return [
    '蓝牙连接偶尔断开，通话声音也不够清晰。',
    '佩戴很舒服，通勤戴两个小时耳朵不疼。',
    '降噪一般，地铁里还是能听到明显噪音。',
    '续航比预期短，下午就需要补电。',
    '包装完整，物流速度很快。',
    '触控操作有点不灵敏，经常误触。',
    '音质清晰，低音效果比旧款好。',
    '连接手机很快，但是切换设备不稳定。',
    '耳塞尺寸合适，跑步时不容易掉。',
    '客服回复及时，换货处理比较顺利。',
  ].map((content, index) => ({
    sourceReviewId: `rv-${String(index + 1).padStart(3, '0')}`,
    productName,
    content,
    rating: index % 3 === 0 ? '2' : index % 3 === 1 ? '5' : '3',
    reviewTime: `2026-06-${String(Math.min(index + 1, 9)).padStart(2, '0')}T10:20:00Z`,
  }))
}

function buildManualJsonlTestResponse(
  payload: JsonlFileCleanPayload,
  options: { importJobId: string; insertedReviewCount: number; updatedReviewCount: number; totalReviewCount: number; handoffNote: string },
): CrawlImportResponse {
  const normalizedProductCode = payload.productCode.trim() || DEFAULT_PRODUCT_CODE
  const normalizedInputPath = payload.inputPath.trim()
  const platform = payload.platform?.trim() || 'jd'
  const productName = payload.productName?.trim() || ''
  return {
    jobId: 'manual-jsonl',
    importJobId: options.importJobId,
    productCode: normalizedProductCode,
    productName: productName || undefined,
    provider: 'local-jsonl',
    platform,
    rawOutputPath: normalizedInputPath,
    cleanedOutputPath: `crawler/output/cleaned/cleaned_reviews_${normalizedProductCode}.jsonl`,
    removedOutputPath: `crawler/output/cleaned/removed_reviews_${normalizedProductCode}.jsonl`,
    cleaningSummaryPath: `crawler/output/cleaned/cleaning_summary_${normalizedProductCode}.json`,
    receivedCount: 126,
    insertedReviewCount: options.insertedReviewCount,
    updatedReviewCount: options.updatedReviewCount,
    totalReviewCount: options.totalReviewCount,
    cleaningSummary: {
      rawCount: 128,
      cleanedCount: 126,
      removedCount: 2,
      htmlCleanedCount: 1,
      exactDuplicateCount: 1,
      emptyContentCount: 1,
      invalidJsonCount: 0,
      placeholderContentCount: 1,
    },
    sampleReviews: buildRecentReviewTestSamples(productName || undefined).slice(0, 3),
    analysisHandoffStatus: 'READY_FOR_ANALYSIS',
    analysisHandoffNote: options.handoffNote,
    importedAt: '2026-06-03T00:06:00Z',
  }
}

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
        ourMentionCount: typeof record.ourMentionCount === 'number' ? record.ourMentionCount : undefined,
        competitorMentionCount: typeof record.competitorMentionCount === 'number' ? record.competitorMentionCount : undefined,
        ourNegativeRate: typeof record.ourNegativeRate === 'number' ? record.ourNegativeRate : undefined,
        competitorNegativeRate:
          typeof record.competitorNegativeRate === 'number' ? record.competitorNegativeRate : undefined,
        negativeRateGap: typeof record.negativeRateGap === 'number' ? record.negativeRateGap : undefined,
      }
    })
    .filter((item): item is CompareItem => item !== null)
}

function normalizeCompareState(state: unknown): CompareState {
  if (typeof state !== 'string') {
    return 'error'
  }
  switch (state.trim()) {
    case 'success':
    case 'taxonomy-mismatch':
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

function normalizeNumber(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function normalizeText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

export function normalizeUxSecondaryLabel(value: unknown, fallback: unknown): string {
  const label = normalizeText(value)
  if (label) {
    return label
  }
  const fallbackLabel = normalizeText(fallback)
  return ASPECT_DISPLAY_LABELS[fallbackLabel] ?? fallbackLabel
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
    return []
  }
  const labels = rawLabels
    .map((label, index) => normalizeUxLabelOption(label, index))
    .filter((label): label is UxLabelOption => label !== null)
  return labels
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

function normalizeUxChangeComparisonState(state: unknown): ContractState {
  return normalizeCollectionState(state) ?? 'error'
}

function normalizeWindowPreset(value: unknown): UxChangeComparisonWindowPreset {
  const normalized = normalizeText(value).toUpperCase().replaceAll('-', '_')
  switch (normalized) {
    case 'P14D':
    case 'TWO_WEEKS':
      return 'TWO_WEEKS'
    case 'P90D':
    case 'THREE_MONTHS':
      return 'THREE_MONTHS'
    case 'CUSTOM':
      return normalized
    case 'P1M':
    case 'P30D':
    case 'ONE_MONTH':
    default:
      return 'ONE_MONTH'
  }
}

function normalizeUxChangeComparisonItems(rawItems: unknown): UxChangeComparisonItem[] {
  if (!Array.isArray(rawItems)) {
    return []
  }
  return rawItems
    .map((item) => {
      if (typeof item !== 'object' || item === null) {
        return null
      }
      const record = item as Record<string, unknown>
      const uxSecondaryLabel = normalizeText(record.uxSecondaryLabel)
      if (!uxSecondaryLabel) {
        return null
      }
      return {
        uxPrimaryLabel: normalizeNotice(record.uxPrimaryLabel),
        uxSecondaryLabel,
        beforeMentionCount: typeof record.beforeMentionCount === 'number' ? record.beforeMentionCount : 0,
        afterMentionCount: typeof record.afterMentionCount === 'number' ? record.afterMentionCount : 0,
        beforeNegativeRate: typeof record.beforeNegativeRate === 'number' ? record.beforeNegativeRate : 0,
        afterNegativeRate: typeof record.afterNegativeRate === 'number' ? record.afterNegativeRate : 0,
        improvementRate: typeof record.improvementRate === 'number' ? record.improvementRate : 0,
        summary: normalizeNotice(record.summary),
      }
    })
    .filter((item): item is UxChangeComparisonItem => item !== null)
}

function normalizeUxChangeComparisonRecord(payload: unknown, fallbackProductCode: string): UxChangeComparisonRecord {
  const record = typeof payload === 'object' && payload !== null ? (payload as Record<string, unknown>) : {}
  const beforeWindow =
    typeof record.beforeWindow === 'object' && record.beforeWindow !== null
      ? (record.beforeWindow as Record<string, unknown>)
      : {}
  const afterWindow =
    typeof record.afterWindow === 'object' && record.afterWindow !== null
      ? (record.afterWindow as Record<string, unknown>)
      : {}
  const id = normalizeText(record.id) || normalizeText(record.comparisonId) || normalizeText(record.checkpointId)
  const productCode = normalizeText(record.productCode) || fallbackProductCode
  const items = normalizeUxChangeComparisonItems(record.items)
  const state = normalizeUxChangeComparisonState(record.state ?? (items.length > 0 ? 'success' : 'empty'))
  return {
    id,
    productCode,
    productName: normalizeNotice(record.productName),
    changeDate: normalizeText(record.changeDate),
    windowPreset: normalizeWindowPreset(record.windowPreset),
    customBeforeDays: typeof record.customBeforeDays === 'number' ? record.customBeforeDays : undefined,
    customAfterDays: typeof record.customAfterDays === 'number' ? record.customAfterDays : undefined,
    beforeWindowStart: normalizeNotice(record.beforeWindowStart) ?? normalizeNotice(beforeWindow.start),
    beforeWindowEnd: normalizeNotice(record.beforeWindowEnd) ?? normalizeNotice(beforeWindow.end),
    afterWindowStart: normalizeNotice(record.afterWindowStart) ?? normalizeNotice(afterWindow.start),
    afterWindowEnd: normalizeNotice(record.afterWindowEnd) ?? normalizeNotice(afterWindow.end),
    state,
    summary: normalizeNotice(record.summary),
    notice: normalizeNotice(record.notice),
    items,
  }
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
    labels,
    state: labels.length > 0 ? 'success' : 'degraded',
    notice: labels.length > 0 ? undefined : '当前 taxonomy 暂无有效 UX 标签，请先在 taxonomy 模块补齐后再绑定。',
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

function normalizeCrawlReviewSamples(rawSamples: unknown): CrawlReviewSample[] {
  if (!Array.isArray(rawSamples)) {
    return []
  }
  return rawSamples
    .map((sample) => {
      if (typeof sample !== 'object' || sample === null) {
        return null
      }
      const record = sample as Record<string, unknown>
      const content = normalizeText(record.content)
      if (!content) {
        return null
      }
      return {
        sourceReviewId: normalizeNotice(record.sourceReviewId),
        productName: normalizeNotice(record.productName),
        content,
        rating: normalizeNotice(record.rating),
        reviewTime: normalizeNotice(record.reviewTime),
      }
    })
    .filter((sample): sample is CrawlReviewSample => sample !== null)
}

function normalizeJsonlFileCandidates(rawItems: unknown): JsonlFileCandidate[] {
  if (!Array.isArray(rawItems)) {
    return []
  }
  return rawItems
    .map((item) => {
      if (typeof item !== 'object' || item === null) {
        return null
      }
      const record = item as Record<string, unknown>
      const path = normalizeText(record.path)
      const fileName = normalizeText(record.fileName)
      if (!path) {
        return null
      }
      return {
        path,
        fileName: fileName || path,
        productCode: normalizeNotice(record.productCode),
        productName: normalizeNotice(record.productName),
        sizeBytes: typeof record.sizeBytes === 'number' ? record.sizeBytes : 0,
        lastModifiedAt: normalizeNotice(record.lastModifiedAt),
        sampleReviews: normalizeCrawlReviewSamples(record.sampleReviews),
      }
    })
    .filter((item): item is JsonlFileCandidate => item !== null)
}

function normalizeImportedProducts(rawItems: unknown): ProductHistoryItem[] {
  if (!Array.isArray(rawItems)) {
    return []
  }
  return rawItems
    .map((item) => {
      if (typeof item !== 'object' || item === null) {
        return null
      }
      const record = item as Record<string, unknown>
      const productCode = normalizeText(record.productCode)
      if (!productCode) {
        return null
      }
      return {
        productCode,
        productName: normalizeNotice(record.productName),
        importedReviewCount: normalizeNumber(record.importedReviewCount) ?? 0,
        analyzedReviewCount: normalizeNumber(record.analyzedReviewCount) ?? 0,
        downstreamReady: record.downstreamReady === true,
        taxonomyBound: record.taxonomyBound === true,
        latestAnalysisStatus: normalizeNotice(record.latestAnalysisStatus),
        latestImportedAt: normalizeNotice(record.latestImportedAt),
        createdAt: normalizeNotice(record.createdAt),
      }
    })
    .filter((item): item is ProductHistoryItem => item !== null)
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
    capturedPackets: typeof record.capturedPackets === 'number' ? record.capturedPackets : undefined,
    outputPath: normalizeNotice(record.outputPath),
    progressPath: normalizeNotice(record.progressPath),
    cleanCommand: normalizeNotice(record.cleanCommand),
    importCommand: normalizeNotice(record.importCommand),
    sampleReviews: normalizeCrawlReviewSamples(record.sampleReviews),
    errorMessage: normalizeNotice(record.errorMessage),
    analysisHandoffStatus: normalizeNotice(record.analysisHandoffStatus),
    analysisHandoffNote: normalizeNotice(record.analysisHandoffNote),
    startedAt: normalizeNotice(record.startedAt),
    finishedAt: normalizeNotice(record.finishedAt),
  }
}

function normalizeCrawlImportResponse(payload: unknown, fallbackJobId: string, fallbackProductCode: string): CrawlImportResponse {
  const record = typeof payload === 'object' && payload !== null ? (payload as Record<string, unknown>) : {}
  const cleaningSummary =
    typeof record.cleaningSummary === 'object' && record.cleaningSummary !== null
      ? (record.cleaningSummary as Record<string, unknown>)
      : {}
  return {
    jobId: normalizeText(record.jobId) || fallbackJobId,
    importJobId: normalizeText(record.importJobId),
    productCode: normalizeText(record.productCode) || fallbackProductCode,
    productName: normalizeNotice(record.productName),
    provider: normalizeText(record.provider) || 'local-jsonl',
    platform: normalizeText(record.platform) || 'jd',
    rawOutputPath: normalizeText(record.rawOutputPath),
    cleanedOutputPath: normalizeText(record.cleanedOutputPath),
    removedOutputPath: normalizeText(record.removedOutputPath),
    cleaningSummaryPath: normalizeText(record.cleaningSummaryPath),
    receivedCount: typeof record.receivedCount === 'number' ? record.receivedCount : 0,
    insertedReviewCount: typeof record.insertedReviewCount === 'number' ? record.insertedReviewCount : 0,
    updatedReviewCount: typeof record.updatedReviewCount === 'number' ? record.updatedReviewCount : 0,
    totalReviewCount: typeof record.totalReviewCount === 'number' ? record.totalReviewCount : 0,
    cleaningSummary,
    sampleReviews: normalizeCrawlReviewSamples(record.sampleReviews),
    analysisHandoffStatus: normalizeText(record.analysisHandoffStatus) || 'READY_FOR_ANALYSIS',
    analysisHandoffNote: normalizeNotice(record.analysisHandoffNote),
    importedAt: normalizeNotice(record.importedAt),
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
    totalReviewCount: normalizeNumber(record.totalReviewCount),
    processedReviewCount: normalizeNumber(record.processedReviewCount),
    progressPercent: normalizeNumber(record.progressPercent),
    currentStage: normalizeNotice(record.currentStage),
    materializedReviewCount: normalizeNumber(record.materializedReviewCount),
    semanticLabelCount: normalizeNumber(record.semanticLabelCount),
    issueClusterCount: normalizeNumber(record.issueClusterCount),
    downstreamReady: record.downstreamReady === true,
  }
}

function normalizeReviewIntakeStatus(payload: unknown, fallbackProductCode: string): ReviewIntakeStatusResponse {
  const record = typeof payload === 'object' && payload !== null ? (payload as Record<string, unknown>) : {}
  const cleaningSummary =
    typeof record.cleaningSummary === 'object' && record.cleaningSummary !== null
      ? (record.cleaningSummary as Record<string, unknown>)
      : {}
  const latestAnalysisJob =
    typeof record.latestAnalysisJob === 'object' && record.latestAnalysisJob !== null
      ? normalizeAnalysisJob(record.latestAnalysisJob, fallbackProductCode)
      : undefined
  return {
    productCode: normalizeText(record.productCode) || fallbackProductCode,
    productName: normalizeNotice(record.productName),
    rawOutputPath: normalizeText(record.rawOutputPath),
    rawJsonlExists: record.rawJsonlExists === true,
    rawCount: typeof record.rawCount === 'number' ? record.rawCount : 0,
    cleanedOutputPath: normalizeText(record.cleanedOutputPath),
    cleanedJsonlExists: record.cleanedJsonlExists === true,
    removedOutputPath: normalizeText(record.removedOutputPath),
    cleaningSummaryPath: normalizeText(record.cleaningSummaryPath),
    cleaningSummary,
    taxonomyBound: record.taxonomyBound === true,
    taxonomyId: normalizeNumber(record.taxonomyId),
    taxonomyVersion: normalizeNumber(record.taxonomyVersion),
    importedReviewCount: typeof record.importedReviewCount === 'number' ? record.importedReviewCount : 0,
    analyzedReviewCount: typeof record.analyzedReviewCount === 'number' ? record.analyzedReviewCount : 0,
    downstreamReady: record.downstreamReady === true,
    latestAnalysisJob,
    recentReviews: normalizeCrawlReviewSamples(record.recentReviews).slice(0, 10),
    stage: normalizeText(record.stage) || 'RAW_READY',
    notice: normalizeNotice(record.notice),
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
      if (taxonomies.length === 0) {
        return {
          productCode,
          category,
          labels: [],
          state: 'empty',
          notice: '暂无可选 taxonomy，请先在 taxonomy 模块创建。',
        }
      }
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
        labels: [],
        state: 'error',
        notice: '标签配置接口暂不可用，请检查后端服务后重新读取 taxonomy。',
      }
    }
  }
}

export async function fetchTaxonomies(productCode = DEFAULT_PRODUCT_CODE): Promise<ProductTaxonomyResponse[]> {
  if (isTestMode) {
    return [
      {
        taxonomyId: 1,
        name: '通用电商 UX 标签',
        productCode,
        category: 'general-product',
        labels: DEFAULT_UX_LABELS,
        state: 'success',
        notice: '测试模式下使用通用电商 UX 标签组合。',
      },
      {
        taxonomyId: 2,
        name: '蓝牙耳机 UX 标签',
        productCode,
        category: 'bluetooth-headset',
        labels: [
          {
            id: 'headset-connection',
            uxPrimaryLabel: '产品硬件',
            uxSecondaryLabel: '连接与稳定性',
            description: '蓝牙连接、断连和多设备切换。',
            enabled: true,
          },
          {
            id: 'headset-battery',
            uxPrimaryLabel: '产品硬件',
            uxSecondaryLabel: '续航与充电',
            description: '电量、充电和续航体验。',
            enabled: true,
          },
        ],
        state: 'success',
        notice: '测试模式下返回蓝牙耳机 UX 标签组合。',
      },
    ]
  }

  try {
    const response = await apiClient.get('/api/v1/taxonomies')
    const taxonomies = Array.isArray(response.data) ? response.data : []
    return taxonomies
      .map((item) => {
        const record = typeof item === 'object' && item !== null ? (item as Record<string, unknown>) : {}
        const fallbackCategory = normalizeText(record.productCategory) || 'general-product'
        return normalizeTaxonomyRecord(item, productCode, fallbackCategory)
      })
      .filter((item) => item.taxonomyId !== undefined)
  } catch {
    return []
  }
}

export async function saveTaxonomyDefinition(payload: ProductTaxonomyPayload): Promise<ProductTaxonomyResponse> {
  if (isTestMode) {
    return {
      taxonomyId: payload.taxonomyId ?? Math.max(1, payload.labels.length + 1),
      name: payload.name?.trim() || `${payload.category} UX 标签`,
      productCode: payload.productCode,
      category: payload.category,
      labels: normalizeUxLabelOptions(payload.labels),
      state: 'success',
      notice: 'taxonomy 已在测试模式中保存。',
      updatedAt: '2026-06-04T00:00:00Z',
    }
  }

  const taxonomyRequest = buildTaxonomyRequest(payload)
  const taxonomyResponse = payload.taxonomyId
    ? await apiClient.put(`/api/v1/taxonomies/${encodeURIComponent(`${payload.taxonomyId}`)}`, taxonomyRequest)
    : await apiClient.post('/api/v1/taxonomies', taxonomyRequest)
  return normalizeTaxonomyRecord(taxonomyResponse.data, payload.productCode, payload.category)
}

export async function bindProductTaxonomy(
  productCode: string,
  taxonomyId: number,
  fallbackCategory = 'general-product',
): Promise<ProductTaxonomyResponse> {
  const normalizedProductCode = productCode.trim() || DEFAULT_PRODUCT_CODE

  if (isTestMode) {
    return {
      taxonomyId,
      name: `taxonomy #${taxonomyId}`,
      productCode: normalizedProductCode,
      category: fallbackCategory,
      labels: DEFAULT_UX_LABELS,
      state: 'success',
      notice: 'taxonomy 已在测试模式中绑定到商品。',
    }
  }

  const bindingResponse = await apiClient.put(`/api/v1/products/${encodeURIComponent(normalizedProductCode)}/taxonomy`, {
    taxonomyId,
  })
  return normalizeProductTaxonomyBinding(bindingResponse.data, normalizedProductCode, fallbackCategory)
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
      capturedPackets: 8,
      outputPath: `crawler/output/raw_reviews_${productCode}.jsonl`,
      progressPath: `crawler/output/progress/jd_${productCode}.json`,
      cleanCommand: `python pipeline/clean_reviews.py --input "crawler/output/raw_reviews_${productCode}.jsonl"`,
      importCommand: `python crawler/import_reviews.py --input "pipeline/output/cleaned_reviews_${productCode}.jsonl" --product-code "${productCode}"`,
      sampleReviews: [
        {
          sourceReviewId: 'rv-001',
          content: '蓝牙连接偶尔断开，通话声音也不够清晰。',
          rating: '2',
          reviewTime: '2026-06-01T10:20:00Z',
        },
      ],
      analysisHandoffStatus: 'READY_FOR_ANALYSIS',
      analysisHandoffNote: '采集完成，请先清洗导入 JSONL，再启动分析。',
      finishedAt: '2026-06-03T00:05:00Z',
    }
  }

  const response = await apiClient.get(`/api/v1/crawl/jobs/${encodeURIComponent(jobId)}`)
  return normalizeCrawlJob(response.data, productCode)
}

export async function importCrawlJob(jobId: string, productCode = DEFAULT_PRODUCT_CODE): Promise<CrawlImportResponse> {
  const normalizedJobId = jobId.trim()
  const normalizedProductCode = productCode.trim() || DEFAULT_PRODUCT_CODE

  if (isTestMode) {
    return {
      jobId: normalizedJobId,
      importJobId: 'import-test-1',
      productCode: normalizedProductCode,
      provider: 'local-jsonl',
      platform: 'jd',
      rawOutputPath: `crawler/output/raw_reviews_${normalizedProductCode}.jsonl`,
      cleanedOutputPath: `crawler/output/cleaned/cleaned_reviews_${normalizedProductCode}.jsonl`,
      removedOutputPath: `crawler/output/cleaned/removed_reviews_${normalizedProductCode}.jsonl`,
      cleaningSummaryPath: `crawler/output/cleaned/cleaning_summary_${normalizedProductCode}.json`,
      receivedCount: 126,
      insertedReviewCount: 120,
      updatedReviewCount: 6,
      totalReviewCount: 126,
      cleaningSummary: {
        rawCount: 128,
        cleanedCount: 126,
        removedCount: 2,
        htmlCleanedCount: 1,
        exactDuplicateCount: 1,
        emptyContentCount: 1,
        invalidJsonCount: 0,
        placeholderContentCount: 0,
      },
      sampleReviews: [
        {
          sourceReviewId: 'rv-001',
          content: '蓝牙连接偶尔断开，通话声音也不够清晰。',
          rating: '2',
          reviewTime: '2026-06-01T10:20:00Z',
        },
      ],
      analysisHandoffStatus: 'READY_FOR_ANALYSIS',
      analysisHandoffNote: 'JSONL 已清洗并导入，可以启动分析。',
      importedAt: '2026-06-03T00:06:00Z',
    }
  }

  const response = await apiClient.post(`/api/v1/crawl/jobs/${encodeURIComponent(normalizedJobId)}/import`)
  return normalizeCrawlImportResponse(response.data, normalizedJobId, normalizedProductCode)
}

export async function fetchJsonlFiles(): Promise<JsonlFileCandidate[]> {
  if (isTestMode) {
    return [
      {
        path: `crawler/output/raw_reviews_${DEFAULT_PRODUCT_CODE}.jsonl`,
        fileName: `raw_reviews_${DEFAULT_PRODUCT_CODE}.jsonl`,
        productCode: DEFAULT_PRODUCT_CODE,
        productName: '小米 Buds 5 Pro',
        sizeBytes: 4096,
        lastModifiedAt: '2026-06-03T00:05:00Z',
        sampleReviews: [
          {
            sourceReviewId: 'rv-001',
            productName: '小米 Buds 5 Pro',
            content: '蓝牙连接偶尔断开，通话声音也不够清晰。',
            rating: '2',
            reviewTime: '2026-06-01T10:20:00Z',
          },
        ],
      },
      {
        path: 'crawler/output/raw_reviews_jd-new-product.jsonl',
        fileName: 'raw_reviews_jd-new-product.jsonl',
        productCode: 'jd-new-product',
        productName: 'OPPO Enco Free4',
        sizeBytes: 2048,
        lastModifiedAt: '2026-06-02T00:05:00Z',
        sampleReviews: [],
      },
    ]
  }

  const response = await apiClient.get('/api/v1/reviews/jsonl-files')
  return normalizeJsonlFileCandidates(response.data)
}

export async function fetchImportedProducts(limit = 50): Promise<ProductHistoryItem[]> {
  if (isTestMode) {
    return [
      {
        productCode: DEFAULT_PRODUCT_CODE,
        productName: '小米 Xiaomi Buds 5',
        importedReviewCount: 746,
        analyzedReviewCount: 746,
        downstreamReady: true,
        taxonomyBound: true,
        latestAnalysisStatus: 'SUCCEEDED',
        latestImportedAt: '2026-06-04T01:00:00Z',
      },
      {
        productCode: 'jd-new-product',
        productName: '小米 Buds 5 Pro',
        importedReviewCount: 126,
        analyzedReviewCount: 80,
        downstreamReady: false,
        taxonomyBound: true,
        latestAnalysisStatus: 'RUNNING',
        latestImportedAt: '2026-06-03T00:06:00Z',
      },
    ].slice(0, Math.max(1, limit))
  }

  const response = await apiClient.get('/api/v1/reviews/imported-products', {
    params: { limit },
  })
  return normalizeImportedProducts(response.data)
}

export async function fetchReviewIntakeStatus(
  productCode = DEFAULT_PRODUCT_CODE,
  inputPath = '',
): Promise<ReviewIntakeStatusResponse> {
  const normalizedProductCode = productCode.trim() || DEFAULT_PRODUCT_CODE
  const normalizedInputPath = inputPath.trim()

  if (isTestMode) {
    return {
      productCode: normalizedProductCode,
      productName: normalizedProductCode === 'jd-new-product' ? '小米 Buds 5 Pro' : undefined,
      rawOutputPath: normalizedInputPath || `crawler/output/raw_reviews_${normalizedProductCode}.jsonl`,
      rawJsonlExists: true,
      rawCount: 128,
      cleanedOutputPath: `crawler/output/cleaned/cleaned_reviews_${normalizedProductCode}.jsonl`,
      cleanedJsonlExists: true,
      removedOutputPath: `crawler/output/cleaned/removed_reviews_${normalizedProductCode}.jsonl`,
      cleaningSummaryPath: `crawler/output/cleaned/cleaning_summary_${normalizedProductCode}.json`,
      cleaningSummary: {
        rawCount: 128,
        cleanedCount: 126,
        removedCount: 2,
        htmlCleanedCount: 1,
        exactDuplicateCount: 1,
        emptyContentCount: 1,
        invalidJsonCount: 0,
        placeholderContentCount: 1,
      },
      taxonomyBound: true,
      taxonomyId: 1,
      taxonomyVersion: 1,
      importedReviewCount: 126,
      analyzedReviewCount: 80,
      downstreamReady: false,
      latestAnalysisJob: {
        jobId: 'analysis-test-1',
        productCode: normalizedProductCode,
        status: 'RUNNING',
        totalReviewCount: 126,
        processedReviewCount: 80,
        progressPercent: 62,
        materializedReviewCount: 80,
        semanticLabelCount: 80,
        issueClusterCount: 2,
        downstreamReady: false,
        currentStage: 'LLM 已完成 80 / 126 条',
        startedAt: '2026-06-03T00:06:00Z',
      },
      recentReviews: buildRecentReviewTestSamples(
        normalizedProductCode === 'jd-new-product' ? '小米 Buds 5 Pro' : undefined,
      ),
      stage: 'ANALYZING',
      notice: 'LLM 正在分析评论，可查看任务进度和最近评论列表。',
    }
  }

  const response = await apiClient.get('/api/v1/reviews/intake-status', {
    params: {
      productCode: normalizedProductCode,
      ...(normalizedInputPath ? { inputPath: normalizedInputPath } : {}),
    },
  })
  return normalizeReviewIntakeStatus(response.data, normalizedProductCode)
}

export async function cleanJsonlFile(payload: JsonlFileCleanPayload): Promise<CrawlImportResponse> {
  const normalizedProductCode = payload.productCode.trim() || DEFAULT_PRODUCT_CODE
  const normalizedInputPath = payload.inputPath.trim()
  const platform = payload.platform?.trim() || 'jd'
  const productName = payload.productName?.trim() || ''

  if (isTestMode) {
    return buildManualJsonlTestResponse(
      { productCode: normalizedProductCode, productName, inputPath: normalizedInputPath, platform },
      {
        importJobId: 'clean-jsonl-test-1',
        insertedReviewCount: 0,
        updatedReviewCount: 0,
        totalReviewCount: 0,
        handoffNote: 'JSONL 已清洗完成，请检查摘要和样本，再绑定 taxonomy 并导入数据库。',
      },
    )
  }

  const response = await apiClient.post('/api/v1/reviews/clean-jsonl', {
    productCode: normalizedProductCode,
    productName,
    inputPath: normalizedInputPath,
    platform,
  })
  return normalizeCrawlImportResponse(response.data, 'manual-jsonl-clean', normalizedProductCode)
}

export async function importJsonlFile(payload: JsonlFileImportPayload): Promise<CrawlImportResponse> {
  const normalizedProductCode = payload.productCode.trim() || DEFAULT_PRODUCT_CODE
  const normalizedInputPath = payload.inputPath.trim()
  const platform = payload.platform?.trim() || 'jd'
  const productName = payload.productName?.trim() || ''

  if (isTestMode) {
    return buildManualJsonlTestResponse(
      { productCode: normalizedProductCode, productName, inputPath: normalizedInputPath, platform },
      {
        importJobId: 'import-jsonl-test-1',
        insertedReviewCount: 120,
        updatedReviewCount: 6,
        totalReviewCount: 126,
        handoffNote: 'JSONL 已导入数据库，可以启动分析。',
      },
    )
  }

  const response = await apiClient.post('/api/v1/reviews/import-jsonl', {
    productCode: normalizedProductCode,
    productName,
    inputPath: normalizedInputPath,
    platform,
    replaceExisting: payload.replaceExisting === true,
  })
  return normalizeCrawlImportResponse(response.data, 'manual-jsonl', normalizedProductCode)
}

export async function startAnalysis(productCode = DEFAULT_PRODUCT_CODE): Promise<AnalysisJobResponse> {
  if (isTestMode) {
    return {
      jobId: 'analysis-test-1',
      productCode,
      status: 'QUEUED',
      startedAt: '2026-06-03T00:06:00Z',
    }
  }

  const response = await apiClient.post('/api/v1/analysis/jobs', { productCode })
  return normalizeAnalysisJob(response.data, productCode)
}

export async function fetchAnalysisJob(jobId: string, productCode = DEFAULT_PRODUCT_CODE): Promise<AnalysisJobResponse> {
  const normalizedJobId = jobId.trim()

  if (isTestMode) {
    return {
      jobId: normalizedJobId || 'analysis-test-1',
      productCode,
      status: 'SUCCEEDED',
      startedAt: '2026-06-03T00:06:00Z',
      finishedAt: '2026-06-03T00:06:12Z',
      totalReviewCount: 126,
      processedReviewCount: 126,
      progressPercent: 100,
      materializedReviewCount: 126,
      semanticLabelCount: 126,
      issueClusterCount: 2,
      downstreamReady: true,
      currentStage: '分析结果已写入 126 条语义评论',
    }
  }

  const response = await apiClient.get(`/api/v1/analysis/jobs/${encodeURIComponent(normalizedJobId)}`)
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
  comparisonProductCode = '',
): Promise<CompareResponse> {
  const normalizedProductCode = productCode.trim()
  const normalizedComparisonProductCode = comparisonProductCode.trim()

  if (isTestMode) {
    if (!normalizedComparisonProductCode) {
      return {
        productCode: normalizedProductCode,
        productName: undefined,
        comparisonProductCode: '',
        comparisonProductName: undefined,
        state: 'missing-target',
        items: [],
        notice: '请输入主商品和竞品编号。',
      }
    }
    return {
      productCode: normalizedProductCode,
      productName: undefined,
      comparisonProductCode: normalizedComparisonProductCode,
      comparisonProductName: undefined,
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
      params: { productCode: normalizedProductCode, comparisonProductCode: normalizedComparisonProductCode },
    })
    return {
      productCode: response.data.productCode ?? normalizedProductCode,
      productName: normalizeNotice(response.data.productName),
      comparisonProductCode:
        typeof response.data.comparisonProductCode === 'string'
          ? response.data.comparisonProductCode
          : normalizedComparisonProductCode,
      comparisonProductName: normalizeNotice(response.data.comparisonProductName),
      items: normalizeCompareItems(response.data.items),
      state: normalizeCompareState(response.data.state),
      notice: typeof response.data.notice === 'string' ? response.data.notice : undefined,
    }
  } catch {
    return {
      productCode: normalizedProductCode,
      productName: undefined,
      comparisonProductCode: normalizedComparisonProductCode,
      comparisonProductName: undefined,
      items: [],
      state: 'error',
      notice: '竞品对比接口请求失败，请稍后重试。',
    }
  }
}

export async function fetchTrends(
  productCode = DEFAULT_PRODUCT_CODE,
  aspect = 'battery',
  uxSecondaryLabel?: string,
): Promise<TrendResponse> {
  const fallbackAspect = normalizeAspectCode(aspect, 'battery')
  const requestedUxSecondaryLabel = normalizeNotice(uxSecondaryLabel)

  if (isTestMode) {
    return {
      aspect: fallbackAspect,
      uxSecondaryLabel: normalizeUxSecondaryLabel(requestedUxSecondaryLabel, fallbackAspect),
      points: [
        { period: '2026-W06', negativeRate: 0.31, mentionVolume: 75 },
        { period: '2026-W09', negativeRate: 0.4, mentionVolume: 105 },
      ],
      state: 'success',
    }
  }

  try {
    const params: Record<string, string> = { productCode, aspect }
    if (requestedUxSecondaryLabel) {
      params.uxSecondaryLabel = requestedUxSecondaryLabel
    }
    const response = await apiClient.get('/api/v1/trends', { params })
    const points = Array.isArray(response.data.points) ? response.data.points : []
    const notice = normalizeNotice(response.data.notice)
    return {
      aspect: normalizeAspectCode(response.data.aspect, 'battery') || fallbackAspect,
      uxPrimaryLabel: normalizeNotice(response.data.uxPrimaryLabel),
      uxSecondaryLabel: normalizeUxSecondaryLabel(response.data.uxSecondaryLabel ?? requestedUxSecondaryLabel, response.data.aspect),
      points,
      state: resolveChartState(points, response.data.state, notice),
      notice,
    }
  } catch (error) {
    return {
      aspect: fallbackAspect,
      uxSecondaryLabel: normalizeUxSecondaryLabel(requestedUxSecondaryLabel, fallbackAspect),
      points: [],
      state: resolveRequestState(error),
    }
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
      const sentimentTag = normalizeSentimentTag(record.sentimentTag)
      return {
        keyword,
        frequency,
        weight,
        sentimentTag,
        partOfSpeech: normalizeText(record.partOfSpeech) || '未标注',
        wordType: normalizeText(record.wordType) || '商品属性',
      }
    })
    .filter((item): item is WordCloudItem => item !== null)
}

function normalizeSentimentTag(value: unknown): string {
  const normalized = normalizeText(value).toUpperCase()
  if (normalized === 'POSITIVE' || normalized === '正向') {
    return 'POSITIVE'
  }
  if (normalized === 'NEGATIVE' || normalized === '负向') {
    return 'NEGATIVE'
  }
  if (normalized === 'NEUTRAL' || normalized === '中性') {
    return 'NEUTRAL'
  }
  return 'NEUTRAL'
}

export async function fetchWordCloud(
  productCode = DEFAULT_PRODUCT_CODE,
  aspect = 'all',
): Promise<WordCloudResponse> {
  if (isTestMode) {
    return {
      productCode,
      aspect,
      uxSecondaryLabel: normalizeUxSecondaryLabel('', aspect),
      items: [
        { keyword: '续航', frequency: 42, weight: 0.92, sentimentTag: 'POSITIVE', partOfSpeech: '名词', wordType: '体验维度' },
        { keyword: '断连', frequency: 31, weight: 0.85, sentimentTag: 'NEGATIVE', partOfSpeech: '动词', wordType: '问题词' },
        { keyword: '降噪', frequency: 27, weight: 0.78, sentimentTag: 'POSITIVE', partOfSpeech: '动词', wordType: '体验维度' },
        { keyword: '佩戴', frequency: 24, weight: 0.7, sentimentTag: 'NEUTRAL', partOfSpeech: '动词', wordType: '体验维度' },
      ],
      notice: '评论关键词已按词频聚合。',
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
      uxSecondaryLabel: normalizeUxSecondaryLabel('', aspect),
      items: [],
      state: resolveRequestState(error),
    }
  }
}

export async function createUxChangeComparison(
  payload: UxChangeComparisonCreatePayload,
): Promise<UxChangeComparisonRecord> {
  const productCode = payload.productCode.trim()
  if (isTestMode) {
    return {
      id: 'ux-change-test-1',
      productCode,
      productName: undefined,
      changeDate: payload.changeDate,
      windowPreset: payload.windowPreset,
      customBeforeDays: payload.customBeforeDays,
      customAfterDays: payload.customAfterDays,
      beforeWindowStart: '2026-05-03',
      beforeWindowEnd: '2026-06-02',
      afterWindowStart: '2026-06-04',
      afterWindowEnd: '2026-07-03',
      state: 'success',
      summary: '连接与稳定性负面率下降，电池与续航仍需观察。',
      items: [
        {
          uxPrimaryLabel: '产品体验',
          uxSecondaryLabel: '连接与稳定性',
          beforeMentionCount: 42,
          afterMentionCount: 38,
          beforeNegativeRate: 0.48,
          afterNegativeRate: 0.31,
          improvementRate: 0.17,
          summary: '断连相关负面反馈下降。',
        },
        {
          uxPrimaryLabel: '产品体验',
          uxSecondaryLabel: '电池与续航',
          beforeMentionCount: 36,
          afterMentionCount: 34,
          beforeNegativeRate: 0.39,
          afterNegativeRate: 0.4,
          improvementRate: -0.01,
          summary: '续航反馈基本持平。',
        },
      ],
    }
  }

  const response = await apiClient.post('/api/v1/ux-change-comparisons', { ...payload, productCode })
  return normalizeUxChangeComparisonRecord(response.data, productCode)
}

export async function fetchUxChangeComparisons(
  productCode = DEFAULT_PRODUCT_CODE,
): Promise<UxChangeComparisonListResponse> {
  const normalizedProductCode = productCode.trim()
  if (isTestMode) {
    const items = [
      await createUxChangeComparison({
        productCode: normalizedProductCode,
        changeDate: '2026-06-03',
        windowPreset: 'ONE_MONTH',
      }),
    ]
    return {
      items,
      state: 'success',
      notice: '测试模式下返回前后对比历史。',
    }
  }

  try {
    const response = await apiClient.get('/api/v1/ux-change-comparisons', {
      params: { productCode: normalizedProductCode },
    })
    const payload = Array.isArray(response.data) ? { items: response.data } : response.data
    const items = Array.isArray(payload.items)
      ? payload.items.map((item: unknown) => normalizeUxChangeComparisonRecord(item, normalizedProductCode))
      : []
    const notice = normalizeNotice(payload.notice)
    return {
      productCode: normalizeText(payload.productCode) || normalizedProductCode,
      productName: normalizeNotice(payload.productName),
      items,
      notice,
      state: resolveCollectionState(items, payload.state, notice),
    }
  } catch {
    return {
      items: [],
      state: 'error',
      notice: '前后对比历史请求失败，请稍后重试。',
    }
  }
}

export async function fetchUxChangeComparisonDetail(
  id: string,
  productCode = DEFAULT_PRODUCT_CODE,
): Promise<UxChangeComparisonRecord> {
  const normalizedId = id.trim()
  const normalizedProductCode = productCode.trim()
  if (isTestMode) {
    return createUxChangeComparison({
      productCode: normalizedProductCode,
      changeDate: '2026-06-03',
      windowPreset: 'ONE_MONTH',
    })
  }

  const response = await apiClient.get(`/api/v1/ux-change-comparisons/${encodeURIComponent(normalizedId)}`)
  return normalizeUxChangeComparisonRecord(response.data, normalizedProductCode)
}
