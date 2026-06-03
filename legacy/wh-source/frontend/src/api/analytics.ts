import { cleanParams, http } from './http'

export type Sentiment = 'POS' | 'NEU' | 'NEG'

export class AnalyticsApiError extends Error {
  cause?: unknown

  constructor(message: string, cause?: unknown) {
    super(message)
    this.name = 'AnalyticsApiError'
    this.cause = cause
  }
}

function normalizeAnalyticsError(err: unknown): AnalyticsApiError {
  if (err instanceof AnalyticsApiError) return err

  const fallbackMsg = '请求失败'
  if (!err || typeof err !== 'object') return new AnalyticsApiError(fallbackMsg, err)

  const anyErr = err as any
  const msg =
    (typeof anyErr?.message === 'string' && anyErr.message) ||
    (typeof anyErr?.response?.data?.msg === 'string' && anyErr.response.data.msg) ||
    fallbackMsg
  return new AnalyticsApiError(msg, err)
}

async function analyticsGet<T>(url: string, params: Record<string, unknown>): Promise<T> {
  try {
    return (await http.get(url, { params: cleanParams(params) })) as T
  } catch (err) {
    throw normalizeAnalyticsError(err)
  }
}

export interface WordCloudItem {
  word: string
  value: number
}

export interface AnalyticsMetaBase {
  totalReviews: number
}

export interface WordCloudMeta {
  topN: number
  totalReviews: AnalyticsMetaBase['totalReviews']
}

export interface WordCloudResponse {
  items: WordCloudItem[]
  meta: WordCloudMeta
}

export interface FetchWordCloudParams {
  productId: string | number
  start?: string | null
  end?: string | null
  aspectId?: string | number | null
  sentiment?: Sentiment | null
  topN?: number | null
}

export function fetchWordCloud(params: FetchWordCloudParams): Promise<WordCloudResponse> {
  return analyticsGet<WordCloudResponse>('/api/analytics/wordcloud', params)
}

export type TrendGranularity = 'day' | 'week'

export interface SentimentTrendPoint {
  date: string
  pos: number
  neg: number
  neu: number
  total: number
  posRate: number
  negRate: number
}

export interface SentimentTrendMeta {
  granularity: TrendGranularity
  totalReviews: AnalyticsMetaBase['totalReviews']
}

export interface SentimentTrendResponse {
  points: SentimentTrendPoint[]
  meta: SentimentTrendMeta
}

export interface FetchSentimentTrendParams {
  productId: string | number
  start?: string | null
  end?: string | null
  granularity?: TrendGranularity | null
}

export function fetchSentimentTrend(params: FetchSentimentTrendParams): Promise<SentimentTrendResponse> {
  return analyticsGet<SentimentTrendResponse>('/api/analytics/sentiment-trend', params)
}
