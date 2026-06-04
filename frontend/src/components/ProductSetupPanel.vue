<template>
  <section class="panel">
    <header class="head">
      <div class="title-block">
        <h3>数据接入</h3>
        <p class="support">清洗 JSONL、绑定标签、导入数据库并启动 LLM 分析。</p>
      </div>
    </header>

    <div class="form-grid">
      <label class="field">
        <span>商品编号</span>
        <input v-model.trim="productCode" data-testid="setup-product-code" type="text" placeholder="jd-100127936932" />
      </label>
      <label class="field">
        <span>商品名称</span>
        <input v-model.trim="productName" data-testid="setup-product-name" type="text" placeholder="小米 Buds 5 Pro" />
      </label>
      <label class="field">
        <span>平台</span>
        <select v-model="platform" data-testid="setup-platform">
          <option value="jd">京东</option>
          <option value="taobao">淘宝</option>
          <option value="other">其他</option>
        </select>
      </label>
      <label class="field">
        <span>商品品类</span>
        <select v-model="selectedTaxonomyKey" data-testid="setup-category" :disabled="taxonomyOptions.length === 0" @change="applySelectedTaxonomy">
          <option v-if="taxonomyOptions.length === 0" value="">读取 taxonomy 后选择</option>
          <option v-for="option in taxonomyOptions" :key="taxonomyOptionKey(option)" :value="taxonomyOptionKey(option)">
            {{ taxonomyOptionLabel(option) }}
          </option>
        </select>
      </label>
    </div>

    <div class="toolbar">
      <button type="button" class="secondary-btn" :disabled="busy" @click="loadTaxonomy">读取标签</button>
      <button type="button" class="secondary-btn" :disabled="busy || statusLoading" @click="refreshIntakeStatus(true)">
        {{ statusLoading ? '刷新中' : '刷新状态' }}
      </button>
      <button type="button" class="primary-btn" data-testid="setup-clean-jsonl" :disabled="busy || !canCleanJsonl" @click="cleanLocalJsonl">清洗 JSONL</button>
      <button type="button" class="primary-btn" :disabled="busy || labels.length === 0" @click="saveTaxonomy">绑定 taxonomy</button>
      <button type="button" class="secondary-btn" data-testid="setup-import-jsonl" :disabled="busy || !canImportJsonl" @click="importLocalJsonl">导入数据库</button>
      <button type="button" class="primary-btn" data-testid="setup-start-analysis" :disabled="busy || !canStartAnalysis" @click="startProductAnalysis">启动 LLM 分析</button>
    </div>

    <div class="panel-body">
      <div class="step-top">
        <p v-if="message" class="notice" :class="{ 'notice--error': messageTone === 'error' }">{{ message }}</p>
        <nav class="step-nav" aria-label="数据接入步骤">
          <button
            v-for="step in setupSteps"
            :key="step.id"
            type="button"
            class="step-tab"
            :class="{ 'step-tab--active': activeSetupStep === step.id }"
            :aria-pressed="activeSetupStep === step.id"
            :data-testid="`setup-step-${step.id}`"
            @click="activeSetupStep = step.id"
          >
            <strong>{{ step.label }}</strong>
            <span>{{ step.description }}</span>
          </button>
        </nav>
      </div>

      <div class="setup-step-shell">
        <section v-show="activeSetupStep === 'jsonl'" class="setup-step setup-step--jsonl">
          <article class="jsonl-controls">
            <label class="field">
              <span>已识别 JSONL</span>
              <div class="jsonl-picker">
                <select v-model="selectedJsonlPath" data-testid="setup-jsonl-candidate" :disabled="jsonlLoading || jsonlCandidates.length === 0" @change="applySelectedJsonlPath">
                  <option value="">{{ jsonlCandidates.length > 0 ? '选择已识别文件' : '未识别到 raw JSONL' }}</option>
                  <option v-for="candidate in jsonlCandidates" :key="candidate.path" :value="candidate.path">
                    {{ candidateLabel(candidate) }}
                  </option>
                </select>
                <button type="button" class="secondary-btn compact" :disabled="jsonlLoading" @click="loadJsonlFiles">
                  {{ jsonlLoading ? '识别中' : '刷新文件' }}
                </button>
              </div>
            </label>
            <label class="field">
              <span>raw JSONL 路径</span>
              <input v-model.trim="rawJsonlPath" data-testid="setup-jsonl-path" type="text" placeholder="crawler/output/raw_reviews_jd-100127936932.jsonl" />
            </label>
          </article>

          <article class="jsonl-panel">
            <div class="section-head">
              <h4>JSONL 文件</h4>
              <span class="file-state">{{ fileState }}</span>
            </div>

            <div class="jsonl-details scroll-region">
              <dl class="file-list">
                <div>
                  <dt>raw_reviews</dt>
                  <dd>{{ displayedRawOutputPath || '请填写 raw JSONL 路径' }}</dd>
                </div>
                <div>
                  <dt>cleaned_reviews</dt>
                  <dd>{{ displayedCleanedOutputPath || '清洗 JSONL 后生成' }}</dd>
                </div>
                <div>
                  <dt>removed_reviews</dt>
                  <dd>{{ displayedRemovedOutputPath || '清洗 JSONL 后生成' }}</dd>
                </div>
                <div>
                  <dt>cleaning_summary</dt>
                  <dd>{{ displayedCleaningSummaryPath || '清洗 JSONL 后生成' }}</dd>
                </div>
              </dl>

              <div v-if="summaryRows.length > 0" class="summary-grid">
                <span v-for="item in summaryRows" :key="item.label">
                  <strong>{{ item.value }}</strong>
                  <small>{{ item.label }}</small>
                </span>
              </div>
            </div>
          </article>
        </section>

        <section v-show="activeSetupStep === 'taxonomy'" class="setup-step setup-step--taxonomy">
          <div class="taxonomy-main">
            <div class="taxonomy-summary">
              <div>
                <h4>{{ selectedTaxonomyName }}</h4>
                <p>{{ selectedTaxonomyMeta }}</p>
              </div>
              <button
                type="button"
                class="primary-btn"
                data-testid="setup-bind-taxonomy"
                :disabled="busy || !activeTaxonomyId"
                @click="saveTaxonomy"
              >
                绑定当前 taxonomy
              </button>
            </div>
            <UxTaxonomyTree :labels="labels" readonly />
          </div>
        </section>

        <section v-show="activeSetupStep === 'analysis'" class="setup-step setup-step--analysis">
          <article class="analysis-panel">
            <div class="section-head">
              <h4>导入与 LLM 分析</h4>
              <span class="file-state">{{ analysisStageText }}</span>
            </div>

            <div class="analysis-scroll scroll-region">
              <div class="stage-grid">
                <span>
                  <strong>{{ cleanStageText }}</strong>
                  <small>清洗阶段</small>
                </span>
                <span>
                  <strong>{{ taxonomyStageText }}</strong>
                  <small>taxonomy 阶段</small>
                </span>
                <span>
                  <strong>{{ importStageText }}</strong>
                  <small>入库阶段</small>
                </span>
                <span>
                  <strong>{{ analysisStageText }}</strong>
                  <small>LLM 分析</small>
                </span>
              </div>

              <div v-if="currentAnalysisJob" class="progress-block" data-testid="setup-analysis-progress">
                <div class="progress-meta">
                  <span>{{ analysisProgressText }}</span>
                  <strong>{{ analysisProgressPercent }}%</strong>
                </div>
                <div class="progress-track" aria-label="LLM 分析进度">
                  <span class="progress-fill" :class="{ 'progress-fill--failed': currentAnalysisJob.status === 'FAILED' }" :style="{ width: `${analysisProgressPercent}%` }"></span>
                </div>
              </div>
              <p v-if="handoffNote" class="handoff">{{ handoffNote }}</p>
              <p v-if="currentAnalysisJob?.errorMessage" class="handoff handoff--error">{{ currentAnalysisJob.errorMessage }}</p>
            </div>
          </article>
        </section>

        <section v-show="activeSetupStep === 'status'" class="setup-step setup-step--status">
          <aside class="status-panel">
            <h4>任务状态</h4>
            <div class="status-scroll scroll-region">
              <dl>
                <div>
                  <dt>商品名称</dt>
                  <dd>{{ resolvedProductName }}</dd>
                </div>
                <div>
                  <dt>商品编号</dt>
                  <dd>{{ intakeStatus?.productCode || taxonomy?.productCode || productCode || '未填写' }}</dd>
                </div>
                <div>
                  <dt>标签数量</dt>
                  <dd>{{ enabledLabelCount }} / {{ labels.length }}</dd>
                </div>
                <div>
                  <dt>taxonomyId</dt>
                  <dd>{{ taxonomy?.taxonomyId ?? intakeStatus?.taxonomyId ?? '待绑定' }}</dd>
                </div>
                <div>
                  <dt>数据来源</dt>
                  <dd>本地 JSONL</dd>
                </div>
                <div>
                  <dt>raw JSONL</dt>
                  <dd>{{ displayedRawOutputPath || '未填写' }}</dd>
                </div>
                <div>
                  <dt>清洗阶段</dt>
                  <dd>{{ cleanStageText }}</dd>
                </div>
                <div>
                  <dt>taxonomy 阶段</dt>
                  <dd>{{ taxonomyStageText }}</dd>
                </div>
                <div>
                  <dt>入库阶段</dt>
                  <dd>{{ importStageText }}</dd>
                </div>
                <div>
                  <dt>数据库评论</dt>
                  <dd>{{ importedReviewCount }}</dd>
                </div>
                <div>
                  <dt>已分析评论</dt>
                  <dd>{{ analyzedReviewCount }}</dd>
                </div>
                <div>
                  <dt>LLM 分析</dt>
                  <dd>{{ analysisStageText }}</dd>
                </div>
              </dl>
            </div>
          </aside>

          <article class="recent-reviews">
            <div class="section-head section-head--compact">
              <h4>最近评论</h4>
              <span class="file-state">{{ visibleSamples.length }} / 10 条</span>
            </div>
            <div class="recent-scroll scroll-region">
              <ul v-if="visibleSamples.length > 0" class="sample-list" data-testid="setup-recent-reviews">
                <li v-for="sample in visibleSamples" :key="sample.sourceReviewId || sample.content">
                  <span>{{ sample.rating ? `${sample.rating}星` : '无评分' }}</span>
                  <p>{{ sample.content }}</p>
                </li>
              </ul>
              <p v-else class="empty-reviews">暂无可展示评论。</p>
            </div>
          </article>
        </section>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import {
  DEFAULT_PRODUCT_CODE,
  bindProductTaxonomy,
  cleanJsonlFile,
  fetchAnalysisJob,
  fetchJsonlFiles,
  fetchProductTaxonomy,
  fetchReviewIntakeStatus,
  fetchTaxonomies,
  importJsonlFile,
  startAnalysis,
} from '../api/client'
import UxTaxonomyTree from './UxTaxonomyTree.vue'
import type {
  AnalysisJobResponse,
  CrawlImportResponse,
  ProductAnalysisReadyPayload,
  ProductTaxonomyResponse,
  ReviewIntakeStatusResponse,
  JsonlFileImportPayload,
  JsonlFileCandidate,
  UxLabelOption,
} from '../types/domain'

