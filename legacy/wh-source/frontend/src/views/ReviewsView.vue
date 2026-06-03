<script setup>
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchAspects, fetchPlatforms } from '../api/meta'
import { fetchReviewDetail, fetchReviews } from '../api/reviews'
import SentimentTag from '../components/SentimentTag.vue'
import { useGlobalFilters } from '../stores/globalFilters'
import { highlightHtml } from '../utils/highlight'
import { useMobileDetect } from '../composables/useMobileDetect'

const route = useRoute()
const router = useRouter()
const { isMobile } = useMobileDetect()

const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const items = ref([])
const total = ref(0)

const page = ref(1)
const pageSize = ref(20)

const platforms = ref([])
const aspects = ref([])

const filters = reactive({
  platformId: null,
  aspectId: null,
  sentiment: null,
  keyword: '',
})

const drawerOpen = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const pendingReviewId = ref(null)

function sentimentTagType(value) {
  const v = String(value || '').toUpperCase()
  if (v === 'POS') return 'success'
  if (v === 'NEG') return 'danger'
  return 'info'
}

function fmtTime(v) {
  return v || '-'
}

function fmtNum(v) {
  return Number(v || 0).toLocaleString()
}

function fmtScore(v) {
  const n = Number(v || 0)
  return n.toFixed(3)
}

async function loadMeta() {
  try {
    const [pf, asp] = await Promise.all([fetchPlatforms(), fetchAspects()])
    platforms.value = pf || []
    aspects.value = asp || []
  } catch (e) {
    ElMessage.error(e?.message || '加载下拉数据失败')
  }
}

function syncFiltersFromRoute() {
  const q = route.query || {}

  const aspectIdRaw = q.aspectId
  if (aspectIdRaw == null || aspectIdRaw === '') {
    filters.aspectId = null
  } else {
    const n = Number(aspectIdRaw)
    filters.aspectId = Number.isFinite(n) ? n : null
  }

  const keywordRaw = q.keyword
  filters.keyword = keywordRaw == null ? '' : String(keywordRaw)

  const sentimentRaw = q.sentiment
  const s = String(sentimentRaw || '').toUpperCase()
  filters.sentiment = s === 'POS' || s === 'NEU' || s === 'NEG' ? s : null

  if ('reviewId' in q) {
    const n = Number(q.reviewId)
    pendingReviewId.value = Number.isFinite(n) ? n : null
  } else {
    pendingReviewId.value = null
  }
}

function pushRouteQuery() {
  const query = { ...route.query }
  if (filters.aspectId) query.aspectId = String(filters.aspectId)
  else delete query.aspectId
  if (filters.sentiment) query.sentiment = String(filters.sentiment)
  else delete query.sentiment
  if (filters.keyword && String(filters.keyword).trim() !== '') query.keyword = String(filters.keyword).trim()
  else delete query.keyword
  delete query.reviewId
  router.replace({ path: '/reviews', query })
}

