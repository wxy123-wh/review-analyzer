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
  ourMentionCount?: number
  competitorMentionCount?: number
  ourNegativeRate?: number
  competitorNegativeRate?: number
  negativeRateGap?: number
}

export type CompareState =
  | 'idle'
  | 'loading'
  | 'success'
  | 'taxonomy-mismatch'
  | 'missing-target'
  | 'comparison-unavailable'
  | 'primary-unavailable'
  | 'error'

export type CompareResponse = {
  productCode: string
  productName?: string
  comparisonProductCode?: string
  comparisonProductName?: string
  items: CompareItem[]
  state: CompareState
  notice?: string
}

export type TrendPoint = {
  period: string
  negativeRate: number
  mentionVolume: number
}

export type TrendSeries = {
  id: string
  aspect: string
  uxPrimaryLabel?: string
  uxSecondaryLabel: string
  color: string
  points: TrendPoint[]
  state: ChartLoadState
  notice?: string
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
  partOfSpeech: string
  wordType: string
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
  capturedPackets?: number
  outputPath?: string
  progressPath?: string
  cleanCommand?: string
  importCommand?: string
  sampleReviews?: CrawlReviewSample[]
  errorMessage?: string
  analysisHandoffStatus?: string
  analysisHandoffNote?: string
  startedAt?: string
  finishedAt?: string
}

export type CrawlReviewSample = {
  sourceReviewId?: string
  productName?: string
  content: string
  rating?: string
  reviewTime?: string
}

export type CrawlImportResponse = {
  jobId: string
  importJobId: string
  productCode: string
  productName?: string
  provider: string
  platform: string
  rawOutputPath: string
  cleanedOutputPath: string
  removedOutputPath: string
  cleaningSummaryPath: string
  receivedCount: number
  insertedReviewCount: number
  updatedReviewCount: number
  totalReviewCount: number
  cleaningSummary: Record<string, unknown>
  sampleReviews: CrawlReviewSample[]
  analysisHandoffStatus: string
  analysisHandoffNote?: string
  importedAt?: string
}

export type JsonlFileImportPayload = {
  productCode: string
  inputPath: string
  platform?: string
  productName?: string
  replaceExisting?: boolean
}

export type JsonlFileCleanPayload = JsonlFileImportPayload

export type JsonlFileCandidate = {
  path: string
  fileName: string
  productCode?: string
  productName?: string
  sizeBytes: number
  lastModifiedAt?: string
  sampleReviews: CrawlReviewSample[]
}

export type ProductHistoryItem = {
  productCode: string
  productName?: string
  importedReviewCount: number
  analyzedReviewCount: number
  downstreamReady: boolean
  taxonomyBound: boolean
  latestAnalysisStatus?: string
  latestImportedAt?: string
  createdAt?: string
}

export type AnalysisJobResponse = {
  jobId: string
  productCode: string
  status: string
  startedAt?: string
  finishedAt?: string
  errorMessage?: string
  totalReviewCount?: number
  processedReviewCount?: number
  progressPercent?: number
  currentStage?: string
  materializedReviewCount?: number
  semanticLabelCount?: number
  issueClusterCount?: number
  downstreamReady?: boolean
}

export type ReviewIntakeStatusResponse = {
  productCode: string
  productName?: string
  rawOutputPath: string
  rawJsonlExists: boolean
  rawCount: number
  cleanedOutputPath: string
  cleanedJsonlExists: boolean
  removedOutputPath: string
  cleaningSummaryPath: string
  cleaningSummary: Record<string, unknown>
  taxonomyBound: boolean
  taxonomyId?: number
  taxonomyVersion?: number
  importedReviewCount: number
  analyzedReviewCount: number
  downstreamReady: boolean
  latestAnalysisJob?: AnalysisJobResponse
  recentReviews: CrawlReviewSample[]
  stage: string
  notice?: string
}

export type ProductAnalysisReadyPayload = {
  productCode: string
  productName?: string
  importResult: CrawlImportResponse
  analysisJob: AnalysisJobResponse
}

export type UxChangeComparisonWindowPreset = 'ONE_MONTH' | 'TWO_WEEKS' | 'THREE_MONTHS' | 'CUSTOM'

export type UxChangeComparisonCreatePayload = {
  productCode: string
  changeDate: string
  windowPreset: UxChangeComparisonWindowPreset
  customBeforeDays?: number
  customAfterDays?: number
}

export type UxChangeComparisonItem = {
  uxPrimaryLabel?: string
  uxSecondaryLabel: string
  beforeMentionCount: number
  afterMentionCount: number
  beforeNegativeRate: number
  afterNegativeRate: number
  improvementRate: number
  summary?: string
}

export type UxChangeComparisonRecord = {
  id: string
  productCode: string
  productName?: string
  changeDate: string
  windowPreset: UxChangeComparisonWindowPreset
  customBeforeDays?: number
  customAfterDays?: number
  beforeWindowStart?: string
  beforeWindowEnd?: string
  afterWindowStart?: string
  afterWindowEnd?: string
  state: ContractState
  summary?: string
  notice?: string
  items: UxChangeComparisonItem[]
}

export type UxChangeComparisonListResponse = CollectionContract<UxChangeComparisonRecord> & {
  productCode?: string
  productName?: string
}
