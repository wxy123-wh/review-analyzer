import { buildCommonParams } from './meta'
import { http } from './http'

export function fetchSuggestions({ productId, start, end }) {
  return http.get('/api/decision/suggestions', { params: buildCommonParams({ productId, start, end }) })
}

export function fetchAiSummary(clusterId) {
  return http.post('/api/decision/ai-summary', { clusterId })
}
