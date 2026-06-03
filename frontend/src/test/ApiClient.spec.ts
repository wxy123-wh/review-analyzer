import { describe, expect, it } from 'vitest'

import { normalizeUxLabelOptions, normalizeUxSecondaryLabel } from '../api/client'

describe('api client normalization', () => {
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
    expect(normalizeUxSecondaryLabel('', 'battery')).toBe('battery')
  })
})
