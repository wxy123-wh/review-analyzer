<template>
  <LoginGate
    v-if="!isAuthenticated"
    :expected-username="internalAccessUsername"
    :expected-password="internalAccessPassword"
    :display-name="internalAccessDisplayName"
    :access-hint="internalAccessHint"
    @enter="handleLogin"
  />

  <AppShellFrame v-else>
    <template #sidebar>
        <AppShellSidebar
          :items="modules"
          :hidden-items="hiddenModules"
          :active-module="activeModule"
          @select="activateModule($event as ModuleId)"
        />
    </template>

    <template #header>
      <AppShellHeader :current-user="currentUser" />
    </template>

    <section class="module-card" data-motion-reveal style="--motion-delay: 120ms">
        <p v-if="loading" class="hint">加载中...</p>
        <p v-else-if="loadError" class="hint error">{{ loadError }}</p>

        <template v-else-if="activeModule === 'overview'">
          <p v-if="overview.notice" class="overview-notice" :class="`overview-notice--${overview.state}`">
            {{ overview.notice }}
          </p>
          <div class="status-grid">
            <StatusCard
              v-for="item in serviceStatuses"
              :key="item.name"
              :name="item.name"
              :status="item.status"
            />
          </div>
          <div class="overview-grid">
            <article class="metric">
              <h3>高优先级问题</h3>
              <p>{{ overview.topIssue?.title ?? '暂无' }}</p>
            </article>
            <article class="metric">
              <h3>问题数量</h3>
              <p>{{ overview.issueCount }}</p>
            </article>
            <article class="metric">
              <h3>已登记动作</h3>
              <p>{{ overview.actionCount }}</p>
            </article>
          </div>
        </template>

        <IssueTable v-else-if="activeModule === 'issues'" :items="issues" :state="issueState" :message="issueMessage" />
        <CompareTable
          v-else-if="activeModule === 'compare'"
          :items="compareItems"
          :state="compareState"
          :message="compareMessage"
          :product-code="compareProductCode"
          :comparison-product-code="compareComparisonProductCode"
        />
        <TrendList
          v-else-if="activeModule === 'trends'"
          :aspect="trendAspect"
          :points="trendPoints"
          :state="trendState"
          :message="trendMessage"
          @retry="reloadTrendData"
        />
        <WordCloudPanel
          v-else-if="activeModule === 'wordcloud'"
          :aspect="wordCloudAspect"
          :items="wordCloudItems"
          :state="wordCloudState"
          :message="wordCloudMessage"
          :notice="wordCloudNotice"
          @retry="reloadWordCloudData"
        />
        <ActionList
          v-else-if="activeModule === 'actions'"
          :items="actions"
          :state="actionState"
          :message="actionMessage"
          @create-demo="createDemoAction"
        />
        <ValidationList
          v-else-if="activeModule === 'validation'"
          :items="validations"
          :state="validationState"
          :message="validationMessage"
        />

        <ShowcasePipelinePanel
          v-else-if="activeModule === 'showcase-pipeline'"
          :data="showcasePipeline"
        />
        <ShowcaseAgentArenaPanel
          v-else-if="activeModule === 'showcase-agent-arena'"
          :data="showcaseAgentArena"
        />
        <ShowcaseExplainabilityPanel
          v-else-if="activeModule === 'showcase-explainability'"
          :data="showcaseExplainability"
        />
        <ShowcaseChaosPanel v-else-if="activeModule === 'showcase-chaos'" :data="showcaseChaos" />
        <ShowcaseReportCenter
          v-else-if="activeModule === 'showcase-report-center'"
          :data="showcaseReportPreview"
          @preview="generateReportPreview"
        />
    </section>
  </AppShellFrame>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import ActionList from './components/ActionList.vue'
