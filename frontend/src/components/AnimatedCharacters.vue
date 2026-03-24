<template>
  <div ref="containerRef" class="animated-characters">
    <div
      ref="purpleRef"
      data-testid="login-monster"
      data-monster-id="purple"
      class="character purple"
      :class="{ jumping: jumpingCharacters.purple, lightened: lightenedCharacters.purple }"
      :style="characterStyle('purple')"
      @click="handleCharacterClick('purple')"
      @dblclick.prevent="handleCharacterDoubleClick('purple')"
    >
      <div ref="purpleFaceRef" class="face purple-face">
        <div class="eyeball" data-max-distance="5">
          <div data-testid="monster-pupil" class="eyeball-pupil"></div>
        </div>
        <div class="eyeball" data-max-distance="5">
          <div data-testid="monster-pupil" class="eyeball-pupil"></div>
        </div>
      </div>
    </div>

    <div
      ref="blackRef"
      data-testid="login-monster"
      data-monster-id="black"
      class="character black"
      :class="{ jumping: jumpingCharacters.black, lightened: lightenedCharacters.black }"
      :style="characterStyle('black')"
      @click="handleCharacterClick('black')"
      @dblclick.prevent="handleCharacterDoubleClick('black')"
    >
      <div ref="blackFaceRef" class="face black-face">
        <div class="eyeball black-eye" data-max-distance="4">
          <div data-testid="monster-pupil" class="eyeball-pupil black-pupil"></div>
        </div>
        <div class="eyeball black-eye" data-max-distance="4">
          <div data-testid="monster-pupil" class="eyeball-pupil black-pupil"></div>
        </div>
      </div>
    </div>

    <div
      ref="orangeRef"
      data-testid="login-monster"
      data-monster-id="orange"
      class="character orange"
      :class="{ jumping: jumpingCharacters.orange, lightened: lightenedCharacters.orange }"
      :style="characterStyle('orange')"
      @click="handleCharacterClick('orange')"
      @dblclick.prevent="handleCharacterDoubleClick('orange')"
    >
      <div ref="orangeFaceRef" class="face orange-face">
        <div data-testid="monster-pupil" class="pupil" data-max-distance="5"></div>
        <div data-testid="monster-pupil" class="pupil" data-max-distance="5"></div>
      </div>
    </div>

    <div
      ref="yellowRef"
      data-testid="login-monster"
      data-monster-id="yellow"
      class="character yellow"
      :class="{ jumping: jumpingCharacters.yellow, lightened: lightenedCharacters.yellow }"
      :style="characterStyle('yellow')"
      @click="handleCharacterClick('yellow')"
      @dblclick.prevent="handleCharacterDoubleClick('yellow')"
    >
      <div ref="yellowFaceRef" class="face yellow-face">
        <div data-testid="monster-pupil" class="pupil" data-max-distance="5"></div>
        <div data-testid="monster-pupil" class="pupil" data-max-distance="5"></div>
      </div>
      <div ref="yellowMouthRef" class="yellow-mouth"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import gsap from 'gsap'
import { computed, onBeforeUnmount, onMounted, ref, watch, watchEffect } from 'vue'

const props = withDefaults(
  defineProps<{
    isTyping?: boolean
    showPassword?: boolean
    passwordLength?: number
  }>(),
  {
    isTyping: false,
    showPassword: false,
    passwordLength: 0,
  },
)

type CharacterState = {
  isTyping: boolean
  isHidingPassword: boolean
  isShowingPassword: boolean
  isLooking: boolean
}

type CharacterId = 'purple' | 'black' | 'orange' | 'yellow'

type QuickToMap = {
  purpleSkew: gsap.QuickToFunc
  blackSkew: gsap.QuickToFunc
  orangeSkew: gsap.QuickToFunc
  yellowSkew: gsap.QuickToFunc
  purpleX: gsap.QuickToFunc
  blackX: gsap.QuickToFunc
  purpleHeight: gsap.QuickToFunc
  purpleFaceLeft: gsap.QuickToFunc
  purpleFaceTop: gsap.QuickToFunc
  blackFaceLeft: gsap.QuickToFunc
  blackFaceTop: gsap.QuickToFunc
  orangeFaceX: gsap.QuickToFunc
  orangeFaceY: gsap.QuickToFunc
  yellowFaceX: gsap.QuickToFunc
  yellowFaceY: gsap.QuickToFunc
  mouthX: gsap.QuickToFunc
  mouthY: gsap.QuickToFunc
}

