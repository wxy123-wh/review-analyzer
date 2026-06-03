<template>
  <section data-testid="login-gate" class="container">
    <div class="ambient-orb ambient-orb-left" aria-hidden="true"></div>
    <div class="ambient-orb ambient-orb-right" aria-hidden="true"></div>

    <div class="auth-stage" data-motion-reveal style="--motion-delay: 40ms">
      <div class="stage-copy" data-motion-reveal style="--motion-delay: 80ms">
        <span class="stage-kicker">Internal-use access</span>
        <h1>用内部体验入口守住首发基线。</h1>
        <p>
          这里仍然只做前端内部访问门禁：不接入真实认证，只把当前环境下的凭据、提示和进入行为收敛成可配置的首发入口。
        </p>
      </div>

      <div class="stage-identity" data-motion-reveal data-motion-spotlight="soft" style="--motion-delay: 120ms">
        <div class="identity-header">
          <span class="identity-chip">旗舰视觉识别</span>
          <span class="identity-caption">四只角色继续承担注意力与交互反馈</span>
        </div>
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

      <div class="stage-context" data-motion-reveal style="--motion-delay: 160ms">
        <article class="context-card" data-motion-hover="lift" data-motion-spotlight="soft">
          <span class="context-label">访问说明</span>
          <p>不接入真实单点登录，也不伪装企业集成，只保留当前仓库需要的内部体验门禁。</p>
        </article>
        <article class="context-card" data-motion-hover="lift" data-motion-spotlight="soft">
          <span class="context-label">交互锚点</span>
          <p>输入时角色联动、鼠标驱动瞳孔追踪、点击跳跃、双击提亮，以及密码可见性反馈全部保留。</p>
        </article>
      </div>
    </div>

    <div class="auth-panel">
      <div class="panel-shell" data-motion-reveal data-motion-spotlight="soft" style="--motion-delay: 120ms">
        <div class="panel-topline">
          <span class="panel-eyebrow">内部访问</span>
          <span class="panel-status">环境门禁已启用</span>
        </div>

        <div class="panel-copy">
          <h2>使用当前环境凭据进入看板</h2>
          <p>凭据来自当前前端环境配置，登录成功后仍会触发现有的 <code>enter</code> 事件。</p>
        </div>

        <div class="demo-note" data-motion-hover="lift">
          <span class="demo-note-label">当前环境凭据</span>
          <div class="demo-note-grid">
            <div>
              <span>账号</span>
              <strong>{{ expectedUsername }}</strong>
            </div>
            <div>
              <span>密码</span>
              <strong>{{ expectedPassword }}</strong>
            </div>
          </div>
          <p v-if="accessHint" class="demo-note-hint">{{ accessHint }}</p>
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
              aria-describedby="login-helper login-error-message"
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
              aria-describedby="login-helper login-error-message"
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

          <p id="login-helper" class="assistive-copy">使用当前环境配置的内部访问凭据进入，键盘 Tab 顺序保持为账号、密码、可见性切换、登录按钮。</p>

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
  accessHint?: string
}>(), {
  expectedUsername: 'wxy',
  expectedPassword: '123456',
  displayName: '内部体验账号',
  accessHint: '',
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
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 420px);
  gap: var(--space-8);
  padding: var(--space-8);
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
  align-content: space-between;
  gap: var(--space-6);
  padding: var(--space-4) 0;
}

.stage-copy {
  display: grid;
  gap: var(--space-3);
  max-width: 60ch;
}

.stage-kicker,
.identity-chip,
.context-label,
.panel-eyebrow,
.panel-status,
.demo-note-label {
  width: fit-content;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border-default);
  background: var(--color-surface-overlay);
  box-shadow: var(--shadow-inset-soft);
}

.stage-kicker,
.identity-chip,
.context-label,
.panel-eyebrow,
.panel-status,
.demo-note-label {
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.stage-kicker,
.identity-chip,
.panel-eyebrow,
.demo-note-label {
  color: var(--color-accent-primary);
}

.panel-status,
.context-label {
  color: var(--color-text-secondary);
}

.stage-copy h1,
.panel-copy h2 {
  margin: 0;
  color: var(--color-text-primary);
  line-height: var(--line-height-tight);
  letter-spacing: -0.03em;
}

.stage-copy h1 {
  max-width: 12ch;
  font-size: clamp(2.75rem, 5vw, 4.75rem);
}

.stage-copy p,
.panel-copy p,
.context-card p {
  margin: 0;
  color: var(--color-text-secondary);
}

.stage-copy p {
  max-width: 58ch;
  font-size: var(--font-size-lg);
  text-wrap: balance;
}

.stage-identity {
  position: relative;
  display: grid;
  gap: var(--space-4);
  padding: var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  background: linear-gradient(180deg, rgba(10, 20, 37, 0.78), rgba(6, 12, 22, 0.9));
  box-shadow: var(--shadow-panel);
  backdrop-filter: blur(18px);
}

.identity-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.identity-caption {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.characters-frame {
  position: relative;
  overflow: hidden;
  min-height: 440px;
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
  min-height: 440px;
  padding: var(--space-4);
}

.stage-context {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.context-card {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-subtle);
  background: linear-gradient(180deg, rgba(11, 21, 38, 0.94), rgba(8, 16, 29, 0.9));
  box-shadow: var(--shadow-raised);
}

.auth-panel {
  display: flex;
  align-items: center;
  justify-content: center;
}

.panel-shell {
  width: 100%;
  max-width: 420px;
  display: grid;
  gap: var(--space-5);
  padding: var(--space-6);
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border-default);
  background: linear-gradient(180deg, rgba(14, 26, 46, 0.96), rgba(7, 14, 25, 0.98));
  box-shadow: var(--shadow-panel);
  backdrop-filter: blur(18px);
}

.panel-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.panel-copy {
  display: grid;
  gap: var(--space-3);
}

.panel-copy h2 {
  font-size: clamp(1.75rem, 3vw, 2.25rem);
}

.panel-copy code {
  color: var(--color-accent-secondary);
  font-family: inherit;
}

.demo-note {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-subtle);
  background: var(--color-surface-overlay);
}

.demo-note-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.demo-note-hint {
  margin: var(--space-3) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.demo-note-grid div {
  display: grid;
  gap: var(--space-1);
}

.demo-note-grid span {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.demo-note-grid strong {
  font-size: var(--font-size-lg);
  color: var(--color-text-primary);
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
  height: 48px;
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

.assistive-copy {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
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
  height: 48px;
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

  .stage-copy h1 {
    max-width: none;
  }

  .stage-context {
    grid-template-columns: 1fr;
  }

  .characters-frame,
  .characters-area {
    min-height: 360px;
  }

  .auth-panel {
    justify-content: stretch;
  }

  .panel-shell {
    max-width: none;
    padding: var(--space-5);
  }

  .panel-topline,
  .identity-header {
    align-items: flex-start;
    flex-direction: column;
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

  .stage-copy p,
  .assistive-copy {
    font-size: var(--font-size-md);
  }

  .characters-frame,
  .characters-area {
    min-height: 320px;
  }

  .demo-note-grid {
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
