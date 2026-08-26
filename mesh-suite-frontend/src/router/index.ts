import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import ResetPasswordView from '@/views/ResetPasswordView.vue'
import DashboardView from '@/views/DashboardView.vue'
import ClienteFormView from '@/views/ClienteFormView.vue'
import ClientesListView from '@/views/ClientesListView.vue'
import ClienteDetailView from '@/views/ClienteDetailView.vue'
import FornecedoresListView from '@/views/FornecedoresListView.vue'
import FornecedorFormView from '@/views/FornecedorFormView.vue'
import FornecedorDetailView from '@/views/FornecedorDetailView.vue'
import ProductFormView from '@/views/ProductFormView.vue'
import ProductsListView from '@/views/ProductsListView.vue'
import CategoriesListView from '@/views/CategoriesListView.vue'
import CategoryFormView from '@/views/CategoryFormView.vue'
import ColorwaysListView from '@/views/ColorwaysListView.vue'
import ColorwayFormView from '@/views/ColorwayFormView.vue'
import PriceTablesListView from '@/views/PriceTablesListView.vue'
import PriceTableFormView from '@/views/PriceTableFormView.vue'
import SalesOrderFormView from '@/views/SalesOrderFormView.vue'
import SalesOrdersListView from '@/views/SalesOrdersListView.vue'
import SalesListView from '@/views/SalesListView.vue'
import UserFormView from '@/views/UserFormView.vue'
import UsersListView from '@/views/UsersListView.vue'
import PermissionsView from '@/views/PermissionsView.vue'
import PermissionProfileFormView from '@/views/PermissionProfileFormView.vue'
import PurchaseOrderFormView from '@/views/PurchaseOrderFormView.vue'
import PurchaseOrdersListView from '@/views/PurchaseOrdersListView.vue'
import PurchaseInvoicesListView from '@/views/PurchaseInvoicesListView.vue'
import PurchaseInvoiceFormView from '@/views/PurchaseInvoiceFormView.vue'
import AccountsPayableListView from '@/views/AccountsPayableListView.vue'
import PaymentMethodsListView from '@/views/PaymentMethodsListView.vue'
import PaymentMethodFormView from '@/views/PaymentMethodFormView.vue'

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
    { path: '/fornecedores', name: 'fornecedores', component: FornecedoresListView },
    { path: '/fornecedores/novo', name: 'fornecedores-novo', component: FornecedorFormView },
    { path: '/fornecedores/:id/editar', name: 'fornecedores-editar', component: FornecedorFormView },
    { path: '/fornecedores/:id', name: 'fornecedores-detalhe', component: FornecedorDetailView },
    { path: '/produtos', name: 'produtos', component: ProductsListView },
    { path: '/produtos/novo', name: 'produtos-novo', component: ProductFormView },
    { path: '/produtos/:id/editar', name: 'produtos-editar', component: ProductFormView },
    { path: '/categorias', name: 'categorias', component: CategoriesListView },
    { path: '/categorias/novo', name: 'categorias-novo', component: CategoryFormView },
    { path: '/categorias/:id/editar', name: 'categorias-editar', component: CategoryFormView },
    { path: '/cores-estampas', name: 'cores-estampas', component: ColorwaysListView },
    { path: '/cores-estampas/novo', name: 'cores-estampas-novo', component: ColorwayFormView },
    { path: '/cores-estampas/:id/editar', name: 'cores-estampas-editar', component: ColorwayFormView },
    { path: '/tabelas-preco', name: 'tabelas-preco', component: PriceTablesListView },
    { path: '/tabelas-preco/novo', name: 'tabelas-preco-novo', component: PriceTableFormView },
    { path: '/tabelas-preco/:id/editar', name: 'tabelas-preco-editar', component: PriceTableFormView },
    { path: '/pedidos', name: 'pedidos', component: SalesOrdersListView },
    { path: '/pedidos/novo', name: 'pedidos-novo', component: SalesOrderFormView },
    { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: SalesOrderFormView },
    { path: '/vendas', name: 'vendas', component: SalesListView },
    { path: '/usuarios', name: 'usuarios', component: UsersListView },
    { path: '/usuarios/novo', name: 'usuarios-novo', component: UserFormView },
    { path: '/usuarios/:id/editar', name: 'usuarios-editar', component: UserFormView },
    { path: '/permissoes', name: 'permissoes', component: PermissionsView },
    { path: '/permissoes/perfis/novo', name: 'permissoes-perfis-novo', component: PermissionProfileFormView },
    { path: '/permissoes/perfis/:id/editar', name: 'permissoes-perfis-editar', component: PermissionProfileFormView },
    { path: '/compras', name: 'compras', component: PurchaseOrdersListView },
    { path: '/compras/novo', name: 'compras-novo', component: PurchaseOrderFormView },
    { path: '/compras/:id/editar', name: 'compras-editar', component: PurchaseOrderFormView },
    { path: '/compras/:id/nota-fiscal', name: 'compras-nota-fiscal', component: PurchaseInvoiceFormView },
    { path: '/notas-fiscais-entrada', name: 'notas-fiscais-entrada', component: PurchaseInvoicesListView },
    { path: '/contas-a-pagar', name: 'contas-a-pagar', component: AccountsPayableListView },
    { path: '/formas-pagamento', name: 'formas-pagamento', component: PaymentMethodsListView },
    { path: '/formas-pagamento/novo', name: 'formas-pagamento-novo', component: PaymentMethodFormView },
    { path: '/formas-pagamento/:id/editar', name: 'formas-pagamento-editar', component: PaymentMethodFormView },
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
