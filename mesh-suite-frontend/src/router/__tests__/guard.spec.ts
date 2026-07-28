import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'
import { authGuard } from '@/router'

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
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('redirects unauthenticated access to a protected route to /login', async () => {
    vi.mocked(authApi.me).mockRejectedValue(new Error('401'))

    const result = await authGuard({ meta: {} })

    expect(result).toEqual({ name: 'login' })
  })

  it('allows authenticated access to a protected route', async () => {
    vi.mocked(authApi.me).mockResolvedValue({ nome: 'Marina', papel: 'ADMINISTRADOR' })

    const result = await authGuard({ meta: {} })

    expect(result).toBe(true)
  })

  it('allows access to a public route without authentication', async () => {
    vi.mocked(authApi.me).mockRejectedValue(new Error('401'))

    const result = await authGuard({ meta: { public: true } })

    expect(result).toBe(true)
  })
})