import AppShellFrame from './components/AppShellFrame.vue'
import AppShellHeader from './components/AppShellHeader.vue'
import AppShellSidebar from './components/AppShellSidebar.vue'
import CompareTable from './components/CompareTable.vue'
import IssueTable from './components/IssueTable.vue'
import LoginGate from './components/LoginGate.vue'
import ShowcaseAgentArenaPanel from './components/ShowcaseAgentArenaPanel.vue'
import ShowcaseChaosPanel from './components/ShowcaseChaosPanel.vue'
import ShowcaseExplainabilityPanel from './components/ShowcaseExplainabilityPanel.vue'
import ShowcasePipelinePanel from './components/ShowcasePipelinePanel.vue'
import ShowcaseReportCenter from './components/ShowcaseReportCenter.vue'
import StatusCard from './components/StatusCard.vue'
import TrendList from './components/TrendList.vue'
import ValidationList from './components/ValidationList.vue'
import WordCloudPanel from './components/WordCloudPanel.vue'
import {
  createAction,
  fetchBackendHealth,
  fetchActions,
  fetchCompare,
  fetchIssues,
  fetchShowcaseAgentArena,
  fetchShowcaseChaos,
  fetchShowcaseExplainability,
  fetchShowcasePipeline,
  fetchTrends,
  fetchValidation,
  fetchWordCloud,
  nlpDemoStatus,
  previewShowcaseReport,
} from './api/client'
import { useMotionPreferences } from './motion/preferences'
import type {
  ActionItem,
  ActionResponse,
  ChartLoadState,
  CompareItem,
  CompareResponse,
  CompareState,
  ContractState,
  IssueItem,
  IssueResponse,
  OverviewContract,
  ServiceStatus,
  ShowcaseAgentArenaData,
  ShowcaseChaosData,
  ShowcaseExplainabilityData,
  ShowcasePipelineData,
  ShowcaseReportPreviewData,
  TrendPoint,
  TrendResponse,
  ValidationItem,
  ValidationResponse,
  WordCloudResponse,
  WordCloudItem,
} from './types/domain'

useMotionPreferences()

type ModuleId =
  | 'overview'
  | 'issues'
  | 'compare'
  | 'trends'
  | 'wordcloud'
  | 'actions'
  | 'validation'
  | 'showcase-pipeline'
  | 'showcase-agent-arena'
  | 'showcase-explainability'
  | 'showcase-chaos'
  | 'showcase-report-center'

type ModuleContractState = 'real' | 'controlled-data-only' | 'placeholder' | 'gated-placeholder'
type ModuleContractStrategy = 'keep' | 'replace' | 'hide-by-default'

type ModuleContract = {
  id: ModuleId
  label: string
  icon: string
  state: ModuleContractState
  dataSource: string
  strategy: ModuleContractStrategy
  featureFlag?: string
}

type ShellModuleItem = ModuleContract & {
  stateLabel: string
  strategyLabel: string
  availabilityLabel: string
  enabled: boolean
}

function isFeatureEnabled(rawValue: unknown, defaultValue = false): boolean {
  if (typeof rawValue !== 'string') {
    return defaultValue
  }
  const normalized = rawValue.trim().toLowerCase()
  if (['1', 'true', 'yes', 'on'].includes(normalized)) {
    return true
  }
  if (['0', 'false', 'no', 'off'].includes(normalized)) {
    return false
  }
  return defaultValue
}

const chaosModuleVisible = isFeatureEnabled(import.meta.env.VITE_SHOW_CHAOS_MODULE, false)
const internalAccessUsername = `${import.meta.env.VITE_INTERNAL_ACCESS_USERNAME ?? 'wxy'}`.trim() || 'wxy'
const internalAccessPassword = `${import.meta.env.VITE_INTERNAL_ACCESS_PASSWORD ?? '123456'}`.trim() || '123456'
const internalAccessDisplayName = `${import.meta.env.VITE_INTERNAL_ACCESS_DISPLAY_NAME ?? '内部体验账号'}`.trim() || '内部体验账号'
const internalAccessHint = `${import.meta.env.VITE_INTERNAL_ACCESS_HINT ?? '仅用于内部首发验收与演示环境访问。'}`.trim()

const moduleStateLabels: Record<ModuleContractState, string> = {
  real: '真实能力',
  'controlled-data-only': '受控数据',
  placeholder: '占位演示',
  'gated-placeholder': '开关占位',
}

const moduleStrategyLabels: Record<ModuleContractStrategy, string> = {
  keep: '当前策略：保留',
  replace: '当前策略：后续替换',
  'hide-by-default': '当前策略：默认隐藏',
}