const emit = defineEmits<{
  (event: 'analysis-ready', payload: ProductAnalysisReadyPayload): void
}>()

const productCode = ref(DEFAULT_PRODUCT_CODE)
const productName = ref('')
const platform = ref('jd')
const category = ref('general-product')
const rawJsonlPath = ref(defaultRawJsonlPath(DEFAULT_PRODUCT_CODE))
const jsonlCandidates = ref<JsonlFileCandidate[]>([])
const selectedJsonlPath = ref('')
const taxonomyOptions = ref<ProductTaxonomyResponse[]>([])
const selectedTaxonomyKey = ref('')
const labels = ref<UxLabelOption[]>([])
const taxonomy = ref<ProductTaxonomyResponse | null>(null)
const cleanResult = ref<CrawlImportResponse | null>(null)
const importResult = ref<CrawlImportResponse | null>(null)
const analysisJob = ref<AnalysisJobResponse | null>(null)
const intakeStatus = ref<ReviewIntakeStatusResponse | null>(null)
const analysisPollCount = ref(0)
const lastAnalysisReadyMaterializedCount = ref(0)
const busy = ref(false)
const jsonlLoading = ref(false)
const statusLoading = ref(false)
const message = ref('')
const messageTone = ref<'info' | 'error'>('info')

const setupSteps = [
  { id: 'jsonl', label: 'JSONL', description: '文件与清洗' },
  { id: 'taxonomy', label: 'taxonomy', description: '选择绑定' },
  { id: 'analysis', label: '导入分析', description: '入库与 LLM' },
  { id: 'status', label: '状态评论', description: '台账与样本' },
] as const
type SetupStepId = (typeof setupSteps)[number]['id']
const activeSetupStep = ref<SetupStepId>('jsonl')

