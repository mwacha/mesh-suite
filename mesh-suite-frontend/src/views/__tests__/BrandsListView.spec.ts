import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import BrandsListView from '@/views/BrandsListView.vue'
import * as brandsApi from '@/api/brands'
import type { BrandResponse } from '@/api/brands'

vi.mock('@/api/brands', async (importOriginal) => {
  const original = await importOriginal<typeof brandsApi>()
  return {
    ...original,
    listBrands: vi.fn(),
    getBrandCounts: vi.fn(),
    deleteBrand: vi.fn(),
  }
})

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/marcas', name: 'marcas', component: BrandsListView },
      { path: '/marcas/novo', name: 'marcas-novo', component: { template: '<div />' } },
      { path: '/marcas/:id/editar', name: 'marcas-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/marcas')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(BrandsListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const marcaExemplo: BrandResponse = {
  id: 'brand-1',
  name: 'Marca Alpha',
  active: true,
  linkedProducts: 3,
  createdAt: '2026-01-01T00:00:00Z',
}

function paginaCom(...content: BrandResponse[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 10 }
}

describe('BrandsListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(brandsApi.getBrandCounts).mockResolvedValue({ total: 7, active: 6, inactive: 1 })
  })

  it('loads and displays the brand list', async () => {
    vi.mocked(brandsApi.listBrands).mockResolvedValue(paginaCom(marcaExemplo))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Marca Alpha')
    expect(wrapper.text()).toContain('3 produtos')
  })

  it('shows the header count and the Total/Ativas/Inativas pills', async () => {
    vi.mocked(brandsApi.listBrands).mockResolvedValue(paginaCom(marcaExemplo))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('7 marcas cadastradas')
    expect(wrapper.text()).toContain('Ativas')
    expect(wrapper.text()).toContain('Inativas')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(brandsApi.listBrands).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de marcas.')
  })

  it('reloads the list when the search field changes', async () => {
    vi.mocked(brandsApi.listBrands).mockResolvedValue(paginaCom())
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-search"]').setValue('Alp')
    await flushPromises()

    expect(brandsApi.listBrands).toHaveBeenLastCalledWith(
      expect.objectContaining({ busca: 'Alp' }),
    )
  })

  it('navigates to the new-brand route when the button is clicked', async () => {
    vi.mocked(brandsApi.listBrands).mockResolvedValue(paginaCom())
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-marca"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('marcas-novo')
  })

  it('sorts by name when the column header is clicked', async () => {
    vi.mocked(brandsApi.listBrands).mockResolvedValue(paginaCom(marcaExemplo))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-nome"]').trigger('click')
    await flushPromises()

    expect(brandsApi.listBrands).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: 'name,asc' }),
    )
  })

  it('deletes a brand after confirmation and reloads the list', async () => {
    vi.mocked(brandsApi.listBrands).mockResolvedValue(paginaCom(marcaExemplo))
    vi.mocked(brandsApi.deleteBrand).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(brandsApi.deleteBrand).toHaveBeenCalledWith('brand-1')
  })

  it('shows the backend message when deletion is blocked because the brand is in use', async () => {
    vi.mocked(brandsApi.listBrands).mockResolvedValue(paginaCom(marcaExemplo))
    vi.mocked(brandsApi.deleteBrand).mockRejectedValue({
      response: { data: { mensagem: 'Não é possível excluir: 3 produto(s) usam esta marca' } },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Não é possível excluir: 3 produto(s) usam esta marca')
  })
})
