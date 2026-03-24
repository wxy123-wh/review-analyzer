import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from '../App.vue'
import LoginGate from '../components/LoginGate.vue'

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function enterDashboard(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.get('[data-testid="login-username"]').setValue('wxy')
  await wrapper.get('[data-testid="login-password"]').setValue('123456')
  await wrapper.get('.form').trigger('submit')
  await wait(850)
  await flushPromises()
}

describe('App shell', () => {
  it('renders the animated login gate before entering dashboard', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Nexus')
    expect(wrapper.text()).not.toContain('飞书账号一键登录')
    expect(wrapper.text()).not.toContain('统一接入前端平台旗下所有系统')
    expect(wrapper.get('[data-testid="login-username"]').attributes('placeholder')).toBe('输入您的账号')
    expect(wrapper.get('[data-testid="login-password"]').attributes('placeholder')).toBe('输入您的密码')
    expect(wrapper.get('[data-testid="login-submit"]').text()).toBe('登录')
    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(false)

    await enterDashboard(wrapper)

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('当前用户：wxy')
  })

  it('renders four animated characters, supports pupil tracking, jump/lighten interactions, password toggle and login validation', async () => {
    const wrapper = mount(LoginGate)
    const monsters = wrapper.findAll('[data-testid="login-monster"]')

    expect(monsters.length).toBe(4)
    expect(monsters.map((item) => item.attributes('data-monster-id'))).toEqual(['purple', 'black', 'orange', 'yellow'])

    const pupils = wrapper.findAll('[data-testid="monster-pupil"]')
    expect(pupils.length).toBe(8)
    const initialStyle = pupils[0].attributes('style') ?? ''

    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 480, clientY: 320 }))
    await wait(50)
    expect(pupils[0].attributes('style') ?? '').not.toBe(initialStyle)

    await monsters[0].trigger('click')
    await wait(260)
    expect(monsters[0].classes()).toContain('jumping')
    await wait(420)
    expect(monsters[0].classes()).not.toContain('jumping')

    await monsters[1].trigger('dblclick')
    await flushPromises()
    expect(monsters[1].classes()).toContain('lightened')
    await monsters[1].trigger('click')
    await flushPromises()
    expect(monsters[1].classes()).not.toContain('lightened')

    const passwordInput = wrapper.get('[data-testid="login-password"]')
    const eyeToggle = wrapper.get('.eye-toggle')

    expect(passwordInput.attributes('type')).toBe('password')
    await eyeToggle.trigger('click')
    expect(wrapper.get('[data-testid="login-password"]').attributes('type')).toBe('text')
    await eyeToggle.trigger('click')
    expect(wrapper.get('[data-testid="login-password"]').attributes('type')).toBe('password')

    await wrapper.get('[data-testid="login-username"]').setValue('bad-user')
    await wrapper.get('[data-testid="login-password"]').setValue('bad-pass')
    await wrapper.get('.form').trigger('submit')
    await wait(850)
    await flushPromises()
    expect(wrapper.get('[data-testid="login-error"]').text()).toContain('账号或密码有误')
    expect(wrapper.emitted('enter')).toBeUndefined()

    await wrapper.get('[data-testid="login-username"]').setValue('wxy')
    await wrapper.get('[data-testid="login-password"]').setValue('123456')
    await wrapper.get('.form').trigger('submit')
    await wait(850)
    await flushPromises()
    expect(wrapper.emitted('enter')?.[0]).toEqual([{ username: 'wxy' }])
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