const containerRef = ref<HTMLDivElement | null>(null)
const purpleRef = ref<HTMLDivElement | null>(null)
const blackRef = ref<HTMLDivElement | null>(null)
const yellowRef = ref<HTMLDivElement | null>(null)
const orangeRef = ref<HTMLDivElement | null>(null)

const purpleFaceRef = ref<HTMLDivElement | null>(null)
const blackFaceRef = ref<HTMLDivElement | null>(null)
const yellowFaceRef = ref<HTMLDivElement | null>(null)
const orangeFaceRef = ref<HTMLDivElement | null>(null)
const yellowMouthRef = ref<HTMLDivElement | null>(null)

const characterIds: CharacterId[] = ['purple', 'black', 'orange', 'yellow']
const SINGLE_CLICK_DELAY_MS = 220

const baseCharacterColors: Record<CharacterId, string> = {
  purple: '#6c3ff5',
  black: '#2d2d2d',
  orange: '#ff9b6b',
  yellow: '#e8d754',
}

const lightCharacterColors: Record<CharacterId, string> = {
  purple: '#9e84ff',
  black: '#707070',
  orange: '#ffc1a1',
  yellow: '#f4e78c',
}

const mouse = { x: 0, y: 0 }
const state: CharacterState = {
  isTyping: false,
  isHidingPassword: false,
  isShowingPassword: false,
  isLooking: false,
}

const quickToRef = ref<QuickToMap | null>(null)
const isLookingRef = ref(false)
const lightenedCharacters = ref<Record<CharacterId, boolean>>({
  purple: false,
  black: false,
  orange: false,
  yellow: false,
})
const jumpingCharacters = ref<Record<CharacterId, boolean>>({
  purple: false,
  black: false,
  orange: false,
  yellow: false,
})

const isHidingPassword = computed(() => (props.passwordLength ?? 0) > 0 && !props.showPassword)
const isShowingPassword = computed(() => (props.passwordLength ?? 0) > 0 && !!props.showPassword)

let rafId = 0
let purpleBlinkTimer: ReturnType<typeof setTimeout> | undefined
let blackBlinkTimer: ReturnType<typeof setTimeout> | undefined
let purplePeekTimer: ReturnType<typeof setTimeout> | undefined
let lookingTimer: ReturnType<typeof setTimeout> | undefined
let onMoveHandler: ((event: MouseEvent) => void) | null = null
const clickTimers: Partial<Record<CharacterId, ReturnType<typeof setTimeout>>> = {}
const jumpStateTimers: Partial<Record<CharacterId, ReturnType<typeof setTimeout>>> = {}

function clearTimer(timer: ReturnType<typeof setTimeout> | undefined): void {
  if (timer) {
    clearTimeout(timer)
  }
}

watchEffect(() => {
  state.isTyping = !!props.isTyping
  state.isHidingPassword = isHidingPassword.value
  state.isShowingPassword = isShowingPassword.value
  state.isLooking = isLookingRef.value
})

function calcPos(el: HTMLElement): { faceX: number; faceY: number; bodySkew: number } {
  const rect = el.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 3
  const dx = mouse.x - cx
  const dy = mouse.y - cy
  return {
    faceX: clamp(dx / 20, -15, 15),
    faceY: clamp(dy / 30, -10, 10),
    bodySkew: clamp(-dx / 120, -6, 6),
  }
}

function calcEyePos(el: HTMLElement, maxDistance: number): { x: number; y: number } {
  const rect = el.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 2
  const dx = mouse.x - cx
  const dy = mouse.y - cy
  const distance = Math.min(Math.hypot(dx, dy), maxDistance)
  const angle = Math.atan2(dy, dx)
  return {
    x: Math.cos(angle) * distance,
    y: Math.sin(angle) * distance,
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value))
}

function characterStyle(characterId: CharacterId): Record<string, string> {
  return {
    backgroundColor: lightenedCharacters.value[characterId]
      ? lightCharacterColors[characterId]
      : baseCharacterColors[characterId],
  }
}

