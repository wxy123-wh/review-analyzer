import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

function parseAspectId(raw) {
  if (raw == null || raw === '') return null
  const n = Number(raw)
  return Number.isFinite(n) ? n : null
}

export function useAnalysisFilters({ aspects }) {
  const route = useRoute()
  const router = useRouter()

  const selectedAspectId = ref(null)

  function aspectIdFromRoute() {
    return parseAspectId(route.query?.aspectId)
  }

  function ensureSelection() {
    const rows = aspects?.value || []
    if (!rows.length) {
      selectedAspectId.value = null
      return
    }

    const fromRoute = aspectIdFromRoute()
    if (fromRoute && rows.some((a) => a.aspectId === fromRoute)) {
      selectedAspectId.value = fromRoute
      return
    }

    if (selectedAspectId.value && rows.some((a) => a.aspectId === selectedAspectId.value)) {
      return
    }

    selectedAspectId.value = rows[0].aspectId
  }

  function setAspectId(id, { syncRoute = false } = {}) {
    selectedAspectId.value = id == null ? null : id
    if (!syncRoute) return
    const query = { ...route.query }
    if (id == null || id === '') delete query.aspectId
    else query.aspectId = String(id)
    router.replace({ path: '/analysis', query })
  }

  function onAspectRowClick(row) {
    if (!row) return
    setAspectId(row.aspectId, { syncRoute: true })
  }

  const selectedAspectName = computed(() => {
    const rows = aspects?.value || []
    const cur = rows.find((a) => a.aspectId === selectedAspectId.value)
    return cur?.aspectName || ''
  })

  watch(
    () => route.query?.aspectId,
    () => ensureSelection(),
  )

  return { selectedAspectId, selectedAspectName, ensureSelection, onAspectRowClick, setAspectId }
}

