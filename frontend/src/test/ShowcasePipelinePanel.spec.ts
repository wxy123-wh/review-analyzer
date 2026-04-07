import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ShowcasePipelinePanel from '../components/ShowcasePipelinePanel.vue'

describe('ShowcasePipelinePanel', () => {
  it('renders live pipeline metrics from real-style stage states', () => {
    const wrapper = mount(ShowcasePipelinePanel, {
      props: {
        data: {
          status: 'LIVE',
          implemented: true,
          note: '流水线视图来自真实 sync/analysis/materialization/action/validation 状态。',
          stages: [
            { name: 'SYNC', state: 'QUEUED', detail: 'provider=aggregator-demo; fetchedCount=0' },
            { name: 'ANALYSIS', state: 'RUNNING', detail: 'jobId=analysis-11' },
            { name: 'MATERIALIZATION', state: 'SUCCEEDED', detail: 'issueCount=5' },
            { name: 'VALIDATION', state: 'DEGRADED', detail: 'validation snapshots are lagging' },
          ],
        },
      },
    })

    expect(wrapper.text()).toContain('实时数据')
    expect(wrapper.text()).toContain('阶段总数')
    expect(wrapper.text()).toContain('4')
    expect(wrapper.text()).toContain('进行中')
    expect(wrapper.text()).toContain('1')
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('SYNC · QUEUED')
    expect(wrapper.text()).toContain('有 1 个阶段处于推进中')
    expect(wrapper.findAll('.stage-card')).toHaveLength(4)
    expect(wrapper.findAll('.state-pill.accent')).toHaveLength(1)
    expect(wrapper.findAll('.state-pill.down')).toHaveLength(1)
  })

  it('renders runtime-unavailable empty state without pretending stage data exists', () => {
    const wrapper = mount(ShowcasePipelinePanel, {
      props: {
        data: {
          status: 'RUNTIME_UNAVAILABLE',
          implemented: true,
          note: '当前未记录可用于流水线视图的真实运行态。',
          stages: [],
        },
      },
    })

    expect(wrapper.text()).toContain('运行态不可用')
    expect(wrapper.text()).toContain('暂无阶段信号')
    expect(wrapper.text()).toContain('等待流水线数据')
    expect(wrapper.text()).toContain('暂无流水线数据')
  })
})