const FINAL_ANALYSIS_STATUSES = new Set(['SUCCEEDED', 'FAILED'])
const ANALYSIS_POLL_INTERVAL_MS = 1000
const MAX_ANALYSIS_POLLS = 300

const enabledLabelCount = computed(() => labels.value.filter((label) => label.enabled).length)
const latestFileResult = computed(() => importResult.value ?? cleanResult.value)
const currentAnalysisJob = computed(() => analysisJob.value ?? intakeStatus.value?.latestAnalysisJob ?? null)
const currentCleaningSummary = computed(() => {
  const statusSummary = intakeStatus.value?.cleaningSummary
  if (statusSummary && Object.keys(statusSummary).length > 0) {
    return statusSummary
  }
  return latestFileResult.value?.cleaningSummary ?? {}
})
const canCleanJsonl = computed(() => productCode.value.length > 0 && rawJsonlPath.value.length > 0)
const canImportJsonl = computed(() => productCode.value.length > 0 && rawJsonlPath.value.length > 0)
const canStartAnalysis = computed(() => productCode.value.length > 0)
const visibleSamples = computed(() => {
  const recentReviews = intakeStatus.value?.recentReviews ?? []
  if (recentReviews.length > 0) {
    return recentReviews.slice(0, 10)
  }
  return (latestFileResult.value?.sampleReviews ?? []).slice(0, 10)
})
const resolvedProductName = computed(() => {
  const statusName = intakeStatus.value?.productName?.trim()
  const importedName = latestFileResult.value?.productName?.trim()
  const typedName = productName.value.trim()
  const sampleName = visibleSamples.value.find((sample) => sample.productName?.trim())?.productName?.trim()
  return statusName || importedName || typedName || sampleName || '待识别'
})
const handoffNote = computed(() =>
  currentAnalysisJob.value
    ? 'LLM 分析已启动，成功后会刷新问题、趋势、词云和卖点。'
    : intakeStatus.value?.notice || latestFileResult.value?.analysisHandoffNote,
)
const cleanStageText = computed(() => {
  const cleaned = numberSummary(currentCleaningSummary.value.cleanedCount)
  if (intakeStatus.value?.cleanedJsonlExists || cleanResult.value) {
    return `已清洗 ${cleaned} 条`
  }
  return '待清洗'
})
const taxonomyStageText = computed(() => {
  const taxonomyId = taxonomy.value?.taxonomyId ?? intakeStatus.value?.taxonomyId
  return taxonomyId ? `已绑定 ${taxonomyId}` : '待绑定'
})
const importStageText = computed(() => {
  const importedCount = intakeStatus.value?.importedReviewCount ?? 0
  if (importedCount > 0 && !importResult.value) {
    return `已导入 ${importedCount} 条`
  }
  if (!importResult.value) {
    return cleanResult.value ? '待导入数据库' : '待清洗后导入'
  }
  return `已导入 ${importResult.value.totalReviewCount} 条`
})
const importedReviewCount = computed(() => importResult.value?.totalReviewCount ?? intakeStatus.value?.importedReviewCount ?? 0)
const analyzedReviewCount = computed(() => intakeStatus.value?.analyzedReviewCount ?? 0)
const analysisStageText = computed(() => {
  if (currentAnalysisJob.value) {
    return currentAnalysisJob.value.status
  }
  return analyzedReviewCount.value > 0 ? `已分析 ${analyzedReviewCount.value} 条` : '待启动'
})
const analysisProgressPercent = computed(() => {
  const job = currentAnalysisJob.value
  if (typeof job?.progressPercent === 'number') {
    return clampPercent(job.progressPercent)
  }
  const status = job?.status
  if (status === 'SUCCEEDED') {
    return 100
  }
  if (status === 'FAILED') {
    return 100
  }
  if (status === 'RUNNING') {
    return Math.min(92, 38 + analysisPollCount.value * 4)
  }
  if (status === 'QUEUED') {
    return Math.min(36, 12 + analysisPollCount.value * 3)
  }
  return 0
})
const analysisProgressText = computed(() => {
  const job = currentAnalysisJob.value
  const status = job?.status
  const total = job?.totalReviewCount
  const processed = job?.processedReviewCount
  if (job?.currentStage) {
    return job.currentStage
  }
  if (typeof total === 'number' && total > 0 && typeof processed === 'number') {
    return `LLM 已完成 ${processed} / ${total} 条`
  }
  if (status === 'SUCCEEDED') {
    return '分析完成，正在刷新下游图表'
  }
  if (status === 'FAILED') {
    return '分析失败，请查看错误信息'
  }
  if (status === 'RUNNING') {
    return `后台 LLM 正在分析，第 ${analysisPollCount.value + 1} 次检查`
  }
  if (status === 'QUEUED') {
    return '任务已创建，等待后端开始执行'
  }
  return '待启动'
})
const fileState = computed(() => {
  if (intakeStatus.value?.stage) {
    return intakeStatus.value.stage.toLowerCase().replaceAll('_', '-')
  }
  if (currentAnalysisJob.value) {
    return 'analysis'
  }
  if (importResult.value) {
    return 'imported'
  }
  if (cleanResult.value) {
    return 'cleaned'
  }
  return rawJsonlPath.value ? 'raw' : 'waiting'
})
const summaryRows = computed(() => {
  const summary = currentCleaningSummary.value
  if (Object.keys(summary).length === 0) {
    return []
  }
  return [
    { label: 'raw', value: numberSummary(summary.rawCount) },
    { label: 'cleaned', value: numberSummary(summary.cleanedCount) },
    { label: 'removed', value: numberSummary(summary.removedCount) },
    { label: 'duplicate', value: numberSummary(summary.exactDuplicateCount) },
    { label: 'placeholder', value: numberSummary(summary.placeholderContentCount) },
    { label: 'empty', value: numberSummary(summary.emptyContentCount) },
    { label: 'invalid', value: numberSummary(summary.invalidJsonCount) },
  ]
})
const displayedRawOutputPath = computed(() => intakeStatus.value?.rawOutputPath || latestFileResult.value?.rawOutputPath || rawJsonlPath.value)
const displayedCleanedOutputPath = computed(() => intakeStatus.value?.cleanedOutputPath || latestFileResult.value?.cleanedOutputPath || '')
const displayedRemovedOutputPath = computed(() => intakeStatus.value?.removedOutputPath || latestFileResult.value?.removedOutputPath || '')
const displayedCleaningSummaryPath = computed(() => intakeStatus.value?.cleaningSummaryPath || latestFileResult.value?.cleaningSummaryPath || '')
const selectedTaxonomy = computed(() => taxonomyOptions.value.find((option) => taxonomyOptionKey(option) === selectedTaxonomyKey.value))
const activeTaxonomyId = computed(() => selectedTaxonomy.value?.taxonomyId ?? taxonomy.value?.taxonomyId)
const selectedTaxonomyName = computed(() => taxonomy.value?.name || selectedTaxonomy.value?.name || '待选择 taxonomy')
const selectedTaxonomyMeta = computed(() => {
  const activeTaxonomy = taxonomy.value ?? selectedTaxonomy.value
  if (!activeTaxonomy) {
    return '读取后从已有 taxonomy 中选择，不再手动输入品类。'
  }
  const id = activeTaxonomy.taxonomyId ? `#${activeTaxonomy.taxonomyId}` : '未保存'
  const categoryText = activeTaxonomy.category || category.value || 'general-product'
  return `${id} · 品类 ${categoryText} · ${enabledLabelCount.value}/${labels.value.length} 个标签启用`
})

