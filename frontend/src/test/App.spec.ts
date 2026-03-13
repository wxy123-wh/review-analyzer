import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from '../App.vue'

describe('App shell', () => {
  it('renders narrow sidebar and switches modules', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('蓝牙耳机评论改进决策系统')
    expect(wrapper.text()).toContain('总览')
    expect(wrapper.text()).toContain('问题')
    expect(wrapper.text()).toContain('对比')
    expect(wrapper.text()).toContain('趋势')
    expect(wrapper.text()).toContain('动作')
    expect(wrapper.text()).toContain('验证')

    await wrapper.get('[data-testid="nav-issues"]').trigger('click')
    expect(wrapper.text()).toContain('问题优先级清单')

    await wrapper.get('[data-testid="nav-compare"]').trigger('click')
    expect(wrapper.text()).toContain('竞品对比概览')
  })
})
