<script setup>
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, onUnmounted } from 'vue'

import { fetchProducts } from '../api/meta'
import { useGlobalFilters } from '../stores/globalFilters'
import { useAuth } from '../stores/auth'

const { productId, dateRange } = useGlobalFilters()
const { token } = useAuth()

const loading = ref(false)
const products = ref([])
const isMobile = ref(window.innerWidth < 768)

const productOptions = computed(() =>
  (products.value || []).map((p) => ({
    id: p.id,
    label: [p.name, p.brand, p.model].filter(Boolean).join(' · '),
  })),
)

// Responsive check
function updateIsMobile() {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  window.addEventListener('resize', updateIsMobile)
  if (!dateRange.value) {
    dateRange.value = lastNDaysRange(30)
  }
  // Only load products if authenticated
  if (token.value) {
    loadProducts()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', updateIsMobile)
})

const startDate = computed({
  get: () => dateRange.value?.[0] || '',
  set: (val) => {
    const end = dateRange.value?.[1] || ''
    dateRange.value = [val, end]
  }
})

const endDate = computed({
  get: () => dateRange.value?.[1] || '',
  set: (val) => {
    const start = dateRange.value?.[0] || ''
    dateRange.value = [start, val]
  }
})

function pad2(v) {
  return String(v).padStart(2, '0')
}

function fmtYmd(d) {
  const dt = d instanceof Date ? d : new Date(d)
  return `${dt.getFullYear()}-${pad2(dt.getMonth() + 1)}-${pad2(dt.getDate())}`
}

function lastNDaysRange(n = 30) {
  const days = Number(n) > 0 ? Number(n) : 30
  const endDate = new Date()
  const startDate = new Date(endDate)
  startDate.setDate(startDate.getDate() - Math.max(0, days - 1))
  return [fmtYmd(startDate), fmtYmd(endDate)]
}

async function loadProducts() {
  if (!token.value) return // Double check
  
  loading.value = true
  try {
    products.value = (await fetchProducts()) || []
    if (!productId.value && products.value.length > 0) {
      productId.value = products.value[0].id
    }
  } catch (e) {
    // Suppress 401 errors here as they are handled globally, but avoid other errors if not authed
    if (token.value) {
      ElMessage.error(e?.message || '加载产品列表失败')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="filters">
    <div class="filters__item">
      <span class="filters__label">产品</span>
      <el-select
        v-model="productId"
        :loading="loading"
        filterable
        clearable
        placeholder="选择产品"
        class="product-select"
      >
        <el-option v-for="p in productOptions" :key="p.id" :label="p.label" :value="p.id" />
      </el-select>
    </div>

    <div class="filters__item">
      <span class="filters__label">时间范围</span>
      
      <!-- Desktop: Single Date Range Picker -->
      <el-date-picker
        v-if="!isMobile"
        v-model="dateRange"
        type="daterange"
        unlink-panels
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        format="YYYY-MM-DD"
        clearable
        style="width: 320px"
      />

      <!-- Mobile: Two Separate Date Pickers stacked -->
      <div v-else class="mobile-date-pickers">
        <el-date-picker
          v-model="startDate"
          type="date"
          placeholder="开始日期"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          style="width: 100%"
        />
        <el-date-picker
          v-model="endDate"
          type="date"
          placeholder="结束日期"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          style="width: 100%"
        />
      </div>

      <span class="filters__hint" :class="{ 'mobile-hint': isMobile }">默认显示最近30天</span>
    </div>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.filters__item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filters__label {
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: nowrap;
}

.filters__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.product-select {
  width: 280px;
}

.mobile-date-pickers {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 160px; /* Adjust width appropriately or let it grow */
}

.mobile-hint {
  display: none; /* Hide hint on mobile to save space if needed, or keep it */
}

@media (max-width: 767px) {
  .filters {
    gap: 12px;
  }
  
  .filters__item {
    align-items: flex-start; /* Align label to top for stacked inputs */
    width: 100%; /* Full width for better mobile layout */
  }
  
  .filters__label {
    min-width: 60px; /* Ensure alignment */
    padding-top: 8px; /* Align with input height roughly */
  }

  .product-select {
    width: 100% !important;
    flex: 1;
  }

  .mobile-date-pickers {
    width: 100%;
    flex: 1;
  }
}
</style>
