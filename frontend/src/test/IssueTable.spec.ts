import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import IssueTable from '../components/IssueTable.vue'
import type { IssueItem } from '../types/domain'

const items: IssueItem[] = [
  {
    issueId: 'iss-bluetooth-1',
    title: '连接稳定性偶发断连',
    aspect: 'bluetooth',
    priorityScore: 0.5543,
    evidenceSummary: '近30天断连反馈上升且竞品差距扩大。',
  },
]

describe('IssueTable', () => {
  it('renders issue rows in success state', () => {
    const wrapper = mount(IssueTable, {
      props: {
        items,
        state: 'success',
      },
    })

    expect(wrapper.text()).toContain('问题优先级清单')
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    expect(wrapper.text()).toContain('连接稳定性偶发断连')
    expect(wrapper.text()).toContain('0.5543')
  })

  it('renders explicit empty-state copy', () => {
    const wrapper = mount(IssueTable, {
      props: {
        items: [],
        state: 'empty',
        message: '暂无问题数据',
      },
    })

    expect(wrapper.text()).toContain('暂无问题数据')
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
  })

  it('renders degraded-state notice instead of pretending success', () => {
    const wrapper = mount(IssueTable, {
      props: {
        items,
        state: 'degraded',
        message: '问题列表暂时回退为受限结果，请稍后刷新。',
      },
    })

    expect(wrapper.text()).toContain('问题列表暂时回退为受限结果，请稍后刷新。')
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
  })

  it('renders error-state message without table rows', () => {
    const wrapper = mount(IssueTable, {
      props: {
        items: [],
        state: 'error',
        message: '问题接口请求失败，请稍后重试。',
      },
    })

    expect(wrapper.text()).toContain('问题接口请求失败，请稍后重试。')
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
  })
})
