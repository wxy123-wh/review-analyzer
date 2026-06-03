<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import GlobalFiltersBar from './components/GlobalFiltersBar.vue'
import MobileNav from './components/MobileNav.vue'
import InstallPrompt from './components/InstallPrompt.vue'
import AppSidebar from './components/AppSidebar.vue'

const route = useRoute()
const isLoginRoute = computed(() => route.path === '/login')
</script>

<template>
  <router-view v-if="isLoginRoute" />

  <el-container v-else class="app">
    <!-- 左侧导航 -->
    <AppSidebar />

    <!-- 右侧主体 -->
    <el-container class="app__content">
      <el-header class="app__header">
        <GlobalFiltersBar />
      </el-header>

      <el-main class="app__main">
        <transition name="page-transition" mode="out-in">
          <router-view :key="route.path" />
        </transition>
      </el-main>

      <!-- 移动端底部导航 (只在小屏显示) -->
      <MobileNav />
      
      <!-- PWA安装提示 -->
      <InstallPrompt />
    </el-container>
  </el-container>
</template>

<style scoped>
.app {
  min-height: 100vh;
  background: var(--neu-bg-light);
  display: flex;
}

.app__content {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden; /* 防止双重滚动 */
}

.app__header {
  height: auto;
  min-height: 64px;
  padding: 12px 24px;
  background: rgba(240, 243, 247, 0.8); /* 透明背景以便内容滚动时透出(如果有) - 但这里是header */
  background: var(--neu-bg-light);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: flex-end; /* 过滤器靠右 */
  z-index: 100;
  flex-shrink: 0;
}

.app__main {
  flex: 1;
  padding: 24px 32px;
  background: transparent;
  overflow-y: auto; /* 主内容滚动 */
  height: 100%;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .app__content {
    height: auto; /* 移动端通常不需要固定高度 */
    min-height: 100vh;
  }

  .app__header {
    justify-content: center;
    padding: 12px 16px;
    position: sticky;
    top: 0;
    box-shadow: var(--neu-shadow-light);
  }

  .app__main {
    padding: 16px;
    padding-bottom: calc(60px + env(safe-area-inset-bottom));
  }
}
</style>
