export function formatShowcaseStatus(status?: string): string {
  if (!status) {
    return '加载中'
  }
  const normalized = status.trim().toUpperCase()
  switch (normalized) {
    case 'LIVE':
      return '实时数据'
    case 'STABLE':
      return '运行稳定'
    case 'DEGRADED':
      return '降级可用'
    case 'CONTROLLED_DATA_ONLY':
      return '受控数据'
    case 'RUNTIME_UNAVAILABLE':
      return '运行态不可用'
    case 'DEMO':
    case 'DEMO_DATA':
      return '占位演示'
    default:
      return status.trim() || '加载中'
  }
}

export function normalizeShowcaseStatus(status?: string): string {
  if (!status) {
    return 'RUNTIME_UNAVAILABLE'
  }
  return status.trim() || 'RUNTIME_UNAVAILABLE'
}
