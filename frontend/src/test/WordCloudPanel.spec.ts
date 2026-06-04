import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import WordCloudPanel from '../components/WordCloudPanel.vue'
import type { WordCloudItem } from '../types/domain'

const items: WordCloudItem[] = [
  { keyword: '续航', frequency: 28, weight: 52, sentimentTag: 'POSITIVE', partOfSpeech: '名词', wordType: '体验维度' },
  { keyword: '漏音', frequency: 18, weight: 36, sentimentTag: 'NEGATIVE', partOfSpeech: '动词', wordType: '问题词' },
]

describe('WordCloudPanel', () => {
  it('renders compact word cloud, ranking, and selected keyword detail in success state', () => {
    const wrapper = mount(WordCloudPanel, {
      props: {
        aspect: 'all',
        items,
        state: 'success',
        notice: '真实评论已按词频聚合。',
      },
    })

    expect(wrapper.text()).toContain('词云')
    expect(wrapper.text()).toContain('全部 · 2 个关键词')
    expect(wrapper.find('.wordcloud-stage').exists()).toBe(true)
    expect(wrapper.findAll('.word-node')).toHaveLength(2)
    expect(wrapper.findAll('.rank-list li')).toHaveLength(2)
    expect(wrapper.text()).toContain('正向')
    expect(wrapper.text()).toContain('负向')
    expect(wrapper.findAll('.word-node')[0].classes()).toContain('positive')
    expect(wrapper.findAll('.word-node')[1].classes()).toContain('negative')
    expect(wrapper.find('.word-detail').text()).toContain('续航')
    expect(wrapper.find('.word-detail').text()).toContain('28')
    expect(wrapper.find('.word-detail').text()).toContain('名词')
    expect(wrapper.find('.word-detail').text()).toContain('体验维度')
    expect(wrapper.text()).not.toContain('颜色仅承担情绪分组')
    expect(wrapper.text()).not.toContain('触控提示')
  })

  it('normalizes unknown sentiment tags to neutral fallback copy and class', () => {
    const wrapper = mount(WordCloudPanel, {
      props: {
        aspect: 'audio',
        items: [{ keyword: '延迟', frequency: 12, weight: 20, sentimentTag: 'mystery', partOfSpeech: '动词', wordType: '问题词' }],
        state: 'success',
      },
    })

    const word = wrapper.get('.word-node')
    expect(word.classes()).toContain('neutral')
    expect(wrapper.find('.word-detail').text()).toContain('中性')
  })

  it('shows empty state and emits retry', async () => {
    const onRetry = vi.fn()
    const wrapper = mount(WordCloudPanel, {
      props: {
        aspect: 'audio',
        items: [],
        state: 'empty',
        message: '暂无词云数据，请先导入真实评论并等待自动分析完成后重试。',
        onRetry,
      },
    })

    expect(wrapper.text()).toContain('暂无词云数据，请先导入真实评论并等待自动分析完成后重试。')
    const refreshButton = wrapper.findAll('button').find((button) => button.text() === '刷新数据')
    expect(refreshButton).toBeDefined()
    await refreshButton!.trigger('click')
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('shows timeout fallback message and emits retry', async () => {
    const onRetry = vi.fn()
    const wrapper = mount(WordCloudPanel, {
      props: {
        aspect: 'call',
        items: [],
        state: 'timeout',
        onRetry,
      },
    })

    expect(wrapper.text()).toContain('接口状态')
    expect(wrapper.text()).toContain('词云接口请求超时，请检查网络后重试。')
    const reloadButton = wrapper.findAll('button').find((button) => button.text() === '重新加载')
    expect(reloadButton).toBeDefined()
    await reloadButton!.trigger('click')
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('shows API failure state and emits retry', async () => {
    const onRetry = vi.fn()
    const wrapper = mount(WordCloudPanel, {
      props: {
        aspect: 'call',
        items: [],
        state: 'error',
        message: '词云接口请求失败，请稍后重试。',
        onRetry,
      },
    })

    expect(wrapper.text()).toContain('词云接口请求失败，请稍后重试。')
    const reloadButton = wrapper.findAll('button').find((button) => button.text() === '重新加载')
    expect(reloadButton).toBeDefined()
    await reloadButton!.trigger('click')
    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})