const moduleContracts: ModuleContract[] = [
  {
    id: 'overview',
    label: '总览',
    icon: 'O',
    state: 'controlled-data-only',
    dataSource: 'client-composed from health/issues/actions/validation contracts',
    strategy: 'keep',
  },
  {
    id: 'issues',
    label: '问题',
    icon: 'I',
    state: 'real',
    dataSource: 'backend query service over controlled demo reviews',
    strategy: 'keep',
  },
  {
    id: 'compare',
    label: '对比',
    icon: 'C',
    state: 'real',
    dataSource: 'backend compare query over materialized controlled demo reviews',
    strategy: 'keep',
  },
  {
    id: 'trends',
    label: '趋势图',
    icon: 'T',
    state: 'real',
    dataSource: 'backend query service over controlled demo reviews',
    strategy: 'keep',
  },
  {
    id: 'wordcloud',
    label: '词云',
    icon: 'W',
    state: 'real',
    dataSource: 'backend query service over controlled demo reviews',
    strategy: 'keep',
  },
  {
    id: 'actions',
    label: '动作',
    icon: 'A',
    state: 'real',
    dataSource: 'backend action query/create APIs with contract-aware state handling',
    strategy: 'keep',
  },
  {
    id: 'validation',
    label: '验证',
    icon: 'V',
    state: 'real',
    dataSource: 'backend validation snapshots with contract-aware state handling',
    strategy: 'keep',
  },
  {
    id: 'showcase-pipeline',
    label: '流水线',
    icon: 'P',
    state: 'real',
    dataSource: 'persisted sync/analysis/materialization/action/validation runtime state',
    strategy: 'keep',
  },
  {
    id: 'showcase-agent-arena',
    label: '智能体',
    icon: 'G',
    state: 'real',
    dataSource: 'synthesized lane view over persisted subsystem runtime state',
    strategy: 'keep',
  },
  {
    id: 'showcase-explainability',
    label: '可解释性',
    icon: 'E',
    state: 'controlled-data-only',
    dataSource: 'deterministic score weights, not model introspection',
    strategy: 'keep',
  },
  {
    id: 'showcase-chaos',
    label: '韧性演练',
    icon: 'H',
    state: 'real',
    dataSource: 'latest sync/analysis/materialization health behind feature flag',
    strategy: 'hide-by-default',
    featureFlag: 'VITE_SHOW_CHAOS_MODULE',
  },
  {
    id: 'showcase-report-center',
    label: '报告中心',
    icon: 'R',
    state: 'real',
    dataSource: 'preview sections assembled from issues/compare/trends/actions/validation queries',
    strategy: 'keep',
  },
]

const moduleAvailability = computed<ShellModuleItem[]>(() =>
  moduleContracts.map((module) => {
    const enabled = module.id !== 'showcase-chaos' || chaosModuleVisible
    return {
      ...module,
      stateLabel: enabled ? moduleStateLabels[module.state] : '已禁用',
      strategyLabel: moduleStrategyLabels[module.strategy],
      availabilityLabel: enabled ? '当前可访问' : `当前已禁用，需开启 ${module.featureFlag ?? '环境开关'}`,
      enabled,
    }
  }),
)

const modules = computed(() => moduleAvailability.value.filter((module) => module.enabled))
const hiddenModules = computed(() => moduleAvailability.value.filter((module) => !module.enabled))

const isAuthenticated = ref(false)
const currentUser = ref('内部访客')
const activeModule = ref<ModuleId>('overview')
const trendAspect = ref('battery')
const compareProductCode = ref('demo-earphone')
const compareComparisonProductCode = ref('demo-earphone-competitor')
const compareState = ref<CompareState>('idle')
const compareMessage = ref('')
const trendState = ref<ChartLoadState>('idle')
const trendMessage = ref('')
const wordCloudAspect = ref('all')
const wordCloudItems = ref<WordCloudItem[]>([])
const wordCloudState = ref<ChartLoadState>('idle')
const wordCloudMessage = ref('')
const wordCloudNotice = ref('')

const loading = ref(false)
const loadError = ref('')

