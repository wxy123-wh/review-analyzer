import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const clientMocks = vi.hoisted(() => ({
  bindProductTaxonomy: vi.fn(),
  cleanJsonlFile: vi.fn(),
  fetchAnalysisJob: vi.fn(),
  fetchImportedProducts: vi.fn(),
  fetchJsonlFiles: vi.fn(),
  fetchProductTaxonomy: vi.fn(),
  fetchReviewIntakeStatus: vi.fn(),
  fetchTaxonomies: vi.fn(),
  importJsonlFile: vi.fn(),
  saveProductTaxonomy: vi.fn(),
  startAnalysis: vi.fn(),
}))

vi.mock('../api/client', () => ({
  DEFAULT_PRODUCT_CODE: 'jd-100127936932',
  bindProductTaxonomy: clientMocks.bindProductTaxonomy,
  cleanJsonlFile: clientMocks.cleanJsonlFile,
  fetchAnalysisJob: clientMocks.fetchAnalysisJob,
  fetchImportedProducts: clientMocks.fetchImportedProducts,
  fetchJsonlFiles: clientMocks.fetchJsonlFiles,
  fetchProductTaxonomy: clientMocks.fetchProductTaxonomy,
  fetchReviewIntakeStatus: clientMocks.fetchReviewIntakeStatus,
  fetchTaxonomies: clientMocks.fetchTaxonomies,
  importJsonlFile: clientMocks.importJsonlFile,
  saveProductTaxonomy: clientMocks.saveProductTaxonomy,
  startAnalysis: clientMocks.startAnalysis,
}))

import ProductSetupPanel from '../components/ProductSetupPanel.vue'

function makeCleanResponse(productCode = 'jd-new-product') {
  return {
    jobId: 'manual-jsonl',
    importJobId: 'clean-jsonl-test-1',
    productCode,
    productName: '小米 Buds 5 Pro',
    provider: 'local-jsonl',
    platform: 'jd',
    rawOutputPath: `crawler/output/raw_reviews_${productCode}.jsonl`,
    cleanedOutputPath: `crawler/output/cleaned/cleaned_reviews_${productCode}.jsonl`,
    removedOutputPath: `crawler/output/cleaned/removed_reviews_${productCode}.jsonl`,
    cleaningSummaryPath: `crawler/output/cleaned/cleaning_summary_${productCode}.json`,
    receivedCount: 126,
    insertedReviewCount: 0,
    updatedReviewCount: 0,
    totalReviewCount: 0,
    cleaningSummary: {
      rawCount: 128,
      cleanedCount: 126,
      removedCount: 2,
      exactDuplicateCount: 1,
      placeholderContentCount: 1,
    },
    sampleReviews: [
      {
        sourceReviewId: 'rv-001',
        productName: '小米 Buds 5 Pro',
        content: '蓝牙连接偶尔断开，通话声音也不够清晰。',
        rating: '2',
      },
    ],
    analysisHandoffStatus: 'READY_FOR_ANALYSIS',
    analysisHandoffNote: 'JSONL 已清洗完成，请检查摘要和样本，再绑定 taxonomy 并导入数据库。',
  }
}

function makeImportResponse(productCode = 'jd-new-product') {
  return {
    ...makeCleanResponse(productCode),
    importJobId: 'import-jsonl-test-1',
    insertedReviewCount: 120,
    updatedReviewCount: 6,
    totalReviewCount: 126,
    analysisHandoffNote: 'JSONL 已导入数据库，可以启动分析。',
  }
}

function makeRecentReviews(count = 10) {
  return Array.from({ length: count }, (_, index) => ({
    sourceReviewId: `recent-${index + 1}`,
    productName: '小米 Buds 5 Pro',
    content: `最近评论 ${index + 1}：蓝牙连接和佩戴体验反馈。`,
    rating: `${index % 2 === 0 ? 2 : 5}`,
    reviewTime: `2026-06-${String(index + 1).padStart(2, '0')}T10:20:00Z`,
  }))
}

function makeIntakeStatus(productCode = 'jd-new-product', overrides: Record<string, unknown> = {}) {
  return {
    productCode,
    productName: '小米 Buds 5 Pro',
    rawOutputPath: `crawler/output/raw_reviews_${productCode}.jsonl`,
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
    ...overrides,
  }
}

