import { defineStore } from 'pinia'
import { me as fetchMe, type MeResponse } from '@/api/auth'

interface AuthState {
  usuario: MeResponse | null
  checked: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({ usuario: null, checked: false }),
  getters: {
    isAuthenticated: (state) => state.usuario !== null,
  },
  actions: {
    async checkSession() {
      try {
        this.usuario = await fetchMe()
      } catch {
        this.usuario = null
      } finally {
        this.checked = true
      }
    },
    clear() {
      this.usuario = null
      this.checked = false
    },
  },
})