function characterElement(characterId: CharacterId): HTMLDivElement | null {
  if (characterId === 'purple') {
    return purpleRef.value
  }
  if (characterId === 'black') {
    return blackRef.value
  }
  if (characterId === 'orange') {
    return orangeRef.value
  }
  return yellowRef.value
}

function triggerJump(characterId: CharacterId): void {
  const target = characterElement(characterId)
  if (!target) {
    return
  }

  const pendingJumpStateTimer = jumpStateTimers[characterId]
  if (pendingJumpStateTimer) {
    clearTimeout(pendingJumpStateTimer)
  }

  jumpingCharacters.value[characterId] = true
  gsap.killTweensOf(target, 'y')
  gsap.fromTo(
    target,
    { y: 0 },
    {
      y: -20,
      duration: 0.16,
      ease: 'power2.out',
      yoyo: true,
      repeat: 1,
      overwrite: 'auto',
      onComplete: () => {
        gsap.set(target, { y: 0 })
      },
    },
  )

  jumpStateTimers[characterId] = setTimeout(() => {
    jumpingCharacters.value[characterId] = false
    jumpStateTimers[characterId] = undefined
  }, 360)
}

function handleCharacterDoubleClick(characterId: CharacterId): void {
  const pendingClickTimer = clickTimers[characterId]
  if (pendingClickTimer) {
    clearTimeout(pendingClickTimer)
    clickTimers[characterId] = undefined
  }
  lightenedCharacters.value[characterId] = true
}

function handleCharacterClick(characterId: CharacterId): void {
  if (lightenedCharacters.value[characterId]) {
    lightenedCharacters.value[characterId] = false
    return
  }

  const pendingClickTimer = clickTimers[characterId]
  if (pendingClickTimer) {
    clearTimeout(pendingClickTimer)
  }
  clickTimers[characterId] = setTimeout(() => {
    triggerJump(characterId)
    clickTimers[characterId] = undefined
  }, SINGLE_CLICK_DELAY_MS)
}

function buildQuickTo(): QuickToMap | null {
  if (
    !purpleRef.value ||
    !blackRef.value ||
    !orangeRef.value ||
    !yellowRef.value ||
    !purpleFaceRef.value ||
    !blackFaceRef.value ||
    !orangeFaceRef.value ||
    !yellowFaceRef.value ||
    !yellowMouthRef.value
  ) {
    return null
  }

  return {
    purpleSkew: gsap.quickTo(purpleRef.value, 'skewX', { duration: 0.3, ease: 'power2.out' }),
    blackSkew: gsap.quickTo(blackRef.value, 'skewX', { duration: 0.3, ease: 'power2.out' }),
    orangeSkew: gsap.quickTo(orangeRef.value, 'skewX', { duration: 0.3, ease: 'power2.out' }),
    yellowSkew: gsap.quickTo(yellowRef.value, 'skewX', { duration: 0.3, ease: 'power2.out' }),
    purpleX: gsap.quickTo(purpleRef.value, 'x', { duration: 0.3, ease: 'power2.out' }),
    blackX: gsap.quickTo(blackRef.value, 'x', { duration: 0.3, ease: 'power2.out' }),
    purpleHeight: gsap.quickTo(purpleRef.value, 'height', { duration: 0.3, ease: 'power2.out' }),
    purpleFaceLeft: gsap.quickTo(purpleFaceRef.value, 'left', { duration: 0.3, ease: 'power2.out' }),
    purpleFaceTop: gsap.quickTo(purpleFaceRef.value, 'top', { duration: 0.3, ease: 'power2.out' }),
    blackFaceLeft: gsap.quickTo(blackFaceRef.value, 'left', { duration: 0.3, ease: 'power2.out' }),
    blackFaceTop: gsap.quickTo(blackFaceRef.value, 'top', { duration: 0.3, ease: 'power2.out' }),
    orangeFaceX: gsap.quickTo(orangeFaceRef.value, 'x', { duration: 0.2, ease: 'power2.out' }),
    orangeFaceY: gsap.quickTo(orangeFaceRef.value, 'y', { duration: 0.2, ease: 'power2.out' }),
    yellowFaceX: gsap.quickTo(yellowFaceRef.value, 'x', { duration: 0.2, ease: 'power2.out' }),
    yellowFaceY: gsap.quickTo(yellowFaceRef.value, 'y', { duration: 0.2, ease: 'power2.out' }),
    mouthX: gsap.quickTo(yellowMouthRef.value, 'x', { duration: 0.2, ease: 'power2.out' }),
    mouthY: gsap.quickTo(yellowMouthRef.value, 'y', { duration: 0.2, ease: 'power2.out' }),
  }
}

