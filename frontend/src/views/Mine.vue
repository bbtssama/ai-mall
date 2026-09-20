<template>
  <div class="mine-page">
    <!-- 个人信息卡 -->
    <div class="user-card mall-card">
      <div class="avatar" :class="{ 'is-guest': !auth.user }">{{ avatarText }}</div>
      <div class="user-info">
        <div class="nickname">{{ displayName }}</div>
        <div class="username">{{ subText }}</div>
      </div>
    </div>

    <!--
      电商数据条：个人中心此前一个数字都没有，只有 4 个跳转行——
      用户想确认「我的货到哪了」得先进订单页再数。
      这里把 4 个高频状态前置，数字取不到时留空（不显示假的 0）。
    -->
    <div class="stat-card mall-card">
      <div v-for="s in stats" :key="s.label" class="stat-item pressable" @click="go(s.to)">
        <el-icon class="stat-icon"><component :is="s.icon" /></el-icon>
        <span class="stat-num">{{ statText(s.value) }}</span>
        <span class="stat-label">{{ s.label }}</span>
      </div>
    </div>

    <!-- 功能入口 -->
    <div class="entry-card mall-card">
      <div v-for="e in entries" :key="e.label" class="entry-row pressable" @click="go(e.to)">
        <el-icon class="entry-icon"><component :is="e.icon" /></el-icon>
        <span class="entry-label">{{ e.label }}</span>
        <span class="entry-arrow">›</span>
      </div>
    </div>

    <el-button class="logout-btn" @click="logout">退出登录</el-button>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Box, Location, ChatDotRound, HomeFilled, Wallet, Van, CircleCheck } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { orderApi, addressApi } from '../api'

const router = useRouter()
const auth = useAuthStore()

// 图标：此前是一组 Emoji（📦📍🤖🏠），风格/尺寸不统一、无法着色，
// 且「AI 助手」配的是 🧬 DNA（是 Logo 的图标，语义不通）。统一换成 Element 图标。
const entries = [
  { label: '我的订单', icon: Box, to: '/orders' },
  { label: '收货地址', icon: Location, to: '/mine/addresses' },
  { label: 'AI 助手', icon: ChatDotRound, to: '/chat' },
  { label: '商城首页', icon: HomeFilled, to: '/' }
]

/** 状态数字；null = 尚未拿到（渲染成空占位，而不是骗人的 0） */
const counts = ref(null)
const stats = computed(() => {
  const c = counts.value
  return [
    { label: '待付款', icon: Wallet, to: '/orders', value: c ? c.pending : null },
    { label: '待收货', icon: Van, to: '/orders', value: c ? c.shipped : null },
    { label: '已完成', icon: CircleCheck, to: '/orders', value: c ? c.completed : null },
    { label: '收货地址', icon: Location, to: '/mine/addresses', value: c ? c.addresses : null }
  ]
})

const displayName = computed(() => auth.user?.nickname || auth.user?.username || '未登录')
const subText = computed(() => (auth.user?.username ? '@' + auth.user.username : '登录后可同步订单与收货地址'))

const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())

function statText(v) {
  if (v === null || v === undefined) return ''
  return v > 99 ? '99+' : String(v)
}

function go(path) { router.push(path) }

/**
 * 数字取不到（接口挂了/无权限）就不显示，入口照常可点——
 * 数据条是锦上添花，不能因为它把整页拖垮。
 */
