import { buildCommonParams } from './meta'
import { cleanParams, http } from './http'

export function fetchReviews({
  productId,
  platformId,
  aspectId,
  sentiment,
  keyword,
  start,
  end,
  page = 1,
  pageSize = 20,
}) {
  return http.get('/api/reviews', {
    params: cleanParams({
      ...buildCommonParams({ productId, start, end }),
      platformId,
      aspectId,
      sentiment,
      keyword,
      page,
      pageSize,
    }),
  })
}

export function fetchReviewDetail(id) {
  return http.get(`/api/reviews/${id}`)
}

