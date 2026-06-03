import { buildCommonParams } from './meta'
import { http } from './http'

export function fetchDashboardOverview({ productId, start, end }) {
  return http.get('/api/dashboard/overview', { params: buildCommonParams({ productId, start, end }) })
}

