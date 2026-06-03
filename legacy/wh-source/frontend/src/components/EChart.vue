<script setup>
import echarts from '../utils/echarts'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '280px' },
})

const chartEl = ref(null)
let chartInstance = null

function resize() {
  if (!chartInstance) return
  chartInstance.resize()
}

onMounted(() => {
  chartInstance = echarts.init(chartEl.value)
  chartInstance.setOption(props.option, true)
  window.addEventListener('resize', resize)
})

watch(
  () => props.option,
  (opt) => {
    if (!chartInstance || !opt) return
    chartInstance.setOption(opt, true)
  },
  { deep: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<template>
  <div ref="chartEl" :style="{ width: '100%', height }" />
</template>

