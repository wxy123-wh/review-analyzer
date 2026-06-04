<template>
  <section data-testid="login-gate" class="container">
    <div class="ambient-orb ambient-orb-left" aria-hidden="true"></div>
    <div class="ambient-orb ambient-orb-right" aria-hidden="true"></div>

    <div class="auth-stage" data-motion-reveal style="--motion-delay: 40ms">
      <div class="stage-identity" data-motion-reveal data-motion-spotlight="soft" style="--motion-delay: 120ms">
        <div class="characters-frame">
          <div class="characters-halo" aria-hidden="true"></div>
          <div class="characters-area">
            <AnimatedCharacters
              :is-typing="isTyping"
              :show-password="showPassword"
              :password-length="password.length"
            />
          </div>
        </div>
      </div>
    </div>

    <div class="auth-panel">
      <div class="panel-shell" data-motion-reveal data-motion-spotlight="soft" style="--motion-delay: 120ms">
        <div class="credential-note" data-motion-hover="lift">
          <div class="credential-note-grid">
            <div>
              <span>账号</span>
              <strong>{{ expectedUsername }}</strong>
            </div>
            <div>
              <span>密码</span>
              <strong>{{ expectedPassword }}</strong>
            </div>
          </div>
        </div>

        <form class="form" @submit.prevent="submit">
          <label class="field-label" for="login-username-input">账号</label>
          <div class="input-affix-wrapper" :class="{ focused: usernameFocused }" data-motion-hover="lift">
            <span class="prefix-icon" aria-hidden="true">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21a8 8 0 0 0-16 0"></path>
                <circle cx="12" cy="7" r="4"></circle>
              </svg>
            </span>
            <input
              id="login-username-input"
              data-testid="login-username"
              v-model.trim="username"
              type="text"
              autocomplete="username"
              placeholder="输入您的账号"
              :aria-invalid="error ? 'true' : 'false'"
              :aria-describedby="error ? 'login-error-message' : undefined"
              @focus="handleUsernameFocus"
              @blur="handleUsernameBlur"
            />
          </div>

          <label class="field-label" for="login-password-input">密码</label>
          <div class="input-affix-wrapper" :class="{ focused: passwordFocused }" data-motion-hover="lift">
            <span class="prefix-icon" aria-hidden="true">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="10" rx="2"></rect>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
              </svg>
            </span>
            <input
              id="login-password-input"
              data-testid="login-password"
              v-model.trim="password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="输入您的密码"
              :aria-invalid="error ? 'true' : 'false'"
              :aria-describedby="error ? 'login-error-message' : undefined"
              @focus="handlePasswordFocus"
              @blur="handlePasswordBlur"
            />
            <button
              type="button"
              class="eye-toggle"
              aria-label="切换密码可见性"
              :aria-pressed="showPassword"
              data-motion-hover="lift"
              @click="showPassword = !showPassword"
            >
              <svg
                v-if="showPassword"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"></path>
                <circle cx="12" cy="12" r="3"></circle>
              </svg>
              <svg
                v-else
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path d="M17.94 17.94A10.94 10.94 0 0 1 12 19C5 19 1 12 1 12a21.8 21.8 0 0 1 5.06-6.94"></path>
                <path d="M9.9 4.24A10.87 10.87 0 0 1 12 4c7 0 11 8 11 8a22.77 22.77 0 0 1-2.16 3.19"></path>
                <path d="M1 1l22 22"></path>
              </svg>
            </button>
          </div>

          <div v-if="error" id="login-error-message" data-testid="login-error" class="error-box" role="alert">{{ error }}</div>

          <button data-testid="login-submit" type="submit" class="submit-btn" :disabled="loading" data-motion-hover="lift">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import AnimatedCharacters from './AnimatedCharacters.vue'

const props = withDefaults(defineProps<{
  expectedUsername?: string
  expectedPassword?: string
  displayName?: string
}>(), {
  expectedUsername: 'wxy',
  expectedPassword: '123456',
  displayName: '内部分析员',
})

