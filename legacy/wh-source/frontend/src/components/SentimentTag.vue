<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: String, default: 'NEU' },
})

const tagType = computed(() => {
  const v = (props.value || '').toUpperCase()
  if (v === 'POS') return 'success'
  if (v === 'NEG') return 'danger'
  return 'info'
})

const label = computed(() => {
  const v = (props.value || '').toUpperCase()
  if (v === 'POS') return '正向'
  if (v === 'NEG') return '负向'
  if (v === 'NEU') return '中性'
  return '中性'
})
</script>

<template>
  <el-tag :type="tagType" effect="light" size="small">{{ label }}</el-tag>
</template>
