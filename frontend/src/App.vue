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

        <nav class="nav">
          <router-link to="/" class="nav-link">首页</router-link>
          <router-link to="/cart" class="nav-link nav-cart">
            购物车
            <el-badge v-if="cartStore.count > 0" :value="cartStore.count" class="cart-badge" />
          </router-link>
          <router-link to="/orders" class="nav-link">我的订单</router-link>
          <router-link to="/chat" class="nav-link">AI 助手</router-link>
        </nav>

        <div class="user-area" v-if="auth.token">
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
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from './stores/auth'
import { useCartStore } from './stores/cart'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const cartStore = useCartStore()
const keyword = ref('')

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
  // 登录态恢复：refresh 角标
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
.nick { cursor: pointer; color: var(--clr-text); padding: 6px 12px; border-radius: var(--radius-full); }
.nick:hover { color: var(--clr-primary); background: var(--clr-primary-bg); }

/* ----- Footer ----- */
.site-footer { background: #fff; border-top: 1px solid var(--clr-border); padding: 20px 0; margin-top: var(--space-6); }
.footer-inner { display: flex; flex-direction: column; align-items: center; gap: 6px; color: var(--clr-text-3); font-size: 13px; }
.footer-sub { color: var(--clr-text-4); font-size: 12px; }
</style>