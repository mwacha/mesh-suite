import { ref, watch, type Ref } from 'vue'
import { listMunicipalities } from '@/api/municipalities'
import type { SearchSelectItem } from '@/components/SearchSelect.vue'

/**
 * Municipality options for a city SearchSelect, scoped to the UF the form has
 * selected.
 *
 * `load()` is meant to run when the dropdown opens rather than on mount: the
 * IBGE table holds ~5.5k rows, so fetching it for every form that merely has an
 * address section would be wasted traffic. The result is cached per UF and
 * dropped when the UF changes, so reopening the dropdown doesn't refetch.
 */
export function useCityOptions(state: Ref<string>) {
  const items = ref<SearchSelectItem[]>([])
  const loading = ref(false)
  const failed = ref(false)
  let loadedFor: string | null = null

  async function load() {
    const uf = state.value || ''
    if (loadedFor === uf) {
      return
    }
    loading.value = true
    failed.value = false
    try {
      const names = await listMunicipalities({ uf: uf || undefined })
      items.value = names.map((name) => ({ id: name, label: name }))
      loadedFor = uf
    } catch {
      items.value = []
      failed.value = true
      loadedFor = null
    } finally {
      loading.value = false
    }
  }

  watch(state, () => {
    loadedFor = null
    items.value = []
  })

  return { items, loading, failed, load }
}
