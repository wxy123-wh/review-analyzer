import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import LoginGate from '../components/LoginGate.vue'

const clientMocks = vi.hoisted(() => ({
  bindProductTaxonomy: vi.fn(),
  cleanJsonlFile: vi.fn(),
  createUxChangeComparison: vi.fn(),
  fetchCompare: vi.fn(),
  fetchCrawlJob: vi.fn(),
  fetchAnalysisJob: vi.fn(),
  fetchIssues: vi.fn(),
  fetchJsonlFiles: vi.fn(),
  fetchPositiveInsights: vi.fn(),
  fetchProductTaxonomy: vi.fn(),
  fetchReviewIntakeStatus: vi.fn(),
  fetchTaxonomies: vi.fn(),
  fetchTrends: vi.fn(),
  fetchUxChangeComparisonDetail: vi.fn(),
  fetchUxChangeComparisons: vi.fn(),
  fetchWordCloud: vi.fn(),
  importCrawlJob: vi.fn(),
  importJsonlFile: vi.fn(),
  saveTaxonomyDefinition: vi.fn(),
  saveProductTaxonomy: vi.fn(),
  startAnalysis: vi.fn(),
  startCrawl: vi.fn(),
}))

vi.mock('../api/client', () => ({
  DEFAULT_PRODUCT_CODE: 'jd-100127936932',
  bindProductTaxonomy: clientMocks.bindProductTaxonomy,
  cleanJsonlFile: clientMocks.cleanJsonlFile,
  createUxChangeComparison: clientMocks.createUxChangeComparison,
  fetchCompare: clientMocks.fetchCompare,
  fetchCrawlJob: clientMocks.fetchCrawlJob,
  fetchAnalysisJob: clientMocks.fetchAnalysisJob,
  fetchIssues: clientMocks.fetchIssues,
  fetchJsonlFiles: clientMocks.fetchJsonlFiles,
  fetchPositiveInsights: clientMocks.fetchPositiveInsights,
  fetchProductTaxonomy: clientMocks.fetchProductTaxonomy,
  fetchReviewIntakeStatus: clientMocks.fetchReviewIntakeStatus,
  fetchTaxonomies: clientMocks.fetchTaxonomies,
  fetchTrends: clientMocks.fetchTrends,
  fetchUxChangeComparisonDetail: clientMocks.fetchUxChangeComparisonDetail,
  fetchUxChangeComparisons: clientMocks.fetchUxChangeComparisons,
  fetchWordCloud: clientMocks.fetchWordCloud,
  importCrawlJob: clientMocks.importCrawlJob,
  importJsonlFile: clientMocks.importJsonlFile,
  saveTaxonomyDefinition: clientMocks.saveTaxonomyDefinition,
  saveProductTaxonomy: clientMocks.saveProductTaxonomy,
  startAnalysis: clientMocks.startAnalysis,
  startCrawl: clientMocks.startCrawl,
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

function makeIntakeStatus(productCode = 'jd-100127936932', inputPath = '') {
  return {
    productCode,
    productName: productCode === 'jd-auto-refresh' ? '小米 Buds 5 Pro' : undefined,
    rawOutputPath: inputPath || `crawler/output/raw_reviews_${productCode}.jsonl`,
    rawJsonlExists: true,
    rawCount: 128,
    cleanedOutputPath: `crawler/output/cleaned/cleaned_reviews_${productCode}.jsonl`,
    cleanedJsonlExists: false,
    removedOutputPath: `crawler/output/cleaned/removed_reviews_${productCode}.jsonl`,
    cleaningSummaryPath: `crawler/output/cleaned/cleaning_summary_${productCode}.json`,
    cleaningSummary: {},
    taxonomyBound: false,
    importedReviewCount: 0,
    analyzedReviewCount: 0,
    downstreamReady: false,
    latestAnalysisJob: undefined,
    recentReviews: [],
    stage: 'RAW_READY',
    notice: '请先选择 raw JSONL 并执行清洗。',
  }
}

describe('App shell', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    clientMocks.fetchIssues.mockResolvedValue({
      state: 'success',
      items: [
        {
          issueId: 'iss-bluetooth-001',
          title: '连接稳定性偶发断连',
          aspect: 'bluetooth',
          uxPrimaryLabel: '产品体验',
          uxSecondaryLabel: '连接与稳定性',
          priorityScore: 0.554,
          evidenceSummary: '近30天断连反馈上升且竞品差距扩大。',
        },
      ],
    })
    clientMocks.fetchPositiveInsights.mockResolvedValue({
      state: 'success',
      items: [
        {
          sellingPointId: 'sp-comfort-test',
          aspect: 'comfort',
          uxPrimaryLabel: '产品体验',
          uxSecondaryLabel: '佩戴与人体工学',
          sellingPoint: '佩戴舒适',
          mentionCount: 12,
          positiveRate: 0.86,
          score: 0.78,
          evidence: ['戴了几个小时耳朵也不疼'],
        },
      ],
    })
    clientMocks.fetchCompare.mockResolvedValue({
      productCode: 'jd-100127936932',
      comparisonProductCode: 'jd-100127936933',
      state: 'success',
      items: [
        { aspect: 'bluetooth', uxSecondaryLabel: '连接与稳定性', ourScore: 0.78, competitorScore: 0.5, gap: 0.28 },
        { aspect: 'battery', uxSecondaryLabel: '电池与续航', ourScore: 0.22, competitorScore: 0.78, gap: -0.56 },
      ],
    })
    clientMocks.fetchTrends.mockResolvedValue({
      aspect: 'battery',
      uxSecondaryLabel: '电池与续航',
      points: [
        { period: '2026-W06', negativeRate: 0.31, mentionVolume: 75 },
        { period: '2026-W09', negativeRate: 0.4, mentionVolume: 105 },
      ],
      state: 'success',
    })
    clientMocks.fetchWordCloud.mockResolvedValue({
      productCode: 'jd-100127936932',
      aspect: 'all',
      uxSecondaryLabel: '全部',
      items: [
        { keyword: '续航', frequency: 42, weight: 0.92, sentimentTag: 'POSITIVE', partOfSpeech: '名词', wordType: '体验维度' },
        { keyword: '断连', frequency: 31, weight: 0.85, sentimentTag: 'NEGATIVE', partOfSpeech: '动词', wordType: '问题词' },
      ],
      notice: '评论关键词已按词频聚合。',
      state: 'success',
    })
    clientMocks.fetchJsonlFiles.mockResolvedValue([
      {
        path: 'crawler/output/raw_reviews_jd-100127936932.jsonl',
        fileName: 'raw_reviews_jd-100127936932.jsonl',
        productCode: 'jd-100127936932',
        productName: '小米 Buds 5 Pro',
        sizeBytes: 4096,
        lastModifiedAt: '2026-06-03T00:05:00Z',
        sampleReviews: [{ productName: '小米 Buds 5 Pro', content: '蓝牙连接偶尔断开，通话声音也不够清晰。' }],
      },
    ])
    clientMocks.fetchProductTaxonomy.mockResolvedValue({
      productCode: 'jd-100127936932',
      category: 'general-product',
      taxonomyId: 1,
      name: '通用电商 UX 标签',
      labels: [
        {
          id: 'quality-performance',
          uxPrimaryLabel: '产品体验',
          uxSecondaryLabel: '质量与性能',
          enabled: true,
        },
      ],
      state: 'success',
    })
    clientMocks.fetchTaxonomies.mockResolvedValue([
      {
        productCode: 'jd-100127936932',
        category: 'general-product',
        taxonomyId: 1,
        name: '通用电商 UX 标签',
        labels: [
          {
            id: 'quality-performance',
            uxPrimaryLabel: '产品体验',
            uxSecondaryLabel: '质量与性能',
            enabled: true,
          },
        ],
        state: 'success',
      },
    ])
    clientMocks.fetchReviewIntakeStatus.mockImplementation((productCode, inputPath) =>
      Promise.resolve(makeIntakeStatus(productCode, inputPath)),
    )
    clientMocks.saveProductTaxonomy.mockImplementation((payload) => Promise.resolve({ ...payload, state: 'success' }))
    clientMocks.saveTaxonomyDefinition.mockImplementation((payload) =>
      Promise.resolve({ ...payload, taxonomyId: payload.taxonomyId ?? 3, state: 'success', notice: 'taxonomy 已保存。' }),
    )
    clientMocks.bindProductTaxonomy.mockImplementation((productCode, taxonomyId) =>
      Promise.resolve({
        productCode,
        category: 'general-product',
        taxonomyId,
        name: '通用电商 UX 标签',
        labels: [
          {
            id: 'quality-performance',
            uxPrimaryLabel: '产品体验',
            uxSecondaryLabel: '质量与性能',
            enabled: true,
          },
        ],
        state: 'success',
        notice: 'taxonomy 已绑定到商品。',
      }),
    )
    clientMocks.startCrawl.mockResolvedValue({
      jobId: 'crawl-test-1',
      productCode: 'jd-100127936932',
      status: 'RUNNING',
      fetchedCount: 0,
      analysisHandoffStatus: 'CRAWL_RUNNING',
    })
    clientMocks.fetchCrawlJob.mockResolvedValue({
      jobId: 'crawl-test-1',
      productCode: 'jd-100127936932',
      status: 'SUCCEEDED',
      fetchedCount: 128,
      capturedPackets: 8,
      outputPath: 'crawler/output/raw_reviews_jd-100127936932.jsonl',
      analysisHandoffStatus: 'READY_FOR_ANALYSIS',
    })
    clientMocks.importCrawlJob.mockResolvedValue({
      jobId: 'crawl-test-1',
      importJobId: 'import-test-1',
      productCode: 'jd-100127936932',
      provider: 'local-jsonl',
      platform: 'jd',
      rawOutputPath: 'crawler/output/raw_reviews_jd-100127936932.jsonl',
      cleanedOutputPath: 'crawler/output/cleaned/cleaned_reviews_jd-100127936932.jsonl',
      removedOutputPath: 'crawler/output/cleaned/removed_reviews_jd-100127936932.jsonl',
      cleaningSummaryPath: 'crawler/output/cleaned/cleaning_summary_jd-100127936932.json',
      receivedCount: 126,
      insertedReviewCount: 120,
      updatedReviewCount: 6,
      totalReviewCount: 126,
      cleaningSummary: { rawCount: 128, cleanedCount: 126, removedCount: 2, exactDuplicateCount: 1 },
      sampleReviews: [{ content: '蓝牙连接偶尔断开，通话声音也不够清晰。', rating: '2' }],
      analysisHandoffStatus: 'READY_FOR_ANALYSIS',
      analysisHandoffNote: 'JSONL 已清洗并导入，可以启动分析。',
    })
    clientMocks.importJsonlFile.mockImplementation((payload) =>
      Promise.resolve({
        jobId: 'manual-jsonl',
        importJobId: 'import-jsonl-test-1',
        productCode: payload.productCode,
        productName: payload.productName,
        provider: 'local-jsonl',
        platform: payload.platform ?? 'jd',
        rawOutputPath: payload.inputPath,
        cleanedOutputPath: `crawler/output/cleaned/cleaned_reviews_${payload.productCode}.jsonl`,
        removedOutputPath: `crawler/output/cleaned/removed_reviews_${payload.productCode}.jsonl`,
        cleaningSummaryPath: `crawler/output/cleaned/cleaning_summary_${payload.productCode}.json`,
        receivedCount: 126,
        insertedReviewCount: 120,
        updatedReviewCount: 6,
        totalReviewCount: 126,
        cleaningSummary: { rawCount: 128, cleanedCount: 126, removedCount: 2, exactDuplicateCount: 1 },
        sampleReviews: [{ productName: payload.productName, content: '蓝牙连接偶尔断开，通话声音也不够清晰。', rating: '2' }],
        analysisHandoffStatus: 'READY_FOR_ANALYSIS',
        analysisHandoffNote: 'JSONL 已清洗并导入，可以启动分析。',
      }),
    )
    clientMocks.cleanJsonlFile.mockImplementation((payload) =>
      Promise.resolve({
        jobId: 'manual-jsonl',
        importJobId: 'clean-jsonl-test-1',
        productCode: payload.productCode,
        productName: payload.productName,
        provider: 'local-jsonl',
        platform: payload.platform ?? 'jd',
        rawOutputPath: payload.inputPath,
        cleanedOutputPath: `crawler/output/cleaned/cleaned_reviews_${payload.productCode}.jsonl`,
        removedOutputPath: `crawler/output/cleaned/removed_reviews_${payload.productCode}.jsonl`,
        cleaningSummaryPath: `crawler/output/cleaned/cleaning_summary_${payload.productCode}.json`,
        receivedCount: 126,
        insertedReviewCount: 0,
        updatedReviewCount: 0,
        totalReviewCount: 0,
        cleaningSummary: { rawCount: 128, cleanedCount: 126, removedCount: 2, exactDuplicateCount: 1 },
        sampleReviews: [{ productName: payload.productName, content: '蓝牙连接偶尔断开，通话声音也不够清晰。', rating: '2' }],
        analysisHandoffStatus: 'READY_FOR_ANALYSIS',
        analysisHandoffNote: 'JSONL 已清洗完成，请检查摘要和样本，再绑定 taxonomy 并导入数据库。',
      }),
    )
    clientMocks.startAnalysis.mockImplementation((productCode) =>
      Promise.resolve({
        jobId: 'analysis-test-1',
        productCode,
        status: 'SUCCEEDED',
        materializedReviewCount: 126,
        semanticLabelCount: 126,
        issueClusterCount: 2,
        downstreamReady: true,
      }),
    )
    clientMocks.fetchAnalysisJob.mockImplementation((jobId, productCode) =>
      Promise.resolve({
        jobId,
        productCode,
        status: 'SUCCEEDED',
        finishedAt: '2026-06-03T00:06:12Z',
        materializedReviewCount: 126,
        semanticLabelCount: 126,
        issueClusterCount: 2,
        downstreamReady: true,
      }),
    )
    clientMocks.fetchUxChangeComparisons.mockResolvedValue({
      state: 'success',
      items: [
        {
          id: 'ux-change-1',
          productCode: 'jd-100127936932',
          changeDate: '2026-06-03',
          windowPreset: 'ONE_MONTH',
          state: 'success',
          summary: '连接与稳定性负面率下降。',
          items: [
            {
              uxPrimaryLabel: '产品体验',
              uxSecondaryLabel: '连接与稳定性',
              beforeMentionCount: 42,
              afterMentionCount: 38,
              beforeNegativeRate: 0.48,
              afterNegativeRate: 0.31,
              improvementRate: 0.17,
            },
          ],
        },
      ],
    })
    clientMocks.fetchUxChangeComparisonDetail.mockImplementation((id) =>
      Promise.resolve({
        id,
        productCode: 'jd-100127936932',
        changeDate: '2026-06-03',
        windowPreset: 'ONE_MONTH',
        state: 'success',
        summary: '详情已加载。',
        items: [],
      }),
    )
    clientMocks.createUxChangeComparison.mockResolvedValue({
      id: 'ux-change-new',
      productCode: 'jd-100127936932',
      changeDate: '2026-06-05',
      windowPreset: 'TWO_WEEKS',
      state: 'success',
      summary: '新时间点已计算。',
      items: [
        {
          uxSecondaryLabel: '电池与续航',
          beforeMentionCount: 20,
          afterMentionCount: 18,
          beforeNegativeRate: 0.4,
          afterNegativeRate: 0.32,
          improvementRate: 0.08,
        },
      ],
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    document.documentElement.removeAttribute('data-motion')
    vi.clearAllMocks()
  })

  it('renders the auth gate before entering the dashboard', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('内部访问')
    expect(wrapper.text()).not.toContain('使用访问凭据进入工作台')
    expect(wrapper.text()).not.toContain('请输入访问凭据')
    expect(wrapper.get('[data-testid="login-submit"]').text()).toBe('登录')
    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(false)

    await enterDashboard(wrapper)

    expect(wrapper.find('[data-testid="login-gate"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="narrow-sidebar"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('当前用户：内部分析员')
    expect(wrapper.text()).not.toContain('评论 VOC 分析工作台')
    expect(wrapper.text()).not.toContain('Review VOC')
  })

  it('preserves login-character interactions, password toggling, loading state, and form error messaging', async () => {
    vi.useRealTimers()

    const wrapper = mount(LoginGate, {
      props: {
        expectedUsername: 'internal-review',
        expectedPassword: 'internal-pass',
        displayName: '内部评审',
      },
    })
    const monsters = wrapper.findAll('[data-testid="login-monster"]')

    expect(monsters.map((item) => item.attributes('data-monster-id'))).toEqual(['purple', 'black', 'orange', 'yellow'])

    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 480, clientY: 320 }))
    await wait(50)

    await monsters[0].trigger('click')
    await wait(260)
    expect(monsters[0].classes()).toContain('jumping')
    await wait(420)
    expect(monsters[0].classes()).not.toContain('jumping')

    const passwordInput = wrapper.get('[data-testid="login-password"]')
    const eyeToggle = wrapper.get('.eye-toggle')

    expect(passwordInput.attributes('type')).toBe('password')
    await eyeToggle.trigger('click')
    expect(wrapper.get('[data-testid="login-password"]').attributes('type')).toBe('text')

    await wrapper.get('[data-testid="login-username"]').setValue('bad-user')
    await wrapper.get('[data-testid="login-password"]').setValue('bad-pass')
    await wrapper.get('.form').trigger('submit')

    expect(wrapper.get('[data-testid="login-submit"]').text()).toBe('登录中...')
    await wait(850)
    await flushPromises()

    expect(wrapper.get('[data-testid="login-error"]').text()).toContain('账号或密码有误')
  })

  it('keeps only the requested business modules in the sidebar', async () => {
    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

    const navIds = wrapper
      .findAll('.nav-item')
      .map((item) => item.attributes('data-testid'))
      .filter(Boolean)

    expect(navIds).toEqual([
      'nav-product-setup',
      'nav-taxonomy',
      'nav-issues',
      'nav-positive-insights',
      'nav-compare',
      'nav-trends',
      'nav-wordcloud',
      'nav-ux-change-comparisons',
    ])
    expect(navIds).toHaveLength(8)
    expect(wrapper.text()).toContain('数据接入')
    expect(wrapper.text()).toContain('taxonomy')
    expect(wrapper.text()).not.toContain('采集配置')

    await wrapper.get('[data-testid="nav-taxonomy"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="taxonomy-manager"]').isVisible()).toBe(true)
  })

  it('loads core data and runs compare only after two product codes are submitted', async () => {
    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

    expect(clientMocks.fetchIssues).toHaveBeenCalledTimes(1)
    expect(clientMocks.fetchIssues).toHaveBeenCalledWith('jd-100127936932')
    expect(clientMocks.fetchPositiveInsights).toHaveBeenCalledTimes(1)
    expect(clientMocks.fetchPositiveInsights).toHaveBeenCalledWith('jd-100127936932')
    expect(clientMocks.fetchTrends).toHaveBeenCalledTimes(1)
    expect(clientMocks.fetchTrends).toHaveBeenCalledWith('jd-100127936932', 'general', '质量与性能')
    expect(clientMocks.fetchCompare).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="nav-issues"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('连接稳定性偶发断连')

    await wrapper.get('[data-testid="nav-positive-insights"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('佩戴舒适')

    await wrapper.get('[data-testid="nav-compare"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('输入两个商品编号后查看对比结果。')

    await wrapper.get('[data-testid="compare-product-code"]').setValue('jd-100127936932')
    await wrapper.get('[data-testid="compare-comparison-product-code"]').setValue('jd-100127936933')
    await wrapper.get('[data-testid="compare-form"]').trigger('submit')
    await flushPromises()

    expect(clientMocks.fetchCompare).toHaveBeenCalledWith('jd-100127936932', 'jd-100127936933')
    expect(wrapper.text()).toContain('竞品对比')
    expect(wrapper.text()).toContain('电池与续航')
  })

  it('refreshes stale empty issue data when the module is opened again', async () => {
    clientMocks.fetchIssues.mockResolvedValueOnce({
      state: 'empty',
      items: [],
      notice: '真实评论已导入数据库，但 LLM 分析结果尚未写入下游表。',
    })

    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

    expect(clientMocks.fetchIssues).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="nav-issues"]').trigger('click')
    await flushPromises()

    expect(clientMocks.fetchIssues).toHaveBeenCalledTimes(2)
    expect(clientMocks.fetchIssues).toHaveBeenLastCalledWith('jd-100127936932')
    expect(wrapper.text()).toContain('连接稳定性偶发断连')
  })

  it('auto-starts analysis after JSONL import and refreshes downstream data with that product code', async () => {
    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

    expect(clientMocks.fetchWordCloud).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="setup-product-code"]').setValue('jd-auto-refresh')
    await wrapper.get('[data-testid="setup-product-name"]').setValue('小米 Buds 5 Pro')
    await wrapper.get('[data-testid="setup-jsonl-path"]').setValue('crawler/output/raw_reviews_jd-auto-refresh.jsonl')
    await wrapper.findAll('button').find((button) => button.text() === '启动 LLM 分析')!.trigger('click')
    await flushPromises()
    await flushPromises()

    expect(clientMocks.importJsonlFile).toHaveBeenCalledWith({
      productCode: 'jd-auto-refresh',
      productName: '小米 Buds 5 Pro',
      inputPath: 'crawler/output/raw_reviews_jd-auto-refresh.jsonl',
      platform: 'jd',
    })
    expect(clientMocks.bindProductTaxonomy).toHaveBeenLastCalledWith('jd-auto-refresh', 1, 'general-product')
    expect(clientMocks.saveProductTaxonomy).not.toHaveBeenCalled()
    expect(clientMocks.startAnalysis).toHaveBeenCalledWith('jd-auto-refresh')
    expect(clientMocks.fetchIssues).toHaveBeenLastCalledWith('jd-auto-refresh')
    expect(clientMocks.fetchPositiveInsights).toHaveBeenLastCalledWith('jd-auto-refresh')
    expect(clientMocks.fetchTrends).toHaveBeenLastCalledWith('jd-auto-refresh', 'general', '质量与性能')
    expect(clientMocks.fetchWordCloud).toHaveBeenLastCalledWith('jd-auto-refresh', 'all')

    await wrapper.get('[data-testid="nav-compare"]').trigger('click')
    await flushPromises()
    expect((wrapper.get('[data-testid="compare-product-code"]').element as HTMLInputElement).value).toBe('jd-auto-refresh')
    expect(wrapper.text()).toContain('小米 Buds 5 Pro')

    await wrapper.get('[data-testid="nav-wordcloud"]').trigger('click')
    await flushPromises()
    expect(clientMocks.fetchWordCloud).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('续航')
    expect(wrapper.text()).toContain('断连')
  })

  it('shows compare taxonomy mismatch and lazy-loads word cloud and ux change comparisons', async () => {
    clientMocks.fetchCompare.mockResolvedValueOnce({
      productCode: 'jd-a',
      comparisonProductCode: 'jd-b',
      state: 'taxonomy-mismatch',
      items: [],
      notice: '两个商品绑定的 taxonomy 不一致。',
    })

    const wrapper = mount(App)
    await flushPromises()
    await enterDashboard(wrapper)

    await wrapper.get('[data-testid="nav-compare"]').trigger('click')
    await wrapper.get('[data-testid="compare-product-code"]').setValue('jd-a')
    await wrapper.get('[data-testid="compare-comparison-product-code"]').setValue('jd-b')
    await wrapper.get('[data-testid="compare-form"]').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('两个商品绑定的 taxonomy 不一致。')

    await wrapper.get('[data-testid="nav-wordcloud"]').trigger('click')
    await flushPromises()
    expect(clientMocks.fetchWordCloud).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('续航')
    expect(wrapper.text()).toContain('断连')

    await wrapper.get('[data-testid="nav-ux-change-comparisons"]').trigger('click')
    await flushPromises()
    expect(clientMocks.fetchUxChangeComparisons).toHaveBeenCalledWith('jd-100127936932')
    expect(wrapper.text()).toContain('前后对比')
    expect(wrapper.text()).toContain('连接与稳定性负面率下降。')

    await wrapper.get('[data-testid="ux-change-date"]').setValue('2026-06-05')
    await wrapper.get('[data-testid="ux-change-window-preset"]').setValue('TWO_WEEKS')
    await wrapper.get('[data-testid="ux-change-form"]').trigger('submit')
    await flushPromises()

    expect(clientMocks.createUxChangeComparison).toHaveBeenCalledWith({
      productCode: 'jd-100127936932',
      changeDate: '2026-06-05',
      windowPreset: 'TWO_WEEKS',
    })
    expect(wrapper.text()).toContain('新时间点已计算。')
    expect(wrapper.text()).toContain('电池与续航')
  })
})
