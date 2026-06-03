import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ShowcaseAgentArenaPanel from '../components/ShowcaseAgentArenaPanel.vue'

describe('ShowcaseAgentArenaPanel', () => {
  it('renders synthesized arena metrics and active-node support', () => {
    const wrapper = mount(ShowcaseAgentArenaPanel, {
      props: {
        data: {
          status: 'LIVE',
          implemented: true,
          note: '席位状态由真实子系统运行态合成。',
          agents: [
            { agentName: 'sync-lane', role: 'SYNC', state: 'QUEUED', confidence: 0.6 },
            { agentName: 'analysis-lane', role: 'ANALYSIS', state: 'RUNNING', confidence: 0.92 },
            { agentName: 'validation-lane', role: 'VALIDATION', state: 'SUCCEEDED', confidence: 0.76 },
          ],
        },
      },
    })

    expect(wrapper.text()).toContain('实时数据')
    expect(wrapper.text()).toContain('智能体数量')
    expect(wrapper.text()).toContain('3')
    expect(wrapper.text()).toContain('平均置信度')
    expect(wrapper.text()).toContain('76%')
    expect(wrapper.text()).toContain('最高 analysis-lane · 92%')
    expect(wrapper.text()).toContain('analysis-lane 正在推进 ANALYSIS')
    expect(wrapper.text()).toContain('3 类职责可见')
    expect(wrapper.findAll('tbody tr')).toHaveLength(3)
  })

  it('falls back to attention support when no node is actively running', () => {
    const wrapper = mount(ShowcaseAgentArenaPanel, {
      props: {
        data: {
          status: 'DEGRADED',
          implemented: true,
          note: '当前仅保留最近一次真实子系统状态。',
          agents: [
            { agentName: 'analysis-lane', role: 'ANALYSIS', state: 'DEGRADED', confidence: 0.51 },
          ],
        },
      },
    })

    expect(wrapper.text()).toContain('降级可用')
    expect(wrapper.text()).toContain('analysis-lane 当前处于 DEGRADED')
    expect(wrapper.find('.state-pill.down').text()).toContain('DEGRADED')
  })
})