function numberSummary(value: unknown): number {
  return typeof value === 'number' ? value : 0
}

function clampPercent(value: number): number {
  return Math.max(0, Math.min(100, Math.round(value)))
}

function defaultRawJsonlPath(code: string): string {
  const normalized = code.trim() || DEFAULT_PRODUCT_CODE
  return `crawler/output/raw_reviews_${normalized}.jsonl`
}

function currentProductCode(): string {
  return productCode.value.trim() || DEFAULT_PRODUCT_CODE
}

function showMessage(text: string, tone: 'info' | 'error' = 'info'): void {
  message.value = text
  messageTone.value = tone
}

function describeRequestError(error: unknown, action: string): string {
  const maybeError = error as { code?: unknown; response?: { status?: unknown; data?: unknown }; message?: unknown }
  if (maybeError?.code === 'ECONNABORTED') {
    return `${action}超时：后端可能正在处理大文件或 LLM 请求，请稍后查看任务状态。`
  }
  const status = typeof maybeError?.response?.status === 'number' ? maybeError.response.status : undefined
  if (status === 408 || status === 504) {
    return `${action}超时：接口超过等待时间，请稍后重试或检查任务是否仍在后台执行。`
  }
  if (status) {
    const data = maybeError.response?.data
    const detail =
      typeof data === 'object' && data !== null && 'message' in data && typeof data.message === 'string'
        ? `：${data.message}`
        : ''
    return `${action}失败：后端接口返回 ${status}${detail}`
  }
  if (typeof maybeError?.message === 'string' && maybeError.message.toLowerCase().includes('network')) {
    return `${action}失败：无法连接到后端服务，请确认 API 服务地址和网络连接。`
  }
  return `${action}失败：请检查输入路径、接口日志或稍后重试。`
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

function isFinalAnalysisStatus(status?: string): boolean {
  return status ? FINAL_ANALYSIS_STATUSES.has(status) : false
}

function isCleanedJsonlPath(path?: string): boolean {
  const normalized = path?.trim().replaceAll('\\', '/').toLowerCase() ?? ''
  return normalized.includes('/cleaned_reviews_') || normalized.split('/').pop()?.startsWith('cleaned_reviews_') === true
}

function analysisDownstreamReady(job: AnalysisJobResponse, status: ReviewIntakeStatusResponse | null): boolean {
  return (
    job.downstreamReady === true ||
    status?.downstreamReady === true ||
    (typeof job.materializedReviewCount === 'number' &&
      job.materializedReviewCount > 0 &&
      typeof job.semanticLabelCount === 'number' &&
      job.semanticLabelCount > 0)
  )
}

function materializedReviewCount(job: AnalysisJobResponse, status: ReviewIntakeStatusResponse | null): number {
  const jobCount = typeof job.materializedReviewCount === 'number' ? job.materializedReviewCount : 0
  const statusCount = typeof status?.analyzedReviewCount === 'number' ? status.analyzedReviewCount : 0
  const latestJobCount =
    typeof status?.latestAnalysisJob?.materializedReviewCount === 'number'
      ? status.latestAnalysisJob.materializedReviewCount
      : 0
  return Math.max(jobCount, statusCount, latestJobCount)
}

function emitAnalysisReadyIfChanged(
  job: AnalysisJobResponse,
  status: ReviewIntakeStatusResponse | null,
  imported: CrawlImportResponse,
  productCode: string,
): boolean {
  if (!analysisDownstreamReady(job, status)) {
    return false
  }
  const currentMaterializedCount = materializedReviewCount(job, status)
  if (currentMaterializedCount <= lastAnalysisReadyMaterializedCount.value) {
    return false
  }
  lastAnalysisReadyMaterializedCount.value = currentMaterializedCount
  emit('analysis-ready', {
    productCode,
    productName: imported.productName || productName.value.trim() || undefined,
    importResult: imported,
    analysisJob: job,
  })
  return true
}

async function pollAnalysisJob(
  initialJob: AnalysisJobResponse,
  productCode: string,
  imported: CrawlImportResponse,
): Promise<AnalysisJobResponse> {
  let currentJob = initialJob
  analysisPollCount.value = 0
  while (currentJob.jobId && !isFinalAnalysisStatus(currentJob.status) && analysisPollCount.value < MAX_ANALYSIS_POLLS) {
    await wait(ANALYSIS_POLL_INTERVAL_MS)
    analysisPollCount.value += 1
    currentJob = await fetchAnalysisJob(currentJob.jobId, productCode)
    analysisJob.value = currentJob
    const status = await refreshIntakeStatus(false)
    emitAnalysisReadyIfChanged(currentJob, status, imported, productCode)
  }
  return currentJob
}

function candidateLabel(candidate: JsonlFileCandidate): string {
  const name = candidate.productName?.trim() || candidate.productCode?.trim() || candidate.fileName
  const code = candidate.productCode?.trim()
  return code && name !== code ? `${name} · ${code}` : name
}

function taxonomyOptionKey(option: ProductTaxonomyResponse): string {
  if (option.taxonomyId) {
    return `id:${option.taxonomyId}`
  }
  return `category:${option.category || 'general-product'}`
}

function taxonomyOptionLabel(option: ProductTaxonomyResponse): string {
  const name = option.name?.trim() || option.category || '未命名 taxonomy'
  const id = option.taxonomyId ? `#${option.taxonomyId}` : '草稿'
  return `${name} · ${id}`
}

function syncSelectedTaxonomy(option: ProductTaxonomyResponse | null): void {
  if (!option) {
    return
  }
  taxonomy.value = option
  category.value = option.category || category.value || 'general-product'
  selectedTaxonomyKey.value = taxonomyOptionKey(option)
  labels.value = option.labels.map((label) => ({ ...label }))
}

function applySelectedTaxonomy(): void {
  const selected = selectedTaxonomy.value
  if (!selected) {
    return
  }
  syncSelectedTaxonomy(selected)
  showMessage(`已选择 ${selected.name || selected.category}，可预览后直接绑定到商品；编辑请到左侧 taxonomy 模块。`)
}

function applyJsonlCandidate(candidate: JsonlFileCandidate): void {
  selectedJsonlPath.value = candidate.path
  rawJsonlPath.value = candidate.path
  intakeStatus.value = null
  analysisJob.value = null
  if (candidate.productCode?.trim()) {
    productCode.value = candidate.productCode.trim()
  }
  if (candidate.productName?.trim()) {
    productName.value = candidate.productName.trim()
  }
  const sampleName = candidate.sampleReviews.find((sample) => sample.productName?.trim())?.productName?.trim()
  if (!productName.value.trim() && sampleName) {
    productName.value = sampleName
  }
}

function applySelectedJsonlPath(): void {
  const candidate = jsonlCandidates.value.find((item) => item.path === selectedJsonlPath.value)
  if (candidate) {
    applyJsonlCandidate(candidate)
    void refreshIntakeStatus(false)
  }
}

async function runTask(action: string, task: () => Promise<void>): Promise<void> {
  busy.value = true
  try {
    await task()
  } catch (error) {
    showMessage(describeRequestError(error, action), 'error')
  } finally {
    busy.value = false
  }
}

async function loadTaxonomy(showToast = true): Promise<void> {
  await runTask('读取 taxonomy', async () => {
    const options = await fetchTaxonomies(currentProductCode())
    taxonomyOptions.value = options
    const response = await fetchProductTaxonomy(productCode.value, category.value || 'general-product')
    const mergedOptions = [
      response,
      ...options.filter((option) => taxonomyOptionKey(option) !== taxonomyOptionKey(response)),
    ]
    taxonomyOptions.value = mergedOptions
    syncSelectedTaxonomy(response)
    if (showToast) {
      showMessage(response.notice || '已读取当前商品绑定的 taxonomy，可从下拉框切换到其他已编辑 taxonomy。')
    }
  })
}

async function bindCurrentTaxonomy(): Promise<ProductTaxonomyResponse | null> {
  const taxonomyId = activeTaxonomyId.value
  if (!taxonomyId) {
    showMessage('请先选择一个已保存的 taxonomy。', 'error')
    return null
  }
  const response = await bindProductTaxonomy(currentProductCode(), taxonomyId, category.value || 'general-product')
  taxonomyOptions.value = [
    response,
    ...taxonomyOptions.value.filter((option) => taxonomyOptionKey(option) !== taxonomyOptionKey(response)),
  ]
  syncSelectedTaxonomy(response)
  showMessage(response.notice || '已把当前 taxonomy 绑定到商品。')
  return response
}

async function loadJsonlFiles(): Promise<void> {
  jsonlLoading.value = true
  try {
    const candidates = await fetchJsonlFiles()
    jsonlCandidates.value = candidates
    if (candidates.length === 0) {
      selectedJsonlPath.value = ''
      showMessage('未在 crawler/output 识别到 raw JSONL 文件，可继续手动填写路径。')
      return
    }
    const currentCode = currentProductCode()
    const preferred = candidates.find((candidate) => candidate.productCode === currentCode) ?? candidates[0]
    applyJsonlCandidate(preferred)
    showMessage(`已识别 ${candidates.length} 个 raw JSONL 文件，并已回填最新候选。`)
  } catch (error) {
    showMessage(describeRequestError(error, '识别 JSONL 文件'), 'error')
  } finally {
    jsonlLoading.value = false
  }
}

async function saveTaxonomy(): Promise<void> {
  await runTask('绑定 taxonomy', async () => {
    await bindCurrentTaxonomy()
    await refreshIntakeStatus(false)
  })
}

function buildJsonlPayload(inputPath = rawJsonlPath.value, replaceExisting = false): JsonlFileImportPayload {
  const normalizedProductName = productName.value.trim()
  const payload: JsonlFileImportPayload = {
    productCode: currentProductCode(),
    inputPath,
    platform: platform.value,
  }
  if (normalizedProductName) {
    payload.productName = normalizedProductName
  }
  if (replaceExisting) {
    payload.replaceExisting = true
  }
  return payload
}

function applyFileResult(result: CrawlImportResponse): void {
  if (result.productName?.trim()) {
    productName.value = result.productName.trim()
  }
}

async function refreshIntakeStatus(showToast = false): Promise<ReviewIntakeStatusResponse | null> {
  statusLoading.value = true
  try {
    const status = await fetchReviewIntakeStatus(currentProductCode(), rawJsonlPath.value)
    intakeStatus.value = status
    if (status.productName?.trim() && !productName.value.trim()) {
      productName.value = status.productName.trim()
    }
    if (status.latestAnalysisJob) {
      analysisJob.value = status.latestAnalysisJob
    }
    if (showToast) {
      showMessage(status.notice || '已刷新当前数据接入状态。')
    }
    return status
  } catch (error) {
    if (showToast) {
      showMessage(describeRequestError(error, '刷新状态'), 'error')
    }
    return null
  } finally {
    statusLoading.value = false
  }
}

async function cleanLocalJsonl(): Promise<void> {
  await runTask('清洗 JSONL', async () => {
    const normalizedProductCode = currentProductCode()
    analysisPollCount.value = 0
    cleanResult.value = await cleanJsonlFile(buildJsonlPayload())
    applyFileResult(cleanResult.value)
    importResult.value = null
    analysisJob.value = null
    showMessage(
      `JSONL 已清洗完成：保留 ${numberSummary(cleanResult.value.cleaningSummary.cleanedCount)} 条，移除 ${numberSummary(
        cleanResult.value.cleaningSummary.removedCount,
      )} 条。请检查摘要和样本，然后绑定 taxonomy。`,
    )
    if (cleanResult.value.productCode?.trim() && cleanResult.value.productCode !== normalizedProductCode) {
      productCode.value = cleanResult.value.productCode.trim()
    }
    await refreshIntakeStatus(false)
  })
}

async function importJsonlIntoDatabase(options: { reuseExistingImport?: boolean } = {}): Promise<CrawlImportResponse | null> {
  const statusCleanedPath = intakeStatus.value?.cleanedJsonlExists ? intakeStatus.value.cleanedOutputPath : ''
  const importPath = cleanResult.value?.cleanedOutputPath || statusCleanedPath || rawJsonlPath.value
  const shouldReplaceExisting = isCleanedJsonlPath(importPath)
  if (!importResult.value && importedReviewCount.value > 0 && (options.reuseExistingImport || !shouldReplaceExisting)) {
    const existingImport: CrawlImportResponse = {
      jobId: 'status-existing',
      importJobId: '',
      productCode: currentProductCode(),
      productName: resolvedProductName.value === '待识别' ? undefined : resolvedProductName.value,
      provider: 'local-jsonl',
      platform: platform.value,
      rawOutputPath: displayedRawOutputPath.value,
      cleanedOutputPath: displayedCleanedOutputPath.value,
      removedOutputPath: displayedRemovedOutputPath.value,
      cleaningSummaryPath: displayedCleaningSummaryPath.value,
      receivedCount: importedReviewCount.value,
      insertedReviewCount: 0,
      updatedReviewCount: 0,
      totalReviewCount: importedReviewCount.value,
      cleaningSummary: currentCleaningSummary.value,
      sampleReviews: visibleSamples.value,
      analysisHandoffStatus: 'READY_FOR_ANALYSIS',
      analysisHandoffNote: intakeStatus.value?.notice,
    }
    importResult.value = existingImport
    return existingImport
  }
  const imported = await importJsonlFile(buildJsonlPayload(importPath, shouldReplaceExisting))
  applyFileResult(imported)
  importResult.value = imported
  if (!cleanResult.value) {
    cleanResult.value = imported
  }
  return imported
}

async function importLocalJsonl(): Promise<void> {
  await runTask('导入数据库', async () => {
    analysisJob.value = null
    analysisPollCount.value = 0
    const imported = await importJsonlIntoDatabase()
    if (!imported) {
      return
    }
    await refreshIntakeStatus(false)
    showMessage(`JSONL 已导入数据库：本次接收 ${imported.receivedCount} 条，当前数据库共 ${imported.totalReviewCount} 条。`)
  })
}

async function startProductAnalysis(): Promise<void> {
  await runTask('启动 LLM 分析', async () => {
    const normalizedProductCode = currentProductCode()
    const savedTaxonomy = await bindCurrentTaxonomy()
    if (!savedTaxonomy) {
      return
    }
    const imported = importResult.value ?? (await importJsonlIntoDatabase({ reuseExistingImport: true }))
    if (!imported) {
      return
    }
    const queuedJob = await startAnalysis(normalizedProductCode)
    analysisJob.value = queuedJob
    lastAnalysisReadyMaterializedCount.value = 0
    await refreshIntakeStatus(false)
    showMessage('LLM 分析任务已创建，正在后台处理。')
    const finalJob = await pollAnalysisJob(queuedJob, normalizedProductCode, imported)
    analysisJob.value = finalJob
    const finalStatus = await refreshIntakeStatus(false)
    const downstreamReady = finalJob.status === 'SUCCEEDED' && analysisDownstreamReady(finalJob, finalStatus)
    if (downstreamReady) {
      emitAnalysisReadyIfChanged(finalJob, finalStatus, imported, normalizedProductCode)
    }
    showMessage(
      finalJob.errorMessage ||
        (downstreamReady
          ? `已导入数据库，当前数据库共 ${imported.totalReviewCount} 条；LLM 分析已完成，可查看问题、趋势、词云和卖点。`
          : finalJob.status === 'SUCCEEDED'
          ? 'LLM 任务已结束，但下游图表依赖的数据没有写入，请重新导入 cleaned JSONL 后再启动分析。'
          : 'LLM 分析任务仍在后台执行，请稍后查看状态。'),
      finalJob.status === 'FAILED' || (finalJob.status === 'SUCCEEDED' && !downstreamReady) ? 'error' : 'info',
    )
  })
}

onMounted(() => {
  void loadTaxonomy(false)
  void (async () => {
    await loadJsonlFiles()
    await refreshIntakeStatus(false)
  })()
})

watch(productCode, (current, previous) => {
  if (intakeStatus.value?.productCode && intakeStatus.value.productCode !== current) {
    intakeStatus.value = null
    analysisJob.value = null
  }
  const matchedCandidate = jsonlCandidates.value.find((candidate) => candidate.productCode === current)
  if (matchedCandidate && (!rawJsonlPath.value || rawJsonlPath.value === defaultRawJsonlPath(previous || DEFAULT_PRODUCT_CODE))) {
    applyJsonlCandidate(matchedCandidate)
    return
  }
  if (!rawJsonlPath.value || rawJsonlPath.value === defaultRawJsonlPath(previous || DEFAULT_PRODUCT_CODE)) {
    rawJsonlPath.value = defaultRawJsonlPath(current)
  }
})
</script>

<style scoped>
.panel {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  gap: var(--space-3);
  height: 100%;
  min-height: 0;
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-lg);
  padding: var(--space-3);
  background: var(--gradient-surface);
  box-shadow: var(--shadow-raised);
}

