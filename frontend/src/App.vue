<template>
  <div class="shell">
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
        <p>V1.0 闭环：同步、分析、优先级、竞品对比、趋势、动作验证</p>
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
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import ActionList from './components/ActionList.vue'
import CompareTable from './components/CompareTable.vue'
import IssueTable from './components/IssueTable.vue'
import StatusCard from './components/StatusCard.vue'
import TrendList from './components/TrendList.vue'
import ValidationList from './components/ValidationList.vue'
import {
  createAction,
  fetchBackendHealth,
  fetchCompare,
  fetchIssues,
  fetchTrends,
  fetchValidation,
  nlpPlaceholderStatus,
} from './api/client'
import type {
  ActionItem,
  CompareItem,
  IssueItem,
  ServiceStatus,
  TrendPoint,
  ValidationItem,
} from './types/domain'

type ModuleId = 'overview' | 'issues' | 'compare' | 'trends' | 'actions' | 'validation'

const modules: Array<{ id: ModuleId; label: string; icon: string }> = [
  { id: 'overview', label: '总览', icon: 'O' },
  { id: 'issues', label: '问题', icon: 'I' },
  { id: 'compare', label: '对比', icon: 'C' },
  { id: 'trends', label: '趋势', icon: 'T' },
  { id: 'actions', label: '动作', icon: 'A' },
  { id: 'validation', label: '验证', icon: 'V' },
]

const activeModule = ref<ModuleId>('overview')
const trendAspect = ref('battery')

const loading = ref(true)
const loadError = ref('')
const serviceStatuses = ref<ServiceStatus[]>([
  { name: 'Backend API', status: 'UNKNOWN' },
  nlpPlaceholderStatus(),
])
const issues = ref<IssueItem[]>([])
const compareItems = ref<CompareItem[]>([])
const trendPoints = ref<TrendPoint[]>([])
const actions = ref<ActionItem[]>([])
const validations = ref<ValidationItem[]>([])

const topIssue = computed(() => issues.value[0])

function activateModule(moduleId: ModuleId): void {
  activeModule.value = moduleId
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
    serviceStatuses.value = [backendStatus, nlpPlaceholderStatus()]
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

onMounted(async () => {
  await loadDashboard()
})
</script>

<style scoped>
:root {
  --bg-main: #f4f7f2;
  --bg-card: rgba(255, 255, 255, 0.9);
  --line: #d6e4dc;
  --ink: #23372d;
  --muted: #61776d;
  --brand: #1f6f53;
  --brand-soft: #d6ece3;
}

.shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 92px 1fr;
  background:
    radial-gradient(circle at 10% 10%, #dcefe4 0%, transparent 35%),
    radial-gradient(circle at 90% 20%, #f3e9d5 0%, transparent 30%),
    var(--bg-main);
  color: var(--ink);
  font-family: 'Segoe UI', 'PingFang SC', sans-serif;
}

.sidebar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  padding: 18px 10px;
  border-right: 1px solid #c8ddd2;
  background: rgba(244, 252, 248, 0.92);
  backdrop-filter: blur(6px);
}

.brand {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-weight: 800;
  letter-spacing: 1px;
  background: linear-gradient(140deg, #1f6f53, #38896a);
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
  color: #335146;
  cursor: pointer;
  display: grid;
  justify-items: center;
  gap: 4px;
}

.nav-item:hover {
  background: rgba(45, 120, 90, 0.08);
}

.nav-item.active {
  border-color: #b7d9cb;
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
  background: rgba(31, 111, 83, 0.15);
}

.label {
  font-size: 12px;
}

.content {
  padding: 26px 28px;
}

.hero h1 {
  margin: 0 0 8px;
  font-size: 32px;
}

.hero p {
  margin: 0;
  color: var(--muted);
}

.module-card {
  margin-top: 20px;
  border-radius: 16px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid #cfe1d8;
  min-height: 360px;
}

.hint {
  margin: 0;
  color: #6a7c73;
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
  color: #4f675d;
}

.metric p {
  margin: 8px 0 0;
  font-size: 18px;
  font-weight: 700;
}

@media (max-width: 900px) {
  .shell {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }

  .sidebar {
    border-right: 0;
    border-bottom: 1px solid #c8ddd2;
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
    width: 50px;
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
