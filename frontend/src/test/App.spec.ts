import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from '../App.vue'
import LoginGate from '../components/LoginGate.vue'

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function settleLoginDelay(ms = 850): Promise<void> {
  await vi.advanceTimersByTimeAsync(ms)
  await flushPromises()
}

async function enterDashboard(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.get('[data-testid="login-username"]').setValue('wxy')
  await wrapper.get('[data-testid="login-password"]').setValue('123456')
  await wrapper.get('.form').trigger('submit')
  await settleLoginDelay()
}

describe('App shell', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    document.documentElement.removeAttribute('data-motion')
  })

  it('renders the redesigned auth gate with stable accessibility hooks before entering the dashboard', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Demo auth entry')
    expect(wrapper.text()).toContain('使用既有演示账号进入看板')
    expect(wrapper.text()).toContain('账号')
    expect(wrapper.text()).toContain('密码')
    expect(wrapper.text()).not.toContain('Nexus')
    expect(wrapper.text()).not.toContain('飞书账号一键登录')
    expect(wrapper.get('[data-testid="login-username"]').attributes('placeholder')).toBe('输入您的账号')
    expect(wrapper.get('[data-testid="login-password"]').attributes('placeholder')).toBe('输入您的密码')
    expect(wrapper.get('[data-testid="login-username"]').attributes('aria-invalid')).toBe('false')
    expect(wrapper.get('[data-testid="login-password"]').attributes('aria-invalid')).toBe('false')
    expect(wrapper.get('[data-testid="login-username"]').attributes('aria-describedby')).toBe('login-helper login-error-message')
    expect(wrapper.get('[data-testid="login-password"]').attributes('aria-describedby')).toBe('login-helper login-error-message')
    expect(wrapper.get('[data-testid="login-submit"]').text()).toBe('登录')
    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(false)
    expect(document.documentElement.getAttribute('data-motion')).toBe('none')

    await enterDashboard(wrapper)

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('当前用户：wxy')
    expect(document.documentElement.getAttribute('data-motion')).toBe('none')
  })

  it('preserves login-character interactions, password toggling, loading state, and validation messaging', async () => {
    vi.useRealTimers()

    const wrapper = mount(LoginGate)
    const monsters = wrapper.findAll('[data-testid="login-monster"]')

    expect(monsters.length).toBe(4)
    expect(monsters.map((item) => item.attributes('data-monster-id'))).toEqual(['purple', 'black', 'orange', 'yellow'])

    const pupils = wrapper.findAll('[data-testid="monster-pupil"]')
    expect(pupils.length).toBe(8)

    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 480, clientY: 320 }))
    await wait(50)

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
    expect(eyeToggle.attributes('aria-pressed')).toBe('false')
    await eyeToggle.trigger('click')
    expect(wrapper.get('[data-testid="login-password"]').attributes('type')).toBe('text')
    expect(wrapper.get('.eye-toggle').attributes('aria-pressed')).toBe('true')
    await eyeToggle.trigger('click')
    expect(wrapper.get('[data-testid="login-password"]').attributes('type')).toBe('password')

    await wrapper.get('[data-testid="login-username"]').setValue('bad-user')
    await wrapper.get('[data-testid="login-password"]').setValue('bad-pass')
    await wrapper.get('.form').trigger('submit')

    expect(wrapper.get('[data-testid="login-submit"]').text()).toBe('登录中...')
    expect(wrapper.get('[data-testid="login-submit"]').attributes('disabled')).toBeDefined()

    await wait(850)
    await flushPromises()

    expect(wrapper.get('[data-testid="login-error"]').text()).toContain('账号或密码有误')
    expect(wrapper.get('[data-testid="login-username"]').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('[data-testid="login-password"]').attributes('aria-invalid')).toBe('true')
    expect(wrapper.emitted('enter')).toBeUndefined()

    await wrapper.get('[data-testid="login-username"]').setValue('wxy')
    await wrapper.get('[data-testid="login-password"]').setValue('123456')
    await wrapper.get('.form').trigger('submit')
    await wait(850)
    await flushPromises()
    expect(wrapper.emitted('enter')?.[0]).toEqual([{ username: 'wxy' }])
  })

  it('renders grouped authenticated navigation, updates active state, and keeps chart modules on deterministic fallback content', async () => {
    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

    expect(wrapper.text()).toContain('蓝牙耳机评论改进决策系统')
    expect(wrapper.text()).toContain('核心模块')
    expect(wrapper.text()).toContain('演示场景')
    expect(wrapper.get('[data-testid="nav-overview"]').attributes('aria-current')).toBe('page')
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
    expect(wrapper.text()).toContain('报告中心')
    expect(wrapper.text()).not.toContain('韧性演练')

    await wrapper.get('[data-testid="nav-issues"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="nav-issues"]').attributes('aria-current')).toBe('page')
    expect(wrapper.text()).toContain('问题优先级清单')

    await wrapper.get('[data-testid="nav-trends"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="nav-trends"]').attributes('aria-current')).toBe('page')
    expect(wrapper.text()).toContain('趋势图（续航）')
    expect(wrapper.text()).toContain('默认显示最新周期详情')
    expect(wrapper.text()).toContain('2026-W09')
    expect(wrapper.text()).toContain('负面率 40.0%')
    expect(wrapper.find('.chart').exists()).toBe(false)

    await wrapper.get('[data-testid="nav-wordcloud"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="nav-wordcloud"]').attributes('aria-current')).toBe('page')
    expect(wrapper.text()).toContain('词云洞察（全部）')
    expect(wrapper.text()).toContain('当前高频词')
    expect(wrapper.text()).toContain('演示模式词云数据')
    expect(wrapper.text()).toContain('正向')
    expect(wrapper.find('.chart').exists()).toBe(false)

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