.head,
.form-grid,
.toolbar,
.panel-body {
  position: relative;
  z-index: var(--z-raised);
}

.title-block,
.jsonl-controls,
.taxonomy-main,
.label-editor,
.status-panel,
.jsonl-panel,
.analysis-panel,
.recent-reviews {
  display: grid;
  gap: var(--space-2);
  min-height: 0;
}

h3,
h4 {
  margin: 0;
  color: var(--color-text-primary);
  line-height: var(--line-height-tight);
}

h3 {
  font-size: var(--font-size-xl);
}

h4 {
  font-size: var(--font-size-lg);
}

.support,
.notice,
.handoff {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(12rem, 1.3fr) minmax(12rem, 1.3fr) minmax(7rem, 0.7fr) minmax(10rem, 0.9fr);
  gap: var(--space-2);
}

.field {
  display: grid;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.field--wide {
  grid-column: span 2;
}

input,
select {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-md);
  background: rgba(8, 16, 29, 0.72);
  color: var(--color-text-primary);
  min-height: 2.25rem;
  padding: var(--space-2) var(--space-3);
  font: inherit;
  box-shadow: var(--shadow-inset-soft);
}

input:focus-visible,
select:focus-visible {
  outline: none;
  box-shadow: var(--shadow-focus);
}

