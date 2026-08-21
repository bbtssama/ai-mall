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
      }
    }
  }
})