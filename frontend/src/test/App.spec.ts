import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from '../App.vue'

describe('App shell', () => {
  it('renders cinematic login gate before entering dashboard', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(false)

    await wrapper.get('[data-testid="login-username"]').setValue('demo')
    await wrapper.get('[data-testid="login-password"]').setValue('demo')
    await wrapper.get('[data-testid="login-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('蓝牙耳机评论改进决策系统')
    expect(wrapper.text()).toContain('总览')
    expect(wrapper.text()).toContain('问题')
    expect(wrapper.text()).toContain('对比')
    expect(wrapper.text()).toContain('趋势')
    expect(wrapper.text()).toContain('动作')
    expect(wrapper.text()).toContain('验证')
    expect(wrapper.text()).toContain('流水线')
    expect(wrapper.text()).toContain('智能体')
    expect(wrapper.text()).toContain('可解释性')
    expect(wrapper.text()).toContain('混沌演练')
    expect(wrapper.text()).toContain('报告中心')

    await wrapper.get('[data-testid="nav-issues"]').trigger('click')
    expect(wrapper.text()).toContain('问题优先级清单')

    await wrapper.get('[data-testid="nav-compare"]').trigger('click')
    expect(wrapper.text()).toContain('竞品对比概览')

    await wrapper.get('[data-testid="nav-showcase-pipeline"]').trigger('click')
    expect(wrapper.text()).toContain('Pipeline Orchestration')
  })
})
