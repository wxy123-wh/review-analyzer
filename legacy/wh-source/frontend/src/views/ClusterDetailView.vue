<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchClusterDetail } from '../api/analysis'
import { fetchAiSummary } from '../api/decision'
import { useMobileDetect } from '../composables/useMobileDetect'
import SentimentTag from '../components/SentimentTag.vue'

const { isMobile } = useMobileDetect()

const route = useRoute()
const router = useRouter()

const clusterId = computed(() => {
  const raw = route.params?.id
  const n = Number(raw)
  return Number.isFinite(n) ? n : null
})

const loading = ref(false)
const detail = ref(null)
const aiLoading = ref(false)
const aiSummary = ref(null)

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function goBack() {
  router.push('/clusters')
}

function openReview(reviewId) {
  if (!reviewId) return
  router.push({ path: '/reviews', query: { reviewId: String(reviewId) } })
}

async function load() {
  if (!clusterId.value) {
    detail.value = null
    aiSummary.value = null
    return
  }
  loading.value = true
  try {
    detail.value = await fetchClusterDetail(clusterId.value)
  } catch (e) {
    ElMessage.error(e?.message || '加载聚类详情失败')
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function analyzeWithAi() {
  if (!clusterId.value) return
  aiLoading.value = true
  aiSummary.value = null
  try {
    const result = await fetchAiSummary(clusterId.value)
    aiSummary.value = result
    ElMessage.success('AI 分析完成')
  } catch (e) {
    ElMessage.error(e?.message || 'AI 分析失败')
  } finally {
    aiLoading.value = false
  }
}

watch(clusterId, load, { immediate: true })

const representativeReviews = computed(() => detail.value?.representativeReviews || [])

</script>

<template>
  <div>
    <el-page-header @back="goBack" content="聚类详情" />

    <el-card shadow="never" v-loading="loading" class="card--mt">
      <template #header>
        <div class="card__title">聚类 #{{ detail?.id ?? '-' }}</div>
      </template>

      <el-descriptions :column="isMobile ? 1 : 3" border>
        <el-descriptions-item label="规模">{{ detail?.size ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="负向占比">{{ fmtRate(detail?.negRate) }}</el-descriptions-item>
        <el-descriptions-item label="高频词">
          <div class="tags">
            <el-tag v-for="t in detail?.topTerms || []" :key="t" size="small" effect="plain">{{ t }}</el-tag>
          </div>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <div class="card__title">代表评论（至少 5 条）</div>
      <div v-if="isMobile" class="mobile-review-list">
        <div v-for="row in representativeReviews" :key="row.id" class="mobile-review-card" @click="openReview(row.id)">
          <div class="mobile-review-header">
            <span class="review-id">#{{ row.id }}</span>
            <span class="review-time">{{ row.reviewTime }}</span>
          </div>
          <div class="mobile-review-body">
            <div class="review-summary">{{ row.contentClean }}</div>
            <div class="sentiment-row">
              <SentimentTag :value="row.overallSentiment" size="small" />
            </div>
          </div>
        </div>
      </div>

      <el-table v-else :data="representativeReviews" style="width: 100%" @row-click="(row) => openReview(row?.id)">
        <el-table-column prop="id" label="评论编号" width="110" />
        <el-table-column prop="reviewTime" label="时间" width="180" />
        <el-table-column prop="overallSentiment" label="情感" width="120">
          <template #default="{ row }">
            <SentimentTag :value="row.overallSentiment" />
          </template>
        </el-table-column>
        <el-table-column prop="contentClean" label="内容摘要" min-width="260" show-overflow-tooltip />
      </el-table>

      <div class="tip">点击行可直接打开该评论详情（在「评论」页）。</div>
    </el-card>

    <el-card shadow="never" class="card--mt">
      <template #header>
        <div class="header-row">
          <div class="card__title">AI 深度分析</div>
          <el-button
            type="primary"
            :loading="aiLoading"
            :disabled="!clusterId"
            @click="analyzeWithAi"
            size="small"
          >
            {{ aiLoading ? '分析中...' : '生成 AI 分析' }}
          </el-button>
        </div>
      </template>

      <template v-if="aiSummary">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="核心问题">
            <div class="summary-text">{{ aiSummary.summary }}</div>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <div class="card__title">优化建议</div>
        <ul class="action-items">
          <li v-for="(item, index) in aiSummary.actionItems || []" :key="index">
            {{ item }}
          </li>
        </ul>
      </template>

      <el-empty v-else description="点击上方按钮，使用 AI 分析该聚类的核心问题和优化建议" :image-size="80" />
    </el-card>
  </div>
</template>

<style scoped>
.card--mt {
  margin-top: 12px;
}
.card__title {
  font-weight: 600;
  margin-bottom: 8px;
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
.summary-text {
  line-height: 1.6;
  color: var(--el-text-color-primary);
  font-size: 14px;
}
.action-items {
  margin: 0;
  padding-left: 24px;
  line-height: 2;
}
.action-items li {
  color: var(--el-text-color-regular);
  margin-bottom: 8px;
}

/* 移动端评论列表样式 */
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
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
.review-summary {
  font-size: 14px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.sentiment-row {
  display: flex;
  justify-content: flex-end;
}
</style>
