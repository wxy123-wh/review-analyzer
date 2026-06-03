<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchEvents } from '../api/events'
import { fetchBeforeAfter } from '../api/evaluate'
import { useGlobalFilters } from '../stores/globalFilters'

import { useMobileDetect } from '../composables/useMobileDetect'

const route = useRoute()
const router = useRouter()
const { productId } = useGlobalFilters()
const { isMobile } = useMobileDetect()

const loadingEvents = ref(false)
const loading = ref(false)

const events = ref([])
const eventId = ref(null)
const report = ref(null)

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function fmtEventType(v) {
  const t = String(v || '').toLowerCase()
  if (t === 'activity') return '活动'
  if (t === 'version') return '版本'
  return t || '-'
}

function syncFromRoute() {
  const raw = route.query?.eventId
  const n = Number(raw)
  eventId.value = Number.isFinite(n) ? n : null
}

async function loadEventsList() {
  if (!productId.value) {
    events.value = []
    return
  }
  loadingEvents.value = true
  try {
    events.value = (await fetchEvents({ productId: productId.value })) || []
    if (!eventId.value && events.value.length > 0) {
      eventId.value = events.value[0].id
      router.replace({ path: '/before-after', query: { ...route.query, eventId: String(eventId.value) } })
    }
  } catch (e) {
    ElMessage.error(e?.message || '加载事件列表失败')
  } finally {
    loadingEvents.value = false
  }
}

