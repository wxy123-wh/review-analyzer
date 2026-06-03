import { cleanParams, http } from './http'

export function fetchAlerts({ productId, status }) {
  return http.get('/api/alerts', { params: cleanParams({ productId, status }) })
}

export function ackAlert(id) {
  return http.post('/api/alerts/ack', null, { params: { id } })
}

