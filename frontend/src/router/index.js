import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  { path: '/', name: 'home', component: () => import('../views/Home.vue') },
  { path: '/product/:id', name: 'product', component: () => import('../views/ProductDetail.vue') },
  { path: '/cart', name: 'cart', component: () => import('../views/Cart.vue') },
  { path: '/orders', name: 'orders', component: () => import('../views/Orders.vue') },
  { path: '/chat', name: 'chat', component: () => import('../views/Chat.vue') },
  { path: '/mine', name: 'mine', component: () => import('../views/Mine.vue') },
  { path: '/mine/addresses', name: 'addresses', component: () => import('../views/AddressManage.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    return { path: '/login' }
  }
  if (to.path === '/login' && token) {
    return { path: '/' }
  }
})

export default router