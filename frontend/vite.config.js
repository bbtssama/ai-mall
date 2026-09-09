import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,
    // 允许通过 Cloudflare Tunnel 访问的外部域名（paimon.store）
    allowedHosts: ['paimon.store', '.paimon.store'],
    // 开发代理：/api 转发到后端 8080，规避跨域
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // ★ /files 也要代理：笔记/头像图片存在后端本地磁盘（uploads/），
      // 后端返回的是相对 URL /files/**。不代理的话 Vite dev server 会把
      // 未匹配的路径 SPA-fallback 成 index.html（Content-Type: text/html），
      // 浏览器 <img> 收到 HTML → 裂图。生产同域部署无此问题。
      '/files': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})