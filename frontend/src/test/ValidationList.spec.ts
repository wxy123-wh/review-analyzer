import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ValidationList from '../components/ValidationList.vue'
import type { ValidationItem } from '../types/domain'

const items: ValidationItem[] = [
  {
    actionId: 'action-test-1',
    beforeNegativeRate: 0.42,
    afterNegativeRate: 0.31,
    improvementRate: 0.11,
    summary: '上线后负面率下降 11.00%，问题热度趋稳。',
  },
]

describe('ValidationList', () => {
  it('renders validation entries in success state', () => {
    const wrapper = mount(ValidationList, {
      props: {
        items,
        state: 'success',
      },
    })

    expect(wrapper.text()).toContain('改进效果验证')
    expect(wrapper.text()).toContain('action-test-1')
    expect(wrapper.text()).toContain('改善 11.00%')
  })

  it('renders empty-state copy', () => {
    const wrapper = mount(ValidationList, {
      props: {
        items: [],
        state: 'empty',
        message: '暂无验证结果',
      },
    })

    expect(wrapper.text()).toContain('暂无验证结果')
  })

  it('renders degraded-state notice while keeping available validation rows', () => {
    const wrapper = mount(ValidationList, {
      props: {
        items,
        state: 'degraded',
        message: '验证结果暂时回退为部分数据，请稍后刷新。',
      },
    })

    expect(wrapper.text()).toContain('验证结果暂时回退为部分数据，请稍后刷新。')
    expect(wrapper.text()).toContain('上线后负面率下降 11.00%，问题热度趋稳。')
  })

  it('renders error-state message when validation loading fails', () => {
    const wrapper = mount(ValidationList, {
      props: {
        items: [],
        state: 'error',
        message: '验证接口请求失败，请稍后重试。',
      },
    })

    expect(wrapper.text()).toContain('验证接口请求失败，请稍后重试。')
  })
})
