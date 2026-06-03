<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { setAuth } from '../stores/auth'
import { login } from '../api/auth'

const route = useRoute()
const router = useRouter()

const isLoading = ref(false)
const showAccounts = ref(false)

// 登录表单数据
const loginForm = reactive({
  username: '',
  password: ''
})

// 表单验证规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, message: '用户名至少2个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6个字符', trigger: 'blur' }
  ]
}

const formRef = ref(null)

// 角色首页映射
function roleHome(role) {
  const r = String(role || '').toUpperCase()
  if (r === 'PM') return '/dashboard/pm'
  if (r === 'MARKET') return '/dashboard/market'
  if (r === 'OPS') return '/dashboard/ops'
  return '/overview'
}

// 处理登录
async function handleLogin() {
  if (!formRef.value) return
  
  // 验证表单
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    isLoading.value = true
    
    try {
      // 调用真实的登录API
      const response = await login({ 
        username: loginForm.username.trim(), 
        password: loginForm.password 
      })
      
      // 设置真实token和角色
      setAuth({ token: response.token, role: response.role })
      
      ElMessage.success('登录成功！')
      
      // 导航到对应的看板
      const redirect = route.query?.redirect ? String(route.query.redirect) : ''
      setTimeout(() => {
        router.replace(redirect || roleHome(response.role))
      }, 300)
    } catch (error) {
      ElMessage.error(error.message || '登录失败，请检查用户名和密码')
    } finally {
      isLoading.value = false
    }
  })
}

// 快捷登录（演示用）
function quickLogin(username) {
  loginForm.username = username
  loginForm.password = '123456'
  handleLogin()
}
</script>

<template>
  <div class="login">
    <div class="login__container">
      <div class="login__header">
        <div class="login__logo">
          <div class="logo-circle">
            <span class="logo-icon">📱</span>
          </div>
        </div>
        <h1 class="login__title">电商口碑分析系统</h1>
        <p class="login__subtitle">欢迎回来，请登录您的账号</p>
      </div>

      <div class="login__content">
        <el-form
          ref="formRef"
          :model="loginForm"
          :rules="rules"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <div class="input-wrapper">
              <span class="input-icon">👤</span>
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                size="large"
                :disabled="isLoading"
                @keyup.enter="handleLogin"
              />
            </div>
          </el-form-item>

          <el-form-item prop="password">
            <div class="input-wrapper">
              <span class="input-icon">🔒</span>
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                :disabled="isLoading"
                show-password
                @keyup.enter="handleLogin"
              />
            </div>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="login-button"
              :loading="isLoading"
              @click="handleLogin"
            >
              <span v-if="!isLoading">登录</span>
              <span v-else>登录中...</span>
            </el-button>
          </el-form-item>
        </el-form>

        <div class="login__footer">
          <div class="login__hint" @click="showAccounts = !showAccounts">
            <span class="hint-dot"></span>
            <span class="hint-text">演示系统 · {{ showAccounts ? '隐藏' : '查看' }}可用账号</span>
          </div>
          
          <transition name="accounts-fade">
            <div v-if="showAccounts" class="demo-accounts">
              <div class="accounts-title">快捷登录（演示）</div>
              <div class="accounts-list">
                <button 
                  class="account-btn"
                  :disabled="isLoading"
                  @click="quickLogin('pm')"
                >
                  <span class="account-icon">📊</span>
                  <div class="account-info">
                    <div class="account-role">产品经理</div>
                    <div class="account-username">pm / 123456</div>
                  </div>
                </button>
                <button 
                  class="account-btn"
                  :disabled="isLoading"
                  @click="quickLogin('market')"
                >
                  <span class="account-icon">📈</span>
                  <div class="account-info">
                    <div class="account-role">市场人员</div>
                    <div class="account-username">market / 123456</div>
                  </div>
                </button>
                <button 
                  class="account-btn"
                  :disabled="isLoading"
                  @click="quickLogin('ops')"
                >
                  <span class="account-icon">⚡</span>
                  <div class="account-info">
                    <div class="account-role">运营人员</div>
                    <div class="account-username">ops / 123456</div>
                  </div>
                </button>
              </div>
            </div>
          </transition>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  position: relative;
}

.login__container {
  max-width: 480px;
  width: 100%;
}

.login__header {
  text-align: center;
  margin-bottom: 48px;
}

.login__logo {
  margin-bottom: 24px;
}

.logo-circle {
  width: 96px;
  height: 96px;
  margin: 0 auto;
  background: var(--neu-bg-card);
  border-radius: var(--neu-radius-round);
  box-shadow: var(--neu-shadow-large);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--neu-transition-base);
}

.logo-circle:hover {
  box-shadow: var(--neu-shadow-hover);
  transform: translateY(-4px) scale(1.05);
}

.logo-icon {
  font-size: 48px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}

.login__title {
  font-family: 'Poppins', 'Inter', sans-serif;
  font-size: 36px;
  font-weight: 700;
  margin: 0 0 12px 0;
  color: var(--neu-text-primary);
  letter-spacing: -0.5px;
}

.login__subtitle {
  font-size: 15px;
  font-weight: 500;
  color: var(--neu-text-tertiary);
  margin: 0;
}

.login__content {
  background: var(--neu-bg-card);
  border-radius: var(--neu-radius-xl);
  padding: 48px;
  box-shadow: var(--neu-shadow-large);
}

