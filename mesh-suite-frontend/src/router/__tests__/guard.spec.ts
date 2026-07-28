import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'
import router from '@/router'

vi.mock('@/api/auth')

describe('auth store session check', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('sets usuario on successful /me call', async () => {
    vi.mocked(authApi.me).mockResolvedValue({ nome: 'Marina', papel: 'ADMINISTRADOR' })

    const store = useAuthStore()
    await store.checkSession()

    expect(store.isAuthenticated).toBe(true)
    expect(store.usuario?.nome).toBe('Marina')
  })

  it('clears usuario on 401 from /me', async () => {
    vi.mocked(authApi.me).mockRejectedValue(new Error('401'))

    const store = useAuthStore()
    await store.checkSession()

    expect(store.isAuthenticated).toBe(false)
  })
})

describe('router navigation guard', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    // Router is a module-level singleton (imported once for the whole test file);
    // each test pushes its own target and asserts where navigation actually lands,
    // so leftover state from a prior test's current route doesn't affect the result.
  })

  it('redirects unauthenticated access to a protected route to /login', async () => {
    vi.mocked(authApi.me).mockRejectedValue(new Error('401'))

    await router.push('/')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('allows authenticated access to a protected route', async () => {
    vi.mocked(authApi.me).mockResolvedValue({ nome: 'Marina', papel: 'ADMINISTRADOR' })

    await router.push('/')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('dashboard')
  })

  it('allows access to a public route without authentication', async () => {
    vi.mocked(authApi.me).mockRejectedValue(new Error('401'))

    await router.push('/login')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
  })
})