.jsonl-picker {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-2);
  align-items: center;
}

.toolbar,
.section-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.section-head {
  justify-content: space-between;
}

.primary-btn,
.secondary-btn,
.icon-btn {
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  color: var(--color-text-primary);
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-sm);
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  box-shadow: var(--shadow-inset-soft);
}

.primary-btn {
  background: linear-gradient(135deg, rgba(122, 184, 255, 0.2), rgba(102, 224, 194, 0.18));
}

.secondary-btn,
.icon-btn {
  background: rgba(8, 16, 29, 0.72);
}

.compact {
  padding-inline: var(--space-2);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.notice {
  padding: var(--space-2) var(--space-3);
  border: 1px dashed var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: rgba(8, 15, 27, 0.42);
}

.notice--error,
.handoff--error {
  border-color: rgba(255, 123, 133, 0.28);
  color: var(--color-semantic-down);
}

.panel-body {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--space-3);
  overflow: hidden;
  min-height: 0;
}

.step-top {
  display: grid;
  gap: var(--space-2);
  min-height: 0;
}

.step-nav {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
  min-height: 0;
}

.step-tab {
  display: grid;
  gap: 2px;
  min-width: 0;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  padding: var(--space-2) var(--space-3);
  background: var(--color-surface-1);
  color: var(--color-text-secondary);
  text-align: left;
  cursor: pointer;
  box-shadow: var(--shadow-inset-soft);
}

