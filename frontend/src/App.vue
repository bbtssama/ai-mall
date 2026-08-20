<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="logo" @click="$router.push('/')">🧬 AI 种草商城</div>
      <div class="nav" v-if="auth.token">
        <el-button text @click="$router.push('/')">首页</el-button>
        <el-button text @click="$router.push('/cart')">购物车</el-button>
        <el-button text @click="$router.push('/orders')">我的订单</el-button>
        <el-button text @click="$router.push('/chat')">AI 助手</el-button>
        <el-dropdown @command="onUserCmd">
          <span class="nick">{{ auth.user?.nickname || auth.user?.username }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { useAuthStore } from './stores/auth'
import { ElMessage } from 'element-plus'
import router from './router'

const auth = useAuthStore()

function onUserCmd(cmd) {
  if (cmd === 'logout') {
    auth.logout().then(() => {
      ElMessage.success('已退出登录')
      router.push('/login')
    })
  }
}
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { background: #f5f6f8; font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; }
.layout { min-height: 100vh; }
.header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid #eee; position: sticky; top: 0; z-index: 100;
}
.logo { font-size: 18px; font-weight: 700; cursor: pointer; color: #e8562c; }
.nick { cursor: pointer; color: #333; margin-left: 8px; }
.main { padding: 20px; }
</style>