const serviceStatuses = ref<ServiceStatus[]>([
  { name: 'Backend API', status: 'UNKNOWN' },
  nlpDemoStatus(),
])
const issues = ref<IssueItem[]>([])
const compareItems = ref<CompareItem[]>([])
const trendPoints = ref<TrendPoint[]>([])
const actions = ref<ActionItem[]>([])
const validations = ref<ValidationItem[]>([])
const issueState = ref<ContractState>('idle')
const issueMessage = ref('')
const actionState = ref<ContractState>('idle')
const actionMessage = ref('')
const validationState = ref<ContractState>('idle')
const validationMessage = ref('')
const overview = ref<OverviewContract>({
  topIssue: null,
  issueCount: 0,
  actionCount: 0,
  validationCount: 0,
  state: 'idle',
})

const showcasePipeline = ref<ShowcasePipelineData | null>(null)
const showcaseAgentArena = ref<ShowcaseAgentArenaData | null>(null)
const showcaseExplainability = ref<ShowcaseExplainabilityData | null>(null)
const showcaseChaos = ref<ShowcaseChaosData | null>(null)
const showcaseReportPreview = ref<ShowcaseReportPreviewData | null>(null)

function resolveContractMessage(
  state: ContractState | ChartLoadState,
  notice: string | undefined,
  fallbackEmpty: string,
  fallbackDegraded: string,
  fallbackError: string,
  fallbackRuntimeUnavailable = fallbackError,
  fallbackDisabled = fallbackEmpty,
  fallbackTimeout = fallbackError,
): string {
  if (state === 'empty') {
    return notice?.trim() || fallbackEmpty
  }
  if (state === 'degraded') {
    return notice?.trim() || fallbackDegraded
  }
  if (state === 'runtime-unavailable') {
    return notice?.trim() || fallbackRuntimeUnavailable
  }
  if (state === 'disabled') {
    return notice?.trim() || fallbackDisabled
  }
  if (state === 'timeout') {
    return notice?.trim() || fallbackTimeout
  }
  if (state === 'error') {
    return notice?.trim() || fallbackError
  }
  return ''
}

function syncOverviewContract(): void {
  const notices = [issueMessage.value, actionMessage.value, validationMessage.value]
    .map((item) => item.trim())
    .filter((item, index, items) => item.length > 0 && items.indexOf(item) === index)
  const states = [issueState.value, actionState.value, validationState.value]
  let state: ContractState = 'success'
  if (states.every((item) => item === 'idle' || item === 'loading')) {
    state = 'loading'
  } else if (states.some((item) => ['degraded', 'error', 'runtime-unavailable', 'disabled'].includes(item))) {
    state = 'degraded'
  } else if (issues.value.length === 0 && actions.value.length === 0 && validations.value.length === 0) {
    state = 'empty'
  }

  overview.value = {
    topIssue: issues.value[0] ?? null,
    issueCount: issues.value.length,
    actionCount: actions.value.length,
    validationCount: validations.value.length,
    state,
    notice:
      state === 'degraded'
        ? notices.join('；')
        : state === 'empty'
          ? '当前暂无可展示的总览数据。'
          : undefined,
  }
}

function applyIssueResponse(response: IssueResponse): void {
  issues.value = response.items
  issueState.value = response.state
  issueMessage.value = resolveContractMessage(
    response.state,
    response.notice,
    '暂无问题数据',
    '问题列表暂时回退为受限结果，请稍后刷新。',
    '问题接口请求失败，请稍后重试。',
  )
  syncOverviewContract()
}

function applyActionResponse(response: ActionResponse): void {
  actions.value = response.items
  actionState.value = response.state
  actionMessage.value = resolveContractMessage(
    response.state,
    response.notice,
    '暂无动作，点击“登记演示数据动作”快速创建。',
    '动作列表暂时只返回部分结果，可稍后重试刷新。',
    '动作接口请求失败，请稍后重试。',
  )
  syncOverviewContract()
}

function applyValidationResponse(response: ValidationResponse): void {
  validations.value = response.items
  validationState.value = response.state
  validationMessage.value = resolveContractMessage(
    response.state,
    response.notice,
    '暂无验证结果',
    '验证结果暂时回退为部分数据，请稍后刷新。',
    '验证接口请求失败，请稍后重试。',
  )
  syncOverviewContract()
}

