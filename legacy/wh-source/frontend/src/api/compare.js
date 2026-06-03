import { buildCommonParams } from './meta'
import { cleanParams, http } from './http'

export function fetchCompareAspects({ productId, competitorId, start, end }) {
  return http.get('/api/compare/aspects', {
    params: cleanParams({ ...buildCommonParams({ productId, start, end }), competitorId }),
  })
}