const emit = defineEmits<{
  (event: 'enter', payload: { username: string; displayName: string }): void
}>()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')
const showPassword = ref(false)
const isTyping = ref(false)
const usernameFocused = ref(false)
const passwordFocused = ref(false)

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function handleUsernameFocus(): void {
  usernameFocused.value = true
  isTyping.value = true
}

function handleUsernameBlur(): void {
  usernameFocused.value = false
  isTyping.value = false
}

function handlePasswordFocus(): void {
  passwordFocused.value = true
}

function handlePasswordBlur(): void {
  passwordFocused.value = false
}

async function submit(): Promise<void> {
  error.value = ''

  if (!username.value || username.value.length < 3) {
    error.value = '请输入账号'
    return
  }

  if (!password.value || password.value.length < 6) {
    error.value = '请输入密码'
    return
  }

  loading.value = true
  await wait(800)

  const valid = username.value === props.expectedUsername && password.value === props.expectedPassword
  if (!valid) {
    error.value = '账号或密码有误，请重新输入'
    loading.value = false
    return
  }

  loading.value = false
  emit('enter', { username: username.value, displayName: props.displayName })
}
</script>

<style scoped>
.container {
  position: relative;
  isolation: isolate;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 640px) minmax(320px, 380px);
  align-items: center;
  justify-content: center;
  gap: clamp(var(--space-5), 6vw, var(--space-8));
  padding: clamp(var(--space-5), 5vw, var(--space-8));
  background-color: var(--color-canvas);
  background-image: var(--gradient-login-canvas);
  background-size: 44px 44px;
  overflow: hidden;
}

.ambient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(36px);
  opacity: 0.7;
  pointer-events: none;
  z-index: var(--z-base);
}

.ambient-orb-left {
  width: 320px;
  height: 320px;
  top: calc(var(--space-8) * -1);
  left: calc(var(--space-8) * -1);
  background: rgba(122, 184, 255, 0.12);
}

.ambient-orb-right {
  width: 260px;
  height: 260px;
  right: calc(var(--space-6) * -1);
  bottom: calc(var(--space-6) * -1);
  background: rgba(102, 224, 194, 0.1);
}

.auth-stage,
.auth-panel {
  position: relative;
  z-index: var(--z-raised);
}

.auth-stage {
  min-width: 0;
  display: grid;
  align-content: center;
}

.stage-identity {
  position: relative;
  display: grid;
  width: 100%;
  max-width: 640px;
  padding: clamp(var(--space-4), 3vw, var(--space-6));
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  background: linear-gradient(180deg, rgba(10, 20, 37, 0.78), rgba(6, 12, 22, 0.9));
  box-shadow: var(--shadow-panel);
  backdrop-filter: blur(18px);
}

.characters-frame {
  position: relative;
  overflow: hidden;
  min-height: clamp(420px, 66vh, 600px);
  border-radius: calc(var(--radius-xl) + var(--space-1));
  border: 1px solid rgba(255, 255, 255, 0.05);
  background:
    radial-gradient(circle at 50% 28%, rgba(122, 184, 255, 0.14) 0%, transparent 44%),
    linear-gradient(180deg, rgba(15, 28, 48, 0.68), rgba(7, 13, 24, 0.98));
}

.characters-halo {
  position: absolute;
  inset: auto 12% 14% 12%;
  height: 120px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(102, 224, 194, 0.22) 0%, rgba(102, 224, 194, 0.08) 38%, transparent 72%);
  filter: blur(12px);
  pointer-events: none;
}

.characters-area {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  min-height: clamp(420px, 66vh, 600px);
  padding: clamp(var(--space-3), 3vw, var(--space-5));
}

.auth-panel {
  display: flex;
  align-items: center;
  min-width: 0;
  justify-content: flex-start;
}

.panel-shell {
  width: 100%;
  max-width: 380px;
  display: grid;
  gap: var(--space-5);
  padding: clamp(var(--space-5), 3vw, var(--space-6));
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border-default);
  background: linear-gradient(180deg, rgba(14, 26, 46, 0.96), rgba(7, 14, 25, 0.98));
  box-shadow: var(--shadow-panel);
  backdrop-filter: blur(18px);
}

