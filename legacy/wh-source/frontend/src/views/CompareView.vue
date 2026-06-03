<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'

import { fetchCompareAspects } from '../api/compare'
import { fetchProducts } from '../api/meta'
import { useGlobalFilters } from '../stores/globalFilters'
import { useMobileDetect } from '../composables/useMobileDetect'

const { isMobile } = useMobileDetect()
const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const loadingCompetitors = ref(false)

const competitorId = ref(null)
const competitors = ref([])
const items = ref([])

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function fmtNum(v) {
  const n = Number(v || 0)
  return n.toFixed(3)
}

async function loadCompetitors() {
  if (!productId.value) {
    competitors.value = []
    competitorId.value = null
    return
  }
  loadingCompetitors.value = true
  try {
    const products = (await fetchProducts()) || []
    competitors.value = products.filter((p) => Boolean(p.isCompetitor) && p.id !== productId.value)
    if (competitors.value.length > 0 && !competitors.value.some((c) => c.id === competitorId.value)) {
      competitorId.value = competitors.value[0].id
    }
    if (competitors.value.length === 0) {
      competitorId.value = null
    }
  } catch (e) {
    ElMessage.error(e?.message || '加载竞品列表失败')
  } finally {
    loadingCompetitors.value = false
  }
}

async function loadCompare() {
  if (!productId.value || !competitorId.value) {
    items.value = []
    return
  }
  loading.value = true
  try {
    const res = await fetchCompareAspects({
      productId: productId.value,
      competitorId: competitorId.value,
      start: start.value,
      end: end.value,
    })
    items.value = res?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载竞品对比失败')
  } finally {
    loading.value = false
  }
}

watch(productId, loadCompetitors, { immediate: true })
watch([productId, competitorId, start, end], loadCompare, { immediate: true })

const tableRows = computed(() => items.value || [])
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never" class="card">
        <template #header>
          <div class="card__title">竞品对比</div>
        </template>

        <div class="toolbar">
          <span class="label">竞品</span>
          <el-select
            v-model="competitorId"
            :loading="loadingCompetitors"
            filterable
            clearable
            placeholder="请选择竞品（需在产品配置中标记为竞品）"
            style="width: 320px"
          >
            <el-option v-for="p in competitors" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </div>

        <el-empty v-if="competitors.length === 0" description="暂无竞品（请在产品配置中将某个产品标记为竞品）" />

        <div v-if="isMobile && competitors.length > 0" class="mobile-list">
          <div v-for="row in tableRows" :key="row.aspectId" class="mobile-card">
            <div class="mobile-card__header">
              <span class="aspect-name">{{ row.aspectName }}</span>
              <span class="diff" :class="{ neg: row?.diff?.negRate > 0, pos: row?.diff?.negRate <= 0 }">
                差值: {{ fmtRate(row?.diff?.negRate) }}
              </span>
            </div>
            
            <div class="mobile-card__body">
              <div class="compare-row">
                <div class="side self">
                  <div class="side-label">本品</div>
                  <div class="stat-main">{{ fmtRate(row?.self?.negRate) }} <span class="sub">负向</span></div>
                  <div class="stat-detail">
                     {{ fmtRate(row?.self?.posRate) }} / {{ fmtRate(row?.self?.neuRate) }}
                  </div>
                </div>
                
                <div class="vs-divider">VS</div>
                
                <div class="side comp">
                  <div class="side-label">竞品</div>
                  <div class="stat-main">{{ fmtRate(row?.competitor?.negRate) }} <span class="sub">负向</span></div>
                  <div class="stat-detail">
                     {{ fmtRate(row?.competitor?.posRate) }} / {{ fmtRate(row?.competitor?.neuRate) }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <el-table v-else-if="competitors.length > 0" :data="tableRows" v-loading="loading" style="width: 100%">
          <el-table-column prop="aspectName" label="维度" min-width="120" />

          <el-table-column label="本品负向占比" width="120">
            <template #default="{ row }">{{ fmtRate(row?.self?.negRate) }}</template>
          </el-table-column>
          <el-table-column label="竞品负向占比" width="120">
            <template #default="{ row }">{{ fmtRate(row?.competitor?.negRate) }}</template>
          </el-table-column>
          <el-table-column label="差值（本品-竞品）" width="150">
            <template #default="{ row }">{{ fmtRate(row?.diff?.negRate) }}</template>
          </el-table-column>
          <el-table-column label="归一化差值" width="120">
            <template #default="{ row }">{{ fmtNum(row?.normalized?.negRate) }}</template>
          </el-table-column>

          <el-table-column label="本品 正向/中性/负向" min-width="240">
            <template #default="{ row }">
              <span class="mono">
                {{ fmtRate(row?.self?.posRate) }} / {{ fmtRate(row?.self?.neuRate) }} / {{ fmtRate(row?.self?.negRate) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="竞品 正向/中性/负向" min-width="240">
            <template #default="{ row }">
              <span class="mono">
                {{ fmtRate(row?.competitor?.posRate) }} / {{ fmtRate(row?.competitor?.neuRate) }} /
                {{ fmtRate(row?.competitor?.negRate) }}
              </span>
            </template>
          </el-table-column>
        </el-table>

        <div class="tip">差值=本品-竞品；归一化为差值的最小-最大归一化。</div>
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
.mono {
  font-variant-numeric: tabular-nums;
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
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
.aspect-name {
  font-weight: 600;
  font-size: 14px;
}
.diff {
  font-size: 13px;
  font-weight: 600;
}
.diff.neg {
  color: var(--el-color-danger);
}
.diff.pos {
  color: var(--el-color-success);
}
.compare-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.side {
  flex: 1;
  text-align: center;
}
.vs-divider {
  width: 40px;
  text-align: center;
  font-weight: bold;
  color: var(--el-text-color-placeholder);
  font-style: italic;
}
.side-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.stat-main {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 2px;
}
.stat-main .sub {
  font-size: 12px;
  font-weight: normal;
  color: var(--el-text-color-secondary);
}
.stat-detail {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
