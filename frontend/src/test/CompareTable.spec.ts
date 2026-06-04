import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import CompareTable from '../components/CompareTable.vue'
import type { CompareItem } from '../types/domain'

const items: CompareItem[] = [
  {
    aspect: 'battery',
    uxSecondaryLabel: '电池与续航',
    ourScore: 0.22,
    competitorScore: 0.78,
    gap: -0.56,
    ourMentionCount: 20,
    competitorMentionCount: 18,
    ourNegativeRate: 0.4,
    competitorNegativeRate: 0.12,
  },
  { aspect: 'bluetooth', uxSecondaryLabel: '连接与稳定性', ourScore: 0.78, competitorScore: 0.5, gap: 0.28 },
]

describe('CompareTable', () => {
  it('renders compare rows and product names in success state', () => {
    const wrapper = mount(CompareTable, {
      props: {
        items,
        state: 'success',
        productCode: 'jd-100127936932',
        productName: '小米 Buds 5 Pro',
        comparisonProductCode: 'jd-100127936933',
        comparisonProductName: 'OPPO Enco Free4',
      },
    })

    expect(wrapper.text()).toContain('竞品对比')
    expect(wrapper.text()).toContain('主产品：小米 Buds 5 Pro')
    expect(wrapper.text()).toContain('jd-100127936932')
    expect(wrapper.text()).toContain('对比产品：OPPO Enco Free4')
    expect(wrapper.text()).toContain('jd-100127936933')
    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
    expect(wrapper.text()).toContain('电池与续航')
    expect(wrapper.text()).toContain('连接与稳定性')
    expect(wrapper.text()).toContain('0.22')
    expect(wrapper.text()).toContain('20 / 18')
    expect(wrapper.text()).toContain('40.0% / 12.0%')
    expect(wrapper.text()).toContain('-0.56')
  })

  it('emits product codes from the compare form', async () => {
    const onCompare = vi.fn()
    const wrapper = mount(CompareTable, {
      props: {
        items: [],
        state: 'idle',
        productCode: 'jd-100127936932',
        onCompare,
      },
    })

    await wrapper.get('[data-testid="compare-comparison-product-code"]').setValue('jd-100127936933')
    await wrapper.get('[data-testid="compare-form"]').trigger('submit')

    expect(onCompare).toHaveBeenCalledWith({
      productCode: 'jd-100127936932',
      comparisonProductCode: 'jd-100127936933',
    })
  })

  it('shows structured unavailable message when comparison target is missing', () => {
    const wrapper = mount(CompareTable, {
      props: {
        items: [],
        state: 'missing-target',
        message: '请输入主商品和竞品编号。',
        productCode: 'jd-100127936932',
      },
    })

    expect(wrapper.text()).toContain('请输入主商品和竞品编号。')
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
  })

  it('keeps taxonomy mismatch on the non-success branch with backend notice copy', () => {
    const wrapper = mount(CompareTable, {
      props: {
        items: [],
        state: 'taxonomy-mismatch',
        message: '两个商品绑定的 taxonomy 不一致。',
        productCode: 'jd-100127936932',
        comparisonProductCode: 'jd-100127936933',
      },
    })

    expect(wrapper.text()).toContain('两个商品绑定的 taxonomy 不一致。')
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
  })
})