.credential-note {
  display: grid;
  padding: var(--space-2);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-subtle);
  background: var(--color-surface-overlay);
}

.credential-note-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
}

.credential-note-grid div {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.04);
}

.credential-note-grid span {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.credential-note-grid strong {
  overflow: hidden;
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form {
  display: grid;
  gap: var(--space-3);
}

.field-label {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-text-secondary);
}

.input-affix-wrapper {
  height: 52px;
  background: var(--color-surface-input);
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-md);
  transition:
    border-color var(--motion-medium) var(--easing-standard),
    box-shadow var(--motion-medium) var(--easing-standard),
    background-color var(--motion-medium) var(--easing-standard);
  display: flex;
  align-items: center;
  padding: 0 var(--space-3);
  box-shadow: var(--shadow-inset-soft);
}

.input-affix-wrapper:hover {
  border-color: var(--color-border-strong);
}

.input-affix-wrapper.focused {
  border-color: var(--color-accent-primary);
  box-shadow: var(--shadow-focus);
}

.input-affix-wrapper:focus-within {
  border-color: var(--color-accent-primary);
  box-shadow: var(--shadow-focus);
}

.input-affix-wrapper input {
  width: 100%;
  border: 0;
  background: transparent;
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
  outline: none;
}

.input-affix-wrapper input::placeholder {
  color: var(--color-text-muted);
}

.prefix-icon {
  color: var(--color-text-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: var(--space-2);
}

.eye-toggle {
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 2.5rem;
  min-height: 2.5rem;
  padding: var(--space-1);
  border-radius: var(--radius-pill);
  transition: color var(--motion-fast) var(--easing-standard);
}

.eye-toggle:hover {
  color: var(--color-text-primary);
}

.eye-toggle:focus-visible {
  color: var(--color-text-primary);
  box-shadow: var(--shadow-focus);
}

.error-box {
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-sm);
  color: var(--color-semantic-down);
  background: var(--color-semantic-down-soft);
  border: 1px solid var(--color-semantic-down);
  border-radius: var(--radius-sm);
}

.submit-btn {
  height: 52px;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--gradient-accent);
  color: var(--color-text-inverse);
  font-size: var(--font-size-lg);
  font-weight: 600;
  letter-spacing: 1px;
  cursor: pointer;
  transition:
    transform var(--motion-fast) var(--easing-standard),
    filter var(--motion-medium) var(--easing-standard),
    opacity var(--motion-fast) var(--easing-standard);
  box-shadow: var(--shadow-glow);
}

.submit-btn:hover:not(:disabled) {
  filter: brightness(1.05);
  transform: translateY(-1px);
}

.submit-btn:active:not(:disabled) {
  opacity: 0.88;
}

.submit-btn:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

@media (max-width: 1024px) {
  .container {
    grid-template-columns: 1fr;
    gap: var(--space-5);
    padding: var(--space-6) var(--space-4);
  }

  .auth-panel {
    order: -1;
  }

  .auth-stage {
    padding: 0;
  }

  .characters-frame,
  .characters-area {
    min-height: 380px;
  }

  .auth-panel {
    justify-content: stretch;
  }

  .panel-shell {
    max-width: none;
    padding: var(--space-5);
  }

}

@media (max-width: 640px) {
  .container {
    padding: var(--space-4);
  }

  .ambient-orb {
    opacity: 0.48;
  }

  .stage-identity,
  .panel-shell {
    padding: var(--space-4);
  }

  .assistive-copy {
    font-size: var(--font-size-md);
  }

  .characters-frame,
  .characters-area {
    min-height: 320px;
  }

  .credential-note-grid {
    grid-template-columns: 1fr;
  }
}

html[data-motion='reduce'] .ambient-orb,
html[data-motion='none'] .ambient-orb,
html[data-motion='reduce'] .characters-halo,
html[data-motion='none'] .characters-halo {
  opacity: 0.32;
  filter: blur(14px);
}

html[data-motion='reduce'] .stage-identity,
html[data-motion='none'] .stage-identity,
html[data-motion='reduce'] .panel-shell,
html[data-motion='none'] .panel-shell {
  backdrop-filter: none;
}
</style>