.step-tab strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-tight);
}

.step-tab span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-tight);
}

.step-tab--active {
  border-color: var(--color-border-strong);
  background: linear-gradient(135deg, rgba(122, 184, 255, 0.16), rgba(102, 224, 194, 0.1));
}

.setup-step-shell {
  overflow: hidden;
  min-height: 0;
}

.setup-step {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.setup-step--jsonl {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--space-3);
}

.setup-step--taxonomy,
.setup-step--analysis {
  display: grid;
  min-height: 0;
}

.taxonomy-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(18rem, 0.42fr);
  gap: var(--space-3);
  min-height: 0;
}

.taxonomy-main {
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  min-width: 0;
}

.taxonomy-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-2);
  min-width: 0;
}

.taxonomy-summary h4,
.taxonomy-summary p {
  margin: 0;
}

.taxonomy-summary p {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.setup-step--status {
  display: grid;
  grid-template-columns: minmax(16rem, 0.78fr) minmax(0, 1.22fr);
  gap: var(--space-3);
  min-height: 0;
}

.jsonl-controls {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.15fr);
}

.label-editor,
.status-panel,
.jsonl-panel,
.analysis-panel,
.recent-reviews {
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  background: var(--color-surface-1);
  box-shadow: var(--shadow-inset-soft);
  overflow: hidden;
}

