import { defineStore } from 'pinia'
import { authApi } from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  actions: {
    async login(form) {
      const data = await authApi.login(form)
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },
    async register(form) {
      return authApi.register(form)
    },
    async fetchMe() {
      this.user = await authApi.me()
      localStorage.setItem('user', JSON.stringify(this.user))
    },
    async logout() {
      try { await authApi.logout() } catch (e) { /* 忽略 */ }
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})