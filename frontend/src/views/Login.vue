<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="title">🧬 AI 种草商城</h2>
      <p class="slogan">种草内容社区 · 电商交易 · AI 引擎</p>
      <el-tabs v-model="mode" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" :rules="rules" ref="loginRef" label-width="0">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="用户名" size="large" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="密码" size="large" show-password @keyup.enter="doLogin" />
            </el-form-item>
            <el-button type="danger" size="large" style="width:100%" :loading="loading" @click="doLogin">登 录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" :rules="regRules" ref="regRef" label-width="0">
            <el-form-item prop="username">
              <el-input v-model="regForm.username" placeholder="用户名（3-20 位字母/数字/下划线）" size="large" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="regForm.password" type="password" placeholder="密码（6-32 位）" size="large" show-password />
            </el-form-item>
            <el-form-item prop="nickname">
              <el-input v-model="regForm.nickname" placeholder="昵称（可选）" size="large" />
            </el-form-item>
            <el-button type="danger" size="large" style="width:100%" :loading="loading" @click="doRegister">注 册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const mode = ref('login')
const loading = ref(false)

const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', nickname: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const regRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '3-20 位字母/数字/下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }
  ]
}

/** 登录成功后的落点：路由守卫会带 ?redirect=/原路径；只接受站内相对路径（挡掉 //evil.com 这类开放重定向） */
function afterLoginTarget() {
  const r = route.query.redirect
  return typeof r === 'string' && r.startsWith('/') && !r.startsWith('//') ? r : '/'
}

async function doLogin() {
  loading.value = true
  try {
    await auth.login(loginForm)
    ElMessage.success('登录成功')
    router.push(afterLoginTarget())
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

async function doRegister() {
  loading.value = true
  try {
    await auth.register(regForm)
    ElMessage.success('注册成功，请登录')
    mode.value = 'login'
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 80vh; display: flex; align-items: center; justify-content: center;
}
.login-card { width: 420px; padding: 10px 20px 20px; }
.title { text-align: center; }
.slogan { text-align: center; color: #999; font-size: 13px; margin-bottom: 16px; }

/* =====================================================================
   移动端适配（≤ 768px）—— 桌面端（>768px）样式完全不受影响
   ===================================================================== */
@media (max-width: 768px) {
  /*
    真·垂直居中：此前卡片落在 y=129~502、下方留了 342px 空白——
    既不是居中也不是贴顶（min-height:0 让它退化成"跟着内容走"）。
    这里改用「视口高 - 顶栏 - 主区上下留白」作为容器高度再居中，
    卡片就被稳稳放在视觉中心；高度不够时（小屏/键盘弹起）自然滚，不会裁切。
  */
  .login-wrap {
    min-height: calc(100vh - var(--hdr-h) - 22px);
    min-height: calc(100dvh - var(--hdr-h) - 22px);
    align-items: center;
    padding: 12px 0 calc(24px + var(--safe-b));
  }
  .login-card {
    width: 100%; max-width: 100%;
    padding: 20px 16px;
    border-radius: var(--r-lg);          /* 桌面端默认 4px 的"Element 味"，手机上统一到 16px */
  }
  .title { font-size: 20px; }
  .slogan { font-size: 12px; margin-bottom: 12px; }
  /* ≥16px：避免 iOS Safari 聚焦输入框时放大页面 */
  .login-card :deep(.el-input__inner) { font-size: 16px; }
  /* 登录 / 注册 标签触摸目标抬高 */
  .login-card :deep(.el-tabs__item) { height: 44px; line-height: 44px; font-size: 15px; }
  /* 主按钮 ≥48px：登录页唯一的动作，必须是最好按的那个 */
  .login-card :deep(.el-button--large) { min-height: 48px; font-size: 16px; }
}
</style>