.jsonl-panel,
.analysis-panel,
.recent-reviews {
  position: relative;
  z-index: var(--z-raised);
}

.scroll-region {
  min-height: 0;
  overflow: auto;
}

.jsonl-details,
.analysis-scroll,
.status-scroll,
.recent-scroll {
  display: grid;
  gap: var(--space-2);
}

.label-list.scroll-region {
  padding-right: var(--space-1);
}

.label-editor--sidebar {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: var(--space-3);
  min-height: 0;
}

.file-state {
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-pill);
  padding: var(--space-1) var(--space-3);
  color: var(--color-accent-primary);
  font-size: var(--font-size-xs);
  text-transform: uppercase;
}

.file-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
}

.summary-grid span {
  display: grid;
  gap: 2px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-sm);
  padding: var(--space-2);
}

.summary-grid strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.summary-grid small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.stage-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
}

.stage-grid span {
  display: grid;
  gap: 2px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-sm);
  padding: var(--space-2);
}

.stage-grid strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-snug);
}

.stage-grid small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.sample-list {
  display: grid;
  gap: var(--space-1);
  margin: 0;
  padding: 0;
  list-style: none;
}

.sample-list li {
  display: grid;
  grid-template-columns: 3.25rem minmax(0, 1fr);
  gap: var(--space-2);
  align-items: start;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-sm);
  padding: var(--space-2);
}

.sample-list span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.sample-list p {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-snug);
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.progress-block {
  display: grid;
  gap: var(--space-2);
}

.progress-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.progress-meta strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
}

.progress-track {
  position: relative;
  overflow: hidden;
  height: 0.5rem;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.05);
}

.progress-fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--color-accent-primary), var(--color-accent-secondary));
  transition: width 180ms ease;
}

.progress-fill--failed {
  background: var(--color-semantic-down);
}

.label-list {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.label-row {
  display: grid;
  grid-template-columns: auto minmax(8rem, 1fr) minmax(10rem, 1.2fr) auto;
  gap: var(--space-2);
  align-items: center;
}

.label-row--sidebar {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-sm);
  padding: var(--space-2);
}

.label-row--sidebar .toggle,
.label-row--sidebar .label-field {
  grid-column: 1 / -1;
}

.label-field {
  gap: var(--space-1);
}

.taxonomy-save {
  width: 100%;
}

.toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.toggle input {
  width: auto;
}

.icon-btn {
  width: 2rem;
  height: 2rem;
  padding: 0;
  color: var(--color-text-secondary);
}

dl {
  display: grid;
  gap: var(--space-2);
  margin: 0;
}

.status-panel dl {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

dl div {
  display: grid;
  gap: 2px;
}

dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

dd {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

@media (max-width: 960px) {
  .form-grid,
  .step-nav,
  .taxonomy-workspace,
  .setup-step--status,
  .jsonl-controls,
  .jsonl-picker,
  .label-row,
  .file-list,
  .summary-grid,
  .stage-grid,
  .sample-list li {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .panel,
  .label-editor,
  .status-panel,
  .jsonl-panel,
  .analysis-panel,
  .recent-reviews {
    padding: var(--space-3);
  }
}
</style>
