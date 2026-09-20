import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由表
 *
 * ★ meta.public = true 表示【无需登录即可访问】。
 *   这是"匿名可浏览商品与种草社区"的落地方式：把浏览类页面标为公开，
 *   把交易/账户/创作类页面留给登录后。
 *
 * 与后端的对应关系（后端 SaTokenConfig 同样放行这些只读接口）：
 *   公开：GET /api/v1/products/**、GET /api/v1/notes/**
 *   需登录：/cart、/orders、/payments、/addresses、/chat、POST /api/v1/notes
 *
 * 注意：标记为 public 只是"不强制跳登录"，不代表该页所有操作都免登录——
 * 例如笔记详情页上的点赞/收藏仍需登录，由页面自己引导（见各视图的空态/提示）。
 */
const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { public: true } },

  // ---- 公开浏览：商品 ----
  { path: '/', name: 'home', component: () => import('../views/Home.vue'), meta: { public: true } },
  { path: '/product/:id', name: 'product', component: () => import('../views/ProductDetail.vue'), meta: { public: true } },

  // ---- 公开浏览：种草社区 ----
  { path: '/notes', name: 'notes', component: () => import('../views/Notes.vue'), meta: { public: true } },
  // 静态段 /notes/create 必须放在 /notes/:id 之前（避免被当成 id=create）
  { path: '/notes/create', name: 'note-create', component: () => import('../views/NoteCreate.vue') },
  { path: '/notes/:id', name: 'note-detail', component: () => import('../views/NoteDetail.vue'), meta: { public: true } },

  // ---- 需要登录：交易 / 账户 / 创作 / AI ----
  { path: '/cart', name: 'cart', component: () => import('../views/Cart.vue') },
  { path: '/orders', name: 'orders', component: () => import('../views/Orders.vue') },
  { path: '/chat', name: 'chat', component: () => import('../views/Chat.vue') },
  { path: '/mine', name: 'mine', component: () => import('../views/Mine.vue') },
  { path: '/mine/addresses', name: 'addresses', component: () => import('../views/AddressManage.vue') },
  // V3：模拟收银台（MOCK 渠道）
  { path: '/mock-cashier', name: 'mock-cashier', component: () => import('../views/MockCashier.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  const isPublic = to.meta.public === true

  // 非公开页且未登录 → 去登录页，并记住原目标（登录后直接回来）
  if (!isPublic && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录还去登录页 → 回首页（或回登录前想去的地方）
  if (to.path === '/login' && token) {
    const redirect = to.query.redirect
    return { path: typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/' }
  }
})

export default router
