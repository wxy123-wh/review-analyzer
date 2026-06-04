import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import TrendList from '../components/TrendList.vue'
import type { TrendSeries } from '../types/domain'

const series: TrendSeries[] = [
  {
    id: 'battery',
    aspect: 'battery',
    uxPrimaryLabel: '产品硬件',
    uxSecondaryLabel: '电池与续航',
    color: '#2563eb',
    points: [
      { period: '2026-W10', negativeRate: 0.22, mentionVolume: 38 },
      { period: '2026-W11', negativeRate: 0.19, mentionVolume: 42 },
    ],
    state: 'success',
  },
  {
    id: 'connection',
    aspect: 'bluetooth',
    uxPrimaryLabel: '产品硬件',
    uxSecondaryLabel: '连接与稳定性',
    color: '#059669',
    points: [
      { period: '2026-W10', negativeRate: 0.31, mentionVolume: 28 },
      { period: '2026-W11', negativeRate: 0.37, mentionVolume: 35 },
    ],
    state: 'success',
  },
]

describe('TrendList', () => {
  it('renders multi-series SVG trend chart and latest-point detail in success state', () => {
    const wrapper = mount(TrendList, {
      props: {
        series,
        state: 'success',
      },
    })

    expect(wrapper.text()).toContain('趋势图')
    expect(wrapper.text()).toContain('2/2 个维度 · 2 个周期')
    expect(wrapper.text()).toContain('电池与续航')
    expect(wrapper.text()).toContain('连接与稳定性')
    expect(wrapper.find('.trend-svg').exists()).toBe(true)
    expect(wrapper.findAll('.line-path')).toHaveLength(2)
    expect(wrapper.findAll('.point-list li')).toHaveLength(2)
    expect(wrapper.text()).toContain('2026-W10')
    expect(wrapper.text()).toContain('22.0%')
    expect(wrapper.text()).toContain('42 条')
    expect(wrapper.text()).toContain('当前周期')
    expect(wrapper.find('.point-detail').text()).toContain('2026-W11')
    expect(wrapper.find('.point-detail').text()).toContain('19.0%')
    expect(wrapper.find('.point-detail').text()).toContain('-3.0%')
    expect(wrapper.text()).not.toContain('折线区域保持轻量')
    expect(wrapper.text()).not.toContain('触控提示')
  })

  it('highlights a clicked series and refreshes the detail panel', async () => {
    const wrapper = mount(TrendList, {
      props: {
        series,
        state: 'success',
      },
    })

    await wrapper.findAll('.legend-item').find((button) => button.text().includes('连接与稳定性'))!.trigger('click')

    expect(wrapper.find('.point-detail').text()).toContain('连接与稳定性')
    expect(wrapper.find('.point-detail').text()).toContain('37.0%')
    expect(wrapper.find('.point-detail').text()).toContain('+6.0%')
    expect(wrapper.findAll('.legend-item')[1].classes()).toContain('active')
  })

  it('shows loading state without retry controls', () => {
    const wrapper = mount(TrendList, {
      props: {
        series: [],
        state: 'loading',
      },
    })

    expect(wrapper.text()).toContain('加载中')
    expect(wrapper.text()).toContain('正在加载趋势图，请稍候...')
    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('shows empty state and emits retry', async () => {
    const onRetry = vi.fn()
    const wrapper = mount(TrendList, {
      props: {
        series: [],
        state: 'empty',
        message: '暂无趋势数据，请先导入真实评论并等待自动分析完成后重试。',
        onRetry,
      },
    })

    expect(wrapper.text()).toContain('暂无趋势数据，请先导入真实评论并等待自动分析完成后重试。')
    const refreshButton = wrapper.findAll('button').find((button) => button.text() === '刷新数据')
    expect(refreshButton).toBeDefined()
    await refreshButton!.trigger('click')
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('shows timeout fallback message and emits retry', async () => {
    const onRetry = vi.fn()
    const wrapper = mount(TrendList, {
      props: {
        series: [],
        state: 'timeout',
        onRetry,
      },
    })

    expect(wrapper.text()).toContain('接口状态')
    expect(wrapper.text()).toContain('趋势接口请求超时，请检查网络后重试。')
    const reloadButton = wrapper.findAll('button').find((button) => button.text() === '重新加载')
    expect(reloadButton).toBeDefined()
    await reloadButton!.trigger('click')
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('shows API failure state and emits retry', async () => {
    const onRetry = vi.fn()
    const wrapper = mount(TrendList, {
      props: {
        series: [],
        state: 'error',
        message: '趋势接口请求失败，请稍后重试。',
        onRetry,
      },
    })

    expect(wrapper.text()).toContain('趋势接口请求失败，请稍后重试。')
    const reloadButton = wrapper.findAll('button').find((button) => button.text() === '重新加载')
    expect(reloadButton).toBeDefined()
    await reloadButton!.trigger('click')
    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})
