<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchKeywords, fetchTopics } from '../api/analysis'
import { fetchCompareAspects } from '../api/compare'
import { fetchProducts } from '../api/meta'
import { useGlobalFilters } from '../stores/globalFilters'
import { useMobileDetect } from '../composables/useMobileDetect'

const router = useRouter()
const { productId, start, end } = useGlobalFilters()
const { isMobile } = useMobileDetect()

const loading = ref(false)
const keywords = ref([])
const topics = ref([])

const competitorId = ref(null)
const competitors = ref([])
const compareItems = ref([])
const loadingCompare = ref(false)

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

async function loadKeywordsAndTopics() {
  if (!productId.value) {
    keywords.value = []
    topics.value = []
    return
  }
  loading.value = true
  try {
    const [kwRes, topicRes] = await Promise.all([
      fetchKeywords({ productId: productId.value, start: start.value, end: end.value, topN: 120 }),
      fetchTopics({ productId: productId.value, start: start.value, end: end.value }),
    ])
    keywords.value = kwRes?.items || []
    topics.value = topicRes?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载看板失败')
  } finally {
    loading.value = false
  }
}

async function loadCompetitors() {
  if (!productId.value) {
    competitors.value = []
    competitorId.value = null
    return
  }
  try {
    const products = (await fetchProducts()) || []
    competitors.value = products.filter((p) => Boolean(p.isCompetitor) && p.id !== productId.value)
    if (competitors.value.length > 0 && !competitors.value.some((c) => c.id === competitorId.value)) {
      competitorId.value = competitors.value[0].id
    }
  } catch (e) {
    competitors.value = []
    competitorId.value = null
  }
}

async function loadCompare() {
  if (!productId.value || !competitorId.value) {
    compareItems.value = []
    return
  }
  loadingCompare.value = true
  try {
    const res = await fetchCompareAspects({
      productId: productId.value,
      competitorId: competitorId.value,
      start: start.value,
      end: end.value,
    })
    compareItems.value = res?.items || []
  } catch (e) {
    compareItems.value = []
  } finally {
    loadingCompare.value = false
  }
}

watch([productId, start, end], loadKeywordsAndTopics, { immediate: true })
watch(productId, loadCompetitors, { immediate: true })
watch([productId, competitorId, start, end], loadCompare, { immediate: true })

const posKeywords = computed(() => {
  const items = (keywords.value || []).map((k) => {
    const posScore = Number(k.freq || 0) - Number(k.negFreq || 0)
    return { ...k, posScore }
  })
  return items
    .filter((k) => k.posScore > 0)
    .sort((a, b) => b.posScore - a.posScore)
    .slice(0, 15)
})

const topTopics = computed(() => (topics.value || []).slice(0, 6))
const comparePreview = computed(() => (compareItems.value || []).slice(0, 8))

