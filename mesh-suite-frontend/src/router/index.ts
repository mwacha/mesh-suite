import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import ResetPasswordView from '@/views/ResetPasswordView.vue'
import DashboardView from '@/views/DashboardView.vue'
import ClienteFormView from '@/views/ClienteFormView.vue'
import ClientesListView from '@/views/ClientesListView.vue'
import ClienteDetailView from '@/views/ClienteDetailView.vue'
import ProdutoFormView from '@/views/ProdutoFormView.vue'
import ProdutosListView from '@/views/ProdutosListView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/esqueci-senha', name: 'forgot-password', component: ForgotPasswordView, meta: { public: true } },
    { path: '/redefinir-senha', name: 'reset-password', component: ResetPasswordView, meta: { public: true } },
    { path: '/', name: 'dashboard', component: DashboardView },
    { path: '/clientes', name: 'clientes', component: ClientesListView },
    { path: '/clientes/novo', name: 'clientes-novo', component: ClienteFormView },
    { path: '/clientes/:id/editar', name: 'clientes-editar', component: ClienteFormView },
    { path: '/clientes/:id', name: 'clientes-detalhe', component: ClienteDetailView },
    { path: '/produtos', name: 'produtos', component: ProdutosListView },
    { path: '/produtos/novo', name: 'produtos-novo', component: ProdutoFormView },
    { path: '/produtos/:id/editar', name: 'produtos-editar', component: ProdutoFormView },
  ],
})

// Exported (not inlined into .beforeEach) so tests can call it directly with a
// constructed `to` object instead of driving real navigation through the router
// singleton -- vue-router short-circuits a push that resolves to the same matched
// record as the current route ("duplicate navigation") without re-running
// beforeEach, which makes navigation-driven guard tests fragile to execution
// order. Calling this function directly sidesteps that entirely.
export async function authGuard(to: Pick<RouteLocationNormalized, 'meta'>) {
  const auth = useAuthStore()
  if (!auth.checked) {
    await auth.checkSession()
  }
  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'login' }
  }
  return true
}

router.beforeEach(authGuard)

export default router
