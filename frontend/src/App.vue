<template>
  <div class="layout">
    <!-- ===== Header ===== -->
    <header class="site-header">
      <div class="container header-inner">
        <div class="logo" @click="goHome">
          <span class="logo-icon">🧬</span>
          <span class="logo-text">AI 种草商城</span>
        </div>

        <div class="search-box">
          <el-input v-model="keyword" placeholder="搜索商品 / 关键词" clearable
                    @keyup.enter="doSearch" @clear="doSearch">
            <template #append>
              <el-button :icon="Search" @click="doSearch" />
            </template>
          </el-input>
        </div>

        <nav class="nav nav-desktop">
          <router-link to="/" class="nav-link">首页</router-link>
          <router-link to="/notes" class="nav-link">种草</router-link>
          <router-link to="/cart" class="nav-link nav-cart">
            购物车
            <el-badge v-if="cartCount > 0" :value="cartCount" class="cart-badge" />
          </router-link>
          <router-link to="/orders" class="nav-link">我的订单</router-link>
          <router-link to="/chat" class="nav-link">AI 助手</router-link>
        </nav>

        <div class="user-area">
          <!-- 已登录：个人中心 + 下拉 -->
          <template v-if="auth.token">
            <router-link to="/mine" class="nav-link user-entry">我的</router-link>
            <el-dropdown @command="onUserCmd">
              <span class="nick">{{ auth.user?.nickname || auth.user?.username }}</span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="mine">个人中心</el-dropdown-item>
                  <el-dropdown-item command="address">收货地址</el-dropdown-item>
                  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <!-- 未登录：登录入口（匿名用户可自由浏览商品与社区） -->
          <router-link v-else to="/login" class="nav-link login-entry">登录 / 注册</router-link>
        </div>
      </div>
    </header>

    <!-- ===== 主内容 ===== -->
    <main class="site-main">
      <div class="container">
        <router-view v-slot="{ Component }">
          <keep-alive :include="['Home']">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </div>
    </main>

    <!-- ===== Footer ===== -->
    <footer class="site-footer">
      <div class="container footer-inner">
        <span>🧬 AI 种草商城 — 内容社区 · 电商交易 · AI 引擎</span>
        <span class="footer-sub">Java 求职实战项目 · V1</span>
      </div>
    </footer>

    <!--
      ===== 移动端底部标签栏（仅小屏显示）=====
      移动端把主导航从顶栏移到拇指可达的底部，顶栏只留 Logo / 搜索 / 登录态。
      需要登录的标签（购物车/订单/AI）不做特殊处理：路由守卫会自动带去登录页。
    -->
    <nav class="tabbar">
      <router-link v-for="t in tabs" :key="t.to" :to="t.to" class="tab-item"
                   :class="{ 'is-active': isActive(t.to) }">
        <span class="tab-icon">
          <el-icon><component :is="t.icon" /></el-icon>
          <span v-if="t.to === '/cart' && cartCount > 0" class="tab-dot">{{ cartCount > 99 ? '99+' : cartCount }}</span>
        </span>
        <span class="tab-label">{{ t.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Search, HomeFilled, Notebook, ShoppingCart, List, ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from './stores/auth'
import { useCartStore } from './stores/cart'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const cartStore = useCartStore()
const keyword = ref('')

/** 底部标签栏配置（仅移动端渲染） */
const tabs = [
  { to: '/', label: '首页', icon: HomeFilled },
  { to: '/notes', label: '种草', icon: Notebook },
  { to: '/cart', label: '购物车', icon: ShoppingCart },
  { to: '/orders', label: '订单', icon: List },
  { to: '/chat', label: 'AI 助手', icon: ChatDotRound }
]

/** 购物车角标：未登录时不请求（接口需要登录），恒为 0 */
const cartCount = computed(() => (auth.token ? cartStore.count : 0))

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path === path || route.path.startsWith(path + '/')
}

function goHome() {
  router.push('/')
}
function doSearch() {
  const kw = keyword.value.trim()
  router.push(kw ? { path: '/', query: { keyword: kw } } : { path: '/' })
}
function onUserCmd(cmd) {
  if (cmd === 'mine') router.push('/mine')
  else if (cmd === 'address') router.push('/mine/addresses')
  else if (cmd === 'logout') {
    auth.logout().then(() => { ElMessage.success('已退出登录'); router.push('/login') })
  }
}

onMounted(async () => {
  // 登录态恢复：refresh 角标（未登录不请求，避免匿名浏览时打受保护接口）
  if (auth.token) cartStore.refresh()
})

// 导航变化时若已登录，刷新购物车角标（购物车/详情/订单等变动后回到其它页也保持最新）
watch(() => route.path, () => {
  if (auth.token) cartStore.refresh()
})
</script>

<style scoped>
.layout { min-height: 100vh; display: flex; flex-direction: column; }
.site-main { flex: 1; padding: var(--space-4) 0 var(--space-8); }

/* ----- Header ----- */
.site-header {
  position: sticky; top: 0; z-index: 100;
  background: var(--clr-bg-card);
  box-shadow: var(--shadow-sm);
}
.header-inner { display: flex; align-items: center; gap: var(--space-4); height: var(--header-height); }
.logo { display: flex; align-items: center; gap: 6px; cursor: pointer; flex-shrink: 0; }
.logo-icon { font-size: 24px; }
.logo-text { font-size: 20px; font-weight: 800; color: var(--clr-primary); letter-spacing: 0.5px; }

.search-box { flex: 1; max-width: 380px; }
.search-box :deep(.el-input__wrapper) { border-radius: var(--radius-full); padding-left: 14px; }
.search-box :deep(.el-input-group__append) { border-radius: 0 var(--radius-full) var(--radius-full) 0; }

.nav { display: flex; align-items: center; gap: var(--space-2); }
.nav-link {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 12px; border-radius: var(--radius-full);
  color: var(--clr-text); text-decoration: none; font-size: 15px;
  transition: all .15s;
}
.nav-link:hover { color: var(--clr-primary); background: var(--clr-primary-bg); }
.nav-link.router-link-exact-active { color: var(--clr-primary); background: var(--clr-primary-bg); }
.nav-cart { position: relative; }
.cart-badge { margin-left: 2px; }

.user-area { display: flex; align-items: center; gap: var(--space-2); margin-left: auto; }
.user-entry { font-weight: 600; }
.login-entry { color: var(--clr-primary); font-weight: 600; }
.nick { cursor: pointer; color: var(--clr-text); padding: 6px 12px; border-radius: var(--radius-full); }
.nick:hover { color: var(--clr-primary); background: var(--clr-primary-bg); }

/* ----- Footer ----- */
.site-footer { background: #fff; border-top: 1px solid var(--clr-border); padding: 20px 0; margin-top: var(--space-6); }
.footer-inner { display: flex; flex-direction: column; align-items: center; gap: 6px; color: var(--clr-text-3); font-size: 13px; }
.footer-sub { color: var(--clr-text-4); font-size: 12px; }

/* ----- 底部标签栏：桌面端默认隐藏 ----- */
.tabbar { display: none; }

/* =====================================================================
   移动端适配（≤ 768px）
   策略：顶栏压缩为两行（Logo + 登录态 / 搜索），主导航下沉到底部标签栏；
        底部为固定定位，主内容与页脚预留相应安全间距。
   ===================================================================== */
@media (max-width: 768px) {
  .site-header { position: sticky; }
  .header-inner {
    height: auto; flex-wrap: wrap;
    gap: 8px; padding: 8px 0 10px;
  }
  /* 第一行：Logo 占左，登录态占右 */
  .logo { order: 1; }
  .logo-text { font-size: 17px; }
  .logo-icon { font-size: 20px; }
  .user-area { order: 2; margin-left: auto; gap: 4px; }
  .user-area .nav-link { padding: 5px 10px; font-size: 13px; }
  .nav-desktop { display: none; }
  /* 第二行：搜索框占满整行 */
  .search-box { order: 3; flex: 1 1 100%; max-width: none; }

  .site-main { padding: 12px 0 16px; }
  /* 给固定底栏让位，避免最后一条内容被遮住 */
  .layout { padding-bottom: calc(56px + env(safe-area-inset-bottom, 0px)); }

  .site-footer { margin-top: 16px; padding: 16px 0; font-size: 12px; }

  .tabbar {
    display: flex;
    position: fixed; left: 0; right: 0; bottom: 0; z-index: 120;
    background: #fff;
    border-top: 1px solid var(--clr-border);
    padding-bottom: env(safe-area-inset-bottom, 0px);
    box-shadow: 0 -1px 6px rgba(0, 0, 0, .04);
  }
  .tab-item {
    flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
    gap: 2px; padding: 7px 0 6px;
    color: var(--clr-text-3); text-decoration: none;
    font-size: 11px; line-height: 1.2;
    -webkit-tap-highlight-color: transparent;
  }
  .tab-item.is-active { color: var(--clr-primary); }
  .tab-icon { position: relative; font-size: 20px; display: inline-flex; }
  .tab-icon .el-icon { font-size: 20px; }
  .tab-dot {
    position: absolute; top: -4px; right: -10px;
    min-width: 15px; height: 15px; padding: 0 4px;
    background: var(--clr-danger); color: #fff;
    border-radius: 999px; font-size: 10px; line-height: 15px; text-align: center;
    font-weight: 700;
  }
  .tab-label { font-weight: 500; }
}
</style>
