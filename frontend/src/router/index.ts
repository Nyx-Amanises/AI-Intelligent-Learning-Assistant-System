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
        { path: '/wrong-questions', name: 'wrong-questions', component: () => import('@/views/WrongQuestionView.vue') },
        { path: '/mastery', name: 'mastery', component: () => import('@/views/KnowledgeMasteryView.vue') },
        { path: '/analytics', name: 'analytics', component: () => import('@/views/LearningAnalyticsView.vue') },
        { path: '/ai-config', name: 'ai-config', component: () => import('@/views/AiConfigView.vue') }
      ]
    }
  ]
})

const DYNAMIC_IMPORT_RELOAD_KEY = 'ai-learning-assistant:dynamic-import-reload'

const isDynamicImportError = (error: unknown) => {
  const message = error instanceof Error ? error.message : String(error)
  return [
    'Failed to fetch dynamically imported module',
    'error loading dynamically imported module',
    'Importing a module script failed',
    'Failed to load module script'
  ].some((text) => message.includes(text))
}

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

router.afterEach(() => {
  sessionStorage.removeItem(DYNAMIC_IMPORT_RELOAD_KEY)
})

router.onError((error, to) => {
  if (!isDynamicImportError(error)) {
    return
  }

  const targetPath = to?.fullPath || window.location.pathname
  const previousTarget = sessionStorage.getItem(DYNAMIC_IMPORT_RELOAD_KEY)
  if (previousTarget === targetPath) {
    sessionStorage.removeItem(DYNAMIC_IMPORT_RELOAD_KEY)
    return
  }

  sessionStorage.setItem(DYNAMIC_IMPORT_RELOAD_KEY, targetPath)
  window.location.assign(targetPath)
})

export default router
