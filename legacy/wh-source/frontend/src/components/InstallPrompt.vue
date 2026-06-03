<template>
  <transition name="slide-up">
    <div v-if="showPrompt" class="install-prompt">
      <div class="install-prompt__container">
        <div class="install-prompt__icon">📱</div>
        <div class="install-prompt__content">
          <div class="install-prompt__title">添加到主屏幕</div>
          <div class="install-prompt__text">安装应用以获得更好的体验</div>
        </div>
        <div class="install-prompt__actions">
          <button class="install-prompt__btn install-prompt__btn--primary" @click="install">
            安装
          </button>
          <button class="install-prompt__btn install-prompt__btn--secondary" @click="dismiss">
            稍后
          </button>
        </div>
        <button class="install-prompt__close" @click="dismiss">✕</button>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const showPrompt = ref(false)
let deferredPrompt = null

onMounted(() => {
  // 监听PWA安装提示事件
  window.addEventListener('beforeinstallprompt', (e) => {
    // 阻止默认的安装提示
    e.preventDefault()
    
    // 保存事件，稍后使用
    deferredPrompt = e
    
    // 检查用户是否之前拒绝过
    const dismissed = localStorage.getItem('pwa-install-dismissed')
    const dismissedTime = dismissed ? parseInt(dismissed) : 0
    const now = Date.now()
    
    // 如果上次拒绝超过7天，再次显示
    if (!dismissed || (now - dismissedTime) > 7 * 24 * 60 * 60 * 1000) {
      // 延迟3秒显示，避免立即打扰用户
      setTimeout(() => {
        showPrompt.value = true
      }, 3000)
    }
  })
  
  // 监听应用安装成功
  window.addEventListener('appinstalled', () => {
    showPrompt.value = false
    console.log('PWA安装成功')
  })
})

async function install() {
  if (!deferredPrompt) return
  
  // 显示安装提示
  deferredPrompt.prompt()
  
  // 等待用户响应
  const { outcome } = await deferredPrompt.userChoice
  
  console.log(`用户选择: ${outcome}`)
  
  // 清除deferredPrompt
  deferredPrompt = null
  showPrompt.value = false
}

function dismiss() {
  showPrompt.value = false
  
  // 记录拒绝时间
  localStorage.setItem('pwa-install-dismissed', Date.now().toString())
}
</script>

<style scoped>
.install-prompt {
  position: fixed;
  bottom: 80px; /* 避开移动端导航 */
  left: 16px;
  right: 16px;
  z-index: 2000;
  pointer-events: none;
}

.install-prompt__container {
  background: linear-gradient(135deg, rgba(10, 10, 10, 0.98), rgba(26, 26, 26, 0.98));
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 16px 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5),
              0 0 0 1px rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  gap: 16px;
  pointer-events: all;
  position: relative;
}

.install-prompt__icon {
  font-size: 36px;
  flex-shrink: 0;
  filter: drop-shadow(0 0 8px rgba(0, 255, 255, 0.5));
}

.install-prompt__content {
  flex: 1;
  min-width: 0;
}

.install-prompt__title {
  font-size: 15px;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 4px;
}

.install-prompt__text {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

.install-prompt__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.install-prompt__btn {
  padding: 8px 16px;
  border-radius: 8px;
  border: none;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.install-prompt__btn--primary {
  background: linear-gradient(135deg, #00ffff, #00cccc);
  color: #0a0a0a;
  box-shadow: 0 2px 8px rgba(0, 255, 255, 0.3);
}

.install-prompt__btn--primary:active {
  transform: scale(0.95);
  box-shadow: 0 1px 4px rgba(0, 255, 255, 0.3);
}

.install-prompt__btn--secondary {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.install-prompt__btn--secondary:active {
  background: rgba(255, 255, 255, 0.15);
  transform: scale(0.95);
}

.install-prompt__close {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.install-prompt__close:hover {
  background: rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.9);
}

/* 动画 */
.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-up-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.slide-up-leave-to {
  opacity: 0;
  transform: translateY(20px);
}

/* 桌面端样式 */
@media (min-width: 768px) {
  .install-prompt {
    bottom: 24px;
    left: auto;
    right: 24px;
    max-width: 400px;
  }
}

/* 小屏幕优化 */
@media (max-width: 480px) {
  .install-prompt__actions {
    flex-direction: column;
    width: 100%;
  }
  
  .install-prompt__btn {
    width: 100%;
  }
  
  .install-prompt__container {
    flex-wrap: wrap;
  }
  
  .install-prompt__close {
    top: 12px;
    right: 12px;
  }
}
</style>
