<template>
  <section data-testid="login-gate" class="container">
    <div class="left-panel">
      <div class="left-spacer"></div>
      <div class="characters-area">
        <AnimatedCharacters
          :is-typing="isTyping"
          :show-password="showPassword"
          :password-length="password.length"
        />
      </div>
      <div class="left-spacer bottom"></div>
    </div>

    <div class="right-panel">
      <div class="form-wrapper">
      <form class="form" @submit.prevent="submit">
        <label class="field-label" for="login-username-input">账号</label>
        <div class="input-affix-wrapper" :class="{ focused: usernameFocused }">
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
            autocomplete="off"
            placeholder="输入您的账号"
            @focus="handleUsernameFocus"
            @blur="handleUsernameBlur"
          />
        </div>

        <label class="field-label" for="login-password-input">密码</label>
        <div class="input-affix-wrapper" :class="{ focused: passwordFocused }">
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
            placeholder="输入您的密码"
            @focus="handlePasswordFocus"
            @blur="handlePasswordBlur"
          />
          <button
            type="button"
            class="eye-toggle"
            aria-label="切换密码可见性"
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

        <div v-if="error" data-testid="login-error" class="error-box">{{ error }}</div>

        <button data-testid="login-submit" type="submit" class="submit-btn" :disabled="loading">
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

const emit = defineEmits<{
  (event: 'enter', payload: { username: string }): void
}>()

const DEMO_USERNAME = 'wxy'
const DEMO_PASSWORD = '123456'

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

  const valid = username.value === DEMO_USERNAME && password.value === DEMO_PASSWORD
  if (!valid) {
    error.value = '账号或密码有误，请重新输入'
    loading.value = false
    return
  }

  loading.value = false
  emit('enter', { username: username.value })
}
</script>

<style scoped>
.container {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background-color: #fff;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.1) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.1) 1px, transparent 1px);
  background-size: 44px 44px;
}

.left-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px;
}

.left-spacer {
  height: 40px;
  flex-shrink: 0;
}

.left-spacer.bottom {
  height: 26px;
}

.characters-area {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  height: 500px;
}

.right-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.form-wrapper {
  width: 100%;
  max-width: 400px;
}

.form {
  display: grid;
  gap: 10px;
  margin-top: 94px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: #475569;
}

.input-affix-wrapper {
  height: 48px;
  background: #fff;
  border: 1px solid #dce2e9;
  border-radius: 10px;
  transition: border-color 0.2s, box-shadow 0.2s;
  display: flex;
  align-items: center;
  padding: 0 12px;
}

.input-affix-wrapper:hover {
  border-color: #b8c3d3;
}

.input-affix-wrapper.focused {
  border-color: #5a6c87;
  box-shadow: 0 0 0 3px rgba(90, 108, 135, 0.12);
}

.input-affix-wrapper input {
  width: 100%;
  border: 0;
  background: transparent;
  font-size: 14px;
  color: #1e293b;
  outline: none;
}

.input-affix-wrapper input::placeholder {
  color: #94a3b8;
}

.prefix-icon {
  color: #94a3b8;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 8px;
}

.eye-toggle {
  border: 0;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.eye-toggle:hover {
  color: #334155;
}

.error-box {
  margin-top: 4px;
  padding: 10px 14px;
  font-size: 13px;
  color: #dc2626;
  background: #fff5f5;
  border: 1px solid #fecaca;
  border-radius: 8px;
}

.submit-btn {
  margin-top: 6px;
  height: 48px;
  border: 0;
  border-radius: 10px;
  background: #334155;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  cursor: pointer;
  transition: background 0.2s, opacity 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background: #1e293b;
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
  }

  .left-panel {
    padding: 26px 18px 0;
  }

  .left-spacer {
    display: none;
  }

  .characters-area {
    height: 360px;
  }

  .right-panel {
    padding: 12px 18px 28px;
  }

  .form-wrapper {
    width: min(420px, 100%);
  }

  .form {
    margin-top: 0;
  }
}
</style>
