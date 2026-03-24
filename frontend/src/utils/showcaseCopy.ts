function looksLikeDemoStatus(status: string): boolean {
  const normalized = status.trim().toUpperCase()
  return normalized.includes('PLACE') || normalized === 'DEMO' || normalized === 'DEMO_DATA'
}

export function formatShowcaseStatus(status?: string): string {
  if (!status) {
    return '加载中'
  }
  if (looksLikeDemoStatus(status)) {
    return '演示数据'
  }
  return status
}

export function normalizeShowcaseStatus(status?: string): string {
  if (!status || looksLikeDemoStatus(status)) {
    return '演示数据'
  }
  return status
}
