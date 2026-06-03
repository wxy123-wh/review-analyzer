import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ProductSetupPanel from '../components/ProductSetupPanel.vue'

describe('ProductSetupPanel', () => {
  it('loads editable UX labels and walks through crawl handoff actions', async () => {
    const wrapper = mount(ProductSetupPanel)
    await flushPromises()

    expect(wrapper.text()).toContain('商品采集与标签配置')
    expect((wrapper.get('[data-testid="setup-secondary-0"]').element as HTMLInputElement).value).toBe('质量与性能')
    expect(wrapper.text()).toContain('3 / 3')

    await wrapper.get('[data-testid="setup-product-code"]').setValue('jd-new-product')
    await wrapper.get('[data-testid="setup-secondary-0"]').setValue('连接与稳定性')
    await wrapper.get('button.primary-btn').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('标签组合已在测试模式中保存。')
    expect(wrapper.text()).toContain('jd-new-product')
    expect((wrapper.get('[data-testid="setup-secondary-0"]').element as HTMLInputElement).value).toBe('连接与稳定性')

    await wrapper.findAll('button').find((button) => button.text() === '启动采集')?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('RUNNING')
    expect(wrapper.text()).toContain('采集任务已启动')

    await wrapper.findAll('button').find((button) => button.text() === '刷新任务')?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('SUCCEEDED')
    expect(wrapper.text()).toContain('128')

    await wrapper.findAll('button').find((button) => button.text() === '启动分析')?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('SUCCEEDED')
    expect(wrapper.text()).toContain('分析任务已启动')
  })
})
