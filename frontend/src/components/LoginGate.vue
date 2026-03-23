<template>
  <section data-testid="login-gate" class="login-gate" @mousemove="trackPointer">
    <div class="login-shell">
      <article class="monster-stage" :data-state="stageState">
        <button
          v-for="(monster, index) in monsters"
          :key="monster.id"
          data-testid="login-monster"
          type="button"
          :data-monster-id="monster.id"
          class="monster"
          :class="[
            `monster-${monster.id}`,
            `shape-${monster.shape}`,
            {
              avoiding: passwordFocused,
              shaking,
              jumping: jumpingMonsterId === monster.id,
            },
          ]"
          @click="triggerJump(monster.id)"
        >
          <span class="monster-antenna" aria-hidden="true"></span>
          <span class="monster-head">
            <span class="eye">
              <span data-testid="monster-pupil" class="pupil" :style="pupilStyle(index, -1)"></span>
            </span>
            <span class="eye">
              <span data-testid="monster-pupil" class="pupil" :style="pupilStyle(index, 1)"></span>
            </span>
          </span>
          <span class="monster-mouth" aria-hidden="true"></span>
          <span class="monster-body" aria-hidden="true"></span>
        </button>
      </article>

      <form class="panel" @submit.prevent="submit">
        <h1>评论改进决策系统</h1>
        <p>请输入演示账号进入系统。</p>
        <label>
          用户名
          <input
            data-testid="login-username"
            v-model.trim="username"
            type="text"
            placeholder="请输入用户名"
            autocomplete="off"
          />
        </label>
        <label>
          密码
          <input
            data-testid="login-password"
            v-model.trim="password"
            type="password"
            placeholder="请输入密码"
            @focus="passwordFocused = true"
            @blur="passwordFocused = false"
          />
        </label>
        <p v-if="error" data-testid="login-error" class="error">{{ error }}</p>
        <button data-testid="login-submit" type="button" @click="submit">进入系统</button>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'

const emit = defineEmits<{
  (event: 'enter', payload: { username: string }): void
}>()

type MonsterId = 'red' | 'blue' | 'yellow'

type MonsterShape = 'crest' | 'horn' | 'round'

type MonsterConfig = {
  id: MonsterId
  shape: MonsterShape
}

const DEMO_USERNAME = 'wxy'
const DEMO_PASSWORD = '123456'

const monsters: MonsterConfig[] = [
  { id: 'red', shape: 'crest' },
  { id: 'blue', shape: 'horn' },
  { id: 'yellow', shape: 'round' },
]

const username = ref('')
const password = ref('')
const error = ref('')
const passwordFocused = ref(false)
const pointerX = ref(viewportCenter().x)
const pointerY = ref(viewportCenter().y)
const shaking = ref(false)
const jumpingMonsterId = ref<MonsterId | null>(null)

let shakeTimer: ReturnType<typeof setTimeout> | undefined
let jumpTimer: ReturnType<typeof setTimeout> | undefined

const stageState = computed(() => {
  if (passwordFocused.value) {
    return 'typing'
  }
  if (shaking.value) {
    return 'error'
  }
  if (jumpingMonsterId.value) {
    return 'jumping'
  }
  return 'tracking'
})

function trackPointer(event: MouseEvent): void {
  pointerX.value = event.clientX
  pointerY.value = event.clientY
}

function pupilStyle(monsterIndex: number, eyeOffset: number): { transform: string } {
  if (passwordFocused.value) {
    return {
      transform: 'translate(-7px, -2px)',
    }
  }

  const center = viewportCenter()
  const horizontalOffset = [-140, 0, 140][monsterIndex] ?? 0
  const x = clamp((pointerX.value - center.x + horizontalOffset + eyeOffset * 8) / 44, -6, 6)
  const y = clamp((pointerY.value - center.y + monsterIndex * 8) / 48, -5, 5)
  return {
    transform: `translate(${x}px, ${y}px)`,
  }
}

async function triggerShake(): Promise<void> {
  if (shakeTimer) {
    clearTimeout(shakeTimer)
  }
  shaking.value = false
  await nextTick()
  shaking.value = true
  shakeTimer = setTimeout(() => {
    shaking.value = false
  }, 460)
}

async function triggerJump(monsterId: MonsterId): Promise<void> {
  if (jumpTimer) {
    clearTimeout(jumpTimer)
  }
  jumpingMonsterId.value = null
  await nextTick()
  jumpingMonsterId.value = monsterId
  jumpTimer = setTimeout(() => {
    if (jumpingMonsterId.value === monsterId) {
      jumpingMonsterId.value = null
    }
  }, 420)
}

async function submit(): Promise<void> {
  const valid = username.value === DEMO_USERNAME && password.value === DEMO_PASSWORD
  if (!valid) {
    error.value = '账号或密码错误，请使用演示账号 wxy / 123456。'
    await triggerShake()
    return
  }
  error.value = ''
  emit('enter', { username: username.value })
}

onBeforeUnmount(() => {
  if (shakeTimer) {
    clearTimeout(shakeTimer)
  }
  if (jumpTimer) {
    clearTimeout(jumpTimer)
  }
})

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function viewportCenter(): { x: number; y: number } {
  if (typeof window === 'undefined') {
    return { x: 400, y: 280 }
  }
  return {
    x: window.innerWidth / 2,
    y: window.innerHeight / 2,
  }
}
</script>

<style scoped>
.login-gate {
  min-height: 100vh;
  background:
    radial-gradient(circle at 12% 18%, rgba(216, 236, 255, 0.55), transparent 34%),
    radial-gradient(circle at 84% 12%, rgba(255, 244, 203, 0.45), transparent 30%),
    #ffffff;
}