function tick(): void {
  const container = containerRef.value
  const qt = quickToRef.value
  if (!container || !qt) {
    rafId = requestAnimationFrame(tick)
    return
  }

  const { isTyping, isHidingPassword, isShowingPassword, isLooking } = state

  if (purpleRef.value && !isShowingPassword) {
    const pos = calcPos(purpleRef.value)
    if (isTyping || isHidingPassword) {
      qt.purpleSkew(pos.bodySkew - 12)
      qt.purpleX(40)
      qt.purpleHeight(440)
    } else {
      qt.purpleSkew(pos.bodySkew)
      qt.purpleX(0)
      qt.purpleHeight(400)
    }
  }

  if (blackRef.value && !isShowingPassword) {
    const pos = calcPos(blackRef.value)
    if (isLooking) {
      qt.blackSkew(pos.bodySkew * 1.5 + 10)
      qt.blackX(20)
    } else if (isTyping || isHidingPassword) {
      qt.blackSkew(pos.bodySkew * 1.5)
      qt.blackX(0)
    } else {
      qt.blackSkew(pos.bodySkew)
      qt.blackX(0)
    }
  }

  if (orangeRef.value && !isShowingPassword) {
    const pos = calcPos(orangeRef.value)
    qt.orangeSkew(pos.bodySkew)
  }

  if (yellowRef.value && !isShowingPassword) {
    const pos = calcPos(yellowRef.value)
    qt.yellowSkew(pos.bodySkew)
  }

  if (purpleRef.value && !isShowingPassword && !isLooking) {
    const pos = calcPos(purpleRef.value)
    const purpleFaceX = pos.faceX >= 0 ? Math.min(25, pos.faceX * 1.5) : pos.faceX
    qt.purpleFaceLeft(45 + purpleFaceX)
    qt.purpleFaceTop(40 + pos.faceY)
  }

  if (blackRef.value && !isShowingPassword && !isLooking) {
    const pos = calcPos(blackRef.value)
    qt.blackFaceLeft(26 + pos.faceX)
    qt.blackFaceTop(32 + pos.faceY)
  }

  if (orangeRef.value && !isShowingPassword) {
    const pos = calcPos(orangeRef.value)
    qt.orangeFaceX(pos.faceX)
    qt.orangeFaceY(pos.faceY)
  }

  if (yellowRef.value && !isShowingPassword) {
    const pos = calcPos(yellowRef.value)
    qt.yellowFaceX(pos.faceX)
    qt.yellowFaceY(pos.faceY)
    qt.mouthX(pos.faceX)
    qt.mouthY(pos.faceY)
  }

  if (!isShowingPassword) {
    const pupils = container.querySelectorAll<HTMLElement>('.pupil')
    pupils.forEach((pupil) => {
      const maxDist = Number(pupil.dataset.maxDistance ?? 5)
      const pos = calcEyePos(pupil, maxDist)
      gsap.set(pupil, { x: pos.x, y: pos.y })
    })

    if (!isLooking) {
      const eyeballs = container.querySelectorAll<HTMLElement>('.eyeball')
      eyeballs.forEach((eyeball) => {
        const maxDist = Number(eyeball.dataset.maxDistance ?? 10)
        const pupil = eyeball.querySelector<HTMLElement>('.eyeball-pupil')
        if (!pupil) {
          return
        }
        const pos = calcEyePos(eyeball, maxDist)
        gsap.set(pupil, { x: pos.x, y: pos.y })
      })
    }
  }

  rafId = requestAnimationFrame(tick)
}

function setEyePupils(target: HTMLDivElement | null, x: number, y: number): void {
  if (!target) {
    return
  }
  target.querySelectorAll<HTMLElement>('.eyeball-pupil').forEach((pupil) => {
    gsap.to(pupil, {
      x,
      y,
      duration: 0.3,
      ease: 'power2.out',
      overwrite: 'auto',
    })
  })
}

function applyLookAtEachOther(): void {
  const qt = quickToRef.value
  if (qt) {
    qt.purpleFaceLeft(55)
    qt.purpleFaceTop(65)
    qt.blackFaceLeft(32)
    qt.blackFaceTop(12)
  }
  setEyePupils(purpleRef.value, 3, 4)
  setEyePupils(blackRef.value, 0, -4)
}

