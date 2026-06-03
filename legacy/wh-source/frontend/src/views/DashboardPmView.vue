<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchClusters } from '../api/analysis'
import { fetchSuggestions } from '../api/decision'
import { fetchDashboardOverview } from '../api/dashboard'
import { fetchEvents } from '../api/events'
import { fetchBeforeAfter } from '../api/evaluate'
import { useGlobalFilters } from '../stores/globalFilters'
import { useMobileDetect } from '../composables/useMobileDetect'

const router = useRouter()
const { productId, start, end } = useGlobalFilters()
const { isMobile } = useMobileDetect()

const loading = ref(false)
const clusters = ref([])
const overview = ref(null)
const suggestions = ref([])

const events = ref([])
const eventId = ref(null)
const beforeAfter = ref(null)
const loadingBeforeAfter = ref(false)

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function fmtLevel(v) {
  const s = String(v || '').toUpperCase()
  if (s === 'ASPECT') return '维度'
  if (s === 'KEYWORD') return '关键词'
  return s || '-'
}

function fmtEventType(v) {
  const t = String(v || '').toLowerCase()
  if (t === 'activity') return '活动'
  if (t === 'version') return '版本'
  return t || '-'
}

async function loadMain() {
  if (!productId.value) {
    clusters.value = []
    overview.value = null
    suggestions.value = []
    return
  }
  loading.value = true
  try {
    const [clusterRes, overviewRes, suggestionRes] = await Promise.all([
      fetchClusters({ productId: productId.value, start: start.value, end: end.value }),
      fetchDashboardOverview({ productId: productId.value, start: start.value, end: end.value }),
      fetchSuggestions({ productId: productId.value, start: start.value, end: end.value }),
    ])
    clusters.value = clusterRes?.items || []
    overview.value = overviewRes
    suggestions.value = suggestionRes?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载看板失败')
  } finally {
    loading.value = false
  }
}

async function loadEventsList() {
  if (!productId.value) {
    events.value = []
    eventId.value = null
    return
  }
  try {
    events.value = (await fetchEvents({ productId: productId.value })) || []
    if (!eventId.value && events.value.length > 0) {
      eventId.value = events.value[0].id
    }
  } catch (e) {
    // ignore
  }
}

async function loadBeforeAfter() {
  if (!eventId.value) {
    beforeAfter.value = null
    return
  }
  loadingBeforeAfter.value = true
  try {
    beforeAfter.value = await fetchBeforeAfter({ eventId: eventId.value })
  } catch (e) {
    beforeAfter.value = null
  } finally {
    loadingBeforeAfter.value = false
  }
}

watch([productId, start, end], loadMain, { immediate: true })
watch(productId, loadEventsList, { immediate: true })
watch(eventId, loadBeforeAfter, { immediate: true })

const topClusters = computed(() =>
  (clusters.value || [])
    .slice()
    .sort((a, b) => Number(b.negRate || 0) - Number(a.negRate || 0))
    .slice(0, 5),
)

const topPriorities = computed(() => (overview.value?.topPriorities || []).slice(0, 8))
const topSuggestions = computed(() => (suggestions.value || []).slice(0, 5))

function openCluster(id) {
  if (!id) return
  router.push({ path: `/clusters/${id}` })
}

function openReview(reviewId) {
  if (!reviewId) return
  router.push({ path: '/reviews', query: { reviewId: String(reviewId) } })
}