.login-shell {
  min-height: 100vh;
  display: grid;
  place-items: center;
  gap: 22px;
  padding: 28px 16px;
}

.monster-stage {
  width: min(680px, 100%);
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr));
  gap: 14px;
}

.monster {
  --skin: #5f8db7;
  --skin-light: #dfeefa;
  --outline: #2e4d67;
  --body: #edf4fb;
  position: relative;
  border: 0;
  padding: 0;
  width: 100%;
  display: grid;
  justify-items: center;
  background: transparent;
  cursor: pointer;
  outline-offset: 4px;
  transition: transform 0.18s ease;
}

.monster:focus-visible {
  outline: 2px solid #245c8d;
}

.monster-antenna {
  position: absolute;
  top: -22px;
  width: 5px;
  height: 24px;
  border-radius: 999px;
  background: var(--outline);
}

.monster-head {
  position: relative;
  width: 118px;
  height: 92px;
  border: 2px solid var(--outline);
  background: linear-gradient(180deg, var(--skin-light), #ffffff 78%);
  border-radius: 26px;
  display: flex;
  justify-content: center;
  gap: 14px;
  align-items: center;
  box-shadow: 0 8px 18px rgba(33, 60, 87, 0.14);
  transition: transform 0.2s ease;
}

.eye {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  border: 1px solid rgba(43, 68, 91, 0.22);
  background: #ffffff;
}

.pupil {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #1f364c;
  transition: transform 0.09s linear;
}

.monster-mouth {
  margin-top: 8px;
  width: 46px;
  height: 8px;
  border-radius: 999px;
  background: var(--skin);
}

.monster-body {
  margin-top: 10px;
  width: 88px;
  height: 46px;
  border-radius: 20px 20px 24px 24px;
  border: 2px solid var(--outline);
  background: linear-gradient(180deg, var(--body), #ffffff);
  transition: transform 0.2s ease;
}

.monster-red {
  --skin: #d95858;
  --skin-light: #ffe3e3;
  --outline: #7a2020;
  --body: #fff0ef;
}

.monster-blue {
  --skin: #3d88cb;
  --skin-light: #deefff;
  --outline: #1c4d79;
  --body: #ebf6ff;
}

.monster-yellow {
  --skin: #d2a11b;
  --skin-light: #fff2cb;
  --outline: #6e5404;
  --body: #fff8dd;
}

.shape-crest .monster-head {
  border-radius: 34px 34px 22px 22px;
}

.shape-crest .monster-antenna {
  top: -26px;
  height: 28px;
}

.panel {
  width: min(420px, 100%);
  border: 1px solid #cad8e4;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(43, 66, 92, 0.1);
  padding: 24px;
  display: grid;
  gap: 14px;
  color: #1f2d3d;
}

.panel h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.2;
}

.panel p {
  margin: 0;
  color: #4a5d70;
}

.panel label {
  display: grid;
  gap: 8px;
  font-size: 14px;
  color: #25384a;
}

.panel input {
  height: 42px;
  border-radius: 10px;
  border: 1px solid #b9c8d7;
  background: #ffffff;
  color: #132332;
  padding: 0 12px;
}

.panel input:focus {
  outline: none;
  border-color: #2e6d9f;
  box-shadow: 0 0 0 2px rgba(46, 109, 159, 0.18);
}

.panel button {
  height: 44px;
  border: 0;
  border-radius: 999px;
  background: #1e5c89;
  color: #ffffff;
  font-weight: 700;
  cursor: pointer;
}

.error {
  color: #b51c1c;
}

.monster.avoiding .monster-head {
  transform: translateX(-7px) rotate(-10deg);
}

.monster.avoiding .monster-body {
  transform: translateX(-3px);
}

.monster.shaking .monster-head {
  animation: head-shake 0.45s ease;
}

.monster.jumping {
  animation: monster-bounce 0.4s ease;
}

.shape-horn .monster-head {
  border-radius: 16px 16px 28px 28px;
}

.shape-horn .monster-antenna {
  width: 0;
  height: 0;
  border: 0;
}

.shape-horn .monster-head::before,
.shape-horn .monster-head::after {
  content: '';
  position: absolute;
  top: -14px;
  width: 4px;
  height: 18px;
  border-radius: 999px;
  background: var(--outline);
}

.shape-horn .monster-head::before {
  left: 32px;
  transform: rotate(-18deg);
}

.shape-horn .monster-head::after {
  right: 32px;
  transform: rotate(18deg);
}

.shape-round .monster-head {
  width: 116px;
  border-radius: 40% 60% 48% 52%;
}

.shape-round .monster-antenna {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  top: -18px;
}

.shape-round .monster-body {
  width: 96px;
  border-radius: 28px 28px 18px 18px;
}

@keyframes head-shake {
  0% {
    transform: rotate(0deg);
  }
  25% {
    transform: rotate(-11deg);
  }
  50% {
    transform: rotate(11deg);
  }
  75% {
    transform: rotate(-9deg);
  }
  100% {
    transform: rotate(0deg);
  }
}

@keyframes monster-bounce {
  0% {
    transform: translateY(0);
  }
  35% {
    transform: translateY(-12px);
  }
  70% {
    transform: translateY(2px);
  }
  100% {
    transform: translateY(0);
  }
}

@media (max-width: 780px) {
  .monster-stage {
    gap: 8px;
  }

  .monster-head {
    width: 98px;
    height: 84px;
  }

  .monster-body {
    width: 74px;
    height: 40px;
  }

  .panel h1 {
    font-size: 24px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .monster,
  .monster *,
  .panel button {
    animation: none !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
