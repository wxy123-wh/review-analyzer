<template>
  <div class="mobile-nav" v-if="isAuthed">
    <!-- 根据角色显示不同的Tab -->
    <div class="nav-tabs">
      <template v-if="role === 'PM'">
        <RouterLink to="/clusters" class="nav-tab" :class="{ active: isActive('/clusters') }">
          <div class="tab-icon">📊</div>
          <div class="tab-label">聚类</div>
        </RouterLink>
        <RouterLink to="/pm" class="nav-tab" :class="{ active: isActive('/pm') }">
          <div class="tab-icon">🎯</div>
          <div class="tab-label">优先级</div>
        </RouterLink>
        <RouterLink to="/suggestions" class="nav-tab" :class="{ active: isActive('/suggestions') }">
          <div class="tab-icon">💡</div>
          <div class="tab-label">建议</div>
        </RouterLink>
        <RouterLink to="/before-after" class="nav-tab" :class="{ active: isActive('/before-after') }">
          <div class="tab-icon">🔄</div>
          <div class="tab-label">对比</div>
        </RouterLink>
      </template>

      <template v-else-if="role === 'MARKET'">
        <RouterLink to="/analysis" class="nav-tab" :class="{ active: isActive('/analysis') }">
          <div class="tab-icon">🔍</div>
          <div class="tab-label">关键词</div>
        </RouterLink>
        <RouterLink to="/topics" class="nav-tab" :class="{ active: isActive('/topics') }">
          <div class="tab-icon">🏷️</div>
          <div class="tab-label">主题</div>
        </RouterLink>
        <RouterLink to="/compare" class="nav-tab" :class="{ active: isActive('/compare') }">
          <div class="tab-icon">⚔️</div>
          <div class="tab-label">竞品</div>
        </RouterLink>
        <RouterLink to="/market" class="nav-tab" :class="{ active: isActive('/market') }">
          <div class="tab-icon">📈</div>
          <div class="tab-label">总览</div>
        </RouterLink>
      </template>

      <template v-else-if="role === 'OPS'">
        <RouterLink to="/overview" class="nav-tab" :class="{ active: isActive('/overview') }">
          <div class="tab-icon">📋</div>
          <div class="tab-label">总览</div>
        </RouterLink>
        <RouterLink to="/alerts" class="nav-tab" :class="{ active: isActive('/alerts') }">
          <div class="tab-icon">⚠️</div>
          <div class="tab-label">预警</div>
        </RouterLink>
        <RouterLink to="/events" class="nav-tab" :class="{ active: isActive('/events') }">
          <div class="tab-icon">📅</div>
          <div class="tab-label">活动</div>
        </RouterLink>
        <RouterLink to="/reviews" class="nav-tab" :class="{ active: isActive('/reviews') }">
          <div class="tab-icon">💬</div>
          <div class="tab-label">评论</div>
        </RouterLink>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuth } from '../stores/auth'

const route = useRoute()
const { isAuthed, role } = useAuth()

const isActive = (path) => {
  return route.path.startsWith(path)
}
</script>

<style scoped>
.mobile-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: linear-gradient(135deg, rgba(10, 10, 10, 0.95), rgba(26, 26, 26, 0.95));
  backdrop-filter: blur(20px);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.5);
  z-index: 1000;
  padding-bottom: env(safe-area-inset-bottom);
}

.nav-tabs {
  display: flex;
  height: 100%;
  justify-content: space-around;
  align-items: center;
  padding: 0 8px;
}

.nav-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  text-decoration: none;
  color: rgba(255, 255, 255, 0.5);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  padding: 8px 4px;
  border-radius: 12px;
  position: relative;
  overflow: hidden;
}

.nav-tab::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.1), rgba(138, 43, 226, 0.1));
  opacity: 0;
  transition: opacity 0.3s ease;
  border-radius: 12px;
  z-index: 0; /* Keep background layer behind content */
}

.nav-tab:active::before {
  opacity: 1;
}

.nav-tab.active {
  color: #00ffff;
}

.nav-tab.active::before {
  opacity: 0.8;
}

.tab-icon {
  font-size: 22px;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  z-index: 1; /* Ensure icon stays above ::before layer */
}

.nav-tab.active .tab-icon {
  transform: scale(1.15);
}

.nav-tab:active .tab-icon {
  transform: scale(0.95);
}

.tab-label {
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
  letter-spacing: 0.5px;
  position: relative;
  z-index: 1; /* Ensure label stays above ::before layer */
}

/* 触摸反馈 */
@media (hover: hover) {
  .nav-tab:hover {
    color: rgba(255, 255, 255, 0.8);
  }
}

/* 适配不同屏幕 */
@media (min-width: 768px) {
  .mobile-nav {
    display: none;
  }
}
</style>