async function loadCounts() {
  try {
    const [orders, addresses] = await Promise.all([
      orderApi.page({ page: 1, pageSize: 100 }),
      addressApi.list()
    ])
    const records = orders?.records || []
    counts.value = {
      pending: records.filter((o) => o.status === 'PENDING_PAY').length,
      // 「待收货」= 已付款但还没收到：已支付 + 已发货
      shipped: records.filter((o) => o.status === 'PAID' || o.status === 'SHIPPED').length,
      completed: records.filter((o) => o.status === 'COMPLETED').length,
      addresses: (addresses || []).length
    }
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(async () => {
  // 兜底：只有 token、localStorage.user 缺失时 store 里 user 为 null，
  // 页面会渲染成橙色「?」+ 空昵称。这里补拉一次用户信息（条件限定，不会循环请求）。
  if (auth.token && !auth.user) {
    try { await auth.fetchMe() } catch (e) { /* 拦截器已提示 */ }
  }
  loadCounts()
})

async function logout() {
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  await auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.mine-page { max-width: 560px; margin: 0 auto; }
.user-card {
  display: flex; align-items: center; gap: 16px; padding: 24px; margin-bottom: 16px;
}
.avatar {
  width: 64px; height: 64px; border-radius: 50%;
  background: linear-gradient(135deg, var(--clr-primary), var(--clr-primary-hover));
  color: #fff; font-size: 28px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.avatar.is-guest { background: linear-gradient(135deg, #c8ccd4, #a8aeb8); }
.nickname { font-size: 20px; font-weight: 700; color: var(--clr-text); }
.username { font-size: 13px; color: var(--clr-text-3); margin-top: 4px; }

/* ---- 数据条 ---- */
.stat-card {
  display: grid; grid-template-columns: repeat(4, 1fr);
  padding: 14px 0; margin-bottom: 16px;
}
.stat-item {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px; cursor: pointer; border-radius: var(--r-sm);
}
.stat-item + .stat-item { border-left: 1px solid var(--clr-border-light); }
.stat-icon { font-size: 20px; color: var(--clr-primary); }
.stat-num { font-size: 16px; font-weight: 700; color: var(--clr-text); line-height: 1.3; }
.stat-label { font-size: 12px; color: var(--clr-text-2); }

/* ---- 功能入口 ---- */
.entry-card { padding: 4px 0; margin-bottom: 24px; }
.entry-row {
  display: flex; align-items: center; gap: 12px; padding: 16px 20px; cursor: pointer;
  transition: background .15s;
}
.entry-row + .entry-row { border-top: 1px solid var(--clr-border-light); }
.entry-row:active { background: var(--clr-primary-bg); }
.entry-icon { font-size: 20px; color: var(--clr-primary); }
.entry-label { flex: 1; font-size: 15px; color: var(--clr-text); }
.entry-arrow { color: var(--clr-text-4); font-size: 20px; }

/* 退出登录：此前是「浅红底 + 4px 圆角」的 plain 按钮，看着像 disabled。
   改成描边红 + 统一圆角，语义清楚且不像禁用态。 */
.logout-btn {
  width: 100%; min-height: 48px;
  background: #fff; border: 1px solid var(--clr-danger); color: var(--clr-danger);
  border-radius: var(--r-sm); font-weight: 600;
}
.logout-btn:active { background: #fff1f1; }

/* =====================================================================
   移动端适配（≤ 768px）
   个人卡由「左头像 + 右信息」压成居中纵向；功能入口本就是列表形态，
   窄屏保持全宽列表并抬高行高到 ≥52px，退出登录按钮加高便于点按。
   ===================================================================== */
@media (max-width: 768px) {
  .mine-page { max-width: none; }

  .user-card {
    flex-direction: column; text-align: center; gap: 10px;
    padding: 20px 16px; margin-bottom: 12px;
  }
  .avatar { width: 56px; height: 56px; font-size: 24px; }
  .nickname { font-size: 17px; }
  .username { margin-top: 2px; }

  /* 数据条：每个格子 64px 高，四等分正好落在拇指区，分隔线由边框给 */
  .stat-card { padding: 10px 0; margin-bottom: 12px; }
  .stat-item { min-height: 64px; gap: 4px; }
  .stat-icon { font-size: 22px; }
  .stat-num { font-size: 17px; }
  .stat-label { font-size: 12px; }

  .entry-card { margin-bottom: 16px; }
  .entry-row { min-height: 54px; padding: 12px 16px; gap: 10px; }
  .entry-icon { font-size: 20px; }
  .entry-label { font-size: 15px; }

  .logout-btn { min-height: 48px; }
}
</style>
