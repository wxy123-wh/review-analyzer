<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchSuggestions } from '../api/decision'
import { useGlobalFilters } from '../stores/globalFilters'
import { useMobileDetect } from '../composables/useMobileDetect'

const { isMobile } = useMobileDetect()

const router = useRouter()
const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const items = ref([])

async function load() {
  if (!productId.value) {
    items.value = []
    return
  }
  loading.value = true
  try {
    const res = await fetchSuggestions({ productId: productId.value, start: start.value, end: end.value })
    items.value = res?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载建议失败')
  } finally {
    loading.value = false
  }
}

function openReview(reviewId) {
  if (!reviewId) return
  router.push({ path: '/reviews', query: { reviewId: String(reviewId) } })
}

watch([productId, start, end], load, { immediate: true })

const tableRows = computed(() => items.value || [])

function fmtRefType(v) {
  const s = String(v || '').toUpperCase()
  if (s === 'ASPECT') return '维度'
  if (s === 'KEYWORD') return '关键词'
  if (s === 'CLUSTER') return '聚类'
  return s || '-'
}
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never" v-loading="loading">
        <template #header>
          <div class="card__title">改进建议</div>
        </template>

        <div v-if="isMobile" class="mobile-list">
          <div v-for="row in tableRows" :key="row.id" class="mobile-card">
            <div class="mobile-card__header">
              <span class="suggestion-id">#{{ row.id }}</span>
              <span class="ref-type">{{ fmtRefType(row.refType) }} #{{ row.refId }}</span>
            </div>
            
            <div class="mobile-card__body">
              <div class="suggestion-text">{{ row.suggestionText }}</div>
            </div>
            
            <div class="mobile-card__footer">
              <span class="label">证据:</span>
              <div class="evidence">
                <el-link
                  v-for="ev in row.evidence || []"
                  :key="ev.reviewId"
                  type="primary"
                  :underline="false"
                  @click.stop="openReview(ev.reviewId)"
                >
                  #{{ ev.reviewId }}
                </el-link>
              </div>
            </div>
          </div>
        </div>

        <el-table v-else :data="tableRows" style="width: 100%">
          <el-table-column prop="id" label="编号" width="90" />
          <el-table-column prop="refType" label="关联类型" width="110">
            <template #default="{ row }">{{ fmtRefType(row.refType) }}</template>
          </el-table-column>
          <el-table-column prop="refId" label="关联编号" width="90" />
          <el-table-column prop="suggestionText" label="建议" min-width="260" />
          <el-table-column label="证据" min-width="320">
            <template #default="{ row }">
              <div class="evidence">
                <el-tooltip
                  v-for="ev in row.evidence || []"
                  :key="ev.reviewId"
                  :content="ev.snippet"
                  placement="top"
                >
                  <el-link type="primary" :underline="false" @click="openReview(ev.reviewId)">{{ ev.reviewId }}</el-link>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="tip">点击证据中的评论编号可直接打开评论详情。</div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.card__title {
  font-weight: 600;
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
}
.mobile-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
}
.suggestion-id {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.ref-type {
  color: var(--el-text-color-secondary);
}
.mobile-card__body {
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--el-text-color-primary);
  line-height: 1.5;
}
.mobile-card__footer {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
</style>
