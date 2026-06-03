export type ServiceStatus = {
  name: string
  status: 'UP' | 'DOWN' | 'UNKNOWN'
}

export type IssueItem = {
  issueId: string
  title: string
  aspect: string
  uxPrimaryLabel?: string
  uxSecondaryLabel?: string
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
  topSellingPoint: PositiveInsightItem | null
  issueCount: number
  positiveInsightCount: number
  actionCount: number
  validationCount: number
  state: ContractState
  notice?: string
}

export type IssueResponse = CollectionContract<IssueItem>

export type PositiveInsightItem = {
  sellingPointId: string
  aspect: string
  uxPrimaryLabel: string
  uxSecondaryLabel: string
  sellingPoint: string
  mentionCount: number
  positiveRate: number
  score: number
  evidence: string[]
}

export type PositiveInsightResponse = CollectionContract<PositiveInsightItem>

export type CompareItem = {
  aspect: string
  uxPrimaryLabel?: string
  uxSecondaryLabel?: string
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
  uxPrimaryLabel?: string
  uxSecondaryLabel?: string
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
  uxPrimaryLabel?: string
  uxSecondaryLabel?: string
  items: WordCloudItem[]
  notice?: string
  state: ChartLoadState
}

export type UxLabelOption = {
  id: string
  uxPrimaryLabel: string
  uxSecondaryLabel: string
  description?: string
  enabled: boolean
}

export type ProductTaxonomyResponse = {
  taxonomyId?: number
  name?: string
  productCode: string
  category: string
  labels: UxLabelOption[]
  state: ContractState
  notice?: string
  updatedAt?: string
}

export type ProductTaxonomyPayload = {
  taxonomyId?: number
  name?: string
  productCode: string
  category: string
  labels: UxLabelOption[]
}

export type CrawlStartPayload = {
  productUrl: string
  productCode?: string
  taxonomyId?: number
  maxPackets?: number
}

export type CrawlJobStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'WAITING_FOR_MANUAL_ACTION'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'

export type CrawlJobResponse = {
  jobId: string
  productCode: string
  status: CrawlJobStatus
  productUrl?: string
  platform?: string
  taxonomyId?: number
  fetchedCount: number
  errorMessage?: string
  analysisHandoffStatus?: string
  analysisHandoffNote?: string
  startedAt?: string
  finishedAt?: string
}

export type AnalysisJobResponse = {
  jobId: string
  productCode: string
  status: string
  startedAt?: string
  finishedAt?: string
  errorMessage?: string
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
