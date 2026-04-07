import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ShowcaseExplainabilityPanel from '../components/ShowcaseExplainabilityPanel.vue'

describe('ShowcaseExplainabilityPanel', () => {
  it('renders controlled-data contributions with dominant feature and weight coverage', () => {
    const wrapper = mount(ShowcaseExplainabilityPanel, {
      props: {
        data: {
          status: 'CONTROLLED_DATA_ONLY',
          implemented: true,
          note: '当前解释的是固定权重问题分数拆解。',
          featureContributions: [
            { feature: 'mention_volume', weight: 0.28 },
            { feature: 'negative_rate', weight: 0.41 },
            { feature: 'trend_growth', weight: 0.2 },
            { feature: 'competitor_gap', weight: 0.11 },
          ],
        },
      },
    })

    expect(wrapper.text()).toContain('受控数据')
    expect(wrapper.text()).toContain('特征数量')
    expect(wrapper.text()).toContain('4')
    expect(wrapper.text()).toContain('主导特征')
    expect(wrapper.text()).toContain('negative_rate')
    expect(wrapper.text()).toContain('当前最高权重 41%')
    expect(wrapper.text()).toContain('权重覆盖')
    expect(wrapper.text()).toContain('100%')
    expect(wrapper.text()).toContain('展示 4 条得分贡献')
    expect(wrapper.findAll('.contribution-card')).toHaveLength(4)
  })

  it('renders an explicit empty state when feature contributions are unavailable', () => {
    const wrapper = mount(ShowcaseExplainabilityPanel, {
      props: {
        data: {
          status: 'RUNTIME_UNAVAILABLE',
          implemented: true,
          note: '当前没有可展示的解释权重。',
          featureContributions: [],
        },
      },
    })

    expect(wrapper.text()).toContain('运行态不可用')
    expect(wrapper.text()).toContain('等待数据')
    expect(wrapper.text()).toContain('暂无解释权重')
    expect(wrapper.text()).toContain('暂无可解释性数据')
  })
})
