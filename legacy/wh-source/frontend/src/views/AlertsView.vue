<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'

import { ackAlert, fetchAlerts } from '../api/alerts'
import { useGlobalFilters } from '../stores/globalFilters'
import { useMobileDetect } from '../composables/useMobileDetect'

const { isMobile } = useMobileDetect()

const { productId } = useGlobalFilters()

const status = ref('new') // new / ack / all
const loading = ref(false)
const items = ref([])

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

async function load() {
  if (!productId.value) {
    items.value = []
    return
  }
  loading.value = true
  try {
    const res = await fetchAlerts({
      productId: productId.value,
      status: status.value === 'all' ? null : status.value,
    })
    items.value = res?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载预警失败')
  } finally {
    loading.value = false
  }
}

async function onAck(row) {
  if (!row?.id) return
  loading.value = true
  try {
    await ackAlert(row.id)
    ElMessage.success('已确认')
    await load()
  } catch (e) {
    ElMessage.error(e?.message || '确认失败')
  } finally {
    loading.value = false
  }
}

watch([productId, status], load, { immediate: true })

const tableRows = computed(() => items.value || [])

function fmtStatus(v) {
  const s = String(v || '').toLowerCase()
  if (s === 'new') return '未确认'
  if (s === 'ack') return '已确认'
  if (s === 'all') return '全部'
  return s || '-'
}

function fmtMetric(v) {
  const m = String(v || '').trim()
  if (m === 'negRate') return '负向占比'
  return m || '-'
}
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="card__title">趋势预警</div>
        </template>

        <div class="toolbar">
          <span class="label">状态</span>
          <el-select v-model="status" style="width: 200px">
            <el-option label="未确认" value="new" />
            <el-option label="已确认" value="ack" />
            <el-option label="全部" value="all" />
          </el-select>
        </div>

        <div v-if="isMobile" class="mobile-list">
          <div v-for="row in tableRows" :key="row.id" class="mobile-card">
            <div class="mobile-card__header">
              <span class="alert-id">#{{ row.id }} {{ fmtMetric(row?.metric) }}</span>
              <span class="alert-status" :class="row?.status === 'new' ? 'new' : 'ack'">
                {{ fmtStatus(row?.status) }}
              </span>
            </div>
            
            <div class="mobile-card__body">
              <div class="info-row">
                <span class="label">维度:</span>
                <span>{{ row?.aspectId ?? '-' }}</span>
              </div>
              <div class="info-row">
                <span class="label">时间:</span>
                <span>{{ row.windowStart }} ~ {{ row.windowEnd }}</span>
              </div>
              
              <div class="value-comparison">
                <div class="val-box">
                  <div class="val-label">上一期</div>
                  <div class="val-num">{{ fmtRate(row?.prevValue) }}</div>
                </div>
                <div class="arrow">→</div>
                <div class="val-box highlight">
                  <div class="val-label">本期</div>
                  <div class="val-num neg">{{ fmtRate(row?.currentValue) }}</div>
                </div>
                <div class="val-box">
                  <div class="val-label">阈值</div>
                  <div class="val-num">{{ fmtRate(row?.threshold) }}</div>
                </div>
              </div>
            </div>
            
            <div class="mobile-card__footer">
              <span class="time">{{ row.createdAt }}</span>
              <el-button 
                v-if="row?.status === 'new'" 
                size="small" 
                type="primary" 
                @click="onAck(row)"
                class="ack-btn"
              >
                确认预警
              </el-button>
            </div>
          </div>
        </div>

        <el-table v-else :data="tableRows" v-loading="loading" style="width: 100%">
          <el-table-column prop="id" label="编号" width="90" />
          <el-table-column prop="metric" label="指标" width="120">
            <template #default="{ row }">{{ fmtMetric(row?.metric) }}</template>
          </el-table-column>
          <el-table-column prop="aspectId" label="维度编号" width="110">
            <template #default="{ row }">{{ row?.aspectId ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="windowStart" label="窗口开始" width="130" />
          <el-table-column prop="windowEnd" label="窗口结束" width="130" />
          <el-table-column prop="currentValue" label="当前值" width="110">
            <template #default="{ row }">{{ fmtRate(row?.currentValue) }}</template>
          </el-table-column>
          <el-table-column prop="prevValue" label="上一窗口" width="110">
            <template #default="{ row }">{{ fmtRate(row?.prevValue) }}</template>
          </el-table-column>
          <el-table-column prop="threshold" label="阈值" width="110">
            <template #default="{ row }">{{ fmtRate(row?.threshold) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">{{ fmtStatus(row?.status) }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row?.status === 'new'" size="small" type="primary" @click="onAck(row)">确认</el-button>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="tip">规则：最近窗口的负向占比相比上一窗口上涨超过阈值，则触发预警。</div>
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
}
.label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.muted {
  color: var(--el-text-color-secondary);
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
  font-weight: 600;
}
.alert-status.new {
  color: var(--el-color-danger);
}
.alert-status.ack {
  color: var(--el-color-success);
}
.info-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 13px;
}
.info-row .label {
  color: var(--el-text-color-secondary);
}
.value-comparison {
  display: flex;
  justify-content: space-around;
  align-items: center;
  background: rgba(0,0,0,0.2);
  padding: 8px;
  border-radius: 4px;
  margin: 10px 0;
}
.val-box {
  text-align: center;
}
.val-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 2px;
}
.val-num {
  font-size: 14px;
  font-weight: 600;
}
.val-num.neg {
  color: var(--el-color-danger);
}
.arrow {
  color: var(--el-text-color-secondary);
}
.mobile-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 8px;
}
.time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
