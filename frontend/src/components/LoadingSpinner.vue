<template>
  <div :class="['loading-spinner', { 'full-screen': fullScreen }]">
    <div class="spinner-container" :style="{ width: `${size}px`, height: `${size}px` }">
      <div class="ring ring-outer" :style="{ borderColor: '#1f84af' }"></div>
      <div class="ring ring-middle" :style="{ borderColor: '#2cc1b5' }"></div>
      <div class="ring ring-inner" :style="{ borderColor: '#6c3ff5' }"></div>
    </div>
    <div v-if="text" class="loading-text">{{ text }}</div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  size?: number
  text?: string
  fullScreen?: boolean
}

withDefaults(defineProps<Props>(), {
  size: 80,
  text: '加载中...',
  fullScreen: false
})
</script>

<style scoped>
.loading-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
}

.loading-spinner.full-screen {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(255, 255, 255, 0.9);
  z-index: 9999;
}

.spinner-container {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ring {
  position: absolute;
  border-radius: 50%;
  border: 3px solid transparent;
  border-top-color: currentColor;
  animation: rotate 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
}

.ring-outer {
  width: 100%;
  height: 100%;
  animation-delay: 0s;
}

.ring-middle {
  width: 75%;
  height: 75%;
  animation-delay: 0.2s;
}

.ring-inner {
  width: 50%;
  height: 50%;
  animation-delay: 0.4s;
}

@keyframes rotate {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

.loading-text {
  font-size: 0.875rem;
  color: #4b5563;
  font-weight: 500;
  letter-spacing: 0.025em;
}

/* Mobile responsive */
@media (max-width: 640px) {
  .loading-text {
    font-size: 0.8125rem;
  }
}

/* Dark mode support */
@media (prefers-color-scheme: dark) {
  .loading-spinner.full-screen {
    background-color: rgba(17, 24, 39, 0.95);
  }

  .loading-text {
    color: #d1d5db;
  }
}
</style>
