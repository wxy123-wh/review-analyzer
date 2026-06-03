<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuth, clearAuth, setAuth } from '../stores/auth'
import { login } from '../api/auth'

const route = useRoute()
const router = useRouter()
const activePath = computed(() => route.path)

const { role } = useAuth()
const isSwitchingRole = ref(false)

const allRoles = [
  { value: 'PM', label: '产品', icon: '📊' },
  { value: 'MARKET', label: '市场', icon: '📈' },
  { value: 'OPS', label: '运营', icon: '⚡' },
]

function roleLabel(r) {
  const v = String(r || '').toUpperCase()
  if (v === 'PM') return '产品'
  if (v === 'MARKET') return '市场'
  if (v === 'OPS') return '运营'
  return '角色'
}

function roleIcon(r) {
  const v = String(r || '').toUpperCase()
  if (v === 'PM') return '📊'
  if (v === 'MARKET') return '📈'
  if (v === 'OPS') return '⚡'
  return '👤'
}

function roleHome(r) {
  const v = String(r || '').toUpperCase()
  if (v === 'PM') return '/dashboard/pm'
  if (v === 'MARKET') return '/dashboard/market'
  if (v === 'OPS') return '/dashboard/ops'
  return '/overview'
}

const menuItems = computed(() => {
  const r = String(role.value || '').toUpperCase()
  const base = [
    { path: roleHome(r), label: `${roleLabel(r)}看板`, icon: '🏠' },
    { path: '/overview', label: '总览', icon: '📋' },
  ]

  // 1. Comments (评论)
  base.push({ path: '/reviews', label: '评论', icon: '💬' })

  // 2. Role specific items
  if (r === 'PM') {
    base.push(
      { path: '/clusters', label: '聚类', icon: '🔷' },
      { path: '/suggestions', label: '建议', icon: '💡' },
      { path: '/events', label: '活动', icon: '🎯' },
      { path: '/before-after', label: '前后对比', icon: '⚖️' },
    )
  } else if (r === 'MARKET') {
    base.push(
      { path: '/topics', label: '主题', icon: '🏷️' },
      { path: '/compare', label: '竞品对比', icon: '🔄' },
    )
  } else if (r === 'OPS') {
    base.push(
      { path: '/alerts', label: '预警', icon: '🚨' },
      { path: '/events', label: '活动', icon: '🎯' },
      { path: '/before-after', label: '前后对比', icon: '⚖️' },
    )
  } else {
    base.push(
      { path: '/topics', label: '主题', icon: '🏷️' },
      { path: '/clusters', label: '聚类', icon: '🔷' },
    )
  }

  // 3. Dimension Analysis (维度分析)
  base.push({ path: '/analysis', label: '维度分析', icon: '🔍' })

  return base
})

function goHome() {
  router.push(roleHome(role.value))
}

async function switchRole(newRole) {
  if (isSwitchingRole.value) return
  if (newRole === role.value) return
  
  isSwitchingRole.value = true
  
  try {
    const roleId = newRole.toLowerCase()
    const response = await login({ 
      username: roleId, 
      password: '123456' 
    })
    
    setAuth({ token: response.token, role: response.role })
    await router.push(roleHome(newRole))
    
    ElMessage({
      message: `已切换到${roleLabel(newRole)}身份`,
      type: 'success',
      duration: 2000,
      grouping: true,
      offset: 20,
    })
  } catch (error) {
    ElMessage.error(error.message || '切换角色失败，请重试')
  } finally {
    isSwitchingRole.value = false
  }
}

function logout() {
  clearAuth()
  router.push('/login')
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar__brand" @click="goHome">
      <div class="brand-icon-wrapper">
        <span class="brand__icon">📱</span>
      </div>
      <span class="brand__text">口碑分析</span>
    </div>

    <div class="sidebar__menu-wrapper">
      <el-menu
        :default-active="activePath"
        class="sidebar__menu"
        router
      >
        <el-menu-item v-for="m in menuItems" :key="m.path" :index="m.path">
          <span class="menu-item__icon">{{ m.icon }}</span>
          <template #title>
            <span>{{ m.label }}</span>
          </template>
        </el-menu-item>
      </el-menu>
    </div>

    <div class="sidebar__footer">
      <el-dropdown @command="switchRole" trigger="click" :disabled="isSwitchingRole" placement="top">
        <div class="role-card" :class="{ 'role-card--disabled': isSwitchingRole }">
          <div class="role-card__avatar">
            {{ roleIcon(role) }}
          </div>
          <div class="role-card__info">
            <span class="role-name">{{ roleLabel(role) }}</span>
            <span class="role-status">在线</span>
          </div>
          <span class="role-arrow">▼</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="r in allRoles"
              :key="r.value"
              :command="r.value"
              :disabled="r.value === role"
            >
              <span class="dropdown-item__icon">{{ r.icon }}</span>
              <span>{{ r.label }}</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <div class="logout-wrapper" @click="logout">
        <span class="logout-icon">🚪</span>
        <span class="logout-text">退出登录</span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 260px;
  height: 100vh;
  background: var(--neu-bg-card);
  display: flex;
  flex-direction: column;
  padding: 24px 16px;
  box-shadow: 4px 0 12px rgba(174, 174, 192, 0.15);
  position: relative;
  z-index: 200;
  transition: all 0.3s ease;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 32px;
  cursor: pointer;
  border-radius: var(--neu-radius-md);
  transition: all var(--neu-transition-base);
}

