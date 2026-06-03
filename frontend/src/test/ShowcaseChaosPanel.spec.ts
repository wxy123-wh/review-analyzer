import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ShowcaseChaosPanel from '../components/ShowcaseChaosPanel.vue'

describe('ShowcaseChaosPanel', () => {
  it('renders runtime mapping and attention counts from real drill states', () => {
    const wrapper = mount(ShowcaseChaosPanel, {
      props: {
        data: {
          status: 'DEGRADED',
          implemented: true,
          note: '当前展示最近一次真实运行态告警。',
          drills: [
            { scenario: 'sync-runtime', state: 'DEGRADED', detail: 'latest sync remains queued' },
            { scenario: 'analysis-runtime', state: 'STABLE', detail: 'latest analysis completed successfully' },
            { scenario: 'materialization-runtime', state: 'UNAVAILABLE', detail: 'no materialized outputs yet' },
            { scenario: 'validation-runtime', state: 'RUNNING', detail: 'validation snapshot generation in progress' },
          ],
        },
      },
    })

    expect(wrapper.text()).toContain('降级可用')
    expect(wrapper.text()).toContain('运行信号')
    expect(wrapper.text()).toContain('4')
    expect(wrapper.text()).toContain('关注项')
    expect(wrapper.text()).toContain('2')
    expect(wrapper.text()).toContain('sync-runtime · DEGRADED')
    expect(wrapper.text()).toContain('当前展示 4 条韧性运行态信号')
    expect(wrapper.findAll('.mapping-item')).toHaveLength(3)
    expect(wrapper.findAll('.drill-card')).toHaveLength(4)
  })

  it('renders runtime-unavailable status without fake drill details', () => {
    const wrapper = mount(ShowcaseChaosPanel, {
      props: {
        data: {
          status: 'RUNTIME_UNAVAILABLE',
          implemented: true,
          note: '当前未记录可用于韧性视图的真实运行态。',
          drills: [],
        },
      },
    })

    expect(wrapper.text()).toContain('运行态不可用')
    expect(wrapper.text()).toContain('暂无运行信号')
    expect(wrapper.text()).toContain('当前没有需要关注的异常信号')
    expect(wrapper.text()).toContain('暂无混沌演练数据')
  })
})
