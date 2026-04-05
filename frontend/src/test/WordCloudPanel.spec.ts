import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import WordCloudPanel from '../components/WordCloudPanel.vue'
import type { WordCloudItem } from '../types/domain'

const items: WordCloudItem[] = [
  { keyword: '续航', frequency: 28, weight: 52, sentimentTag: 'POSITIVE' },
  { keyword: '漏音', frequency: 18, weight: 36, sentimentTag: 'NEGATIVE' },
]

describe('WordCloudPanel', () => {
  it('renders fallback chip list, legend, and summary in success state', () => {
    const wrapper = mount(WordCloudPanel, {
      props: {
        aspect: 'all',
        items,
        state: 'success',
        notice: '演示数据已按词频聚合。',
      },
    })

    expect(wrapper.text()).toContain('词云洞察（全部）')
    expect(wrapper.text()).toContain('颜色仅承担情绪分组')
    expect(wrapper.text()).toContain('正向')
    expect(wrapper.text()).toContain('中性')
    expect(wrapper.text()).toContain('负向')
    expect(wrapper.find('.chart').exists()).toBe(false)
    const chips = wrapper.findAll('.chip-list li')
    expect(chips).toHaveLength(2)
    expect(chips[0].classes()).toContain('positive')
    expect(chips[1].classes()).toContain('negative')
    expect(wrapper.text()).toContain('词频 28')
    expect(wrapper.text()).toContain('当前高频词')
    expect(wrapper.find('.summary').text()).toContain('续航')
    expect(wrapper.find('.summary').text()).toContain('正向')
    expect(wrapper.text()).toContain('演示数据已按词频聚合。')
    expect(wrapper.text()).toContain('触控提示：轻触词项可查看关键词与词频详情。')
  })

  it('normalizes unknown sentiment tags to neutral fallback copy and class', () => {
    const wrapper = mount(WordCloudPanel, {
      props: {
        aspect: 'audio',
        items: [{ keyword: '延迟', frequency: 12, weight: 20, sentimentTag: 'mystery' }],
        state: 'success',
      },
    })

    const chip = wrapper.get('.chip-list li')
    expect(chip.classes()).toContain('neutral')
    expect(wrapper.find('.summary').text()).toContain('中性')
  })

  it('shows empty state and emits retry', async () => {
    const wrapper = mount(WordCloudPanel, {
      props: {
        aspect: 'audio',
        items: [],
        state: 'empty',
        message: '暂无词云数据，建议初始化演示评论数据后重试。',
      },
    })

    expect(wrapper.text()).toContain('暂无词云数据，建议初始化演示评论数据后重试。')
    const refreshButton = wrapper.findAll('button').find((button) => button.text() === '刷新数据')
    expect(refreshButton).toBeDefined()
    await refreshButton!.trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('shows timeout fallback message and emits retry', async () => {
    const wrapper = mount(WordCloudPanel, {
      props: {
        aspect: 'call',
        items: [],
        state: 'timeout',
      },
    })

    expect(wrapper.text()).toContain('接口状态')
    expect(wrapper.text()).toContain('词云接口请求超时，请检查网络后重试。')
    const reloadButton = wrapper.findAll('button').find((button) => button.text() === '重新加载')
    expect(reloadButton).toBeDefined()
    await reloadButton!.trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('shows API failure state and emits retry', async () => {
    const wrapper = mount(WordCloudPanel, {
      props: {
        aspect: 'call',
        items: [],
        state: 'error',
        message: '词云接口请求失败，请稍后重试。',
      },
    })

    expect(wrapper.text()).toContain('词云接口请求失败，请稍后重试。')
    const reloadButton = wrapper.findAll('button').find((button) => button.text() === '重新加载')
    expect(reloadButton).toBeDefined()
    await reloadButton!.trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
