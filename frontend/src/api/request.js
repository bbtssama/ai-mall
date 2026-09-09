import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截：附加 Sa-Token 令牌
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = token
  return config
})

// 响应拦截：解包 R{code,msg,data}
request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && body.code === 200) return body.data
    ElMessage.error(body?.msg || '请求失败')
    return Promise.reject(new Error(body?.msg || 'error'))
  },
  (err) => {
    const status = err.response?.status
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      ElMessage.warning('登录已过期，请重新登录')
      router.push('/login')
    } else if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
      // 超时 ≠ 网络不通：请求可能仍在服务端正常处理（AI 类长耗时接口），
      // 与"网络异常"混在一起会误导排查——单独提示
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error(err.response?.data?.msg || '网络异常，请稍后再试')
    }
    return Promise.reject(err)
  }
)

export default request