function applyTrendResponse(response: TrendResponse): void {
  trendAspect.value = response.aspect
  trendPoints.value = response.points
  trendState.value = response.state
  trendMessage.value = resolveContractMessage(
    response.state,
    response.notice,
    '暂无趋势数据，建议先初始化演示评论数据。',
    '趋势数据暂时只保留最近一次可用时间窗，请稍后重试。',
    '趋势接口请求失败，请稍后重试。',
    '趋势运行态暂不可用，请稍后重试。',
    '趋势模块当前已禁用。',
    '趋势接口请求超时，请检查网络后重试。',
  )
}

function applyCompareResponse(response: CompareResponse): void {
  compareProductCode.value = response.productCode
  compareComparisonProductCode.value = response.comparisonProductCode ?? ''
  compareItems.value = response.items
  compareState.value = response.state

  if (response.state === 'missing-target') {
    compareMessage.value = response.notice?.trim() || '请选择需要对比的竞品后再查看对比结果。'
  } else if (response.state === 'comparison-unavailable') {
    compareMessage.value = response.notice?.trim() || '竞品暂无可用分析结果，请先完成受控数据初始化与分析。'
  } else if (response.state === 'primary-unavailable') {
    compareMessage.value = response.notice?.trim() || '主产品暂无可用分析结果，请先完成受控数据初始化与分析。'
  } else if (response.state === 'error') {
    compareMessage.value = response.notice?.trim() || '竞品对比接口请求失败，请稍后重试。'
  } else {
    compareMessage.value = ''
  }
}

function applyWordCloudResponse(response: WordCloudResponse): void {
  wordCloudAspect.value = response.aspect
  wordCloudItems.value = response.items
  wordCloudState.value = response.state
  wordCloudMessage.value = resolveContractMessage(
    response.state,
    response.notice,
    '暂无词云数据，建议先初始化演示评论数据。',
    '词云数据暂时退化为受限结果，请稍后重试。',
    '词云接口请求失败，请稍后重试。',
    '词云运行态暂不可用，请稍后重试。',
    '词云模块当前已禁用。',
    '词云接口请求超时，请检查网络后重试。',
  )
  wordCloudNotice.value = response.state === 'success' ? response.notice?.trim() ?? '' : ''
}

function handleLogin(payload: { username: string; displayName: string }): void {
  currentUser.value = payload.displayName || payload.username
  isAuthenticated.value = true
  void loadDashboard()
}

function activateModule(moduleId: ModuleId): void {
  activeModule.value = moduleId
  void ensureModuleData(moduleId)
}

async function ensureModuleData(moduleId: ModuleId): Promise<void> {
  if (moduleId === 'wordcloud' && wordCloudState.value === 'idle') {
    await reloadWordCloudData()
  } else if (moduleId.startsWith('showcase-')) {
    // Preload all showcase data in parallel when any showcase module is accessed
    await Promise.all([
      showcasePipeline.value ? Promise.resolve() : fetchShowcasePipeline().then(data => showcasePipeline.value = data),
      showcaseAgentArena.value ? Promise.resolve() : fetchShowcaseAgentArena().then(data => showcaseAgentArena.value = data),
      showcaseExplainability.value ? Promise.resolve() : fetchShowcaseExplainability().then(data => showcaseExplainability.value = data),
      showcaseChaos.value ? Promise.resolve() : fetchShowcaseChaos().then(data => showcaseChaos.value = data),
    ])
  }
}

