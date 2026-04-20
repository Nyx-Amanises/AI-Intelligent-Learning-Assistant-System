import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    {
      path: '/',
      component: () => import('@/views/layout/AppLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: '/dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
        { path: '/materials', name: 'materials', component: () => import('@/views/MaterialView.vue') },
        { path: '/ai-tasks', name: 'ai-tasks', component: () => import('@/views/AiTaskCenterView.vue') },
        { path: '/rag-eval', name: 'rag-eval', component: () => import('@/views/RagEvalView.vue') },
        { path: '/summary', name: 'summary', component: () => import('@/views/SummaryView.vue') },
        { path: '/quiz', name: 'quiz', component: () => import('@/views/QuizView.vue') },
        { path: '/practice', name: 'practice', component: () => import('@/views/PracticeView.vue') },
        { path: '/ai-config', name: 'ai-config', component: () => import('@/views/AiConfigView.vue') }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && !userStore.token) {
    return '/login'
  }
  if (to.path === '/login' && userStore.token) {
    return '/dashboard'
  }
  return true
})

export default router
