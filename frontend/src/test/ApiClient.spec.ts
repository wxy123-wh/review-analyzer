import { afterEach, describe, expect, it, vi } from 'vitest'

import { normalizeUxLabelOptions, normalizeUxSecondaryLabel, resolveApiBaseURL } from '../api/client'

afterEach(() => {
  vi.unstubAllEnvs()
  vi.doUnmock('axios')
  vi.resetModules()
})

describe('api client normalization', () => {
  it('prefers explicit API base URL configuration', () => {
    expect(resolveApiBaseURL(' https://api.example.test ')).toBe('https://api.example.test')
  })

  it('derives the default API base URL from the current browser host', () => {
    expect(resolveApiBaseURL('', { protocol: 'http:', hostname: '127.0.0.1' })).toBe('http://127.0.0.1:8080')
    expect(resolveApiBaseURL(undefined, { protocol: 'http:', hostname: '192.168.31.20' })).toBe(
      'http://192.168.31.20:8080',
    )
    expect(resolveApiBaseURL('', { protocol: 'https:', hostname: 'review-console.local' })).toBe(
      'https://review-console.local:8080',
    )
  })

  it('keeps localhost as a safe fallback when no browser location is available', () => {
    expect(resolveApiBaseURL('', undefined)).toBe('http://localhost:8080')
  })

  it('keeps structured UX labels and removes invalid taxonomy entries', () => {
    const labels = normalizeUxLabelOptions([
      {
        id: 'connectivity',
        uxPrimaryLabel: ' 产品体验 ',
        uxSecondaryLabel: ' 连接与稳定性 ',
        enabled: false,
      },
      {
        id: 'invalid',
        uxPrimaryLabel: '',
        uxSecondaryLabel: '缺少一级标签',
      },
    ])

    expect(labels).toEqual([
      {
        id: 'connectivity',
        uxPrimaryLabel: '产品体验',
        uxSecondaryLabel: '连接与稳定性',
        enabled: false,
      },
    ])
  })

  it('falls back to legacy aspect only when uxSecondaryLabel is missing', () => {
    expect(normalizeUxSecondaryLabel(' 物流与售后 ', 'service')).toBe('物流与售后')
    expect(normalizeUxSecondaryLabel('', 'battery')).toBe('电池与续航')
    expect(normalizeUxSecondaryLabel('', 'all')).toBe('全部')
  })

  it('keeps the issues failure state and message when the request fails', async () => {
    const get = vi.fn().mockRejectedValue(new Error('network unavailable'))
    const requestUse = vi.fn()
    const create = vi.fn(() => ({
      get,
      post: vi.fn(),
      put: vi.fn(),
      interceptors: {
        request: { use: requestUse },
      },
    }))

    vi.stubEnv('MODE', 'development')
    vi.stubEnv('VITE_API_BASE_URL', '')
    vi.doMock('axios', () => ({
      default: {
        create,
        isAxiosError: vi.fn(() => false),
      },
    }))

    const { fetchIssues } = await import('../api/client')
    const result = await fetchIssues('jd-failure-case')

    expect(create).toHaveBeenCalledWith({ baseURL: 'http://localhost:8080', timeout: 10000 })
    expect(get).toHaveBeenCalledWith('/api/v1/issues', { params: { productCode: 'jd-failure-case' } })
    expect(result).toEqual({
      items: [],
      state: 'error',
      notice: '问题接口请求失败，请稍后重试。',
    })
  })
})
