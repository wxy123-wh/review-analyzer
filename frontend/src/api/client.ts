import axios from 'axios'

import type {
  ActionCreatePayload,
  ActionItem,
  ChartLoadState,
  CompareItem,
  IssueItem,
  ServiceStatus,
  ShowcaseAgentArenaData,
  ShowcaseChaosData,
  ShowcaseExplainabilityData,
  ShowcasePipelineData,
  ShowcaseReportPreviewData,
  TrendResponse,
  ValidationItem,
  WordCloudItem,
  WordCloudResponse,
} from '../types/domain'
import { normalizeShowcaseStatus } from '../utils/showcaseCopy'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const isTestMode = import.meta.env.MODE === 'test'

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
  const upper = trimmed.toUpperCase()
  if (upper.includes('PLACE') || upper.includes('DEMO')) {
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

export async function fetchIssues(productCode = 'demo-earphone'): Promise<IssueItem[]> {
  if (isTestMode) {
    return [
      {
        issueId: 'iss-bluetooth-001',
        title: '连接稳定性偶发断连',
        aspect: 'bluetooth',
        priorityScore: 0.554,
        evidenceSummary: '近30天断连反馈上升且竞品差距扩大。',
      },
    ]
  }

  try {
    const response = await apiClient.get('/api/v1/issues', { params: { productCode } })
    if (!Array.isArray(response.data.items)) {
      return []
    }
    return response.data.items.map((item: IssueItem) => ({
      ...item,
      aspect: normalizeAspectCode(item.aspect, 'general'),
    }))
  } catch {
    return []
  }
}

export async function fetchCompare(productCode = 'demo-earphone'): Promise<CompareItem[]> {
  if (isTestMode) {
    return [
      { aspect: 'bluetooth', ourScore: 0.82, competitorScore: 0.78, gap: 0.04 },
      { aspect: 'noise-canceling', ourScore: 0.76, competitorScore: 0.81, gap: -0.05 },
      { aspect: 'battery', ourScore: 0.71, competitorScore: 0.84, gap: -0.13 },
    ]
  }

  try {
    const response = await apiClient.get('/api/v1/compare', { params: { productCode } })
    return normalizeCompareItems(response.data.items)
  } catch {
    return []
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
    const points = response.data.points ?? []
    return {
      aspect: normalizeAspectCode(response.data.aspect, 'battery') || fallbackAspect,
      points,
      state: points.length > 0 ? 'success' : 'empty',
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
    const notice = typeof response.data.notice === 'string' ? response.data.notice : undefined
    return {
      productCode: response.data.productCode ?? productCode,
      aspect: normalizeAspectCode(response.data.aspect, aspect === 'all' ? 'all' : 'battery'),
      items,
      notice,
      state: items.length > 0 ? 'success' : 'empty',
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

export async function fetchValidation(actionId?: string): Promise<ValidationItem[]> {
  if (isTestMode) {
    return [
      {
        actionId: actionId ?? 'action-test-1',
        beforeNegativeRate: 0.42,
        afterNegativeRate: 0.31,
        improvementRate: 0.11,
        summary: '上线后负面率下降 11.00%，问题热度趋稳。',
      },
    ]
  }

  try {
    const response = await apiClient.get('/api/v1/validation', {
      params: actionId ? { actionId } : {},
    })
    return response.data.items ?? []
  } catch {
    return []
  }
}

export async function fetchShowcasePipeline(): Promise<ShowcasePipelineData> {
  if (isTestMode) {
    return {
      status: '演示数据',
      implemented: false,
      note: 'v1-state=placeholder; strategy=replace; data-source=static-demo-payload; 当前仅用于固定字段占位，不代表真实流水线编排。',
      stages: [
        { name: 'SYNC', state: 'DONE', detail: '已接入 12,480 条评论样本' },
        { name: 'ASPECT_NLP', state: 'RUNNING', detail: '领域词典 v0.3 正在演示推演' },
        { name: 'SCORING', state: 'QUEUED', detail: '等待批处理窗口收敛' },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/showcase/pipeline')
    return normalizeShowcaseData(
      response.data as ShowcasePipelineData,
      'v1-state=placeholder; strategy=replace; data-source=static-demo-payload; 当前仅用于固定字段占位，不代表真实流水线编排。',
    )
  } catch {
    return {
      status: '演示数据',
      implemented: false,
      note: 'v1-state=placeholder; strategy=replace; data-source=static-demo-payload; 流水线接口暂不可用，已切换为占位数据。',
      stages: [],
    }
  }
}

export async function fetchShowcaseAgentArena(): Promise<ShowcaseAgentArenaData> {
  if (isTestMode) {
    return {
      status: '演示数据',
      implemented: false,
      note: 'v1-state=placeholder; strategy=replace; data-source=static-demo-payload; 当前仅保留智能体协同字段骨架。',
      agents: [
        { agentName: 'collector-agent', role: 'SYNC', state: 'IDLE', confidence: 0.98 },
        { agentName: 'insight-agent', role: 'ANALYZE', state: 'RUNNING', confidence: 0.86 },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/showcase/agent-arena')
    return normalizeShowcaseData(
      response.data as ShowcaseAgentArenaData,
      'v1-state=placeholder; strategy=replace; data-source=static-demo-payload; 当前仅保留智能体协同字段骨架。',
    )
  } catch {
    return {
      status: '演示数据',
      implemented: false,
      note: 'v1-state=placeholder; strategy=replace; data-source=static-demo-payload; 智能体接口暂不可用，已切换为占位数据。',
      agents: [],
    }
  }
}

export async function fetchShowcaseExplainability(): Promise<ShowcaseExplainabilityData> {
  if (isTestMode) {
    return {
      status: '演示数据',
      implemented: false,
      note: 'v1-state=controlled-data-only; strategy=keep; data-source=deterministic-score-weights; 当前解释的是既有优先级权重，不是模型内部归因。',
      featureContributions: [
        { feature: 'negative_rate', weight: 0.35 },
        { feature: 'mention_volume', weight: 0.25 },
        { feature: 'trend_growth', weight: 0.2 },
        { feature: 'competitor_gap', weight: 0.2 },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/showcase/explainability')
    return normalizeShowcaseData(
      response.data as ShowcaseExplainabilityData,
      'v1-state=controlled-data-only; strategy=keep; data-source=deterministic-score-weights; 当前解释的是既有优先级权重，不是模型内部归因。',
    )
  } catch {
    return {
      status: '演示数据',
      implemented: false,
      note: 'v1-state=controlled-data-only; strategy=keep; data-source=deterministic-score-weights; 可解释性接口暂不可用，已切换为固定权重数据。',
      featureContributions: [],
    }
  }
}

export async function fetchShowcaseChaos(): Promise<ShowcaseChaosData> {
  if (isTestMode) {
    return {
      status: '演示数据',
      implemented: false,
      note: 'v1-state=gated-placeholder; strategy=hide-by-default; data-source=static-demo-payload; 当前仅保留韧性演练剧本字段。',
      drills: [
        { scenario: 'db-latency-spike', state: 'PENDING', detail: '模拟 p95 延迟升至 3 秒' },
        { scenario: 'provider-rate-limit', state: 'PENDING', detail: '模拟 OneBound 429 波动' },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/showcase/chaos')
    return normalizeShowcaseData(
      response.data as ShowcaseChaosData,
      'v1-state=gated-placeholder; strategy=hide-by-default; data-source=static-demo-payload; 当前仅保留韧性演练剧本字段。',
    )
  } catch {
    return {
      status: '演示数据',
      implemented: false,
      note: 'v1-state=gated-placeholder; strategy=hide-by-default; data-source=static-demo-payload; 韧性演练接口暂不可用，已切换为占位数据。',
      drills: [],
    }
  }
}

export async function previewShowcaseReport(module: string): Promise<ShowcaseReportPreviewData> {
  if (isTestMode) {
    return {
      status: '演示数据',
      implemented: false,
      note: 'v1-state=placeholder; strategy=replace; data-source=static-preview-sections; 当前仅提供报告段落预览，不执行真实导出。',
      previewSections: [
        `模块 ${module} 的执行摘要（演示数据）`,
        '核心问题快照与证据清单',
        '关键指标趋势与下一步检查项',
      ],
    }
  }

  try {
    const response = await apiClient.post('/api/v1/showcase/reports/preview', { module })
    return normalizeShowcaseData(
      response.data as ShowcaseReportPreviewData,
      'v1-state=placeholder; strategy=replace; data-source=static-preview-sections; 当前仅提供报告段落预览，不执行真实导出。',
    )
  } catch {
    return {
      status: '演示数据',
      implemented: false,
      note: 'v1-state=placeholder; strategy=replace; data-source=static-preview-sections; 报告预览接口暂不可用，已切换为占位数据。',
      previewSections: [],
    }
  }
}
