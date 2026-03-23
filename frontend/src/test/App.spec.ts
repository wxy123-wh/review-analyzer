import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from '../App.vue'
import LoginGate from '../components/LoginGate.vue'

async function enterDashboard(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.get('[data-testid="login-username"]').setValue('wxy')
  await wrapper.get('[data-testid="login-password"]').setValue('123456')
  await wrapper.get('[data-testid="login-submit"]').trigger('click')
  await flushPromises()
}

describe('App shell', () => {
  it('renders cinematic login gate before entering dashboard', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('评论改进决策系统')
    expect(wrapper.text()).toContain('请输入演示账号进入系统。')
    expect(wrapper.get('[data-testid="login-username"]').attributes('placeholder')).toBe('请输入用户名')
    expect(wrapper.get('[data-testid="login-password"]').attributes('placeholder')).toBe('请输入密码')
    expect(wrapper.get('[data-testid="login-submit"]').text()).toBe('进入系统')
    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(false)

    await enterDashboard(wrapper)

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('当前用户：wxy')
  })

  it('renders three monsters and supports tracking, privacy, shake and jump states', async () => {
    const wrapper = mount(LoginGate)
    const gate = wrapper.get('[data-testid="login-gate"]')
    const monsters = wrapper.findAll('[data-testid="login-monster"]')

    expect(monsters.length).toBe(3)
    expect(monsters.map((item) => item.attributes('data-monster-id'))).toEqual(['red', 'blue', 'yellow'])

    await gate.trigger('mousemove', { clientX: 480, clientY: 320 })

    const pupils = wrapper.findAll('[data-testid="monster-pupil"]')
    expect(pupils.length).toBe(6)
    expect(pupils[0].attributes('style')).toContain('translate(')

    monsters.forEach((monster) => {
      expect(monster.classes()).not.toContain('avoiding')
    })

    await wrapper.get('[data-testid="login-password"]').trigger('focus')
    monsters.forEach((monster) => {
      expect(monster.classes()).toContain('avoiding')
    })

    await wrapper.get('[data-testid="login-password"]').trigger('blur')
    monsters.forEach((monster) => {
      expect(monster.classes()).not.toContain('avoiding')
    })

    await monsters[1].trigger('click')
    await flushPromises()
    expect(monsters[1].classes()).toContain('jumping')
    expect(monsters[0].classes()).not.toContain('jumping')
    expect(monsters[2].classes()).not.toContain('jumping')

    await wrapper.get('[data-testid="login-username"]').setValue('bad-user')
    await wrapper.get('[data-testid="login-password"]').setValue('bad-pass')
    await wrapper.get('[data-testid="login-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="login-error"]').text()).toContain('账号或密码错误')
    expect(wrapper.get('[data-testid="login-error"]').text()).toContain('wxy / 123456')
    monsters.forEach((monster) => {
      expect(monster.classes()).toContain('shaking')
    })
  })

  it('renders baseline and showcase modules through navigation', async () => {
    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

    expect(wrapper.text()).toContain('蓝牙耳机评论改进决策系统')
    expect(wrapper.text()).toContain('总览')
    expect(wrapper.text()).toContain('问题')
    expect(wrapper.text()).toContain('对比')
    expect(wrapper.text()).toContain('趋势图')
    expect(wrapper.text()).toContain('词云')
    expect(wrapper.text()).toContain('动作')
    expect(wrapper.text()).toContain('验证')
    expect(wrapper.text()).toContain('流水线')
    expect(wrapper.text()).toContain('智能体')
    expect(wrapper.text()).toContain('可解释性')
    expect(wrapper.text()).not.toContain('混沌演练')
    expect(wrapper.text()).toContain('报告中心')

    await wrapper.get('[data-testid="nav-issues"]').trigger('click')
    expect(wrapper.text()).toContain('问题优先级清单')

    await wrapper.get('[data-testid="nav-compare"]').trigger('click')
    expect(wrapper.text()).toContain('竞品对比概览')

    await wrapper.get('[data-testid="nav-wordcloud"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('词云洞察')

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

    await wrapper.get('[data-testid="nav-showcase-report-center"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('报告中心')
    expect(wrapper.text()).toContain('生成演示数据报告预览')
  })
})
