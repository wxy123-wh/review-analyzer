import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import LoginGate from '../components/LoginGate.vue'
import ShowcaseChaosPanel from '../components/ShowcaseChaosPanel.vue'

const clientMocks = vi.hoisted(() => ({
  createAction: vi.fn(),
  fetchActions: vi.fn(),
  fetchBackendHealth: vi.fn(),
  fetchCompare: vi.fn(),
  fetchIssues: vi.fn(),
  fetchShowcaseAgentArena: vi.fn(),
  fetchShowcaseChaos: vi.fn(),
  fetchShowcaseExplainability: vi.fn(),
  fetchShowcasePipeline: vi.fn(),
  fetchTrends: vi.fn(),
  fetchValidation: vi.fn(),
  fetchWordCloud: vi.fn(),
  nlpDemoStatus: vi.fn(),
  previewShowcaseReport: vi.fn(),
}))

vi.mock('../api/client', () => ({
  createAction: clientMocks.createAction,
  fetchActions: clientMocks.fetchActions,
  fetchBackendHealth: clientMocks.fetchBackendHealth,
  fetchCompare: clientMocks.fetchCompare,
  fetchIssues: clientMocks.fetchIssues,
  fetchShowcaseAgentArena: clientMocks.fetchShowcaseAgentArena,
  fetchShowcaseChaos: clientMocks.fetchShowcaseChaos,
  fetchShowcaseExplainability: clientMocks.fetchShowcaseExplainability,
  fetchShowcasePipeline: clientMocks.fetchShowcasePipeline,
  fetchTrends: clientMocks.fetchTrends,
  fetchValidation: clientMocks.fetchValidation,
  fetchWordCloud: clientMocks.fetchWordCloud,
  nlpDemoStatus: clientMocks.nlpDemoStatus,
  previewShowcaseReport: clientMocks.previewShowcaseReport,
}))