async function mountSetupPanel() {
  const wrapper = mount(ProductSetupPanel)
  await flushPromises()
  await wrapper.get('[data-testid="setup-product-code"]').setValue('jd-new-product')
  await wrapper.get('[data-testid="setup-product-name"]').setValue('小米 Buds 5 Pro')
  await wrapper.get('[data-testid="setup-jsonl-path"]').setValue('crawler/output/raw_reviews_jd-new-product.jsonl')
  return wrapper
}

describe('ProductSetupPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clientMocks.fetchImportedProducts.mockResolvedValue([
      {
        productCode: 'jd-100127936932',
        productName: '小米 Xiaomi Buds 5',
        importedReviewCount: 746,
        analyzedReviewCount: 746,
        downstreamReady: true,
        taxonomyBound: true,
        latestAnalysisStatus: 'SUCCEEDED',
      },
      {
        productCode: 'jd-new-product',
        productName: '小米 Buds 5 Pro',
        importedReviewCount: 126,
        analyzedReviewCount: 80,
        downstreamReady: false,
        taxonomyBound: true,
        latestAnalysisStatus: 'RUNNING',
      },
    ])
    clientMocks.fetchJsonlFiles.mockResolvedValue([
      {
        path: 'crawler/output/raw_reviews_jd-100127936932.jsonl',
        fileName: 'raw_reviews_jd-100127936932.jsonl',
        productCode: 'jd-100127936932',
        productName: '小米 Buds 5 Pro',
        sizeBytes: 4096,
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
      notice: '测试模式下使用通用电商 UX 标签组合。',
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
      {
        productCode: 'jd-100127936932',
        category: 'bluetooth-headset',
        taxonomyId: 2,
        name: '蓝牙耳机 UX 标签',
        labels: [
          {
            id: 'headset-connection',
            uxPrimaryLabel: '产品硬件',
            uxSecondaryLabel: '连接与稳定性',
            enabled: true,
          },
        ],
        state: 'success',
      },
    ])
    clientMocks.saveProductTaxonomy.mockImplementation((payload) =>
      Promise.resolve({ ...payload, taxonomyId: payload.taxonomyId ?? 1, state: 'success', notice: '标签组合已保存。' }),
    )
    clientMocks.bindProductTaxonomy.mockImplementation((productCode, taxonomyId) =>
      Promise.resolve({
        productCode,
        category: taxonomyId === 2 ? 'bluetooth-headset' : 'general-product',
        taxonomyId,
        name: taxonomyId === 2 ? '蓝牙耳机 UX 标签' : '通用电商 UX 标签',
        labels: taxonomyId === 2
          ? [
              {
                id: 'headset-connection',
                uxPrimaryLabel: '产品硬件',
                uxSecondaryLabel: '连接与稳定性',
                enabled: true,
              },
            ]
          : [
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
    clientMocks.cleanJsonlFile.mockImplementation((payload) => Promise.resolve(makeCleanResponse(payload.productCode)))
    clientMocks.importJsonlFile.mockImplementation((payload) => Promise.resolve(makeImportResponse(payload.productCode)))
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
    clientMocks.fetchReviewIntakeStatus.mockImplementation((productCode) =>
      Promise.resolve(makeIntakeStatus(productCode)),
    )
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('cleans JSONL and shows summary without starting analysis', async () => {
    const wrapper = await mountSetupPanel()

    expect(wrapper.text()).toContain('数据接入')
    expect(wrapper.find('[data-testid="setup-product-url"]').exists()).toBe(false)
    expect((wrapper.get('[data-testid="setup-platform"]').element as HTMLSelectElement).value).toBe('jd')
    expect(wrapper.text()).toContain('已识别 1 个 raw JSONL 文件')
    expect(wrapper.text()).toContain('质量与性能')

    await wrapper.get('[data-testid="setup-clean-jsonl"]').trigger('click')
    await flushPromises()

    expect(clientMocks.cleanJsonlFile).toHaveBeenCalledWith({
      productCode: 'jd-new-product',
      productName: '小米 Buds 5 Pro',
      inputPath: 'crawler/output/raw_reviews_jd-new-product.jsonl',
      platform: 'jd',
    })
    expect(clientMocks.importJsonlFile).not.toHaveBeenCalled()
    expect(clientMocks.startAnalysis).not.toHaveBeenCalled()
    expect(wrapper.emitted('analysis-ready')).toBeUndefined()
    expect(wrapper.text()).toContain('cleaned_reviews_jd-new-product.jsonl')
    expect(wrapper.text()).toContain('removed_reviews_jd-new-product.jsonl')
    expect(wrapper.text()).toContain('cleaning_summary_jd-new-product.json')
    expect(wrapper.text()).toContain('JSONL 已清洗完成')
    expect(wrapper.text()).toContain('保留 126 条')
    expect(wrapper.text()).toContain('placeholder')
    expect(wrapper.text()).toContain('待导入数据库')
    expect(wrapper.text()).toContain('待启动')
  })

  it('restores persisted status and defaults the recent comment list to 10 rows', async () => {
    clientMocks.fetchJsonlFiles.mockResolvedValue([
      {
        path: 'crawler/output/raw_reviews_jd-new-product.jsonl',
        fileName: 'raw_reviews_jd-new-product.jsonl',
        productCode: 'jd-new-product',
        productName: '小米 Buds 5 Pro',
        sizeBytes: 4096,
        sampleReviews: [{ productName: '小米 Buds 5 Pro', content: '蓝牙连接偶尔断开，通话声音也不够清晰。' }],
      },
    ])
    clientMocks.fetchReviewIntakeStatus.mockImplementation((productCode) =>
      Promise.resolve(
        makeIntakeStatus(productCode, {
          cleanedJsonlExists: true,
          cleaningSummary: {
            rawCount: 128,
            cleanedCount: 126,
            removedCount: 2,
            exactDuplicateCount: 1,
            placeholderContentCount: 1,
          },
          taxonomyBound: true,
          taxonomyId: 1,
          taxonomyVersion: 1,
          importedReviewCount: 126,
          analyzedReviewCount: 80,
          downstreamReady: false,
          latestAnalysisJob: {
            jobId: 'analysis-running',
            productCode,
            status: 'RUNNING',
            totalReviewCount: 126,
            processedReviewCount: 80,
            progressPercent: 62,
            materializedReviewCount: 80,
            semanticLabelCount: 80,
            issueClusterCount: 2,
            downstreamReady: false,
            currentStage: 'LLM 已完成 80 / 126 条',
          },
          recentReviews: makeRecentReviews(12),
          stage: 'ANALYZING',
          notice: 'LLM 正在分析评论，可查看任务进度和最近评论列表。',
        }),
      ),
    )

    const wrapper = await mountSetupPanel()
    await flushPromises()

    expect(wrapper.text()).toContain('已导入 126 条')
    expect(wrapper.text()).toContain('已分析评论')
    expect(wrapper.text()).toContain('80')
    expect(wrapper.get('[data-testid="setup-analysis-progress"]').text()).toContain('LLM 已完成 80 / 126 条')
    expect(wrapper.get('[data-testid="setup-analysis-progress"]').text()).toContain('62%')
    expect(wrapper.findAll('[data-testid="setup-recent-reviews"] li')).toHaveLength(10)
    expect(wrapper.text()).toContain('10 / 10 条')
  })

  it('selects an imported product history item for dashboard display', async () => {
    const wrapper = await mountSetupPanel()

    await wrapper.get('[data-testid="setup-product-history"]').setValue('jd-100127936932')
    await wrapper.get('[data-testid="setup-show-product"]').trigger('click')
    await flushPromises()

    expect(clientMocks.fetchReviewIntakeStatus).toHaveBeenLastCalledWith(
      'jd-100127936932',
      'crawler/output/raw_reviews_jd-100127936932.jsonl',
    )
    expect(wrapper.emitted('product-selected')?.[0]?.[0]).toMatchObject({
      productCode: 'jd-100127936932',
      productName: '小米 Xiaomi Buds 5',
    })
    expect(wrapper.text()).toContain('已切换当前展示商品')
  })

  it('switches between setup steps inside the panel', async () => {
    const wrapper = await mountSetupPanel()

    expect(wrapper.get('[data-testid="setup-step-jsonl"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('.setup-step--jsonl').isVisible()).toBe(true)

    await wrapper.get('[data-testid="setup-step-taxonomy"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="setup-step-taxonomy"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('.setup-step--taxonomy').isVisible()).toBe(true)
    expect(wrapper.find('[data-testid="ux-label-sidebar"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="setup-secondary-0"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="setup-category"]').element.tagName).toBe('SELECT')

    await wrapper.get('[data-testid="setup-category"]').setValue('id:2')
    await flushPromises()

    expect(wrapper.text()).toContain('蓝牙耳机 UX 标签')
    expect(wrapper.text()).toContain('连接与稳定性')

    await wrapper.get('[data-testid="setup-step-status"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="setup-step-status"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('.setup-step--status').isVisible()).toBe(true)
    expect(wrapper.text()).toContain('任务状态')
  })

  it('starts analysis only from the analysis stage and emits refresh payload', async () => {
    const onAnalysisReady = vi.fn()
    const wrapper = mount(ProductSetupPanel, {
      props: {
        onAnalysisReady,
      },
    })
    await flushPromises()
    await wrapper.get('[data-testid="setup-product-code"]').setValue('jd-new-product')
    await wrapper.get('[data-testid="setup-product-name"]').setValue('小米 Buds 5 Pro')
    await wrapper.get('[data-testid="setup-jsonl-path"]').setValue('crawler/output/raw_reviews_jd-new-product.jsonl')

    await wrapper.get('[data-testid="setup-clean-jsonl"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="setup-start-analysis"]').trigger('click')
    await flushPromises()

    expect(clientMocks.bindProductTaxonomy).toHaveBeenLastCalledWith('jd-new-product', 1, 'general-product')
    expect(clientMocks.saveProductTaxonomy).not.toHaveBeenCalled()
    expect(clientMocks.importJsonlFile).toHaveBeenCalledWith({
      productCode: 'jd-new-product',
      productName: '小米 Buds 5 Pro',
      inputPath: 'crawler/output/cleaned/cleaned_reviews_jd-new-product.jsonl',
      platform: 'jd',
      replaceExisting: true,
    })
    expect(clientMocks.startAnalysis).toHaveBeenCalledWith('jd-new-product')
    expect(wrapper.text()).toContain('当前数据库共 126')
    expect(wrapper.text()).toContain('SUCCEEDED')
    expect(wrapper.text()).toContain('LLM 分析已完成')
    expect(onAnalysisReady).toHaveBeenCalledWith(expect.objectContaining({
      productCode: 'jd-new-product',
      productName: '小米 Buds 5 Pro',
      importResult: expect.objectContaining({
        productCode: 'jd-new-product',
        importJobId: 'import-jsonl-test-1',
      }),
      analysisJob: expect.objectContaining({
        productCode: 'jd-new-product',
        status: 'SUCCEEDED',
      }),
    }))
  })

  it('starts analysis from existing imported reviews without replacing cleaned JSONL', async () => {
    const onAnalysisReady = vi.fn()
    clientMocks.fetchJsonlFiles.mockResolvedValue([
      {
        path: 'crawler/output/raw_reviews_jd-new-product.jsonl',
        fileName: 'raw_reviews_jd-new-product.jsonl',
        productCode: 'jd-new-product',
        productName: '小米 Buds 5 Pro',
        sizeBytes: 4096,
        sampleReviews: [{ productName: '小米 Buds 5 Pro', content: '蓝牙连接偶尔断开，通话声音也不够清晰。' }],
      },
    ])
    clientMocks.fetchReviewIntakeStatus.mockImplementation((productCode) =>
      Promise.resolve(
        makeIntakeStatus(productCode, {
          cleanedJsonlExists: true,
          cleaningSummary: { rawCount: 128, cleanedCount: 126, removedCount: 2 },
          taxonomyBound: true,
          taxonomyId: 1,
          taxonomyVersion: 1,
          importedReviewCount: 126,
          recentReviews: makeRecentReviews(3),
          stage: 'IMPORTED',
          notice: '清洗评论已导入数据库，可以启动 LLM 分析。',
        }),
      ),
    )
    const wrapper = mount(ProductSetupPanel, {
      props: {
        onAnalysisReady,
      },
    })
    await flushPromises()
    await wrapper.get('[data-testid="setup-product-code"]').setValue('jd-new-product')
    await wrapper.get('[data-testid="setup-product-name"]').setValue('小米 Buds 5 Pro')
    await wrapper.get('[data-testid="setup-jsonl-path"]').setValue('crawler/output/raw_reviews_jd-new-product.jsonl')

    await wrapper.get('[data-testid="setup-start-analysis"]').trigger('click')
    await flushPromises()

    expect(clientMocks.importJsonlFile).not.toHaveBeenCalled()
    expect(clientMocks.startAnalysis).toHaveBeenCalledWith('jd-new-product')
    expect(wrapper.text()).toContain('当前数据库共 126')
    expect(onAnalysisReady).toHaveBeenCalledWith(expect.objectContaining({
      productCode: 'jd-new-product',
      importResult: expect.objectContaining({
        jobId: 'status-existing',
        totalReviewCount: 126,
      }),
    }))
  })

  it('polls a queued analysis job and shows progress feedback', async () => {
    vi.useFakeTimers()
    const onAnalysisReady = vi.fn()
    clientMocks.startAnalysis.mockImplementation((productCode) =>
      Promise.resolve({ jobId: 'analysis-test-queued', productCode, status: 'QUEUED' }),
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
    const wrapper = mount(ProductSetupPanel, {
      props: {
        onAnalysisReady,
      },
    })
    await flushPromises()
    await wrapper.get('[data-testid="setup-product-code"]').setValue('jd-new-product')
    await wrapper.get('[data-testid="setup-product-name"]').setValue('小米 Buds 5 Pro')
    await wrapper.get('[data-testid="setup-jsonl-path"]').setValue('crawler/output/raw_reviews_jd-new-product.jsonl')

    await wrapper.get('[data-testid="setup-clean-jsonl"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="setup-start-analysis"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="setup-analysis-progress"]').text()).toContain('任务已创建')
    expect(wrapper.text()).toContain('QUEUED')
    expect(clientMocks.fetchAnalysisJob).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(clientMocks.fetchAnalysisJob).toHaveBeenCalledWith('analysis-test-queued', 'jd-new-product')
    expect(wrapper.text()).toContain('SUCCEEDED')
    expect(wrapper.get('[data-testid="setup-analysis-progress"]').text()).toContain('100%')
    expect(onAnalysisReady).toHaveBeenCalledWith(expect.objectContaining({
      productCode: 'jd-new-product',
      analysisJob: expect.objectContaining({
        jobId: 'analysis-test-queued',
        status: 'SUCCEEDED',
      }),
    }))
  })

  it('emits refresh payload while a running analysis job has partial materialized results', async () => {
    vi.useFakeTimers()
    const onAnalysisReady = vi.fn()
    clientMocks.startAnalysis.mockImplementation((productCode) =>
      Promise.resolve({ jobId: 'analysis-test-running', productCode, status: 'QUEUED' }),
    )
    clientMocks.fetchAnalysisJob
      .mockImplementationOnce((jobId, productCode) =>
        Promise.resolve({
          jobId,
          productCode,
          status: 'RUNNING',
          totalReviewCount: 126,
          processedReviewCount: 10,
          progressPercent: 18,
          currentStage: 'LLM 已完成并写入 10 / 126 条',
          materializedReviewCount: 10,
          semanticLabelCount: 10,
          issueClusterCount: 1,
          downstreamReady: true,
        }),
      )
      .mockImplementationOnce((jobId, productCode) =>
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
    const wrapper = mount(ProductSetupPanel, {
      props: {
        onAnalysisReady,
      },
    })
    await flushPromises()
    await wrapper.get('[data-testid="setup-product-code"]').setValue('jd-new-product')
    await wrapper.get('[data-testid="setup-product-name"]').setValue('小米 Buds 5 Pro')
    await wrapper.get('[data-testid="setup-jsonl-path"]').setValue('crawler/output/raw_reviews_jd-new-product.jsonl')

    await wrapper.get('[data-testid="setup-clean-jsonl"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="setup-start-analysis"]').trigger('click')
    await flushPromises()

    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(wrapper.get('[data-testid="setup-analysis-progress"]').text()).toContain('写入 10 / 126 条')
    expect(onAnalysisReady).toHaveBeenCalledTimes(1)
    expect(onAnalysisReady).toHaveBeenLastCalledWith(expect.objectContaining({
      productCode: 'jd-new-product',
      analysisJob: expect.objectContaining({
        jobId: 'analysis-test-running',
        status: 'RUNNING',
        materializedReviewCount: 10,
      }),
    }))

    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(onAnalysisReady).toHaveBeenCalledTimes(2)
    expect(onAnalysisReady).toHaveBeenLastCalledWith(expect.objectContaining({
      productCode: 'jd-new-product',
      analysisJob: expect.objectContaining({
        jobId: 'analysis-test-running',
        status: 'SUCCEEDED',
        materializedReviewCount: 126,
      }),
    }))
  })

  it('distinguishes backend interface errors from timeout errors', async () => {
    const wrapper = await mountSetupPanel()

    clientMocks.cleanJsonlFile.mockRejectedValueOnce({ response: { status: 500, data: { message: 'clean failed' } } })
    await wrapper.get('[data-testid="setup-clean-jsonl"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('清洗 JSONL失败：后端接口返回 500：clean failed')

    clientMocks.cleanJsonlFile.mockRejectedValueOnce({ code: 'ECONNABORTED' })
    await wrapper.get('[data-testid="setup-clean-jsonl"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('清洗 JSONL超时：后端可能正在处理大文件或 LLM 请求')
  })
})