function openLink(path) {
  router.push(path)
}
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <div class="headerLinks">
        <el-button @click="openLink('/analysis')">维度分析</el-button>
        <el-button @click="openLink('/topics')">主题</el-button>
        <el-button type="primary" @click="openLink('/compare')">竞品对比</el-button>
      </div>

      <el-row :gutter="12">
        <el-col :xs="24" :lg="12">
          <el-card shadow="never" v-loading="loading">
            <template #header><div class="card__title">正向关键词（简化）</div></template>
            
            <!-- 移动端视图 -->
            <div v-if="isMobile" class="mobile-list">
              <div v-for="row in posKeywords" :key="row.keyword" class="mobile-card">
                <div class="mobile-card__header">
                  <span class="keyword-text">{{ row.keyword }}</span>
                  <span class="mobile-card__metric pos">得分: {{ row.posScore }}</span>
                </div>
                <div class="mobile-card__footer">
                  <span>总频次: {{ row.freq }}</span>
                  <span>负向: {{ row.negFreq }}</span>
                </div>
              </div>
            </div>

            <!-- 桌面端视图 -->
            <el-table v-else :data="posKeywords" style="width: 100%">
              <el-table-column prop="keyword" label="关键词" min-width="160" />
              <el-table-column prop="freq" label="总频次" width="110" />
              <el-table-column prop="negFreq" label="负向频次" width="110" />
              <el-table-column prop="posScore" label="正向得分" width="120" />
            </el-table>
            <div class="tip">正向得分=总频次-负向频次（简化计算，用于近似正向热词）。</div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="12">
          <el-card shadow="never" v-loading="loading">
            <template #header><div class="card__title">主题分布（前 6）</div></template>
            
            <!-- 移动端视图 -->
            <div v-if="isMobile" class="mobile-list">
              <div v-for="row in topTopics" :key="row.topicId" class="mobile-card">
                <div class="mobile-card__header">
                  <span class="topic-id">主题 #{{ row.topicId }}</span>
                  <span class="mobile-card__metric">权重: {{ fmtRate(row.weight) }}</span>
                </div>
                <div class="tags">
                  <el-tag v-for="w in (row.topWords || []).slice(0, 10)" :key="w" size="small" effect="plain" class="mini-tag">
                    {{ w }}
                  </el-tag>
                </div>
              </div>
            </div>

            <!-- 桌面端视图 -->
            <el-table v-else :data="topTopics" style="width: 100%">
              <el-table-column prop="topicId" label="主题编号" width="90" />
              <el-table-column label="权重" width="110">
                <template #default="{ row }">{{ fmtRate(row.weight) }}</template>
              </el-table-column>
              <el-table-column label="高频词" min-width="220">
                <template #default="{ row }">
                  <div class="tags">
                    <el-tag v-for="w in (row.topWords || []).slice(0, 6)" :key="w" size="small" effect="plain">{{ w }}</el-tag>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="card--mt" v-loading="loadingCompare">
            <template #header><div class="card__title">竞品对比预览</div></template>
            <div class="toolbar">
              <span class="label">竞品</span>
              <el-select v-model="competitorId" filterable clearable placeholder="选择竞品" style="width: 260px">
                <el-option v-for="p in competitors" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
              <el-button type="primary" :disabled="!competitorId" @click="openLink('/compare')">详情</el-button>
            </div>
            <el-empty v-if="competitors.length === 0" description="暂无竞品（请在产品配置中将某个产品标记为竞品）" />
            
            <template v-else>
              <!-- 移动端视图 -->
              <div v-if="isMobile" class="mobile-list">
                <div v-for="row in comparePreview" :key="row.aspectName" class="mobile-card">
                  <div class="mobile-card__header">
                    <span class="aspect-name">{{ row.aspectName }}</span>
                    <span class="mobile-card__metric" :class="{ neg: Number(row?.diff?.negRate) > 0, pos: Number(row?.diff?.negRate) < 0 }">
                      差值: {{ fmtRate(row?.diff?.negRate) }}
                    </span>
                  </div>
                  <div class="mobile-card__footer">
                    <span>归一化: {{ Number(row?.normalized?.negRate || 0).toFixed(3) }}</span>
                  </div>
                </div>
              </div>

              <!-- 桌面端视图 -->
              <el-table v-else :data="comparePreview" style="width: 100%">
                <el-table-column prop="aspectName" label="维度" min-width="120" />
                <el-table-column label="负向占比差值" width="140">
                  <template #default="{ row }">{{ fmtRate(row?.diff?.negRate) }}</template>
                </el-table-column>
                <el-table-column label="归一化差值" width="120">
                  <template #default="{ row }">{{ Number(row?.normalized?.negRate || 0).toFixed(3) }}</template>
                </el-table-column>
              </el-table>
            </template>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.headerLinks {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.card__title {
  font-weight: 600;
}
.card--mt {
  margin-top: 12px;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
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
.keyword-text, .aspect-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.topic-id {
  font-weight: 500;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.mobile-card__metric {
  font-weight: 600;
  font-size: 13px;
}
.mobile-card__metric.pos {
  color: var(--el-color-success);
}
.mobile-card__metric.neg {
  color: var(--el-color-danger);
}
.mobile-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 8px;
}
.mini-tag {
  margin-right: 4px;
  margin-bottom: 4px;
}

@media (max-width: 768px) {
  .headerLinks {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 4px;
    -webkit-overflow-scrolling: touch;
  }
  .headerLinks .el-button {
    flex-shrink: 0;
  }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .toolbar .el-select {
    width: 100% !important;
  }
  .toolbar .el-button {
    width: 100%;
  }
  .label {
    margin-bottom: 4px;
  }
}
</style>