function applyHidingPassword(): void {
  const qt = quickToRef.value
  if (qt) {
    qt.purpleFaceLeft(55)
    qt.purpleFaceTop(65)
  }
}

function applyShowPassword(): void {
  const qt = quickToRef.value
  if (qt) {
    qt.purpleSkew(0)
    qt.blackSkew(0)
    qt.orangeSkew(0)
    qt.yellowSkew(0)
    qt.purpleX(0)
    qt.blackX(0)
    qt.purpleHeight(400)

    qt.purpleFaceLeft(20)
    qt.purpleFaceTop(35)
    qt.blackFaceLeft(10)
    qt.blackFaceTop(28)
    qt.orangeFaceX(-32)
    qt.orangeFaceY(-5)
    qt.yellowFaceX(-32)
    qt.yellowFaceY(-5)
    qt.mouthX(-30)
    qt.mouthY(0)
  }

  setEyePupils(purpleRef.value, -4, -4)
  setEyePupils(blackRef.value, -4, -4)

  orangeRef.value?.querySelectorAll<HTMLElement>('.pupil').forEach((pupil) => {
    gsap.to(pupil, {
      x: -5,
      y: -4,
      duration: 0.3,
      ease: 'power2.out',
      overwrite: 'auto',
    })
  })

  yellowRef.value?.querySelectorAll<HTMLElement>('.pupil').forEach((pupil) => {
    gsap.to(pupil, {
      x: -5,
      y: -4,
      duration: 0.3,
      ease: 'power2.out',
      overwrite: 'auto',
    })
  })
}

function scheduleBlink(
  targetRef: { value: HTMLDivElement | null },
  fallbackSize: number,
  setTimer: (timer: ReturnType<typeof setTimeout>) => void,
): void {
  const eyeballs = targetRef.value?.querySelectorAll<HTMLElement>('.eyeball')
  if (!eyeballs || !eyeballs.length) {
    return
  }

  const timer = setTimeout(() => {
    eyeballs.forEach((el) => {
      gsap.to(el, { height: 2, duration: 0.08, ease: 'power2.in' })
    })

    setTimeout(() => {
      eyeballs.forEach((el) => {
        const size = Number(el.style.width.replace('px', '')) || fallbackSize
        gsap.to(el, { height: size, duration: 0.08, ease: 'power2.out' })
      })
      scheduleBlink(targetRef, fallbackSize, setTimer)
    }, 150)
  }, Math.random() * 4000 + 3000)

  setTimer(timer)
}

watch(
  [() => props.isTyping, isShowingPassword],
  ([typing, showing]) => {
    if (typing && !showing) {
      isLookingRef.value = true
      state.isLooking = true
      applyLookAtEachOther()

      clearTimer(lookingTimer)
      lookingTimer = setTimeout(() => {
        isLookingRef.value = false
        state.isLooking = false
        purpleRef.value?.querySelectorAll<HTMLElement>('.eyeball-pupil').forEach((pupil) => {
          gsap.killTweensOf(pupil)
        })
      }, 800)
      return
    }

    clearTimer(lookingTimer)
    isLookingRef.value = false
    state.isLooking = false
  },
  { immediate: true },
)

watch(
  [isHidingPassword, isShowingPassword],
  ([hiding, showing]) => {
    if (showing) {
      applyShowPassword()
      return
    }
    if (hiding) {
      applyHidingPassword()
    }
  },
  { immediate: true },
)

watch(
  [isShowingPassword, () => props.passwordLength],
  ([showing, passwordLength]) => {
    clearTimer(purplePeekTimer)
    if (!showing || (passwordLength ?? 0) <= 0) {
      return
    }

    const purplePupils = purpleRef.value?.querySelectorAll<HTMLElement>('.eyeball-pupil')
    if (!purplePupils || !purplePupils.length) {
      return
    }

    const schedulePeek = (): void => {
      purplePeekTimer = setTimeout(() => {
        purplePupils.forEach((pupil) => {
          gsap.to(pupil, {
            x: 4,
            y: 5,
            duration: 0.3,
            ease: 'power2.out',
            overwrite: 'auto',
          })
        })

        const qt = quickToRef.value
        if (qt) {
          qt.purpleFaceLeft(20)
          qt.purpleFaceTop(35)
        }

        setTimeout(() => {
          purplePupils.forEach((pupil) => {
            gsap.to(pupil, {
              x: -4,
              y: -4,
              duration: 0.3,
              ease: 'power2.out',
              overwrite: 'auto',
            })
          })
          schedulePeek()
        }, 800)
      }, Math.random() * 3000 + 2000)
    }

    schedulePeek()
  },
  { immediate: true },
)

