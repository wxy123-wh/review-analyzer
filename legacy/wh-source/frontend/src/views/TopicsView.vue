<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchTopics } from '../api/analysis'
import { useGlobalFilters } from '../stores/globalFilters'
import { useMobileDetect } from '../composables/useMobileDetect'

const { isMobile } = useMobileDetect()

const router = useRouter()
const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const topicCount = ref(0)
const topics = ref([])

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function openReview(reviewId) {
  if (!reviewId) return
  router.push({ path: '/reviews', query: { reviewId: String(reviewId) } })
}

async function load() {
  if (!productId.value) {
    topicCount.value = 0
    topics.value = []
    return
  }
  loading.value = true
  try {
    const res = await fetchTopics({ productId: productId.value, start: start.value, end: end.value })
    topicCount.value = Number(res?.topicCount || 0)
    topics.value = res?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载主题失败')
  } finally {
    loading.value = false
  }
}

watch([productId, start, end], load, { immediate: true })

const tableRows = computed(() => topics.value || [])
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never" v-loading="loading">
        <template #header>
          <div class="card__title">主题分布（{{ topicCount }}）</div>
        </template>

        <div v-if="isMobile" class="mobile-list">
          <div v-for="row in tableRows" :key="row.topicId" class="mobile-card">
            <div class="mobile-card__header">
              <span class="topic-id">主题 #{{ row.topicId }}</span>
              <span class="topic-weight">权重: {{ fmtRate(row.weight) }}</span>
            </div>
            
            <div class="mobile-card__body">
              <div class="tags">
                <el-tag v-for="w in (row.topWords || []).slice(0, 10)" :key="w" size="small" effect="plain" class="mini-tag">
                  {{ w }}
                </el-tag>
              </div>
            </div>
            
            <div class="mobile-card__footer">
              <span class="label">证据:</span>
              <div class="evidence">
                <el-link
                  v-for="rid in (row.evidenceReviewIds || []).slice(0, 6)"
                  :key="rid"
                  type="primary"
                  :underline="false"
                  @click="openReview(rid)"
                >
                  #{{ rid }}
                </el-link>
              </div>
            </div>
          </div>
        </div>

        <el-table v-else :data="tableRows" style="width: 100%">
          <el-table-column prop="topicId" label="主题编号" width="90" />

          <el-table-column label="权重" width="120">
            <template #default="{ row }">{{ fmtRate(row.weight) }}</template>
          </el-table-column>

          <el-table-column label="高频词" min-width="320">
            <template #default="{ row }">
              <div class="tags">
                <el-tag v-for="w in row.topWords || []" :key="w" size="small" effect="plain">{{ w }}</el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="证据" min-width="260">
            <template #default="{ row }">
              <div class="evidence">
                <el-link
                  v-for="rid in (row.evidenceReviewIds || []).slice(0, 8)"
                  :key="rid"
                  type="primary"
                  :underline="false"
                  @click="openReview(rid)"
                >
                  {{ rid }}
                </el-link>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="tip">点击证据中的评论编号可直接打开评论详情（在「评论」页）。</div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.card__title {
  font-weight: 600;
}
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
}
.topic-id {
  font-weight: 600;
  font-size: 14px;
}
.topic-weight {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.mini-tag {
  margin-right: 4px;
  margin-bottom: 4px;
}
.mobile-card__footer {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
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
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
