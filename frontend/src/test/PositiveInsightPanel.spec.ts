import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PositiveInsightPanel from '../components/PositiveInsightPanel.vue'
import type { PositiveInsightItem } from '../types/domain'

const items: PositiveInsightItem[] = [
  {
    sellingPointId: 'sp-comfort-1',
    aspect: 'comfort',
    uxPrimaryLabel: '产品体验',
    uxSecondaryLabel: '佩戴与人体工学',
    sellingPoint: '佩戴舒适',
    mentionCount: 12,
    positiveRate: 0.86,
    score: 0.78,
    evidence: ['戴了几个小时耳朵也不疼'],
  },
]

describe('PositiveInsightPanel', () => {
  it('renders positive insight cards in success state', () => {
    const wrapper = mount(PositiveInsightPanel, {
      props: {
        items,
        state: 'success',
      },
    })

    expect(wrapper.text()).toContain('正面卖点')
    expect(wrapper.text()).toContain('佩戴舒适')
    expect(wrapper.text()).toContain('佩戴与人体工学')
    expect(wrapper.text()).toContain('86.0%')
    expect(wrapper.text()).toContain('戴了几个小时耳朵也不疼')
  })

  it('renders empty and error states', () => {
    const empty = mount(PositiveInsightPanel, {
      props: {
        items: [],
        state: 'empty',
        message: '暂无可提炼的正面 UX 标签',
      },
    })
    expect(empty.text()).toContain('暂无可提炼的正面 UX 标签')

    const error = mount(PositiveInsightPanel, {
      props: {
        items: [],
        state: 'error',
        message: '卖点接口请求失败，请稍后重试。',
      },
    })
    expect(error.text()).toContain('卖点接口请求失败，请稍后重试。')
  })
})