async function loadReport() {
  if (!eventId.value) {
    report.value = null
    return
  }
  loading.value = true
  try {
    report.value = await fetchBeforeAfter({ eventId: eventId.value })
  } catch (e) {
    ElMessage.error(e?.message || '加载前后对比失败')
    report.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => route.query?.eventId,
  () => syncFromRoute(),
  { immediate: true },
)
watch(productId, loadEventsList, { immediate: true })
watch(eventId, loadReport, { immediate: true })

const aspectRows = computed(() => {
  const before = report.value?.before?.aspects || []
  const after = report.value?.after?.aspects || []
  const map = new Map()
  before.forEach((a) => {
    if (!a) return
    map.set(a.aspectId, { aspectId: a.aspectId, beforeNegRate: a.negRate, afterNegRate: 0 })
  })
  after.forEach((a) => {
    if (!a) return
    const row = map.get(a.aspectId) || { aspectId: a.aspectId, beforeNegRate: 0, afterNegRate: 0 }
    row.afterNegRate = a.negRate
    map.set(a.aspectId, row)
  })
  return Array.from(map.values()).sort((a, b) => Number(a.aspectId) - Number(b.aspectId))
})

const keywordRows = computed(() => report.value?.keywordChanges || [])
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="card__title">活动/版本前后对比</div>
        </template>

        <div class="toolbar">
          <span class="label">事件</span>
          <el-select
            v-model="eventId"
            :loading="loadingEvents"
            filterable
            clearable
            placeholder="请选择事件"
            style="width: 360px"
            @change="
              (v) => {
                if (!v) return
                router.replace({ path: '/before-after', query: { ...route.query, eventId: String(v) } })
              }
            "
          >
            <el-option
              v-for="e in events"
              :key="e.id"
              :label="`${e.name} · ${fmtEventType(e.type)} · ${e.startDate}~${e.endDate}`"
              :value="e.id"
            />
          </el-select>
        </div>

        <el-empty v-if="!eventId" description="请选择一个事件" />

        <div v-else v-loading="loading">
          <el-descriptions :column="isMobile ? 1 : 3" border>
            <el-descriptions-item label="事件">
              {{ report?.event?.name }}（{{ fmtEventType(report?.event?.type) }}）
            </el-descriptions-item>
            <el-descriptions-item label="开始日期">{{ report?.event?.startDate }}</el-descriptions-item>
            <el-descriptions-item label="结束日期">{{ report?.event?.endDate }}</el-descriptions-item>
          </el-descriptions>

          <el-divider />

          <el-row :gutter="12">
            <el-col :xs="24" :md="12">
              <el-card shadow="never">
                <template #header><div class="card__title">变更前</div></template>
                <div class="kpi">
                  <div class="kpi__row">评论数：{{ report?.before?.reviewCount ?? '-' }}</div>
                  <div class="kpi__row">负向占比：{{ fmtRate(report?.before?.negRate) }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-card shadow="never">
                <template #header><div class="card__title">变更后</div></template>
                <div class="kpi">
                  <div class="kpi__row">评论数：{{ report?.after?.reviewCount ?? '-' }}</div>
                  <div class="kpi__row">负向占比：{{ fmtRate(report?.after?.negRate) }}</div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <el-card shadow="never" class="card--mt">
            <template #header><div class="card__title">各维度负向占比</div></template>
            
            <div v-if="isMobile" class="mobile-list">
              <div v-for="row in aspectRows" :key="row.aspectId" class="mobile-card">
                <div class="mobile-card__header">
                  <span class="aspect-id">维度 #{{ row.aspectId }}</span>
                  <span class="diff" :class="{ neg: (row.afterNegRate || 0) - (row.beforeNegRate || 0) > 0, pos: (row.afterNegRate || 0) - (row.beforeNegRate || 0) <= 0 }">
                    差值: {{ fmtRate((row.afterNegRate || 0) - (row.beforeNegRate || 0)) }}
                  </span>
                </div>
                <div class="mobile-card__body compare-body">
                  <div class="compare-item">
                    <span class="lbl">前</span>
                    <span class="val">{{ fmtRate(row.beforeNegRate) }}</span>
                  </div>
                  <div class="arrow">→</div>
                  <div class="compare-item">
                    <span class="lbl">后</span>
                    <span class="val">{{ fmtRate(row.afterNegRate) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <el-table v-else :data="aspectRows" style="width: 100%">
              <el-table-column prop="aspectId" label="维度编号" width="110" />
              <el-table-column label="变更前负向占比" width="160">
                <template #default="{ row }">{{ fmtRate(row.beforeNegRate) }}</template>
              </el-table-column>
              <el-table-column label="变更后负向占比" width="160">
                <template #default="{ row }">{{ fmtRate(row.afterNegRate) }}</template>
              </el-table-column>
              <el-table-column label="差值" width="140">
                <template #default="{ row }">{{ fmtRate((row.afterNegRate || 0) - (row.beforeNegRate || 0)) }}</template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="card--mt">
            <template #header><div class="card__title">关键词变化</div></template>
            
            <div v-if="isMobile" class="mobile-list">
              <div v-for="row in keywordRows" :key="row.keyword" class="mobile-card">
                <div class="mobile-card__header">
                  <span class="keyword">{{ row.keyword }}</span>
                  <span class="diff">差值: {{ row.diff }}</span>
                </div>
                <div class="mobile-card__body compare-body">
                  <div class="compare-item">
                    <span class="lbl">前</span>
                    <span class="val">{{ row.beforeFreq }}</span>
                  </div>
                  <div class="arrow">→</div>
                  <div class="compare-item">
                    <span class="lbl">后</span>
                    <span class="val">{{ row.afterFreq }}</span>
                  </div>
                </div>
              </div>
            </div>

            <el-table v-else :data="keywordRows" style="width: 100%">
              <el-table-column prop="keyword" label="关键词" min-width="160" />
              <el-table-column prop="beforeFreq" label="变更前" width="120" />
              <el-table-column prop="afterFreq" label="变更后" width="120" />
              <el-table-column prop="diff" label="差值" width="120" />
            </el-table>
          </el-card>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.card__title {
  font-weight: 600;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.card--mt {
  margin-top: 12px;
}
.kpi__row {
  font-variant-numeric: tabular-nums;
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
.mobile-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
  font-size: 14px;
}
.diff {
  font-size: 13px;
}
.diff.neg {
  color: var(--el-color-danger);
}
.diff.pos {
  color: var(--el-color-success);
}
.compare-body {
  display: flex;
  justify-content: space-around;
  align-items: center;
  background: rgba(0,0,0,0.2);
  padding: 8px;
  border-radius: 4px;
}
.compare-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.compare-item .lbl {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 2px;
}
.compare-item .val {
  font-size: 14px;
  font-weight: 600;
}
.arrow {
  color: var(--el-text-color-secondary);
}
</style>
