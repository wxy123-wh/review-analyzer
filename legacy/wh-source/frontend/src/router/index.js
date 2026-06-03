import { createRouter, createWebHistory } from 'vue-router'

// 路由懒加载 - 优化初始加载性能
const LoginView = () => import('../views/LoginView.vue')
const OverviewView = () => import('../views/OverviewView.vue')
const AnalysisView = () => import('../views/AnalysisView.vue')
const ReviewsView = () => import('../views/ReviewsView.vue')
const TopicsView = () => import('../views/TopicsView.vue')
const ClustersView = () => import('../views/ClustersView.vue')
const ClusterDetailView = () => import('../views/ClusterDetailView.vue')
const CompareView = () => import('../views/CompareView.vue')
const AlertsView = () => import('../views/AlertsView.vue')
const SuggestionsView = () => import('../views/SuggestionsView.vue')
const EventsView = () => import('../views/EventsView.vue')
const BeforeAfterView = () => import('../views/BeforeAfterView.vue')
const DashboardPmView = () => import('../views/DashboardPmView.vue')
const DashboardMarketView = () => import('../views/DashboardMarketView.vue')
const DashboardOpsView = () => import('../views/DashboardOpsView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/overview' },
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/dashboard/pm', component: DashboardPmView, meta: { roles: ['PM'] } },
    { path: '/dashboard/market', component: DashboardMarketView, meta: { roles: ['MARKET'] } },
    { path: '/dashboard/ops', component: DashboardOpsView, meta: { roles: ['OPS'] } },
    { path: '/overview', component: OverviewView },
    { path: '/analysis', component: AnalysisView },
    { path: '/reviews', component: ReviewsView },
    { path: '/topics', component: TopicsView, meta: { roles: ['MARKET'] } },
    { path: '/clusters', component: ClustersView, meta: { roles: ['PM'] } },
    { path: '/clusters/:id', component: ClusterDetailView, meta: { roles: ['PM'] } },
    { path: '/compare', component: CompareView, meta: { roles: ['MARKET'] } },
    { path: '/alerts', component: AlertsView, meta: { roles: ['OPS'] } },
    { path: '/suggestions', component: SuggestionsView, meta: { roles: ['PM'] } },
    { path: '/events', component: EventsView, meta: { roles: ['PM', 'OPS'] } },
    { path: '/before-after', component: BeforeAfterView, meta: { roles: ['PM', 'OPS'] } },
    { path: '/:pathMatch(.*)*', redirect: '/overview' },
  ],
})

function roleHome(role) {
  const r = String(role || '').toUpperCase()
  if (r === 'PM') return '/dashboard/pm'
  if (r === 'MARKET') return '/dashboard/market'
  if (r === 'OPS') return '/dashboard/ops'
  return '/overview'
}

router.beforeEach((to) => {
  if (to.meta?.public) {
    const token = localStorage.getItem('repu_token')
    const role = localStorage.getItem('repu_role')
    if (to.path === '/login' && token && role) {
      return roleHome(role)
    }
    return true
  }

  const token = localStorage.getItem('repu_token')
  const role = localStorage.getItem('repu_role')
  if (!token || !role) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const allowed = to.meta?.roles
  if (Array.isArray(allowed) && allowed.length > 0 && !allowed.includes(String(role).toUpperCase())) {
    return roleHome(role)
  }

  return true
})

export default router
