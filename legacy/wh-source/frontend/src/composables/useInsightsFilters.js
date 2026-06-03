import { computed, ref, watch } from 'vue'

function normalizeSentiment(input) {
  const v = String(input || '').toUpperCase()
  if (v === 'POS' || v === 'NEG') return v
  return 'ALL'
}

export function useWordCloudFilters(props) {
  const localSentiment = ref('ALL')
  watch(
    () => props.sentiment,
    (v) => {
      localSentiment.value = normalizeSentiment(v)
    },
    { immediate: true },
  )

  const topN = ref(80)

  const normalizedSentimentOrNull = computed(() => {
    const v = normalizeSentiment(localSentiment.value)
    return v === 'ALL' ? null : v
  })

  return { localSentiment, topN, normalizedSentimentOrNull }
}

function normalizeGranularity(input) {
  const v = String(input || '').toLowerCase()
  return v === 'week' ? 'week' : 'day'
}

export function useSentimentTrendFilters(props) {
  const localGranularity = ref('day')
  watch(
    () => props.granularity,
    (v) => {
      localGranularity.value = normalizeGranularity(v)
    },
    { immediate: true },
  )

  const mode = ref('count') // count | rate

  return { localGranularity, mode }
}

