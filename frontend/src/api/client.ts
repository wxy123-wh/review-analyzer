import axios from 'axios'

import type {
  ActionCreatePayload,
  ActionItem,
  ActionResponse,
  ChartLoadState,
  CompareItem,
  CompareResponse,
  CompareState,
  ContractState,
  IssueItem,
  IssueResponse,
  ServiceStatus,
  ShowcaseAgentArenaData,
  ShowcaseChaosData,
  ShowcaseExplainabilityData,
  ShowcasePipelineData,
  ShowcaseReportPreviewData,
  TrendResponse,
  ValidationItem,
  ValidationResponse,
  WordCloudItem,
  WordCloudResponse,
} from '../types/domain'
import { normalizeShowcaseStatus } from '../utils/showcaseCopy'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const isTestMode = import.meta.env.MODE === 'test'
export const DEFAULT_COMPARE_PRODUCT_CODE = 'demo-earphone-competitor'

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
        ourScore,
        competitorScore,
        gap,
      }
    })
    .filter((item): item is CompareItem => item !== null)
    .sort(
      (left, right) =>
        CANONICAL_COMPARE_ASPECTS.indexOf(left.aspect as (typeof CANONICAL_COMPARE_ASPECTS)[number]) -
        CANONICAL_COMPARE_ASPECTS.indexOf(right.aspect as (typeof CANONICAL_COMPARE_ASPECTS)[number]),
    )
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
        priorityScore: typeof record.priorityScore === 'number' ? record.priorityScore : 0,
        evidenceSummary,
      }
    })
    .filter((item): item is IssueItem => item !== null)
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

export function nlpDemoStatus(): ServiceStatus {
  return { name: 'NLP Service', status: isTestMode ? 'UP' : 'UNKNOWN' }
}

export async function fetchIssues(productCode = 'demo-earphone'): Promise<IssueResponse> {
  if (isTestMode) {
    return {
      state: 'success',
      items: [
        {
          issueId: 'iss-bluetooth-001',
          title: '连接稳定性偶发断连',
          aspect: 'bluetooth',
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

export async function fetchCompare(
  productCode = 'demo-earphone',
  comparisonProductCode = DEFAULT_COMPARE_PRODUCT_CODE,
): Promise<CompareResponse> {
  if (isTestMode) {
    return {
      productCode,
      comparisonProductCode,
      state: 'success',
      items: [
        { aspect: 'battery', ourScore: 0.22, competitorScore: 0.78, gap: -0.56 },
        { aspect: 'bluetooth', ourScore: 0.78, competitorScore: 0.5, gap: 0.28 },
        { aspect: 'noise-canceling', ourScore: 0.5, competitorScore: 0.78, gap: -0.28 },
        { aspect: 'comfort', ourScore: 0.78, competitorScore: 0.5, gap: 0.28 },
        { aspect: 'microphone', ourScore: 0.5, competitorScore: 0.22, gap: 0.28 },
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
  productCode = 'demo-earphone',
  aspect = 'battery',
): Promise<TrendResponse> {
  const fallbackAspect = normalizeAspectCode(aspect, 'battery')

  if (isTestMode) {
    return {
      aspect: fallbackAspect,
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
      points,
      state: resolveChartState(points, response.data.state, notice),
      notice,
    }
  } catch (error) {
    return { aspect: fallbackAspect, points: [], state: resolveRequestState(error) }
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
  productCode = 'demo-earphone',
  aspect = 'all',
): Promise<WordCloudResponse> {
  if (isTestMode) {
    return {
      productCode,
      aspect,
      items: [
        { keyword: '续航', frequency: 42, weight: 0.92, sentimentTag: 'POSITIVE' },
        { keyword: '断连', frequency: 31, weight: 0.85, sentimentTag: 'NEGATIVE' },
        { keyword: '降噪', frequency: 27, weight: 0.78, sentimentTag: 'POSITIVE' },
        { keyword: '佩戴', frequency: 24, weight: 0.7, sentimentTag: 'NEUTRAL' },
      ],
      notice: '演示模式词云数据',
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
      items,
      notice,
      state: resolveChartState(items, response.data.state, notice),
    }
  } catch (error) {
    return {
      productCode,
      aspect: aspect === 'all' ? 'all' : normalizeAspectCode(aspect, 'battery'),
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
          productCode: 'demo-earphone',
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
        { name: 'SYNC', state: 'QUEUED', detail: 'provider=aggregator-demo; productCode=demo-earphone; fetchedCount=0' },
        { name: 'ANALYSIS', state: 'SUCCEEDED', detail: 'productCode=demo-earphone; jobId=analysis-test-1' },
        { name: 'MATERIALIZATION', state: 'SUCCEEDED', detail: 'productCode=demo-earphone; issueCount=3; outputs align with the latest persisted analysis window' },
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
      status: 'CONTROLLED_DATA_ONLY',
      implemented: true,
      note: 'v1-state=controlled-data-only; strategy=keep; data-source=materialized_issue_scores+deterministic-score-weights; 当前解释的是固定权重问题得分拆解。',
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
      'v1-state=controlled-data-only; strategy=keep; data-source=deterministic-score-weights; 可解释性运行态暂不可用。',
    )
  } catch {
    return {
      status: 'CONTROLLED_DATA_ONLY',
      implemented: true,
      note: 'v1-state=controlled-data-only; strategy=keep; data-source=deterministic-score-weights; 可解释性接口暂不可用，当前仅保留固定权重拆解。',
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
        { scenario: 'sync-runtime', state: 'DEGRADED', detail: 'provider=aggregator-demo; productCode=demo-earphone; latest sync remains queued' },
        { scenario: 'analysis-runtime', state: 'STABLE', detail: 'productCode=demo-earphone; latest analysis completed successfully' },
        { scenario: 'materialization-runtime', state: 'STABLE', detail: 'productCode=demo-earphone; materialized outputs are aligned with the latest analysis window' },
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
