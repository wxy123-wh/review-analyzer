import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it } from 'vitest'

import TrendList from '../components/TrendList.vue'
import type { TrendPoint } from '../types/domain'

const points: TrendPoint[] = [
  { period: '2026-W10', negativeRate: 0.22, mentionVolume: 38 },
  { period: '2026-W11', negativeRate: 0.19, mentionVolume: 42 },
]

describe('TrendList', () => {
  it('renders fallback trend list in success state', async () => {
    const wrapper = mount(TrendList, {
      props: {
        aspect: 'battery',
        points,
        state: 'success',
      },
    })
    await nextTick()

    expect(wrapper.text()).toContain('趋势图（续航）')
    expect(wrapper.find('.chart').exists()).toBe(false)
    expect(wrapper.findAll('.point-list li')).toHaveLength(2)
    expect(wrapper.text()).toContain('2026-W10')
    expect(wrapper.text()).toContain('负面率 22.0%')
    expect(wrapper.text()).toContain('提及量 42')
    expect(wrapper.text()).toContain('点位值')
    expect(wrapper.text()).toContain('2026-W11')
    expect(wrapper.text()).toContain('19.0%')
  })

  it('shows empty state and emits retry', async () => {
    const wrapper = mount(TrendList, {
      props: {
        aspect: 'audio',
        points: [],
        state: 'empty',
        message: '暂无趋势数据，建议初始化演示评论数据后重试。',
      },
    })

    expect(wrapper.text()).toContain('暂无趋势数据，建议初始化演示评论数据后重试。')
    const refreshButton = wrapper.findAll('button').find((button) => button.text() === '刷新数据')
    expect(refreshButton).toBeDefined()
    await refreshButton!.trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('shows API failure state and emits retry', async () => {
    const wrapper = mount(TrendList, {
      props: {
        aspect: 'comfort',
        points: [],
        state: 'error',
        message: '趋势接口请求失败，请稍后重试。',
      },
    })

    expect(wrapper.text()).toContain('趋势接口请求失败，请稍后重试。')
    const reloadButton = wrapper.findAll('button').find((button) => button.text() === '重新加载')
    expect(reloadButton).toBeDefined()
    await reloadButton!.trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
