import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CompareTable from '../components/CompareTable.vue'
import type { CompareItem } from '../types/domain'

const items: CompareItem[] = [
  { aspect: 'battery', uxSecondaryLabel: '电池与续航', ourScore: 0.22, competitorScore: 0.78, gap: -0.56 },
  { aspect: 'bluetooth', uxSecondaryLabel: '连接与稳定性', ourScore: 0.78, competitorScore: 0.5, gap: 0.28 },
]

describe('CompareTable', () => {
  it('renders canonical compare rows and product labels in success state', () => {
    const wrapper = mount(CompareTable, {
      props: {
        items,
        state: 'success',
        productCode: 'jd-100127936932',
        comparisonProductCode: 'jd-competitor',
      },
    })

    expect(wrapper.text()).toContain('竞品对比概览')
    expect(wrapper.text()).toContain('主产品：jd-100127936932')
    expect(wrapper.text()).toContain('对比产品：jd-competitor')
    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
    expect(wrapper.text()).toContain('电池与续航')
    expect(wrapper.text()).toContain('连接与稳定性')
    expect(wrapper.text()).toContain('0.22')
    expect(wrapper.text()).toContain('-0.56')
  })

  it('shows structured unavailable message when comparison target is missing', () => {
    const wrapper = mount(CompareTable, {
      props: {
        items: [],
        state: 'missing-target',
        message: '请选择需要对比的竞品后再查看对比结果。',
        productCode: 'jd-100127936932',
      },
    })

    expect(wrapper.text()).toContain('请选择需要对比的竞品后再查看对比结果。')
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
  })

  it('keeps degraded compare states on the existing non-success branch with backend notice copy', () => {
    const wrapper = mount(CompareTable, {
      props: {
        items: [],
        state: 'comparison-unavailable',
        message: '竞品暂无可用分析结果，请先完成真实评论初始化与分析。',
        productCode: 'jd-100127936932',
        comparisonProductCode: 'jd-competitor',
      },
    })

    expect(wrapper.text()).toContain('竞品暂无可用分析结果，请先完成真实评论初始化与分析。')
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
  })
})
