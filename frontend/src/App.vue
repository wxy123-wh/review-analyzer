<template>
  <LoginGate v-if="!isAuthenticated" @enter="handleLogin" />

  <div v-else class="shell">
    <aside data-testid="narrow-sidebar" class="sidebar">
      <div class="brand">WH</div>
      <nav class="nav">
        <button
          v-for="module in modules"
          :key="module.id"
          :data-testid="`nav-${module.id}`"
          type="button"
          class="nav-item"
          :class="{ active: activeModule === module.id }"
          @click="activateModule(module.id)"
        >
          <span class="dot">{{ module.icon }}</span>
          <span class="label">{{ module.label }}</span>
        </button>
      </nav>
    </aside>

    <main class="content">
      <header class="hero">
        <h1>蓝牙耳机评论改进决策系统</h1>
        <p>V1.5 演示扩展：登录互动、智能体协同、流水线编排、混沌演练、可解释性分析、报告中心</p>
        <span class="user-chip">当前用户：{{ currentUser }}</span>
      </header>

      <section class="module-card">
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
        <TrendList v-else-if="activeModule === 'trends'" :aspect="trendAspect" :points="trendPoints" />
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
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import ActionList from './components/ActionList.vue'
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
  nlpDemoStatus,
  previewShowcaseReport,
} from './api/client'
import type {
  ActionItem,
  CompareItem,
  IssueItem,
  ServiceStatus,
  ShowcaseAgentArenaData,
  ShowcaseChaosData,
  ShowcaseExplainabilityData,
  ShowcasePipelineData,
  ShowcaseReportPreviewData,
  TrendPoint,
  ValidationItem,
} from './types/domain'

type ModuleId =
  | 'overview'
  | 'issues'
  | 'compare'
  | 'trends'
  | 'actions'
  | 'validation'
  | 'showcase-pipeline'
  | 'showcase-agent-arena'
  | 'showcase-explainability'
  | 'showcase-chaos'
  | 'showcase-report-center'

const modules: Array<{ id: ModuleId; label: string; icon: string }> = [
  { id: 'overview', label: '总览', icon: 'O' },
  { id: 'issues', label: '问题', icon: 'I' },
  { id: 'compare', label: '对比', icon: 'C' },
  { id: 'trends', label: '趋势', icon: 'T' },
  { id: 'actions', label: '动作', icon: 'A' },
  { id: 'validation', label: '验证', icon: 'V' },
  { id: 'showcase-pipeline', label: '流水线', icon: 'P' },
  { id: 'showcase-agent-arena', label: '智能体', icon: 'G' },
  { id: 'showcase-explainability', label: '可解释性', icon: 'E' },
  { id: 'showcase-chaos', label: '混沌演练', icon: 'H' },
  { id: 'showcase-report-center', label: '报告中心', icon: 'R' },
]

const isAuthenticated = ref(false)
const currentUser = ref('访客')
const activeModule = ref<ModuleId>('overview')
const trendAspect = ref('battery')

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
  if (moduleId === 'showcase-pipeline' && !showcasePipeline.value) {
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
    trendPoints.value = trendResponse.points
    validations.value = validationList
  } catch {
    loadError.value = '加载失败，请检查后端服务是否可用。'
  } finally {
    loading.value = false
  }
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
:root {
  --bg-main: #eef4f8;
  --bg-card: rgba(255, 255, 255, 0.92);
  --line: #d0dfeb;
  --ink: #22323f;
  --muted: #587083;
  --brand: #1f84af;
  --brand-soft: #d5eefd;
}

.shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 104px 1fr;
  background:
    radial-gradient(circle at 10% 10%, #d7f2ea 0%, transparent 35%),
    radial-gradient(circle at 90% 20%, #dfeeff 0%, transparent 30%),
    linear-gradient(180deg, #f7fbff, #eef6fc);
  color: var(--ink);
  font-family: 'Segoe UI', 'PingFang SC', sans-serif;
}

.sidebar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  padding: 18px 10px;
  border-right: 1px solid #c5dbee;
  background: rgba(244, 250, 255, 0.92);
  backdrop-filter: blur(6px);
}

.brand {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-weight: 800;
  letter-spacing: 1px;
  background: linear-gradient(140deg, #1f84af, #2cc1b5);
  color: #fff;
}

.nav {
  width: 100%;
  display: grid;
  gap: 8px;
}

.nav-item {
  border: 1px solid transparent;
  background: transparent;
  border-radius: 12px;
  padding: 8px 4px;
  color: #36596d;
  cursor: pointer;
  display: grid;
  justify-items: center;
  gap: 4px;
}

.nav-item:hover {
  background: rgba(59, 153, 197, 0.11);
}

.nav-item.active {
  border-color: #acd7ef;
  background: var(--brand-soft);
}

.dot {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  background: rgba(31, 132, 175, 0.14);
}

.label {
  font-size: 12px;
}

.content {
  padding: 26px 28px;
}

.hero {
  display: grid;
  gap: 6px;
}

.hero h1 {
  margin: 0;
  font-size: 32px;
}

.hero p {
  margin: 0;
  color: var(--muted);
}

.user-chip {
  width: fit-content;
  margin-top: 6px;
  border-radius: 999px;
  border: 1px solid #aed6ea;
  background: rgba(255, 255, 255, 0.82);
  color: #1f5d7b;
  font-size: 12px;
  padding: 6px 12px;
}

.module-card {
  margin-top: 20px;
  border-radius: 16px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid #d2e2ee;
  min-height: 360px;
}

.hint {
  margin: 0;
  color: #5f7482;
}

.hint.error {
  color: #9a3e3e;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 12px;
}

.overview-grid {
  margin-top: 14px;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.metric {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px;
  background: var(--bg-card);
}

.metric h3 {
  margin: 0;
  font-size: 13px;
  color: #4f6675;
}

.metric p {
  margin: 8px 0 0;
  font-size: 18px;
  font-weight: 700;
}

@media (max-width: 980px) {
  .shell {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }

  .sidebar {
    border-right: 0;
    border-bottom: 1px solid #c5dbee;
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    padding: 10px 12px;
  }

  .nav {
    width: auto;
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .nav-item {
    width: 52px;
    padding: 6px 2px;
  }

  .label {
    font-size: 10px;
  }

  .content {
    padding: 18px;
  }

  .hero h1 {
    font-size: 24px;
  }
}
</style>
