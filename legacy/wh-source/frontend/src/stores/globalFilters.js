import { computed, ref } from 'vue'

const productId = ref(null)
const start = ref(null)
const end = ref(null)

const dateRange = computed({
  get() {
    if (!start.value || !end.value) return null
    return [start.value, end.value]
  },
  set(range) {
    if (!range || range.length !== 2) {
      start.value = null
      end.value = null
      return
    }
    start.value = range[0] || null
    end.value = range[1] || null
  },
})

export function useGlobalFilters() {
  return { productId, start, end, dateRange }
}

