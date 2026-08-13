import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import ResetPasswordView from '@/views/ResetPasswordView.vue'
import DashboardView from '@/views/DashboardView.vue'
import ClienteFormView from '@/views/ClienteFormView.vue'
import ClientesListView from '@/views/ClientesListView.vue'
import ClienteDetailView from '@/views/ClienteDetailView.vue'
import ProductFormView from '@/views/ProductFormView.vue'
import ProductsListView from '@/views/ProductsListView.vue'
import CategoriesListView from '@/views/CategoriesListView.vue'
import CategoryFormView from '@/views/CategoryFormView.vue'
import ColorwaysListView from '@/views/ColorwaysListView.vue'
import ColorwayFormView from '@/views/ColorwayFormView.vue'
import TabelasPrecoListView from '@/views/TabelasPrecoListView.vue'
import TabelaPrecoFormView from '@/views/TabelaPrecoFormView.vue'
import PedidoFormView from '@/views/PedidoFormView.vue'
import PedidosListView from '@/views/PedidosListView.vue'
import SalesListView from '@/views/SalesListView.vue'
import UserFormView from '@/views/UserFormView.vue'
import UsersListView from '@/views/UsersListView.vue'
import PurchaseOrderFormView from '@/views/PurchaseOrderFormView.vue'
import PurchaseOrdersListView from '@/views/PurchaseOrdersListView.vue'
import AccountsPayableListView from '@/views/AccountsPayableListView.vue'

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
    { path: '/produtos', name: 'produtos', component: ProductsListView },
    { path: '/produtos/novo', name: 'produtos-novo', component: ProductFormView },
    { path: '/produtos/:id/editar', name: 'produtos-editar', component: ProductFormView },
    { path: '/categorias', name: 'categorias', component: CategoriesListView },
    { path: '/categorias/novo', name: 'categorias-novo', component: CategoryFormView },
    { path: '/categorias/:id/editar', name: 'categorias-editar', component: CategoryFormView },
    { path: '/cores-estampas', name: 'cores-estampas', component: ColorwaysListView },
    { path: '/cores-estampas/novo', name: 'cores-estampas-novo', component: ColorwayFormView },
    { path: '/cores-estampas/:id/editar', name: 'cores-estampas-editar', component: ColorwayFormView },
    { path: '/tabelas-preco', name: 'tabelas-preco', component: TabelasPrecoListView },
    { path: '/tabelas-preco/novo', name: 'tabelas-preco-novo', component: TabelaPrecoFormView },
    { path: '/tabelas-preco/:id/editar', name: 'tabelas-preco-editar', component: TabelaPrecoFormView },
    { path: '/pedidos', name: 'pedidos', component: PedidosListView },
    { path: '/pedidos/novo', name: 'pedidos-novo', component: PedidoFormView },
    { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: PedidoFormView },
    { path: '/vendas', name: 'vendas', component: SalesListView },
    { path: '/usuarios', name: 'usuarios', component: UsersListView },
    { path: '/usuarios/novo', name: 'usuarios-novo', component: UserFormView },
    { path: '/usuarios/:id/editar', name: 'usuarios-editar', component: UserFormView },
    { path: '/compras', name: 'compras', component: PurchaseOrdersListView },
    { path: '/compras/novo', name: 'compras-novo', component: PurchaseOrderFormView },
    { path: '/compras/:id/editar', name: 'compras-editar', component: PurchaseOrderFormView },
    { path: '/contas-a-pagar', name: 'contas-a-pagar', component: AccountsPayableListView },
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
