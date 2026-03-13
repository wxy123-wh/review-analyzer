export type ServiceStatus = {
  name: string
  status: 'UP' | 'DOWN' | 'UNKNOWN'
}

export type IssueItem = {
  issueId: string
  title: string
  aspect: string
  priorityScore: number
  evidenceSummary: string
}

export type CompareItem = {
  aspect: string
  ourScore: number
  competitorScore: number
  gap: number
}

export type TrendPoint = {
  period: string
  negativeRate: number
  mentionVolume: number
}

export type ActionItem = {
  actionId: string
  productCode: string
  issueId: string
  actionName: string
  actionDesc?: string
  status: string
  createdAt: string
}

export type ValidationItem = {
  actionId: string
  beforeNegativeRate: number
  afterNegativeRate: number
  improvementRate: number
  summary: string
}

export type ActionCreatePayload = {
  productCode: string
  issueId: string
  actionName: string
  actionDesc?: string
}