async function loadDashboard(): Promise<void> {
  loading.value = true
  loadError.value = ''
  issueState.value = 'loading'
  actionState.value = 'loading'
  validationState.value = 'loading'
  syncOverviewContract()
  compareState.value = 'loading'
  trendState.value = 'loading'
  try {
    const [backendStatus, issueResponse, actionResponse, compareResponse, trendResponse, validationResponse] = await Promise.all([
      fetchBackendHealth(),
      fetchIssues(),
      fetchActions(),
      fetchCompare(),
      fetchTrends(undefined, trendAspect.value),
      fetchValidation(),
    ])
    serviceStatuses.value = [backendStatus, nlpDemoStatus()]
    applyIssueResponse(issueResponse)
    applyActionResponse(actionResponse)
    applyCompareResponse(compareResponse)
    applyTrendResponse(trendResponse)
    applyValidationResponse(validationResponse)
  } catch {
    loadError.value = '加载失败，请检查后端服务是否可用。'
    applyIssueResponse({ items: [], state: 'error', notice: '问题接口请求失败，请稍后重试。' })
    applyActionResponse({ items: [], state: 'error', notice: '动作接口请求失败，请稍后重试。' })
    applyValidationResponse({ items: [], state: 'error', notice: '验证接口请求失败，请稍后重试。' })
    applyCompareResponse({
      productCode: compareProductCode.value,
      comparisonProductCode: compareComparisonProductCode.value,
      items: [],
      state: 'error',
      notice: '竞品对比接口请求失败，请稍后重试。',
    })
    trendState.value = 'error'
    trendMessage.value = '趋势接口请求失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function reloadTrendData(): Promise<void> {
  trendState.value = 'loading'
  const response = await fetchTrends(undefined, trendAspect.value)
  applyTrendResponse(response)
}

async function reloadWordCloudData(): Promise<void> {
  wordCloudState.value = 'loading'
  const response = await fetchWordCloud(undefined, wordCloudAspect.value)
  applyWordCloudResponse(response)
}

async function createDemoAction(): Promise<void> {
  const issue = issues.value[0]
  if (!issue) {
    return
  }
  try {
    const action = await createAction({
      productCode: 'demo-earphone',
      issueId: issue.issueId,
      actionName: `处理：${issue.title}`,
      actionDesc: issue.evidenceSummary,
    })
    const [actionResponse, validationResponse] = await Promise.all([fetchActions(), fetchValidation()])
    applyActionResponse(
      actionResponse.items.length > 0
        ? actionResponse
        : {
            items: [action, ...actions.value],
            state: 'degraded',
            notice: actionResponse.notice?.trim() || '动作已创建，但列表刷新仍未返回新增记录。',
          },
    )
    applyValidationResponse(validationResponse)
  } catch {
    loadError.value = '动作登记失败，请稍后重试。'
  }
}

async function generateReportPreview(module: string): Promise<void> {
  showcaseReportPreview.value = await previewShowcaseReport(module)
}
</script>

<style scoped>
.module-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-xl);
  padding: var(--space-6);
  background:
    linear-gradient(135deg, rgba(122, 184, 255, 0.05), transparent 32%),
    linear-gradient(180deg, rgba(16, 29, 49, 0.96), rgba(8, 16, 29, 0.98));
  border: 1px solid var(--color-border-default);
  min-height: 420px;
  box-shadow: var(--shadow-panel);
  backdrop-filter: blur(18px);
}

.module-card::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), transparent 12%),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04), transparent 20% 80%, rgba(255, 255, 255, 0.02));
}

.module-card > * {
  position: relative;
  z-index: var(--z-raised);
}

.hint {
  margin: 0;
  color: var(--color-text-secondary);
}

.hint.error {
  color: var(--color-semantic-down);
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: var(--space-4);
}

.overview-grid {
  margin-top: var(--space-5);
  display: grid;
  gap: var(--space-4);
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.overview-notice {
  margin: 0 0 var(--space-4);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  border: 1px dashed var(--color-border-subtle);
  background: rgba(8, 15, 27, 0.42);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.overview-notice--degraded,
.overview-notice--error {
  border-color: rgba(255, 123, 133, 0.28);
  color: var(--color-semantic-down);
}

.metric {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  background: linear-gradient(180deg, rgba(19, 34, 58, 0.96), rgba(10, 18, 32, 0.98));
  box-shadow: var(--shadow-raised);
}

.metric::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(135deg, rgba(122, 184, 255, 0.08), transparent 46%);
}

.metric h3 {
  position: relative;
  z-index: var(--z-raised);
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  letter-spacing: 0.04em;
}

.metric p {
  position: relative;
  z-index: var(--z-raised);
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-xl);
  font-weight: 700;
  color: var(--color-text-primary);
}

@media (max-width: 980px) {
  .module-card {
    min-height: 360px;
    padding: var(--space-4);
  }
}

@media (max-width: 720px) {
  .module-card {
    min-height: 0;
    padding: var(--space-3);
    backdrop-filter: none;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }
}

html[data-motion='reduce'] .module-card,
html[data-motion='none'] .module-card {
  backdrop-filter: none;
}

</style>
