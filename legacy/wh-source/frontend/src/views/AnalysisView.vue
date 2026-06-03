<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'

import { fetchAspectAnalysis, fetchKeywords, fetchTrend } from '../api/analysis'
import { useAnalysisFilters } from '../composables/useAnalysisFilters'
import EChart from '../components/EChart.vue'
import SentimentTrendCard from '../components/SentimentTrendCard.vue'
import WordCloudCard from '../components/WordCloudCard.vue'
import { useGlobalFilters } from '../stores/globalFilters'
import { useMobileDetect } from '../composables/useMobileDetect'
import { mergeMobileConfig } from '../utils/chartMobileConfig'

const { isMobile } = useMobileDetect()

const { productId, start, end, dateRange } = useGlobalFilters()

const loadingAspects = ref(false)
const loadingRight = ref(false)

const aspects = ref([])
const { selectedAspectId, selectedAspectName, ensureSelection, onAspectRowClick } = useAnalysisFilters({ aspects })

const trendSeries = ref([])
const keywords = ref([])

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

async function loadAspects() {
  if (!productId.value) {
    aspects.value = []
    ensureSelection()
    return
  }
  loadingAspects.value = true
  try {
    const res = await fetchAspectAnalysis({ productId: productId.value, start: start.value, end: end.value })
    aspects.value = res?.items || []
    ensureSelection()
  } catch (e) {
    ElMessage.error(e?.message || '加载维度分析失败')
  } finally {
    loadingAspects.value = false
  }
}

async function loadRight() {
  if (!productId.value || !selectedAspectId.value) {
    trendSeries.value = []
    keywords.value = []
    return
  }
  loadingRight.value = true
  try {
    const [trendRes, kwRes] = await Promise.all([
      fetchTrend({ productId: productId.value, aspectId: selectedAspectId.value, start: start.value, end: end.value }),
      fetchKeywords({ productId: productId.value, aspectId: selectedAspectId.value, start: start.value, end: end.value, topN: 20 }),
    ])
    trendSeries.value = trendRes?.series || []
    keywords.value = kwRes?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载趋势/关键词失败')
  } finally {
    loadingRight.value = false
  }
}

watch([productId, start, end], loadAspects, { immediate: true })
watch(selectedAspectId, loadRight, { immediate: true })

