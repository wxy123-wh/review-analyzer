<template>
  <LoginGate v-if="!isAuthenticated" @enter="handleLogin" />

  <AppShellFrame v-else>
    <template #sidebar>
      <AppShellSidebar
        :items="modules"
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
              <p>{{ topIssue?.title ?? '暂无' }}</p>
            </article>
            <article class="metric">
              <h3>问题数量</h3>
              <p>{{ issues.length }}</p>
            </article>
            <article class="metric">
              <h3>已登记动作</h3>
              <p>{{ actions.length }}</p>
            </article>
          </div>
        </template>

        <IssueTable v-else-if="activeModule === 'issues'" :items="issues" />
        <CompareTable v-else-if="activeModule === 'compare'" :items="compareItems" />
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
        <ActionList v-else-if="activeModule === 'actions'" :items="actions" @create-demo="createDemoAction" />
        <ValidationList v-else-if="activeModule === 'validation'" :items="validations" />

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
  ChartLoadState,
  CompareItem,
  IssueItem,
  ServiceStatus,
  ShowcaseAgentArenaData,
  ShowcaseChaosData,
  ShowcaseExplainabilityData,
  ShowcasePipelineData,
  ShowcaseReportPreviewData,
  TrendPoint,
  TrendResponse,
  ValidationItem,
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

const allModules: Array<{ id: ModuleId; label: string; icon: string }> = [
  { id: 'overview', label: '总览', icon: 'O' },
  { id: 'issues', label: '问题', icon: 'I' },
  { id: 'compare', label: '对比', icon: 'C' },
  { id: 'trends', label: '趋势图', icon: 'T' },
  { id: 'wordcloud', label: '词云', icon: 'W' },
  { id: 'actions', label: '动作', icon: 'A' },
  { id: 'validation', label: '验证', icon: 'V' },
  { id: 'showcase-pipeline', label: '流水线', icon: 'P' },
  { id: 'showcase-agent-arena', label: '智能体', icon: 'G' },
  { id: 'showcase-explainability', label: '可解释性', icon: 'E' },
  { id: 'showcase-chaos', label: '韧性演练', icon: 'H' },
  { id: 'showcase-report-center', label: '报告中心', icon: 'R' },
]

const modules = computed(() =>
  allModules.filter((module) => chaosModuleVisible || module.id !== 'showcase-chaos'),
)

const isAuthenticated = ref(false)
const currentUser = ref('访客')
const activeModule = ref<ModuleId>('overview')
const trendAspect = ref('battery')
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

const showcasePipeline = ref<ShowcasePipelineData | null>(null)
const showcaseAgentArena = ref<ShowcaseAgentArenaData | null>(null)
const showcaseExplainability = ref<ShowcaseExplainabilityData | null>(null)
const showcaseChaos = ref<ShowcaseChaosData | null>(null)
const showcaseReportPreview = ref<ShowcaseReportPreviewData | null>(null)

const topIssue = computed(() => issues.value[0])

function applyTrendResponse(response: TrendResponse): void {
  trendAspect.value = response.aspect
  trendPoints.value = response.points
  trendState.value = response.state
  if (response.state === 'empty') {
    trendMessage.value = '暂无趋势数据，建议先初始化演示评论数据。'
  } else if (response.state === 'timeout') {
    trendMessage.value = '趋势接口请求超时，请检查网络后重试。'
  } else if (response.state === 'error') {
    trendMessage.value = '趋势接口请求失败，请稍后重试。'
  } else {
    trendMessage.value = ''
  }
}

function applyWordCloudResponse(response: WordCloudResponse): void {
  wordCloudAspect.value = response.aspect
  wordCloudItems.value = response.items
  wordCloudNotice.value = response.notice?.trim() ?? ''
  wordCloudState.value = response.state
  if (response.state === 'empty') {
    wordCloudMessage.value = '暂无词云数据，建议先初始化演示评论数据。'
  } else if (response.state === 'timeout') {
    wordCloudMessage.value = '词云接口请求超时，请检查网络后重试。'
  } else if (response.state === 'error') {
    wordCloudMessage.value = '词云接口请求失败，请稍后重试。'
  } else {
    wordCloudMessage.value = ''
  }
}

function handleLogin(payload: { username: string }): void {
  currentUser.value = payload.username
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
  } else if (moduleId === 'showcase-pipeline' && !showcasePipeline.value) {
    showcasePipeline.value = await fetchShowcasePipeline()
  } else if (moduleId === 'showcase-agent-arena' && !showcaseAgentArena.value) {
    showcaseAgentArena.value = await fetchShowcaseAgentArena()
  } else if (moduleId === 'showcase-explainability' && !showcaseExplainability.value) {
    showcaseExplainability.value = await fetchShowcaseExplainability()
  } else if (moduleId === 'showcase-chaos' && !showcaseChaos.value) {
    showcaseChaos.value = await fetchShowcaseChaos()
  }
}

async function loadDashboard(): Promise<void> {
  loading.value = true
  loadError.value = ''
  trendState.value = 'loading'
  try {
    const [backendStatus, issueList, compareList, trendResponse, validationList] = await Promise.all([
      fetchBackendHealth(),
      fetchIssues(),
      fetchCompare(),
      fetchTrends(undefined, trendAspect.value),
      fetchValidation(),
    ])
    serviceStatuses.value = [backendStatus, nlpDemoStatus()]
    issues.value = issueList
    compareItems.value = compareList
    applyTrendResponse(trendResponse)
    validations.value = validationList
  } catch {
    loadError.value = '加载失败，请检查后端服务是否可用。'
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
    actions.value = [action, ...actions.value]
    validations.value = await fetchValidation(action.actionId)
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
