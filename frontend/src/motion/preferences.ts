import { computed, onBeforeUnmount, onMounted, readonly, ref } from 'vue'

export type MotionMode = 'full' | 'reduce' | 'none'

const MOTION_MEDIA_QUERY = '(prefers-reduced-motion: reduce)'
const MOTION_ATTRIBUTE = 'data-motion'
const isTestMode = import.meta.env.MODE === 'test'

const systemPrefersReducedMotion = ref(false)
const motionMode = ref<MotionMode>('full')

let mediaQuery: MediaQueryList | null = null
let activeConsumers = 0
let listening = false

function getMediaQuery(): MediaQueryList | null {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return null
  }

  if (!mediaQuery) {
    mediaQuery = window.matchMedia(MOTION_MEDIA_QUERY)
  }

  return mediaQuery
}

function resolveMotionMode(): MotionMode {
  if (isTestMode) {
    return 'none'
  }

  return systemPrefersReducedMotion.value ? 'reduce' : 'full'
}

function applyMotionMode(): void {
  motionMode.value = resolveMotionMode()

  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute(MOTION_ATTRIBUTE, motionMode.value)
  }
}

function handleMotionPreferenceChange(event: MediaQueryListEvent): void {
  systemPrefersReducedMotion.value = event.matches
  applyMotionMode()
}

function startMotionPreferenceTracking(): void {
  activeConsumers += 1
  const query = getMediaQuery()
  systemPrefersReducedMotion.value = Boolean(query?.matches)
  applyMotionMode()

  if (!query || listening) {
    return
  }

  if (typeof query.addEventListener === 'function') {
    query.addEventListener('change', handleMotionPreferenceChange)
  } else {
    query.addListener(handleMotionPreferenceChange)
  }
  listening = true
}

function stopMotionPreferenceTracking(): void {
  activeConsumers = Math.max(0, activeConsumers - 1)
  if (activeConsumers > 0) {
    return
  }

  const query = getMediaQuery()
  if (!query || !listening) {
    return
  }

  if (typeof query.removeEventListener === 'function') {
    query.removeEventListener('change', handleMotionPreferenceChange)
  } else {
    query.removeListener(handleMotionPreferenceChange)
  }
  listening = false
}

applyMotionMode()

export function useMotionPreferences() {
  onMounted(startMotionPreferenceTracking)
  onBeforeUnmount(stopMotionPreferenceTracking)

  return {
    motionMode: readonly(motionMode),
    prefersReducedMotion: computed(() => motionMode.value === 'reduce'),
    suppressNonEssentialMotion: computed(() => motionMode.value !== 'full'),
    disableAllNonEssentialMotion: computed(() => motionMode.value === 'none'),
    isTestMode,
  }
}
