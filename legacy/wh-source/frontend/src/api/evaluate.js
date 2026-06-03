import { http } from './http'

export function fetchBeforeAfter({ eventId }) {
  return http.get('/api/evaluate/before-after', { params: { eventId } })
}