function openLink(path) {
  router.push(path)
}
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <div class="headerLinks">
        <el-button @click="openLink('/clusters')">聚类</el-button>
        <el-button @click="openLink('/suggestions')">建议</el-button>
        <el-button @click="openLink('/events')">创建活动</el-button>
        <el-button type="primary" @click="openLink('/before-after')">前后对比</el-button>
      </div>

      <el-row :gutter="12" class="main-row">
        <el-col :xs="24" :lg="12" class="col-left">
          <el-card shadow="never" v-loading="loading">
            <template #header><div class="card__title">重点聚类</div></template>
            
            <!-- 移动端视图 -->
            <div v-if="isMobile" class="mobile-list">
              <div 
                v-for="row in topClusters" 
                :key="row.id" 
                class="mobile-card"
                @click="openCluster(row?.id)"
              >
                <div class="mobile-card__header">
                  <span class="mobile-card__id">#{{ row.id }}</span>
                  <span class="mobile-card__metric neg">{{ fmtRate(row.negRate) }} 负向</span>
                </div>
                <div class="mobile-card__body">
                  <div class="mobile-card__row">
                    <span class="label">规模:</span>
                    <span class="value">{{ row.size }}</span>
                  </div>
                  <div class="tags">
                    <el-tag v-for="t in (row.topTerms || []).slice(0, 5)" :key="t" size="small" effect="plain" class="mini-tag">
                      {{ t }}
                    </el-tag>
                  </div>
                </div>
              </div>
            </div>

            <!-- 桌面端视图 -->
            <el-table v-else :data="topClusters" style="width: 100%" @row-click="(row) => openCluster(row?.id)">
              <el-table-column prop="id" label="编号" width="90" align="right" header-align="right" />
              <el-table-column prop="size" label="规模" width="90" align="right" header-align="right" />
              <el-table-column prop="negRate" label="负向占比" width="110" align="right" header-align="right">
                <template #default="{ row }">{{ fmtRate(row.negRate) }}</template>
              </el-table-column>
              <el-table-column label="高频词" min-width="220">
                <template #default="{ row }">
                  <div class="tags">
                    <el-tag v-for="t in (row.topTerms || []).slice(0, 6)" :key="t" size="small" effect="plain">
                      {{ t }}
                    </el-tag>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="card--mt" v-loading="loading">
            <template #header><div class="card__title">优先级问题</div></template>
            
            <!-- 移动端视图 -->
            <div v-if="isMobile" class="mobile-list">
              <div v-for="row in topPriorities" :key="row.name" class="mobile-card">
                <div class="mobile-card__header">
                  <span class="priority-badge">{{ fmtLevel(row.level) }}</span>
                  <span class="mobile-card__metric neg">{{ fmtRate(row.negRate) }}</span>
                </div>
                <div class="mobile-card__title-row">{{ row.name }}</div>
                <div class="mobile-card__footer">
                  <span class="label">评论量: {{ row.volume }}</span>
                </div>
              </div>
            </div>

            <!-- 桌面端视图 -->
            <el-table v-else :data="topPriorities" style="width: 100%">
              <el-table-column prop="level" label="层级" width="110">
                <template #default="{ row }">{{ fmtLevel(row.level) }}</template>
              </el-table-column>
              <el-table-column prop="name" label="名称" min-width="180" />
              <el-table-column prop="negRate" label="负向占比" width="110" align="right" header-align="right">
                <template #default="{ row }">{{ fmtRate(row.negRate) }}</template>
              </el-table-column>
              <el-table-column prop="volume" label="评论量" width="110" align="right" header-align="right" />
            </el-table>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="12" class="col-right">
          <el-card shadow="never" v-loading="loading">
            <template #header><div class="card__title">改进建议</div></template>
            
            <!-- 移动端视图 -->
            <div v-if="isMobile" class="mobile-list">
              <div v-for="(row, idx) in topSuggestions" :key="idx" class="mobile-card">
                <div class="suggestion-text">{{ row.suggestionText }}</div>
                <div class="mobile-card__footer">
                  <span class="label">证据评论:</span>
                  <div class="evidence">
                    <el-link
                      v-for="ev in (row.evidence || []).slice(0, 3)"
                      :key="ev.reviewId"
                      type="primary"
                      :underline="false"
                      @click="openReview(ev.reviewId)"
                    >
                      #{{ ev.reviewId }}
                    </el-link>
                  </div>
                </div>
              </div>
            </div>

            <!-- 桌面端视图 -->
            <el-table v-else :data="topSuggestions" style="width: 100%">
              <el-table-column prop="suggestionText" label="建议" min-width="260" />
              <el-table-column label="证据" width="220">
                <template #default="{ row }">
                  <div class="evidence">
                    <el-link
                      v-for="ev in (row.evidence || []).slice(0, 3)"
                      :key="ev.reviewId"
                      type="primary"
                      :underline="false"
                      @click="openReview(ev.reviewId)"
                    >
                      {{ ev.reviewId }}
                    </el-link>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="card--mt" v-loading="loadingBeforeAfter">
            <template #header><div class="card__title">活动/版本前后对比</div></template>

            <div class="toolbar">
              <span class="label">事件</span>
              <el-select v-model="eventId" filterable clearable placeholder="请选择事件" style="width: 320px">
                <el-option
                  v-for="e in events"
                  :key="e.id"
                  :label="`${e.name} · ${fmtEventType(e.type)} · ${e.startDate}~${e.endDate}`"
                  :value="e.id"
                />
              </el-select>
              <el-button type="primary" :disabled="!eventId" @click="openLink(`/before-after?eventId=${eventId}`)">详情</el-button>
            </div>

            <el-empty v-if="!eventId" description="请先创建并选择一个事件" />
            <div v-else class="ba">
              <div class="ba__row">
                变更前负向占比：<b>{{ fmtRate(beforeAfter?.before?.negRate) }}</b>
              </div>
              <div class="ba__row">
                变更后负向占比：<b>{{ fmtRate(beforeAfter?.after?.negRate) }}</b>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.headerLinks {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.main-row {
  align-items: stretch;
}
.col-left, .col-right {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.card__title {
  font-weight: 600;
}
.card--mt {
  /* margin-top managed by flex gap now */
  margin-top: 0; 
}
.el-card {
  display: flex;
  flex-direction: column;
}
:deep(.el-card__body) {
  flex: 1;
  padding: 16px; /* more compact padding */
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.ba__row {
  line-height: 1.9;
}

/* 移动端卡片样式 */
.mobile-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mobile-card {
  background: var(--neu-bg-light);
  border-radius: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
}
.mobile-card:active {
  background: var(--neu-bg-base);
}
.mobile-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.mobile-card__id {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.mobile-card__metric.neg {
  color: var(--el-color-danger);
  font-weight: 600;
}
.mobile-card__body {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.mobile-card__row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}
.mini-tag {
  margin-right: 4px;
  margin-bottom: 4px;
}
.priority-badge {
  background: var(--neu-bg-base);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.mobile-card__title-row {
  margin-bottom: 8px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}
.mobile-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.suggestion-text {
  margin-bottom: 12px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
}

@media (max-width: 768px) {
  .headerLinks {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 4px; /* scrollbar space */
    -webkit-overflow-scrolling: touch;
  }
  .headerLinks .el-button {
    flex-shrink: 0;
  }
}
</style>
