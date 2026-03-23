import axios from 'axios'

import type {
  ActionCreatePayload,
  ActionItem,
  CompareItem,
  IssueItem,
  ServiceStatus,
  ShowcaseAgentArenaData,
  ShowcaseChaosData,
  ShowcaseExplainabilityData,
  ShowcasePipelineData,
  ShowcaseReportPreviewData,
  TrendPoint,
  ValidationItem,
} from '../types/domain'
import { normalizeShowcaseStatus } from '../utils/showcaseCopy'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const isTestMode = import.meta.env.MODE === 'test'

export const apiClient = axios.create({
  baseURL,
  timeout: 10000,
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
        issueId: 'iss-connectivity-001',
        title: '连接稳定性偶发断连',
        aspect: 'connectivity',
        priorityScore: 0.554,
        evidenceSummary: '近30天断连反馈上升且竞品差距扩大。',
      },
    ]
  }

  try {
    const response = await apiClient.get('/api/v1/issues', { params: { productCode } })
    return response.data.items ?? []
  } catch {
    return []
  }
}

export async function fetchCompare(productCode = 'demo-earphone'): Promise<CompareItem[]> {
  if (isTestMode) {
    return [
      { aspect: 'audio', ourScore: 0.82, competitorScore: 0.78, gap: 0.04 },
      { aspect: 'battery', ourScore: 0.71, competitorScore: 0.84, gap: -0.13 },
    ]
  }

  try {
    const response = await apiClient.get('/api/v1/compare', { params: { productCode } })
    return response.data.items ?? []
  } catch {
    return []
  }
}

export async function fetchTrends(
  productCode = 'demo-earphone',
  aspect = 'battery',
): Promise<{ aspect: string; points: TrendPoint[] }> {
  if (isTestMode) {
    return {
      aspect,
      points: [
        { period: '2026-W06', negativeRate: 0.31, mentionVolume: 75 },
        { period: '2026-W09', negativeRate: 0.4, mentionVolume: 105 },
      ],
    }
  }

  try {
    const response = await apiClient.get('/api/v1/trends', { params: { productCode, aspect } })
    return {
      aspect: response.data.aspect ?? aspect,
      points: response.data.points ?? [],
    }
  } catch {
    return { aspect, points: [] }
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
      note: '流水线编排当前使用演示数据回放。',
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
      '流水线编排当前使用演示数据回放。',
    )
  } catch {
    return {
      status: '演示数据',
      implemented: false,
      note: '流水线接口暂不可用，已切换为演示数据。',
      stages: [],
    }
  }
}

export async function fetchShowcaseAgentArena(): Promise<ShowcaseAgentArenaData> {
  if (isTestMode) {
    return {
      status: '演示数据',
      implemented: false,
      note: '智能体协同当前使用演示数据仿真。',
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
      '智能体协同当前使用演示数据仿真。',
    )
  } catch {
    return {
      status: '演示数据',
      implemented: false,
      note: '智能体接口暂不可用，已切换为演示数据。',
      agents: [],
    }
  }
}

export async function fetchShowcaseExplainability(): Promise<ShowcaseExplainabilityData> {
  if (isTestMode) {
    return {
      status: '演示数据',
      implemented: false,
      note: '可解释性分析当前使用演示数据权重。',
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
      '可解释性分析当前使用演示数据权重。',
    )
  } catch {
    return {
      status: '演示数据',
      implemented: false,
      note: '可解释性接口暂不可用，已切换为演示数据。',
      featureContributions: [],
    }
  }
}

export async function fetchShowcaseChaos(): Promise<ShowcaseChaosData> {
  if (isTestMode) {
    return {
      status: '演示数据',
      implemented: false,
      note: '混沌演练当前使用演示数据剧本。',
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
      '混沌演练当前使用演示数据剧本。',
    )
  } catch {
    return {
      status: '演示数据',
      implemented: false,
      note: '混沌演练接口暂不可用，已切换为演示数据。',
      drills: [],
    }
  }
}

export async function previewShowcaseReport(module: string): Promise<ShowcaseReportPreviewData> {
  if (isTestMode) {
    return {
      status: '演示数据',
      implemented: false,
      note: '测试模式下报告导出关闭，当前展示演示数据预览。',
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
      '报告中心当前展示演示数据预览。',
    )
  } catch {
    return {
      status: '演示数据',
      implemented: false,
      note: '报告预览接口暂不可用，已切换为演示数据。',
      previewSections: [],
    }
  }
}