.sidebar__brand:hover {
  background: rgba(108, 155, 209, 0.05);
}

.brand-icon-wrapper {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--neu-bg-base);
  border-radius: var(--neu-radius-sm);
  box-shadow: var(--neu-shadow-inset);
  font-size: 20px;
}

.brand__text {
  font-family: 'Poppins', sans-serif;
  font-weight: 700;
  font-size: 18px;
  color: var(--neu-text-primary);
  letter-spacing: -0.5px;
}

.sidebar__menu-wrapper {
  flex: 1;
  overflow-y: auto;
  margin-bottom: 24px;
}

.sidebar__menu {
  border-right: none;
  background: transparent;
}

:deep(.el-menu-item) {
  height: 50px;
  line-height: 50px;
  margin-bottom: 8px;
  border-radius: var(--neu-radius-md);
  color: var(--neu-text-secondary);
  font-weight: 600;
}

:deep(.el-menu-item:hover) {
  color: var(--neu-primary);
  background: var(--neu-bg-base);
}

:deep(.el-menu-item.is-active) {
  color: var(--neu-primary);
  background: var(--neu-bg-base);
  box-shadow: var(--neu-shadow-inset);
}

.menu-item__icon {
  margin-right: 12px;
  font-size: 18px;
  width: 24px;
  text-align: center;
}

.sidebar__footer {
  padding-top: 20px;
  border-top: 1px solid rgba(90, 106, 127, 0.1);
}

.role-card {
  display: flex;
  align-items: center;
  padding: 12px;
  background: var(--neu-bg-base);
  border-radius: var(--neu-radius-md);
  cursor: pointer;
  transition: all var(--neu-transition-base);
  box-shadow: var(--neu-shadow-light);
  margin-bottom: 12px;
}

.role-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--neu-shadow-hover);
}

.role-card:active {
  transform: translateY(0);
  box-shadow: var(--neu-shadow-inset);
}

.role-card__avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--neu-bg-card);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  margin-right: 12px;
  box-shadow: var(--neu-shadow-light);
}

.role-card__info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.role-name {
  font-weight: 700;
  color: var(--neu-text-primary);
  font-size: 14px;
}

.role-status {
  font-size: 11px;
  color: var(--neu-success);
  margin-top: 2px;
}

.role-arrow {
  font-size: 10px;
  color: var(--neu-text-tertiary);
}

.logout-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  color: var(--neu-text-secondary);
  cursor: pointer;
  border-radius: var(--neu-radius-md);
  transition: all var(--neu-transition-base);
  font-size: 14px;
  font-weight: 500;
}

.logout-wrapper:hover {
  color: var(--neu-error);
  background: rgba(232, 139, 143, 0.1);
}

/* Scrollbar hidden for menu but scrollable */
.sidebar__menu-wrapper::-webkit-scrollbar {
  width: 4px;
}
.sidebar__menu-wrapper::-webkit-scrollbar-thumb {
  background: transparent;
}
.sidebar__menu-wrapper:hover::-webkit-scrollbar-thumb {
  background: rgba(90, 106, 127, 0.2);
}

@media (max-width: 1024px) {
  .sidebar {
    width: 80px;
    padding: 24px 12px;
  }
  
  .brand__text,
  .role-card__info,
  .role-arrow,
  .logout-text,
  :deep(.el-menu-item .el-menu-tooltip__trigger) {
    /* Element Plus handles collapse mostly via props, but we are doing responsive custom styling here for simple collapse */
    /* Or we can just use element plus collapse mode */
  }
  
  /* Simple collapse simulation */
  .brand__text { display: none; }
  .role-card__info { display: none; }
  .role-arrow { display: none; }
  .logout-text { display: none; }
  .sidebar__brand { justify-content: center; padding: 12px 0; }
  .brand-icon-wrapper { margin: 0; }
  .role-card { padding: 10px; justify-content: center; }
  .role-card__avatar { margin: 0; }
  .logout-wrapper { justify-content: center; }
  :deep(.el-menu-item span) { display: none; } 
  :deep(.el-menu-item .menu-item__icon) { margin: 0; width: 100%; font-size: 20px; }
}

/* Mobile: Sidebar usually hidden, handled by MobileNav or Drawer. For now, hide on mobile */
@media (max-width: 768px) {
  .sidebar {
    display: none;
  }
}
</style>
