<template>
  <section
    data-testid="login-gate"
    class="login-gate"
    @mousemove="trackPointer"
    @mouseleave="clearTrail"
  >
    <div class="aurora aurora-a"></div>
    <div class="aurora aurora-b"></div>
    <div class="grid"></div>

    <div class="cursor-core" :style="cursorStyle"></div>
    <span
      v-for="point in trailPoints"
      :key="point.id"
      class="cursor-trail"
      :style="{ left: `${point.x}px`, top: `${point.y}px`, opacity: point.opacity }"
    />

    <div class="login-shell">
      <article class="mascot" :class="{ privacy: passwordFocused }">
        <div class="antenna"></div>
        <div class="head">
          <div class="eye">
            <span class="pupil" :style="pupilStyle"></span>
          </div>
          <div class="eye">
            <span class="pupil" :style="pupilStyle"></span>
          </div>
        </div>
        <div class="mouth"></div>
        <div class="arms">
          <span class="arm left"></span>
          <span class="arm right"></span>
        </div>
      </article>

      <form class="panel" @submit.prevent="submit">
        <h1>WH Quantum Console</h1>
        <p>演示入口：炫技模式已激活。</p>
        <label>
          用户名
          <input
            data-testid="login-username"
            v-model.trim="username"
            type="text"
            placeholder="demo"
            autocomplete="off"
          />
        </label>
        <label>
          密码
          <input
            data-testid="login-password"
            v-model.trim="password"
            type="password"
            placeholder="demo"
            @focus="passwordFocused = true"
            @blur="passwordFocused = false"
          />
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <button data-testid="login-submit" type="button" @click="submit">进入演示大厅</button>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const emit = defineEmits<{
  (event: 'enter', payload: { username: string }): void
}>()

type TrailPoint = {
  id: number
  x: number
  y: number
  opacity: number
}

const username = ref('')
const password = ref('')
const error = ref('')
const passwordFocused = ref(false)
const pointerX = ref(300)
const pointerY = ref(240)
const trailPoints = ref<TrailPoint[]>([])

const cursorStyle = computed(() => ({
  left: `${pointerX.value}px`,
  top: `${pointerY.value}px`,
}))

const pupilStyle = computed(() => {
  const x = Math.max(-5, Math.min(5, (pointerX.value - window.innerWidth / 2) / 40))
  const y = Math.max(-5, Math.min(5, (pointerY.value - window.innerHeight / 2) / 40))
  return {
    transform: `translate(${x}px, ${y}px)`,
  }
})

function trackPointer(event: MouseEvent): void {
  pointerX.value = event.clientX
  pointerY.value = event.clientY

  const nextPoint: TrailPoint = {
    id: Date.now() + Math.floor(Math.random() * 1000),
    x: event.clientX,
    y: event.clientY,
    opacity: 0.45,
  }
  trailPoints.value = [nextPoint, ...trailPoints.value.slice(0, 11)].map((point, index) => ({
    ...point,
    opacity: Math.max(0, 0.45 - index * 0.04),
  }))
}

function clearTrail(): void {
  trailPoints.value = []
}

function submit(): void {
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码（演示账号任意非空）'
    return
  }
  error.value = ''
  emit('enter', { username: username.value })
}
</script>