/* 登录表单样式 */
.login-form {
  margin-bottom: 0;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 28px;
}

.login-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  background: var(--neu-bg-base);
  border-radius: var(--neu-radius-md);
  box-shadow: var(--neu-shadow-inset);
  padding: 0 16px;
  transition: all var(--neu-transition-base);
}

.input-wrapper:focus-within {
  box-shadow: inset -2px -2px 6px rgba(255, 255, 255, 0.7),
              inset 2px 2px 6px rgba(90, 106, 127, 0.2),
              0 0 0 3px rgba(108, 155, 209, 0.1);
}

.input-icon {
  font-size: 20px;
  margin-right: 12px;
  flex-shrink: 0;
}

.input-wrapper :deep(.el-input) {
  flex: 1;
}

.input-wrapper :deep(.el-input__wrapper) {
  background: transparent;
  box-shadow: none;
  padding: 14px 0;
}

.input-wrapper :deep(.el-input__inner) {
  font-size: 15px;
  color: var(--neu-text-primary);
}

.input-wrapper :deep(.el-input__inner::placeholder) {
  color: var(--neu-text-tertiary);
}

/* 登录按钮 */
.login-button {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, var(--neu-primary-light) 0%, var(--neu-primary) 100%);
  border: none;
  border-radius: var(--neu-radius-md);
  box-shadow: -4px -4px 8px rgba(255, 255, 255, 0.5),
              4px 4px 12px rgba(108, 155, 209, 0.4);
  transition: all var(--neu-transition-base);
}

.login-button:hover {
  box-shadow: -6px -6px 12px rgba(255, 255, 255, 0.6),
              6px 6px 16px rgba(108, 155, 209, 0.5);
  transform: translateY(-2px);
}

.login-button:active {
  box-shadow: inset -2px -2px 4px rgba(255, 255, 255, 0.3),
              inset 2px 2px 4px rgba(86, 129, 184, 0.4);
  transform: translateY(0);
}

/* Footer样式 */
.login__footer {
  text-align: center;
  padding-top: 32px;
  border-top: 1px solid rgba(90, 106, 127, 0.1);
}

.login__hint {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--neu-text-tertiary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--neu-transition-base);
  user-select: none;
}

.login__hint:hover {
  color: var(--neu-primary);
}

.hint-dot {
  width: 8px;
  height: 8px;
  background: linear-gradient(135deg, var(--neu-primary-light), var(--neu-primary));
  border-radius: 50%;
  box-shadow: 0 0 8px rgba(108, 155, 209, 0.5);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.6;
    transform: scale(0.9);
  }
}

/* 演示账号列表 */
.demo-accounts {
  margin-top: 24px;
  padding: 20px;
  background: var(--neu-bg-base);
  border-radius: var(--neu-radius-lg);
  box-shadow: var(--neu-shadow-inset);
}

.accounts-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--neu-text-secondary);
  margin-bottom: 16px;
  text-align: center;
}

.accounts-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.account-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--neu-bg-card);
  border: none;
  border-radius: var(--neu-radius-md);
  box-shadow: var(--neu-shadow-light);
  cursor: pointer;
  transition: all var(--neu-transition-base);
  text-align: left;
}

.account-btn:hover {
  box-shadow: var(--neu-shadow-hover);
  transform: translateY(-2px);
}

.account-btn:active {
  box-shadow: var(--neu-shadow-inset);
  transform: translateY(0);
}

.account-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  pointer-events: none;
}

.account-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.account-info {
  flex: 1;
}

.account-role {
  font-size: 14px;
  font-weight: 600;
  color: var(--neu-text-primary);
  margin-bottom: 2px;
}

.account-username {
  font-size: 12px;
  color: var(--neu-text-tertiary);
  font-family: 'Courier New', monospace;
}

/* 动画 */
.accounts-fade-enter-active,
.accounts-fade-leave-active {
  transition: all 0.3s ease;
}

.accounts-fade-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.accounts-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .login {
    padding: 20px 16px;
  }

  .login__header {
    margin-bottom: 32px;
  }

  .login__content {
    padding: 32px 24px;
  }
  
  .login__title {
    font-size: 28px;
  }

  .logo-circle {
    width: 80px;
    height: 80px;
  }

  .logo-icon {
    font-size: 40px;
  }

  .login-form :deep(.el-form-item) {
    margin-bottom: 24px;
  }
}

/* 横屏手机适配 */
@media (max-width: 767px) and (orientation: landscape) {
  .login {
    padding: 16px;
  }
  
  .login__header {
    margin-bottom: 20px;
  }
  
  .logo-circle {
    width: 64px;
    height: 64px;
  }
  
  .logo-icon {
    font-size: 32px;
  }
  
  .login__title {
    font-size: 24px;
    margin-bottom: 8px;
  }
  
  .login__subtitle {
    font-size: 13px;
  }
  
  .login__content {
    padding: 24px 20px;
  }

  .demo-accounts {
    padding: 16px;
    margin-top: 16px;
  }

  .accounts-list {
    gap: 8px;
  }
}

/* 移动端触摸优化 */
@media (hover: none) and (max-width: 767px) {
  .account-btn:hover {
    transform: none;
  }
  
  .account-btn:active {
    transform: scale(0.98);
  }
}
</style>
