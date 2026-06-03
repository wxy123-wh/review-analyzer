import { cleanParams, http } from './http'

export function fetchProducts() {
  return http.get('/api/meta/products')
}

export function fetchPlatforms() {
  return http.get('/api/meta/platforms')
}

export function fetchAspects() {
  return http.get('/api/meta/aspects')
}

export function buildCommonParams({ productId, start, end }) {
  return cleanParams({ productId, start, end })
}

