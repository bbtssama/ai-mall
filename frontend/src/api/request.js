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

/** 清空本地登录态（localStorage + Pinia，保持两处一致） */
function clearAuthState() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  // 动态 import：避免与 stores/auth.js 形成模块初始化环（该 store 依赖本模块）
  import('../stores/auth')
    .then(({ useAuthStore }) => {
      const auth = useAuthStore()
      auth.token = ''
      auth.user = null
    })
    .catch(() => { /* store 尚未就绪时忽略：localStorage 已清干净 */ })
}

/**
 * 统一的「未登录 / 登录过期」处理。
 *
 * ★ 为什么 HTTP 状态码与业务码两个通道都要处理：
 *   后端把"未登录"当作**业务码** 401 放在 HTTP 200 的响应体里
 *   （GlobalExceptionHandler 的 NotLoginException 分支没有 @ResponseStatus），
 *   所以两种形态都会出现：err.response.status === 401 与 body.code === 401。
 *   只处理其中一个，会导致 token 失效后前端永远不清理登录态 —— 用户卡在"死 token"状态。
 */
function handleUnauthorized(msg) {
  clearAuthState()
  const cur = router.currentRoute.value
  const onPublicPage = cur.meta && cur.meta.public === true
  if (onPublicPage) {
    // 公开页上的"需要登录的操作"（匿名点赞、加购等）：只提示，不打断浏览
    ElMessage.warning(msg || '该操作需要登录，请先登录')
  } else {
    ElMessage.warning('登录已过期，请重新登录')
    router.push({ path: '/login', query: { redirect: cur.fullPath } })
  }
}

// 响应拦截：解包 R{code,msg,data}
request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && body.code === 200) return body.data
    // 业务码 401：未登录 / 登录过期（见函数注释，这是本案的主通道）
    if (body && body.code === 401) {
      handleUnauthorized()
      return Promise.reject(new Error(body.msg || '未登录'))
    }
    ElMessage.error(body?.msg || '请求失败')
    return Promise.reject(new Error(body?.msg || 'error'))
  },
  (err) => {
    const status = err.response?.status
    if (status === 401) {
      handleUnauthorized()
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