onMounted(() => {
  const container = containerRef.value
  if (!container) {
    return
  }

  gsap.set(container.querySelectorAll<HTMLElement>('.pupil'), { x: 0, y: 0 })
  gsap.set(container.querySelectorAll<HTMLElement>('.eyeball-pupil'), { x: 0, y: 0 })

  quickToRef.value = buildQuickTo()

  const onMove = (event: MouseEvent): void => {
    mouse.x = event.clientX
    mouse.y = event.clientY
  }
  onMoveHandler = onMove

  window.addEventListener('mousemove', onMove, { passive: true })
  rafId = requestAnimationFrame(tick)

  scheduleBlink(purpleRef, 18, (timer) => {
    purpleBlinkTimer = timer
  })

  scheduleBlink(blackRef, 16, (timer) => {
    blackBlinkTimer = timer
  })
})

onBeforeUnmount(() => {
  if (onMoveHandler) {
    window.removeEventListener('mousemove', onMoveHandler)
  }
  cancelAnimationFrame(rafId)
  characterIds.forEach((characterId) => {
    const clickTimer = clickTimers[characterId]
    if (clickTimer) {
      clearTimeout(clickTimer)
      clickTimers[characterId] = undefined
    }

    const jumpTimer = jumpStateTimers[characterId]
    if (jumpTimer) {
      clearTimeout(jumpTimer)
      jumpStateTimers[characterId] = undefined
    }
  })
  clearTimer(purpleBlinkTimer)
  clearTimer(blackBlinkTimer)
  clearTimer(purplePeekTimer)
  clearTimer(lookingTimer)
})
</script>

<style scoped>
.animated-characters {
  position: relative;
  width: 550px;
  height: 400px;
}

.character {
  position: absolute;
  bottom: 0;
  transform-origin: bottom center;
  will-change: transform;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.character.jumping {
  filter: brightness(1.05);
}

.character.lightened {
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.45);
}

.purple {
  left: 70px;
  width: 180px;
  height: 400px;
  background: #6c3ff5;
  border-radius: 10px 10px 0 0;
  z-index: 1;
}

.black {
  left: 240px;
  width: 120px;
  height: 310px;
  background: #2d2d2d;
  border-radius: 8px 8px 0 0;
  z-index: 2;
}

.orange {
  left: 0;
  width: 240px;
  height: 200px;
  background: #ff9b6b;
  border-radius: 120px 120px 0 0;
  z-index: 3;
}

.yellow {
  left: 310px;
  width: 140px;
  height: 230px;
  background: #e8d754;
  border-radius: 70px 70px 0 0;
  z-index: 4;
}

.face {
  position: absolute;
  display: flex;
  align-items: center;
}

.purple-face {
  gap: 32px;
  left: 45px;
  top: 40px;
}

.black-face {
  gap: 24px;
  left: 26px;
  top: 32px;
}

.orange-face {
  gap: 32px;
  left: 82px;
  top: 90px;
}

.yellow-face {
  gap: 24px;
  left: 52px;
  top: 40px;
}

.eyeball {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  will-change: height;
}

.black-eye {
  width: 16px;
  height: 16px;
}

.eyeball-pupil {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #2d2d2d;
  will-change: transform;
}

.black-pupil {
  width: 6px;
  height: 6px;
}

.pupil {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #2d2d2d;
  will-change: transform;
}

.yellow-mouth {
  position: absolute;
  left: 40px;
  top: 88px;
  width: 80px;
  height: 4px;
  border-radius: 9999px;
  background: #2d2d2d;
  will-change: transform;
}

@media (max-width: 1200px) {
  .animated-characters {
    transform: scale(0.85);
    transform-origin: center bottom;
  }
}

@media (max-width: 640px) {
  .animated-characters {
    transform: scale(0.72);
  }
}
</style>