const trendOption = computed(() => {
  const series = trendSeries.value || []
  const baseOption = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['正向', '中性', '负向', '负向占比'] },
    grid: { left: 40, right: 50, top: 40, bottom: 40 },
    xAxis: { type: 'category', data: series.map((s) => s.date) },
    yAxis: [
      { type: 'value', name: '数量' },
      { type: 'value', name: '负向占比', min: 0, max: 1, axisLabel: { formatter: (v) => `${Math.round(v * 100)}%` } },
    ],
    series: [
      { name: '正向', type: 'bar', stack: 'sent', data: series.map((s) => s.pos) },
      { name: '中性', type: 'bar', stack: 'sent', data: series.map((s) => s.neu) },
      { name: '负向', type: 'bar', stack: 'sent', data: series.map((s) => s.neg) },
      { name: '负向占比', type: 'line', yAxisIndex: 1, smooth: true, data: series.map((s) => s.negRate) },
    ],
  }

  if (isMobile.value) {
    return mergeMobileConfig(baseOption, {
      grid: { right: '15%' }, // 给右侧Y轴留出空间
      legend: { bottom: 0 }
    })
  }
  return baseOption
})
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-row :gutter="12" class="main-row">
        <el-col :xs="24" :md="10" :lg="8" class="col-left">
          <el-card shadow="never" v-loading="loadingAspects" class="full-height-card">
            <template #header>
              <div class="card__title">维度列表</div>
            </template>
            <div v-if="isMobile" class="mobile-list">
              <div 
                v-for="row in aspects" 
                :key="row.aspectId" 
                class="mobile-card"
                :class="{ active: selectedAspectId === row.aspectId }"
                @click="onAspectRowClick(row)"
              >
                <div class="mobile-card__header">
                  <span class="aspect-name">{{ row.aspectName }}</span>
                  <span class="mobile-card__metric neg">{{ fmtRate(row.negRate) }} 负向</span>
                </div>
                <div class="mobile-card__body">
                  <div class="mobile-card__row">
                    <span class="label">评论量: {{ row.volume }}</span>
                    <span class="label">正向: {{ fmtRate(row.posRate) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <el-table
              v-else
              :data="aspects"
              :current-row-key="selectedAspectId"
              highlight-current-row
              row-key="aspectId"
              @row-click="onAspectRowClick"
              height="100%"
            >
              <el-table-column prop="aspectName" label="维度" min-width="120" />
              <el-table-column prop="volume" label="评论量" width="100" align="right" header-align="right" />
              <el-table-column prop="negRate" label="负向占比" width="110" align="right" header-align="right">
                <template #default="{ row }">{{ fmtRate(row.negRate) }}</template>
              </el-table-column>
              <el-table-column prop="posRate" label="正向占比" width="110" align="right" header-align="right">
                <template #default="{ row }">{{ fmtRate(row.posRate) }}</template>
              </el-table-column>
              <el-table-column prop="neuRate" label="中性占比" width="110" align="right" header-align="right">
                <template #default="{ row }">{{ fmtRate(row.neuRate) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="14" :lg="16" class="col-right">
          <el-card shadow="never" v-loading="loadingRight">
            <template #header>
              <div class="card__title">趋势 · {{ selectedAspectName || '-' }}</div>
            </template>
            <EChart :option="trendOption" height="320px" />
          </el-card>

          <el-card class="card--mt" shadow="never" v-loading="loadingRight">
            <template #header>
              <div class="card__title">关键词排行 · {{ selectedAspectName || '-' }}</div>
            </template>
            <div v-if="isMobile" class="mobile-list">
              <div v-for="row in keywords" :key="row.keyword" class="mobile-card">
                <div class="mobile-card__header">
                  <span class="keyword-text">{{ row.keyword }}</span>
                  <span class="mobile-card__metric neg">{{ row.negFreq }} 负向</span>
                </div>
                <div class="mobile-card__footer">
                  <span>总频次: {{ row.freq }}</span>
                </div>
              </div>
            </div>

            <el-table v-else :data="keywords" style="width: 100%">
              <el-table-column prop="keyword" label="关键词" min-width="160" />
              <el-table-column prop="freq" label="总频次" width="120" align="right" header-align="right" />
              <el-table-column prop="negFreq" label="负向频次" width="120" align="right" header-align="right" />
            </el-table>
            <div class="tip">排序规则：按负向频次降序，其次按总频次降序（由后端保证）。</div>
          </el-card>
        </el-col>
      </el-row>

      <el-divider class="insight-divider" content-position="left">洞察</el-divider>
      <el-row :gutter="12">
        <el-col :xs="24" :md="12">
          <WordCloudCard
            :product-id="productId"
            :date-range="dateRange"
            :aspect-id="selectedAspectId"
            :aspect-name="selectedAspectName"
          />
        </el-col>
        <el-col :xs="24" :md="12">
          <SentimentTrendCard
            :product-id="productId"
            :date-range="dateRange"
            granularity="day"
          />
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.main-row {
  align-items: stretch;
}
.col-right {
  display: flex;
  flex-direction: column;
}
.full-height-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
:deep(.full-height-card .el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0; /* remove padding for full-height table */
  overflow: hidden;
}
.card__title {
  font-weight: 600;
}
.card--mt {
  margin-top: 12px;
}
.col-right .card--mt {
  flex: 1; /* allow keywords card to take remaining space if needed */
  display: flex;
  flex-direction: column;
}
:deep(.col-right .card--mt .el-card__body) {
  flex: 1;
}
.insight-divider {
  margin-top: 24px;
  margin-bottom: 24px;
}
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

/* 移动端卡片样式 */
.mobile-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  max-height: 400px;
  overflow-y: auto;
}
.mobile-card {
  background: var(--neu-bg-light);
  border-radius: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}
.mobile-card.active {
  border-color: var(--el-color-primary);
  background: rgba(108, 155, 209, 0.1);
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
.aspect-name, .keyword-text {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.mobile-card__metric {
  font-weight: 600;
  font-size: 13px;
}
.mobile-card__metric.neg {
  color: var(--el-color-danger);
}
.mobile-card__body {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.mobile-card__row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}
.mobile-card__footer {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 768px) {
  /* 重置full-height-card在移动端的高度，避免在大屏手机上太高 */
  .full-height-card {
    height: auto;
    min-height: 300px;
  }
}
</style>
