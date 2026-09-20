<template>
  <div class="layout" :class="{ 'is-blank': isBlank }">
    <!-- ===== Header ===== -->
    <header class="site-header">
      <div class="container header-inner">
        <div class="logo" @click="goHome">
          <span class="logo-icon">🧬</span>
          <span class="logo-text">AI 种草商城</span>
        </div>

        <!--
          搜索：桌面用「输入框 + 提交按钮」的输入组；移动端换成内嵌图标的紧凑输入框
          （append 的灰块在手机上会贴在全圆角 pill 右端露出尖角，是典型"桌面控件移植"痕迹）。
          两者共用 keyword，按断点显隐。
        -->
        <div v-if="!isBlank" class="search-box">
          <el-input class="search-desktop" v-model="keyword" placeholder="搜索商品 / 关键词" clearable
                    @keyup.enter="doSearch" @clear="doSearch">
            <template #append>
              <el-button :icon="Search" @click="doSearch" />
            </template>
          </el-input>
          <el-input class="search-mobile" v-model="keyword" placeholder="搜索商品 / 关键词" clearable
                    :prefix-icon="Search" @keyup.enter="doSearch" @clear="doSearch" />
        </div>

        <nav v-if="!isBlank" class="nav nav-desktop">
          <router-link to="/" class="nav-link">首页</router-link>
          <router-link to="/notes" class="nav-link">种草</router-link>
          <router-link to="/cart" class="nav-link nav-cart">
            购物车
            <el-badge v-if="cartCount > 0" :value="cartCount" class="cart-badge" />
          </router-link>
          <router-link to="/orders" class="nav-link">我的订单</router-link>
          <router-link to="/chat" class="nav-link">AI 助手</router-link>
        </nav>

        <div v-if="!isBlank" class="user-area">
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
          <router-link v-else to="/login" class="nav-link login-entry">登录</router-link>
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

    <!-- ===== Footer（App 型页面 / blank 页面不渲染） ===== -->
    <footer v-if="showFooter" class="site-footer">
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
    <nav v-if="!isBlank" class="tabbar">
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

/**
 * blank 布局：登录等"任务型页面"不需要全局壳
 * —— 顶部搜索框与底部标签栏都收掉，避免出现"在登录页还挂着搜索商品"
 *   以及"未登录却露出购物车/订单入口（点了又弹回登录）"这两类荒谬感。
 */
const isBlank = computed(() => route.meta.blank === true)

/**
 * 是否渲染全站页脚。
 * App 型页面（如 /chat，occupies 整屏、自身滚动）不该有页脚——它会把页面撑成长文档，
 * 让吸底元素（聊天输入区）跟着错位。这类页面用 `meta.hideFooter` 声明。
 */
const showFooter = computed(() => !isBlank.value && route.meta.hideFooter !== true)

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

.search-box { flex: 1; max-width: 380px; min-width: 0; }
.search-mobile { display: none; }
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
   ★ 2026-09-20 重构：顶栏由"两行 97px"压成"单行 48px"。
     此前顶栏 = Logo 行 + 整行搜索框 = 97px，占掉首屏 11%~21%
     （个人中心页因为头像区被顶到下方，实测 21%）。
     现改为移动端电商的通行范式：一行里塞下
       [Logo 图标] [搜索框占中间] [登录/我的]
     并把字面 Logo 收成一个图标，把搜索框做成内嵌图标的紧凑 pill。
   ===================================================================== */
@media (max-width: 768px) {
  .header-inner {
    height: var(--hdr-h);
    flex-wrap: nowrap;            /* 单行 */
    gap: 10px;
    padding: 0;
  }
  /* 字面 Logo 在手机上省掉，只留图标 —— 把宽度让给搜索框 */
  .logo-text { display: none; }
  .logo-icon { font-size: 22px; }

  .nav-desktop { display: none; }

  /* 搜索框占中间，可压缩（min-width:0 很关键，否则 flex 项不会收缩） */
  .search-desktop { display: none; }
  .search-mobile { display: block; }
  .search-box { flex: 1 1 auto; max-width: none; min-width: 0; }
  .search-box :deep(.el-input__wrapper) {
    border-radius: var(--radius-full);
    background: #f4f5f7;
    box-shadow: none !important;
    padding-left: 10px;
  }
  .search-box :deep(.el-input__inner) { font-size: 16px; }  /* ≥16px 防 iOS 聚焦缩放 */
  .search-box :deep(.el-input__prefix) { color: var(--clr-text-3); }

  /* 右侧登录态：只留最要紧的一个入口 */
  .user-area { gap: 0; margin-left: 0; flex: 0 0 auto; }
  .user-area .nav-link { padding: 4px 6px; font-size: 14px; }
  .user-entry { display: none; }        /* "我的"入口由底部标签栏承担 */
  .nick { padding: 4px 2px; max-width: 84px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

  .site-main { padding: 10px 0 12px; }

  .site-footer { margin-top: 12px; padding: 14px 0; font-size: 12px; }

  /* ---- 底部标签栏 ---- */
  .tabbar {
    display: flex;
    position: fixed; left: 0; right: 0; bottom: 0; z-index: 120;
    background: rgba(255, 255, 255, .96);
    backdrop-filter: saturate(180%) blur(12px);
    border-top: 1px solid var(--clr-border);
    padding-bottom: var(--safe-b);          /* ★ 刘海机 home indicator 避让 */
    box-shadow: 0 -1px 6px rgba(0, 0, 0, .04);
  }
  .tab-item {
    flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
    gap: 2px; padding: 6px 0 5px; min-height: var(--tabbar-h);
    color: var(--clr-text-3); text-decoration: none;
    font-size: 11px; line-height: 1.2;
    -webkit-tap-highlight-color: transparent;
    transition: color .15s;
  }
  .tab-item.is-active { color: var(--clr-primary); }
  .tab-item:active { opacity: .7; }         /* 触摸反馈 */
  .tab-icon { position: relative; display: inline-flex; }
  .tab-icon .el-icon { font-size: 24px; }   /* 此前 20px，偏小 */
  .tab-dot {
    position: absolute; top: -5px; right: -11px;
    min-width: 18px; height: 18px; padding: 0 5px;
    background: var(--clr-danger); color: #fff;
    border-radius: 999px; font-size: 11px; line-height: 18px; text-align: center;
    font-weight: 700; box-shadow: 0 0 0 2px #fff;
  }
  .tab-label { font-weight: 500; }

  /* 内容为固定底栏让位（用统一 token，避免各页各写一套 mags） */
  .layout:not(.is-blank) { padding-bottom: var(--pad-bottom); }

  /* blank 布局（登录等）：只有一行极简顶栏，没有标签栏 */
  .is-blank .header-inner { justify-content: center; }
  .is-blank .logo-text { display: inline; }
}
</style>
