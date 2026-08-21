import { defineStore } from 'pinia'
import { cartApi } from '../api'

/**
 * 购物车全局状态：角标数量等轻量信息
 */
export const useCartStore = defineStore('cart', {
  state: () => ({
    count: 0
  }),
  actions: {
    async refresh() {
      try {
        const items = await cartApi.list() || []
        this.count = items.reduce((s, i) => s + (i.quantity || 0), 0)
      } catch (e) {
        this.count = 0
      }
    }
  }
})