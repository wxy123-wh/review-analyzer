import gsap from 'gsap'
import { onBeforeUnmount } from 'vue'

type Cleanup = () => void

export function useMotionLifecycle() {
  const cleanups = new Set<Cleanup>()
  const trackedTimeouts = new Map<number, Cleanup>()
  const trackedAnimationFrames = new Map<number, Cleanup>()

  function registerCleanup(cleanup: Cleanup): Cleanup {
    cleanups.add(cleanup)
    return cleanup
  }

  function unregisterCleanup(cleanup: Cleanup): void {
    cleanups.delete(cleanup)
  }

  function runCleanup(cleanup: Cleanup): void {
    unregisterCleanup(cleanup)
    cleanup()
  }

  function setTrackedTimeout(callback: () => void, delay: number): number {
    const timeoutId = window.setTimeout(() => {
      const cleanup = trackedTimeouts.get(timeoutId)
      if (cleanup) {
        unregisterCleanup(cleanup)
        trackedTimeouts.delete(timeoutId)
      }
      callback()
    }, delay)

    const cleanup = registerCleanup(() => {
      window.clearTimeout(timeoutId)
      trackedTimeouts.delete(timeoutId)
    })
    trackedTimeouts.set(timeoutId, cleanup)

    return timeoutId
  }

  function clearTrackedTimeout(timeoutId: number | undefined): void {
    if (timeoutId === undefined) {
      return
    }

    const cleanup = trackedTimeouts.get(timeoutId)
    if (cleanup) {
      runCleanup(cleanup)
      return
    }

    window.clearTimeout(timeoutId)
  }

  function requestTrackedAnimationFrame(callback: FrameRequestCallback): number {
    const animationFrameId = window.requestAnimationFrame((time) => {
      const cleanup = trackedAnimationFrames.get(animationFrameId)
      if (cleanup) {
        unregisterCleanup(cleanup)
        trackedAnimationFrames.delete(animationFrameId)
      }
      callback(time)
    })

    const cleanup = registerCleanup(() => {
      window.cancelAnimationFrame(animationFrameId)
      trackedAnimationFrames.delete(animationFrameId)
    })
    trackedAnimationFrames.set(animationFrameId, cleanup)

    return animationFrameId
  }

  function cancelTrackedAnimationFrame(animationFrameId: number | undefined): void {
    if (animationFrameId === undefined) {
      return
    }

    const cleanup = trackedAnimationFrames.get(animationFrameId)
    if (cleanup) {
      runCleanup(cleanup)
      return
    }

    window.cancelAnimationFrame(animationFrameId)
  }

  function registerGsapContext(setup: gsap.ContextFunc, scope?: Element | string | object): gsap.Context {
    const context = gsap.context(setup, scope)
    registerCleanup(() => {
      context.revert()
    })
    return context
  }

  function killTweens(target: gsap.TweenTarget, properties?: string): void {
    gsap.killTweensOf(target, properties)
  }

  function cleanup(): void {
    Array.from(cleanups).forEach((cleanupFn) => {
      runCleanup(cleanupFn)
    })
  }

  onBeforeUnmount(() => {
    cleanup()
  })

  return {
    registerCleanup,
    unregisterCleanup,
    setTrackedTimeout,
    clearTrackedTimeout,
    requestTrackedAnimationFrame,
    cancelTrackedAnimationFrame,
    registerGsapContext,
    killTweens,
    cleanup,
  }
}
