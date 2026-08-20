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
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
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

async function doLogin() {
  loading.value = true
  try {
    await auth.login(loginForm)
    ElMessage.success('登录成功')
    router.push('/')
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
</style>