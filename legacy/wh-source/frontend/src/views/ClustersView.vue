<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchClusters } from '../api/analysis'
import { useGlobalFilters } from '../stores/globalFilters'
import { useMobileDetect } from '../composables/useMobileDetect'

const router = useRouter()
const { isMobile } = useMobileDetect()
const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const clusters = ref([])

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function openCluster(id) {
  if (!id) return
  router.push({ path: `/clusters/${id}` })
}

function openReview(reviewId) {
  if (!reviewId) return
  router.push({ path: '/reviews', query: { reviewId: String(reviewId) } })
}

async function load() {
  if (!productId.value) {
    clusters.value = []
    return
  }
  loading.value = true
  try {
    const res = await fetchClusters({ productId: productId.value, start: start.value, end: end.value })
    clusters.value = res?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载聚类失败')
  } finally {
    loading.value = false
  }
}

watch([productId, start, end], load, { immediate: true })

const tableRows = computed(() => clusters.value || [])
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never" v-loading="loading">
        <template #header>
          <div class="card__title">问题聚类</div>
        </template>

        <!-- 移动端视图 -->
        <div v-if="isMobile" class="mobile-list">
          <div 
            v-for="row in tableRows" 
            :key="row.id" 
            class="mobile-card"
            @click="openCluster(row.id)"
          >
            <div class="mobile-card__header">
              <span class="cluster-id">聚类 #{{ row.id }}</span>
              <span class="mobile-card__metric neg">负向: {{ fmtRate(row.negRate) }}</span>
            </div>
            <div class="mobile-card__body">
              <div class="mobile-card__row">
                <span class="label">规模: {{ row.size }}</span>
              </div>
              <div class="tags">
                <el-tag v-for="t in (row.topTerms || []).slice(0, 8)" :key="t" size="small" effect="plain" class="mini-tag">
                  {{ t }}
                </el-tag>
              </div>
            </div>
            <div class="mobile-card__footer">
              <span class="label">代表评论:</span>
              <div class="evidence">
                <el-link
                  v-for="rid in (row.representativeReviewIds || []).slice(0, 4)"
                  :key="rid"
                  type="primary"
                  :underline="false"
                  @click.stop="openReview(rid)"
                >
                  #{{ rid }}
                </el-link>
              </div>
            </div>
          </div>
        </div>

        <!-- 桌面端视图 -->
        <el-table v-else :data="tableRows" style="width: 100%" @row-click="(row) => openCluster(row?.id)">
          <el-table-column prop="id" label="聚类编号" width="110" />
          <el-table-column prop="size" label="规模" width="100" />
          <el-table-column prop="negRate" label="负向占比" width="120">
            <template #default="{ row }">{{ fmtRate(row.negRate) }}</template>
          </el-table-column>

          <el-table-column label="高频词" min-width="320">
            <template #default="{ row }">
              <div class="tags">
                <el-tag v-for="t in row.topTerms || []" :key="t" size="small" effect="plain">{{ t }}</el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="代表评论" min-width="240">
            <template #default="{ row }">
              <div class="evidence">
                <el-link
                  v-for="rid in row.representativeReviewIds || []"
                  :key="rid"
                  type="primary"
                  :underline="false"
                  @click.stop="openReview(rid)"
                >
                  {{ rid }}
                </el-link>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="tip">点击行进入聚类详情；点击代表评论的编号可直接打开评论详情。</div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.card__title {
  font-weight: 600;
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
  cursor: pointer;
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
.cluster-id {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.mobile-card__metric.neg {
  color: var(--el-color-danger);
  font-weight: 600;
  font-size: 13px;
}
.mobile-card__body {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 8px;
}
.mobile-card__row {
  margin-bottom: 6px;
}
.mini-tag {
  margin-right: 4px;
  margin-bottom: 4px;
}
.mobile-card__footer {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 8px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
