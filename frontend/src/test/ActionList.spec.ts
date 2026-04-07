import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ActionList from '../components/ActionList.vue'
import type { ActionItem } from '../types/domain'

const items: ActionItem[] = [
  {
    actionId: 'action-1',
    productCode: 'demo-earphone',
    issueId: 'iss-battery-7',
    actionName: '处理：续航体验波动',
    actionDesc: '基于动作关联评论窗口回看负向率变化。',
    status: 'PLANNED',
    createdAt: '2026-03-12T00:00:00Z',
  },
]

describe('ActionList', () => {
  it('renders action items in success state', () => {
    const wrapper = mount(ActionList, {
      props: {
        items,
        state: 'success',
      },
    })

    expect(wrapper.text()).toContain('改进行动登记')
    expect(wrapper.text()).toContain('处理：续航体验波动')
    expect(wrapper.text()).toContain('PLANNED')
  })

  it('renders empty-state guidance when no actions exist', () => {
    const wrapper = mount(ActionList, {
      props: {
        items: [],
        state: 'empty',
        message: '暂无动作，点击“登记演示数据动作”快速创建。',
      },
    })

    expect(wrapper.text()).toContain('暂无动作，点击“登记演示数据动作”快速创建。')
  })

  it('renders degraded-state notice while preserving available actions', () => {
    const wrapper = mount(ActionList, {
      props: {
        items,
        state: 'degraded',
        message: '动作列表暂时只返回部分结果，可稍后重试刷新。',
      },
    })

    expect(wrapper.text()).toContain('动作列表暂时只返回部分结果，可稍后重试刷新。')
    expect(wrapper.text()).toContain('处理：续航体验波动')
  })

  it('renders error-state message when actions fail to load', () => {
    const wrapper = mount(ActionList, {
      props: {
        items: [],
        state: 'error',
        message: '动作接口请求失败，请稍后重试。',
      },
    })

    expect(wrapper.text()).toContain('动作接口请求失败，请稍后重试。')
  })
})
