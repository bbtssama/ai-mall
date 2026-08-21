<template>
  <div class="mine-page">
    <!-- 个人信息卡 -->
    <div class="user-card mall-card">
      <div class="avatar">{{ avatarText }}</div>
      <div class="user-info">
        <div class="nickname">{{ auth.user?.nickname || auth.user?.username }}</div>
        <div class="username">@{{ auth.user?.username }}</div>
      </div>
    </div>

    <!-- 功能入口 -->
    <div class="entry-card mall-card">
      <div class="entry-row" @click="go('/orders')">
        <span class="entry-icon">📦</span>
        <span class="entry-label">我的订单</span>
        <span class="entry-arrow">›</span>
      </div>
      <div class="entry-row" @click="go('/mine/addresses')">
        <span class="entry-icon">📍</span>
        <span class="entry-label">收货地址</span>
        <span class="entry-arrow">›</span>
      </div>
      <div class="entry-row" @click="go('/chat')">
        <span class="entry-icon">🤖</span>
        <span class="entry-label">AI 助手</span>
        <span class="entry-arrow">›</span>
      </div>
      <div class="entry-row" @click="go('/')">
        <span class="entry-icon">🏠</span>
        <span class="entry-label">商城首页</span>
        <span class="entry-arrow">›</span>
      </div>
    </div>

    <el-button class="logout-btn" type="danger" plain @click="logout">退出登录</el-button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const avatarText = computed(() => {
  const n = auth.user?.nickname || auth.user?.username || '？'
  return n.slice(0, 1).toUpperCase()
})

function go(path) { router.push(path) }

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
}
.nickname { font-size: 20px; font-weight: 700; color: var(--clr-text); }
.username { font-size: 13px; color: var(--clr-text-3); margin-top: 4px; }

.entry-card { padding: 4px 0; margin-bottom: 24px; }
.entry-row {
  display: flex; align-items: center; gap: 12px; padding: 16px 20px; cursor: pointer;
  transition: background .15s;
}
.entry-row + .entry-row { border-top: 1px solid var(--clr-border-light); }
.entry-row:hover { background: var(--clr-primary-bg); }
.entry-icon { font-size: 20px; }
.entry-label { flex: 1; font-size: 15px; color: var(--clr-text); }
.entry-arrow { color: var(--clr-text-4); font-size: 20px; }

.logout-btn { width: 100%; }
</style>