async function loadList() {
  if (!productId.value) {
    items.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const res = await fetchReviews({
      productId: productId.value,
      start: start.value,
      end: end.value,
      platformId: filters.platformId,
      aspectId: filters.aspectId,
      sentiment: filters.sentiment,
      keyword: filters.keyword,
      page: page.value,
      pageSize: pageSize.value,
    })
    items.value = res?.items || []
    total.value = Number(res?.total || 0)
    page.value = Number(res?.page || page.value)
    pageSize.value = Number(res?.pageSize || pageSize.value)
  } catch (e) {
    ElMessage.error(e?.message || '加载评论列表失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  pushRouteQuery()
  loadList()
}

function resetFilters() {
  filters.platformId = null
  filters.aspectId = null
  filters.sentiment = null
  filters.keyword = ''
  page.value = 1
  pushRouteQuery()
  loadList()
}

async function openDetailById(id, { clearRouteReviewId = false } = {}) {
  const reviewId = Number(id)
  if (!Number.isFinite(reviewId) || reviewId <= 0) return
  drawerOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await fetchReviewDetail(reviewId)
    if (clearRouteReviewId) {
      const query = { ...route.query }
      delete query.reviewId
      router.replace({ path: '/reviews', query })
    }
  } catch (e) {
    ElMessage.error(e?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function openDetail(row) {
  if (!row?.id) return
  await openDetailById(row.id)
}

const highlightTerms = computed(() => {
  const terms = []
  const kw = String(filters.keyword || '').trim()
  if (kw) terms.push(kw)
  const ars = detail.value?.aspectResults || []
  ars.forEach((a) => {
    ;(a.hitKeywords || []).forEach((k) => terms.push(k))
  })
  return terms
})

const highlightedContentClean = computed(() => highlightHtml(detail.value?.contentClean || '', highlightTerms.value))

watch([productId, start, end], () => {
  page.value = 1
  loadList()
})

watch(
  () => route.query,
  async () => {
    syncFiltersFromRoute()
    page.value = 1
    await loadList()
    if (pendingReviewId.value) {
      await openDetailById(pendingReviewId.value, { clearRouteReviewId: true })
    }
  },
  { immediate: true },
)

onMounted(() => {
  loadMeta()
})
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never" class="card">
        <template #header>
          <div class="card__title">筛选</div>
        </template>

        <el-form :inline="!isMobile" label-width="80px" :class="{ 'mobile-form': isMobile }">
          <el-form-item label="平台">
            <el-select v-model="filters.platformId" clearable placeholder="全部" :style="{ width: isMobile ? '100%' : '180px' }">
              <el-option v-for="p in platforms" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="维度">
            <el-select v-model="filters.aspectId" clearable placeholder="全部" :style="{ width: isMobile ? '100%' : '180px' }">
              <el-option v-for="a in aspects" :key="a.id" :label="a.name" :value="a.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="情感">
            <el-select v-model="filters.sentiment" clearable placeholder="全部" :style="{ width: isMobile ? '100%' : '140px' }">
              <el-option label="正向" value="POS" />
              <el-option label="中性" value="NEU" />
              <el-option label="负向" value="NEG" />
            </el-select>
          </el-form-item>

          <el-form-item label="关键词">
            <el-input
              v-model="filters.keyword"
              clearable
              placeholder="评论内容模糊匹配"
              :style="{ width: isMobile ? '100%' : '260px' }"
              @keyup.enter="search"
            />
          </el-form-item>

          <el-form-item class="form-actions">
            <el-button type="primary" @click="search" :style="{ width: isMobile ? '100%' : 'auto', flex: isMobile ? 1 : 'none' }">查询</el-button>
            <el-button @click="resetFilters" :style="{ width: isMobile ? '100%' : 'auto', flex: isMobile ? 1 : 'none' }">重置</el-button>
          </el-form-item>
        </el-form>

        <div class="tip">
          时间范围使用顶部全局筛选器（开始日期/结束日期）；当前列表接口会携带：产品、开始日期、结束日期、平台、维度、情感、关键词。
        </div>
      </el-card>

      <el-card shadow="never" class="card" v-loading="loading">
        <template #header>
          <div class="card__title">评论列表</div>
        </template>

        <!-- 移动端列表视图 -->
        <div v-if="isMobile" class="mobile-review-list">
          <div v-for="row in items" :key="row.id" class="mobile-review-card" @click="openDetail(row)">
            <div class="mobile-review-header">
              <div class="platform-info">
                <span class="platform-name">{{ row.platformName }}</span>
                <span class="review-time">{{ fmtTime(row.reviewTime) }}</span>
              </div>
              <SentimentTag :value="row.overallSentiment" size="small" />
            </div>
            
            <div class="review-content">{{ row.contentClean }}</div>
            
            <div class="mobile-review-footer">
              <div class="rating-info">评分: {{ row.rating }}</div>
              <div class="aspect-scroll">
                <el-tag
                  v-for="a in row.aspects || []"
                  :key="a.aspectId"
                  :type="sentimentTagType(a.sentiment)"
                  style="margin-right: 4px;"
                  effect="light"
                  size="small"
                >
                  {{ a.aspectName }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>

        <!-- 桌面端表格视图 -->
        <el-table v-else :data="items" style="width: 100%" @row-click="openDetail">
          <el-table-column prop="reviewTime" label="时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.reviewTime) }}</template>
          </el-table-column>
          <el-table-column prop="platformName" label="平台" width="120" />
          <el-table-column prop="rating" label="评分" width="90" />
          <el-table-column prop="overallSentiment" label="情感" width="100">
            <template #default="{ row }">
              <SentimentTag :value="row.overallSentiment" />
            </template>
          </el-table-column>
          <el-table-column prop="contentClean" label="内容摘要" min-width="260" show-overflow-tooltip />
          <el-table-column label="维度标签" min-width="220">
            <template #default="{ row }">
              <div class="tags">
                <el-tag
                  v-for="a in row.aspects || []"
                  :key="a.aspectId"
                  :type="sentimentTagType(a.sentiment)"
                  effect="light"
                  size="small"
                >
                  {{ a.aspectName }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            :pager-count="isMobile ? 5 : 7"
            :small="isMobile"
            @current-change="loadList"
            @size-change="
              () => {
                page = 1
                loadList()
              }
            "
          />
        </div>
      </el-card>

      <el-drawer v-model="drawerOpen" title="评论详情" :size="isMobile ? '100%' : '60%'">
        <div v-loading="detailLoading">
          <template v-if="detail">
            <div v-if="isMobile" class="mobile-desc">
              <div class="desc-row"><span>平台:</span> {{ detail.platformName }}</div>
              <div class="desc-row"><span>时间:</span> {{ fmtTime(detail.reviewTime) }}</div>
              <div class="desc-row"><span>评分:</span> {{ detail.rating ?? '-' }}</div>
              <div class="desc-row"><span>情感:</span> <SentimentTag :value="detail.overallSentiment" /></div>
            </div>
            
            <el-descriptions v-else :column="2" border>
              <el-descriptions-item label="平台">{{ detail.platformName }}</el-descriptions-item>
              <el-descriptions-item label="产品">{{ detail.productName }}</el-descriptions-item>
              <el-descriptions-item label="评分">{{ detail.rating ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="时间">{{ fmtTime(detail.reviewTime) }}</el-descriptions-item>
              <el-descriptions-item label="情感">
                <SentimentTag :value="detail.overallSentiment" />
              </el-descriptions-item>
              <el-descriptions-item label="综合得分">{{ fmtScore(detail.overallScore) }}</el-descriptions-item>
            </el-descriptions>

            <el-divider />

            <el-card shadow="never">
              <template #header>
                <div class="card__title">内容（清洗后）· 关键词高亮</div>
              </template>
              <div class="content" v-html="highlightedContentClean" />
            </el-card>

            <el-card shadow="never" class="card--mt">
              <template #header>
                <div class="card__title">维度结果</div>
              </template>
              <el-table :data="detail.aspectResults || []" style="width: 100%">
                <el-table-column prop="aspectName" label="维度" width="140" />
                <el-table-column label="命中关键词" min-width="220">
                  <template #default="{ row }">
                    <div class="tags">
                      <el-tag v-for="k in row.hitKeywords || []" :key="k" size="small" effect="plain">{{ k }}</el-tag>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="sentiment" label="情感" width="100">
                  <template #default="{ row }">
                    <SentimentTag :value="row.sentiment" />
                  </template>
                </el-table-column>
                <el-table-column prop="score" label="得分" width="110">
                  <template #default="{ row }">{{ fmtScore(row.score) }}</template>
                </el-table-column>
                <el-table-column prop="confidence" label="置信度" width="110">
                  <template #default="{ row }">{{ Number(row.confidence || 0).toFixed(3) }}</template>
                </el-table-column>
              </el-table>
            </el-card>

            <el-card shadow="never" class="card--mt">
              <template #header>
                <div class="card__title">原始内容</div>
              </template>
              <pre class="pre">{{ detail.contentRaw }}</pre>
            </el-card>
          </template>

          <el-empty v-else description="请选择一条评论查看详情" />
        </div>
      </el-drawer>
    </template>
  </div>
</template>

<style scoped>
.card {
  margin-bottom: 12px;
}
.card__title {
  font-weight: 600;
}
.card--mt {
  margin-top: 12px;
}
.tip {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.content {
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

<style scoped>
/* 移动端特定样式 */
.mobile-form .el-form-item {
  margin-right: 0;
  margin-bottom: 12px;
  width: 100%;
}
.mobile-form .form-actions :deep(.el-form-item__content) {
  display: flex !important;
  gap: 10px;
}
.mobile-review-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mobile-review-card {
  background: var(--neu-bg-light);
  border-radius: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}
.mobile-review-card:active {
  background: var(--neu-bg-base);
}
.mobile-review-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}
.platform-info {
  display: flex;
  flex-direction: column;
}
.platform-name {
  font-weight: 600;
  font-size: 14px;
}
.review-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.review-content {
  font-size: 14px;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}
.mobile-review-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 8px;
}
.rating-info {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-warning);
  white-space: nowrap;
}
.aspect-scroll {
  flex: 1;
  overflow-x: auto;
  white-space: nowrap;
  -webkit-overflow-scrolling: touch;
  /* hide scrollbar */
  scrollbar-width: none;
}
.aspect-scroll::-webkit-scrollbar {
  display: none;
}
.mobile-desc {
  background: var(--neu-bg-light);
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 16px;
}
.desc-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
.desc-row:last-child {
  border-bottom: none;
}
.desc-row span {
  color: var(--el-text-color-secondary);
}
</style>