<style scoped>
.login-gate {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 15% 15%, #2cc8a3 0%, transparent 28%),
    radial-gradient(circle at 82% 20%, #37a9ff 0%, transparent 33%),
    radial-gradient(circle at 50% 88%, #ffd166 0%, transparent 34%),
    linear-gradient(140deg, #041026 0%, #0b1e3f 60%, #102d53 100%);
}

.grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(139, 194, 255, 0.16) 1px, transparent 1px),
    linear-gradient(90deg, rgba(139, 194, 255, 0.16) 1px, transparent 1px);
  background-size: 38px 38px;
  mask-image: radial-gradient(circle at center, black 25%, transparent 80%);
}

.aurora {
  position: absolute;
  width: 56vmax;
  height: 56vmax;
  filter: blur(48px);
  opacity: 0.4;
  border-radius: 50%;
  animation: drift 10s ease-in-out infinite alternate;
}

.aurora-a {
  top: -20vmax;
  left: -18vmax;
  background: #5fffd0;
}

.aurora-b {
  bottom: -24vmax;
  right: -20vmax;
  background: #6ec0ff;
  animation-delay: 1s;
}

.cursor-core {
  position: fixed;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  border: 2px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 0 18px rgba(120, 217, 255, 0.85);
}

.cursor-trail {
  position: fixed;
  width: 11px;
  height: 11px;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  background: rgba(141, 231, 255, 0.8);
  box-shadow: 0 0 14px rgba(105, 212, 255, 0.85);
}

.login-shell {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  gap: 26px;
  padding: 24px;
}

.mascot {
  position: relative;
  width: 160px;
  height: 168px;
}

.antenna {
  position: absolute;
  top: -28px;
  left: calc(50% - 2px);
  width: 4px;
  height: 28px;
  background: #9fcbff;
}

.head {
  width: 100%;
  height: 112px;
  border-radius: 24px;
  border: 2px solid rgba(151, 214, 255, 0.8);
  background: rgba(11, 29, 58, 0.76);
  display: flex;
  justify-content: center;
  gap: 20px;
  align-items: center;
  box-shadow: 0 0 22px rgba(97, 196, 255, 0.38);
}

.eye {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #f4fbff;
}

.pupil {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #0d3467;
  transition: transform 0.08s linear;
}

.mouth {
  margin: 10px auto 0;
  width: 62px;
  height: 10px;
  border-radius: 999px;
  background: rgba(135, 211, 255, 0.8);
}

.arms {
  position: relative;
  margin-top: 12px;
  height: 38px;
}

.arm {
  position: absolute;
  width: 48px;
  height: 12px;
  border-radius: 999px;
  background: rgba(180, 223, 255, 0.9);
  top: 10px;
  transition: transform 0.2s ease;
}

.left {
  left: 20px;
}

.right {
  right: 20px;
}

.mascot.privacy .left {
  transform: translate(15px, -32px) rotate(38deg);
}

.mascot.privacy .right {
  transform: translate(-15px, -32px) rotate(-38deg);
}

.panel {
  width: min(430px, 100%);
  border: 1px solid rgba(147, 210, 255, 0.45);
  border-radius: 20px;
  background: rgba(9, 25, 49, 0.68);
  box-shadow: 0 14px 38px rgba(7, 23, 53, 0.45);
  backdrop-filter: blur(8px);
  padding: 26px;
  display: grid;
  gap: 14px;
  color: #ebf6ff;
}

.panel h1 {
  margin: 0;
  font-size: 28px;
  letter-spacing: 1px;
}

.panel p {
  margin: 0;
  color: #b7d8ff;
}

.panel label {
  display: grid;
  gap: 8px;
  font-size: 13px;
}

.panel input {
  height: 42px;
  border-radius: 12px;
  border: 1px solid rgba(143, 205, 255, 0.5);
  background: rgba(8, 19, 40, 0.78);
  color: #f2f8ff;
  padding: 0 12px;
}

.panel input:focus {
  outline: none;
  border-color: #8be8ff;
  box-shadow: 0 0 0 2px rgba(126, 228, 255, 0.24);
}

.panel button {
  height: 44px;
  border: 0;
  border-radius: 999px;
  background: linear-gradient(90deg, #00c2ff, #38e9c4);
  color: #073159;
  font-weight: 700;
  cursor: pointer;
}

.error {
  color: #ffd0d0;
}

@keyframes drift {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(3vmax, -2vmax, 0) scale(1.06);
  }
}

@media (max-width: 780px) {
  .panel h1 {
    font-size: 24px;
  }

  .cursor-core,
  .cursor-trail {
    display: none;
  }
}
</style>
