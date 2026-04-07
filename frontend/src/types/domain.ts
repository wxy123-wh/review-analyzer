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

export type ContractState = 'idle' | 'loading' | 'success' | 'empty' | 'degraded' | 'error' | 'disabled' | 'runtime-unavailable'

export type CollectionContract<T> = {
  items: T[]
  state: ContractState
  notice?: string
}

export type OverviewContract = {
  topIssue: IssueItem | null
  issueCount: number
  actionCount: number
  validationCount: number
  state: ContractState
  notice?: string
}

export type IssueResponse = CollectionContract<IssueItem>

export type CompareItem = {
  aspect: string
  ourScore: number
  competitorScore: number
  gap: number
}

export type CompareState =
  | 'idle'
  | 'loading'
  | 'success'
  | 'missing-target'
  | 'comparison-unavailable'
  | 'primary-unavailable'
  | 'error'

export type CompareResponse = {
  productCode: string
  comparisonProductCode?: string
  items: CompareItem[]
  state: CompareState
  notice?: string
}

export type TrendPoint = {
  period: string
  negativeRate: number
  mentionVolume: number
}

export type ChartLoadState =
  | 'idle'
  | 'loading'
  | 'success'
  | 'empty'
  | 'degraded'
  | 'error'
  | 'timeout'
  | 'disabled'
  | 'runtime-unavailable'

export type TrendResponse = {
  aspect: string
  points: TrendPoint[]
  state: ChartLoadState
  notice?: string
}

export type WordCloudItem = {
  keyword: string
  frequency: number
  weight: number
  sentimentTag: string
}

export type WordCloudResponse = {
  productCode: string
  aspect: string
  items: WordCloudItem[]
  notice?: string
  state: ChartLoadState
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

export type ActionResponse = CollectionContract<ActionItem>

export type ValidationItem = {
  actionId: string
  beforeNegativeRate: number
  afterNegativeRate: number
  improvementRate: number
  summary: string
}

export type ValidationResponse = CollectionContract<ValidationItem>

export type ActionCreatePayload = {
  productCode: string
  issueId: string
  actionName: string
  actionDesc?: string
}

export type ShowcaseStage = {
  name: string
  state: string
  detail: string
}

export type ShowcasePipelineData = {
  status: string
  implemented: boolean
  note: string
  stages: ShowcaseStage[]
}

export type ShowcaseAgent = {
  agentName: string
  role: string
  state: string
  confidence: number
}

export type ShowcaseAgentArenaData = {
  status: string
  implemented: boolean
  note: string
  agents: ShowcaseAgent[]
}

export type ShowcaseFeatureContribution = {
  feature: string
  weight: number
}

export type ShowcaseExplainabilityData = {
  status: string
  implemented: boolean
  note: string
  featureContributions: ShowcaseFeatureContribution[]
}

export type ShowcaseChaosDrill = {
  scenario: string
  state: string
  detail: string
}

export type ShowcaseChaosData = {
  status: string
  implemented: boolean
  note: string
  drills: ShowcaseChaosDrill[]
}

export type ShowcaseReportPreviewData = {
  status: string
  implemented: boolean
  note: string
  previewSections: string[]
}