import App from '../App.vue'

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
    clientMocks.fetchBackendHealth.mockResolvedValue({ name: 'Backend API', status: 'UP' })
    clientMocks.fetchIssues.mockResolvedValue({
      state: 'success',
      items: [
        {
          issueId: 'iss-bluetooth-001',
          title: '连接稳定性偶发断连',
          aspect: 'bluetooth',
          priorityScore: 0.554,
          evidenceSummary: '近30天断连反馈上升且竞品差距扩大。',
        },
      ],
    })
    clientMocks.fetchActions.mockResolvedValue({
      state: 'success',
      items: [
        {
          actionId: 'action-test-1',
          productCode: 'demo-earphone',
          issueId: 'iss-battery-7',
          actionName: '处理：续航体验波动',
          actionDesc: '基于动作关联评论窗口回看负向率变化。',
          status: 'PLANNED',
          createdAt: '2026-03-12T00:00:00Z',
        },
      ],
    })
    clientMocks.fetchCompare.mockResolvedValue({
      productCode: 'demo-earphone',
      comparisonProductCode: 'demo-earphone-competitor',
      state: 'success',
      items: [
        { aspect: 'battery', ourScore: 0.22, competitorScore: 0.78, gap: -0.56 },
        { aspect: 'noise-canceling', ourScore: 0.5, competitorScore: 0.78, gap: -0.28 },
      ],
    })
    clientMocks.fetchTrends.mockResolvedValue({
      aspect: 'battery',
      points: [
        { period: '2026-W06', negativeRate: 0.31, mentionVolume: 75 },
        { period: '2026-W09', negativeRate: 0.4, mentionVolume: 105 },
      ],
      state: 'success',
    })
    clientMocks.fetchValidation.mockResolvedValue({
      state: 'success',
      items: [
        {
          actionId: 'action-test-1',
          beforeNegativeRate: 0.42,
          afterNegativeRate: 0.31,
          improvementRate: 0.11,
          summary: '上线后负面率下降 11.00%，问题热度趋稳。',
        },
      ],
    })
    clientMocks.fetchWordCloud.mockResolvedValue({
      productCode: 'demo-earphone',
      aspect: 'all',
      items: [
        { keyword: '续航', frequency: 42, weight: 0.92, sentimentTag: 'POSITIVE' },
        { keyword: '断连', frequency: 31, weight: 0.85, sentimentTag: 'NEGATIVE' },
      ],
      notice: '演示模式词云数据',
      state: 'success',
    })
    clientMocks.nlpDemoStatus.mockReturnValue({ name: 'NLP Service', status: 'UP' })
    clientMocks.fetchShowcasePipeline.mockResolvedValue({
      status: 'LIVE',
      implemented: true,
      note: '流水线视图来自真实 sync/analysis/materialization/action/validation 状态。',
      stages: [
        { name: 'SYNC', state: 'QUEUED', detail: 'provider=aggregator-demo; productCode=demo-earphone; fetchedCount=0' },
        { name: 'ANALYSIS', state: 'SUCCEEDED', detail: 'productCode=demo-earphone; jobId=analysis-test-1' },
        { name: 'MATERIALIZATION', state: 'SUCCEEDED', detail: 'productCode=demo-earphone; issueCount=3' },
      ],
    })
    clientMocks.fetchShowcaseAgentArena.mockResolvedValue({
      status: 'LIVE',
      implemented: true,
      note: '席位状态由真实子系统运行态合成。',
      agents: [
        { agentName: 'sync-lane', role: 'SYNC', state: 'QUEUED', confidence: 0.6 },
        { agentName: 'analysis-lane', role: 'ANALYSIS', state: 'SUCCEEDED', confidence: 0.92 },
      ],
    })
    clientMocks.fetchShowcaseExplainability.mockResolvedValue({
      status: 'CONTROLLED_DATA_ONLY',
      implemented: true,
      note: '当前解释的是固定权重问题分数拆解，不是模型内部归因。',
      featureContributions: [
        { feature: 'negative_rate', weight: 0.41 },
        { feature: 'mention_volume', weight: 0.28 },
      ],
    })
    clientMocks.fetchShowcaseChaos.mockResolvedValue({
      status: 'DEGRADED',
      implemented: true,
      note: '当前展示最近一次真实运行态告警。',
      drills: [
        { scenario: 'sync-runtime', state: 'DEGRADED', detail: 'latest sync remains queued' },
        { scenario: 'analysis-runtime', state: 'STABLE', detail: 'latest analysis completed successfully' },
      ],
    })
    clientMocks.previewShowcaseReport.mockResolvedValue({
      status: 'LIVE',
      implemented: true,
      note: '当前预览由真实 issues/compare/trends/actions/validation 查询结果拼装。',
      previewSections: ['执行摘要：当前最高优先级问题是连接稳定性偶发断连。'],
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    document.documentElement.removeAttribute('data-motion')
    vi.clearAllMocks()
  })

  it('renders the redesigned auth gate with stable accessibility hooks before entering the dashboard', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Internal-use access')
    expect(wrapper.text()).toContain('使用当前环境凭据进入看板')
    expect(wrapper.text()).toContain('仅用于内部首发验收与演示环境访问。')
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
    expect(wrapper.text()).toContain('当前用户：内部体验账号')
    expect(document.documentElement.getAttribute('data-motion')).toBe('none')
  })

  it('preserves login-character interactions, password toggling, loading state, and validation messaging', async () => {
    vi.useRealTimers()

    const wrapper = mount(LoginGate, {
      props: {
        expectedUsername: 'internal-review',
        expectedPassword: 'internal-pass',
        displayName: '内部评审',
        accessHint: '仅开放给内部评审环境。',
      },
    })
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

    expect(wrapper.text()).toContain('internal-review')
    expect(wrapper.text()).toContain('internal-pass')
    expect(wrapper.text()).toContain('仅开放给内部评审环境。')

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

    await wrapper.get('[data-testid="login-username"]').setValue('internal-review')
    await wrapper.get('[data-testid="login-password"]').setValue('internal-pass')
    await wrapper.get('.form').trigger('submit')
    await wait(850)
    await flushPromises()
    expect(wrapper.emitted('enter')?.[0]).toEqual([{ username: 'internal-review', displayName: '内部评审' }])
  })

  it('renders dashboard data from real response contracts and keeps compare/trend/wordcloud semantics intact', async () => {
    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

    expect(clientMocks.fetchIssues).toHaveBeenCalledTimes(1)
    expect(clientMocks.fetchActions).toHaveBeenCalledTimes(1)
    expect(clientMocks.fetchValidation).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('连接稳定性偶发断连')
    expect(wrapper.text()).toContain('1')

    await wrapper.get('[data-testid="nav-compare"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('竞品对比概览')
    expect(wrapper.text()).toContain('noise-canceling')

    await wrapper.get('[data-testid="nav-trends"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('趋势图（续航）')
    expect(wrapper.text()).toContain('2026-W09')
    expect(wrapper.text()).toContain('负面率 40.0%')

    await wrapper.get('[data-testid="nav-wordcloud"]').trigger('click')
    await flushPromises()
    expect(clientMocks.fetchWordCloud).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('词云洞察（全部）')
    expect(wrapper.text()).toContain('演示模式词云数据')
    expect(wrapper.text()).toContain('续航')

    await wrapper.get('[data-testid="nav-actions"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('处理：续航体验波动')

    await wrapper.get('[data-testid="nav-validation"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('上线后负面率下降 11.00%，问题热度趋稳。')
  })

  it('shows degraded notices consistently for overview, issues, actions, and validation contracts', async () => {
    clientMocks.fetchIssues.mockResolvedValueOnce({
      state: 'degraded',
      items: [
        {
          issueId: 'iss-bluetooth-001',
          title: '连接稳定性偶发断连',
          aspect: 'bluetooth',
          priorityScore: 0.554,
          evidenceSummary: '近30天断连反馈上升且竞品差距扩大。',
        },
      ],
      notice: '问题列表暂时回退为受限结果，请稍后刷新。',
    })
    clientMocks.fetchActions.mockResolvedValueOnce({
      state: 'degraded',
      items: [],
      notice: '动作列表暂时只返回部分结果，可稍后重试刷新。',
    })
    clientMocks.fetchValidation.mockResolvedValueOnce({
      state: 'degraded',
      items: [],
      notice: '验证结果暂时回退为部分数据，请稍后刷新。',
    })

    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

    expect(wrapper.text()).toContain('问题列表暂时回退为受限结果，请稍后刷新。')

    await wrapper.get('[data-testid="nav-issues"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('问题列表暂时回退为受限结果，请稍后刷新。')
    expect(wrapper.text()).toContain('连接稳定性偶发断连')

    await wrapper.get('[data-testid="nav-actions"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('动作列表暂时只返回部分结果，可稍后重试刷新。')

    await wrapper.get('[data-testid="nav-validation"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('验证结果暂时回退为部分数据，请稍后刷新。')
  })

  it('renders explicit disabled, degraded, and runtime-unavailable semantics without module-specific fallback copy', async () => {
    clientMocks.fetchTrends.mockResolvedValueOnce({
      aspect: 'battery',
      points: [],
      state: 'degraded',
      notice: '趋势数据暂时只保留最近一次可用时间窗，请稍后重试。',
    })
    clientMocks.fetchWordCloud.mockResolvedValueOnce({
      productCode: 'demo-earphone',
      aspect: 'all',
      items: [],
      state: 'runtime-unavailable',
      notice: '词云运行态暂不可用，请稍后重试。',
    })

    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

    expect(wrapper.get('[data-testid="nav-hidden-showcase-chaos"]').text()).toContain('已禁用')
    expect(wrapper.get('[data-testid="nav-hidden-showcase-chaos"]').text()).toContain('需开启 VITE_SHOW_CHAOS_MODULE')

    await wrapper.get('[data-testid="nav-trends"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('趋势数据暂时只保留最近一次可用时间窗，请稍后重试。')

    await wrapper.get('[data-testid="nav-wordcloud"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('词云运行态暂不可用，请稍后重试。')
  })

  it('renders live showcase semantics and generates report preview from real-style payloads', async () => {
    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

    await wrapper.get('[data-testid="nav-showcase-pipeline"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('实时数据')
    expect(wrapper.text()).toContain('流水线视图来自真实 sync/analysis/materialization/action/validation 状态。')
    expect(wrapper.text()).toContain('MATERIALIZATION')

    await wrapper.get('[data-testid="nav-showcase-agent-arena"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('sync-lane')
    expect(wrapper.text()).toContain('analysis-lane')

    await wrapper.get('[data-testid="nav-showcase-explainability"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('受控数据')
    expect(wrapper.text()).toContain('negative_rate')

    await wrapper.get('[data-testid="nav-showcase-report-center"]').trigger('click')
    await flushPromises()
    await wrapper.get('.preview-button').trigger('click')
    await flushPromises()
    expect(clientMocks.previewShowcaseReport).toHaveBeenCalledWith('overview')
    expect(wrapper.text()).toContain('执行摘要：当前最高优先级问题是连接稳定性偶发断连。')
  })

  it('shows degraded and runtime-unavailable showcase states without falling back to placeholder copy', async () => {
    clientMocks.fetchShowcasePipeline.mockResolvedValueOnce({
      status: 'DEGRADED',
      implemented: true,
      note: '流水线当前部分降级，但仍保留最近一次真实阶段信号。',
      stages: [{ name: 'VALIDATION', state: 'DEGRADED', detail: 'validation snapshots are lagging behind actions' }],
    })
    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

    await wrapper.get('[data-testid="nav-showcase-pipeline"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('降级可用')
    expect(wrapper.text()).toContain('流水线当前部分降级，但仍保留最近一次真实阶段信号。')
    expect(wrapper.text()).toContain('VALIDATION')

    const chaosWrapper = mount(ShowcaseChaosPanel, {
      props: {
        data: {
          status: 'RUNTIME_UNAVAILABLE',
          implemented: true,
          note: '当前未记录可用于韧性视图的真实运行态。',
          drills: [{ scenario: 'materialization-runtime', state: 'UNAVAILABLE', detail: 'no materialized outputs yet' }],
        },
      },
    })

    expect(chaosWrapper.text()).toContain('运行态不可用')
    expect(chaosWrapper.text()).toContain('materialization-runtime')
    expect(chaosWrapper.text()).toContain('当前未记录可用于韧性视图的真实运行态。')
  })
})
