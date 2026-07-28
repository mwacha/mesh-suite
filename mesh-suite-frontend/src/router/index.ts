import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import ResetPasswordView from '@/views/ResetPasswordView.vue'
import DashboardView from '@/views/DashboardView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/esqueci-senha', name: 'forgot-password', component: ForgotPasswordView, meta: { public: true } },
    { path: '/redefinir-senha', name: 'reset-password', component: ResetPasswordView, meta: { public: true } },
    { path: '/', name: 'dashboard', component: DashboardView },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.checked) {
    await auth.checkSession()
  }
  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'login' }
  }
  return true
})

export default router
