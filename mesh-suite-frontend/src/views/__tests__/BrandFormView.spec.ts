import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import BrandFormView from '@/views/BrandFormView.vue'
import * as brandsApi from '@/api/brands'

vi.mock('@/api/brands', async (importOriginal) => {
  const original = await importOriginal<typeof brandsApi>()
  return {
    ...original,
    getBrand: vi.fn(),
    createBrand: vi.fn(),
    updateBrand: vi.fn(),
  }
})

function mountWithRouter(path = '/marcas/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/marcas', name: 'marcas', component: { template: '<div />' } },
      { path: '/marcas/novo', name: 'marcas-novo', component: BrandFormView },
      { path: '/marcas/:id/editar', name: 'marcas-editar', component: BrandFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(BrandFormView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

describe('BrandFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows a required-field error when nome is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(brandsApi.createBrand).not.toHaveBeenCalled()
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(brandsApi.createBrand).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Marca Alpha')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(brandsApi.createBrand).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Marca Alpha', active: true }),
    )
    expect(router.currentRoute.value.name).toBe('marcas')
  })

  it('shows a conflict message on duplicate nome (409)', async () => {
    vi.mocked(brandsApi.createBrand).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Marca Alpha')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma marca cadastrada com este nome')
  })

  it('toggles between Ativo and Inativo status', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="status-INATIVO"]').trigger('click')
    vi.mocked(brandsApi.createBrand).mockResolvedValue({} as any)
    await wrapper.find('[data-test="nome"]').setValue('Marca Alpha')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(brandsApi.createBrand).toHaveBeenCalledWith(
      expect.objectContaining({ active: false }),
    )
  })

  it('loads existing brand data in edit mode', async () => {
    vi.mocked(brandsApi.getBrand).mockResolvedValue({
      id: 'brand-1', name: 'Marca Alpha', active: true, linkedProducts: 2, createdAt: '2026-01-01T00:00:00Z',
    })

    const { wrapper } = await mountWithRouter('/marcas/brand-1/editar')
    await flushPromises()

    expect(brandsApi.getBrand).toHaveBeenCalledWith('brand-1')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Marca Alpha')
  })

  it('shows an error message when loading brand data fails in edit mode', async () => {
    vi.mocked(brandsApi.getBrand).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/marcas/brand-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da marca.')
  })
})
