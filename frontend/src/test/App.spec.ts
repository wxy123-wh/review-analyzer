import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from '../App.vue'
import LoginGate from '../components/LoginGate.vue'

async function enterDashboard(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.get('[data-testid="login-username"]').setValue('demo')
  await wrapper.get('[data-testid="login-password"]').setValue('demo')
  await wrapper.get('[data-testid="login-submit"]').trigger('click')
  await flushPromises()
}

describe('App shell', () => {
  it('renders cinematic login gate before entering dashboard', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(false)

    await enterDashboard(wrapper)

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(true)
  })

  it('tracks mascot cursor and toggles privacy animation on password focus', async () => {
    const wrapper = mount(LoginGate)
    const gate = wrapper.get('[data-testid="login-gate"]')

    await gate.trigger('mousemove', { clientX: 480, clientY: 320 })

    const cursor = wrapper.get('[data-testid="mascot-cursor-core"]')
    expect(cursor.attributes('style')).toContain('left: 480px')
    expect(cursor.attributes('style')).toContain('top: 320px')

    const pupils = wrapper.findAll('[data-testid="mascot-pupil"]')
    expect(pupils.length).toBe(2)
    expect(pupils[0].attributes('style')).toContain('translate(')

    const mascot = wrapper.get('[data-testid="login-mascot"]')
    expect(mascot.classes()).not.toContain('privacy')

    await wrapper.get('[data-testid="login-password"]').trigger('focus')
    expect(mascot.classes()).toContain('privacy')

    await wrapper.get('[data-testid="login-password"]').trigger('blur')
    expect(mascot.classes()).not.toContain('privacy')
  })

  it('renders baseline and showcase modules through navigation', async () => {
    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

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
    await flushPromises()
    expect(wrapper.text()).toContain('流水线编排')
    expect(wrapper.text()).toContain('演示数据')

    await wrapper.get('[data-testid="nav-showcase-agent-arena"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('智能体协同台')

    await wrapper.get('[data-testid="nav-showcase-explainability"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('可解释性分析')

    await wrapper.get('[data-testid="nav-showcase-chaos"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('混沌演练剧场')

    await wrapper.get('[data-testid="nav-showcase-report-center"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('报告中心')
    expect(wrapper.text()).toContain('生成演示数据报告预览')
  })
})
