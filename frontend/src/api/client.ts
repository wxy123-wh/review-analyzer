import axios from 'axios'

import type {
  ActionCreatePayload,
  ActionItem,
  CompareItem,
  IssueItem,
  ServiceStatus,
  TrendPoint,
  ValidationItem,
} from '../types/domain'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const isTestMode = import.meta.env.MODE === 'test'

export const apiClient = axios.create({
  baseURL,
  timeout: 10000,
})

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

export function nlpPlaceholderStatus(): ServiceStatus {
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
